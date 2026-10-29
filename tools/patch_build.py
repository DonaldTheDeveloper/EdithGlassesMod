import os
import re
j = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/")
res = os.path.expanduser("~/EdithGlassesMod/src/main/resources/")


def rw(p, f, base=None):
    path = (base or j) + p
    s = open(path, encoding="utf-8").read()
    s = f(s)
    open(path, "w", encoding="utf-8").write(s)


def rep(s, a, b):
    assert a in s, "missing: " + a[:80]
    return s.replace(a, b, 1)


# ---- mixin registration
def mixins(s):
    return rep(s, '"UseItemMixin"', '"UseItemMixin",\n    "DamageEventMixin"')


rw("edithglasses.mixins.json", mixins, res)


# ---- client: ticks, keys, commands
def client(s):
    s = rep(s, "        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Guard::pre);",
            "        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Guard::pre);\n"
            "        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Builder::pre);")
    s = rep(s, "            com.example.edithglasses.feature.Memory.tick(mc);\n",
            "            com.example.edithglasses.feature.Memory.tick(mc);\n            com.example.edithglasses.feature.Builder.tick(mc);\n")
    s = rep(s, "while (confirmKey.consumeClick()) if (Assistant.hasPending()) Assistant.confirm(mc);",
            "while (confirmKey.consumeClick()) { if (Assistant.hasPending()) Assistant.confirm(mc); else if (com.example.edithglasses.feature.Builder.previewing()) say(com.example.edithglasses.feature.Builder.confirm(mc)); }")
    s = rep(s, "while (cancelKey.consumeClick()) { if (Assistant.hasPending()) Assistant.cancel(mc); else Assistant.stopSpeaking(); }",
            "while (cancelKey.consumeClick()) { if (com.example.edithglasses.feature.Builder.busy()) com.example.edithglasses.feature.Builder.cancel(mc, \"you pressed N\"); else if (Assistant.hasPending()) Assistant.cancel(mc); else Assistant.stopSpeaking(); }")
    s = rep(s, '                .then(ClientCommands.literal("intro")',
            '                .then(ClientCommands.literal("build")\n'
            '                    .executes(c -> { say("Build a house: /edith build starter, normal or luxury (add a wood or wall: oak, spruce, birch, dark, brick, stone, cobble). Then Y builds it, N stops it."); return 1; })\n'
            '                    .then(ClientCommands.literal("confirm").executes(c -> { say(com.example.edithglasses.feature.Builder.confirm(Minecraft.getInstance())); return 1; }))\n'
            '                    .then(ClientCommands.literal("cancel").executes(c -> { com.example.edithglasses.feature.Builder.cancel(Minecraft.getInstance(), "cancelled"); return 1; }))\n'
            '                    .then(ClientCommands.literal("rotate").executes(c -> { say(com.example.edithglasses.feature.Builder.rotate()); return 1; }))\n'
            '                    .then(ClientCommands.argument("house", StringArgumentType.word()).executes(c -> { say(com.example.edithglasses.feature.Builder.preview(Minecraft.getInstance(), StringArgumentType.getString(c, "house"), "")); return 1; })\n'
            '                        .then(ClientCommands.argument("style", StringArgumentType.greedyString()).executes(c -> { say(com.example.edithglasses.feature.Builder.preview(Minecraft.getInstance(), StringArgumentType.getString(c, "house"), StringArgumentType.getString(c, "style"))); return 1; }))))\n'
            '                .then(ClientCommands.literal("intro")')
    return s


rw("EdithGlassesClient.java", client)

# ---- hud: hologram + damage arcs
rw("mixin/HudMixin.java", lambda s: rep(s, "        if (!this.minecraft.options.getCameraType().isFirstPerson()) return;\n        if (!Glasses",
    "        com.example.edithglasses.feature.Builder.draw(graphics, this.minecraft.font, graphics.guiWidth(), graphics.guiHeight());\n        if (!this.minecraft.options.getCameraType().isFirstPerson()) return;\n        if (!Glasses")
    if "if (!this.minecraft.options.getCameraType().isFirstPerson()) return;\n        if (!Glasses" in s else rep(s,
    "        if (!com.example.edithglasses.HudLayout.editing()) {",
    "        com.example.edithglasses.feature.Builder.draw(graphics, this.minecraft.font, graphics.guiWidth(), graphics.guiHeight());\n        if (!com.example.edithglasses.HudLayout.editing()) {"))

rw("EdithHud.java", lambda s: rep(s, "        gauges(g, font, p, cx, cy, h);\n",
    "        gauges(g, font, p, cx, cy, h);\n        com.example.edithglasses.feature.DamageLog.draw(g, font, w, h);\n"))


# ---- AI context: inventory + recent damage
def ctx(s):
    s = rep(s, "        HitResult hit = mc.hitResult;",
            '''        java.util.Map<String, Integer> inv = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!st.isEmpty()) inv.merge(st.getHoverName().getString(), st.getCount(), Integer::sum);
        }
        if (!inv.isEmpty()) {
            sb.append("Inventory: ");
            int n = 0;
            for (var e : inv.entrySet()) {
                if (n++ >= 36) break;
                sb.append(e.getKey()).append(" x").append(e.getValue()).append("; ");
            }
            sb.append('\\n');
        }
        sb.append(com.example.edithglasses.feature.DamageLog.recent());
        HitResult hit = mc.hitResult;''')
    return s


rw("ai/GameContext.java", ctx)


# ---- assistant: voice intents for building
def assistant(s):
    s = rep(s, '''            String low = text.toLowerCase(Locale.ROOT).trim().replaceAll("[.!?,]+$", "");''',
            '''            String low = text.toLowerCase(Locale.ROOT).trim().replaceAll("[.!?,]+$", "");
            java.util.regex.Matcher bm = java.util.regex.Pattern.compile(".*\\\\b(build|make|construct)\\\\b.*\\\\b(starter|small|normal|medium|regular|luxury|big|large|mansion)\\\\b.*\\\\b(house|home)\\\\b.*").matcher(low);
            if (bm.matches()) {
                String size = low.contains("luxury") || low.contains("mansion") ? "luxury" : low.matches(".*\\\\b(big|large)\\\\b.*") ? "luxury"
                    : low.matches(".*\\\\b(normal|medium|regular)\\\\b.*") ? "normal" : "starter";
                String style = low.replaceAll(".*\\\\b(house|home)\\\\b", "").trim();
                mc.execute(() -> say(mc, com.example.edithglasses.feature.Builder.preview(mc, size, style)));
                return;
            }
            if (com.example.edithglasses.feature.Builder.previewing() && low.matches(".*\\\\b(confirm|yes|build it|go ahead|do it|start)\\\\b.*")) {
                mc.execute(() -> say(mc, com.example.edithglasses.feature.Builder.confirm(mc)));
                return;
            }
            if (com.example.edithglasses.feature.Builder.busy() && low.matches(".*\\\\b(stop|cancel|abort|never mind)\\\\b.*")) {
                mc.execute(() -> com.example.edithglasses.feature.Builder.cancel(mc, "you asked me to stop"));
                return;
            }''')
    return s


rw("ai/Assistant.java", assistant)


# ---- menu
def menu(s):
    return rep(s, '        tools.add(action("Add friend"',
               '        tools.add(action("Build starter house", "Shows a hologram that follows your crosshair. Press Y to let EDITH build it (5x5, one room).", "PREVIEW", () -> { onClose(); com.example.edithglasses.feature.Builder.preview(minecraft, "starter", ""); }));\n'
               '        tools.add(action("Build normal house", "7x9 house with pillars, windows, a two step roof and furniture.", "PREVIEW", () -> { onClose(); com.example.edithglasses.feature.Builder.preview(minecraft, "normal", ""); }));\n'
               '        tools.add(action("Build luxury house", "11x13 house with two rooms, big windows, skylights, lanterns and furniture. Needs lots of blocks in survival.", "PREVIEW", () -> { onClose(); com.example.edithglasses.feature.Builder.preview(minecraft, "luxury", ""); }));\n'
               '        tools.add(action("Stop building", "Cancels the hologram or stops EDITH while she builds.", "STOP", () -> com.example.edithglasses.feature.Builder.cancel(minecraft, "stopped from the menu")));\n'
               '        tools.add(action("Add friend"')


rw("EdithMenuScreen.java", menu)
print("patched")
