package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.ai.Assistant;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/** Chat Translator output ("[de -> en] ...") shown as subtitles on the HUD, optionally read aloud. */
public final class Subtitles {
    public record Line(String text, long until) {
    }

    private static final Pattern TRANSLATED = Pattern.compile("^\\[[^\\]]{1,24} -> [^\\]]{1,24}\\]\\s*(.*)$", Pattern.DOTALL);
    public static final List<Line> LINES = new CopyOnWriteArrayList<>();

    private Subtitles() {
    }

    public static void onChat(Minecraft mc, String message) {
        if (!EdithConfig.get().subtitles) return;
        var m = TRANSLATED.matcher(message.trim());
        if (!m.matches()) return;
        String body = m.group(1).trim();
        if (body.isEmpty()) return;
        if (body.length() > 140) body = body.substring(0, 140) + "...";
        LINES.add(new Line(body, System.currentTimeMillis() + 9000));
        while (LINES.size() > 3) LINES.remove(0);
        if (EdithConfig.get().speakTranslations) Assistant.announce(mc, body);
    }

    public static List<Line> visible() {
        long now = System.currentTimeMillis();
        LINES.removeIf(l -> l.until() < now);
        return LINES;
    }
}
