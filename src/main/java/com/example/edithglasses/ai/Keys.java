package com.example.edithglasses.ai;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Reads and saves API keys in config/edith-keys.json (or GROQ_API_KEY, OPENAI_API_KEY ... environment variables). Never logged. */
public final class Keys {
    private Keys() {
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("edith-keys.json");
    }

    private static JsonObject load() {
        try {
            Path p = path();
            if (Files.exists(p)) return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    public static String get(String id) {
        if (id.equals("local")) return com.example.edithglasses.EdithConfig.get().localAi ? "local" : "";
        try {
            JsonObject o = load();
            if (o.has(id) && !o.get(id).getAsString().isBlank()) return o.get(id).getAsString().trim();
        } catch (Exception ignored) {
        }
        String e = System.getenv(id.toUpperCase() + "_API_KEY");
        return e == null ? "" : e.trim();
    }

    public static boolean has(String id) {
        return !get(id).isBlank();
    }

    public static boolean any() {
        for (Ais.Ai a : Ais.ALL) if (has(a.id())) return true;
        return false;
    }

    /** Saves (or, with a blank value, removes) one key and leaves the others as they are. */
    public static synchronized void set(String id, String value) {
        if (id.equals("local")) return;
        try {
            JsonObject o = load();
            for (Ais.Ai a : Ais.ALL) if (!o.has(a.id())) o.addProperty(a.id(), "");
            o.addProperty(id, value == null ? "" : value.trim());
            Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) {
        }
    }

    public static String groq() {
        return get("groq");
    }

    public static String gemini() {
        return get("gemini");
    }
}
