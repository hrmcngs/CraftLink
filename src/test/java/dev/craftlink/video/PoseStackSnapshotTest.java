package dev.craftlink.video;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PoseStackSnapshotTest {
    @Test void failingRendererCannotLeaveThePcStackPoppedOrModified(){
        var stack=new PoseStack();stack.translate(1,2,3);var base=stack.last();stack.pushPose();stack.translate(4,5,6);var top=stack.last();
        var poses=new ArrayDeque<PoseStack.Pose>();poses.add(base);poses.add(top);var saved=new PoseStackSnapshot(poses);
        poses.clear();top.pose().identity();base.normal().zero();saved.restore(poses);
        assertEquals(2,poses.size());assertSame(top,poses.getLast());assertEquals(5f,top.pose().m30());assertEquals(1f,base.normal().m00());
    }
}
