package dev.craftlink.input;

import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

/** Bounded latest-state mailbox: networking never mutates Minecraft. */
public final class RemoteState {
    public static final Set<String> ALLOWED = Set.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use", "inventory", "drop", "swap", "escape");
    private Set<String> held = Set.of();
    private double yaw, pitch;
    private int slot = -1;
    private long updated;
    private final Set<String> presses = new HashSet<>();
    private final List<TouchEvent> touches = new ArrayList<>();
    public synchronized void update(Set<String> keys, double dx, double dy, int hotbar) {
        for (String key : keys) if (!held.contains(key)) presses.add(key);
        held = Set.copyOf(keys);
        yaw = Math.max(-180, Math.min(180, yaw + dx));
        pitch = Math.max(-180, Math.min(180, pitch + dy));
        if (hotbar >= 0) slot = hotbar;
        updated = System.nanoTime();
    }
    public synchronized Snapshot take() {
        if (System.nanoTime() - updated > 750_000_000L) clear();
        Snapshot snapshot = new Snapshot(held, yaw, pitch, slot, Set.copyOf(presses), List.copyOf(touches));
        yaw = pitch = 0; slot = -1;
        presses.clear(); touches.clear();
        return snapshot;
    }
    public synchronized void touch(TouchEvent event) {
        if (touches.size() >= 32) throw new IllegalArgumentException("Too many pending touches");
        touches.add(event); updated = System.nanoTime();
    }
    public synchronized void clear() { held = Set.of(); yaw = pitch = 0; slot = -1; presses.clear(); touches.clear(); }
    public record Snapshot(Set<String> held, double yaw, double pitch, int slot, Set<String> presses, List<TouchEvent> touches) {}
}
