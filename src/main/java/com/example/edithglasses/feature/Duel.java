package com.example.edithglasses.feature;

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
            + opponent + ". If you want it to end, write stop in chat.");
        return "Duel assist ON vs " + opponent + " (max 3 min).";
    }

    public static void stop(Minecraft mc, String why) {
        if (!active()) return;
        opponent = "";
        pending = "";
        if (mc != null && mc.gui != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal("EDITH: duel assist ended - " + why));
    }

    /** If the opponent writes "stop" (or end/enough/cancel) in chat, the duel assist ends at once. */
    public static void onChat(Minecraft mc, String message) {
        if (!active()) return;
        String m = message.toLowerCase(java.util.Locale.ROOT);
        if (m.contains(opponent.toLowerCase(java.util.Locale.ROOT)) && !m.contains("[edith]") && m.matches("(?s).*\b(stop|end|enough|cancel|desist)\b.*")) {
            mc.execute(() -> stop(mc, opponent + " asked to stop."));
        }
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
