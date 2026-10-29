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


edit(j + "EdithConfig.java", [("    /** EDITH answers out loud. */", """    /** Heal automatically with splash/drinkable healing, golden apples (hotbar) when health is at or below autoHealAt (half hearts). */
    public boolean autoHeal = true;
    public float autoHealAt = 8.0f;
    /** EDITH answers out loud. */""")])

edit(j + "EdithGlassesClient.java", [
    ("        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Guard::pre);",
     "        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Guard::pre);\n"
     "        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.AutoHeal::pre);"),
    ("            com.example.edithglasses.feature.Guard.tick(mc);",
     "            com.example.edithglasses.feature.Guard.tick(mc);\n            com.example.edithglasses.feature.AutoHeal.tick(mc);"),
    ('                .then(ClientCommands.literal("track")',
     '                .then(ClientCommands.literal("heal")\n'
     '                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().autoHeal = true; EdithConfig.save(); say("Auto heal ON at " + EdithConfig.get().autoHealAt / 2 + " hearts"); return 1; }))\n'
     '                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().autoHeal = false; EdithConfig.save(); say("Auto heal OFF"); return 1; }))\n'
     '                    .then(ClientCommands.literal("at").then(ClientCommands.argument("hearts", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(1f, 10f)).executes(c -> {\n'
     '                        EdithConfig.get().autoHealAt = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "hearts") * 2f;\n'
     '                        EdithConfig.save();\n'
     '                        say("Auto heal at " + EdithConfig.get().autoHealAt / 2 + " hearts");\n'
     '                        return 1;\n'
     '                    }))))\n'
     '                .then(ClientCommands.literal("track")'),
])
edit(j + "feature/HudExtras.java", [
    ("        if (Guard.enabled()) {", '        if (AutoHeal.enabled()) { g.text(font, "AUTO-HEAL", l + 8, ly, 0xFF70FF90, false); ly += 10; }\n        if (Guard.enabled()) {')])
print("patched")
