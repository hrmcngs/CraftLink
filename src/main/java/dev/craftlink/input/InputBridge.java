package dev.craftlink.input;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class InputBridge {
    private final RemoteState state;
    private Set<String> previous = Set.of();
    private final Set<KeyMapping> owned = new HashSet<>();
    public InputBridge(RemoteState state) { this.state = state; }
    public void tick(Minecraft mc) {
        var snapshot = state.take();
        if (mc.player == null || mc.level == null || mc.screen != null) { release(); previous = Set.of(); return; }
        Set<String> held = snapshot.held();
        Map<String, KeyMapping> bindings = Map.ofEntries(
            Map.entry("forward", mc.options.keyUp), Map.entry("back", mc.options.keyDown),
            Map.entry("left", mc.options.keyLeft), Map.entry("right", mc.options.keyRight),
            Map.entry("jump", mc.options.keyJump), Map.entry("sneak", mc.options.keyShift),
            Map.entry("sprint", mc.options.keySprint), Map.entry("attack", mc.options.keyAttack), Map.entry("use", mc.options.keyUse));
        bindings.forEach((name, key) -> {
            if (held.contains(name)) {
                key.setDown(true); owned.add(key);
                if (!previous.contains(name) && (name.equals("attack") || name.equals("use"))) KeyMapping.click(key.getKey());
            } else if (owned.remove(key)) key.setDown(false);
        });
        mc.player.setYRot(mc.player.getYRot() + (float)snapshot.yaw());
        mc.player.setXRot(Math.max(-90, Math.min(90, mc.player.getXRot() + (float)snapshot.pitch())));
        if (snapshot.slot() >= 0) mc.player.getInventory().selected = snapshot.slot();
        if (edge(held, "inventory")) KeyMapping.click(mc.options.keyInventory.getKey());
        if (edge(held, "drop")) mc.player.drop(false);
        if (edge(held, "swap")) mc.player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        if (edge(held, "escape")) mc.pauseGame(false);
        previous = held;
    }
    private boolean edge(Set<String> held, String key) { return held.contains(key) && !previous.contains(key); }
    private void release() { owned.forEach(k -> k.setDown(false)); owned.clear(); }
}
