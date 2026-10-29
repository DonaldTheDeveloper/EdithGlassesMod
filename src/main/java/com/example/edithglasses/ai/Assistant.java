package com.example.edithglasses.ai;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.Glasses;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** E.D.I.T.H.: push-to-talk (only while the glasses are on) -> speech to text -> chat -> spoken reply, with confirmed actions. */
public final class Assistant {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private static final java.util.regex.Pattern STRIKE_WORDS = java.util.regex.Pattern.compile(
        "(?i).*\\b(target|drone|strike|take (him|her|it|them) out|eliminate|kill|destroy|attack|neutrali[sz]e|fire at|engage)\\b.*");
    private static final long PENDING_MS = 20_000;

    private record Pending(String kind, String text, java.util.UUID id, long created) {
    }

    private static final String SYSTEM = """
        You are E.D.I.T.H., the AI inside the player's smart glasses in Minecraft. Calm, sharp, a little dry humour, always helpful.
        Reply with ONLY one JSON object and nothing else:
        {"say": "what you say out loud: at most two short sentences, plain text, no markdown, no emoji",
         "command": null or "a single Minecraft command without the leading slash",
         "chat": null or "a message to send in public chat",
         "strike": false,
         "waypoint": null or {"action": "set" | "delete" | "track" | "untrack" | "goto", "name": "short name", "x": 0, "y": 0, "z": 0},
         "remember": null or "a short note to save (it is stored together with the position where the user said it)",
         "forget": null or "a keyword: saved notes that contain it are deleted",
         "target": null or "the mob the user names as the strike target when they say its name, for example wither"}
        Rules:
        - Answer in the language the user spoke.
        - Use the GAME STATE block for questions about the world, the player, items, mobs and recent chat.
        - Only set "command" when the user clearly asked you to do something a command can do; never invent commands that do not exist in Minecraft.
        - Set "strike" to true ONLY when the user designates the entity they are looking at as a target to be eliminated (for example "EDITH that is a target", "send a drone", "take them out", "kill that"). Then do not set "command". Say you are preparing a drone strike on the target (use its name from GAME STATE) and ask them to say confirm.
        - Set "waypoint" when the user asks you to remember/mark the current spot ("set"), forget one ("delete"), guide them to one ("track") or stop guiding ("untrack"). These run immediately without confirmation.
        - MEMORY: the GAME STATE may contain MEMORY lines: notes the user asked you to remember, and chests, barrels and shulker boxes the user opened with what was inside. Use them to answer "where did I put ..." or "do I have ..." (say the container and its coordinates and how far away it is). Say honestly when nothing is remembered. Never invent a location.
        - When the user asks you to remember something, set "remember" to a short note, and confirm in "say". To delete a note set "forget" to a keyword.
        - When the user wants to be taken or guided to a remembered place, set "waypoint" to {"action": "goto", "name": short name, "x":..., "y":..., "z":...} using the coordinates from MEMORY. It runs immediately.
        - When the user names the mob to strike ("target the wither"), set "target" to that name. EDITH finds the nearest one even if the user is not looking at it.
        - Only set "chat" when the user asked you to tell or say something to the other players. Write exactly the message to send.
        - If the user asks what someone in chat said or asks for a translation, use the Recent chat lines and answer in "say".
        - The user must confirm commands and chat messages before they run; say what you are about to do and ask them to say confirm.
        """;

    private static final Audio mic = new Audio();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edith-ai");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService SPEAK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edith-speech");
        t.setDaemon(true);
        return t;
    });
    private static final java.util.concurrent.atomic.AtomicInteger speaking = new java.util.concurrent.atomic.AtomicInteger();
    private static final List<Providers.Turn> history = new ArrayList<>();

    public static volatile State state = State.IDLE;
    public static volatile String reply = "";
    public static volatile String heard = "";
    public static volatile long replyUntil = 0;
    private static volatile Pending pending;
    private static boolean wasDown = false;
    /** True when the question was typed: the answer then appears in chat only, no voice (unless speakTypedReplies). */
    private static volatile boolean typed = false;
    /** What you were looking at when you started talking ("that is a target"). */
    private static volatile net.minecraft.world.entity.Entity aimed;

    private Assistant() {
    }

    /** Speak a short line out loud (alerts, subtitles). Does nothing when voice is off. */
    public static void announce(Minecraft mc, String text) {
        if (!EdithConfig.get().speak) return;
        POOL.submit(() -> {
            try {
                Providers.Speech sp = Providers.speak(text);
                if (sp != null) Audio.play(sp);
                else Audio.systemSpeak(text);
            } catch (Exception e) {
                Audio.systemSpeak(text);
            }
        });
    }

    private static Pending live() {
        Pending p = pending;
        if (p != null && System.currentTimeMillis() - p.created() > PENDING_MS) {
            pending = null;
            return null;
        }
        return p;
    }

    public static boolean hasPending() {
        return live() != null;
    }

    public static String pendingText() {
        Pending p = live();
        if (p == null) return null;
        return switch (p.kind()) {
            case "command" -> "/" + p.text();
            case "strike" -> "DRONE STRIKE: " + p.text();
            default -> "CHAT: " + p.text();
        };
    }

    // ---------------------------------------------------------------- push to talk

    public static void tick(Minecraft mc, KeyMapping ptt) {
        boolean down = ptt.isDown() && mc.player != null && mc.gui.screen() == null && Glasses.equipped(mc.player);
        if (down && !wasDown && state == State.SPEAKING) stopSpeaking();
        if (down && !wasDown && state == State.IDLE) {
            try {
                mic.startRecording();
                aimed = Glasses.target();
                state = State.LISTENING;
            } catch (Exception e) {
                say(mc, "Microphone unavailable: " + e.getMessage());
            }
        } else if (!down && wasDown && state == State.LISTENING) {
            byte[] wav = mic.stopRecording();
            if (wav == null) {
                state = State.IDLE;
            } else if (Audio.lastRms < 0.003) {
                state = State.IDLE;
                say(mc, "I can't hear you. Microphone level " + String.format("%.4f", Audio.lastRms) + ". Check your mic with /edith mics.");
            } else {
                state = State.THINKING;
                POOL.submit(() -> handleAudio(mc, wav));
            }
        } else if (state == State.LISTENING && !down) {
            mic.stopRecording();
            state = State.IDLE;
        }
        wasDown = down;
    }

    private static void handleAudio(Minecraft mc, byte[] wav) {
        try {
            typed = false;
            String text = Providers.transcribe(wav);
            if (text.isBlank()) {
                state = State.IDLE;
                return;
            }
            handleText(mc, text);
        } catch (Exception e) {
            fail(mc, e);
        }
    }

    /** Typed entry point (/edith ask) and the second half of the voice path. */
    public static void ask(Minecraft mc, String text) {
        typed = true;
        aimed = Glasses.target();
        state = State.THINKING;
        POOL.submit(() -> handleText(mc, text));
    }

    private static void handleText(Minecraft mc, String text) {
        try {
            heard = text;
            mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(
                Component.literal("EDITH heard: " + text).withStyle(ChatFormatting.GRAY)));
            Pending p = live();
            if (p != null) {
                String t = text.toLowerCase(Locale.ROOT);
                if (t.matches(".*\\b(confirm|yes|yeah|do it|go ahead|proceed|affirmative|ja)\\b.*")) {
                    pending = null;
                    run(mc, p);
                    return;
                }
                if (t.matches(".*\\b(cancel|no|nope|stop|abort|never mind|nei)\\b.*")) {
                    pending = null;
                    say(mc, "Cancelled.");
                    return;
                }
            }
            String low = text.toLowerCase(Locale.ROOT).trim().replaceAll("[.!?,]+$", "");
            java.util.regex.Matcher bm = java.util.regex.Pattern.compile(".*\\b(build|make|construct)\\b.*\\b(starter|small|normal|medium|regular|luxury|big|large|mansion)\\b.*\\b(house|home)\\b.*").matcher(low);
            if (bm.matches()) {
                String size = low.contains("luxury") || low.contains("mansion") ? "luxury" : low.matches(".*\\b(big|large)\\b.*") ? "luxury"
                    : low.matches(".*\\b(normal|medium|regular)\\b.*") ? "normal" : "starter";
                String style = low.replaceAll(".*\\b(house|home)\\b", "").trim();
                mc.execute(() -> say(mc, com.example.edithglasses.feature.Builder.preview(mc, size, style)));
                return;
            }
            if (com.example.edithglasses.feature.Builder.previewing() && low.matches(".*\\b(confirm|yes|build it|go ahead|do it|start)\\b.*")) {
                mc.execute(() -> say(mc, com.example.edithglasses.feature.Builder.confirm(mc)));
                return;
            }
            if (com.example.edithglasses.feature.Builder.busy() && low.matches(".*\\b(stop|cancel|abort|never mind)\\b.*")) {
                mc.execute(() -> com.example.edithglasses.feature.Builder.cancel(mc, "you asked me to stop"));
                return;
            }
            if (low.matches("^(hey |ok |okay )?(edith[, ]+)?(stop|quiet|silence|shut up|enough|skip|be quiet|that's enough|never mind|nevermind)( talking)?$")) {
                stopSpeaking();
                state = State.IDLE;
                return;
            }
            if (low.matches(".*\\b(analy[sz]e|scan (it|that|him|her|them|this)|what('s| is) that|what am i looking at|tell me about (it|that|this))\\b.*")) {
                net.minecraft.world.entity.Entity at = aimed != null && aimed.isAlive() ? aimed : Glasses.target();
                say(mc, com.example.edithglasses.feature.Analyse.speech(mc, at));
                return;
            }
            String user = "GAME STATE:\n" + GameContext.build(mc) + com.example.edithglasses.feature.Memory.context(mc, text) + "\nUSER SAID: " + text;
            String extra = EdithConfig.get().extraPrompt;
            String nicks = EdithConfig.get().nicknames;
            String nickRule = nicks == null || nicks.isBlank() ? "" : "\nThe user likes to be called by these nicknames: " + nicks
                + ". Use one of them now and then instead of their player name (not in every sentence).";
            String raw = Providers.chat(SYSTEM + nickRule + (extra.isBlank() ? "" : extra), history, user);
            String say = raw, command = null, chat = null;
            boolean strike = false;
            String wpAction = null, wpName = null, remember = null, forget = null, targetName = null;
            double wx = 0, wy = 0, wz = 0;
            boolean wpHasXyz = false;
            try {
                JsonObject o = JsonParser.parseString(extractJson(raw)).getAsJsonObject();
                say = str(o, "say");
                command = str(o, "command");
                chat = str(o, "chat");
                if (o.has("waypoint") && o.get("waypoint").isJsonObject()) {
                    wpAction = str(o.getAsJsonObject("waypoint"), "action");
                    wpName = str(o.getAsJsonObject("waypoint"), "name");
                    JsonObject wo = o.getAsJsonObject("waypoint");
                    if (wo.has("x") && wo.has("z") && wo.get("x").isJsonPrimitive() && wo.get("z").isJsonPrimitive()) {
                        wx = wo.get("x").getAsDouble();
                        wy = wo.has("y") && wo.get("y").isJsonPrimitive() ? wo.get("y").getAsDouble() : 64;
                        wz = wo.get("z").getAsDouble();
                        wpHasXyz = true;
                    }
                }
                remember = str(o, "remember");
                forget = str(o, "forget");
                targetName = str(o, "target");
                strike = o.has("strike") && !o.get("strike").isJsonNull() && o.get("strike").getAsBoolean();
            } catch (Exception ignored) {
                // model ignored the format: speak the raw text
            }
            history.add(new Providers.Turn("user", text));
            history.add(new Providers.Turn("assistant", say == null ? raw : say));
            while (history.size() > 12) history.remove(0);

            // never start a strike unless the words really asked for one (speech-to-text noise must not arm it)
            if (strike && !STRIKE_WORDS.matcher(text).matches()) strike = false;
            if (remember != null && !remember.isBlank()) com.example.edithglasses.feature.Memory.addNote(mc, remember);
            if (forget != null && !forget.isBlank()) com.example.edithglasses.feature.Memory.forget(forget);
            if (wpAction != null) {
                say = switch (wpAction) {
                    case "goto" -> wpHasXyz ? com.example.edithglasses.feature.Waypoints.setAt(mc, wpName, wx, wy, wz, null) : "I don't have coordinates for that.";
                    case "set" -> com.example.edithglasses.feature.Waypoints.set(mc, wpName);
                    case "delete" -> com.example.edithglasses.feature.Waypoints.delete(wpName == null ? "" : wpName);
                    case "track" -> com.example.edithglasses.feature.Waypoints.track(wpName);
                    default -> com.example.edithglasses.feature.Waypoints.track("");
                };
            } else if (strike) {
                net.minecraft.world.entity.Entity named = targetName != null && !targetName.isBlank()
                    ? com.example.edithglasses.feature.Analyse.findNamed(mc, targetName) : null;
                net.minecraft.world.entity.Entity t = named != null ? named : aimed != null && aimed.isAlive() ? aimed : Glasses.target();
                if (t == null) {
                    say = targetName != null && !targetName.isBlank() ? "I can't find a " + targetName + " nearby." : "I don't have a target in sight.";
                } else if (t instanceof net.minecraft.world.entity.player.Player || !(t instanceof net.minecraft.world.entity.LivingEntity)) {
                    say = "I won't strike that. Players and objects are not valid targets.";
                } else {
                    pending = new Pending("strike", t.getName().getString(), t.getUUID(), System.currentTimeMillis());
                }
            } else if (command != null) {
                command = command.startsWith("/") ? command.substring(1) : command;
                String reason = blocked(command);
                if (reason != null) {
                    say = "I can't run that. " + reason;
                } else {
                    pending = new Pending("command", command, null, System.currentTimeMillis());
                }
            } else if (chat != null) {
                pending = new Pending("chat", chat, null, System.currentTimeMillis());
            }
            say(mc, say == null || say.isBlank() ? "Done." : say);
        } catch (Exception e) {
            fail(mc, e);
        }
    }

    public static void confirm(Minecraft mc) {
        Pending p = live();
        POOL.submit(() -> {
            if (p == null) {
                say(mc, "Nothing to confirm.");
                return;
            }
            pending = null;
            run(mc, p);
        });
    }

    public static void cancel(Minecraft mc) {
        pending = null;
        Audio.stopSpeaking();
        POOL.submit(() -> say(mc, "Cancelled."));
    }

    private static void run(Minecraft mc, Pending p) {
        if (p.kind().equals("strike")) {
            say(mc, "Drone launched.");
            Strike.execute(mc, p.id(), p.text());
            say(mc, "Target eliminated.");
            return;
        }
        mc.execute(() -> {
            if (mc.player == null) return;
            if (p.kind().equals("command")) mc.player.connection.sendCommand(p.text());
            else mc.player.connection.sendChat(p.text());
        });
        say(mc, p.kind().equals("command") ? "Command sent." : "Message sent.");
    }

    private static String blocked(String command) {
        String first = command.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        for (String b : EdithConfig.get().blockedCommands.split(",")) {
            if (first.equals(b.trim().toLowerCase(Locale.ROOT))) return "/" + first + " is on the blocked list.";
        }
        return null;
    }

    // ---------------------------------------------------------------- output

    private static void say(Minecraft mc, String text) {
        reply = text;
        replyUntil = System.currentTimeMillis() + 9000;
        mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(
            Component.literal("EDITH: " + text).withStyle(ChatFormatting.AQUA)));
        if (!EdithConfig.get().speak || (typed && !EdithConfig.get().speakTypedReplies)) {
            state = State.IDLE;
            return;
        }
        state = State.SPEAKING;
        final int g = Audio.speechGen();
        final String spoken = forSpeech(text);
        speaking.incrementAndGet();
        SPEAK.submit(() -> {
            try {
                if (Audio.speechGen() != g) return;
                Providers.Speech s = Providers.speak(spoken);
                if (Audio.speechGen() != g) return;
                if (s != null) Audio.play(s);
                else Audio.systemSpeak(spoken);
            } catch (Exception e) {
                if (Audio.speechGen() == g) Audio.systemSpeak(spoken);
            } finally {
                if (speaking.decrementAndGet() <= 0 && state == State.SPEAKING) state = State.IDLE;
            }
        });
    }

    /** What is said out loud: the first two sentences (the full text stays on screen and in chat). */
    private static String forSpeech(String text) {
        String t = text.trim();
        if (t.length() <= 170) return t;
        int sentences = 0, cut = -1;
        for (int i = 0; i < t.length() - 1; i++) {
            char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && t.charAt(i + 1) == ' ' && ++sentences == 2) {
                cut = i + 1;
                break;
            }
        }
        if (cut < 0 || cut > 240) {
            cut = t.lastIndexOf(' ', 200);
            if (cut < 0) cut = Math.min(t.length(), 200);
        }
        return t.substring(0, cut).trim();
    }

    /** Stops EDITH talking right now (push-to-talk, the N key and "stop" do this too). */
    public static void stopSpeaking() {
        Audio.stopSpeaking();
        if (state == State.SPEAKING) state = State.IDLE;
    }

    private static void fail(Minecraft mc, Exception e) {
        state = State.IDLE;
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        reply = "Error: " + m;
        replyUntil = System.currentTimeMillis() + 9000;
        mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(
            Component.literal("EDITH error: " + m).withStyle(ChatFormatting.RED)));
    }

    private static String extractJson(String raw) {
        int a = raw.indexOf('{'), b = raw.lastIndexOf('}');
        return a >= 0 && b > a ? raw.substring(a, b + 1) : raw;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return null;
        String s = e.getAsString().trim();
        return s.isEmpty() || s.equalsIgnoreCase("null") ? null : s;
    }
}
