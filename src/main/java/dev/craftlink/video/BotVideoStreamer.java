package dev.craftlink.video;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import dev.craftlink.classroom.*;
import dev.craftlink.mixin.BotRenderTargetAccessor;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.*;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** One 480x270 BOT view at a time, at most 12 captures/second across all peers. */
public final class BotVideoStreamer {
    private final ClassroomServer server;
    private final ClassroomBots bots;
    private final ExecutorService encoder = Executors.newSingleThreadExecutor(r -> {var t=new Thread(r,"CraftLink BOT video");t.setDaemon(true);return t;});
    private final AtomicBoolean encoding = new AtomicBoolean();
    private final JpegEncoder jpeg = new JpegEncoder();
    private TextureTarget target;
    private long nextCapture;
    private int cursor, sequence;
    private boolean rendering;
    private volatile String error = "";
    public BotVideoStreamer(ClassroomServer server, ClassroomBots bots){this.server=server;this.bots=bots;}
    public boolean rendering(){return rendering;}
    public String error(){return error;}
    public void capture(Minecraft mc,float partial){
        ClassroomRoom room=server.room();
        if(room==null||mc.level==null||mc.player==null||encoding.get()||encoder.isShutdown()||System.nanoTime()<nextCapture)return;
        var peers=room.videoTargets();if(peers.isEmpty())return;
        nextCapture=System.nanoTime()+83_333_333L;
        if(Minecraft.useShaderTransparency()){error="BOT映像: 描画優先度を処理優先または描画優先に変更してください";return;}
        var peer=peers.get(Math.floorMod(cursor++,peers.size()));var uuid=bots.entityIds().get(peer.id());
        Entity bot=null;for(Entity entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(uuid)){bot=entity;break;}
        if(bot==null||!encoding.compareAndSet(false,true))return;
        var originalTarget=mc.getMainRenderTarget();var originalCamera=mc.cameraEntity;
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        var inverse=new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend=GL11.glIsEnabled(GL11.GL_BLEND), cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
float[] color=RenderSystem.getShaderColor().clone();
var modelView=RenderSystem.getModelViewStack();modelView.pushPose();modelView.setIdentity();RenderSystem.applyModelViewMatrix();
boolean pushed=false;

        try{
            rendering=true;
            if(target==null)target=new TextureTarget(480,270,true,Minecraft.ON_OSX);
            ((BotRenderTargetAccessor)mc).craftlink$target(target);mc.cameraEntity=bot;
            target.bindWrite(true);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
            BotCamera camera=new BotCamera();camera.configure(mc,bot,partial);
            PoseStack pose=new PoseStack();pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));pose.mulPose(Axis.YP.rotationDegrees(camera.getYRot()+180));
            Matrix4f lens=new Matrix4f().perspective((float)Math.toRadians(70),480f/270f,.05f,Math.max(64,mc.options.getEffectiveRenderDistance()*16));
            RenderSystem.setProjectionMatrix(lens,VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.setInverseViewRotationMatrix(new Matrix3f(pose.last().normal()).invert());
            mc.getProfiler().push("craftlink_bot_view");pushed=true;
            mc.levelRenderer.prepareCullFrustum(pose,camera.getPosition(),lens);
            mc.levelRenderer.renderLevel(pose,partial,System.nanoTime(),false,camera,mc.gameRenderer,mc.gameRenderer.lightTexture(),lens);
            int[] pixels;try(var image=Screenshot.takeScreenshot(target)){pixels=image.getPixelsRGBA();}
            int frame=sequence=sequence==Integer.MAX_VALUE?1:sequence+1;
            encoder.execute(()->{try{room.publishVideo(peer,frame,VideoFrame.packet(frame,480,270,0,jpeg.encodeFrame(480,270,pixels)));error="";}catch(Exception failure){error="BOT映像の圧縮に失敗しました";}finally{encoding.set(false);}});
        }catch(Exception failure){error="BOT映像取得失敗: "+failure.getClass().getSimpleName();encoding.set(false);nextCapture=System.nanoTime()+5_000_000_000L;}
        finally{
            if(pushed)mc.getProfiler().pop();
            mc.cameraEntity=originalCamera;((BotRenderTargetAccessor)mc).craftlink$target(originalTarget);
            RenderSystem.setProjectionMatrix(projection,sorting);RenderSystem.setInverseViewRotationMatrix(inverse);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);RenderSystem.bindTexture(texture);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            mc.getEntityRenderDispatcher().prepare(mc.level,mc.gameRenderer.getMainCamera(),mc.crosshairPickEntity);
modelView.popPose();RenderSystem.applyModelViewMatrix();
if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
RenderSystem.depthMask(depthMask);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
rendering=false;

        }
    }
    private static final class BotCamera extends Camera {
        void configure(Minecraft mc,Entity bot,float partial) {
            setup(mc.level,bot,false,false,partial);
            // A fresh Camera has zero smoothed eye height until tick(); use this BOT's actual eyes.
            setPosition(bot.getEyePosition(partial));
        }
    }
    public void close(){encoder.shutdownNow();if(target!=null){target.destroyBuffers();target=null;}}
}
