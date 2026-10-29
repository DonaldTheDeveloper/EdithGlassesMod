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


edit(j + "EdithConfig.java", [("    /** EDITH answers out loud. */", """    /** Water bucket clutch: place water just before landing from a dangerous fall (bucket from hotbar or inventory). */
    public boolean clutch = true;
    /** EDITH answers out loud. */""")])

edit(j + "EdithGlassesClient.java", [
    ("            com.example.edithglasses.feature.Guard.tick(mc);",
     "            com.example.edithglasses.feature.Clutch.tick(mc);\n            com.example.edithglasses.feature.Guard.tick(mc);"),
    ('                .then(ClientCommands.literal("heal")',
     '                .then(ClientCommands.literal("clutch")\n'
     '                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().clutch = true; EdithConfig.save(); say("Water bucket clutch ON"); return 1; }))\n'
     '                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().clutch = false; EdithConfig.save(); say("Water bucket clutch OFF"); return 1; })))\n'
     '                .then(ClientCommands.literal("heal")'),
])

edit(j + "EdithMenuScreen.java", [
    ('        prot.add(toggle("Fight hostile mobs"',
     '        prot.add(toggle("Water bucket clutch", "When a fall would hurt, EDITH places water just before you land and picks it up again. Takes the bucket from your inventory if it is not in the hotbar.",\n'
     '            () -> c.clutch, () -> c.clutch = !c.clutch));\n'
     '        prot.add(toggle("Fight hostile mobs"')])

edit(j + "EdithHud.java", [
    ('{"SNT", Guard.enabled()}, {"HEL", AutoHeal.enabled()}, {"ALR", c.alerts},',
     '{"SNT", Guard.enabled()}, {"HEL", AutoHeal.enabled()}, {"CLT", com.example.edithglasses.feature.Clutch.enabled()}, {"ALR", c.alerts},')])
print("patched")
