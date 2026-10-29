package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.Glasses;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Water bucket clutch: when you fall far enough to be hurt, EDITH looks down, takes a water bucket (from the hotbar, or
 * out of your inventory) and places the water just before you land, then picks it up again and puts everything back.
 */
public final class Clutch {
    private enum Phase { IDLE, READY, PLACED, PICKUP }

    private static Phase phase = Phase.IDLE;
    private static int prevSlot = -1;
    private static int swappedFrom = -1;     // inventory index (9..35) a bucket was pulled out of
    private static int hotbarUsed = -1;
    private static int timer = 0;
    private static Vec3 placedAt = Vec3.ZERO;

    private Clutch() {
    }

    public static boolean enabled() {
        return EdithConfig.get().clutch && Glasses.equipped();
    }

    public static boolean busy() {
        return phase != Phase.IDLE;
    }

    /** Distance from your feet to the first block below (>= 0), -1 for nothing below (void), -2 if you will land in water. */
    private static double groundDistance(Minecraft mc) {
        var me = mc.player;
        Vec3 from = me.position();
        Vec3 to = from.subtract(0, 48, 0);
        var solid = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, me));
        if (solid.getType() == HitResult.Type.MISS) return -1;
        var any = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, me));
        if (any.getType() == HitResult.Type.BLOCK && !mc.level.getFluidState(any.getBlockPos()).isEmpty()
            && any.getLocation().y > solid.getLocation().y + 0.5) return -2;
        return Math.max(0, from.y - solid.getLocation().y);
    }

    public static void tick(Minecraft mc) {
        var me = mc.player;
        if (me == null || mc.level == null || mc.gameMode == null) return;
        if (!enabled() || me.isCreative() || me.isSpectator() || mc.gui.screen() != null) {
            if (phase != Phase.IDLE) finish(mc);
            return;
        }

        switch (phase) {
            case IDLE -> {
                if (me.onGround() || me.isInWater() || me.isFallFlying() || me.isPassenger() || me.getAbilities().flying) return;
                if (me.getDeltaMovement().y > -0.45) return;
                double dist = groundDistance(mc);
                if (dist < 0 || me.fallDistance + dist < 4.5) return;
                if (!takeBucket(mc)) return;
                phase = Phase.READY;
            }
            case READY -> {
                if (me.onGround() || me.isInWater()) {
                    phase = Phase.PICKUP;
                    timer = 0;
                    return;
                }
                aimDown(me);
                if (me.getMainHandItem().is(Items.BUCKET)) {   // the water went down
                    placedAt = me.position();
                    phase = Phase.PLACED;
                    timer = 0;
                    return;
                }
                double dist = groundDistance(mc);
                double speed = -me.getDeltaMovement().y;
                // the server handles this click one tick from now: the ground must be inside arm's reach by then
                if (dist >= 0 && dist - speed <= 2.7) {
                    mc.gameMode.useItem(me, InteractionHand.MAIN_HAND);
                    me.swing(InteractionHand.MAIN_HAND);
                }
            }
            case PLACED -> {
                aimDown(me);
                if (++timer > 60) {
                    finish(mc);
                    return;
                }
                if (me.onGround() || me.isInWater()) {
                    phase = Phase.PICKUP;
                    timer = 0;
                }
            }
            case PICKUP -> {
                aimDown(me);
                timer++;
                // empty bucket in hand: scoop the water back up
                if (timer >= 4 && me.getMainHandItem().is(Items.BUCKET)) {
                    mc.gameMode.useItem(me, InteractionHand.MAIN_HAND);
                    me.swing(InteractionHand.MAIN_HAND);
                }
                if (timer > 20 || (timer > 6 && me.getMainHandItem().is(Items.WATER_BUCKET))) finish(mc);
            }
            default -> {
            }
        }
    }

    private static void aimDown(net.minecraft.client.player.LocalPlayer me) {
        me.setXRot(90f);
    }

    /** Puts a water bucket in the selected hotbar slot (pulling it out of the inventory if needed). */
    private static boolean takeBucket(Minecraft mc) {
        var me = mc.player;
        var inv = me.getInventory();
        prevSlot = inv.getSelectedSlot();
        swappedFrom = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(Items.WATER_BUCKET)) {
                inv.setSelectedSlot(i);
                hotbarUsed = i;
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i).is(Items.WATER_BUCKET)) {
                // swap it with the hotbar slot you are holding
                mc.gameMode.handleContainerInput(me.containerMenu.containerId, i, prevSlot, ContainerInput.SWAP, me);
                swappedFrom = i;
                hotbarUsed = prevSlot;
                return true;
            }
        }
        return false;
    }

    private static void finish(Minecraft mc) {
        var me = mc.player;
        if (me != null && mc.gameMode != null) {
            if (swappedFrom >= 0) {
                // the bucket (full or empty) goes back to where it came from, your item comes back
                mc.gameMode.handleContainerInput(me.containerMenu.containerId, swappedFrom, hotbarUsed, ContainerInput.SWAP, me);
            }
            if (prevSlot >= 0) me.getInventory().setSelectedSlot(prevSlot);
        }
        phase = Phase.IDLE;
        swappedFrom = -1;
        prevSlot = -1;
        hotbarUsed = -1;
    }
}
