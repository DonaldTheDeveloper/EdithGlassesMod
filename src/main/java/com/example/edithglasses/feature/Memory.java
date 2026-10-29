package com.example.edithglasses.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * EDITH's memory, saved in config/edith-memory.json:
 * notes you asked her to remember (with the place where you said it), and what was inside every chest, barrel, shulker box,
 * hopper or dispenser you opened, so "where did I put my golden apples" has an answer. It only records what you open yourself.
 */
public final class Memory {
    public static final class Note {
        public String text;
        public String world, dim;
        public double x, y, z;
        public long time;
    }

    public static final class Box {
        public String world, dim, label;
        public int x, y, z;
        public boolean ender;
        public Map<String, Integer> items = new LinkedHashMap<>();
        public Map<String, String> names = new LinkedHashMap<>();
        public long time;
    }

    private static final class Store {
        List<Note> notes = new ArrayList<>();
        Map<String, Box> boxes = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> STOP = Set.of("where", "what", "when", "which", "the", "did", "does", "didn", "put", "have", "had", "has", "my", "mine",
        "was", "were", "are", "you", "can", "could", "tell", "find", "know", "remember", "left", "stored", "store", "got", "get", "any", "some", "that", "this",
        "and", "for", "from", "with", "into", "about", "edith", "please", "hey", "how", "many", "much", "last", "time", "i", "a", "an", "is", "it", "in", "on", "of", "to");
    private static Store store;

    // tracking of the container screen that is open
    private static net.minecraft.client.gui.screens.Screen openScreen;
    private static AbstractContainerMenu openMenu;
    private static BlockPos openPos;
    private static BlockPos lookPos;
    private static boolean deadBefore = false;

    private Memory() {
    }

    // ------------------------------------------------------------------ storage

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("edith-memory.json");
    }

    private static synchronized Store s() {
        if (store == null) {
            try {
                store = Files.exists(path()) ? GSON.fromJson(Files.readString(path()), Store.class) : new Store();
            } catch (Exception e) {
                store = new Store();
            }
            if (store == null) store = new Store();
            if (store.notes == null) store.notes = new ArrayList<>();
            if (store.boxes == null) store.boxes = new LinkedHashMap<>();
        }
        return store;
    }

    private static synchronized void save() {
        try {
            Files.writeString(path(), GSON.toJson(s()));
        } catch (Exception ignored) {
        }
    }

    public static String world(Minecraft mc) {
        var srv = mc.getCurrentServer();
        return srv != null && srv.ip != null ? srv.ip : "singleplayer";
    }

    private static String dim(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    // ------------------------------------------------------------------ notes

    public static synchronized String addNote(Minecraft mc, String text) {
        if (text == null || text.isBlank() || mc.player == null) return "What should I remember?";
        Note n = new Note();
        n.text = text.trim();
        n.world = world(mc);
        n.dim = dim(mc);
        n.x = mc.player.getX();
        n.y = mc.player.getY();
        n.z = mc.player.getZ();
        n.time = System.currentTimeMillis();
        s().notes.add(n);
        while (s().notes.size() > 200) s().notes.remove(0);
        save();
        return "Remembered: " + n.text;
    }

    public static synchronized String forget(String what) {
        if (what == null || what.isBlank()) return "Forget what?";
        String q = what.trim().toLowerCase(Locale.ROOT);
        if (q.equals("all") || q.equals("everything")) {
            int n = s().notes.size() + s().boxes.size();
            s().notes.clear();
            s().boxes.clear();
            save();
            return "Forgot everything (" + n + " items).";
        }
        int before = s().notes.size();
        s().notes.removeIf(n -> n.text.toLowerCase(Locale.ROOT).contains(q));
        int removed = before - s().notes.size();
        save();
        return removed == 0 ? "I had nothing about that." : "Forgot " + removed + (removed == 1 ? " note." : " notes.");
    }

    public static synchronized int noteCount() {
        return s().notes.size();
    }

    public static synchronized int boxCount() {
        return s().boxes.size();
    }

    public static synchronized String listNotes() {
        if (s().notes.isEmpty()) return "No notes yet. Say 'EDITH, remember ...' or use /edith remember <text>.";
        StringBuilder sb = new StringBuilder("Notes: ");
        int from = Math.max(0, s().notes.size() - 8);
        for (int i = from; i < s().notes.size(); i++) sb.append(i + 1).append(". ").append(s().notes.get(i).text).append("  ");
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ containers

    private static boolean storageBlock(String id) {
        return id.contains("chest") || id.contains("barrel") || id.contains("shulker") || id.contains("hopper")
            || id.contains("dispenser") || id.contains("dropper");
    }

    private static boolean storageMenu(AbstractContainerMenu m) {
        return m instanceof ChestMenu || m instanceof ShulkerBoxMenu || m instanceof HopperMenu || m instanceof DispenserMenu;
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            openScreen = null;
            openMenu = null;
            return;
        }
        // died: remember where
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !deadBefore) addNote(mc, "I died here (my items may be on the ground)");
        deadBefore = dead;

        var screen = mc.gui.screen();
        if (screen == null) {
            if (openMenu != null) snapshot(mc);          // last look at the contents when the chest closes
            openScreen = null;
            openMenu = null;
            if (mc.hitResult instanceof BlockHitResult b && mc.hitResult.getType() == HitResult.Type.BLOCK) lookPos = b.getBlockPos();
            return;
        }
        if (screen instanceof AbstractContainerScreen<?> acs && storageMenu(acs.getMenu())) {
            if (openScreen != screen) {
                openScreen = screen;
                openMenu = acs.getMenu();
                openPos = lookPos;
            }
            if (mc.level.getGameTime() % 4 == 0) snapshot(mc);
        } else if (openMenu != null && !(screen instanceof AbstractContainerScreen<?>)) {
            snapshot(mc);
            openScreen = null;
            openMenu = null;
        }
    }

    private static void snapshot(Minecraft mc) {
        if (openMenu == null || openPos == null || mc.level == null || mc.player == null) return;
        var state = mc.level.getBlockState(openPos);
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (!storageBlock(id)) return;
        boolean ender = id.contains("ender_chest");
        Box b = new Box();
        b.world = world(mc);
        b.dim = dim(mc);
        b.x = openPos.getX();
        b.y = openPos.getY();
        b.z = openPos.getZ();
        b.ender = ender;
        b.label = state.getBlock().getName().getString();
        b.time = System.currentTimeMillis();
        int slots = 0;
        for (Slot sl : openMenu.slots) {
            if (sl.container == mc.player.getInventory()) continue;
            slots++;
            ItemStack st = sl.getItem();
            if (st.isEmpty()) continue;
            String item = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
            b.items.merge(item, st.getCount(), Integer::sum);
            b.names.putIfAbsent(item, st.getHoverName().getString());
        }
        if (slots == 0) return;
        String key = ender ? b.world + "|ender" : b.world + "|" + b.dim + "|" + b.x + "|" + b.y + "|" + b.z;
        synchronized (Memory.class) {
            s().boxes.put(key, b);
            if (s().boxes.size() > 400) s().boxes.remove(s().boxes.keySet().iterator().next());
        }
        save();
    }

    // ------------------------------------------------------------------ searching

    public record Hit(Box box, int count, String itemName, double distance) {
    }

    private static List<String> tokens(String q) {
        List<String> out = new ArrayList<>();
        for (String w : q.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() >= 3 && !STOP.contains(w)) out.add(w.endsWith("s") && w.length() > 3 ? w.substring(0, w.length() - 1) : w);
        }
        return out;
    }

    /** Containers (of this server) holding something that matches every word of the query, nearest first. */
    public static synchronized List<Hit> find(Minecraft mc, String query) {
        List<String> toks = tokens(query);
        List<Hit> out = new ArrayList<>();
        if (toks.isEmpty() || mc.player == null) return out;
        String world = world(mc);
        for (Box b : s().boxes.values()) {
            if (!world.equals(b.world)) continue;
            int total = 0;
            String shown = "";
            for (Map.Entry<String, Integer> e : b.items.entrySet()) {
                String hay = (e.getKey().replace('_', ' ') + " " + b.names.getOrDefault(e.getKey(), "")).toLowerCase(Locale.ROOT);
                boolean all = true;
                for (String t : toks) if (!hay.contains(t)) { all = false; break; }
                if (all) {
                    total += e.getValue();
                    if (shown.isEmpty()) shown = b.names.getOrDefault(e.getKey(), e.getKey());
                }
            }
            if (total > 0) {
                double d = b.ender || !b.dim.equals(dim(mc)) ? Double.MAX_VALUE
                    : Math.hypot(b.x + 0.5 - mc.player.getX(), b.z + 0.5 - mc.player.getZ());
                out.add(new Hit(b, total, shown, d));
            }
        }
        out.sort((a, c) -> Double.compare(a.distance(), c.distance()));
        return out;
    }

    private static String where(Box b) {
        return b.ender ? b.label : b.label + " at " + b.x + " " + b.y + " " + b.z + (b.dim.isEmpty() ? "" : " (" + shortDim(b.dim) + ")");
    }

    private static String shortDim(String d) {
        return d.replace("minecraft:", "");
    }

    /** Text answer for /edith find; also starts guiding you to the nearest match. */
    public static String findText(Minecraft mc, String query) {
        List<Hit> hits = find(mc, query);
        if (hits.isEmpty()) return "I have not seen " + query + " in any chest you opened. (I only know what I saw you open.)";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Hit h : hits) {
            if (n++ >= 4) break;
            sb.append(h.itemName()).append(" x").append(h.count()).append(" in ").append(where(h.box()));
            if (h.distance() < Double.MAX_VALUE) sb.append(", ").append(Math.round(h.distance())).append(" m away");
            sb.append(". ");
        }
        Hit best = hits.get(0);
        if (!best.box().ender && best.distance() < Double.MAX_VALUE) {
            Waypoints.setAt(mc, "find " + query.trim(), best.box().x + 0.5, best.box().y, best.box().z + 0.5, best.box().dim);
            sb.append("Guiding you to the nearest one.");
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ context for the AI

    /** Notes and the containers that match the question, kept short so every question stays cheap. */
    public static synchronized String context(Minecraft mc, String userText) {
        StringBuilder sb = new StringBuilder();
        if (mc.player == null) return "";
        String world = world(mc);
        List<Note> mine = new ArrayList<>();
        for (Note n : s().notes) if (world.equals(n.world)) mine.add(n);
        if (!mine.isEmpty()) {
            sb.append("MEMORY notes (saved at the position where the user said them):\n");
            for (int i = Math.max(0, mine.size() - 25); i < mine.size(); i++) {
                Note n = mine.get(i);
                sb.append("  - ").append(n.text).append(" [").append(Math.round(n.x)).append(' ').append(Math.round(n.y)).append(' ')
                    .append(Math.round(n.z)).append(' ').append(shortDim(n.dim)).append("]\n");
            }
        }
        List<Hit> hits = find(mc, userText);
        if (!hits.isEmpty()) {
            sb.append("MEMORY containers the user opened that hold what they asked about (nearest first):\n");
            int n = 0;
            for (Hit h : hits) {
                if (n++ >= 6) break;
                sb.append("  - ").append(h.itemName()).append(" x").append(h.count()).append(" in ").append(where(h.box()));
                if (h.distance() < Double.MAX_VALUE) sb.append(", ").append(Math.round(h.distance())).append(" blocks away");
                sb.append('\n');
            }
        } else if (!tokens(userText).isEmpty() && !s().boxes.isEmpty()) {
            sb.append("MEMORY: no opened container is known to hold that (").append(s().boxes.size()).append(" containers remembered).\n");
        }
        return sb.toString();
    }
}
