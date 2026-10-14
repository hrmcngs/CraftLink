package dev.craftlink.input;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TouchStateTest {
    @Test void quickTapAndHotbarSelectionSurviveAFollowingNeutralPacket() {
        RemoteState state=new RemoteState();state.update(Set.of("jump","use"),0,0,4);state.update(Set.of(),0,0,-1);
        var snapshot=state.take();assertTrue(snapshot.held().isEmpty());assertTrue(snapshot.presses().containsAll(Set.of("jump","use")));assertEquals(4,snapshot.slot());
        assertTrue(state.take().presses().isEmpty());assertEquals(-1,state.take().slot());
    }
    @Test void touchQueueIsBoundedAndClearsOnDisconnect() {
        RemoteState state=new RemoteState();var event=new TouchEvent("tap",1,.5,.5);
        for(int i=0;i<32;i++)state.touch(event);
        assertThrows(IllegalArgumentException.class,()->state.touch(event));
        assertEquals(32,state.take().touches().size());assertTrue(state.take().touches().isEmpty());
        state.touch(event);state.clear();assertTrue(state.take().touches().isEmpty());
    }
    @Test void touchCoordinatesAndPhasesAreRestricted() {
        assertThrows(IllegalArgumentException.class,()->new TouchEvent("tap",1,Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->new TouchEvent("tap",1,1.1,0));
        assertThrows(IllegalArgumentException.class,()->new TouchEvent("keyboard",1,.5,.5));
    }
}
