package dev.craftlink.input;

import net.minecraft.client.player.Input;
import java.util.Set;
import java.util.function.Supplier;

/** Independent input source; never reads or writes keyboard mappings. */
public final class RemoteInput extends Input {
    private final Input local;
    private final Supplier<Set<String>> remote;
    public RemoteInput(Input local, Supplier<Set<String>> remote) { this.local = local; this.remote = remote; }
    public Input local() { return local; }
    @Override public void tick(boolean slow, float sneakSpeed) {
        local.tick(slow, sneakSpeed);
        Set<String> held = remote.get();
        float scale = slow ? sneakSpeed : 1;
        float forward = axis(held, "forward", "back") * scale;
        float sideways = axis(held, "left", "right") * scale;
        forwardImpulse = mergeAxis(local.forwardImpulse, forward, scale);
        leftImpulse = mergeAxis(local.leftImpulse, sideways, scale);
        up = local.up || held.contains("forward"); down = local.down || held.contains("back");
        left = local.left || held.contains("left"); right = local.right || held.contains("right");
        jumping = local.jumping || held.contains("jump");
        shiftKeyDown = local.shiftKeyDown || held.contains("sneak");
    }
    private static int axis(Set<String> held, String positive, String negative) {
        return (held.contains(positive) ? 1 : 0) - (held.contains(negative) ? 1 : 0);
    }
    private static float mergeAxis(float local, float remote, float scale) {
        if (remote == 0) return local;
        float limit = Math.max(Math.abs(local), scale);
        return Math.max(-limit, Math.min(limit, local + remote));
    }
}
