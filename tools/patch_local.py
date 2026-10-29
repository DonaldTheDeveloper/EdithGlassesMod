import os
j = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/")


def rw(p, f):
    s = open(j + p, encoding="utf-8").read()
    s = f(s)
    open(j + p, "w", encoding="utf-8").write(s)


def rep(s, a, b):
    assert a in s, "missing: " + a[:80]
    return s.replace(a, b, 1)


# ---- config
rw("EdithConfig.java", lambda s: rep(s, "    /** Hologram terrain map on the HUD. */",
    "    /** Local AI (Ollama or any OpenAI-compatible server on this PC or another one). No key needed. */\n"
    "    public boolean localAi = false;\n"
    "    public String localUrl = \"http://localhost:11434/v1\";\n"
    "    /** Hologram terrain map on the HUD. */"))

# ---- registry
def ais(s):
    s = rep(s, '        new Ai("together", "Together AI", "https://api.together.xyz/v1", "api.together.ai", false));',
            '        new Ai("together", "Together AI", "https://api.together.xyz/v1", "api.together.ai", false),\n'
            '        new Ai("local", "Local AI (Ollama)", "http://localhost:11434/v1", "ollama.com", false));')
    s = rep(s, "    public static Ai find(String id) {",
            "    /** Where to send requests: the address from the settings for the local AI, the fixed one for the others. */\n"
            "    public static String base(Ai ai) {\n"
            "        if (ai.id().equals(\"local\")) {\n"
            "            String u = com.example.edithglasses.EdithConfig.get().localUrl;\n"
            "            u = u == null || u.isBlank() ? ai.base() : u.trim();\n"
            "            return u.endsWith(\"/\") ? u.substring(0, u.length() - 1) : u;\n"
            "        }\n"
            "        return ai.base();\n"
            "    }\n\n"
            "    public static Ai find(String id) {")
    return s


rw("ai/Ais.java", ais)

# ---- keys: the local AI has no key, it is simply switched on
def keys(s):
    s = rep(s, "    public static String get(String id) {\n        try {",
            "    public static String get(String id) {\n"
            "        if (id.equals(\"local\")) return com.example.edithglasses.EdithConfig.get().localAi ? \"local\" : \"\";\n"
            "        try {")
    s = rep(s, "    public static synchronized void set(String id, String value) {\n        try {",
            "    public static synchronized void set(String id, String value) {\n"
            "        if (id.equals(\"local\")) return;\n"
            "        try {")
    return s


rw("ai/Keys.java", keys)

rw("ai/Models.java", lambda s: rep(s, "ai.base() + \"/models\"", "Ais.base(ai) + \"/models\""))


def providers(s):
    s = rep(s, "Http.postJsonH(ai.base() + (ai.anthropic()", "Http.postJsonH(Ais.base(ai) + (ai.anthropic()")
    # a provider that is down (connection refused, timeout) must not stop the others from being tried
    s = rep(s, '''            String out = switch (id) {
                case "groq" -> tryGroq(system, history, user);
                case "gemini" -> tryGemini(system, history, user);
                default -> tryCompat(Ais.find(id), system, history, user);
            };
            if (out != null) return out;''', '''            try {
                String out = switch (id) {
                    case "groq" -> tryGroq(system, history, user);
                    case "gemini" -> tryGemini(system, history, user);
                    default -> tryCompat(Ais.find(id), system, history, user);
                };
                if (out != null) return out;
            } catch (Exception e) {
                lastError = id + ": " + e.getClass().getSimpleName() + (id.equals("local") ? " (is Ollama running?)" : "");
            }''')
    s = rep(s, '''            } else {
                body.addProperty("max_tokens", 700);
                body.addProperty("temperature", 0.6);
            }''', '''            } else {
                body.addProperty("max_tokens", 700);
                body.addProperty("temperature", 0.6);
                if (ai.id().equals("local")) {
                    JsonObject fmt = new JsonObject();
                    fmt.addProperty("type", "json_object");
                    body.add("response_format", fmt);
                }
            }''')
    return s


rw("ai/Providers.java", providers)


# ---- key screen doubles as the local AI settings
def keyscreen(s):
    s = rep(s, '''        key.addFormatter((text, first) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));''',
            '''        if (ai.id().equals("local")) {
            key.setMaxLength(200);
            key.setHint(Component.literal("http://localhost:11434/v1"));
            key.setValue(EdithConfig.get().localUrl);
        } else {
            key.addFormatter((text, first) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        }''')
    s = rep(s, '''        text(g, font, "API KEY", px + 14, py + 8, DIM, 0.6f);''', '''        boolean local = ai.id().equals("local");
        text(g, font, local ? "LOCAL MODEL" : "API KEY", px + 14, py + 8, DIM, 0.6f);''')
    s = rep(s, '''        String status = has ? "KEY SAVED" : "NO KEY";''', '''        String status = local ? (has ? "ON" : "OFF") : has ? "KEY SAVED" : "NO KEY";''')
    s = rep(s, '''        text(g, font, "Get a key at " + ai.site() + ". It is saved only on this computer.", px + 14, py + 30 - 6, DIM, 0.55f);''',
            '''        text(g, font, local ? "Address of the Ollama server. Install it from ollama.com, then run: ollama pull qwen3:4b" : "Get a key at " + ai.site() + ". It is saved only on this computer.", px + 14, py + 30 - 6, DIM, 0.55f);''')
    s = rep(s, '''        String k = key.getValue().trim();
        String m = model.getValue().trim();''', '''        String k = key.getValue().trim();
        String m = model.getValue().trim();
        if (ai.id().equals("local")) {
            EdithConfig.get().localUrl = k.isEmpty() ? "http://localhost:11434/v1" : k;
            EdithConfig.get().localAi = true;
            k = "";
        }''')
    s = rep(s, '''        if (k.isEmpty() && !Keys.has(ai.id())) {''', '''        if (k.isEmpty() && !Keys.has(ai.id()) && !ai.id().equals("local")) {''')
    s = rep(s, '''        key.setValue("");
        message = "Saved. Checking the key...";''', '''        if (!ai.id().equals("local")) key.setValue("");
        message = ai.id().equals("local") ? "Saved and switched on. Looking for the server..." : "Saved. Checking the key...";''')
    s = rep(s, '''                message = "Key works. EDITH can use " + ai.label() + " now.";''',
            '''                message = ai.id().equals("local") ? "Found " + Models.compatChat.get("local").size() + " model(s). EDITH can think locally now."
                    : "Key works. EDITH can use " + ai.label() + " now.";''')
    s = rep(s, '''            Keys.set(ai.id(), "");
            message = "Key removed.";''', '''            Keys.set(ai.id(), "");
            if (ai.id().equals("local")) EdithConfig.get().localAi = false;
            EdithConfig.save();
            message = ai.id().equals("local") ? "Local AI switched off." : "Key removed.";''')
    s = rep(s, '''                message = "Saved, but the check failed" + (why.isEmpty() ? "." : ": " + (why.length() > 70 ? why.substring(0, 70) : why));''',
            '''                message = ai.id().equals("local") ? "No model found. Is Ollama running and did you pull a model?"
                    : "Saved, but the check failed" + (why.isEmpty() ? "." : ": " + (why.length() > 70 ? why.substring(0, 70) : why));''')
    return s


rw("KeyEntryScreen.java", keyscreen)


# ---- menu rows
def menu(s):
    s = rep(s, '''            aiPage.add(action(a.label() + (has ? "  [saved]" : ""),
                (has ? "Key saved. Click to change or remove it." : "No key yet. Click to paste one. Get it at " + a.site() + ".")''',
            '''            boolean local = a.id().equals("local");
            aiPage.add(action(a.label() + (has ? (local ? "  [on]" : "  [saved]") : ""),
                (local ? (has ? "On. Runs a model on your own PC, no internet or key needed for thinking (the voice still comes from a cloud voice). Click to change the address or switch off."
                    : "Runs a model on this PC. Install Ollama (ollama.com), run: ollama pull qwen3:4b, then click here to switch it on. Slower and less smart than the cloud AIs, so it works best as a fallback.")
                    : has ? "Key saved. Click to change or remove it." : "No key yet. Click to paste one. Get it at " + a.site() + ".")''')
    s = rep(s, '''                has ? "CHANGE" : "ADD KEY", () ->''', '''                has ? "CHANGE" : (local ? "SET UP" : "ADD KEY"), () ->''')
    return s


rw("EdithMenuScreen.java", menu)
print("patched")
