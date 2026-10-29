package com.example.edithglasses.feature;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small drawing helpers for the holographic HUD: thin lines, arcs with tick marks, corner brackets, tiny text. */
public final class HudStyle {
    public static final int BRIGHT = 0xFFD8F8FF;
    public static final int LINE = 0xCC5CD0F0;
    public static final int DIM = 0x664FA8C8;
    public static final int FAINT = 0x33407890;
    public static final int RED = 0xFFFF4D4D;
    public static final int AMBER = 0xFFFFC857;
    public static final int GREEN = 0xFF6FFFA0;

    private HudStyle() {
    }

    public static int alpha(int color, float a) {
        int al = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * a)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    public static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c) {
        if (y0 == y1) {
            g.fill(Math.min(x0, x1), y0, Math.max(x0, x1) + 1, y0 + 1, c);
        } else if (x0 == x1) {
            g.fill(x0, Math.min(y0, y1), x0 + 1, Math.max(y0, y1) + 1, c);
        } else {
            int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
            for (int i = 0; i <= n; i++) {
                int x = x0 + (x1 - x0) * i / n, y = y0 + (y1 - y0) * i / n;
                g.fill(x, y, x + 1, y + 1, c);
            }
        }
    }

    public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** Four corner brackets only, like a targeting frame. */
    public static void corners(GuiGraphicsExtractor g, int x, int y, int w, int h, int len, int c) {
        g.fill(x, y, x + len, y + 1, c);
        g.fill(x, y, x + 1, y + len, c);
        g.fill(x + w - len, y, x + w, y + 1, c);
        g.fill(x + w - 1, y, x + w, y + len, c);
        g.fill(x, y + h - 1, x + len, y + h, c);
        g.fill(x, y + h - len, x + 1, y + h, c);
        g.fill(x + w - len, y + h - 1, x + w, y + h, c);
        g.fill(x + w - 1, y + h - len, x + w, y + h, c);
    }

    /** Dark glass panel with a bright top edge and a notched corner. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        g.fillGradient(x, y, x + w, y + h, 0xAA08243C, 0x8805141F);
        g.fill(x, y, x + w, y + 1, accent);
        g.fill(x, y, x + 1, y + h, alpha(accent, 0.6f));
        g.fill(x + w - 6, y + h - 1, x + w, y + h, accent);
        g.fill(x + w - 1, y + h - 6, x + w, y + h, accent);
    }

    /** Text at a fraction of the normal size. */
    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    public static int width(Font font, String s, float scale) {
        return Math.round(font.width(s) * scale);
    }

    public static void centered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color, float scale) {
        text(g, font, s, cx - width(font, s, scale) / 2, y, color, scale);
    }

    /** Points of an arc (degrees, 0 = right, 90 = up) with tick marks; returns nothing, draws directly. */
    public static void arc(GuiGraphicsExtractor g, int cx, int cy, int r, double a0, double a1, int color, int tickEvery, int majorEvery) {
        for (double a = a0; a <= a1; a += 0.8) {
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(r * Math.cos(rad)), y = cy - (int) Math.round(r * Math.sin(rad));
            g.fill(x, y, x + 1, y + 1, color);
        }
        for (int a = (int) Math.ceil(a0); a <= a1; a++) {
            if (a % tickEvery != 0) continue;
            int len = a % majorEvery == 0 ? 6 : 3;
            double rad = Math.toRadians(a);
            for (int k = 1; k <= len; k++) {
                int x = cx + (int) Math.round((r + k) * Math.cos(rad)), y = cy - (int) Math.round((r + k) * Math.sin(rad));
                g.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    /** Filled part of an arc (thicker, bright). */
    public static void arcFill(GuiGraphicsExtractor g, int cx, int cy, int r, double a0, double a1, int color) {
        for (double a = a0; a <= a1; a += 0.5) {
            double rad = Math.toRadians(a);
            for (int k = -2; k <= -1; k++) {
                int x = cx + (int) Math.round((r + k) * Math.cos(rad)), y = cy - (int) Math.round((r + k) * Math.sin(rad));
                g.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    public static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int color, int step) {
        for (int a = 0; a < 360; a += step) {
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(r * Math.cos(rad)), y = cy - (int) Math.round(r * Math.sin(rad));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
