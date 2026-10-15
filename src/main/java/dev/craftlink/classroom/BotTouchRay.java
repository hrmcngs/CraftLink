package dev.craftlink.classroom;

import org.joml.Vector3d;

/** Matches the BOT camera's 70 degree vertical FOV and Minecraft yaw/pitch convention. */
public final class BotTouchRay {
    private BotTouchRay() {}
    public static Vector3d direction(ClassroomRoom.Aim aim){
        var view=aim.view();double yaw=Math.toRadians(view.yaw()),pitch=Math.toRadians(view.pitch());
        var forward=new Vector3d(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        var right=new Vector3d(-Math.cos(yaw),0,-Math.sin(yaw));
        var up=new Vector3d(right).cross(forward);
        double tangent=Math.tan(Math.toRadians(35));
        return forward.add(right.mul((2*aim.x()-1)*tangent*view.aspect())).add(up.mul((1-2*aim.y())*tangent)).normalize();
    }
}
