package com.example.edithglasses.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Map;

/**
 * Target analysis: what a mob is, how hard it hits, how dangerous it is for you right now (your health, armor and weapon)
 * and what it drops. Worked out locally from the mob's own stats and your gear, so it needs no AI and works offline.
 */
public final class Analyse {
    /** {special danger and damage per hit, drops}. Damage 0 means "use the mob's attack attribute". */
    private record Info(double dmg, String note, String drops) {
    }

    private static final Map<String, Info> INFO = Map.ofEntries(
        Map.entry("creeper", new Info(20, "Explodes up close, keep your distance.", "gunpowder")),
        Map.entry("skeleton", new Info(4, "Shoots arrows from range, strafe or close in fast.", "bones, arrows")),
        Map.entry("stray", new Info(4, "Shoots slowing arrows.", "bones, arrows, tipped arrows")),
        Map.entry("wither_skeleton", new Info(8, "Melee hits apply the wither effect.", "coal, bones, rarely a skull")),
        Map.entry("zombie", new Info(0, "Slow melee, can call reinforcements.", "rotten flesh, iron, carrots, potatoes")),
        Map.entry("husk", new Info(0, "Zombie that inflicts hunger.", "rotten flesh, iron")),
        Map.entry("drowned", new Info(0, "Zombie of the water, some carry tridents.", "rotten flesh, copper, nautilus shells, tridents")),
        Map.entry("spider", new Info(0, "Fast and climbs walls, hostile at night.", "string, spider eyes")),
        Map.entry("cave_spider", new Info(0, "Small, poisonous bite.", "string, spider eyes")),
        Map.entry("enderman", new Info(0, "Do not look at its eyes. Teleports, hits hard when angry.", "ender pearls")),
        Map.entry("blaze", new Info(6, "Flies and throws fireballs. Bring fire resistance or a bow.", "blaze rods")),
        Map.entry("ghast", new Info(8, "Floating fireballs, you can reflect them back.", "ghast tears, gunpowder")),
        Map.entry("witch", new Info(6, "Throws harmful potions, heals herself.", "potion ingredients, sticks, redstone, glowstone")),
        Map.entry("phantom", new Info(0, "Dive bombs players who have not slept.", "phantom membranes")),
        Map.entry("pillager", new Info(5, "Crossbow shots, usually in a raid party.", "crossbows, arrows")),
        Map.entry("vindicator", new Info(0, "Axe hits very hard in melee.", "emeralds, iron axes")),
        Map.entry("evoker", new Info(6, "Summons fangs and vexes, kill it first.", "totem of undying, emeralds")),
        Map.entry("ravager", new Info(0, "Charges and knocks you back, very tanky.", "saddles")),
        Map.entry("piglin_brute", new Info(0, "Hits hard, never calms down.", "gold axes")),
        Map.entry("hoglin", new Info(0, "Charges and knocks you back.", "pork, leather")),
        Map.entry("zoglin", new Info(0, "Attacks everything nearby.", "rotten flesh")),
        Map.entry("guardian", new Info(6, "Laser beam, thorns while its spikes are out.", "prismarine, fish")),
        Map.entry("elder_guardian", new Info(8, "Gives mining fatigue, strong guardian.", "prismarine, sponges")),
        Map.entry("shulker", new Info(4, "Homing bullets that make you levitate.", "shulker shells")),
        Map.entry("slime", new Info(0, "Splits into smaller slimes.", "slimeballs")),
        Map.entry("magma_cube", new Info(0, "Splits when hit, burns.", "magma cream")),
        Map.entry("silverfish", new Info(0, "Small, calls others out of blocks.", "nothing")),
        Map.entry("breeze", new Info(6, "Wind charges throw you around.", "breeze rods")),
        Map.entry("warden", new Info(30, "Blind but hears everything. Sonic boom ignores armor. Avoid it.", "sculk catalyst")),
        Map.entry("wither", new Info(14, "Boss. Wither skulls blow up blocks and rot you. Bring strong gear and a bow.", "nether star")),
        Map.entry("ender_dragon", new Info(12, "Boss. Destroy the end crystals first, then fight it in the air.", "dragon egg, lots of experience")),
        Map.entry("iron_golem", new Info(0, "Neutral until attacked, hits very hard.", "iron, poppies")));

    private Analyse() {
    }

    private static String key(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
    }

    private static double attr(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, double fallback) {
        try {
            return e.getAttributes().hasAttribute(a) ? e.getAttributeValue(a) : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static boolean boss(String k) {
        return k.equals("wither") || k.equals("ender_dragon") || k.equals("warden") || k.equals("elder_guardian");
    }

    /** 0 = harmless, 1..5 = how dangerous it is for you (5 = it wins). */
    public static int threat(Entity target, Player me) {
        if (!(target instanceof LivingEntity le) || me == null) return 0;
        String k = key(target);
        Info info = INFO.get(k);
        double dmg = info != null && info.dmg() > 0 ? info.dmg() : attr(le, Attributes.ATTACK_DAMAGE, 0);
        boolean hostile = target instanceof Enemy || info != null;
        if (!hostile && dmg <= 0) return 0;
        double reduction = Math.min(0.8, me.getArmorValue() * 0.04);
        double incoming = Math.max(0.5, dmg * (1 - (k.equals("warden") || k.equals("creeper") ? 0 : reduction)));
        double ttd = Math.max(1, me.getHealth()) / incoming;                      // seconds until it kills you
        double mine = Math.max(1, attr(me, Attributes.ATTACK_DAMAGE, 1)) * 1.2;   // your damage per second
        double ttk = Math.max(1, le.getHealth()) / mine;                          // seconds until you kill it
        double ratio = ttk / ttd;
        int level = ratio < 0.25 ? 1 : ratio < 0.6 ? 2 : ratio < 1.0 ? 3 : ratio < 2.0 ? 4 : 5;
        if (boss(k)) level = Math.max(level, k.equals("elder_guardian") ? 3 : 5);
        return level;
    }

    /** One short spoken description. */
    public static String speech(Minecraft mc, Entity target) {
        Player me = mc.player;
        if (target == null) return "I have nothing in sight to analyse.";
        String name = target.getName().getString();
        if (!(target instanceof LivingEntity le)) return name + ". Not a living target.";
        String k = key(target);
        Info info = INFO.get(k);
        StringBuilder sb = new StringBuilder(name).append(". ");
        sb.append(Math.round(le.getHealth())).append(" health");
        double dmg = info != null && info.dmg() > 0 ? info.dmg() : attr(le, Attributes.ATTACK_DAMAGE, 0);
        if (dmg > 0) sb.append(", hits for about ").append(Math.round(dmg));
        sb.append(". ");
        if (info != null) sb.append(info.note()).append(' ');
        int t = threat(target, me);
        if (t == 0) {
            sb.append("Harmless.");
        } else {
            sb.append("Threat ").append(t).append(" of 5 for you right now");
            if (t >= 4) sb.append(me != null && me.getArmorValue() < 10 ? ", your armor is weak for this" : ", it is stronger than you");
            sb.append('.');
        }
        if (info != null && !info.drops().equals("nothing")) sb.append(" Drops ").append(info.drops()).append('.');
        return sb.toString().replace("  ", " ").trim();
    }

    /** Nearest living, non-player mob whose name or type matches (for "target the wither" when you are not looking at it). */
    public static Entity findNamed(Minecraft mc, String name) {
        if (mc.level == null || mc.player == null || name == null || name.isBlank()) return null;
        String q = name.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity) || e instanceof Player || !e.isAlive()) continue;
            String k = key(e), n = e.getName().getString().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!k.contains(q) && !n.contains(q) && !q.contains(k)) continue;
            double d = mc.player.distanceToSqr(e);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }
}
