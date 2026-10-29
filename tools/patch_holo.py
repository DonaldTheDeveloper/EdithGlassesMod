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


edit(j + "EdithConfig.java", [("    /** EDITH answers out loud. */", """    /** Hologram terrain map on the HUD. */
    public boolean holoMap = true;
    public int holoRadius = 20;
    /** Facial recognition: scan boxes and identification tags on players in view. */
    public boolean faceId = true;
    /** Boot animation when the glasses go on (and EDITH greets you if introVoice). */
    public boolean intro = true;
    public boolean introVoice = true;
    /** EDITH answers out loud. */""")])

edit(j + "mixin/HudMixin.java", [
    ("        if (EdithHud.visible()) EdithHud.draw(graphics);",
     "        if (EdithHud.visible()) EdithHud.draw(graphics);\n"
     "        com.example.edithglasses.feature.Intro.draw(graphics, this.minecraft.font, graphics.guiWidth(), graphics.guiHeight());")])

edit(j + "EdithHud.java", [
    ("        target(g, font, mc, p, w, h, m);\n",
     "        target(g, font, mc, p, w, h, m);\n"
     "        com.example.edithglasses.feature.FaceId.draw(g, font, w, h);\n"
     "        com.example.edithglasses.feature.HoloMap.draw(g, font, w - m - 6 - 118, 108, 118, 78);\n")])

edit(j + "EdithGlassesClient.java", [
    ("            com.example.edithglasses.feature.Clutch.tick(mc);\n",
     "            com.example.edithglasses.feature.Clutch.tick(mc);\n"
     "            com.example.edithglasses.feature.HoloMap.tick(mc);\n"
     "            com.example.edithglasses.feature.Intro.tick(mc);\n"),
    ('                .then(ClientCommands.literal("clutch")',
     '                .then(ClientCommands.literal("map")\n'
     '                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().holoMap = true; EdithConfig.save(); say("Hologram map ON"); return 1; }))\n'
     '                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().holoMap = false; EdithConfig.save(); say("Hologram map OFF"); return 1; }))\n'
     '                    .then(ClientCommands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 28)).executes(c -> { EdithConfig.get().holoRadius = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "radius"); EdithConfig.save(); say("Hologram map radius: " + EdithConfig.get().holoRadius); return 1; })))\n'
     '                .then(ClientCommands.literal("face")\n'
     '                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().faceId = true; EdithConfig.save(); say("Facial recognition ON"); return 1; }))\n'
     '                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().faceId = false; EdithConfig.save(); say("Facial recognition OFF"); return 1; })))\n'
     '                .then(ClientCommands.literal("friend")\n'
     '                    .then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> { say(com.example.edithglasses.feature.FaceId.add(StringArgumentType.getString(c, "name"))); return 1; })))\n'
     '                    .then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> { say(com.example.edithglasses.feature.FaceId.remove(StringArgumentType.getString(c, "name"))); return 1; })))\n'
     '                    .then(ClientCommands.literal("list").executes(c -> { say(com.example.edithglasses.feature.FaceId.list()); return 1; }))\n'
     '                    .then(ClientCommands.literal("clear").executes(c -> { com.example.edithglasses.feature.FaceId.clear(); say("Friends cleared"); return 1; }))\n'
     '                    .then(ClientCommands.literal("target").executes(c -> {\n'
     '                        if (com.example.edithglasses.Glasses.target() instanceof net.minecraft.world.entity.player.Player pl) say(com.example.edithglasses.feature.FaceId.add(pl.getName().getString()));\n'
     '                        else say("Look at a player first.");\n'
     '                        return 1; })))\n'
     '                .then(ClientCommands.literal("intro").executes(c -> { Minecraft mc = Minecraft.getInstance(); mc.execute(() -> com.example.edithglasses.feature.Intro.play(mc)); return 1; }))\n'
     '                .then(ClientCommands.literal("clutch")'),
])

edit(j + "EdithMenuScreen.java", [
    ('        vision.add(toggle("Threat alerts"',
     '        vision.add(toggle("Hologram map", "A small 3D terrain model of your surroundings on the HUD. It turns with you and shows players, mobs and caves.", () -> c.holoMap, () -> c.holoMap = !c.holoMap));\n'
     '        vision.add(slider("Map radius", "How many blocks around you the hologram map covers.", 8, 28, 2, () -> c.holoRadius, v -> c.holoRadius = (int) v, v -> (int) v + " blocks"));\n'
     '        vision.add(toggle("Facial recognition", "Scan boxes on players in view. After a short scan EDITH tags them FRIEND or UNKNOWN with distance, health and what they hold.", () -> c.faceId, () -> c.faceId = !c.faceId));\n'
     '        vision.add(toggle("Boot animation", "The film-style start-up sequence when you put the glasses on.", () -> c.intro, () -> c.intro = !c.intro));\n'
     '        vision.add(toggle("Threat alerts"'),
    ('        tools.add(action("Photo"',
     '        tools.add(action("Add friend", "Adds the player you are looking at to your friends list (recognition tags them FRIEND and the Sentinel ignores them).", "ADD", () -> {\n'
     '            if (com.example.edithglasses.Glasses.target() instanceof net.minecraft.world.entity.player.Player pl) com.example.edithglasses.feature.FaceId.add(pl.getName().getString());\n'
     '        }));\n'
     '        tools.add(action("Play boot animation", "Plays the start-up sequence now.", "PLAY", () -> { onClose(); com.example.edithglasses.feature.Intro.play(minecraft); }));\n'
     '        tools.add(action("Photo"'),
])
print("patched")
