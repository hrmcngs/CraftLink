package dev.craftlink.video;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
/** Restore even when a failing third-party renderer leaves extra pushes or pops behind. */
public final class PoseStackSnapshot {
    private record Entry(PoseStack.Pose pose,Matrix4f position,Matrix3f normal) {}
    private final List<Entry> entries;
    public PoseStackSnapshot(Deque<PoseStack.Pose> poses){entries=poses.stream().map(p->new Entry(p,new Matrix4f(p.pose()),new Matrix3f(p.normal()))).toList();}
    public void restore(Deque<PoseStack.Pose> poses){
        poses.clear();for(var entry:entries){entry.pose.pose().set(entry.position);entry.pose.normal().set(entry.normal);poses.addLast(entry.pose);}
    }
}
