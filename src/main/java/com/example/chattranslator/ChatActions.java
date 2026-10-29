package com.example.chattranslator;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public final class ChatActions {
    private ChatActions() {}

    public static void copy(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        info("Copied message to clipboard.");
    }

    public static void paste(ChatScreen screen, String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        screen.insertText(text, false);
    }

    public static void translate(String text) {
        Translator.translate(text, Config.get().targetLang).thenAccept(r -> {
            if (r == null) { info("Translation failed: " + Translator.lastError); return; }
            show(r);
        });
    }

    public static void show(Translator.Result r) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(
            Component.literal("[" + r.sourceLang() + " -> " + Config.get().targetLang + "] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(r.text()).withStyle(ChatFormatting.GRAY))));
    }

    public static void info(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW)));
    }
}
