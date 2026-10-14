package dev.craftlink.input;

import java.util.Set;

/** Bounded latest-state mailbox: networking never mutates Minecraft. */
public final class RemoteState {
    public static final Set<String> ALLOWED = Set.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use", "inventory", "drop", "swap", "escape");
    private Set<String> held = Set.of();
    private double yaw, pitch;
    private int slot = -1;
    private long updated;
    public synchronized void update(Set<String> keys, double dx, double dy, int hotbar) {
        held = Set.copyOf(keys);
        yaw = Math.max(-180, Math.min(180, yaw + dx));
        pitch = Math.max(-180, Math.min(180, pitch + dy));
        slot = hotbar;
        updated = System.nanoTime();
    }
    public synchronized Snapshot take() {
        if (System.nanoTime() - updated > 750_000_000L) clear();
        Snapshot snapshot = new Snapshot(held, yaw, pitch, slot);
        yaw = pitch = 0; slot = -1;
        return snapshot;
    }
    public synchronized void clear() { held = Set.of(); yaw = pitch = 0; slot = -1; }
    public record Snapshot(Set<String> held, double yaw, double pitch, int slot) {}
}
