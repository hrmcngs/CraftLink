package dev.craftlink.classroom;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassroomAreaTest {
    @Test void landingBelowAFlyingHostDoesNotRecallTheBot() {
        assertFalse(ClassroomBots.outsideArea(3, 64, 3, 0, 0, -64));
        assertFalse(ClassroomBots.outsideArea(3, -20, 3, 0, 0, -64));
    }
    @Test void horizontalBoundaryAndVoidStillRecallTheBot() {
        assertFalse(ClassroomBots.outsideArea(32, 64, 0, 0, 0, -64));
        assertTrue(ClassroomBots.outsideArea(33, 64, 0, 0, 0, -64));
        assertTrue(ClassroomBots.outsideArea(0, -69, 0, 0, 0, -64));
    }
}
