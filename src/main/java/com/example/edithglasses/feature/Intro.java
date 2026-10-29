package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.Glasses;
import com.example.edithglasses.ai.Assistant;
import com.example.edithglasses.ai.Audio;
import com.example.edithglasses.ai.Keys;
import com.example.edithglasses.ai.Providers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import static com.example.edithglasses.feature.HudStyle.*;

/**
 * Boot sequence when you put the glasses on: the view fades in from black, rings and a scan line sweep the lens,
 * E.D.I.T.H. types itself out with its expansion, a short system check runs and EDITH greets you (voice and chimes).
 */
public final class Intro {
    private static final long TOTAL = 5200;
    private static final String[][] CHECKS = {
        {"OPTICS", "ONLINE"}, {"TARGETING", "ONLINE"}, {"THERMAL IMAGING", "STANDBY"},
        {"SATELLITE UPLINK", "SECURE"}, {"VOICE LINK", "?"},
    };

    private static long start = -1, lastPlayed = -100000;
    private static volatile Providers.Speech greetAudio;
    private static volatile String greetText = "";
    private static volatile String nick = "";
    private static volatile boolean preparing = false;
    private static volatile boolean voiceFailed = false;
    private static boolean wasEquipped = false;
    private static int soundStep = 0;

    private Intro() {
    }

    public static boolean playing() {
        return start >= 0;
    }

    /** The name EDITH uses for you this session: one of your nicknames, or your player name. */
    public static String callName(Minecraft mc) {
        if (nick.isEmpty()) {
            String n = EdithConfig.get().nicknames;
            String[] parts = n == null ? new String[0] : n.split(",");
            java.util.List<String> ok = new java.util.ArrayList<>();
            for (String p : parts) if (!p.isBlank()) ok.add(p.trim());
            nick = ok.isEmpty() ? (mc.player == null ? "" : mc.player.getName().getString()) : ok.get(new java.util.Random().nextInt(ok.size()));
        }
        return nick;
    }

    public static void resetName() {
        nick = "";
        greetAudio = null;
        greetText = "";
    }

    /** Makes the greeting audio in the background so it plays the moment the animation starts (cloud voices take seconds). */
    private static void prepare(Minecraft mc) {
        EdithConfig c = EdithConfig.get();
        if (!c.introVoice || !c.speak || preparing || mc.player == null) return;
        String text = "Hello, " + callName(mc) + ". Edith online.";
        if (text.equals(greetText) && greetAudio != null) return;
        if (voiceFailed && text.equals(greetText)) return;
        preparing = true;
        greetText = text;
        Thread t = new Thread(() -> {
            try {
                greetAudio = Providers.speak(text);
                voiceFailed = greetAudio == null;
                if (voiceFailed) {
                    String why = Providers.lastError;
                    mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(
                        "EDITH voice: the cloud voice did not answer" + (why == null || why.isEmpty() ? "." : " (" + why + ").") + " Using the system voice for the greeting.")));
                }
            } catch (Exception e) {
                voiceFailed = true;
            } finally {
                preparing = false;
            }
        }, "edith-greeting");
        t.setDaemon(true);
        t.start();
    }

    /** Starts the sequence right away (menu button / command). */
    public static void play(Minecraft mc) {
        start = System.currentTimeMillis();
        lastPlayed = start;
        soundStep = 0;
        EdithConfig c = EdithConfig.get();
        if (c.introVoice && c.speak && mc.player != null) {
            String text = "Hello, " + callName(mc) + ". Edith online.";
            Providers.Speech sp = text.equals(greetText) ? greetAudio : null;
            Thread t = new Thread(() -> {
                try {
                    if (sp != null) Audio.play(sp);
                    else Assistant.announce(mc, text);
                } catch (Exception e) {
                    Audio.systemSpeak(text);
                }
            }, "edith-greeting-play");
            t.setDaemon(true);
            t.start();
        }
    }

    private static int prepTick = 0;

    public static void tick(Minecraft mc) {
        if (mc.player != null && ++prepTick % 100 == 1) prepare(mc);
        boolean eq = mc.player != null && Glasses.equipped(mc.player);
        if (eq && !wasEquipped && EdithConfig.get().intro && start < 0
            && System.currentTimeMillis() - lastPlayed > 20000 && mc.options.getCameraType().isFirstPerson()) {
            play(mc);
        }
        wasEquipped = eq;
        if (start < 0) return;
        if (!eq) {
            start = -1;
            return;
        }
        long t = System.currentTimeMillis() - start;
        if (t > TOTAL) {
            start = -1;
            return;
        }
        // chimes that follow the animation
        long[] at = {0, 900, 1500, 2100, 2700, 3300};
        while (soundStep < at.length && t >= at[soundStep]) {
            var snd = switch (soundStep) {
                case 0 -> SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.5f);
                case 5 -> SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.8f);
                default -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f + soundStep * 0.18f);
            };
            mc.getSoundManager().play(snd);
            soundStep++;
        }
    }

    public static void draw(GuiGraphicsExtractor g, Font font, int w, int h) {
        if (start < 0) return;
        Minecraft mc = Minecraft.getInstance();
        float t = (System.currentTimeMillis() - start) / 1000f;
        float total = TOTAL / 1000f;
        int cx = w / 2, cy = h / 2;

        // black fade-in and a final fade-out of everything
        float dark = t < 0.3f ? 1f : Math.max(0f, 1f - (t - 0.3f) / 1.4f);
        float fade = t > total - 0.7f ? Math.max(0f, (total - t) / 0.7f) : 1f;
        if (dark > 0) g.fill(0, 0, w, h, alpha(0xFF000810, dark * 0.92f));

        // expanding rings
        for (int i = 0; i < 3; i++) {
            float k = t - 0.2f - i * 0.28f;
            if (k < 0) continue;
            float e = Math.min(1f, k / 1.3f);
            int r = (int) (10 + e * (h * 0.42f + i * 14));
            ring(g, cx, cy, r, alpha(LINE, fade * 0.8f * (1f - e)), 6);
        }
        // rotating segment arcs
        float rot = t * 140f;
        float arcA = Math.min(1f, t / 0.6f) * fade;
        for (int s = 0; s < 3; s++) {
            double a0 = rot + s * 120, a1 = a0 + 70;
            arc(g, cx, cy, (int) (h * 0.30f), a0, a1, alpha(BRIGHT, arcA), 90, 90);
            arc(g, cx, cy, (int) (h * 0.30f) - 8, -a0 - 20, -a0 + 40, alpha(LINE, arcA), 90, 90);
        }

        // scan line sweeping down the lens
        float sweep = (t - 0.6f) / 1.6f;
        if (sweep > 0 && sweep < 1) {
            int sy = (int) (sweep * h);
            g.fillGradient(0, sy - 12, w, sy, 0x0040E0FF, alpha(0x3340E0FF, fade));
            g.fill(0, sy, w, sy + 1, alpha(BRIGHT, fade));
        }

        // E.D.I.T.H. typed out
        String name = "E.D.I.T.H.";
        int chars = Mth.clamp((int) ((t - 0.7f) / 0.11f), 0, name.length());
        float big = 2.4f;
        String shown = name.substring(0, chars);
        if (chars > 0) {
            int tw = width(font, name, big);
            text(g, font, shown, cx - tw / 2, cy - 36, alpha(BRIGHT, fade), big);
            if (chars < name.length() && (System.currentTimeMillis() / 100) % 2 == 0)
                g.fill(cx - tw / 2 + width(font, shown, big) + 1, cy - 36, cx - tw / 2 + width(font, shown, big) + 3, cy - 36 + 18, alpha(BRIGHT, fade));
        }
        String sub = "EVEN DEAD, I'M THE HERO";
        int sc = Mth.clamp((int) ((t - 1.7f) / 0.04f), 0, sub.length());
        if (sc > 0) centered(g, font, sub.substring(0, sc), cx, cy - 10, alpha(LINE, fade), 0.8f);

        // system check
        int lines = Mth.clamp((int) ((t - 2.2f) / 0.28f) + 1, 0, CHECKS.length);
        if (t > 2.2f) {
            boolean voice = Keys.any();
            for (int i = 0; i < lines; i++) {
                String label = CHECKS[i][0];
                String state = CHECKS[i][1].equals("?") ? (voice ? "ONLINE" : "NO KEYS") : CHECKS[i][1];
                int col = state.equals("NO KEYS") ? AMBER : state.equals("STANDBY") ? LINE : GREEN;
                int y = cy + 6 + i * 9;
                text(g, font, label, cx - 70, y, alpha(DIM, fade * 1.5f), 0.6f);
                text(g, font, state, cx + 70 - width(font, state, 0.6f), y, alpha(col, fade), 0.6f);
                for (int x = cx - 70 + width(font, label, 0.6f) + 4; x < cx + 66 - width(font, state, 0.6f); x += 4)
                    g.fill(x, y + 4, x + 1, y + 5, alpha(FAINT, fade));
            }
        }

        // welcome
        if (t > 3.5f) {
            String who = callName(mc).toUpperCase();
            float a = Math.min(1f, (t - 3.5f) / 0.4f) * fade;
            String hello = "WELCOME, " + who;
            int ww = width(font, hello, 1.1f) + 20;
            g.fill(cx - ww / 2, cy + 58, cx + ww / 2, cy + 74, alpha(0x7706202F, a));
            box(g, cx - ww / 2, cy + 58, ww, 16, alpha(LINE, a));
            centered(g, font, hello, cx, cy + 62, alpha(GREEN, a), 1.1f);
        }
    }
}
