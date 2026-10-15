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

/** One BOT view at a time, at most 20 captures/second across all peers. */
public final class BotVideoStreamer {
    private final ClassroomServer server;
    private final ClassroomBots bots;
    private final ExecutorService encoder = Executors.newSingleThreadExecutor(r -> {var t=new Thread(r,"CraftLink BOT video");t.setDaemon(true);return t;});
    private final AtomicBoolean encoding = new AtomicBoolean();
    private final BotFirstPersonRenderer firstPerson=new BotFirstPersonRenderer();
    private final JpegEncoder jpeg = new JpegEncoder();
    private TextureTarget target;
    private long nextCapture;
    private int cursor, sequence;
    private boolean rendering;
    private volatile int lastPlayerCount;
    private int playerCount;
    public int lastPlayerCount(){return lastPlayerCount;}
    public void entityRendered(Entity entity){if(rendering && entity instanceof net.minecraft.world.entity.player.Player)playerCount++;}
    private volatile String error = "";
    private volatile ClassroomRoom failedRoom;
    public void suspend(RuntimeException failure){
        rendering=false;failedRoom=server.room();error="BOT映像を停止しました。部屋を終了して再開してください";
        if(failedRoom!=null)failedRoom.videoStatus(error);
        com.mojang.logging.LogUtils.getLogger().error("CraftLink BOT video suspended after a rendering failure",failure);
    }
    public BotVideoStreamer(ClassroomServer server, ClassroomBots bots){this.server=server;this.bots=bots;}
    public void bindWorldTarget(){if(rendering && target!=null)target.bindWrite(true);}
    public boolean rendering(){return rendering;}
    public String error(){return error;}
    public void capture(Minecraft mc,float partial){
        ClassroomRoom room=server.room();
        if(room==null||room==failedRoom||mc.getOverlay()!=null||mc.level==null||mc.player==null||encoding.get()||encoder.isShutdown()||System.nanoTime()<nextCapture)return;
        var peers=room.videoTargets();if(peers.isEmpty())return;
        long captureStart=System.nanoTime();
        nextCapture=captureStart+50_000_000L;
        if(Minecraft.useShaderTransparency()){error="BOT映像: PCのビデオ設定でグラフィックスを処理優先または描画優先に変更してください";room.videoStatus(error);return;}
        var peer=peers.get(Math.floorMod(cursor++,peers.size()));var uuid=bots.entityIds().get(peer.id());
        Entity bot=null;for(Entity entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(uuid)){bot=entity;break;}
        if(bot==null){room.videoStatus("BOTを読み込み中です。PCでBOTの近くへ移動してください");return;}
        if(!encoding.compareAndSet(false,true))return;
        var quality=peer.quality();
        var sharedState=new VideoRenderState();
        var originalTarget=mc.getMainRenderTarget();var originalCamera=mc.cameraEntity;
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        var inverse=new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);int[] scissorBox=new int[4];GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissorBox);
boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend=GL11.glIsEnabled(GL11.GL_BLEND), cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
float[] color=RenderSystem.getShaderColor().clone();
var modelView=RenderSystem.getModelViewStack();
var savedPoses=new PoseStackSnapshot(((dev.craftlink.mixin.PoseStackAccessor)modelView).craftlink$poses());
modelView.pushPose();modelView.setIdentity();RenderSystem.applyModelViewMatrix();
boolean pushed=false;

        try{
            rendering=true;playerCount=0;RenderSystem.disableScissor();
            if(target==null || target.width!=quality.width || target.height!=quality.height){
                if(target!=null)target.destroyBuffers();
                target=new TextureTarget(quality.width,quality.height,true,Minecraft.ON_OSX);
            }
            ((BotRenderTargetAccessor)mc).craftlink$target(target);mc.cameraEntity=bot;
            target.bindWrite(true);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
            BotCamera camera=new BotCamera();camera.configure(mc,bot,partial);
            PoseStack pose=new PoseStack();pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));pose.mulPose(Axis.YP.rotationDegrees(camera.getYRot()+180));
            Matrix4f lens=new Matrix4f().perspective((float)Math.toRadians(70),(float)quality.width/quality.height,.05f,Math.max(64,mc.options.getEffectiveRenderDistance()*16));
            RenderSystem.setProjectionMatrix(lens,VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.setInverseViewRotationMatrix(new Matrix3f(pose.last().normal()).invert());
            mc.getProfiler().push("craftlink_bot_view");pushed=true;
            mc.levelRenderer.prepareCullFrustum(pose,camera.getPosition(),lens);
            mc.levelRenderer.renderLevel(pose,partial,System.nanoTime(),false,camera,mc.gameRenderer,mc.gameRenderer.lightTexture(),lens);
            if(bot instanceof ClassroomBot humanoid){
                // Vanilla hands are a second, camera-local pass over the world image.
                target.bindWrite(true);
                modelView.setIdentity();RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(lens,VertexSorting.DISTANCE_TO_ORIGIN);
                RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
                RenderSystem.setInverseViewRotationMatrix(new Matrix3f());
                mc.gameRenderer.lightTexture().turnOnLightLayer();
                try { firstPerson.render(mc,humanoid,partial); }
                finally { mc.gameRenderer.lightTexture().turnOffLightLayer(); }
            }
            int[] pixels;try(var image=Screenshot.takeScreenshot(target)){pixels=image.getPixelsRGBA();}
            int frame=sequence=sequence==Integer.MAX_VALUE?1:sequence+1;
            var view=new ClassroomRoom.ViewSample(camera.getPosition().x,camera.getPosition().y,camera.getPosition().z,camera.getYRot(),camera.getXRot(),(double)quality.width/quality.height);
            encoder.execute(()->{try{room.publishVideo(peer,frame,VideoFrame.packet(frame,quality.width,quality.height,0,jpeg.encodeFrame(quality.width,quality.height,pixels,quality.jpegQuality)),view);error="";room.videoStatus("");}catch(Exception failure){error="BOT映像の圧縮に失敗しました";room.videoStatus(error);}finally{encoding.set(false);}});
        }catch(Exception failure){suspend(failure instanceof RuntimeException runtime?runtime:new RuntimeException(failure));encoding.set(false);}
        finally{
            if(pushed)try{mc.getProfiler().pop();}catch(RuntimeException failure){suspend(failure);}
            mc.cameraEntity=originalCamera;((BotRenderTargetAccessor)mc).craftlink$target(originalTarget);
            RenderSystem.setProjectionMatrix(projection,sorting);RenderSystem.setInverseViewRotationMatrix(inverse);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            mc.getEntityRenderDispatcher().prepare(mc.level,mc.gameRenderer.getMainCamera(),mc.crosshairPickEntity);
            mc.getBlockEntityRenderDispatcher().prepare(mc.level,mc.gameRenderer.getMainCamera(),mc.hitResult);
            sharedState.restore();
savedPoses.restore(((dev.craftlink.mixin.PoseStackAccessor)modelView).craftlink$poses());RenderSystem.applyModelViewMatrix();
if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
if(scissor)RenderSystem.enableScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);else RenderSystem.disableScissor();
RenderSystem.depthMask(depthMask);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
lastPlayerCount=playerCount;rendering=false;
nextCapture=Math.max(nextCapture,captureStart+2*(System.nanoTime()-captureStart));

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
