package dev.craftlink.video;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Unproject a top-left-origin video position using the exact rendered world matrix. */
public final class ScreenRay {
    private ScreenRay() {}
    public static Vector3f direction(Matrix4f inverseViewProjection, double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) throw new IllegalArgumentException("Invalid position");
        float nx = (float)(2 * x - 1), ny = (float)(1 - 2 * y);
        Vector3f near = new Vector3f(nx, ny, -1).mulProject(inverseViewProjection);
        Vector3f far = new Vector3f(nx, ny, 1).mulProject(inverseViewProjection);
        return far.sub(near).normalize();
    }
}
