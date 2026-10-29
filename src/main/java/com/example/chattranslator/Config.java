package com.example.chattranslator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Config {
    /** Bumped when defaults change; older config files get migrated once. */
    public int configVersion = 3;

    /** Language incoming messages are translated INTO. */
    public String targetLang = "en";
    /** Off by default: other people's chat is left alone unless you turn this on. */
    public boolean autoTranslate = false;

    /** Language your own messages are translated INTO before sending ("rouxls" = Rouxls Kaard style). */
    public String outgoingLang = "rouxls";
    public boolean outgoingTranslate = true;

    /** Quick-switch languages for what you write. Star them in the language picker, cycle with the hotkey. */
    public List<String> favorites = new ArrayList<>(List.of("en", "de", "rouxls"));

    /**
     * Quick moderation: in the chat screen, middle-click a line, then middle-click the same player again
     * within 3 seconds to run this command. {player} and {reason} are filled in.
     */
    public boolean quickModEnabled = true;
    public String quickModCommand = "ban {player} {reason}";
    public String quickModReason = "Breaking the server rules";
    /** Quick-mute key (Delete by default, change it in Controls): hover a chat line and press it. AdvancedBan syntax. */
    public String quickMuteCommand = "tempmute {player} 1h {reason}";
    public String quickMuteReason = "Chat rules";
    /** Leave false so it only works when the game knows you are a moderator. Set true on servers that use plugin permissions. */
    public boolean quickModSkipPermissionCheck = false;

    /**
     * Commands whose message text is translated when "translate my messages" is on.
     * Format "name:N" where N is how many arguments (e.g. a player name) come before the message.
     * Edit this list in config/chattranslator.json to add your server's own commands.
     */
    public List<String> translateCommands = new ArrayList<>(List.of(
        "msg:1", "tell:1", "w:1", "whisper:1", "m:1", "pm:1", "t:1",
        "r:0", "reply:0", "me:0", "teammsg:0", "tm:0"));

    /** Name shown on buttons, e.g. "German" instead of "de". */
    public static String label(String code) {
        return Languages.name(code);
    }

    public boolean isFavorite(String code) {
        for (String f : favorites) if (f.equalsIgnoreCase(code)) return true;
        return false;
    }

    public void toggleFavorite(String code) {
        if (!favorites.removeIf(f -> f.equalsIgnoreCase(code))) favorites.add(code);
        save();
    }

    private static Config instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("chattranslator.json");
    }

    public static Config get() {
        if (instance == null) {
            try { instance = GSON.fromJson(Files.readString(path()), Config.class); } catch (Exception ignored) {}
            if (instance == null) instance = new Config();
            if (instance.favorites == null) instance.favorites = new ArrayList<>(List.of("en", "de", "rouxls"));
            if (instance.configVersion < 3) {
                // v3: Rouxls only styles what YOU write. Reading other people's chat is back to off / English.
                instance.configVersion = 3;
                instance.autoTranslate = false;
                if ("rouxls".equals(instance.targetLang)) instance.targetLang = "en";
                save();
            }
        }
        return instance;
    }

    public static void save() {
        try { Files.writeString(path(), GSON.toJson(get())); } catch (Exception ignored) {}
    }
}
