package dev.craftlink.video;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.craftlink.classroom.ClassroomRoom;
import dev.craftlink.mixin.BotRenderTargetAccessor;
import dev.craftlink.mixin.PoseStackAccessor;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** Bounded icon work queue. Network/server threads only enqueue copies; GPU work is client-only. */
public final class ItemIcons {
    private record Icon(String key,ItemStack stack,CompletableFuture<String> image) {}
    private static final LinkedHashMap<String,Icon> cache=new LinkedHashMap<>(128,.75f,true);
    private static final ArrayDeque<Icon> queue=new ArrayDeque<>();
    private record Preview(int skin,Map<net.minecraft.world.entity.EquipmentSlot,ItemStack> equipment,net.minecraft.nbt.Tag accessories,CompletableFuture<String> image) {}
    private static final LinkedHashMap<UUID,Preview> previews=new LinkedHashMap<>();
    private static long nextKey;
    private static TextureTarget target;
    private static net.minecraft.client.renderer.RenderBuffers iconBuffers;
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
    public static void preview(ClassroomRoom room,UUID peer,dev.craftlink.classroom.ClassroomBot bot){
        var equipment=new EnumMap<net.minecraft.world.entity.EquipmentSlot,ItemStack>(net.minecraft.world.entity.EquipmentSlot.class);
        for(var slot:net.minecraft.world.entity.EquipmentSlot.values())equipment.put(slot,bot.getItemBySlot(slot).copy());
        var job=new Preview(bot.skinIndex(),equipment,dev.craftlink.classroom.AccessoryAppearance.capture(bot),new CompletableFuture<>());
        synchronized(ItemIcons.class){
            if(previews.size()>=8&&!previews.containsKey(peer))return;
            var old=previews.put(peer,job);if(old!=null)old.image.complete("");
        }
        job.image.thenAccept(image->{if(image.isEmpty())return;var message=new JsonObject();message.addProperty("type","appearance");message.addProperty("image",image);room.publishInventory(peer,message);});
    }
    public static void renderQueued(Minecraft mc){
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        for(int i=0;i<3;i++){
            Icon icon;synchronized(ItemIcons.class){icon=queue.poll();}if(icon==null)break;
            try{icon.image.complete(render(mc,16,16,64,64,graphics->graphics.renderItem(icon.stack,0,0)));}
            catch(Exception|LinkageError failure){iconBuffers=null;icon.image.complete("");com.mojang.logging.LogUtils.getLogger().warn("CraftLink item icon render failed",failure);}
        }
        Preview preview=null;
        synchronized(ItemIcons.class){if(!previews.isEmpty()){var iterator=previews.values().iterator();preview=iterator.next();iterator.remove();}}
        if(preview!=null){
            try{
                var entity=new dev.craftlink.classroom.ClassroomBot(dev.craftlink.classroom.ClassroomEntities.BOT.get(),mc.level);
                entity.previewSkin(preview.skin);preview.equipment.forEach(entity::setItemSlot);
                dev.craftlink.classroom.AccessoryAppearance.apply(entity,preview.accessories);
                var dispatcher=mc.getEntityRenderDispatcher();var orientation=new org.joml.Quaternionf(dispatcher.cameraOrientation());
                try{preview.image.complete(render(mc,64,96,128,192,graphics->net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,32,90,42,-12,0,entity)));}
                finally{dispatcher.overrideCameraOrientation(orientation);dispatcher.setRenderShadow(true);}
            }catch(Exception|LinkageError failure){iconBuffers=null;preview.image.complete("");com.mojang.logging.LogUtils.getLogger().warn("CraftLink equipment preview failed",failure);}
        }
    }
    private static String render(Minecraft mc,int logicalWidth,int logicalHeight,int width,int height,java.util.function.Consumer<GuiGraphics> paint)throws java.io.IOException{
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
            if(target==null||target.width!=width||target.height!=height){if(target!=null)target.destroyBuffers();target=new TextureTarget(width,height,true,Minecraft.ON_OSX);}
            ((BotRenderTargetAccessor)mc).craftlink$target(target);target.bindWrite(true);
            RenderSystem.disableScissor();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.clearColor(0,0,0,0);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,logicalWidth,logicalHeight,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            poses.setIdentity();poses.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();
            // Mod glints can emit foil and base vertices concurrently. RenderBuffers
            // installs their fixed buffers; a single immediate builder cannot do that.
            if(iconBuffers==null)iconBuffers=new net.minecraft.client.renderer.RenderBuffers();
            var buffers=iconBuffers.bufferSource();var graphics=new GuiGraphics(mc,buffers);
            paint.accept(graphics);graphics.flush();
            // Screenshot.takeScreenshot forces every alpha byte to 255, leaving black squares.
            try(var image=new NativeImage(width,height,false)){
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
    public static synchronized void close(){for(Icon icon:queue)icon.image.complete("");queue.clear();cache.clear();for(var preview:previews.values())preview.image.complete("");previews.clear();iconBuffers=null;if(target!=null){target.destroyBuffers();target=null;}}
}
