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


edit(j + "EdithConfig.java", [("    /** EDITH answers out loud. */", """    /** Sentinel also fights hostile MOBS that reach you (never players). */
    public boolean guardFight = false;
    /** Player name to track live on the HUD. */
    public String trackedPlayer = "";
    /** EDITH answers out loud. */""")])

# ---- fight hostile mobs inside Guard.tick, after the player-threat logic found nothing
edit(j + "feature/Guard.java", [
    ("        if (from == null) {\n            chain = 0;\n            return;\n        }",
     "        if (from == null) {\n            chain = 0;\n            fightMobs(mc);\n            return;\n        }"),
    ("    private static boolean isFriend(Player o) {", """    /** Defends against hostile mobs that get within reach. Never attacks players. Creepers are run from instead. */
    private static void fightMobs(Minecraft mc) {
        if (!EdithConfig.get().guardFight || mc.gameMode == null) return;
        var me = mc.player;
        Entity target = null;
        double best = 1e9;
        for (Entity e : mc.level.getEntities(me, me.getBoundingBox().inflate(6))) {
            if (!(e instanceof net.minecraft.world.entity.monster.Enemy) || !(e instanceof net.minecraft.world.entity.LivingEntity le) || !le.isAlive()) continue;
            double d = me.distanceTo(e);
            if (e.getType() == EntityTypes.CREEPER && d < 6) {
                Float yaw = pickDirection(mc, e.position());
                if (yaw != null) {
                    targetYaw = yaw;
                    evadeLeft = EVADE_TICKS;
                    Alerts.push(mc, "SENTINEL: creeper - backing off", 0xFFFFC857, null);
                }
                return;
            }
            if (d < best && d <= 3.6) {
                best = d;
                target = e;
            }
        }
        if (target == null) return;
        // hold the best weapon from the hotbar
        int bestSlot = -1, bestScore = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = me.getInventory().getItem(i);
            int score = s.is(Items.MACE) ? 5 : s.is(ItemTags.SWORDS) ? 4 : s.is(ItemTags.AXES) ? 3 : s.is(Items.TRIDENT) ? 2 : s.is(ItemTags.SPEARS) ? 2 : 0;
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0 && bestSlot != me.getInventory().getSelectedSlot()) me.getInventory().setSelectedSlot(bestSlot);
        // face it
        Vec3 eye = me.getEyePosition();
        Vec3 aim = target.getBoundingBox().getCenter().subtract(eye);
        me.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        me.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))));
        if (me.getAttackStrengthScale(0.5f) >= 0.95f) {
            mc.gameMode.attack(me, target);
            me.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    private static boolean isFriend(Player o) {"""),
])

edit(j + "EdithGlassesClient.java", [
    ("            com.example.edithglasses.feature.Guard.tick(mc);",
     "            com.example.edithglasses.feature.Guard.tick(mc);\n            com.example.edithglasses.feature.Tracker.tick(mc);"),
    ('                    .then(ClientCommands.literal("status").executes(c -> { say(com.example.edithglasses.feature.Guard.describe()); return 1; }))',
     '                    .then(ClientCommands.literal("status").executes(c -> { say(com.example.edithglasses.feature.Guard.describe()); return 1; }))\n'
     '                    .then(ClientCommands.literal("fight")\n'
     '                        .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().guardFight = true; EdithConfig.save(); say("Sentinel will fight hostile mobs that reach you (never players)."); return 1; }))\n'
     '                        .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().guardFight = false; EdithConfig.save(); say("Sentinel fighting OFF"); return 1; })))'),
    ('                .then(ClientCommands.literal("xray")',
     '                .then(ClientCommands.literal("track")\n'
     '                    .then(ClientCommands.literal("off").executes(c -> { com.example.edithglasses.feature.Tracker.set(""); say("Tracking off."); return 1; }))\n'
     '                    .then(ClientCommands.literal("status").executes(c -> { say(com.example.edithglasses.feature.Tracker.status(Minecraft.getInstance())); return 1; }))\n'
     '                    .then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {\n'
     '                        com.example.edithglasses.feature.Tracker.set(StringArgumentType.getString(c, "player"));\n'
     '                        say("Tracking " + StringArgumentType.getString(c, "player") + ". They show on the HUD while inside your render distance.");\n'
     '                        return 1;\n'
     '                    })))\n'
     '                .then(ClientCommands.literal("xray")'),
])

edit(j + "feature/HudExtras.java", [
    ("        // translated chat as subtitles", """        // live tracked player
        if (Tracker.hasPosition(mc)) {
            double dx = Tracker.x - p.getX(), dz = Tracker.z - p.getZ();
            float target = Mth.positiveModulo((float) Math.toDegrees(Math.atan2(-dx, dz)) + 180f, 360f);
            float delta = Mth.wrapDegrees(target - heading);
            int x = cx + Math.round(Mth.clamp(delta, -60f, 60f) * (stripHalf / 60.0F));
            boolean live = Tracker.visible;
            int col = live ? 0xFF60D0FF : 0xFF7F9FB0;
            String label = Tracker.name() + "  " + Math.round(Math.hypot(dx, dz)) + " m"
                + (live ? (Tracker.health >= 0 ? "  " + Math.round(Tracker.health) + " hp" : "")
                        : "  (last seen " + (System.currentTimeMillis() - Tracker.lastSeen) / 1000 + " s ago)");
            g.fill(x - 1, stripY + 5, x + 2, stripY + 12, col);
            if (Math.abs(delta) > 60f) g.text(font, delta > 0 ? ">" : "<", x + (delta > 0 ? -8 : 4), stripY + 4, col, false);
            g.text(font, label, Mth.clamp(x - font.width(label) / 2, 4, w - font.width(label) - 4), stripY - 26, col, false);
        }

        // translated chat as subtitles"""),
    ("        if (Guard.enabled()) {", '        if (!Tracker.name().isEmpty()) { g.text(font, "TRACK: " + Tracker.name(), l + 8, ly, 0xFF60D0FF, false); ly += 10; }\n        if (Guard.enabled()) {'),
])
print("patched")
