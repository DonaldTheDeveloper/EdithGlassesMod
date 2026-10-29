package com.example.edithglasses.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

/**
 * Recon drone: your view leaves your head and flies forward where you look (steer with the mouse) for up to 20
 * seconds, then snaps back. Client-side only: nothing is sent to the server and your body stays where it is.
 */
public final class Recon {
    private static final int MAX_TICKS = 400;
    private static final double SPEED = 0.55;     // blocks per tick
    private static final double MAX_DIST = 90;

    private static Marker cam;
    private static int ticks;
    private static Vec3 origin = Vec3.ZERO;

    private Recon() {
    }

    public static boolean active() {
        return cam != null;
    }

    public static int secondsLeft() {
        return Math.max(0, (MAX_TICKS - ticks) / 20);
    }

    public static void toggle(Minecraft mc) {
        if (active()) stop(mc, "Recon drone recalled.");
        else start(mc);
    }

    private static void start(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        cam = new Marker(EntityTypes.MARKER, mc.level);
        origin = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        cam.setPos(origin.add(look.scale(1.2)));
        cam.setYRot(mc.player.getYRot());
        cam.setXRot(mc.player.getXRot());
        cam.setOldPosAndRot();
        ticks = 0;
        mc.setCameraEntity(cam);
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
    }

    public static void stop(Minecraft mc, String message) {
        if (cam == null) return;
        cam = null;
        mc.setCameraEntity(mc.player);
        if (message != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
    }

    public static void tick(Minecraft mc) {
        if (cam == null) return;
        if (mc.player == null || mc.level == null) {
            cam = null;
            return;
        }
        ticks++;
        cam.setOldPosAndRot();
        cam.setYRot(mc.player.getYRot());
        cam.setXRot(mc.player.getXRot());
        Vec3 next = cam.position().add(mc.player.getLookAngle().scale(SPEED));
        cam.setPos(next);
        if (ticks >= MAX_TICKS) stop(mc, "Recon drone out of power.");
        else if (next.distanceTo(origin) > MAX_DIST) stop(mc, "Recon drone at maximum range.");
    }
}
