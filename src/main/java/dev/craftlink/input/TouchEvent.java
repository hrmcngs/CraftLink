package dev.craftlink.input;

import java.util.Set;

/** Normalized position within a displayed video frame; never an OS mouse event. */
public record TouchEvent(String phase, int frame, double x, double y) {
    private static final Set<String> PHASES = Set.of("tap", "hold", "down", "move", "up", "cancel");
    public TouchEvent {
        if (!PHASES.contains(phase) || frame < 0 || !Double.isFinite(x) || !Double.isFinite(y)
            || x < 0 || x > 1 || y < 0 || y > 1) throw new IllegalArgumentException("Invalid video touch");
    }
}
