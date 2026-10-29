package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

/** Photo mode: draws a "REC" overlay for a frame, takes a normal screenshot, then flashes. */
public final class Photo {
    private static int stage = 0;
    private static int flash = 0;

    private Photo() {
    }

    public static void request() {
        if (stage == 0) stage = 1;
    }

    /** Take the photo after this many ticks (lets a menu close first). */
    public static void requestAfter(int ticks) {
        if (stage == 0) stage = -ticks;
    }

    /** True for the frames whose HUD should show the recording overlay. */
    public static boolean overlay() {
        return stage > 0;
    }

    public static float flashAlpha() {
        return flash > 0 ? flash / 8.0f : 0f;
    }

    public static void tick(Minecraft mc) {
        if (flash > 0) flash--;
        if (stage == 0 || mc.level == null) return;
        if (stage < 0) {
            stage++;
            if (stage == 0) stage = 1;
            return;
        }
        stage++;
        if (stage == 4) { // two rendered frames with the overlay have passed
            Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(),
                msg -> mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(Component.literal("EDITH: photo saved. ").append(msg))));
            flash = 8;
            stage = 0;
        }
    }

    public static String stamp(Minecraft mc) {
        var p = mc.player;
        String t = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return p == null ? t : t + "   " + Math.round(p.getX()) + " " + Math.round(p.getY()) + " " + Math.round(p.getZ());
    }
}
