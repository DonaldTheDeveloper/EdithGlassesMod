package com.example.edithglasses;

import com.example.edithglasses.feature.FaceId;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import static com.example.edithglasses.feature.HudStyle.*;

/** Give a player your own identification tag ("Law Leader", "Enemy", ...) that facial recognition shows instead of UNKNOWN. */
public class TagScreen extends Screen {
    private static final String[] PRESETS = {"Friend", "Enemy", "Law Leader", "Ally", "Suspect", "Trader"};

    private final Screen parent;
    private EditBox player, label;
    private String color = "";
    private String message = "";
    private int messageColor = DIM;

    public TagScreen(Screen parent) {
        super(Component.literal("Tag player"));
        this.parent = parent;
    }

    private int px() {
        return width / 2 - 160;
    }

    private int py() {
        return height / 2 - 84;
    }

    @Override
    protected void init() {
        int x = px() + 14, y = py();
        player = new EditBox(this.font, x, y + 32, 292, 16, Component.literal("Player"));
        player.setMaxLength(32);
        player.setHint(Component.literal("player name"));
        addRenderableWidget(player);
        label = new EditBox(this.font, x, y + 62, 292, 16, Component.literal("Tag"));
        label.setMaxLength(24);
        label.setHint(Component.literal("tag text, for example Law Leader"));
        addRenderableWidget(label);
        if (com.example.edithglasses.Glasses.target() instanceof net.minecraft.world.entity.player.Player p) {
            player.setValue(p.getName().getString());
            String[] t = FaceId.tagOf(p.getName().getString());
            if (t != null) {
                label.setValue(t[0]);
                color = t[1];
            }
            setInitialFocus(label);
        } else {
            setInitialFocus(player);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, 0xB0020C16);
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x08CFF4FF);
        panel(g, px(), py(), 320, 168, LINE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        Font font = this.font;
        int px = px(), py = py();
        text(g, font, "IDENTIFICATION", px + 14, py + 8, DIM, 0.6f);
        text(g, font, "TAG A PLAYER", px + 14, py + 16, BRIGHT, 1.0f);
        text(g, font, "Look at someone first and their name is filled in.", px + 306 - width(font, "Look at someone first and their name is filled in.", 0.5f), py + 12, DIM, 0.5f);
        text(g, font, "PLAYER", px + 14, py + 26, DIM, 0.5f);
        text(g, font, "TAG", px + 14, py + 56, DIM, 0.5f);

        int cx = px + 14;
        for (String p : PRESETS) {
            int w = width(font, p.toUpperCase(), 0.6f) + 10;
            chip(g, font, cx, py + 84, w, p.toUpperCase(), mx, my, false, LINE);
            cx += w + 4;
        }
        cx = px + 14;
        String[] names = FaceId.COLOR_NAMES;
        for (String n : names) {
            int w = 34;
            int col = n.isEmpty() ? LINE : FaceId.colorValue("", n);
            chip(g, font, cx, py + 102, w, n.isEmpty() ? "AUTO" : n.toUpperCase(), mx, my, n.equals(color), col);
            cx += w + 2;
        }
        button(g, font, px + 14, py + 128, 92, "SAVE TAG", mx, my);
        button(g, font, px + 114, py + 128, 92, "REMOVE TAG", mx, my);
        button(g, font, px + 214, py + 128, 92, "CLOSE", mx, my);
        text(g, font, message.isEmpty() ? "AUTO colours enemy tags red, friend tags green and leader tags gold." : message, px + 14, py + 150,
            message.isEmpty() ? DIM : messageColor, 0.55f);
    }

    private void chip(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, int mx, int my, boolean selected, int col) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 12;
        g.fill(x, y, x + w, y + 12, selected ? alpha(col, 0.35f) : hover ? 0x66104868 : 0x55041722);
        box(g, x, y, w, 12, selected ? col : hover ? BRIGHT : alpha(col, 0.7f));
        centered(g, font, label, x + w / 2, y + 3, selected || hover ? BRIGHT : col, 0.55f);
    }

    private void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 14;
        g.fill(x, y, x + w, y + 14, hover ? 0x99166486 : 0x77041722);
        box(g, x, y, w, 14, hover ? BRIGHT : LINE);
        centered(g, font, label, x + w / 2, y + 4, hover ? BRIGHT : LINE, 0.65f);
    }

    private boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void save() {
        String n = player.getValue().trim(), l = label.getValue().trim();
        if (n.isEmpty() || l.isEmpty()) {
            message = "Fill in the player name and the tag text.";
            messageColor = AMBER;
            return;
        }
        FaceId.setTag(n, l, color);
        message = n + " is now tagged " + l.toUpperCase() + ".";
        messageColor = GREEN;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int px = px(), py = py();
        int cx = px + 14;
        for (String p : PRESETS) {
            int w = width(this.font, p.toUpperCase(), 0.6f) + 10;
            if (in(mx, my, cx, py + 84, w, 12)) {
                label.setValue(p);
                color = "";
                return true;
            }
            cx += w + 4;
        }
        cx = px + 14;
        for (String n : FaceId.COLOR_NAMES) {
            if (in(mx, my, cx, py + 102, 34, 12)) {
                color = n;
                return true;
            }
            cx += 36;
        }
        if (in(mx, my, px + 14, py + 128, 92, 14)) {
            save();
            return true;
        }
        if (in(mx, my, px + 114, py + 128, 92, 14)) {
            String n = player.getValue().trim();
            message = n.isEmpty() ? "Type the player's name." : FaceId.removeTag(n);
            messageColor = LINE;
            return true;
        }
        if (in(mx, my, px + 214, py + 128, 92, 14)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
