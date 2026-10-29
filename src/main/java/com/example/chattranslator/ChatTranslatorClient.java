package com.example.chattranslator;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ChatTranslatorClient implements ClientModInitializer {
    private static boolean sendingTranslated = false;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chattranslator", "main"));
        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.chattranslator.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        QuickMod.muteKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.chattranslator.quickmute", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DELETE, category));
        KeyMapping cycleKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.chattranslator.cycle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, category));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) mc.gui.setScreen(new SettingsScreen(null));
            while (cycleKey.consumeClick()) cycleFavorite();
        });

        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> { if (!overlay) auto(msg); });
        ClientReceiveMessageEvents.CHAT.register((msg, signed, sender, params, ts) -> auto(msg));

        // Outgoing: translate what I type before it is sent.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (sendingTranslated || !Config.get().outgoingTranslate || message.isBlank()) return true;
            Translator.translate(message, Config.get().outgoingLang).thenAccept(r -> {
                String out = r != null ? r.text() : message; // fall back to original on failure
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player == null) return;
                    sendingTranslated = true;
                    try { mc.player.connection.sendChat(out); } finally { sendingTranslated = false; }
                });
            });
            return false;
        });

        // Outgoing commands like /msg <player> <text>: translate only the text part.
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (sendingTranslated || !Config.get().outgoingTranslate) return true;
            String[] parts = command.split(" ", -1);
            String name = parts[0].toLowerCase(java.util.Locale.ROOT);
            if (name.startsWith("minecraft:")) name = name.substring("minecraft:".length());
            int skip = -1;
            for (String entry : Config.get().translateCommands) {
                int i = entry.lastIndexOf(':');
                if (i > 0 && entry.substring(0, i).equalsIgnoreCase(name)) {
                    try { skip = Integer.parseInt(entry.substring(i + 1).trim()); } catch (NumberFormatException ignored) {}
                    break;
                }
            }
            if (skip < 0 || parts.length <= skip + 1) return true;
            String prefix = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, skip + 1));
            String text = String.join(" ", java.util.Arrays.copyOfRange(parts, skip + 1, parts.length));
            if (text.isBlank()) return true;
            Translator.translate(text, Config.get().outgoingLang).thenAccept(r -> {
                String out = prefix + " " + (r != null ? r.text() : text);
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player == null) return;
                    sendingTranslated = true;
                    try { mc.player.connection.sendCommand(out); } finally { sendingTranslated = false; }
                });
            });
            return false;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(ClientCommands.literal("translate")
                .then(ClientCommands.literal("menu").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.schedule(() -> mc.gui.setScreen(new SettingsScreen(null)));
                    return 1;
                }))
                // /translate rouxls [on|off]: Rouxls Kaard speech for your own messages only
                .then(ClientCommands.literal("rouxls")
                    .executes(c -> { setRouxls(!rouxlsOn()); return 1; })
                    .then(ClientCommands.literal("on").executes(c -> { setRouxls(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setRouxls(false); return 1; })))
                // /translate quickmod [on|off]: middle-click quick moderation in the chat screen
                .then(ClientCommands.literal("quickmod")
                    .executes(c -> {
                        Config.get().quickModEnabled = !Config.get().quickModEnabled;
                        Config.save();
                        ChatActions.info("Quick moderation (middle-click twice) " + (Config.get().quickModEnabled ? "ON" : "OFF"));
                        return 1;
                    })
                    .then(ClientCommands.literal("on").executes(c -> { Config.get().quickModEnabled = true; Config.save(); ChatActions.info("Quick moderation ON"); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { Config.get().quickModEnabled = false; Config.save(); ChatActions.info("Quick moderation OFF"); return 1; })))
                .then(ClientCommands.literal("cycle").executes(c -> { cycleFavorite(); return 1; }))
                .then(ClientCommands.literal("fav")
                    .then(ClientCommands.literal("list").executes(c -> {
                        ChatActions.info("Favorites: " + String.join(", ", Config.get().favorites));
                        return 1;
                    }))
                    .then(ClientCommands.literal("add").then(ClientCommands.argument("code", StringArgumentType.word()).executes(c -> {
                        String code = StringArgumentType.getString(c, "code");
                        if (!Config.get().isFavorite(code)) Config.get().toggleFavorite(code);
                        ChatActions.info("Favorites: " + String.join(", ", Config.get().favorites));
                        return 1;
                    })))
                    .then(ClientCommands.literal("remove").then(ClientCommands.argument("code", StringArgumentType.word()).executes(c -> {
                        String code = StringArgumentType.getString(c, "code");
                        if (Config.get().isFavorite(code)) Config.get().toggleFavorite(code);
                        ChatActions.info("Favorites: " + String.join(", ", Config.get().favorites));
                        return 1;
                    }))))
                .then(ClientCommands.literal("dialects").executes(c -> {
                    ChatActions.info("Fun dialects (use with /translate say <name> or /translate lang <name>): "
                        + "british, cockney, posh, scottish, northern, welsh, rouxls");
                    return 1;
                }))
                .then(ClientCommands.literal("say")
                    .executes(c -> { setOut(!Config.get().outgoingTranslate, null); return 1; })
                    .then(ClientCommands.literal("off").executes(c -> { setOut(false, null); return 1; }))
                    .then(ClientCommands.argument("lang", StringArgumentType.word()).executes(c -> {
                        setOut(true, StringArgumentType.getString(c, "lang"));
                        return 1;
                    })))
                .then(ClientCommands.literal("auto")
                    .executes(c -> { toggleAuto(!Config.get().autoTranslate); return 1; })
                    .then(ClientCommands.literal("on").executes(c -> { toggleAuto(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { toggleAuto(false); return 1; })))
                .then(ClientCommands.literal("lang")
                    .then(ClientCommands.argument("code", StringArgumentType.word()).executes(c -> {
                        Config.get().targetLang = StringArgumentType.getString(c, "code");
                        Config.save();
                        ChatActions.info("Incoming language: " + Config.get().targetLang);
                        return 1;
                    })))
                .then(ClientCommands.literal("text")
                    .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                        ChatActions.translate(StringArgumentType.getString(c, "text"));
                        return 1;
                    })))));
    }

    /** Rouxls speech only styles what YOU write. It never touches other people's chat. */
    public static boolean rouxlsOn() {
        Config c = Config.get();
        return c.outgoingTranslate && "rouxls".equals(c.outgoingLang);
    }

    public static void setRouxls(boolean on) {
        Config cfg = Config.get();
        cfg.outgoingTranslate = on;
        if (on) cfg.outgoingLang = "rouxls";
        Config.save();
        ChatActions.info("Rouxls Kaard speech " + (on ? "ON for your messages, hee hee hee!" : "OFF"));
    }

    /** Quick switch what you write in: a language code, or "" for no translation. */
    public static void writeAs(String code) {
        Config cfg = Config.get();
        if (code == null || code.isEmpty()) {
            cfg.outgoingTranslate = false;
        } else {
            cfg.outgoingTranslate = true;
            cfg.outgoingLang = code;
        }
        Config.save();
    }

    /** Hotkey / command: favorite 1 -> favorite 2 -> ... -> off -> favorite 1. */
    public static void cycleFavorite() {
        Config cfg = Config.get();
        java.util.List<String> favs = cfg.favorites;
        if (favs.isEmpty()) {
            ChatActions.info("No favorite languages yet. Open the K menu and click a star next to a language.");
            return;
        }
        int at = -1;
        if (cfg.outgoingTranslate) {
            for (int i = 0; i < favs.size(); i++) if (favs.get(i).equalsIgnoreCase(cfg.outgoingLang)) at = i;
        }
        if (cfg.outgoingTranslate && at == favs.size() - 1) {
            writeAs("");
            ChatActions.info("Writing normally (translation off)");
            return;
        }
        String next = favs.get(at + 1); // at == -1 starts at the first favorite
        writeAs(next);
        ChatActions.info("Writing as: " + Config.label(next));
    }

    private static void setOut(boolean on, String lang) {
        Config cfg = Config.get();
        cfg.outgoingTranslate = on;
        if (lang != null) cfg.outgoingLang = lang;
        Config.save();
        ChatActions.info("Translating my messages: " + (on ? "ON -> " + cfg.outgoingLang : "OFF"));
    }

    private static void toggleAuto(boolean on) {
        Config.get().autoTranslate = on;
        Config.save();
        ChatActions.info("Auto-translate incoming " + (on ? "ON" : "OFF") + " (to " + Config.get().targetLang + ")");
    }

    // ---- keeping incoming translation quiet -------------------------------------------------

    private static final java.util.Map<String, Long> SEEN = new java.util.LinkedHashMap<>();
    private static final java.util.ArrayDeque<Long> RECENT_CALLS = new java.util.ArrayDeque<>();

    /** True if this exact line was handled in the last 15 seconds (stops repeat spam). */
    private static synchronized boolean duplicate(String s) {
        long now = System.currentTimeMillis();
        SEEN.values().removeIf(t -> now - t > 15_000);
        return SEEN.put(s, now) != null;
    }

    /** At most 3 translated lines per 5 seconds, so a busy chat can never flood you. */
    private static synchronized boolean underLimit() {
        long now = System.currentTimeMillis();
        while (!RECENT_CALLS.isEmpty() && now - RECENT_CALLS.peekFirst() > 5_000) RECENT_CALLS.pollFirst();
        if (RECENT_CALLS.size() >= 3) return false;
        RECENT_CALLS.addLast(now);
        return true;
    }

    private static String base(String lang) {
        String l = lang.toLowerCase(java.util.Locale.ROOT);
        return l.length() > 2 && l.charAt(2) == '-' ? l.substring(0, 2) : l;
    }

    private static void auto(Component msg) {
        Config cfg = Config.get();
        if (!cfg.autoTranslate) return;
        String s = msg.getString().trim();
        if (s.length() < 4) return;
        if (s.startsWith("[") && s.contains(" -> ")) return; // our own output
        Dialect.Mode mode = Dialect.Mode.byCode(cfg.targetLang);
        String real = mode != null ? "en" : cfg.targetLang; // fun styles only restyle text that really was translated
        // Already in your language? Then do nothing at all, no web call and no extra line.
        if (base(real).equals("en") && Lang.isProbablyEnglish(s)) return;
        if (duplicate(s) || !underLimit()) return;
        Translator.translate(s, real).thenAccept(r -> {
            // Only show real translations: known source language, different from yours, text actually changed.
            if (r == null || r.sourceLang().equals("?") || base(r.sourceLang()).equals(base(real))) return;
            String out = mode != null ? Dialect.apply(r.text(), mode) : r.text();
            if (out.equalsIgnoreCase(s)) return;
            ChatActions.show(new Translator.Result(out, r.sourceLang()));
        });
    }
}
