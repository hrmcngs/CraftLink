package dev.craftlink.input;

import dev.craftlink.mixin.MinecraftActions;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;

/** Remote intent is applied on the game thread, without synthetic key presses. */
public final class InputBridge {
    private final RemoteState state;
    private final RemoteButtons buttons = new RemoteButtons();
    public final VideoTouchInput videoTouch;
    private LocalPlayer controlled;
    private RemoteInput movement;
    private boolean immediateAttack, remoteUsing;
    public InputBridge(RemoteState state, dev.craftlink.video.VideoStreamer video) { this.state = state; this.videoTouch = new VideoTouchInput(video); }
    private boolean active(Minecraft mc) {
        return mc.player != null && mc.player == controlled && mc.level != null && mc.gameMode != null
            && mc.screen == null && mc.getOverlay() == null && !mc.isPaused();
    }
    public boolean held(String action) { return active(Minecraft.getInstance()) && buttons.held(action); }
    private Set<String> movementButtons() {
        if (!active(Minecraft.getInstance())) return Set.of();
        var held = new java.util.HashSet<>(buttons.held());
        for (String key : Set.of("jump", "forward", "back", "left", "right")) if (buttons.pressed(key)) held.add(key);
        return held;
    }
    public void tick(Minecraft mc) {
        var snapshot = state.take();
        dev.craftlink.addon.AddonActions.tick(mc, snapshot, mc.player != null && mc.level != null && mc.gameMode != null
            && mc.screen == null && mc.getOverlay() == null && !mc.isPaused());
        videoTouch.tick(mc, snapshot.touches());
        if (mc.screen != null && snapshot.presses().contains("escape")) { mc.screen.onClose(); buttons.clear(); return; }
        immediateAttack = false;
        if (controlled != mc.player) {
            if (controlled != null && controlled.input == movement) controlled.input = movement.local();
            controlled = mc.player; movement = null; remoteUsing = false; buttons.clear();
        }
        if (controlled != null && controlled.input != movement) {
            // Preserve another mod's replacement Input as the local source.
            movement = new RemoteInput(controlled.input, this::movementButtons);
            controlled.input = movement;
        }
        if (controlled != null && mc.gameMode != null) {
            // A GUI or a lost connection must release only remote ownership.
            // Reading physical ownership here never changes the key or its click queue.
            if (remoteUsing && (!active(mc) || !snapshot.held().contains("use"))) {
                if (controlled.isUsingItem() && !mc.options.keyUse.isDown()) mc.gameMode.releaseUsingItem(controlled);
                remoteUsing = false;
            }
            if (!active(mc) && buttons.held("attack") && !mc.options.keyAttack.isDown()) mc.gameMode.stopDestroyBlock();
        }
        if (!active(mc)) { buttons.clear(); return; }
        buttons.update(snapshot.held(), snapshot.presses());
        mc.player.setYRot(mc.player.getYRot() + (float)snapshot.yaw());
        mc.player.setXRot(Math.max(-90, Math.min(90, mc.player.getXRot() + (float)snapshot.pitch())));
        if (snapshot.slot() >= 0) {
            if (mc.player.isSpectator()) mc.gui.getSpectatorGui().onHotbarSelected(snapshot.slot());
            else mc.player.getInventory().selected = snapshot.slot();
        }
        if (buttons.pressed("drop") && !mc.player.isSpectator() && mc.player.drop(false)) mc.player.swing(InteractionHand.MAIN_HAND);
        if (buttons.pressed("swap") && !mc.player.isSpectator()) mc.player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        if (buttons.pressed("inventory")) {
            if (mc.gameMode.isServerControlledInventory()) mc.player.sendOpenInventory();
            else { mc.getTutorial().onOpenInventory(); mc.setScreen(new InventoryScreen(mc.player)); }
        } else if (buttons.pressed("escape")) mc.pauseGame(false);
    }
    /** Called once at Minecraft's ordinary interaction processing point. */
    public void interactions(Minecraft mc, MinecraftActions actions) {
        if (!active(mc)) return;
        videoTouch.interactions(mc, actions);
        if (!active(mc)) return;
        if (buttons.pressed("attack") && !mc.player.isUsingItem()) immediateAttack = actions.craftlink$startAttack();
        if ((buttons.held("use") || buttons.pressed("use")) && !mc.player.isUsingItem()
            && (buttons.pressed("use") || actions.craftlink$useDelay() == 0)) {
            actions.craftlink$startUseItem(); remoteUsing = mc.player.isUsingItem();
        }
    }
    public boolean continueAttack() { return held("attack") && !immediateAttack || active(Minecraft.getInstance()) && videoTouch.heldAttack(); }
}
