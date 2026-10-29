import os
j = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/")


def rw(p, f):
    s = open(j + p, encoding="utf-8").read()
    s = f(s)
    open(j + p, "w", encoding="utf-8").write(s)


def rep(s, a, b):
    assert a in s, "missing: " + a[:80]
    return s.replace(a, b, 1)


# ------------------------------------------------------------------ config
rw("EdithConfig.java", lambda s: rep(s, "    /** Hologram terrain map on the HUD. */",
    "    /** Which AI to try first: auto (groq, gemini, openai ... in that order) or a provider id such as openai or anthropic. */\n"
    "    public String preferredAi = \"auto\";\n"
    "    /** Optional model per provider (provider id -> model id). Empty = the newest suitable model from the provider's own list. */\n"
    "    public java.util.Map<String, String> aiModels = new java.util.HashMap<>();\n"
    "    public String openaiVoice = \"nova\";\n"
    "    /** Keep every spoken line on disk and replay it instead of generating it again. */\n"
    "    public boolean voiceCache = true;\n"
    "    /** Hologram terrain map on the HUD. */"))

# ------------------------------------------------------------------ http
def http(s):
    return rep(s, "    /** Minimal multipart upload", '''    static java.util.Map<String, String> auth(Ais.Ai ai, String key) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        if (ai.anthropic()) {
            m.put("x-api-key", key);
            m.put("anthropic-version", "2023-06-01");
        } else {
            m.put("Authorization", "Bearer " + key);
        }
        return m;
    }

    static Resp getH(String url, java.util.Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    static Resp postJsonH(String url, String json, java.util.Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        return send(b.build());
    }

    /** Minimal multipart upload''')
rw("ai/Http.java", http)

# ------------------------------------------------------------------ models
def models(s):
    s = rep(s, "    public static volatile String error = \"\";",
        "    /** Chat models of the OpenAI-style providers and Anthropic, by provider id (best first, a few each). */\n"
        "    public static volatile java.util.Map<String, List<String>> compatChat = java.util.Map.of();\n"
        "    public static volatile List<String> openaiStt = List.of();\n"
        "    public static volatile List<String> openaiTts = List.of();\n"
        "    public static volatile String error = \"\";")
    s = rep(s, "        if (System.currentTimeMillis() - lastRefresh > 3_600_000L || (groqChat.isEmpty() && geminiChat.isEmpty())) refresh();",
        "        long age = System.currentTimeMillis() - lastRefresh;\n"
        "        boolean any = !groqChat.isEmpty() || !geminiChat.isEmpty() || !compatChat.isEmpty();\n"
        "        if (age > 3_600_000L || (!any && age > 60_000L)) refresh();")
    s = rep(s, "        error = err.toString();\n    }",
        "        refreshCompat(err);\n        error = err.toString();\n    }\n\n" + '''    private record Listed(String id, long created) {
    }

    private static boolean junk(String id) {
        for (String bad : new String[]{"embed", "moderation", "image", "dall", "sora", "realtime", "audio", "search", "instruct", "davinci",
            "babbage", "rerank", "guard", "vision", "codex", "deep-research", "computer-use", "-pro", "ocr", "safeguard"}) {
            if (id.contains(bad)) return true;
        }
        return id.matches("o\\\\d.*");   // reasoning-only families answer slowly and eat the token budget
    }

    private static void refreshCompat(StringBuilder err) {
        java.util.Map<String, List<String>> out = new java.util.HashMap<>();
        for (Ais.Ai ai : Ais.ALL) {
            if (!ai.compat() || !Keys.has(ai.id())) continue;
            try {
                Http.Resp r = Http.getH(ai.base() + "/models", Http.auth(ai, Keys.get(ai.id())));
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
    }''')
    return s


rw("ai/Models.java", models)
