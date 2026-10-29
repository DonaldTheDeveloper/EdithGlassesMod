import os, re
base = os.path.expanduser("~/EdithGlassesMod/src/main")
j = base + "/java/com/example/edithglasses/"


def edit(path, pairs):
    s = open(path, encoding="utf-8").read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit("missing anchor in %s: %s" % (path, a[:70]))
        s = s.replace(a, b, 1)
    open(path, "w", encoding="utf-8").write(s)


# ---- Photo: delayed request so the menu is gone before the picture
edit(j + "feature/Photo.java", [
    ("    public static void request() {\n        if (stage == 0) stage = 1;\n    }",
     "    public static void request() {\n        if (stage == 0) stage = 1;\n    }\n\n"
     "    /** Take the photo after this many ticks (lets a menu close first). */\n"
     "    public static void requestAfter(int ticks) {\n        if (stage == 0) stage = -ticks;\n    }"),
    ("    public static boolean overlay() {\n        return stage > 0;\n    }", "    public static boolean overlay() {\n        return stage > 0;\n    }"),
    ("        if (stage == 0 || mc.level == null) return;\n        stage++;",
     "        if (stage == 0 || mc.level == null) return;\n        if (stage < 0) {\n            stage++;\n            if (stage == 0) stage = 1;\n            return;\n        }\n        stage++;"),
])

# ---- AutoHeal: heal right now on demand
edit(j + "feature/AutoHeal.java", [
    ("    public static void tick(Minecraft mc) {",
     "    private static boolean force = false;\n\n"
     "    /** Heal now with whatever is in the hotbar, whatever your health is. */\n"
     "    public static void healNow(Minecraft mc) {\n        force = true;\n        cooldown = 0;\n    }\n\n"
     "    public static void tick(Minecraft mc) {"),
    ("        if (!enabled() || mc.gui.screen() != null || me.isSpectator() || me.isCreative() || cooldown > 0) return;\n        float low = EdithConfig.get().autoHealAt;\n        if (me.getHealth() > low) return;",
     "        boolean now = force;\n        if (now && mc.gui.screen() == null) force = false;\n"
     "        if (mc.gui.screen() != null || me.isSpectator() || me.isCreative() || cooldown > 0) return;\n"
     "        if (!now && !enabled()) return;\n"
     "        float low = EdithConfig.get().autoHealAt;\n        if (!now && me.getHealth() > low) return;"),
])

# ---- client: one menu key instead of many
s = open(j + "EdithGlassesClient.java", encoding="utf-8").read()
for name in ("nightKey", "thermalKey", "xrayKey", "photoKey", "reconKey", "guardKey"):
    s = re.sub(r"\n        KeyMapping %s = KeyMappingHelper\.registerKeyMapping\(\n[^\n]*\n" % name, "\n", s)
for line in (
    "            while (guardKey.consumeClick()) if (Glasses.equipped(mc.player)) setGuard(!EdithConfig.get().guard);\n",
    "            while (thermalKey.consumeClick()) if (Glasses.equipped(mc.player)) setThermal(!EdithConfig.get().thermal);\n",
    "            while (photoKey.consumeClick()) if (Glasses.equipped(mc.player)) com.example.edithglasses.feature.Photo.request();\n",
    "            while (reconKey.consumeClick()) {\n                if (com.example.edithglasses.feature.Recon.active() || Glasses.equipped(mc.player)) com.example.edithglasses.feature.Recon.toggle(mc);\n            }\n",
    "            while (xrayKey.consumeClick()) if (Glasses.equipped(mc.player)) setXray(!EdithConfig.get().xray);\n",
    "            while (nightKey.consumeClick()) {\n                if (Glasses.equipped(mc.player)) setNight(!EdithConfig.get().nightVision);\n            }\n",
):
    if line not in s:
        raise SystemExit("missing line: " + line[:60])
    s = s.replace(line, "", 1)
anchor = "        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Guard::pre);"
assert anchor in s
s = s.replace(anchor, """        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.edithglasses.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));
""" + anchor, 1)
anchor2 = "            com.example.edithglasses.feature.Guard.tick(mc);"
s = s.replace(anchor2, "            while (menuKey.consumeClick()) if (mc.gui.screen() == null) mc.gui.setScreen(new EdithMenuScreen(null));\n" + anchor2, 1)
s = s.replace('                .then(ClientCommands.literal("hud")', '                .then(ClientCommands.literal("menu").executes(c -> { Minecraft mc = Minecraft.getInstance(); mc.schedule(() -> mc.gui.setScreen(new EdithMenuScreen(null))); return 1; }))\n                .then(ClientCommands.literal("hud")', 1)
open(j + "EdithGlassesClient.java", "w", encoding="utf-8").write(s)

edit(base + "/resources/assets/edithglasses/lang/en_us.json", [
    ('"key.edithglasses.ptt"', '"key.edithglasses.menu": "Open the EDITH menu (all features)",\n  "key.edithglasses.ptt"')])
print("patched")
