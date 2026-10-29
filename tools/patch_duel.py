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


# ---- Guard: split the combat skills into engage() so a consensual duel can reuse them
p = j + "feature/Guard.java"
s = open(p, encoding="utf-8").read()
marker = "        // best weapon in the hotbar\n"
assert marker in s
head, tail = s.split(marker, 1)
# tail runs to the end of fightMobs: everything up to "\n    }\n\n    private static boolean isFriend"
body, rest = tail.split("\n    }\n\n    private static boolean isFriend", 1)
s = (head
     + "        engage(mc, target, shooter, shield);\n    }\n\n"
     + "    /** The fighting skills: weapon choice, aim, range, strafing, critical hits, shield. Package-private so Duel can use it. */\n"
     + "    static void engage(Minecraft mc, Entity target, boolean shooter, boolean shield) {\n"
     + "        var me = mc.player;\n"
     + marker + body + "\n    }\n\n    private static boolean isFriend" + rest)
# the duel opponent is not a threat to run from
s = s.replace("            if (o == me || o.isSpectator() || o.isDeadOrDying() || isFriend(o)) continue;\n            double d = o.distanceTo(me);\n            double horiz",
              "            if (o == me || o.isSpectator() || o.isDeadOrDying() || isFriend(o) || Duel.isOpponent(o)) continue;\n            double d = o.distanceTo(me);\n            double horiz", 1)
# tick(): duel runs before the threat logic
s = s.replace("        // remember who just hit us: they count as danger for 6 seconds, whatever they hold",
              "        if (Duel.active()) {\n            Duel.tick(mc);\n            return;\n        }\n\n        // remember who just hit us: they count as danger for 6 seconds, whatever they hold", 1)
open(p, "w", encoding="utf-8").write(s)

# ---- Duel class
open(j + "feature/Duel.java", "w", encoding="utf-8").write('''package com.example.edithglasses.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Consensual duel assist. You name ONE opponent and confirm; EDITH tells them in chat that you are using combat assist,
 * then fights only that player with the same skills it uses on mobs. It stops when the duel ends (death, time out, they
 * leave), when anybody else hurts you, or when you say so. It never touches anyone else.
 */
public final class Duel {
    private static final long MAX_MS = 180_000;

    private static String pending = "";
    private static String opponent = "";
    private static long started = 0;
    private static int lost = 0;
    private static Player lastHurtGuard;

    private Duel() {
    }

    public static boolean active() {
        return !opponent.isEmpty();
    }

    public static String opponentName() {
        return opponent;
    }

    public static boolean isOpponent(Player p) {
        return active() && p.getName().getString().equalsIgnoreCase(opponent);
    }

    public static String request(String name) {
        pending = name.trim();
        return "Duel assist vs " + pending + ". Only use this if " + pending + " knows and agrees, and the server allows it. "
            + "Type /edith duel confirm to start: EDITH will announce it in chat. /edith duel stop ends it.";
    }

    public static String confirm(Minecraft mc) {
        if (pending.isEmpty() || mc.player == null) return "Nothing to confirm. Use /edith duel <player> first.";
        opponent = pending;
        pending = "";
        started = System.currentTimeMillis();
        lost = 0;
        mc.player.connection.sendChat("[EDITH] " + mc.player.getName().getString() + " is using combat assist in this duel against "
            + opponent + ". Say stop and it ends.");
        return "Duel assist ON vs " + opponent + " (max 3 min).";
    }

    public static void stop(Minecraft mc, String why) {
        if (!active()) return;
        opponent = "";
        pending = "";
        if (mc != null && mc.gui != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal("EDITH: duel assist ended - " + why));
    }

    public static void tick(Minecraft mc) {
        var me = mc.player;
        if (!active() || me == null || mc.level == null) return;
        if (System.currentTimeMillis() - started > MAX_MS) {
            stop(mc, "time is up.");
            return;
        }
        if (me.isDeadOrDying()) {
            stop(mc, "you died.");
            return;
        }
        // somebody other than the opponent hit me: the duel is not a duel any more
        if (me.hurtTime > 0 && me.getLastHurtByMob() instanceof Player p && p != me && !isOpponent(p) && p != lastHurtGuard) {
            lastHurtGuard = p;
            stop(mc, "someone else attacked you.");
            return;
        }
        Player opp = null;
        for (Player p : mc.level.players()) if (p != me && isOpponent(p)) opp = p;
        if (opp == null) {
            if (++lost > 200) stop(mc, opponent + " is not around.");
            return;
        }
        lost = 0;
        if (opp.isDeadOrDying() || opp.isSpectator()) {
            stop(mc, "the duel is over.");
            return;
        }
        if (opp.distanceTo(me) > 8) {
            // stay on them but do not run across the map: just face and approach
            Guard.engage(mc, opp, false, false);
            return;
        }
        Guard.engage(mc, opp, false, me.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD) && opp.getMainHandItem().is(net.minecraft.world.item.Items.BOW));
    }
}
''')

# ---- commands
edit(j + "EdithGlassesClient.java", [
    ('                .then(ClientCommands.literal("heal")',
     '                .then(ClientCommands.literal("duel")\n'
     '                    .then(ClientCommands.literal("confirm").executes(c -> { say(com.example.edithglasses.feature.Duel.confirm(Minecraft.getInstance())); return 1; }))\n'
     '                    .then(ClientCommands.literal("stop").executes(c -> { com.example.edithglasses.feature.Duel.stop(Minecraft.getInstance(), "you stopped it."); return 1; }))\n'
     '                    .then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> { say(com.example.edithglasses.feature.Duel.request(StringArgumentType.getString(c, "player"))); return 1; })))\n'
     '                .then(ClientCommands.literal("heal")'),
])
edit(j + "feature/HudExtras.java", [
    ("        if (AutoHeal.enabled()) {", '        if (Duel.active()) { g.text(font, "DUEL: " + Duel.opponentName(), l + 8, ly, 0xFFFF8080, false); ly += 10; }\n        if (AutoHeal.enabled()) {')])
print("patched")
