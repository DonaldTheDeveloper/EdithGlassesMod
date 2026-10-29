package com.example.edithglasses.ai;

import com.example.edithglasses.EdithConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Speech-to-text, chat and text-to-speech with automatic fail-over between Groq and Gemini models. */
public final class Providers {
    public record Turn(String role, String text) {
    }

    /** Raw audio ready to play: either a WAV file or 24 kHz 16-bit mono PCM. */
    public record Speech(byte[] data, boolean wav, int rate) {
    }

    public static volatile String lastChatModel = "";
    public static volatile String lastError = "";
    private static final Map<String, Long> cooldown = new HashMap<>();

    private Providers() {
    }

    private static boolean cooling(String id) {
        Long until = cooldown.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    private static void cool(String id, int status) {
        long ms = status == 429 ? 60_000L : (status >= 500 ? 30_000L : 600_000L);
        cooldown.put(id, System.currentTimeMillis() + ms);
    }

    private static List<String> order(String override, List<String> auto) {
        List<String> out = new ArrayList<>();
        if (override != null && !override.isBlank()) out.add(override);
        for (String m : auto) if (!out.contains(m)) out.add(m);
        return out;
    }

    // ---------------------------------------------------------------- speech to text

    public static String transcribe(byte[] wav) throws Exception {
        Models.ensure();
        EdithConfig cfg = EdithConfig.get();
        String gk = Keys.groq();
        if (!gk.isBlank()) {
            for (String m : order(cfg.groqSttModel, Models.groqStt)) {
                String id = "groq:" + m;
                if (cooling(id)) continue;
                Http.Resp r = Http.postAudio("https://api.groq.com/openai/v1/audio/transcriptions", gk, m, wav);
                if (r.ok()) return JsonParser.parseString(r.text()).getAsJsonObject().get("text").getAsString().trim();
                note("Groq STT " + m, r);
                cool(id, r.status());
            }
        }
        String ok = Keys.get("openai");
        if (!ok.isBlank()) {
            for (String m : Models.openaiStt) {
                String id = "openai:" + m;
                if (cooling(id)) continue;
                Http.Resp r = Http.postAudio("https://api.openai.com/v1/audio/transcriptions", ok, m, wav);
                if (r.ok()) return JsonParser.parseString(r.text()).getAsJsonObject().get("text").getAsString().trim();
                note("OpenAI STT " + m, r);
                cool(id, r.status());
            }
        }
        String mk = Keys.gemini();
        if (!mk.isBlank()) {
            for (String m : order(cfg.geminiChatModel, Models.geminiChat)) {
                String id = "gemini:" + m;
                if (cooling(id)) continue;
                JsonObject inline = new JsonObject();
                inline.addProperty("mimeType", "audio/wav");
                inline.addProperty("data", Base64.getEncoder().encodeToString(wav));
                JsonObject audioPart = new JsonObject();
                audioPart.add("inlineData", inline);
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", "Transcribe this speech exactly, in the language spoken. Output only the transcript. If there is no speech output nothing.");
                JsonArray parts = new JsonArray();
                parts.add(audioPart);
                parts.add(textPart);
                JsonObject content = new JsonObject();
                content.addProperty("role", "user");
                content.add("parts", parts);
                JsonArray contents = new JsonArray();
                contents.add(content);
                JsonObject body = new JsonObject();
                body.add("contents", contents);
                Http.Resp r = Http.postJson(gemUrl(m, mk), body.toString(), null);
                if (r.ok()) return geminiText(r.text()).trim();
                note("Gemini STT " + m, r);
                cool(id, r.status());
            }
        }
        throw new IllegalStateException("No speech-to-text model worked. " + lastError);
    }

    // ---------------------------------------------------------------- chat

    /** Providers that have a key, the preferred one first. */
    private static List<String> providerOrder() {
        List<String> out = new ArrayList<>();
        String pref = EdithConfig.get().preferredAi;
        if (pref != null && !pref.isBlank() && !pref.equalsIgnoreCase("auto") && Keys.has(pref.toLowerCase())) out.add(pref.toLowerCase());
        for (Ais.Ai a : Ais.ALL) if (Keys.has(a.id()) && !out.contains(a.id())) out.add(a.id());
        return out;
    }

    public static String chat(String system, List<Turn> history, String user) throws Exception {
        Models.ensure();
        List<String> ids = providerOrder();
        if (ids.isEmpty()) throw new IllegalStateException("No API keys yet. Add one in the G menu > AI.");
        for (String id : ids) {
            try {
                String out = switch (id) {
                    case "groq" -> tryGroq(system, history, user);
                    case "gemini" -> tryGemini(system, history, user);
                    default -> tryCompat(Ais.find(id), system, history, user);
                };
                if (out != null) return out;
            } catch (Exception e) {
                lastError = id + ": " + e.getClass().getSimpleName() + (id.equals("local") ? " (is Ollama running?)" : "");
            }
        }
        throw new IllegalStateException("Every chat model failed. " + lastError);
    }

    private static String tryGroq(String system, List<Turn> history, String user) throws Exception {
        EdithConfig cfg = EdithConfig.get();
        String gk = Keys.groq();
        for (String m : order(cfg.groqChatModel, Models.groqChat)) {
            String id = "groq:" + m;
            if (cooling(id)) continue;
            JsonArray msgs = new JsonArray();
            msgs.add(msg("system", system));
            for (Turn t : history) msgs.add(msg(t.role(), t.text()));
            msgs.add(msg("user", user));
            JsonObject body = new JsonObject();
            body.addProperty("model", m);
            body.add("messages", msgs);
            body.addProperty("temperature", 0.6);
            body.addProperty("max_completion_tokens", 600);
            Http.Resp r = Http.postJson("https://api.groq.com/openai/v1/chat/completions", body.toString(), gk);
            if (r.ok()) {
                String text = JsonParser.parseString(r.text()).getAsJsonObject().getAsJsonArray("choices").get(0)
                    .getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                lastChatModel = "groq/" + m;
                return text;
            }
            note("Groq chat " + m, r);
            cool(id, r.status());
        }
        return null;
    }

    private static String tryGemini(String system, List<Turn> history, String user) throws Exception {
        EdithConfig cfg = EdithConfig.get();
        String mk = Keys.gemini();
        for (String m : order(cfg.geminiChatModel, Models.geminiChat)) {
            String id = "gemini:" + m;
            if (cooling(id)) continue;
            JsonArray contents = new JsonArray();
            for (Turn t : history) contents.add(geminiContent(t.role().equals("assistant") ? "model" : "user", t.text()));
            contents.add(geminiContent("user", user));
            JsonObject sys = new JsonObject();
            JsonArray sp = new JsonArray();
            JsonObject st = new JsonObject();
            st.addProperty("text", system);
            sp.add(st);
            sys.add("parts", sp);
            JsonObject body = new JsonObject();
            body.add("systemInstruction", sys);
            body.add("contents", contents);
            JsonObject gen = new JsonObject();
            gen.addProperty("responseMimeType", "application/json");
            gen.addProperty("temperature", 0.6);
            body.add("generationConfig", gen);
            Http.Resp r = Http.postJson(gemUrl(m, mk), body.toString(), null);
            if (r.ok()) {
                lastChatModel = "gemini/" + m;
                return geminiText(r.text());
            }
            note("Gemini chat " + m, r);
            cool(id, r.status());
        }
        return null;
    }

    /** OpenAI, OpenRouter, Mistral, DeepSeek, xAI, Cerebras, Together (OpenAI chat format) and Anthropic (messages format). */
    private static String tryCompat(Ais.Ai ai, String system, List<Turn> history, String user) throws Exception {
        if (ai == null) return null;
        String key = Keys.get(ai.id());
        String forced = EdithConfig.get().aiModels.get(ai.id());
        for (String m : order(forced, Models.compatChat.getOrDefault(ai.id(), List.of()))) {
            String id = ai.id() + ":" + m;
            if (cooling(id)) continue;
            JsonObject body = new JsonObject();
            body.addProperty("model", m);
            JsonArray msgs = new JsonArray();
            if (ai.anthropic()) body.addProperty("system", system);
            else msgs.add(msg("system", system));
            for (Turn t : history) msgs.add(msg(t.role(), t.text()));
            msgs.add(msg("user", user));
            body.add("messages", msgs);
            if (ai.anthropic()) {
                body.addProperty("max_tokens", 700);
            } else if (ai.id().equals("openai")) {
                body.addProperty("max_completion_tokens", 2000);
            } else {
                body.addProperty("max_tokens", 700);
                body.addProperty("temperature", 0.6);
                if (ai.id().equals("local")) {
                    JsonObject fmt = new JsonObject();
                    fmt.addProperty("type", "json_object");
                    body.add("response_format", fmt);
                }
            }
            Http.Resp r = Http.postJsonH(Ais.base(ai) + (ai.anthropic() ? "/messages" : "/chat/completions"), body.toString(), Http.auth(ai, key));
            if (r.ok()) {
                JsonObject o = JsonParser.parseString(r.text()).getAsJsonObject();
                StringBuilder text = new StringBuilder();
                if (ai.anthropic()) {
                    for (JsonElement p : o.getAsJsonArray("content")) {
                        JsonObject po = p.getAsJsonObject();
                        if (po.has("text")) text.append(po.get("text").getAsString());
                    }
                } else {
                    JsonObject msg = o.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
                    if (msg.has("content") && !msg.get("content").isJsonNull()) text.append(msg.get("content").getAsString());
                }
                if (!text.toString().isBlank()) {
                    lastChatModel = ai.id() + "/" + m;
                    return text.toString();
                }
                lastError = ai.label() + " " + m + " sent an empty answer";
                cool(id, 500);
                continue;
            }
            note(ai.label() + " chat " + m, r);
            cool(id, r.status());
        }
        return null;
    }

    // ---------------------------------------------------------------- text to speech

    /** The name is read as a word, not spelled out letter by letter. */
    public static String spoken(String text) {
        return text == null ? "" : text.replace("E.D.I.T.H.", "Edith").replace("E.D.I.T.H", "Edith").replace("EDITH", "Edith");
    }

    /** Returns null when no cloud voice worked (caller falls back to the system voice). */
    public static Speech speak(String text) {
        text = spoken(text);
        boolean cache = EdithConfig.get().voiceCache && text.length() <= 240;
        if (cache) {
            for (String src : new String[]{"gemini", "groq", "openai"}) {
                if (!Keys.has(src)) continue;
                Speech hit = cacheGet(src, text);
                if (hit != null) return hit;
            }
        }
        lastTtsSource = "";
        Speech made = speakRemote(text);
        if (made != null && cache && !lastTtsSource.isEmpty()) cachePut(lastTtsSource, text, made);
        return made;
    }

    // ---- voice cache: every line is generated once, then played from disk
    public static volatile String lastTtsSource = "";

    private static java.nio.file.Path cacheDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("edith-voice-cache");
    }

    private static String cacheName(String source, String text) {
        EdithConfig c = EdithConfig.get();
        String voice = source.equals("gemini") ? c.geminiVoice : source.equals("groq") ? c.groqVoice : c.openaiVoice;
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-1").digest((source + "|" + voice + "|" + text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb + ".bin";
        } catch (Exception e) {
            return Integer.toHexString((source + voice + text).hashCode()) + ".bin";
        }
    }

    private static Speech cacheGet(String source, String text) {
        try {
            java.nio.file.Path p = cacheDir().resolve(cacheName(source, text));
            if (!java.nio.file.Files.exists(p)) return null;
            byte[] all = java.nio.file.Files.readAllBytes(p);
            if (all.length < 6) return null;
            int rate = ((all[1] & 0xFF) << 24) | ((all[2] & 0xFF) << 16) | ((all[3] & 0xFF) << 8) | (all[4] & 0xFF);
            return new Speech(java.util.Arrays.copyOfRange(all, 5, all.length), all[0] == 1, rate);
        } catch (Exception e) {
            return null;
        }
    }

    private static void cachePut(String source, String text, Speech s) {
        try {
            java.nio.file.Files.createDirectories(cacheDir());
            byte[] out = new byte[5 + s.data().length];
            out[0] = (byte) (s.wav() ? 1 : 0);
            out[1] = (byte) (s.rate() >>> 24);
            out[2] = (byte) (s.rate() >>> 16);
            out[3] = (byte) (s.rate() >>> 8);
            out[4] = (byte) s.rate();
            System.arraycopy(s.data(), 0, out, 5, s.data().length);
            java.nio.file.Files.write(cacheDir().resolve(cacheName(source, text)), out);
            java.io.File[] files = cacheDir().toFile().listFiles();
            if (files != null && files.length > 300) {
                java.util.Arrays.sort(files, java.util.Comparator.comparingLong(java.io.File::lastModified));
                for (int i = 0; i < files.length - 300; i++) files[i].delete();
            }
        } catch (Exception ignored) {
        }
    }

    public static int cacheCount() {
        String[] n = cacheDir().toFile().list();
        return n == null ? 0 : n.length;
    }

    public static void cacheClear() {
        java.io.File[] files = cacheDir().toFile().listFiles();
        if (files != null) for (java.io.File f : files) f.delete();
    }

    private static Speech speakRemote(String text) {
        try {
            Models.ensure();
            EdithConfig cfg = EdithConfig.get();
            String mk = Keys.gemini();
            if (!mk.isBlank()) {
                for (String m : order(cfg.geminiTtsModel, Models.geminiTts)) {
                    String id = "gemini:" + m;
                    if (cooling(id)) continue;
                    JsonObject part = new JsonObject();
                    part.addProperty("text", text);
                    JsonArray parts = new JsonArray();
                    parts.add(part);
                    JsonObject content = new JsonObject();
                    content.add("parts", parts);
                    JsonArray contents = new JsonArray();
                    contents.add(content);
                    JsonObject prebuilt = new JsonObject();
                    prebuilt.addProperty("voiceName", cfg.geminiVoice);
                    JsonObject voice = new JsonObject();
                    voice.add("prebuiltVoiceConfig", prebuilt);
                    JsonObject speech = new JsonObject();
                    speech.add("voiceConfig", voice);
                    JsonArray mods = new JsonArray();
                    mods.add("AUDIO");
                    JsonObject gen = new JsonObject();
                    gen.add("responseModalities", mods);
                    gen.add("speechConfig", speech);
                    JsonObject body = new JsonObject();
                    body.add("contents", contents);
                    body.add("generationConfig", gen);
                    Http.Resp r = Http.postJson(gemUrl(m, mk), body.toString(), null);
                    if (r.ok()) {
                        JsonObject inline = JsonParser.parseString(r.text()).getAsJsonObject().getAsJsonArray("candidates").get(0)
                            .getAsJsonObject().getAsJsonObject("content").getAsJsonArray("parts").get(0)
                            .getAsJsonObject().getAsJsonObject("inlineData");
                        byte[] raw = Base64.getDecoder().decode(inline.get("data").getAsString());
                        String mime = inline.has("mimeType") ? inline.get("mimeType").getAsString() : "";
                        // some models answer with a full WAV file, others with raw 16-bit PCM (rate in the mime type)
                        boolean isWav = mime.toLowerCase().contains("wav") || (raw.length > 4 && raw[0] == 'R' && raw[1] == 'I' && raw[2] == 'F' && raw[3] == 'F');
                        java.util.regex.Matcher rm = java.util.regex.Pattern.compile("rate=([0-9]+)").matcher(mime);
                        lastTtsSource = "gemini";
                        return new Speech(raw, isWav, rm.find() ? Integer.parseInt(rm.group(1)) : 24000);
                    }
                    note("Gemini TTS " + m, r);
                    cool(id, r.status());
                }
            }
            String gk = Keys.groq();
            if (!gk.isBlank()) {
                for (String m : order(cfg.groqTtsModel, Models.groqTts)) {
                    String id = "groq:" + m;
                    if (cooling(id)) continue;
                    JsonObject body = new JsonObject();
                    body.addProperty("model", m);
                    body.addProperty("input", text);
                    body.addProperty("voice", cfg.groqVoice);
                    body.addProperty("response_format", "wav");
                    Http.Resp r = Http.postJson("https://api.groq.com/openai/v1/audio/speech", body.toString(), gk);
                    if (r.ok()) {
                        lastTtsSource = "groq";
                        return new Speech(r.body(), true, 0);
                    }
                    note("Groq TTS " + m, r);
                    cool(id, r.status());
                }
            }
            String ok = Keys.get("openai");
            if (!ok.isBlank()) {
                for (String m : Models.openaiTts) {
                    String id = "openai:" + m;
                    if (cooling(id)) continue;
                    JsonObject body = new JsonObject();
                    body.addProperty("model", m);
                    body.addProperty("input", text);
                    body.addProperty("voice", cfg.openaiVoice);
                    body.addProperty("response_format", "wav");
                    Http.Resp r = Http.postJson("https://api.openai.com/v1/audio/speech", body.toString(), ok);
                    if (r.ok()) {
                        lastTtsSource = "openai";
                        return new Speech(r.body(), true, 0);
                    }
                    note("OpenAI TTS " + m, r);
                    cool(id, r.status());
                }
            }
        } catch (Exception e) {
            lastError = "TTS: " + e.getMessage();
        }
        return null;
    }

    // ---------------------------------------------------------------- helpers

    private static String gemUrl(String model, String key) {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;
    }

    private static JsonObject msg(String role, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", text);
        return o;
    }

    private static JsonObject geminiContent(String role, String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject c = new JsonObject();
        c.addProperty("role", role);
        c.add("parts", parts);
        return c;
    }

    private static String geminiText(String json) {
        JsonArray parts = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("candidates").get(0).getAsJsonObject()
            .getAsJsonObject("content").getAsJsonArray("parts");
        StringBuilder sb = new StringBuilder();
        for (JsonElement p : parts) if (p.getAsJsonObject().has("text")) sb.append(p.getAsJsonObject().get("text").getAsString());
        return sb.toString();
    }

    /** Remembers why a call failed, without ever including the key. */
    private static void note(String what, Http.Resp r) {
        String body = r.text();
        if (body.length() > 160) body = body.substring(0, 160);
        lastError = what + " -> HTTP " + r.status() + " " + body.replaceAll("key=[A-Za-z0-9_-]+", "key=***");
    }
}
