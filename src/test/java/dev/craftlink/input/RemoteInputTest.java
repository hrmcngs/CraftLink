package dev.craftlink.input;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.player.Input;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemoteInputTest {
    @Test void remoteReleaseDoesNotReleaseLocalMovementOrJump() {
        Input local = new Input(); local.forwardImpulse = 1; local.up = true; local.jumping = true;
        var held = new AtomicReference<>(Set.of("forward", "jump", "sneak"));
        RemoteInput remote = new RemoteInput(local, held::get);
        remote.tick(false, 0.3f);
        assertEquals(1, remote.forwardImpulse); assertTrue(remote.shiftKeyDown);
        assertFalse(local.shiftKeyDown); // remote never writes into the underlying input
        held.set(Set.of()); remote.tick(false, 0.3f);
        assertEquals(1, remote.forwardImpulse); assertTrue(remote.jumping); assertTrue(remote.up); assertFalse(remote.shiftKeyDown);
        assertEquals(1, local.forwardImpulse); assertTrue(local.jumping);
    }
    @Test void oppositeSourcesCancelWithoutChangingEitherSource() {
        Input local = new Input(); local.forwardImpulse = 1; local.up = true;
        RemoteInput remote = new RemoteInput(local, () -> Set.of("back", "left", "right"));
        remote.tick(false, 0.3f);
        assertEquals(0, remote.forwardImpulse); assertEquals(0, remote.leftImpulse);
        assertEquals(1, local.forwardImpulse); assertFalse(local.down);
    }
    @Test void remoteMovementRespectsSlowFactorAndDoesNotDoubleSpeed() {
        Input local = new Input(); local.forwardImpulse = 0.3f;
        RemoteInput remote = new RemoteInput(local, () -> Set.of("forward", "left"));
        remote.tick(true, 0.3f);
        assertEquals(0.3f, remote.forwardImpulse); assertEquals(0.3f, remote.leftImpulse);
        assertEquals(0, local.leftImpulse);
    }
    @Test void remoteButtonsHaveIndependentEdgesAndClear() {
        RemoteButtons buttons = new RemoteButtons();
        buttons.update(Set.of("attack", "inventory"));
        assertTrue(buttons.pressed("attack")); assertTrue(buttons.pressed("inventory"));
        buttons.update(Set.of("attack")); assertFalse(buttons.pressed("attack")); assertFalse(buttons.held("inventory"));
        buttons.clear(); assertFalse(buttons.held("attack"));
        buttons.update(Set.of("attack")); assertTrue(buttons.pressed("attack"));
    }
}
