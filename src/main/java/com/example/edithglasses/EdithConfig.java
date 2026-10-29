package com.example.edithglasses;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class EdithConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static EdithConfig instance;

    /** "always" = HUD whenever the glasses are on (first person), "zoom" = only while zoomed or holding shift, "off". */
    public String hudMode = "always";
    /** Server command that puts the held item on your head (/wear uses it). Empty = do not send anything. */
    public String hatCommand = "hat";

    /** Your own video clip for the drone strike (e.g. C:/Videos/drone.mp4). Empty = short countdown only. */
    public String droneVideo = "";
    /** If ffplay is not installed the clip opens in your default player; wait this long before the strike. */
    public int droneSeconds = 12;
    /** Minimum pause between the commands of a drone strike (servers kick for command spam). */
    public int commandDelayMs = 650;
    /** How far (blocks) the HUD target lock reaches. */
    public double scanRange = 128.0;
    /** Part of the microphone name to use (see /edith mics). Empty = system default. */
    public String micDevice = "";
    /** Typing "edith, ..." in chat talks to EDITH instead of sending the message. Empty = off. */
    public String chatPrefix = "edith,";
    /** Answer typed questions out loud too (voice questions are always answered out loud when voice is on). */
    public boolean speakTypedReplies = false;
    /** Night vision while the glasses are on (toggle with the key or /edith night). */
    public boolean nightVision = false;
    /** Threat alerts (hostile mobs close, creepers, low health/hunger). */
    public boolean alerts = true;
    public boolean speakAlerts = false;
    /** Show Chat Translator output as HUD subtitles; read it aloud if speakTranslations. */
    public boolean subtitles = true;
    public boolean speakTranslations = false;
    /** Thermal vision: living things glow through walls (toggle with the key or /edith thermal). Many servers forbid this. */
    public boolean thermal = false;
    /** X-ray = ores only (AR markers through walls). Thermal vision = living things only. They are independent. Many servers forbid both. */
    public boolean xray = false;
    public int xrayRadius = 18;
    /** Sentinel: run away from players with a sword/axe/spear/trident/mace/wind charge (and incoming wind charges). */
    public boolean guard = false;
    /** 0 = always while the glasses are on, otherwise only after this many seconds without any input from you. */
    public int guardAfkSeconds = 0;
    /** Comma separated player names the Sentinel ignores. */
    public String guardFriends = "";
    /** Sentinel also fights hostile MOBS that reach you (never players). */
    public boolean guardFight = false;
    /** Player name to track live on the HUD. */
    public String trackedPlayer = "";
    /** Heal automatically with splash/drinkable healing, golden apples (hotbar) when health is at or below autoHealAt (half hearts). */
    public boolean autoHeal = true;
    public float autoHealAt = 8.0f;
    /** Hide the normal hotbar, hearts, food, armor and experience while the EDITH HUD is showing (it draws its own). */
    public boolean hideVanillaHud = true;
    /** Water bucket clutch: place water just before landing from a dangerous fall (bucket from hotbar or inventory). */
    public boolean clutch = true;
    /** Nicknames EDITH calls you by (comma separated; she picks one at random). Empty = your player name. */
    public String nicknames = "Donald,Ducky";
    /** Moved HUD parts: part name -> {dx, dy} pixels (see HudLayout). */
    public java.util.Map<String, int[]> hudOffsets = new java.util.HashMap<>();
    /** Which AI to try first: auto (groq, gemini, openai ... in that order) or a provider id such as openai or anthropic. */
    public String preferredAi = "auto";
    /** Optional model per provider (provider id -> model id). Empty = the newest suitable model from the provider's own list. */
    public java.util.Map<String, String> aiModels = new java.util.HashMap<>();
    public String openaiVoice = "nova";
    /** Keep every spoken line on disk and replay it instead of generating it again. */
    public boolean voiceCache = true;
    /** Your own identification tags: player name (lower case) -> "label|colour". */
    public java.util.Map<String, String> playerTags = new java.util.HashMap<>();
    /** Local AI (Ollama or any OpenAI-compatible server on this PC or another one). No key needed. */
    public boolean localAi = false;
    public String localUrl = "http://localhost:11434/v1";
    /** Hologram terrain map on the HUD. */
    public boolean holoMap = true;
    public int holoRadius = 20;
    /** Facial recognition: scan boxes and identification tags on players in view. */
    public boolean faceId = true;
    /** Boot animation when the glasses go on (and EDITH greets you if introVoice). */
    public boolean intro = true;
    public boolean introVoice = true;
    /** EDITH answers out loud. */
    public boolean speak = true;
    /** Leave empty for automatic (newest suitable model from the provider's live model list). */
    public String groqChatModel = "";
    public String geminiChatModel = "";
    public String groqSttModel = "";
    public String groqTtsModel = "";
    public String geminiTtsModel = "";
    public String geminiVoice = "Kore";
    /** A light digital shimmer on EDITH's voice (short echo), like an AI assistant. */
    public boolean voiceEffect = false;
    /** ffmpeg audio filter that shapes the voice towards the film EDITH (fitted by tools/voice_fit.py). Empty = off. Needs ffmpeg. */
    public String voiceFilter = "highpass=f=110,equalizer=f=280:t=q:w=1.0:g=-6.0,equalizer=f=650:t=q:w=1.0:g=3.6,equalizer=f=1400:t=q:w=0.8:g=5.4,equalizer=f=3000:t=q:w=0.8:g=-0.1,equalizer=f=5500:t=q:w=0.8:g=-1.5,acompressor=threshold=-20dB:ratio=3:attack=5:release=80:makeup=2,asetrate=23259,aresample=24000,atempo=1.0318";
    public String groqVoice = "diana";
    /** Extra personality text appended to EDITH's system prompt. */
    public String extraPrompt = "";
    /** Commands EDITH may never propose (first word, no slash). */
    public String blockedCommands = "op,deop,ban,ban-ip,pardon,pardon-ip,kick,stop,whitelist,save-off,publish,reload";

    /** Light shaping that keeps each voice's own character (the film filter pulls every voice towards one sound). */
    public static final String LIGHT_FILTER = "highpass=f=80,acompressor=threshold=-18dB:ratio=2:attack=5:release=80:makeup=1";

    /** Picks a Gemini voice. Kore is the one the film filter was fitted to; every other voice gets the light filter so it really sounds different. */
    public static void applyVoice(String name) {
        EdithConfig c = get();
        c.geminiVoice = name;
        c.voiceFilter = name.equalsIgnoreCase("Kore") ? new EdithConfig().voiceFilter : LIGHT_FILTER;
        save();
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("edithglasses.json");
    }

    public static EdithConfig get() {
        if (instance == null) {
            try {
                Path p = path();
                instance = Files.exists(p) ? GSON.fromJson(Files.readString(p), EdithConfig.class) : new EdithConfig();
            } catch (Exception e) {
                instance = new EdithConfig();
            }
            if (instance == null) instance = new EdithConfig();
        }
        return instance;
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(get()));
        } catch (Exception ignored) {
        }
    }
}
