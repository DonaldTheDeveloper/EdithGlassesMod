package com.example.chattranslator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Free, key-less translation. Tries several engines in turn so a rate limit (HTTP 429)
 * on one of them does not break translation:
 *   1. translate.googleapis.com   2. translate.google.com   3. clients5.google.com   4. MyMemory
 */
public final class Translator {
    public record Result(String text, String sourceLang) {}

    public static volatile String lastError = "";

    /**
     * Translates to a real language code, or to a dialect (british, cockney, posh, ...):
     * dialects first translate to English, then rewrite it locally. If the web engines are
     * down, the dialect is still applied to the original text.
     */
    public static CompletableFuture<Result> translate(String text, String target) {
        Dialect.Mode mode = Dialect.Mode.byCode(target);
        if (mode == null) return online(text, target);
        // Already English: no web call at all, the dialect is applied instantly.
        if (Lang.isProbablyEnglish(text)) {
            return CompletableFuture.completedFuture(new Result(Dialect.apply(text, mode), "en"));
        }
        return online(text, "en").thenApply(r -> {
            String base = r != null ? r.text() : text;
            String src = r != null ? r.sourceLang() : "?";
            return new Result(Dialect.apply(base, mode), src);
        });
    }

    /** Engine that worked last time; tried first so a blocked engine is not retried on every message. */
    private static volatile int preferred = 0;

    /** Recent web results, so repeated messages (and re-translating the same line) are instant. */
    private static final java.util.Map<String, Result> CACHE = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, Result> eldest) {
                return size() > 500;
            }
        });

    private static CompletableFuture<Result> online(String text, String target) {
        String key = target + "|" + text;
        Result cached = CACHE.get(key);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return CompletableFuture.supplyAsync(() -> {
            String q = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String tl = URLEncoder.encode(target, StandardCharsets.UTF_8);
            StringBuilder errors = new StringBuilder();
            for (int attempt = 0; attempt < 4; attempt++) {
                int engine = (preferred + attempt) % 4;
                try {
                    Result r = switch (engine) {
                        case 0 -> parseGtx(get("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t&tl=" + tl + "&q=" + q));
                        case 1 -> parseGtx(get("https://translate.google.com/translate_a/single?client=gtx&sl=auto&dt=t&tl=" + tl + "&q=" + q));
                        case 2 -> parseDict(get("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=" + tl + "&q=" + q));
                        default -> parseMyMemory(get("https://api.mymemory.translated.net/get?langpair=Autodetect%7C" + tl + "&q=" + q), text, target);
                    };
                    if (r != null) {
                        preferred = engine;
                        CACHE.put(key, r);
                        return r;
                    }
                } catch (Exception e) {
                    errors.append("[").append(engine + 1).append("] ").append(e.getMessage()).append(" ");
                }
            }
            lastError = errors.toString().trim();
            System.err.println("[ChatTranslator] all engines failed: " + lastError);
            return null;
        });
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setConnectTimeout(2500);
        con.setReadTimeout(4000);
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        int code = con.getResponseCode();
        if (code != 200) throw new RuntimeException("HTTP " + code);
        try (InputStream in = con.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Result parseGtx(String body) {
        JsonArray root = JsonParser.parseString(body).getAsJsonArray();
        StringBuilder sb = new StringBuilder();
        for (JsonElement seg : root.get(0).getAsJsonArray()) {
            sb.append(seg.getAsJsonArray().get(0).getAsString());
        }
        String src = root.size() > 2 && !root.get(2).isJsonNull() ? root.get(2).getAsString() : "?";
        return new Result(sb.toString(), src);
    }

    /** clients5 returns [["translated","src"]] (or a bare string array for a single result). */
    private static Result parseDict(String body) {
        JsonElement el = JsonParser.parseString(body);
        JsonArray root = el.getAsJsonArray();
        JsonElement first = root.get(0);
        if (first.isJsonArray()) {
            JsonArray a = first.getAsJsonArray();
            return new Result(a.get(0).getAsString(), a.size() > 1 ? a.get(1).getAsString() : "?");
        }
        return new Result(first.getAsString(), "?");
    }

    private static Result parseMyMemory(String body, String original, String target) {
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        JsonObject data = o.getAsJsonObject("responseData");
        String text = data.get("translatedText").getAsString();
        String status = o.has("responseStatus") && !o.get("responseStatus").isJsonNull() ? o.get("responseStatus").getAsString() : "200";
        if (!status.equals("200") || text.startsWith("PLEASE SELECT TWO DISTINCT") || text.startsWith("MYMEMORY WARNING")) {
            // MyMemory answers this when the text is already in the target language: nothing to translate.
            if (text.startsWith("PLEASE SELECT TWO DISTINCT")) return new Result(original, target);
            throw new RuntimeException("MyMemory: " + text);
        }
        String src = data.has("detectedLanguage") && !data.get("detectedLanguage").isJsonNull()
            ? data.get("detectedLanguage").getAsString() : "?";
        return new Result(text, src);
    }
}
