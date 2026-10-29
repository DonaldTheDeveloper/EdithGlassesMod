import os
base = os.path.expanduser("~/EdithGlassesMod/src/main")
j = base + "/java/com/example/edithglasses/"


def edit(path, pairs):
    s = open(path, encoding="utf-8").read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit("missing anchor in %s: %s" % (path, a[:60]))
        s = s.replace(a, b, 1)
    open(path, "w", encoding="utf-8").write(s)


edit(j + "EdithConfig.java", [("    /** EDITH answers out loud. */", """    /** X-ray ore overlay (AR markers through walls). Many servers forbid this. */
    public boolean xray = false;
    /** Thermal vision also marks ores. */
    public boolean thermalShowsOres = true;
    public int xrayRadius = 18;
    /** EDITH answers out loud. */""")])

edit(j + "feature/HudExtras.java", [
    ('        // labels under "ONLINE"', '        OreScan.draw(g, font, w, h);\n\n        // labels under "ONLINE"'),
    ("        if (Glasses.thermalActive()) {", '        if (OreScan.active()) { g.text(font, "X-RAY", l + 8, ly, 0xFF40E8FF, false); ly += 10; }\n        if (Glasses.thermalActive()) {'),
])

edit(j + "EdithGlassesClient.java", [
    ("            com.example.edithglasses.feature.Alerts.tick(mc);",
     "            com.example.edithglasses.feature.Alerts.tick(mc);\n            com.example.edithglasses.feature.OreScan.tick(mc);\n            while (xrayKey.consumeClick()) if (Glasses.equipped(mc.player)) setXray(!EdithConfig.get().xray);"),
    ("        KeyMapping photoKey", """        KeyMapping xrayKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.edithglasses.xray", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
        KeyMapping photoKey"""),
    ('                .then(ClientCommands.literal("photo")', """                .then(ClientCommands.literal("xray")
                    .executes(c -> { setXray(!EdithConfig.get().xray); return 1; })
                    .then(ClientCommands.literal("on").executes(c -> { setXray(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setXray(false); return 1; })))
                .then(ClientCommands.literal("photo")"""),
    ("    private static void setThermal(boolean on) {", """    private static void setXray(boolean on) {
        EdithConfig.get().xray = on;
        EdithConfig.save();
        say("EDITH x-ray: " + (on ? "ON - ores are marked through walls (many servers ban this, use single player)" : "OFF"));
    }

    private static void setThermal(boolean on) {"""),
    ('say("EDITH thermal vision: " + (on ? "ON (many servers forbid this - check the rules)" : "OFF"));',
     'say("EDITH thermal vision: " + (on ? "ON" + (EdithConfig.get().thermalShowsOres ? " (also marks ores - many servers forbid this)" : " (many servers forbid this)") : "OFF"));'),
])

edit(base + "/resources/assets/edithglasses/lang/en_us.json", [
    ('"key.edithglasses.photo"', '"key.edithglasses.xray": "Toggle EDITH x-ray ore overlay (glasses on)",\n  "key.edithglasses.photo"')])
edit(base + "/resources/edithglasses.mixins.json", [('"TeamColorMixin"', '"TeamColorMixin", "CameraAccessor"')])
print("patched")
