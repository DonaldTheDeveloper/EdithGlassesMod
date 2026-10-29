package com.example.edithglasses.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Named locations saved in config/edith-waypoints.json. The tracked one is drawn on the HUD compass. */
public final class Waypoints {
    public static final class WP {
        public String name;
        public String dim;
        public double x, y, z;
    }

    private static final class Store {
        Map<String, WP> points = new LinkedHashMap<>();
        String tracked = "";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Store store;

    private Waypoints() {
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("edith-waypoints.json");
    }

    private static Store s() {
        if (store == null) {
            try {
                store = Files.exists(path()) ? GSON.fromJson(Files.readString(path()), Store.class) : new Store();
            } catch (Exception e) {
                store = new Store();
            }
            if (store == null) store = new Store();
            if (store.points == null) store.points = new LinkedHashMap<>();
            if (store.tracked == null) store.tracked = "";
        }
        return store;
    }

    private static void save() {
        try {
            Files.writeString(path(), GSON.toJson(s()));
        } catch (Exception ignored) {
        }
    }

    private static String key(String n) {
        return n.trim().toLowerCase(Locale.ROOT);
    }

    public static String dimension(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    public static String set(Minecraft mc, String name) {
        if (mc.player == null || name == null || name.isBlank()) return "Give the waypoint a name.";
        WP w = new WP();
        w.name = name.trim();
        w.dim = dimension(mc);
        w.x = mc.player.getX();
        w.y = mc.player.getY();
        w.z = mc.player.getZ();
        s().points.put(key(name), w);
        s().tracked = key(name);
        save();
        return "Waypoint " + w.name + " saved and tracked.";
    }

    /** A waypoint at given coordinates (for "take me to my golden apples") that is tracked right away. */
    public static String setAt(Minecraft mc, String name, double x, double y, double z, String dim) {
        if (name == null || name.isBlank()) name = "target";
        WP w = new WP();
        w.name = name.trim();
        w.dim = dim == null || dim.isBlank() ? dimension(mc) : dim;
        w.x = x;
        w.y = y;
        w.z = z;
        s().points.put(key(name), w);
        s().tracked = key(name);
        save();
        return "Guiding you to " + w.name + ".";
    }

    public static String delete(String name) {
        WP w = s().points.remove(key(name));
        if (key(name).equals(s().tracked)) s().tracked = "";
        save();
        return w == null ? "No waypoint called " + name + "." : "Waypoint " + w.name + " deleted.";
    }

    public static String track(String name) {
        if (name == null || name.isBlank()) {
            s().tracked = "";
            save();
            return "Tracking off.";
        }
        if (!s().points.containsKey(key(name))) return "No waypoint called " + name + ".";
        s().tracked = key(name);
        save();
        return "Tracking " + s().points.get(key(name)).name + ".";
    }

    public static String list(Minecraft mc) {
        if (s().points.isEmpty()) return "No waypoints saved.";
        StringBuilder sb = new StringBuilder("Waypoints: ");
        for (WP w : s().points.values()) {
            sb.append(w.name);
            if (mc.player != null && w.dim.equals(dimension(mc))) {
                sb.append(" (").append(Math.round(Math.hypot(w.x - mc.player.getX(), w.z - mc.player.getZ()))).append(" m)");
            } else {
                sb.append(" (other dimension)");
            }
            sb.append(", ");
        }
        return sb.substring(0, sb.length() - 2);
    }

    /** The tracked waypoint if it is in the player's current dimension, else null. */
    public static WP tracked(Minecraft mc) {
        WP w = s().points.get(s().tracked);
        return w != null && w.dim.equals(dimension(mc)) ? w : null;
    }
}
