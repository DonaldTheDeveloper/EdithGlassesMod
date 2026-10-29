package com.example.chattranslator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SettingsScreen extends Screen {
    private final Screen parent;
    private Button rouxlsToggle, outToggle, outLang, inToggle, inLang;
    private final List<Button> quick = new ArrayList<>();
    private final List<String> quickCodes = new ArrayList<>(); // "" = off

    public SettingsScreen(Screen parent) {
        super(Component.literal("Chat Translator"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        quick.clear();
        quickCodes.clear();
        int w = 220, x = width / 2 - w / 2, y = Math.max(32, height / 2 - 94);
        Config c = Config.get();

        // 1) Rouxls: one switch, only affects what YOU write
        rouxlsToggle = addRenderableWidget(Button.builder(Component.empty(), b -> { ChatTranslatorClient.setRouxls(!ChatTranslatorClient.rouxlsOn()); refresh(); })
            .bounds(x, y, w, 20).build());

        // 2) Quick switch between favorite languages for what you write ([Off] + favorites)
        quickCodes.add("");
        for (String f : c.favorites) if (quickCodes.size() < 6) quickCodes.add(f);
        int n = quickCodes.size(), gap = 2, bw = (w - (n - 1) * gap) / n;
        for (int i = 0; i < n; i++) {
            String code = quickCodes.get(i);
            Button b = addRenderableWidget(Button.builder(Component.empty(), btn -> {
                ChatTranslatorClient.writeAs(code);
                refresh();
            }).bounds(x + i * (bw + gap), y + 24, bw, 20).build());
            quick.add(b);
        }

        // 3) Writing
        outToggle = addRenderableWidget(Button.builder(Component.empty(), b -> { c.outgoingTranslate = !c.outgoingTranslate; refresh(); })
            .bounds(x, y + 56, w, 20).build());
        outLang = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.gui.setScreen(
                new LanguageScreen(this, "Language I write in chat as", c.outgoingLang, code -> { c.outgoingLang = code; Config.save(); })))
            .bounds(x, y + 80, w, 20).build());

        // 4) Reading other people's chat
        inToggle = addRenderableWidget(Button.builder(Component.empty(), b -> { c.autoTranslate = !c.autoTranslate; refresh(); })
            .bounds(x, y + 112, w, 20).build());
        inLang = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.gui.setScreen(
                new LanguageScreen(this, "Language to show incoming chat in", c.targetLang, code -> { c.targetLang = code; Config.save(); })))
            .bounds(x, y + 136, w, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(x, y + 168, w, 20).build());
        refresh();
    }

    private void refresh() {
        Config c = Config.get();
        rouxlsToggle.setMessage(Component.literal("Rouxls Kaard speech: " + (ChatTranslatorClient.rouxlsOn() ? "ON" : "OFF")));
        for (int i = 0; i < quick.size(); i++) {
            String code = quickCodes.get(i);
            boolean selected = code.isEmpty() ? !c.outgoingTranslate : (c.outgoingTranslate && code.equalsIgnoreCase(c.outgoingLang));
            String label = code.isEmpty() ? "Off" : Languages.shortName(code);
            quick.get(i).setMessage(Component.literal(selected ? "[" + label + "]" : label));
        }
        outToggle.setMessage(Component.literal("Translate my messages: " + (c.outgoingTranslate ? "ON" : "OFF")));
        outLang.setMessage(Component.literal("I write in chat as: " + Config.label(c.outgoingLang)));
        inToggle.setMessage(Component.literal("Translate other people's chat: " + (c.autoTranslate ? "ON" : "OFF")));
        inLang.setMessage(Component.literal("Show their chat in: " + Config.label(c.targetLang)));
        Config.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, title, width / 2, Math.max(32, height / 2 - 94) - 16, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
