package dev.craftlink.addon;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActionStateTest {
    @Test void shortPressSurvivesButClickIsConsumedOnce() {
        var state = new ActionState(); String id = "addon:example:ability";
        state.update(Set.of(), Set.of(id), Set.of(id));
        assertFalse(state.held(id)); assertTrue(state.consume(id)); assertFalse(state.consume(id));
    }
    @Test void heldDoesNotRepeatClicksAndDisablingReleases() {
        var state = new ActionState(); String id = "addon:key:example.ability";
        state.update(Set.of(id), Set.of(), Set.of(id));
        assertTrue(state.held(id)); assertTrue(state.consume(id));
        state.update(Set.of(id), Set.of(), Set.of(id)); assertFalse(state.consume(id));
        state.update(Set.of(id), Set.of(id), Set.of()); assertFalse(state.held(id)); assertFalse(state.consume(id));
        state.update(Set.of(id), Set.of(id), Set.of(id)); state.clear(); assertFalse(state.held(id)); assertFalse(state.consume(id));
    }
    @Test void unlistedActionsNeverBecomeActive() {
        var state = new ActionState();
        state.update(Set.of("addon:unknown:action"), Set.of("addon:unknown:action"), Set.of("addon:example:ability"));
        assertFalse(state.held("addon:unknown:action")); assertFalse(state.consume("addon:unknown:action"));
    }
}
