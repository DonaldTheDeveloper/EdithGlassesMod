import os
j = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/")


def rw(p, f):
    s = open(j + p, encoding="utf-8").read()
    s = f(s)
    open(j + p, "w", encoding="utf-8").write(s)


def rep(s, a, b):
    assert a in s, a[:70]
    return s.replace(a, b, 1)


rw("EdithConfig.java", lambda s: rep(s, "    /** Hologram terrain map on the HUD. */",
    "    /** Moved HUD parts: part name -> {dx, dy} pixels (see HudLayout). */\n    public java.util.Map<String, int[]> hudOffsets = new java.util.HashMap<>();\n    /** Hologram terrain map on the HUD. */"))


def hud(s):
    s = rep(s, "        title(g, font, cx, time);\n", "        HudLayout.begin(g, HudLayout.El.TITLE);\n        title(g, font, cx, time);\n        HudLayout.end(g);\n")
    s = rep(s, "        telemetry(g, font, mc, p, m + 6, 52);\n        radar(g, font, mc, p, w - m - 44, 66, 30, time);\n",
            "        HudLayout.begin(g, HudLayout.El.TELEMETRY);\n        telemetry(g, font, mc, p, m + 6, 52);\n        HudLayout.end(g);\n"
            "        HudLayout.begin(g, HudLayout.El.RADAR);\n        radar(g, font, mc, p, w - m - 44, 66, 30, time);\n        HudLayout.end(g);\n")
    s = rep(s, "        tabs(g, font, cx, h - 52);\n        if (EdithConfig.get().hideVanillaHud) hotbar(g, font, p, cx, h - m - 20);\n",
            "        HudLayout.begin(g, HudLayout.El.TABS);\n        tabs(g, font, cx, h - 52);\n        HudLayout.end(g);\n"
            "        if (EdithConfig.get().hideVanillaHud) {\n            HudLayout.begin(g, HudLayout.El.HOTBAR);\n            hotbar(g, font, p, cx, h - m - 20);\n            HudLayout.end(g);\n        }\n")
    s = rep(s, "        com.example.edithglasses.feature.HoloMap.draw(g, font, w - m - 6 - 140, 108, 140, 96);\n",
            "        HudLayout.begin(g, HudLayout.El.HOLOMAP);\n        com.example.edithglasses.feature.HoloMap.draw(g, font, w - m - 6 - 140, 108, 140, 96);\n        HudLayout.end(g);\n")
    s = rep(s, "        drawAssistant(g, font, w, h - m - 62, cx, time);\n    }",
            "        HudLayout.begin(g, HudLayout.El.ASSISTANT);\n        drawAssistant(g, font, w, h - m - 62, cx, time);\n        HudLayout.end(g);\n    }")
    s = rep(s, "        // data card, bottom left\n        int cw = 104", "        // data card, bottom left\n        HudLayout.begin(g, HudLayout.El.TARGET);\n        int cw = 104")
    i = s.index("        // data card, bottom left")
    k = s.index("    // ------------------------------------------------------------------ bottom: mode tabs")
    block = s[i:k]
    cut = block.rstrip().rfind("    }")
    block = block[:cut] + "        HudLayout.end(g);\n    }\n\n"
    s = s[:i] + block + s[k:]
    s = rep(s, "        if (mc.player == null || !Glasses.equipped(mc.player)) return false;\n",
            "        if (HudLayout.editing()) return mc.player != null;\n        if (mc.player == null || !Glasses.equipped(mc.player)) return false;\n")
    return s


rw("EdithHud.java", hud)

rw("mixin/HudMixin.java", lambda s: rep(s,
    "        if (!this.minecraft.options.getCameraType().isFirstPerson()) return;\n        if (!Glasses.equipped(this.minecraft.player)) return;",
    "        if (!com.example.edithglasses.HudLayout.editing()) {\n            if (!this.minecraft.options.getCameraType().isFirstPerson()) return;\n            if (!Glasses.equipped(this.minecraft.player)) return;\n        }"))

rw("EdithMenuScreen.java", lambda s: rep(s, '        tools.add(action("Add friend"',
    '        tools.add(action("Move HUD parts", "Drag the radar, map, hotbar, tabs and other HUD parts to where you want them.", "EDIT", () -> minecraft.gui.setScreen(new HudEditScreen(this))));\n        tools.add(action("Add friend"'))

rw("EdithGlassesClient.java", lambda s: rep(s, '                .then(ClientCommands.literal("intro")',
    '                .then(ClientCommands.literal("layout")\n'
    '                    .executes(c -> { Minecraft mc = Minecraft.getInstance(); mc.schedule(() -> mc.gui.setScreen(new HudEditScreen(null))); return 1; })\n'
    '                    .then(ClientCommands.literal("reset").executes(c -> { HudLayout.resetAll(); say("HUD layout reset."); return 1; })))\n'
    '                .then(ClientCommands.literal("intro")'))
print("patched")
