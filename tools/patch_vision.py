import os
j = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/")


def edit(path, pairs):
    s = open(path, encoding="utf-8").read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit("missing anchor in %s: %s" % (path, a[:70]))
        s = s.replace(a, b, 1)
    open(path, "w", encoding="utf-8").write(s)


edit(j + "feature/OreScan.java", [
    ("        EdithConfig c = EdithConfig.get();\n        return Glasses.equipped() && (c.xray || (c.thermal && c.thermalShowsOres));",
     "        return Glasses.equipped() && EdithConfig.get().xray;"),
])
edit(j + "EdithConfig.java", [
    ("    /** Thermal vision also marks ores. */\n    public boolean thermalShowsOres = true;\n", ""),
    ("    /** X-ray ore overlay (AR markers through walls). Many servers forbid this. */",
     "    /** X-ray = ores only (AR markers through walls). Thermal vision = living things only. They are independent. Many servers forbid both. */"),
])
edit(j + "EdithGlassesClient.java", [
    ('say("EDITH thermal vision: " + (on ? "ON" + (EdithConfig.get().thermalShowsOres ? " (also marks ores - many servers forbid this)" : " (many servers forbid this)") : "OFF"));',
     'say("EDITH thermal vision (living things): " + (on ? "ON - many servers forbid this" : "OFF"));'),
    ('say("EDITH x-ray: " + (on ? "ON - ores are marked through walls (many servers ban this, use single player)" : "OFF"));',
     'say("EDITH x-ray (ores): " + (on ? "ON - many servers ban this, use single player" : "OFF"));'),
    ('                .then(ClientCommands.literal("xray")', """                .then(ClientCommands.literal("vision")
                    .then(ClientCommands.literal("both").executes(c -> { setThermal(true); setXray(true); return 1; }))
                    .then(ClientCommands.literal("thermal").executes(c -> { setThermal(true); setXray(false); return 1; }))
                    .then(ClientCommands.literal("xray").executes(c -> { setThermal(false); setXray(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setThermal(false); setXray(false); return 1; })))
                .then(ClientCommands.literal("xray")"""),
])
print("patched")
