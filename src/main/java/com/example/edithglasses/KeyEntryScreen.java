package com.example.edithglasses;

import com.example.edithglasses.ai.Ais;
import com.example.edithglasses.ai.Keys;
import com.example.edithglasses.ai.Models;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import static com.example.edithglasses.feature.HudStyle.*;

/** Paste an API key for one AI service. The key is hidden while typing and is only ever written to config/edith-keys.json. */
public class KeyEntryScreen extends Screen {
    private final Screen parent;
    private final Ais.Ai ai;
    private EditBox key, model;
    private volatile String message = "";
    private volatile int messageColor = DIM;

    public KeyEntryScreen(Screen parent, Ais.Ai ai) {
        super(Component.literal("API key"));
        this.parent = parent;
        this.ai = ai;
    }

    private int px() {
        return width / 2 - 150;
    }

    private int py() {
        return height / 2 - 62;
    }

    @Override
    protected void init() {
        int x = px() + 14, y = py() + 40;
        key = new EditBox(this.font, x, y, 272, 16, Component.literal("API key"));
        key.setMaxLength(400);
        key.setHint(Component.literal("paste your key here (Ctrl+V)"));
        if (ai.id().equals("local")) {
            key.setMaxLength(200);
            key.setHint(Component.literal("http://localhost:11434/v1"));
            key.setValue(EdithConfig.get().localUrl);
        } else {
            key.addFormatter((text, first) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        }
        addRenderableWidget(key);
        model = new EditBox(this.font, x, y + 34, 272, 16, Component.literal("Model"));
        model.setMaxLength(120);
        String forced = EdithConfig.get().aiModels.get(ai.id());
        if (forced != null) model.setValue(forced);
        addRenderableWidget(model);
        setInitialFocus(key);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, 0xB0020C16);
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x08CFF4FF);
        int px = px(), py = py();
        panel(g, px, py, 300, 138, LINE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        Font font = this.font;
        int px = px(), py = py();
        boolean local = ai.id().equals("local");
        text(g, font, local ? "LOCAL MODEL" : "API KEY", px + 14, py + 8, DIM, 0.6f);
        text(g, font, ai.label().toUpperCase(), px + 14, py + 16, BRIGHT, 1.0f);
        boolean has = Keys.has(ai.id());
        String status = local ? (has ? "ON" : "OFF") : has ? "KEY SAVED" : "NO KEY";
        text(g, font, status, px + 286 - width(font, status, 0.7f), py + 10, has ? GREEN : AMBER, 0.7f);
        text(g, font, local ? "Address of the Ollama server. Install it from ollama.com, then run: ollama pull qwen2.5:3b" : "Get a key at " + ai.site() + ". It is saved only on this computer.", px + 14, py + 30 - 6, DIM, 0.55f);
        text(g, font, "MODEL (optional: leave empty and EDITH picks the newest one itself)", px + 14, py + 63, DIM, 0.55f);
        button(g, font, px + 14, py + 100, 86, "SAVE & CHECK", mx, my);
        button(g, font, px + 107, py + 100, 86, "REMOVE KEY", mx, my);
        button(g, font, px + 200, py + 100, 86, "CLOSE", mx, my);
        if (!message.isEmpty()) text(g, font, message, px + 14, py + 122, messageColor, 0.65f);
    }

    private void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, int mx, int my) {
        boolean hover = in(mx, my, x, y, w);
        g.fill(x, y, x + w, y + 14, hover ? 0x99166486 : 0x77041722);
        box(g, x, y, w, 14, hover ? BRIGHT : LINE);
        centered(g, font, label, x + w / 2, y + 4, hover ? BRIGHT : LINE, 0.65f);
    }

    private boolean in(double mx, double my, int x, int y, int w) {
        return mx >= x && mx < x + w && my >= y && my < y + 14;
    }

    private void save() {
        String k = key.getValue().trim();
        String m = model.getValue().trim();
        if (ai.id().equals("local")) {
            EdithConfig.get().localUrl = k.isEmpty() ? "http://localhost:11434/v1" : k;
            EdithConfig.get().localAi = true;
            k = "";
        }
        if (m.isEmpty()) EdithConfig.get().aiModels.remove(ai.id());
        else EdithConfig.get().aiModels.put(ai.id(), m);
        EdithConfig.save();
        if (k.isEmpty() && !Keys.has(ai.id()) && !ai.id().equals("local")) {
            message = "Paste a key first.";
            messageColor = AMBER;
            return;
        }
        if (!k.isEmpty()) Keys.set(ai.id(), k);
        if (!ai.id().equals("local")) key.setValue("");
        message = ai.id().equals("local") ? "Saved and switched on. Looking for the server..." : "Saved. Checking the key...";
        messageColor = LINE;
        Thread t = new Thread(() -> {
            Models.refresh();
            boolean ok = switch (ai.id()) {
                case "groq" -> !Models.groqChat.isEmpty();
                case "gemini" -> !Models.geminiChat.isEmpty();
                default -> !Models.compatChat.getOrDefault(ai.id(), java.util.List.of()).isEmpty();
            };
            if (ok) {
                message = ai.id().equals("local") ? "Found " + Models.compatChat.get("local").size() + " model(s). EDITH can think locally now."
                    : "Key works. EDITH can use " + ai.label() + " now.";
                messageColor = GREEN;
            } else {
                String why = Models.error;
                message = ai.id().equals("local") ? "No model found. Is Ollama running and did you pull a model?"
                    : "Saved, but the check failed" + (why.isEmpty() ? "." : ": " + (why.length() > 70 ? why.substring(0, 70) : why));
                messageColor = RED;
            }
        }, "edith-key-check");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int px = px(), py = py();
        if (in(mx, my, px + 14, py + 100, 86)) {
            save();
            return true;
        }
        if (in(mx, my, px + 107, py + 100, 86)) {
            Keys.set(ai.id(), "");
            if (ai.id().equals("local")) EdithConfig.get().localAi = false;
            EdithConfig.save();
            message = ai.id().equals("local") ? "Local AI switched off." : "Key removed.";
            messageColor = LINE;
            return true;
        }
        if (in(mx, my, px + 200, py + 100, 86)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) {   // Enter
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
