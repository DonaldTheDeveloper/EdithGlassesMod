package com.example.edithglasses;

import com.example.edithglasses.HudLayout.El;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import static com.example.edithglasses.feature.HudStyle.*;

/** Drag the parts of the EDITH HUD where you want them. The real HUD is drawn underneath, boxes show what can be grabbed. */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private El selected = null, dragging = null;
    private int grabX, grabY, startDx, startDy;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Move HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        HudLayout.setEditing(true);
    }

    private int[] boxOf(El e) {
        int[] r = HudLayout.rect(e, width, height);
        int[] o = HudLayout.clamp(e, HudLayout.offset(e)[0], HudLayout.offset(e)[1], width, height);
        return new int[]{r[0] + o[0], r[1] + o[1], r[2], r[3]};
    }

    private El at(double mx, double my) {
        El[] all = El.values();
        for (int i = all.length - 1; i >= 0; i--) {
            int[] b = boxOf(all[i]);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return all[i];
        }
        return null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, 0x40020C16);
    }

    private int panelX() {
        return width / 2 - 150;
    }

    private int panelY() {
        return height / 2 + 24;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        extractBackground(g, mx, my, delta);
        Font font = this.font;
        for (El e : El.values()) {
            int[] b = boxOf(e);
            boolean sel = e == selected;
            boolean hover = e == at(mx, my) && dragging == null;
            int col = sel ? AMBER : hover ? BRIGHT : LINE;
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], alpha(col, sel ? 0.18f : 0.08f));
            box(g, b[0], b[1], b[2], b[3], alpha(col, sel ? 1f : 0.8f));
            String tag = e.label.toUpperCase();
            int tw = width(font, tag, 0.6f) + 6;
            g.fill(b[0], b[1] - 9, b[0] + tw, b[1], alpha(0xFF041722, 0.9f));
            text(g, font, tag, b[0] + 3, b[1] - 7, col, 0.6f);
        }
        int px = panelX(), py = panelY();
        panel(g, px, py, 300, 52, LINE);
        centered(g, font, "MOVE HUD", width / 2, py + 5, BRIGHT, 0.9f);
        centered(g, font, "Drag a box to move it. Arrow keys nudge the selected one (Shift = 10).", width / 2, py + 17, DIM, 0.6f);
        centered(g, font, "R resets the selected one. Esc saves and closes.", width / 2, py + 25, DIM, 0.6f);
        button(g, font, px + 14, py + 35, 130, "RESET ALL", mx, my);
        button(g, font, px + 156, py + 35, 130, "DONE", mx, my);
    }

    private void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, int mx, int my) {
        boolean hover = in(mx, my, x, y, w);
        g.fill(x, y, x + w, y + 12, hover ? 0x99166486 : 0x77041722);
        box(g, x, y, w, 12, hover ? BRIGHT : LINE);
        centered(g, font, label, x + w / 2, y + 3, hover ? BRIGHT : LINE, 0.7f);
    }

    private boolean in(double mx, double my, int x, int y, int w) {
        return mx >= x && mx < x + w && my >= y && my < y + 12;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int px = panelX(), py = panelY();
        if (in(mx, my, px + 14, py + 35, 130)) {
            HudLayout.resetAll();
            return true;
        }
        if (in(mx, my, px + 156, py + 35, 130)) {
            onClose();
            return true;
        }
        El e = at(mx, my);
        selected = e;
        if (e != null) {
            dragging = e;
            grabX = (int) mx;
            grabY = (int) my;
            startDx = HudLayout.offset(e)[0];
            startDy = HudLayout.offset(e)[1];
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            HudLayout.set(dragging, startDx + (int) event.x() - grabX, startDy + (int) event.y() - grabY, width, height);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) EdithConfig.save();
        dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selected != null) {
            int step = (event.modifiers() & 1) != 0 ? 10 : 1;   // shift
            int dx = 0, dy = 0;
            switch (event.key()) {
                case 263 -> dx = -step;   // left
                case 262 -> dx = step;    // right
                case 265 -> dy = -step;   // up
                case 264 -> dy = step;    // down
                case 82 -> {              // R
                    HudLayout.set(selected, 0, 0, width, height);
                    EdithConfig.save();
                    return true;
                }
                default -> {
                }
            }
            if (dx != 0 || dy != 0) {
                HudLayout.set(selected, HudLayout.offset(selected)[0] + dx, HudLayout.offset(selected)[1] + dy, width, height);
                EdithConfig.save();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        HudLayout.setEditing(false);
        EdithConfig.save();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        HudLayout.setEditing(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
