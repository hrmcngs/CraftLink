package dev.craftlink.video;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.craftlink.classroom.ClassroomRoom;
import dev.craftlink.mixin.BotRenderTargetAccessor;
import dev.craftlink.mixin.PoseStackAccessor;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** Bounded icon work queue. Network/server threads only enqueue copies; GPU work is client-only. */
public final class ItemIcons {
    private record Icon(String key,ItemStack stack,CompletableFuture<String> image) {}
    private static final LinkedHashMap<String,Icon> cache=new LinkedHashMap<>(128,.75f,true);
    private static final ArrayDeque<Icon> queue=new ArrayDeque<>();
    private static long nextKey;
    private static TextureTarget target;
    public static synchronized String request(ItemStack stack){
        if(stack.isEmpty())return "";
        ItemStack copy=stack.copy();copy.setCount(1);
        String identity=copy.save(new net.minecraft.nbt.CompoundTag()).toString();
        Icon icon=cache.get(identity);
        if(icon==null){
            if(queue.size()>=256)return "";
            icon=new Icon(Long.toString(++nextKey),copy,new CompletableFuture<>());cache.put(identity,icon);queue.add(icon);
            while(cache.size()>1024)cache.remove(cache.keySet().iterator().next());
        }
        return icon.key;
    }
    public static void send(ClassroomRoom room,UUID peer,JsonObject message){
        room.publishInventory(peer,message);
        var icons=new ArrayList<Icon>();
        synchronized(ItemIcons.class){
            JsonArray entries=message.has("slots")?message.getAsJsonArray("slots"):message.getAsJsonArray("items");
            if(entries==null)return;
            var keys=new HashSet<String>();for(JsonElement entry:entries)if(entry.getAsJsonObject().has("iconKey"))keys.add(entry.getAsJsonObject().get("iconKey").getAsString());
            for(Icon icon:cache.values())if(keys.contains(icon.key))icons.add(icon);
        }
        if(icons.isEmpty())return;
        CompletableFuture.allOf(icons.stream().map(Icon::image).toArray(CompletableFuture[]::new)).thenRun(()->{
            var result=new JsonObject();result.addProperty("type","itemIcons");var images=new JsonArray();
            for(Icon icon:icons){String image=icon.image.getNow("");if(image.isEmpty())continue;var item=new JsonObject();item.addProperty("key",icon.key);item.addProperty("image",image);images.add(item);}
            result.add("icons",images);room.publishInventory(peer,result);
        });
    }
    public static void renderQueued(Minecraft mc){
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        for(int i=0;i<3;i++){
            Icon icon;synchronized(ItemIcons.class){icon=queue.poll();}if(icon==null)return;
            try{icon.image.complete(render(mc,icon.stack));}
            catch(Exception failure){icon.image.complete("");com.mojang.logging.LogUtils.getLogger().warn("CraftLink item icon render failed",failure);}
        }
    }
    private static String render(Minecraft mc,ItemStack stack)throws java.io.IOException{
        var state=new VideoRenderState();var original=mc.getMainRenderTarget();
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        var poses=RenderSystem.getModelViewStack();var saved=new PoseStackSnapshot(((PoseStackAccessor)poses).craftlink$poses());
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int[] box=new int[4];GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,box);
        float[] clear=new float[4];GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);
        float[] color=RenderSystem.getShaderColor().clone();
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try{
            if(target==null)target=new TextureTarget(64,64,true,Minecraft.ON_OSX);
            ((BotRenderTargetAccessor)mc).craftlink$target(target);target.bindWrite(true);
            RenderSystem.disableScissor();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.clearColor(0,0,0,0);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,16,16,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            poses.setIdentity();poses.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();
            var buffers=MultiBufferSource.immediate(new BufferBuilder(256));var graphics=new GuiGraphics(mc,buffers);
            graphics.renderItem(stack,0,0);graphics.flush();
            // Screenshot.takeScreenshot forces every alpha byte to 255, leaving black squares.
            try(var image=new NativeImage(64,64,false)){
                RenderSystem.bindTexture(target.getColorTextureId());image.downloadTexture(0,false);image.flipY();
                return "data:image/png;base64,"+Base64.getEncoder().encodeToString(image.asByteArray());
            }
        }finally{
            ((BotRenderTargetAccessor)mc).craftlink$target(original);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);RenderSystem.setProjectionMatrix(projection,sorting);
            saved.restore(((PoseStackAccessor)poses).craftlink$poses());RenderSystem.applyModelViewMatrix();state.restore();
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(scissor)RenderSystem.enableScissor(box[0],box[1],box[2],box[3]);else RenderSystem.disableScissor();
            RenderSystem.depthMask(mask);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);RenderSystem.clearColor(clear[0],clear[1],clear[2],clear[3]);
        }
    }
    public static synchronized void close(){for(Icon icon:queue)icon.image.complete("");queue.clear();cache.clear();if(target!=null){target.destroyBuffers();target=null;}}
}
