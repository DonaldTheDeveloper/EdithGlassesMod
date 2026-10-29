package com.example.edithglasses;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Where the movable HUD parts sit: a saved pixel offset per part, applied as a translation while it is drawn. */
public final class HudLayout {
    public enum El {
        TITLE("Title banner"), TELEMETRY("Telemetry"), RADAR("Radar"), HOLOMAP("Hologram map"),
        TARGET("Target card"), TABS("Mode tabs"), HOTBAR("Hotbar"), ASSISTANT("Assistant panel");

        public final String label;

        El(String label) {
            this.label = label;
        }
    }

    private static boolean editing = false;

    private HudLayout() {
    }

    public static boolean editing() {
        return editing;
    }

    public static void setEditing(boolean on) {
        editing = on;
    }

    /** Default position and size {x, y, w, h} of a part, for a screen of w x h GUI pixels. */
    public static int[] rect(El e, int w, int h) {
        int cx = w / 2, m = 8;
        return switch (e) {
            case TITLE -> new int[]{cx - 62, 5, 124, 12};
            case TELEMETRY -> new int[]{10, 50, 72, 66};
            case RADAR -> new int[]{w - m - 78, 32, 68, 70};
            case HOLOMAP -> new int[]{w - m - 146, 108, 140, 96};
            case TARGET -> new int[]{m + 6, h - m - 120, 104, 60};
            case TABS -> new int[]{cx - 143, h - 54, 287, 14};
            case HOTBAR -> new int[]{cx - 115, h - 40, 205, 32};
            case ASSISTANT -> {
                int pw = Math.min(w / 2, 300) + 16;
                yield new int[]{cx - pw / 2, h - m - 62 - 44, pw, 44};
            }
        };
    }

    public static int[] offset(El e) {
        int[] o = EdithConfig.get().hudOffsets.get(e.name());
        return o == null || o.length < 2 ? new int[]{0, 0} : o;
    }

    /** Keeps the part fully on screen. */
    public static int[] clamp(El e, int dx, int dy, int w, int h) {
        int[] r = rect(e, w, h);
        return new int[]{Mth.clamp(dx, -r[0], Math.max(-r[0], w - r[0] - r[2])), Mth.clamp(dy, -r[1], Math.max(-r[1], h - r[1] - r[3]))};
    }

    public static void set(El e, int dx, int dy, int w, int h) {
        int[] c = clamp(e, dx, dy, w, h);
        if (c[0] == 0 && c[1] == 0) EdithConfig.get().hudOffsets.remove(e.name());
        else EdithConfig.get().hudOffsets.put(e.name(), c);
    }

    public static void resetAll() {
        EdithConfig.get().hudOffsets.clear();
        EdithConfig.save();
    }

    public static void begin(GuiGraphicsExtractor g, El e) {
        int[] o = clamp(e, offset(e)[0], offset(e)[1], g.guiWidth(), g.guiHeight());
        g.pose().pushMatrix();
        g.pose().translate(o[0], o[1]);
    }

    public static void end(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }
}
