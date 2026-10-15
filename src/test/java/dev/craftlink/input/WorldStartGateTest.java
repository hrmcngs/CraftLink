package dev.craftlink.input;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorldStartGateTest {
    @Test void playerBeforeTerrainScreenFinishesDoesNotStartTheRoom(){
        var gate=new WorldStartGate();var world=new Object();for(int i=0;i<100;i++)assertFalse(gate.tick(world,false));
        for(int i=0;i<19;i++)assertFalse(gate.tick(world,true));assertTrue(gate.tick(world,true));
    }
    @Test void leavingOrSwitchingWorldRequiresAFreshSettlingPeriod(){
        var gate=new WorldStartGate();var old=new Object();for(int i=0;i<20;i++)gate.tick(old,true);
        assertFalse(gate.tick(null,false));var next=new Object();assertFalse(gate.tick(next,true));
        for(int i=0;i<19;i++)gate.tick(next,true);assertTrue(gate.tick(next,true));assertFalse(gate.tick(new Object(),true));
    }
}
