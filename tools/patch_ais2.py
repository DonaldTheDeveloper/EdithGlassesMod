import os
j = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/")
path = j + "ai/Providers.java"
s = open(path, encoding="utf-8").read()


def rep(s, a, b):
    assert a in s, "missing: " + a[:80]
    return s.replace(a, b, 1)


# ---------------------------------------------------------------- chat: provider loop
a = s.index("    public static String chat(String system")
b = s.index("    // ---------------------------------------------------------------- text to speech")
new_chat = '''    /** Providers that have a key, the preferred one first. */
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
            String out = switch (id) {
                case "groq" -> tryGroq(system, history, user);
                case "gemini" -> tryGemini(system, history, user);
                default -> tryCompat(Ais.find(id), system, history, user);
            };
            if (out != null) return out;
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
            }
            Http.Resp r = Http.postJsonH(ai.base() + (ai.anthropic() ? "/messages" : "/chat/completions"), body.toString(), Http.auth(ai, key));
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

'''
s = s[:a] + new_chat + s[b:]

# ---------------------------------------------------------------- speech to text: OpenAI between Groq and Gemini
s = rep(s, '''        String mk = Keys.gemini();
        if (!mk.isBlank()) {
            for (String m : order(cfg.geminiChatModel, Models.geminiChat)) {
                String id = "gemini:" + m;
                if (cooling(id)) continue;
                JsonObject inline = new JsonObject();''', '''        String ok = Keys.get("openai");
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
                JsonObject inline = new JsonObject();''')

# ---------------------------------------------------------------- text to speech: cache + openai
s = rep(s, '''    public static Speech speak(String text) {
        text = spoken(text);
        try {''', '''    public static Speech speak(String text) {
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
        try {''')
s = rep(s, "                        return new Speech(raw, isWav, rm.find() ? Integer.parseInt(rm.group(1)) : 24000);",
        "                        lastTtsSource = \"gemini\";\n                        return new Speech(raw, isWav, rm.find() ? Integer.parseInt(rm.group(1)) : 24000);")
s = rep(s, "                    if (r.ok()) return new Speech(r.body(), true, 0);\n                    note(\"Groq TTS \" + m, r);\n                    cool(id, r.status());\n                }\n            }\n",
        "                    if (r.ok()) {\n                        lastTtsSource = \"groq\";\n                        return new Speech(r.body(), true, 0);\n                    }\n                    note(\"Groq TTS \" + m, r);\n                    cool(id, r.status());\n                }\n            }\n"
        "            String ok = Keys.get(\"openai\");\n"
        "            if (!ok.isBlank()) {\n"
        "                for (String m : Models.openaiTts) {\n"
        "                    String id = \"openai:\" + m;\n"
        "                    if (cooling(id)) continue;\n"
        "                    JsonObject body = new JsonObject();\n"
        "                    body.addProperty(\"model\", m);\n"
        "                    body.addProperty(\"input\", text);\n"
        "                    body.addProperty(\"voice\", cfg.openaiVoice);\n"
        "                    body.addProperty(\"response_format\", \"wav\");\n"
        "                    Http.Resp r = Http.postJson(\"https://api.openai.com/v1/audio/speech\", body.toString(), ok);\n"
        "                    if (r.ok()) {\n"
        "                        lastTtsSource = \"openai\";\n"
        "                        return new Speech(r.body(), true, 0);\n"
        "                    }\n"
        "                    note(\"OpenAI TTS \" + m, r);\n"
        "                    cool(id, r.status());\n"
        "                }\n"
        "            }\n")
open(path, "w", encoding="utf-8").write(s)

# ---------------------------------------------------------------- other callers
p = j + "feature/Intro.java"
t = open(p, encoding="utf-8").read()
t = t.replace("!Keys.groq().isBlank() || !Keys.gemini().isBlank()", "Keys.any()")
open(p, "w", encoding="utf-8").write(t)
print("patched")
