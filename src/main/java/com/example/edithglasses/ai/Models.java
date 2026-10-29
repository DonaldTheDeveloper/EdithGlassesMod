package com.example.edithglasses.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks each provider which models exist right now and ranks them, so nothing is hard-coded to an old model.
 * Lists are best-first; callers walk them for automatic fail-over.
 */
public final class Models {
    public static volatile List<String> groqChat = List.of();
    public static volatile List<String> groqStt = List.of();
    public static volatile List<String> groqTts = List.of();
    public static volatile List<String> geminiChat = List.of();
    public static volatile List<String> geminiTts = List.of();
    /** Chat models of the OpenAI-style providers and Anthropic, by provider id (best first, a few each). */
    public static volatile java.util.Map<String, List<String>> compatChat = java.util.Map.of();
    public static volatile List<String> openaiStt = List.of();
    public static volatile List<String> openaiTts = List.of();
    public static volatile String error = "";
    private static long lastRefresh = 0;

    private Models() {
    }

    public static synchronized void ensure() {
        long age = System.currentTimeMillis() - lastRefresh;
        boolean any = !groqChat.isEmpty() || !geminiChat.isEmpty() || !compatChat.isEmpty();
        boolean localMissing = com.example.edithglasses.EdithConfig.get().localAi && compatChat.getOrDefault("local", List.of()).isEmpty();
        if (age > 3_600_000L || (!any && age > 60_000L) || (localMissing && age > 120_000L)) refresh();
    }

    public static synchronized void refresh() {
        lastRefresh = System.currentTimeMillis();
        StringBuilder err = new StringBuilder();
        String gk = Keys.groq();
        if (!gk.isBlank()) {
            try {
                Http.Resp r = Http.get("https://api.groq.com/openai/v1/models", gk);
                if (r.ok()) parseGroq(JsonParser.parseString(r.text()).getAsJsonObject().getAsJsonArray("data"));
                else err.append("Groq model list HTTP ").append(r.status()).append(". ");
            } catch (Exception e) {
                err.append("Groq model list failed: ").append(e.getMessage()).append(". ");
            }
        }
        String mk = Keys.gemini();
        if (!mk.isBlank()) {
            try {
                Http.Resp r = Http.get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=" + mk, null);
                if (r.ok()) parseGemini(JsonParser.parseString(r.text()).getAsJsonObject().getAsJsonArray("models"));
                else err.append("Gemini model list HTTP ").append(r.status()).append(". ");
            } catch (Exception e) {
                err.append("Gemini model list failed: ").append(e.getMessage()).append(". ");
            }
        }
        refreshCompat(err);
        error = err.toString();
    }

    private record Listed(String id, long created) {
    }

    private static boolean junk(String id) {
        for (String bad : new String[]{"embed", "moderation", "image", "dall", "sora", "realtime", "audio", "search", "instruct", "davinci",
            "babbage", "rerank", "guard", "vision", "codex", "deep-research", "computer-use", "-pro", "ocr", "safeguard"}) {
            if (id.contains(bad)) return true;
        }
        return id.matches("o\\d.*");   // reasoning-only families answer slowly and eat the token budget
    }

    private static void refreshCompat(StringBuilder err) {
        java.util.Map<String, List<String>> out = new java.util.HashMap<>();
        for (Ais.Ai ai : Ais.ALL) {
            if (!ai.compat() || !Keys.has(ai.id())) continue;
            try {
                Http.Resp r = Http.getH(Ais.base(ai) + "/models", Http.auth(ai, Keys.get(ai.id())));
                if (!r.ok()) {
                    err.append(ai.label()).append(" model list HTTP ").append(r.status()).append(". ");
                    continue;
                }
                JsonArray data = JsonParser.parseString(r.text()).getAsJsonObject().getAsJsonArray("data");
                List<Listed> all = new ArrayList<>();
                for (JsonElement e : data) {
                    JsonObject o = e.getAsJsonObject();
                    if (!o.has("id")) continue;
                    long created = 0;
                    try {
                        if (o.has("created") && o.get("created").isJsonPrimitive()) created = (long) o.get("created").getAsDouble();
                        else if (o.has("created_at")) created = java.time.Instant.parse(o.get("created_at").getAsString()).getEpochSecond();
                    } catch (Exception ignored) {
                    }
                    all.add(new Listed(o.get("id").getAsString(), created));
                }
                all.sort(Comparator.comparingLong(Listed::created).reversed());
                List<String> chat = new ArrayList<>(), stt = new ArrayList<>(), tts = new ArrayList<>();
                for (Listed m : all) {
                    String id = m.id().toLowerCase(Locale.ROOT);
                    if (id.contains("transcribe") || id.contains("whisper")) stt.add(m.id());
                    else if (id.contains("tts")) tts.add(m.id());
                    else if (junk(id)) continue;
                    else chat.add(m.id());
                }
                if (ai.anthropic()) {
                    chat.sort(Comparator.comparingInt((String id) -> id.contains("sonnet") ? 0 : id.contains("haiku") ? 1 : 2));
                } else {
                    chat.sort(Comparator.comparingInt((String id) -> small(id) ? 1 : 0));
                }
                out.put(ai.id(), List.copyOf(chat.subList(0, Math.min(4, chat.size()))));
                if (ai.id().equals("openai")) {
                    openaiStt = List.copyOf(stt);
                    openaiTts = List.copyOf(tts);
                }
            } catch (Exception e) {
                err.append(ai.label()).append(" model list failed: ").append(e.getMessage()).append(". ");
            }
        }
        compatChat = out;
    }

    private record GroqModel(String id, long created) {
    }

    private static void parseGroq(JsonArray data) {
        List<GroqModel> all = new ArrayList<>();
        for (JsonElement e : data) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("active") && !o.get("active").getAsBoolean()) continue;
            all.add(new GroqModel(o.get("id").getAsString(), o.has("created") ? o.get("created").getAsLong() : 0));
        }
        all.sort(Comparator.comparingLong(GroqModel::created).reversed());
        List<String> chat = new ArrayList<>(), stt = new ArrayList<>(), tts = new ArrayList<>();
        for (GroqModel m : all) {
            String id = m.id().toLowerCase(Locale.ROOT);
            if (id.contains("whisper")) stt.add(m.id());
            else if (id.contains("tts") || id.contains("orpheus")) tts.add(m.id());
            else if (id.contains("guard") || id.contains("embed") || id.contains("compound")) continue;
            else chat.add(m.id());
        }
        // newest first, but small/instant models go behind the bigger ones (stable sort keeps the date order)
        chat.sort(Comparator.comparingInt((String id) -> small(id) ? 1 : 0));
        stt.sort(Comparator.comparingInt((String id) -> id.contains("turbo") ? 0 : 1));
        groqChat = chat;
        groqStt = stt;
        groqTts = tts;
    }

    private static boolean small(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        return s.contains("8b") || s.contains("instant") || s.contains("1b") || s.contains("3b") || s.contains("mini");
    }

    private static final Pattern GEMINI = Pattern.compile("gemini-(\\d+(?:\\.\\d+)?)-(flash|pro)(.*)");

    private record GeminiModel(String name, double ver, int tier, int stable) {
    }

    private static void parseGemini(JsonArray models) {
        List<GeminiModel> chat = new ArrayList<>(), tts = new ArrayList<>();
        for (JsonElement e : models) {
            JsonObject o = e.getAsJsonObject();
            String name = o.get("name").getAsString().replace("models/", "");
            if (!o.has("supportedGenerationMethods") || !o.get("supportedGenerationMethods").toString().contains("generateContent")) continue;
            Matcher m = GEMINI.matcher(name);
            if (!m.matches()) continue;
            String rest = m.group(3);
            double ver = Double.parseDouble(m.group(1));
            int tier = m.group(2).equals("flash") ? (rest.contains("lite") ? 1 : 3) : 2; // flash > pro > flash-lite (speed)
            int stable = (rest.contains("preview") || rest.contains("exp")) ? 0 : 1;
            GeminiModel g = new GeminiModel(name, ver, tier, stable);
            if (rest.contains("tts")) tts.add(g);
            else if (rest.contains("image") || rest.contains("live") || rest.contains("embed") || rest.contains("audio")
                || rest.contains("robotics") || rest.contains("computer") || rest.contains("thinking")) continue;
            else chat.add(g);
        }
        Comparator<GeminiModel> cmp = Comparator.comparingDouble(GeminiModel::ver)
            .thenComparingInt(GeminiModel::tier).thenComparingInt(GeminiModel::stable).reversed();
        chat.sort(cmp);
        tts.sort(cmp);
        geminiChat = chat.stream().map(GeminiModel::name).toList();
        geminiTts = tts.stream().map(GeminiModel::name).toList();
    }
}
