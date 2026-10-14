package dev.craftlink.input;

import dev.craftlink.mixin.MinecraftActions;
import dev.craftlink.video.VideoStreamer;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;

/** Translate only Minecraft video coordinates; no OS input or arbitrary key injection. */
public final class VideoTouchInput {
    private final VideoStreamer video;
    private final ArrayDeque<TouchEvent> taps = new ArrayDeque<>();
    private TouchEvent mining;
    private boolean startMining, immediateAttack, overriding;
    private Screen dragging;
    private double guiX, guiY;
    private HitResult savedHit;
    private Entity savedEntity;
    public VideoTouchInput(VideoStreamer video) { this.video = video; }
    public void tick(Minecraft mc, List<TouchEvent> events) {
        immediateAttack = false;
        if (!video.active()) { clear(mc); return; }
        for (TouchEvent event : events) {
            if (event.phase().equals("cancel") || event.phase().equals("up")) { releaseGui(mc); mining = null; startMining = false; continue; }
            var sample = video.sample(event.frame());
            if (sample == null) continue;
            if (sample.context() == 1 && mc.screen == sample.screen()
                && sample.guiWidth() == mc.getWindow().getGuiScaledWidth() && sample.guiHeight() == mc.getWindow().getGuiScaledHeight()) {
                double x = event.x() * sample.guiWidth(), y = event.y() * sample.guiHeight();
                switch (event.phase()) {
                    case "down" -> { releaseGui(mc); dragging = mc.screen; guiX=x; guiY=y; dragging.mouseClicked(x, y, 0); }
                    case "move" -> { if (dragging != null && dragging == mc.screen) {
                        boolean consumed=dragging.mouseDragged(x, y, 0, x-guiX, y-guiY);
                        if (!consumed && Math.abs(y-guiY)>2) dragging.mouseScrolled(x, y, -(y-guiY)/16);
                        guiX=x; guiY=y;
                    } }
                    case "tap" -> { Screen screen = mc.screen; screen.mouseClicked(x, y, 0); if (screen == mc.screen) screen.mouseReleased(x, y, 0); }
                    default -> {}
                }
            } else if (sample.context() == 0 && mc.screen == null && !mc.isPaused()) {
                switch (event.phase()) {
                    case "tap" -> { if (taps.size() < 32) taps.addLast(event); }
                    case "hold" -> { mining = event; startMining = true; }
                    case "move" -> { if (mining != null) mining = event; }
                    default -> {}
                }
            }
        }
        if (mining != null && (mc.screen != null || video.sample(mining.frame()) == null)) { mining = null; startMining = false; }
        if (mc.screen != null) taps.clear();
    }
    public void interactions(Minecraft mc, MinecraftActions actions) {
        while (!taps.isEmpty()) {
            TouchEvent event = taps.removeFirst();
            if (mc.screen != null) continue;
            HitResult hit = video.pick(mc, video.sample(event.frame()), event.x(), event.y());
            if (hit == null) continue;
            withHit(mc, hit, () -> { if (hit.getType() == HitResult.Type.ENTITY) actions.craftlink$startAttack(); else actions.craftlink$startUseItem(); });
        }
        if (mining != null && startMining && mc.screen == null) {
            startMining = false;
            HitResult hit = video.pick(mc, video.sample(mining.frame()), mining.x(), mining.y());
            if (hit != null) withHit(mc, hit, () -> immediateAttack = actions.craftlink$startAttack());
        }
    }
    public boolean heldAttack() { return mining != null && video.active() && video.sample(mining.frame()) != null && !immediateAttack; }
    public void beforeMining(Minecraft mc) {
        if (!heldAttack() || mc.screen != null) return;
        HitResult hit = video.pick(mc, video.sample(mining.frame()), mining.x(), mining.y());
        if (hit == null) return;
        savedHit=mc.hitResult; savedEntity=mc.crosshairPickEntity; overriding=true;
        setHit(mc, hit);
    }
    public void afterMining(Minecraft mc) { if (overriding) { mc.hitResult=savedHit; mc.crosshairPickEntity=savedEntity; overriding=false; } }
    private static void setHit(Minecraft mc, HitResult hit) { mc.hitResult=hit; mc.crosshairPickEntity=hit instanceof EntityHitResult entity ? entity.getEntity() : null; }
    private static void withHit(Minecraft mc, HitResult hit, Runnable action) {
        HitResult original=mc.hitResult; Entity entity=mc.crosshairPickEntity;
        try { setHit(mc, hit); action.run(); } finally { mc.hitResult=original; mc.crosshairPickEntity=entity; }
    }
    private void releaseGui(Minecraft mc) { if (dragging != null && dragging == mc.screen) dragging.mouseReleased(guiX, guiY, 0); dragging=null; }
    private void clear(Minecraft mc) { releaseGui(mc); mining=null; startMining=false; taps.clear(); afterMining(mc); }
}
