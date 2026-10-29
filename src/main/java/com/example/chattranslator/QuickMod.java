package com.example.chattranslator;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.server.permissions.Permissions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quick moderation from the chat screen. The server still decides whether you are allowed;
 * this only saves typing.
 *  - Middle-click a line twice (within 3 seconds): run the quick ban command.
 *  - Press the quick-mute key (Delete by default) while hovering a line: run the quick mute command.
 */
public final class QuickMod {
    private QuickMod() {}

    /** Set when the mod starts; rebindable in Controls. */
    public static KeyMapping muteKey;

    private static final long CONFIRM_MS = 3000;
    private static String pending;
    private static long pendingAt;

    public static synchronized void cancelPending() {
        pending = null;
    }

    private static String expand(String template, String player, String reason) {
        return template.replace("{player}", player).replace("{reason}", reason);
    }

    /** Returns the player named in the line, or null (after telling the user why). */
    private static String target(Minecraft mc, String lineText) {
        Config cfg = Config.get();
        if (!cfg.quickModEnabled) return null;
        if (mc.player == null || mc.getConnection() == null) return null;
        if (!cfg.quickModSkipPermissionCheck && !mc.player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
            ChatActions.info("Quick moderation needs moderator permission.");
            return null;
        }
        String name = findPlayer(mc, lineText);
        if (name == null) {
            ChatActions.info("Could not tell which player wrote that line.");
            return null;
        }
        if (name.equalsIgnoreCase(mc.player.getName().getString())) {
            ChatActions.info("That line is yours.");
            return null;
        }
        return name;
    }

    /** Middle-click: ask for a second click, then run the quick ban command. */
    public static synchronized void click(String lineText) {
        Minecraft mc = Minecraft.getInstance();
        String name = target(mc, lineText);
        if (name == null) { pending = null; return; }
        Config cfg = Config.get();
        String command = expand(cfg.quickModCommand, name, cfg.quickModReason);

        long now = System.currentTimeMillis();
        if (name.equals(pending) && now - pendingAt <= CONFIRM_MS) {
            pending = null;
            mc.player.connection.sendCommand(command);
            ChatActions.info("Sent: /" + command);
        } else {
            pending = name;
            pendingAt = now;
            ChatActions.info("Middle-click again within 3 seconds to run /" + command + "   (any other click cancels)");
        }
    }

    /** Quick-mute key: runs the quick mute command at once (AdvancedBan: tempmute <player> 1h <reason>). */
    public static synchronized void mute(String lineText) {
        Minecraft mc = Minecraft.getInstance();
        String name = target(mc, lineText);
        if (name == null) return;
        Config cfg = Config.get();
        String command = expand(cfg.quickMuteCommand, name, cfg.quickMuteReason);
        pending = null;
        mc.player.connection.sendCommand(command);
        ChatActions.info("Sent: /" + command);
    }

    /** The online player whose name appears first in the line (the sender is normally first). */
    private static String findPlayer(Minecraft mc, String line) {
        String best = null;
        int bestAt = Integer.MAX_VALUE;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String n = info.getProfile().name();
            if (n == null || n.isEmpty()) continue;
            Matcher m = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(n) + "(?![A-Za-z0-9_])",
                Pattern.CASE_INSENSITIVE).matcher(line);
            if (m.find() && (m.start() < bestAt || (m.start() == bestAt && best != null && n.length() > best.length()))) {
                best = n;
                bestAt = m.start();
            }
        }
        return best;
    }
}
