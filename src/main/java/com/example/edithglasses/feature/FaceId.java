package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.Glasses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.example.edithglasses.feature.HudStyle.*;

/**
 * Facial recognition: players in view get a scan box on their head. After a short scan they are identified against your
 * friends list: FRIEND (green), DUEL opponent / TRACKED (amber), or UNKNOWN (white), with distance, health and what they
 * hold. It only reads what your game already knows about visible players. It never acts on anyone.
 * The friends list is the same one the Sentinel ignores.
 */
public final class FaceId {
    private static final long SCAN_MS = 900;
    private static final Map<UUID, long[]> scans = new HashMap<>();   // {scan start, last seen}

    private FaceId() {
    }

    public static boolean active() {
        return EdithConfig.get().faceId && Glasses.equipped();
    }

    // ---------------------------------------------------------------- friends list

    private static List<String> friends() {
        List<String> out = new ArrayList<>();
        String f = EdithConfig.get().guardFriends;
        if (f != null) for (String n : f.split(",")) if (!n.isBlank()) out.add(n.trim());
        return out;
    }

    public static boolean isFriend(Player p) {
        String n = p.getName().getString();
        for (String f : friends()) if (f.equalsIgnoreCase(n)) return true;
        return false;
    }

    public static String add(String name) {
        for (String f : friends()) if (f.equalsIgnoreCase(name)) return name + " is already a friend.";
        List<String> l = friends();
        l.add(name);
        save(l);
        return name + " added to your friends. EDITH and the Sentinel will treat them as a friend.";
    }

    public static String remove(String name) {
        List<String> l = friends();
        if (!l.removeIf(f -> f.equalsIgnoreCase(name))) return name + " is not on your friends list.";
        save(l);
        return name + " removed from your friends.";
    }

    public static String list() {
        List<String> l = friends();
        return l.isEmpty() ? "No friends saved yet. Use /edith friend add <name>." : "Friends: " + String.join(", ", l);
    }

    public static void clear() {
        save(new ArrayList<>());
    }

    private static void save(List<String> l) {
        EdithConfig.get().guardFriends = String.join(",", l);
        EdithConfig.save();
    }

    // ---------------------------------------------------------------- custom tags (your own identification labels)

    public static final String[] COLOR_NAMES = {"", "red", "green", "amber", "blue", "white", "purple", "orange"};

    /** {label, colour name} of a tagged player, or null. */
    public static String[] tagOf(String name) {
        String v = EdithConfig.get().playerTags.get(name.toLowerCase(Locale.ROOT));
        if (v == null) return null;
        int i = v.lastIndexOf('|');
        return i < 0 ? new String[]{v, ""} : new String[]{v.substring(0, i), v.substring(i + 1)};
    }

    public static void setTag(String name, String label, String color) {
        String l = label.trim().replace("|", "");
        if (l.length() > 24) l = l.substring(0, 24);
        EdithConfig.get().playerTags.put(name.trim().toLowerCase(Locale.ROOT), l + "|" + (color == null ? "" : color));
        EdithConfig.save();
    }

    public static String removeTag(String name) {
        boolean had = EdithConfig.get().playerTags.remove(name.trim().toLowerCase(Locale.ROOT)) != null;
        EdithConfig.save();
        return had ? "Tag removed from " + name + "." : name + " has no tag.";
    }

    public static String listTags() {
        var t = EdithConfig.get().playerTags;
        if (t.isEmpty()) return "No tags yet. Use /edith tag set <player> <tag> or G > Tools > Tag a player.";
        StringBuilder sb = new StringBuilder("Tags: ");
        t.forEach((k, v) -> sb.append(k).append(" = ").append(v.contains("|") ? v.substring(0, v.lastIndexOf('|')) : v).append(", "));
        return sb.substring(0, sb.length() - 2);
    }

    /** Colour for a tag: the one you picked, else guessed from the words (enemy red, friend green, leader gold). */
    public static int colorValue(String label, String color) {
        switch (color == null ? "" : color) {
            case "red": return RED;
            case "green": return GREEN;
            case "amber": return AMBER;
            case "blue": return 0xFF60D0FF;
            case "white": return 0xFFFFFFFF;
            case "purple": return 0xFFB070FF;
            case "orange": return 0xFFFF9A40;
            default: break;
        }
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (l.matches(".*(enemy|hostile|rival|wanted|threat|griefer|hacker|thief|traitor|bandit|danger).*")) return RED;
        if (l.matches(".*(friend|ally|buddy|teammate|mate|family|bro|homie).*")) return GREEN;
        if (l.matches(".*(leader|law|admin|owner|mod|boss|sheriff|police|king|queen|captain|chief|mayor|general|commander).*")) return AMBER;
        if (l.matches(".*(trader|merchant|shop|vendor|neutral).*")) return 0xFF60D0FF;
        return 0xFFFFFFFF;
    }

    /** Colour of a player's dot on the hologram map. */
    public static int dotColor(Player p) {
        String[] t = tagOf(p.getName().getString());
        if (t != null) return colorValue(t[0], t[1]);
        return isFriend(p) ? GREEN : 0xFFFFFFFF;
    }

    // ---------------------------------------------------------------- drawing

    private static boolean armed(ItemStack s) {
        return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT)
            || s.is(Items.BOW) || s.is(Items.CROSSBOW) || s.is(Items.WIND_CHARGE) || s.is(ItemTags.SPEARS);
    }

    public static void draw(GuiGraphicsExtractor g, Font font, int w, int h) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        Player me = mc.player;
        if (mc.level == null || me == null) return;
        long now = System.currentTimeMillis();
        double range = Math.min(EdithConfig.get().scanRange, 64);
        Project pr = new Project(w, h);
        Player lookingAt = Glasses.target() instanceof Player t ? t : null;

        List<Player> ps = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == me || p.isSpectator() || p.isInvisible() || me.distanceTo(p) > range) continue;
            ps.add(p);
        }
        ps.sort((a, b) -> Float.compare(me.distanceTo(a), me.distanceTo(b)));

        int drawn = 0;
        for (Player p : ps) {
            if (drawn >= 5) break;
            Vec3 headTop = new Vec3(p.getX(), p.getY() + p.getBbHeight() + 0.05, p.getZ());
            Vec3 neck = new Vec3(p.getX(), p.getY() + p.getBbHeight() - 0.55, p.getZ());
            double[] a = pr.at(headTop), b = pr.at(neck);
            if (a == null || b == null) continue;
            if (a[0] < 6 || a[0] > w - 6 || a[1] < 6 || a[1] > h - 6) continue;
            if (!me.hasLineOfSight(p)) {
                scans.remove(p.getUUID());
                continue;
            }
            long[] st = scans.computeIfAbsent(p.getUUID(), k -> new long[]{now, now});
            if (now - st[1] > 1200) st[0] = now;
            st[1] = now;
            float prog = Math.min(1f, (now - st[0]) / (float) SCAN_MS);
            boolean done = prog >= 1f;
            drawn++;

            int size = (int) Math.max(14, Math.min(60, Math.abs(b[1] - a[1]) * 1.15));
            int x = (int) a[0] - size / 2, y = (int) a[1] - 1;
            String name = p.getName().getString();
            boolean friend = isFriend(p);
            boolean duel = Duel.isOpponent(p);
            boolean tracked = name.equalsIgnoreCase(Tracker.name());
            String[] tag = tagOf(name);
            int col = !done ? LINE : tag != null ? colorValue(tag[0], tag[1]) : friend ? GREEN : duel ? RED : tracked ? AMBER : 0xFFFFFFFF;
            String status = tag != null ? tag[0].toUpperCase(Locale.ROOT) : friend ? "FRIEND" : duel ? "DUEL OPPONENT" : tracked ? "TRACKED" : "UNKNOWN";

            corners(g, x, y, size, size, Math.max(4, size / 4), col);
            if (!done) {
                int sy = y + (int) (prog * size);
                g.fillGradient(x, sy - 4, x + size, sy, 0x0040E0FF, 0x8840E0FF);
                g.fill(x, sy, x + size, sy + 1, BRIGHT);
                text(g, font, "SCANNING " + (int) (prog * 100) + "%", x, y - 9, LINE, 0.6f);
                // landmark ticks like a face mesh
                int mid = x + size / 2;
                g.fill(x + size / 4, y + size * 2 / 5, x + size / 4 + 2, y + size * 2 / 5 + 1, BRIGHT);
                g.fill(x + size * 3 / 4 - 2, y + size * 2 / 5, x + size * 3 / 4, y + size * 2 / 5 + 1, BRIGHT);
                g.fill(mid, y + size * 3 / 5, mid + 1, y + size * 3 / 5 + 1, BRIGHT);
                continue;
            }

            // identified: tag to the right of the box
            int tx = x + size + 6, ty = y;
            boolean wide = tx + 96 > w;
            if (wide) tx = x - 6 - 92;
            g.fill(wide ? tx + 92 : x + size, y + size / 2, wide ? x : tx, y + size / 2 + 1, alpha(col, 0.6f));
            panel(g, tx, ty, 92, 36, col);
            text(g, font, "IDENTIFIED", tx + 4, ty + 3, alpha(col, 0.8f), 0.5f);
            text(g, font, name.toUpperCase(Locale.ROOT), tx + 4, ty + 10, BRIGHT, 0.85f);
            text(g, font, status + "  " + Math.round(me.distanceTo(p)) + " M", tx + 4, ty + 20, col, 0.6f);
            ItemStack held = p.getMainHandItem();
            String heldName = held.isEmpty() ? "EMPTY HANDED" : held.getHoverName().getString().toUpperCase(Locale.ROOT);
            boolean arm = armed(held) || armed(p.getOffhandItem());
            text(g, font, "HP " + Math.round(p.getHealth()) + "  " + (heldName.length() > 16 ? heldName.substring(0, 16) : heldName),
                tx + 4, ty + 28, arm && !friend ? AMBER : DIM, 0.55f);
            if (p == lookingAt) box(g, x - 2, y - 2, size + 4, size + 4, alpha(col, 0.5f));
        }
        scans.values().removeIf(s -> now - s[1] > 5000);
    }
}
