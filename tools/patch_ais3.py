import os
j = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/")


def rw(p, f):
    s = open(j + p, encoding="utf-8").read()
    s = f(s)
    open(j + p, "w", encoding="utf-8").write(s)


def rep(s, a, b):
    assert a in s, "missing: " + a[:80]
    return s.replace(a, b, 1)


def menu(s):
    s = rep(s, '{"VISION", "PROTECTION", "TOOLS", "VOICE", "SYSTEM"}', '{"VISION", "PROTECTION", "TOOLS", "VOICE", "AI", "SYSTEM"}')
    s = rep(s, "        List<Ctl> sys = new ArrayList<>();", '''        // voice cache
        voice.add(toggle("Save voice lines", "Every line EDITH speaks is generated once and then replayed from your disk, so repeats cost nothing.",
            () -> c.voiceCache, () -> c.voiceCache = !c.voiceCache));
        voice.add(action("Clear voice cache", com.example.edithglasses.ai.Providers.cacheCount() + " saved lines. Clearing makes EDITH generate them again next time.", "CLEAR",
            () -> com.example.edithglasses.ai.Providers.cacheClear()));

        List<Ctl> aiPage = new ArrayList<>();
        java.util.List<String> prefIds = new ArrayList<>();
        prefIds.add("auto");
        for (var a : com.example.edithglasses.ai.Ais.ALL) prefIds.add(a.id());
        aiPage.add(cycle("Preferred AI", "Which AI EDITH asks first. The others are used automatically when it fails or runs out. Auto tries them in the order below.",
            () -> c.preferredAi.toUpperCase(),
            () -> c.preferredAi = prefIds.get((Math.max(0, prefIds.indexOf(c.preferredAi.toLowerCase())) + 1) % prefIds.size()),
            () -> c.preferredAi = prefIds.get((Math.max(0, prefIds.indexOf(c.preferredAi.toLowerCase())) + prefIds.size() - 1) % prefIds.size())));
        for (var a : com.example.edithglasses.ai.Ais.ALL) {
            boolean has = com.example.edithglasses.ai.Keys.has(a.id());
            aiPage.add(action(a.label() + (has ? "  [saved]" : ""),
                (has ? "Key saved. Click to change or remove it." : "No key yet. Click to paste one. Get it at " + a.site() + ".")
                    + (a.id().equals("groq") || a.id().equals("gemini") ? " Also does voice." : a.id().equals("openai") ? " Also does voice." : ""),
                has ? "CHANGE" : "ADD KEY", () -> minecraft.gui.setScreen(new KeyEntryScreen(this, a))));
        }
        aiPage.add(action("Check models", "Ask every AI with a key which models it offers right now (EDITH always picks the newest).", "CHECK", () -> {
            Thread t = new Thread(com.example.edithglasses.ai.Models::refresh, "edith-models");
            t.setDaemon(true);
            t.start();
        }));
        pages.add(aiPage);

        List<Ctl> sys = new ArrayList<>();''')
    return s


rw("EdithMenuScreen.java", menu)


def client(s):
    s = rep(s, '''        boolean g = !Keys.groq().isBlank(), m = !Keys.gemini().isBlank();
        say("Keys: Groq " + (g ? "found" : "MISSING") + ", Gemini " + (m ? "found" : "MISSING") + " (" + Keys.path() + ")");''',
        '''        StringBuilder ks = new StringBuilder();
        for (var a : com.example.edithglasses.ai.Ais.ALL) ks.append(a.label()).append(Keys.has(a.id()) ? " yes, " : " no, ");
        say("Keys: " + ks + "(add them in the G menu > AI, or in " + Keys.path() + ")");''')
    s = rep(s, '                .then(ClientCommands.literal("models")',
        '                .then(ClientCommands.literal("voicecache")\n'
        '                    .then(ClientCommands.literal("clear").executes(c -> { com.example.edithglasses.ai.Providers.cacheClear(); say("Voice cache cleared."); return 1; }))\n'
        '                    .executes(c -> { say("Voice cache: " + com.example.edithglasses.ai.Providers.cacheCount() + " saved lines (EDITH replays these instead of generating them again)."); return 1; }))\n'
        '                .then(ClientCommands.literal("ai")\n'
        '                    .then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {\n'
        '                        String n = StringArgumentType.getString(c, "name").toLowerCase();\n'
        '                        if (!n.equals("auto") && com.example.edithglasses.ai.Ais.find(n) == null) { say("Unknown AI. Use auto or one of: groq, gemini, openai, anthropic, openrouter, mistral, deepseek, xai, cerebras, together."); return 1; }\n'
        '                        EdithConfig.get().preferredAi = n; EdithConfig.save(); say("EDITH asks " + n + " first."); return 1; })))\n'
        '                .then(ClientCommands.literal("models")')
    return s


rw("EdithGlassesClient.java", client)
print("patched")
