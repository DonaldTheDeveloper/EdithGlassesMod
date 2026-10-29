package com.example.edithglasses.feature;

import com.example.edithglasses.Glasses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** HUD parts for the extra features: alerts, waypoint marker, subtitles, thermal/recon labels, photo overlay. */
public final class HudExtras {
    private static final int BRIGHT = 0xFFE4F6FF;
    private static final int LINE = 0xCC8CD2FF;
    private static final int WARN = 0xFFFFC857;

    private HudExtras() {
    }

    public static void draw(GuiGraphicsExtractor g, Font font, int w, int h, int l, int t, int b, int cx, int stripHalf, int stripY, float heading) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return;

        OreScan.draw(g, font, w, h);

        // alerts under the compass
        int ay = stripY + 50;
        for (Alerts.Alert a : Alerts.visible()) {
            boolean blink = (System.currentTimeMillis() / 250) % 2 == 0;
            String s = "! " + a.text() + " !";
            int tw = font.width(s);
            g.fill(cx - tw / 2 - 6, ay - 2, cx + tw / 2 + 6, ay + 10, 0x99200808);
            g.text(font, s, cx - tw / 2, ay, blink ? a.color() : BRIGHT, false);
            ay += 14;
        }

        // tracked waypoint on the compass strip
        Waypoints.WP wp = Waypoints.tracked(mc);
        if (wp != null) {
            double dx = wp.x - p.getX(), dz = wp.z - p.getZ();
            float target = Mth.positiveModulo((float) Math.toDegrees(Math.atan2(-dx, dz)) + 180f, 360f);
            float delta = Mth.wrapDegrees(target - heading);
            int x = cx + Math.round(Mth.clamp(delta, -60f, 60f) * (stripHalf / 60.0F));
            String label = wp.name + "  " + Math.round(Math.hypot(dx, dz)) + " m";
            g.fill(x - 1, stripY + 5, x + 2, stripY + 12, WARN);
            if (Math.abs(delta) > 60f) g.text(font, delta > 0 ? ">" : "<", x + (delta > 0 ? -8 : 4), stripY + 4, WARN, false);
            g.text(font, label, Mth.clamp(x - font.width(label) / 2, 4, w - font.width(label) - 4), stripY + 30, WARN, false);
        }

        // live tracked player
        if (Tracker.hasPosition(mc)) {
            double dx = Tracker.x - p.getX(), dz = Tracker.z - p.getZ();
            float target = Mth.positiveModulo((float) Math.toDegrees(Math.atan2(-dx, dz)) + 180f, 360f);
            float delta = Mth.wrapDegrees(target - heading);
            int x = cx + Math.round(Mth.clamp(delta, -60f, 60f) * (stripHalf / 60.0F));
            boolean live = Tracker.visible;
            int col = live ? 0xFF60D0FF : 0xFF7F9FB0;
            String label = Tracker.name() + "  " + Math.round(Math.hypot(dx, dz)) + " m"
                + (live ? (Tracker.health >= 0 ? "  " + Math.round(Tracker.health) + " hp" : "")
                        : "  (last seen " + (System.currentTimeMillis() - Tracker.lastSeen) / 1000 + " s ago)");
            g.fill(x - 1, stripY + 5, x + 2, stripY + 12, col);
            if (Math.abs(delta) > 60f) g.text(font, delta > 0 ? ">" : "<", x + (delta > 0 ? -8 : 4), stripY + 4, col, false);
            g.text(font, label, Mth.clamp(x - font.width(label) / 2, 4, w - font.width(label) - 4), stripY + 40, col, false);
        }

        // translated chat as subtitles
        int sy = b - 96;
        var subs = Subtitles.visible();
        for (int i = subs.size() - 1; i >= 0; i--) {
            String s = subs.get(i).text();
            int tw = font.width(s);
            g.fill(cx - tw / 2 - 5, sy - 2, cx + tw / 2 + 5, sy + 10, 0x88000A1A);
            g.text(font, s, cx - tw / 2, sy, BRIGHT, false);
            sy -= 14;
        }

        // photo overlay
        if (Photo.overlay()) {
            g.text(font, "● REC", r(w, font, "● REC") , t + 6, 0xFFFF5050, false);
            String st = Photo.stamp(mc);
            g.text(font, st, w - font.width(st) - l, b - 6, BRIGHT, false);
        }
    }

    private static int r(int w, Font font, String s) {
        return w - font.width(s) - 40;
    }

    /** Drawn even without the HUD: the camera flash after a photo. */
    public static void flash(GuiGraphicsExtractor g, int w, int h) {
        float a = Photo.flashAlpha();
        if (a > 0f) g.fill(0, 0, w, h, ((int) (a * 150) << 24) | 0xFFFFFF);
    }
}
