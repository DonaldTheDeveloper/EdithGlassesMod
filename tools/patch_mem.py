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
def config(s):
    s = rep(s, "    /** Hologram terrain map on the HUD. */",
            "    /** Your own identification tags: player name (lower case) -> \"label|colour\". */\n"
            "    public java.util.Map<String, String> playerTags = new java.util.HashMap<>();\n"
            "    /** Hologram terrain map on the HUD. */")
    s = rep(s, "    private static Path path() {",
            "    /** Light shaping that keeps each voice's own character (the film filter pulls every voice towards one sound). */\n"
            "    public static final String LIGHT_FILTER = \"highpass=f=80,acompressor=threshold=-18dB:ratio=2:attack=5:release=80:makeup=1\";\n\n"
            "    /** Picks a Gemini voice. Kore is the one the film filter was fitted to; every other voice gets the light filter so it really sounds different. */\n"
            "    public static void applyVoice(String name) {\n"
            "        EdithConfig c = get();\n"
            "        c.geminiVoice = name;\n"
            "        c.voiceFilter = name.equalsIgnoreCase(\"Kore\") ? new EdithConfig().voiceFilter : LIGHT_FILTER;\n"
            "        save();\n"
            "    }\n\n"
            "    private static Path path() {")
    return s


rw("EdithConfig.java", config)


# ------------------------------------------------------------------ FaceId: custom tags
def faceid(s):
    s = rep(s, "    // ---------------------------------------------------------------- drawing",
            '''    // ---------------------------------------------------------------- custom tags (your own identification labels)

    public static final String[] COLOR_NAMES = {"", "red", "green", "amber", "blue", "white", "purple", "orange"};

    /** {label, colour name} of a tagged player, or null. */
    public static String[] tagOf(String name) {
        String v = EdithConfig.get().playerTags.get(name.toLowerCase(Locale.ROOT));
        if (v == null) return null;
        int i = v.lastIndexOf('|');
        return i < 0 ? new String[]{v, ""} : new String[]{v.substring(0, i), v.substring(i + 1)};
    }

    public static void setTag(String name, String label, String color) {
        String l = label.trim().replace("|", "");
        if (l.length() > 24) l = l.substring(0, 24);
        EdithConfig.get().playerTags.put(name.trim().toLowerCase(Locale.ROOT), l + "|" + (color == null ? "" : color));
        EdithConfig.save();
    }

    public static String removeTag(String name) {
        boolean had = EdithConfig.get().playerTags.remove(name.trim().toLowerCase(Locale.ROOT)) != null;
        EdithConfig.save();
        return had ? "Tag removed from " + name + "." : name + " has no tag.";
    }

    public static String listTags() {
        var t = EdithConfig.get().playerTags;
        if (t.isEmpty()) return "No tags yet. Use /edith tag set <player> <tag> or G > Tools > Tag a player.";
        StringBuilder sb = new StringBuilder("Tags: ");
        t.forEach((k, v) -> sb.append(k).append(" = ").append(v.contains("|") ? v.substring(0, v.lastIndexOf('|')) : v).append(", "));
        return sb.substring(0, sb.length() - 2);
    }

    /** Colour for a tag: the one you picked, else guessed from the words (enemy red, friend green, leader gold). */
    public static int colorValue(String label, String color) {
        switch (color == null ? "" : color) {
            case "red": return RED;
            case "green": return GREEN;
            case "amber": return AMBER;
            case "blue": return 0xFF60D0FF;
            case "white": return 0xFFFFFFFF;
            case "purple": return 0xFFB070FF;
            case "orange": return 0xFFFF9A40;
            default: break;
        }
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (l.matches(".*(enemy|hostile|rival|wanted|threat|griefer|hacker|thief|traitor|bandit|danger).*")) return RED;
        if (l.matches(".*(friend|ally|buddy|teammate|mate|family|bro|homie).*")) return GREEN;
        if (l.matches(".*(leader|law|admin|owner|mod|boss|sheriff|police|king|queen|captain|chief|mayor|general|commander).*")) return AMBER;
        if (l.matches(".*(trader|merchant|shop|vendor|neutral).*")) return 0xFF60D0FF;
        return 0xFFFFFFFF;
    }

    /** Colour of a player's dot on the hologram map. */
    public static int dotColor(Player p) {
        String[] t = tagOf(p.getName().getString());
        if (t != null) return colorValue(t[0], t[1]);
        return isFriend(p) ? GREEN : 0xFFFFFFFF;
    }

    // ---------------------------------------------------------------- drawing''')
    s = rep(s, '''            int col = !done ? LINE : friend ? GREEN : duel ? RED : tracked ? AMBER : 0xFFFFFFFF;
            String status = friend ? "FRIEND" : duel ? "DUEL OPPONENT" : tracked ? "TRACKED" : "UNKNOWN";''',
            '''            String[] tag = tagOf(name);
            int col = !done ? LINE : tag != null ? colorValue(tag[0], tag[1]) : friend ? GREEN : duel ? RED : tracked ? AMBER : 0xFFFFFFFF;
            String status = tag != null ? tag[0].toUpperCase(Locale.ROOT) : friend ? "FRIEND" : duel ? "DUEL OPPONENT" : tracked ? "TRACKED" : "UNKNOWN";''')
    return s


rw("feature/FaceId.java", faceid)
rw("feature/HoloMap.java", lambda s: rep(s, "(FaceId.isFriend(pl) ? GREEN : 0xFFFFFFFF)", "FaceId.dotColor(pl)"))


# ------------------------------------------------------------------ waypoints: guide to coordinates
rw("feature/Waypoints.java", lambda s: rep(s, "    public static String delete(String name) {",
    '''    /** A waypoint at given coordinates (for "take me to my golden apples") that is tracked right away. */
    public static String setAt(Minecraft mc, String name, double x, double y, double z, String dim) {
        if (name == null || name.isBlank()) name = "target";
        WP w = new WP();
        w.name = name.trim();
        w.dim = dim == null || dim.isBlank() ? dimension(mc) : dim;
        w.x = x;
        w.y = y;
        w.z = z;
        s().points.put(key(name), w);
        s().tracked = key(name);
        save();
        return "Guiding you to " + w.name + ".";
    }

    public static String delete(String name) {'''))


# ------------------------------------------------------------------ assistant
def assistant(s):
    s = rep(s, '''         "waypoint": null or {"action": "set" | "delete" | "track" | "untrack", "name": "short name"}}''',
            '''         "waypoint": null or {"action": "set" | "delete" | "track" | "untrack" | "goto", "name": "short name", "x": 0, "y": 0, "z": 0},
         "remember": null or "a short note to save (it is stored together with the position where the user said it)",
         "forget": null or "a keyword: saved notes that contain it are deleted"}''')
    s = rep(s, '''        - Only set "chat" when''', '''        - MEMORY: the GAME STATE may contain MEMORY lines: notes the user asked you to remember, and chests, barrels and shulker boxes the user opened with what was inside. Use them to answer "where did I put ..." or "do I have ..." (say the container and its coordinates and how far away it is). Say honestly when nothing is remembered. Never invent a location.
        - When the user asks you to remember something, set "remember" to a short note, and confirm in "say". To delete a note set "forget" to a keyword.
        - When the user wants to be taken or guided to a remembered place, set "waypoint" to {"action": "goto", "name": short name, "x":..., "y":..., "z":...} using the coordinates from MEMORY. It runs immediately.
        - Only set "chat" when''')
    s = rep(s, '''            String wpAction = null, wpName = null;''', '''            String wpAction = null, wpName = null, remember = null, forget = null;
            double wx = 0, wy = 0, wz = 0;
            boolean wpHasXyz = false;''')
    s = rep(s, '''                    wpName = str(o.getAsJsonObject("waypoint"), "name");
                }''', '''                    wpName = str(o.getAsJsonObject("waypoint"), "name");
                    JsonObject wo = o.getAsJsonObject("waypoint");
                    if (wo.has("x") && wo.has("z") && wo.get("x").isJsonPrimitive() && wo.get("z").isJsonPrimitive()) {
                        wx = wo.get("x").getAsDouble();
                        wy = wo.has("y") && wo.get("y").isJsonPrimitive() ? wo.get("y").getAsDouble() : 64;
                        wz = wo.get("z").getAsDouble();
                        wpHasXyz = true;
                    }
                }
                remember = str(o, "remember");
                forget = str(o, "forget");''')
    s = rep(s, '''            if (wpAction != null) {
                say = switch (wpAction) {''', '''            if (remember != null && !remember.isBlank()) com.example.edithglasses.feature.Memory.addNote(mc, remember);
            if (forget != null && !forget.isBlank()) com.example.edithglasses.feature.Memory.forget(forget);
            if (wpAction != null) {
                say = switch (wpAction) {
                    case "goto" -> wpHasXyz ? com.example.edithglasses.feature.Waypoints.setAt(mc, wpName, wx, wy, wz, null) : "I don't have coordinates for that.";''')
    s = rep(s, '''String user = "GAME STATE:\\n" + GameContext.build(mc) + "\\nUSER SAID: " + text;''',
            '''String user = "GAME STATE:\\n" + GameContext.build(mc) + com.example.edithglasses.feature.Memory.context(mc, text) + "\\nUSER SAID: " + text;''')
    return s


rw("ai/Assistant.java", assistant)


# ------------------------------------------------------------------ client: tick + commands + voice command
def client(s):
    s = rep(s, "            com.example.edithglasses.feature.HoloMap.tick(mc);\n",
            "            com.example.edithglasses.feature.HoloMap.tick(mc);\n            com.example.edithglasses.feature.Memory.tick(mc);\n")
    s = rep(s, '''                    EdithConfig.get().geminiVoice = StringArgumentType.getString(c, "name");
                    EdithConfig.save();''', '''                    EdithConfig.applyVoice(StringArgumentType.getString(c, "name"));''')
    s = rep(s, '                .then(ClientCommands.literal("intro")',
            '                .then(ClientCommands.literal("tag")\n'
            '                    .then(ClientCommands.literal("list").executes(c -> { say(com.example.edithglasses.feature.FaceId.listTags()); return 1; }))\n'
            '                    .then(ClientCommands.literal("remove").then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> { say(com.example.edithglasses.feature.FaceId.removeTag(StringArgumentType.getString(c, "player"))); return 1; })))\n'
            '                    .then(ClientCommands.literal("set").then(ClientCommands.argument("player", StringArgumentType.word()).then(ClientCommands.argument("tag", StringArgumentType.greedyString()).executes(c -> {\n'
            '                        String p = StringArgumentType.getString(c, "player"), t = StringArgumentType.getString(c, "tag");\n'
            '                        com.example.edithglasses.feature.FaceId.setTag(p, t, "");\n'
            '                        say(p + " is now tagged " + t.toUpperCase() + "."); return 1; }))))\n'
            '                    .then(ClientCommands.literal("color").then(ClientCommands.argument("player", StringArgumentType.word()).then(ClientCommands.argument("color", StringArgumentType.word()).executes(c -> {\n'
            '                        String p = StringArgumentType.getString(c, "player"), col = StringArgumentType.getString(c, "color").toLowerCase();\n'
            '                        String[] t = com.example.edithglasses.feature.FaceId.tagOf(p);\n'
            '                        if (t == null) { say(p + " has no tag yet. Use /edith tag set first."); return 1; }\n'
            '                        com.example.edithglasses.feature.FaceId.setTag(p, t[0], col.equals("auto") ? "" : col);\n'
            '                        say("Tag colour of " + p + ": " + col + " (red, green, amber, blue, white, purple, orange or auto)."); return 1; }))))\n'
            '                    .then(ClientCommands.literal("target").then(ClientCommands.argument("tag", StringArgumentType.greedyString()).executes(c -> {\n'
            '                        if (com.example.edithglasses.Glasses.target() instanceof net.minecraft.world.entity.player.Player pl) {\n'
            '                            String t = StringArgumentType.getString(c, "tag");\n'
            '                            com.example.edithglasses.feature.FaceId.setTag(pl.getName().getString(), t, "");\n'
            '                            say(pl.getName().getString() + " is now tagged " + t.toUpperCase() + ".");\n'
            '                        } else say("Look at a player first.");\n'
            '                        return 1; }))))\n'
            '                .then(ClientCommands.literal("remember").then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> { say(com.example.edithglasses.feature.Memory.addNote(Minecraft.getInstance(), StringArgumentType.getString(c, "text"))); return 1; })))\n'
            '                .then(ClientCommands.literal("forget").then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> { say(com.example.edithglasses.feature.Memory.forget(StringArgumentType.getString(c, "text"))); return 1; })))\n'
            '                .then(ClientCommands.literal("find").then(ClientCommands.argument("item", StringArgumentType.greedyString()).executes(c -> { say(com.example.edithglasses.feature.Memory.findText(Minecraft.getInstance(), StringArgumentType.getString(c, "item"))); return 1; })))\n'
            '                .then(ClientCommands.literal("memory").executes(c -> { say(com.example.edithglasses.feature.Memory.listNotes() + "  (" + com.example.edithglasses.feature.Memory.boxCount() + " containers remembered)"); return 1; }))\n'
            '                .then(ClientCommands.literal("intro")')
    return s


rw("EdithGlassesClient.java", client)


# ------------------------------------------------------------------ menu
def menu(s):
    s = rep(s, '    private static final String[] VOICES = {"Kore", "Sulafat", "Aoede", "Leda", "Erinome", "Zephyr", "Achernar", "Despina"};',
            '''    private static final String[][] VOICE_INFO = {
        {"Kore", "firm"}, {"Sulafat", "warm"}, {"Aoede", "breezy"}, {"Leda", "youthful"}, {"Erinome", "clear"}, {"Zephyr", "bright"},
        {"Achernar", "soft"}, {"Despina", "smooth"}, {"Callirrhoe", "easy-going"}, {"Autonoe", "bright"}, {"Gacrux", "mature"},
        {"Vindemiatrix", "gentle"}, {"Laomedeia", "upbeat"}, {"Pulcherrima", "forward"}, {"Achird", "friendly"}, {"Schedar", "even"},
        {"Puck", "upbeat, male"}, {"Charon", "informative, male"}, {"Fenrir", "excitable, male"}, {"Orus", "firm, male"},
        {"Enceladus", "breathy, male"}, {"Iapetus", "clear, male"}, {"Umbriel", "easy-going, male"}, {"Algieba", "smooth, male"},
        {"Algenib", "gravelly, male"}, {"Rasalgethi", "informative, male"}, {"Alnilam", "firm, male"}, {"Zubenelgenubi", "casual, male"},
        {"Sadachbia", "lively, male"}, {"Sadaltager", "knowledgeable, male"}};
    private static final String[] VOICES = java.util.Arrays.stream(VOICE_INFO).map(v -> v[0]).toArray(String[]::new);''')
    a = s.index('        voice.add(cycle("Voice", "Which Gemini voice EDITH uses."')
    b = s.index('        voice.add(toggle("Digital shimmer"')
    s = s[:a] + '''        voice.add(cycle("Voice", "Which Gemini voice EDITH uses (30 to choose from, female and male). Picking Kore uses the film filter, any other voice uses the light filter so it really sounds different.",
            () -> { for (String[] v : VOICE_INFO) if (v[0].equalsIgnoreCase(c.geminiVoice)) return v[0] + " - " + v[1]; return c.geminiVoice; },
            () -> EdithConfig.applyVoice(VOICES[(indexOf(VOICES, c.geminiVoice) + 1) % VOICES.length]),
            () -> EdithConfig.applyVoice(VOICES[(indexOf(VOICES, c.geminiVoice) + VOICES.length - 1) % VOICES.length])));
        voice.add(action("Preview voice", "Hear the current voice and filter.", "PLAY",
            () -> com.example.edithglasses.ai.Assistant.announce(minecraft, "Hello. This is how I sound.")));
        voice.add(cycle("Voice filter", "FILM is tuned to the film EDITH and pulls every voice towards that one sound. LIGHT keeps each voice's own character. OFF is the plain voice. Needs ffmpeg.",
            () -> c.voiceFilter == null || c.voiceFilter.isBlank() ? "OFF" : c.voiceFilter.equals(new EdithConfig().voiceFilter) ? "FILM" : "LIGHT",
            () -> c.voiceFilter = (c.voiceFilter == null || c.voiceFilter.isBlank()) ? new EdithConfig().voiceFilter : c.voiceFilter.equals(new EdithConfig().voiceFilter) ? EdithConfig.LIGHT_FILTER : "",
            () -> c.voiceFilter = (c.voiceFilter == null || c.voiceFilter.isBlank()) ? EdithConfig.LIGHT_FILTER : c.voiceFilter.equals(new EdithConfig().voiceFilter) ? "" : new EdithConfig().voiceFilter));
''' + s[b:]
    s = rep(s, '        tools.add(action("Add friend"',
            '        tools.add(action("Tag a player", "Give a player your own identification tag such as Law Leader or Enemy. Facial recognition then shows it instead of UNKNOWN. " + c.playerTags.size() + " tagged.", "TAG", () -> minecraft.gui.setScreen(new TagScreen(this))));\n'
            '        tools.add(action("Memory", com.example.edithglasses.feature.Memory.noteCount() + " notes and " + com.example.edithglasses.feature.Memory.boxCount() + " containers remembered. Ask EDITH where you put something. Click to forget everything.", "FORGET ALL", () -> com.example.edithglasses.feature.Memory.forget("all")));\n'
            '        tools.add(action("Add friend"')
    return s


rw("EdithMenuScreen.java", menu)
print("patched")
