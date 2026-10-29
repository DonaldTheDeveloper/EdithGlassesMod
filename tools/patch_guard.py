import os
base = os.path.expanduser("~/EdithGlassesMod/src/main")
j = base + "/java/com/example/edithglasses/"


def edit(path, pairs):
    s = open(path, encoding="utf-8").read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit("missing anchor in %s: %s" % (path, a[:70]))
        s = s.replace(a, b, 1)
    open(path, "w", encoding="utf-8").write(s)


edit(j + "EdithConfig.java", [("    /** EDITH answers out loud. */", """    /** Sentinel: run away from players with a sword/axe/spear/trident/mace/wind charge (and incoming wind charges). */
    public boolean guard = false;
    /** 0 = always while the glasses are on, otherwise only after this many seconds without any input from you. */
    public int guardAfkSeconds = 0;
    /** Comma separated player names the Sentinel ignores. */
    public String guardFriends = "";
    /** EDITH answers out loud. */""")])

edit(j + "EdithGlassesClient.java", [
    ("        ClientTickEvents.END_CLIENT_TICK.register(mc -> {",
     "        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Guard::pre);\n"
     "        KeyMapping guardKey = KeyMappingHelper.registerKeyMapping(\n"
     "            new KeyMapping(\"key.edithglasses.guard\", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));\n\n"
     "        ClientTickEvents.END_CLIENT_TICK.register(mc -> {\n"
     "            while (guardKey.consumeClick()) if (Glasses.equipped(mc.player)) setGuard(!EdithConfig.get().guard);\n"
     "            com.example.edithglasses.feature.Guard.tick(mc);"),
    ('                .then(ClientCommands.literal("xray")', """                .then(ClientCommands.literal("guard")
                    .executes(c -> { setGuard(!EdithConfig.get().guard); return 1; })
                    .then(ClientCommands.literal("on").executes(c -> { setGuard(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setGuard(false); return 1; }))
                    .then(ClientCommands.literal("status").executes(c -> { say(com.example.edithglasses.feature.Guard.describe()); return 1; }))
                    .then(ClientCommands.literal("afk").then(ClientCommands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3600)).executes(c -> {
                        EdithConfig.get().guardAfkSeconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seconds");
                        EdithConfig.save();
                        say(com.example.edithglasses.feature.Guard.describe());
                        return 1;
                    })))
                    .then(ClientCommands.literal("friend")
                        .then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                            String n = StringArgumentType.getString(c, "name");
                            EdithConfig.get().guardFriends = (EdithConfig.get().guardFriends == null || EdithConfig.get().guardFriends.isBlank() ? "" : EdithConfig.get().guardFriends + ",") + n;
                            EdithConfig.save();
                            say(com.example.edithglasses.feature.Guard.describe());
                            return 1;
                        })))
                        .then(ClientCommands.literal("clear").executes(c -> { EdithConfig.get().guardFriends = ""; EdithConfig.save(); say("Sentinel friends cleared"); return 1; }))))
                .then(ClientCommands.literal("xray")"""),
    ("    private static void setXray(boolean on) {", """    private static void setGuard(boolean on) {
        EdithConfig.get().guard = on;
        EdithConfig.save();
        say("EDITH Sentinel: " + (on ? "ON - you will run from players with weapons, a mace or wind charges. Automatic movement is cheating on many servers." : "OFF"));
    }

    private static void setXray(boolean on) {"""),
])

edit(j + "feature/HudExtras.java", [
    ("        if (OreScan.active()) {", '        if (Guard.enabled()) { g.text(font, Guard.active() ? "SENTINEL" : "SENTINEL (standby)", l + 8, ly, 0xFF9FE870, false); ly += 10; }\n        if (OreScan.active()) {')])
edit(base + "/resources/assets/edithglasses/lang/en_us.json", [
    ('"key.edithglasses.photo"', '"key.edithglasses.guard": "Toggle EDITH Sentinel (run from weapons, glasses on)",\n  "key.edithglasses.photo"')])
print("patched")
