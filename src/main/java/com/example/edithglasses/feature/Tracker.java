package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.Glasses;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Live player tracking: follows one named player with a HUD marker, distance and health while they are inside the area
 * the server sends you (your render/tracking distance). When they leave it, the last seen position stays on the HUD.
 * This only uses what the server already sent your game; it cannot find players far away.
 */
public final class Tracker {
    public static volatile boolean visible = false;
    public static volatile double x, y, z;
    public static volatile String dimension = "";
    public static volatile long lastSeen = 0;
    public static volatile float health = -1;
    private static boolean wasVisible = false;

    private Tracker() {
    }

    public static String name() {
        String n = EdithConfig.get().trackedPlayer;
        return n == null ? "" : n.trim();
    }

    public static void set(String name) {
        EdithConfig.get().trackedPlayer = name == null ? "" : name.trim();
        EdithConfig.save();
        visible = false;
        wasVisible = false;
        lastSeen = 0;
    }

    public static void tick(Minecraft mc) {
        String n = name();
        if (n.isEmpty() || mc.level == null || mc.player == null) {
            visible = false;
            return;
        }
        Player found = null;
        for (Player p : mc.level.players()) {
            if (p != mc.player && p.getName().getString().equalsIgnoreCase(n)) {
                found = p;
                break;
            }
        }
        if (found != null) {
            visible = true;
            x = found.getX();
            y = found.getY();
            z = found.getZ();
            dimension = Waypoints.dimension(mc);
            lastSeen = System.currentTimeMillis();
            health = found.getHealth();
            if (!wasVisible && Glasses.equipped(mc.player)) Alerts.push(mc, "TRACKING: " + found.getName().getString() + " in range", 0xFF60D0FF, null);
        } else {
            visible = false;
            if (wasVisible && Glasses.equipped(mc.player)) Alerts.push(mc, "TRACKING: " + n + " out of range", 0xFFFFC857, null);
        }
        wasVisible = visible;
    }

    /** True when there is something to draw: the player is in range, or a last seen position exists in this dimension. */
    public static boolean hasPosition(Minecraft mc) {
        return !name().isEmpty() && lastSeen > 0 && dimension.equals(Waypoints.dimension(mc));
    }

    public static String status(Minecraft mc) {
        if (name().isEmpty()) return "Not tracking anyone.";
        if (visible) return "Tracking " + name() + ": in range, " + Math.round(Math.hypot(x - mc.player.getX(), z - mc.player.getZ())) + " m away.";
        if (lastSeen == 0) return "Tracking " + name() + ": not seen yet (they must be inside your render distance).";
        long s = (System.currentTimeMillis() - lastSeen) / 1000;
        return "Tracking " + name() + ": last seen " + s + " s ago, " + Math.round(Math.hypot(x - mc.player.getX(), z - mc.player.getZ())) + " m from here.";
    }
}
