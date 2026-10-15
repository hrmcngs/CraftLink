package dev.craftlink.video;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.craftlink.network.LinkServer;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Render-thread capture, single background encoder, one acknowledged network frame. */
public final class VideoStreamer {
    private final LinkServer server;
    private final ExecutorService encoder = Executors.newSingleThreadExecutor(r -> { var thread = new Thread(r, "CraftLink video encoder"); thread.setDaemon(true); return thread; });
    private final AtomicBoolean encoding = new AtomicBoolean();
    private final JpegEncoder jpegEncoder = new JpegEncoder();
    private int[] previousPixels;
    private int previousWidth, previousHeight;
    private Screen previousScreen;
    private int previousContext = -1;
    private long previousEpoch = -1, lastEncodedAt;
    private final ArrayDeque<Sample> history = new ArrayDeque<>();
    private TextureTarget target;
    private Matrix4f worldInverse;
    private long projectionAt, nextCapture;
    private int sequence;
    private volatile String error = "";
    public VideoStreamer(LinkServer server) { this.server = server; }
    public boolean active() { return server.videoActive(); }
    public String error() { return error; }
    public void projection(Matrix4f inverse) { worldInverse = new Matrix4f(inverse); projectionAt = System.nanoTime(); }
    public Sample sample(int id) {
        long now = System.nanoTime(), epoch = server.videoEpoch();
        for (Sample sample : history) if (sample.id == id && sample.epoch == epoch && now - sample.at < 2_000_000_000L) return sample;
        return null;
    }
    public void capture(Minecraft mc) {
        if (!server.canStream() || encoder.isShutdown() || encoding.get() || System.nanoTime() < nextCapture) return;
        nextCapture = System.nanoTime() + 66_666_667L; // up to 15 FPS; never queue frames
        if (!encoding.compareAndSet(false, true)) return;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        var sharedState = new VideoRenderState();
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try {
            var source = mc.getMainRenderTarget();
            if (source.viewWidth < 1 || source.viewHeight < 1) { encoding.set(false); return; }
            double scale = Math.min(1, Math.min(854.0 / source.viewWidth, 480.0 / source.viewHeight));
            int width = Math.max(1, (int)(source.viewWidth * scale)), height = Math.max(1, (int)(source.viewHeight * scale));
            if (target == null || target.width != width || target.height != height) {
                if (target != null) target.destroyBuffers();
                target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, source.viewWidth, source.viewHeight, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            int[] pixels;
            try (var image = Screenshot.takeScreenshot(target)) { pixels = image.getPixelsRGBA(); }
            long epoch = server.videoEpoch();
            boolean world = mc.player != null && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == mc.player
                && worldInverse != null && System.nanoTime() - projectionAt < 200_000_000L;
            int context = mc.screen != null ? 1 : world ? 0 : 2;
            // Keep a fresh frame for touch validation, but don't repeatedly compress static UI.
            if (epoch == previousEpoch && width == previousWidth && height == previousHeight
                && previousScreen == mc.screen && previousContext == context
                && System.nanoTime() - lastEncodedAt < 500_000_000L
                && java.util.Arrays.equals(previousPixels, pixels)) {
                encoding.set(false);
                return;
            }
            previousPixels = pixels; previousWidth = width; previousHeight = height;
            previousScreen = mc.screen; previousContext = context;
            previousEpoch = epoch; lastEncodedAt = System.nanoTime();
            int id = sequence = sequence == Integer.MAX_VALUE ? 1 : sequence + 1;
            history.addFirst(new Sample(id, epoch, System.nanoTime(), context, mc.screen,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                mc.getCameraEntity(), world ? new Matrix4f(worldInverse) : null));
            while (history.size() > 32) history.removeLast();
            encoder.execute(() -> {
                try {
                    byte[] jpeg = jpegEncoder.encodeFrame(width, height, pixels);
                    server.publishVideo(epoch, id, VideoFrame.packet(id, width, height, context, jpeg)); error = "";
                } catch (Exception failure) { error = "映像エンコード失敗: " + failure.getClass().getSimpleName(); }
                finally { encoding.set(false); }
            });
        } catch (Exception failure) { error = "映像取得失敗: " + failure.getClass().getSimpleName(); encoding.set(false); }
        finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            sharedState.restore();
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
    public HitResult pick(Minecraft mc, Sample sample, double x, double y) {
        if (sample == null || sample.context != 0 || mc.player == null || mc.level == null || sample.camera != mc.player || !mc.options.getCameraType().isFirstPerson()) return null;
        var direction = ScreenRay.direction(sample.inverse, x, y);
        Vec3 vector = new Vec3(direction.x, direction.y, direction.z);
        Vec3 origin = mc.player.getEyePosition(1);
        double blockReach = mc.player.getBlockReach(), entityReach = mc.player.getEntityReach(), reach = Math.max(blockReach, entityReach);
        Vec3 end = origin.add(vector.scale(reach));
        BlockHitResult block = mc.level.clip(new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        double distance = block.getType() == HitResult.Type.MISS ? reach * reach : origin.distanceToSqr(block.getLocation());
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(mc.player, origin, end,
            mc.player.getBoundingBox().expandTowards(vector.scale(reach)).inflate(1), e -> !e.isSpectator() && e.isPickable(), Math.min(distance, entityReach * entityReach));
        if (entity != null && origin.distanceToSqr(entity.getLocation()) <= entityReach * entityReach) return entity;
        if (origin.distanceToSqr(block.getLocation()) <= blockReach * blockReach) return block;
        return BlockHitResult.miss(end, net.minecraft.core.Direction.getNearest(direction.x, direction.y, direction.z), net.minecraft.core.BlockPos.containing(end));
    }
    public void close() { encoder.shutdownNow(); if (target != null) { target.destroyBuffers(); target = null; } history.clear(); }
    public record Sample(int id, long epoch, long at, int context, Screen screen, int guiWidth, int guiHeight, Entity camera, Matrix4f inverse) {}
}
