package com.example.edithglasses;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/** Shared state: are the glasses on, and how far is the ctrl+scroll zoom turned. */
public final class Glasses {
    public static final float MAX_ZOOM = 12.0F;
    private static boolean cameraZoom = false;

    /** The camera mode zooms further (24x, like a long lens). */
    public static void setCameraZoomMode(boolean on) {
        cameraZoom = on;
        if (!on && zoom > MAX_ZOOM) zoom = MAX_ZOOM;
    }

    public static void resetZoom() {
        zoom = 1.0F;
    }
    private static float zoom = 1.0F;
    private static float smoothZoom = 1.0F;

    private Glasses() {
    }

    private static boolean localWorn = false;

    /** Client-side "worn" flag set by /wear (only you see it; the server /hat makes it visible to others). */
    public static boolean localWorn() {
        return localWorn;
    }

    public static void setLocalWorn(boolean on) {
        localWorn = on;
    }

    /** Worn on the head (really or via /wear), or held in either hand. */
    public static boolean equipped(LocalPlayer p) {
        return p != null && (localWorn || p.getItemBySlot(EquipmentSlot.HEAD).is(Items.SPYGLASS)
            || p.getMainHandItem().is(Items.SPYGLASS) || p.getOffhandItem().is(Items.SPYGLASS));
    }

    public static boolean equipped() {
        return equipped(Minecraft.getInstance().player);
    }

    public static void scroll(double amount) {
        zoom = Mth.clamp(zoom * (float) Math.pow(1.25, amount), 1.0F, cameraZoom ? 24.0F : MAX_ZOOM);
        if (zoom < 1.02F) zoom = 1.0F;
    }

    /** Called every client tick: eases the displayed zoom and drops it when the glasses come off. */
    public static void tick(Minecraft mc) {
        if (!equipped(mc.player)) zoom = 1.0F;
        updateTarget(mc);
        smoothZoom += (zoom - smoothZoom) * 0.35F;
        if (Math.abs(smoothZoom - zoom) < 0.005F) smoothZoom = zoom;
    }

    private static net.minecraft.world.entity.Entity target;

    /** Entity under the crosshair within the scan range (not hidden behind blocks), or null. */
    public static net.minecraft.world.entity.Entity target() {
        return target;
    }

    private static void updateTarget(Minecraft mc) {
        target = null;
        var p = mc.player;
        if (p == null || mc.level == null || !equipped(p)) return;
        double range = EdithConfig.get().scanRange;
        var from = p.getEyePosition();
        var view = p.getViewVector(1.0F);
        var to = from.add(view.scale(range));
        var box = p.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
            p, from, to, box, e -> !e.isSpectator() && e.isPickable(), range * range);
        if (hit == null) return;
        var block = p.pick(range, 1.0F, false);
        if (block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
            && from.distanceToSqr(block.getLocation()) < from.distanceToSqr(hit.getLocation())) return;
        target = hit.getEntity();
    }

    public static boolean thermalActive() {
        return EdithConfig.get().thermal && equipped();
    }

    private static boolean lastNight = false;

    /** Night vision is on in the config AND the glasses are worn or held. */
    public static boolean nightActive() {
        return EdithConfig.get().nightVision && equipped();
    }

    /** True once whenever night vision just switched on or off, so the light map gets rebuilt. */
    public static boolean nightChanged() {
        boolean now = nightActive();
        if (now == lastNight) return false;
        lastNight = now;
        return true;
    }

    public static float zoom() {
        return smoothZoom;
    }

    public static float targetZoom() {
        return zoom;
    }
}
