package com.example.edithglasses.feature;

import com.example.edithglasses.mixin.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Projects world positions onto the (GUI scaled) screen using the current camera. */
public final class Project {
    private final Vec3 pos, f, right, up;
    private final double tan, aspect;
    private final int w, h;

    public Project(int w, int h) {
        Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
        this.pos = cam.position();
        this.f = Vec3.directionFromRotation(cam.xRot(), cam.yRot());
        this.right = f.cross(new Vec3(0, 1, 0)).normalize();
        this.up = right.cross(f);
        this.tan = Math.tan(Math.toRadians(((CameraAccessor) cam).edith$fov()) / 2.0);
        this.aspect = (double) w / h;
        this.w = w;
        this.h = h;
    }

    /** {screenX, screenY, depth} or null when behind the camera. */
    public double[] at(Vec3 p) {
        Vec3 d = p.subtract(pos);
        double z = d.dot(f);
        if (z < 0.3) return null;
        return new double[]{w / 2.0 + (d.dot(right) / (z * tan * aspect)) * (w / 2.0), h / 2.0 - (d.dot(up) / (z * tan)) * (h / 2.0), z};
    }
}
