package dev.craftlink.classroom;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotTouchRayTest {
    private ClassroomRoom.Aim aim(double yaw,double pitch,double x,double y){return new ClassroomRoom.Aim(new ClassroomRoom.ViewSample(0,1.62,0,yaw,pitch,16.0/9),x,y);}
    @Test void centerMatchesMinecraftCameraOrientation(){
        var south=BotTouchRay.direction(aim(0,0,.5,.5));assertEquals(0,south.x,1e-9);assertEquals(0,south.y,1e-9);assertEquals(1,south.z,1e-9);
        var west=BotTouchRay.direction(aim(90,0,.5,.5));assertEquals(-1,west.x,1e-9);assertEquals(0,west.z,1e-9);
        var down=BotTouchRay.direction(aim(0,90,.5,.5));assertEquals(-1,down.y,1e-9);
    }
    @Test void screenEdgesRespectAspectAndVerticalFov(){
        var right=BotTouchRay.direction(aim(0,0,1,.5));assertTrue(right.x<0);assertEquals(Math.tan(Math.toRadians(35))*16/9,-right.x/right.z,1e-9);
        var up=BotTouchRay.direction(aim(0,0,.5,0));assertEquals(Math.tan(Math.toRadians(35)),up.y/up.z,1e-9);assertEquals(1,up.length(),1e-9);
    }
}
