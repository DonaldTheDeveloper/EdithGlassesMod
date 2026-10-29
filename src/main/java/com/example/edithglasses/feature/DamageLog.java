package com.example.edithglasses.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.example.edithglasses.feature.HudStyle.*;

/**
 * Where did that hit come from? Remembers the last damage you took (from the damage packets the server already sends your
 * game) and draws a red arc on the edge of the HUD pointing at the source. Also feeds "what hit me" / "how did I die" to EDITH.
 */
public final class DamageLog {
    private record Hit(long time, Vec3 pos, String type, String by) {
    }

    private static final List<Hit> HITS = new CopyOnWriteArrayList<>();

    private DamageLog() {
    }

    /** Called for every damage packet (see the ClientPacketListener mixin). */
    public static void record(Minecraft mc, ClientboundDamageEventPacket p) {
        if (mc.player == null || mc.level == null || p.entityId() != mc.player.getId()) return;
        Vec3 pos = p.sourcePosition().orElse(null);
        String by = "";
        Entity direct = mc.level.getEntity(p.sourceDirectId());
        Entity cause = mc.level.getEntity(p.sourceCauseId());
        if (cause != null) by = cause.getName().getString();
        else if (direct != null) by = direct.getName().getString();
        if (pos == null) {
            if (cause != null) pos = cause.position();
            else if (direct != null) pos = direct.position();
        }
        String type = p.sourceType().value().msgId();
        HITS.add(new Hit(System.currentTimeMillis(), pos, type, by));
        while (HITS.size() > 12) HITS.remove(0);
    }

    private static String readable(String type) {
        return switch (type) {
            case "mob" -> "melee hit";
            case "player" -> "player hit";
            case "arrow" -> "arrow";
            case "fall" -> "fall";
            case "explosion", "player.explosion" -> "explosion";
            case "onFire", "inFire", "lava" -> "fire or lava";
            case "drown" -> "drowning";
            case "starve" -> "hunger";
            case "wither", "witherSkull" -> "wither";
            case "magic", "indirectMagic" -> "magic";
            case "thrown", "trident" -> "thrown weapon";
            case "fireball", "unattributed_fireball" -> "fireball";
            case "sonic_boom" -> "sonic boom";
            default -> type.replace('_', ' ');
        };
    }

    /** Short summary of the last minutes of damage for the AI ("how did I die?"). */
    public static String recent() {
        long now = System.currentTimeMillis();
        List<String> parts = new ArrayList<>();
        for (int i = HITS.size() - 1; i >= 0 && parts.size() < 6; i--) {
            Hit h = HITS.get(i);
            long ago = (now - h.time()) / 1000;
            if (ago > 120) break;
            parts.add(ago + "s ago " + (h.by().isEmpty() ? "" : h.by() + " ") + readable(h.type()));
        }
        return parts.isEmpty() ? "" : "Recent damage taken: " + String.join("; ", parts) + "\n";
    }

    public static void draw(GuiGraphicsExtractor g, Font font, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        var me = mc.player;
        if (me == null) return;
        long now = System.currentTimeMillis();
        int cx = w / 2, cy = h / 2, r = Math.min((int) (h * 0.40), 120);
        for (Hit hit : HITS) {
            long age = now - hit.time();
            if (age > 2600 || hit.pos() == null) continue;
            double dx = hit.pos().x - me.getX(), dz = hit.pos().z - me.getZ();
            if (dx * dx + dz * dz < 0.01) continue;
            double yaw = Math.toRadians(me.getYRot());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            double sx = dx * rx + dz * rz, sy = dx * fx + dz * fz;
            double angle = Math.toDegrees(Math.atan2(sy, sx));            // 0 = right, 90 = ahead
            float fade = 1f - Mth.clamp(age / 2600f, 0f, 1f);
            int col = alpha(RED, 0.35f + 0.65f * fade);
            for (int k = 0; k < 3; k++) arcFill(g, cx, cy, r + k - 1, angle - 22, angle + 22, col);
            double rad = Math.toRadians(angle);
            int lx = cx + (int) Math.round((r + 12) * Math.cos(rad)), ly = cy - (int) Math.round((r + 12) * Math.sin(rad));
            String tag = (hit.by().isEmpty() ? "" : hit.by().toUpperCase(Locale.ROOT) + " ") + readable(hit.type()).toUpperCase(Locale.ROOT);
            centered(g, font, tag, lx, ly - 3, alpha(RED, 0.4f + 0.6f * fade), 0.55f);
        }
    }
}
