package com.example.edithglasses;

import com.example.edithglasses.ai.Assistant;
import com.example.edithglasses.feature.Alerts;
import com.example.edithglasses.feature.Duel;
import com.example.edithglasses.feature.Guard;
import com.example.edithglasses.feature.AutoHeal;
import com.example.edithglasses.feature.HudExtras;
import com.example.edithglasses.feature.HudStyle;
import com.example.edithglasses.feature.OreScan;
import com.example.edithglasses.feature.Project;
import com.example.edithglasses.feature.Recon;
import com.example.edithglasses.feature.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.example.edithglasses.feature.HudStyle.*;

/** The EDITH heads-up display: a holographic, film-style overlay (tinted glass, gauges, radar, status tabs). */
public final class EdithHud {
    private EdithHud() {
    }

    /** True while the EDITH HUD is drawn (first person, glasses on, HUD mode allows it). */
    public static boolean visible() {
        Minecraft mc = Minecraft.getInstance();
        if (HudLayout.editing()) return mc.player != null;
        if (com.example.edithglasses.feature.CameraMode.active()) return false;
        if (mc.player == null || !Glasses.equipped(mc.player)) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        String mode = EdithConfig.get().hudMode;
        return "always".equals(mode) || ("zoom".equals(mode) && (Glasses.zoom() > 1.02F || mc.hasShiftDown()));
    }

    public static boolean hidesVanilla() {
        if (com.example.edithglasses.feature.CameraMode.active()) return true;
        return EdithConfig.get().hideVanillaHud && visible();
    }

    public static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int cx = w / 2, cy = h / 2;
        long time = mc.level.getGameTime();
        int m = 8;

        glass(g, w, h);
        frame(g, w, h, m);

        // heading in compass degrees (0 = north, clockwise)
        float heading = Mth.positiveModulo(p.getYRot() + 180.0F, 360.0F);
        int stripHalf = Math.min(w / 4, 150);
        int stripY = 24;

        HudLayout.begin(g, HudLayout.El.TITLE);
        title(g, font, cx, time);
        HudLayout.end(g);
        compass(g, font, cx, stripY, stripHalf, heading);
        gauges(g, font, p, cx, cy, h);
        com.example.edithglasses.feature.DamageLog.draw(g, font, w, h);
        HudLayout.begin(g, HudLayout.El.TELEMETRY);
        telemetry(g, font, mc, p, m + 6, 52);
        HudLayout.end(g);
        HudLayout.begin(g, HudLayout.El.RADAR);
        radar(g, font, mc, p, w - m - 44, 66, 30, time);
        HudLayout.end(g);
        target(g, font, mc, p, w, h, m);
        com.example.edithglasses.feature.FaceId.draw(g, font, w, h);
        HudLayout.begin(g, HudLayout.El.HOLOMAP);
        com.example.edithglasses.feature.HoloMap.draw(g, font, w - m - 6 - 140, 108, 140, 96);
        HudLayout.end(g);
        HudLayout.begin(g, HudLayout.El.TABS);
        tabs(g, font, cx, h - 52);
        HudLayout.end(g);
        if (EdithConfig.get().hideVanillaHud) {
            HudLayout.begin(g, HudLayout.El.HOTBAR);
            hotbar(g, font, p, cx, h - m - 20);
            HudLayout.end(g);
        }

        HudExtras.draw(g, font, w, h, m + 6, 8, h - m, cx, stripHalf, stripY, heading);
        HudLayout.begin(g, HudLayout.El.ASSISTANT);
        drawAssistant(g, font, w, h - m - 62, cx, time);
        HudLayout.end(g);
    }

    // ------------------------------------------------------------------ atmosphere

    private static void glass(GuiGraphicsExtractor g, int w, int h) {
        g.fill(0, 0, w, h, 0x0C10507A);                                   // cool tint over the whole view
        g.fillGradient(0, 0, w, h / 4, 0x66021C30, 0x00021C30);           // darker top and bottom edges
        g.fillGradient(0, h - h / 4, w, h, 0x00021C30, 0x66021C30);
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x07CFF4FF); // faint scan lines
    }

    private static void frame(GuiGraphicsExtractor g, int w, int h, int m) {
        corners(g, m, m, w - 2 * m, h - 2 * m, 26, LINE);
        corners(g, m + 3, m + 3, w - 2 * m - 6, h - 2 * m - 6, 8, DIM);
        // side rulers
        for (int i = 0; i < 9; i++) {
            int y = h / 2 - 40 + i * 10;
            int len = i % 4 == 0 ? 6 : 3;
            g.fill(m, y, m + len, y + 1, DIM);
            g.fill(w - m - len, y, w - m, y + 1, DIM);
        }
    }

    // ------------------------------------------------------------------ top: title + compass

    private static void title(GuiGraphicsExtractor g, Font font, int cx, long time) {
        String s;
        int col = BRIGHT;
        switch (Assistant.state) {
            case LISTENING -> { s = "VOICE INPUT"; col = AMBER; }
            case THINKING -> s = "PROCESSING";
            case SPEAKING -> s = "E.D.I.T.H.";
            default -> {
                if (Recon.active()) { s = "RECON DRONE"; col = AMBER; }
                else if (Duel.active()) { s = "DUEL ASSIST"; col = RED; }
                else s = "SCAN ACTIVATED";
            }
        }
        int tw = width(font, s, 0.8f) + 24;
        int x = cx - tw / 2;
        g.fill(x, 5, x + tw, 17, 0x7706202F);
        box(g, x, 5, tw, 12, LINE);
        g.fill(x - 6, 11, x, 12, DIM);
        g.fill(x + tw, 11, x + tw + 6, 12, DIM);
        boolean pulse = (time / 10) % 2 == 0;
        g.fill(x + 5, 9, x + 8, 12, pulse ? col : alpha(col, 0.4f));
        centered(g, font, s, cx + 3, 8, col, 0.8f);
    }

    private static void compass(GuiGraphicsExtractor g, Font font, int cx, int y, int half, float heading) {
        g.fill(cx - half, y + 11, cx + half, y + 12, DIM);
        for (int deg = (int) Math.floor((heading - 60) / 5) * 5; deg <= heading + 60; deg += 5) {
            int x = cx + Math.round((deg - heading) * (half / 60.0F));
            if (Math.abs(x - cx) > half) continue;
            int d = Math.floorMod(deg, 360);
            boolean major = d % 15 == 0;
            g.fill(x, y + (major ? 6 : 8), x + 1, y + 11, major ? LINE : FAINT);
            String lab = cardinal(d);
            if (lab != null) centered(g, font, lab, x, y - 2, d % 90 == 0 ? BRIGHT : LINE, 0.75f);
            else if (d % 30 == 0) centered(g, font, String.valueOf(d), x, y, DIM, 0.6f);
        }
        g.fill(cx, y + 12, cx + 1, y + 17, BRIGHT);
        g.fill(cx - 2, y + 17, cx + 3, y + 18, BRIGHT);
        centered(g, font, String.format("%03d", Math.round(heading) % 360), cx, y + 20, BRIGHT, 0.75f);
    }

    private static String cardinal(int deg) {
        return switch (deg) {
            case 0 -> "N"; case 45 -> "NE"; case 90 -> "E"; case 135 -> "SE";
            case 180 -> "S"; case 225 -> "SW"; case 270 -> "W"; case 315 -> "NW";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ centre: horizon, reticle, gauges

    private static void gauges(GuiGraphicsExtractor g, Font font, Player p, int cx, int cy, int h) {
        int r = Math.min((int) (h * 0.30), 80);

        // horizon line with a gap in the middle
        for (int x = cx - r + 10; x < cx - 16; x += 6) g.fill(x, cy, Math.min(x + 4, cx - 16), cy + 1, DIM);
        for (int x = cx + 16; x < cx + r - 10; x += 6) g.fill(x, cy, Math.min(x + 4, cx + r - 10), cy + 1, DIM);
        ring(g, cx, cy, 3, BRIGHT, 30);
        g.fill(cx, cy, cx + 1, cy + 1, BRIGHT);
        g.fill(cx - 9, cy, cx - 5, cy + 1, LINE);
        g.fill(cx + 6, cy, cx + 10, cy + 1, LINE);
        g.fill(cx, cy - 9, cx + 1, cy - 5, LINE);
        g.fill(cx, cy + 6, cx + 1, cy + 10, LINE);

        // left arc: health
        arc(g, cx, cy, r, 146, 214, LINE, 4, 12);
        float hp = Mth.clamp(p.getHealth() / Math.max(1f, p.getMaxHealth()), 0f, 1f);
        int hpCol = hp < 0.35f ? RED : hp < 0.6f ? AMBER : GREEN;
        arcFill(g, cx, cy, r, 214 - 68 * hp, 214, hpCol);
        text(g, font, "HP", cx - r - 26, cy - 14, DIM, 0.7f);
        text(g, font, String.valueOf(Math.round(p.getHealth())), cx - r - 26, cy - 5, hpCol, 1.0f);
        int food = p.getFoodData().getFoodLevel();
        text(g, font, "FOOD " + food, cx - r - 26, cy + 8, food <= 6 ? AMBER : DIM, 0.65f);
        text(g, font, "ARMOR " + p.getArmorValue(), cx - r - 26, cy + 16, DIM, 0.65f);
        if (p.experienceLevel > 0) text(g, font, "LVL " + p.experienceLevel, cx - r - 26, cy + 24, GREEN, 0.65f);

        // right arc: pitch
        arc(g, cx, cy, r, -34, 34, LINE, 4, 12);
        float pitch = -p.getXRot();
        double a = Mth.clamp(pitch, -60f, 60f) / 60.0 * 32.0;
        for (int k = -4; k <= 9; k++) {
            int x = cx + (int) Math.round((r + k) * Math.cos(Math.toRadians(a))), y = cy - (int) Math.round((r + k) * Math.sin(Math.toRadians(a)));
            g.fill(x, y, x + 1, y + 1, BRIGHT);
        }
        text(g, font, "PITCH", cx + r + 12, cy - 14, DIM, 0.7f);
        text(g, font, String.format("%+.0f", pitch), cx + r + 12, cy - 5, BRIGHT, 1.0f);
        float z = Glasses.targetZoom();
        text(g, font, String.format("ZOOM x%.1f", z), cx + r + 12, cy + 8, z > 1.05f ? AMBER : DIM, 0.65f);
    }

    // ------------------------------------------------------------------ left telemetry

    private static void telemetry(GuiGraphicsExtractor g, Font font, Minecraft mc, Player p, int x, int y) {
        BlockPos bp = p.blockPosition();
        long t = mc.level.getDefaultClockTime() % 24000L;
        int hh = (int) ((t / 1000 + 6) % 24), mm = (int) ((t % 1000) * 60 / 1000);
        Vec3 v = p.getDeltaMovement();
        double speed = Math.hypot(v.x, v.z) * 20.0;
        String biome = mc.level.getBiome(bp).unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
        int light = mc.level.getMaxLocalRawBrightness(bp);
        String[][] rows = {
            {"SYS", "ONLINE"},
            {"POS", bp.getX() + " " + bp.getZ()},
            {"ALT", String.valueOf(bp.getY())},
            {"SPD", String.format("%.1f m/s", speed)},
            {"BIO", biome.replace('_', ' ')},
            {"LUX", String.valueOf(light)},
            {"TIME", String.format("%02d:%02d", hh, mm)},
        };
        g.fill(x - 4, y - 2, x - 3, y + rows.length * 9, DIM);
        for (int i = 0; i < rows.length; i++) {
            int yy = y + i * 9;
            g.fill(x - 4, yy + 3, x - 1, yy + 4, LINE);
            text(g, font, rows[i][0], x, yy, DIM, 0.65f);
            boolean low = rows[i][0].equals("LUX") && light < 8;
            text(g, font, rows[i][1], x + 22, yy, low ? AMBER : BRIGHT, 0.65f);
        }
    }

    // ------------------------------------------------------------------ right: radar and contact list

    private static void radar(GuiGraphicsExtractor g, Font font, Minecraft mc, Player p, int cx, int cy, int r, long time) {
        g.fill(cx - r - 4, cy - r - 4, cx + r + 4, cy + r + 4, 0x55041722);
        corners(g, cx - r - 4, cy - r - 4, 2 * r + 8, 2 * r + 8, 6, LINE);
        ring(g, cx, cy, r, DIM, 6);
        ring(g, cx, cy, r / 2, FAINT, 9);
        g.fill(cx - r, cy, cx + r + 1, cy + 1, FAINT);
        g.fill(cx, cy - r, cx + 1, cy + r + 1, FAINT);
        // sweep
        double sweep = Math.toRadians((time * 6) % 360);
        for (int i = 0; i < r; i += 1) {
            int x = cx + (int) Math.round(i * Math.cos(sweep)), y = cy - (int) Math.round(i * Math.sin(sweep));
            g.fill(x, y, x + 1, y + 1, alpha(LINE, 0.5f));
        }
        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        // north marker
        int nx = cx + (int) Math.round((r - 2) * Math.sin(yaw)), ny = cy + (int) Math.round((r - 2) * Math.cos(yaw));
        centered(g, font, "N", nx, ny - 3, AMBER, 0.6f);

        List<String> contacts = new ArrayList<>();
        double range = 32.0;
        List<Entity> near = new ArrayList<>(mc.level.getEntities(p, p.getBoundingBox().inflate(range)));
        near.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        int hostile = 0;
        for (Entity e : near) {
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
            double d = Math.hypot(dx, dz);
            if (d > range) continue;
            double sx = (dx * rx + dz * rz) / range * r, sy = (dx * fx + dz * fz) / range * r;
            int col = e instanceof Enemy ? RED : e instanceof Player ? 0xFF60D0FF : AMBER;
            int px = cx + (int) Math.round(sx), py = cy - (int) Math.round(sy);
            g.fill(px - 1, py - 1, px + 2, py + 2, col);
            if (e instanceof Enemy) {
                hostile++;
                if (contacts.size() < 4) contacts.add(e.getName().getString() + " " + Math.round(p.distanceTo(e)) + "m");
            }
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, BRIGHT);
        int ly = cy + r + 9;
        text(g, font, "CONTACTS " + hostile, cx - r - 4, ly, hostile > 0 ? RED : DIM, 0.65f);
        for (int i = 0; i < contacts.size(); i++) text(g, font, contacts.get(i), cx - r - 4, ly + 8 + i * 7, alpha(RED, 0.85f), 0.6f);
    }

    // ------------------------------------------------------------------ target lock

    private static void target(GuiGraphicsExtractor g, Font font, Minecraft mc, Player p, int w, int h, int m) {
        Entity e = Glasses.target();
        if (e == null) return;
        if (e instanceof Player && com.example.edithglasses.feature.FaceId.active()) return;   // the recognition tag already shows this
        boolean hostile = e instanceof Enemy;
        int col = hostile ? RED : e instanceof Player ? 0xFF60D0FF : LINE;

        // brackets around the entity itself
        Project pr = new Project(w, h);
        double[] top = pr.at(new Vec3(e.getX(), e.getY() + e.getBbHeight(), e.getZ()));
        double[] bot = pr.at(new Vec3(e.getX(), e.getY(), e.getZ()));
        if (top != null && bot != null) {
            int hh = (int) Math.max(14, Math.abs(bot[1] - top[1]));
            int ww = (int) Math.max(12, hh * 0.62);
            int x = (int) ((top[0] + bot[0]) / 2) - ww / 2, y = (int) Math.min(top[1], bot[1]) - 3;
            corners(g, x, y, ww, hh + 6, Math.max(4, Math.min(10, hh / 4)), col);
            String label = e.getName().getString().toUpperCase() + "  " + Math.round(p.distanceTo(e)) + " M";
            text(g, font, label, x, y - 9, col, 0.7f);
            if (e instanceof LivingEntity le) {
                float f = Mth.clamp(le.getHealth() / Math.max(1f, le.getMaxHealth()), 0f, 1f);
                g.fill(x, y + hh + 8, x + ww, y + hh + 10, FAINT);
                g.fill(x, y + hh + 8, x + Math.round(ww * f), y + hh + 10, col);
            }
        }

        // data card, bottom left
        HudLayout.begin(g, HudLayout.El.TARGET);
        int cw = 104, ch = 60, x = m + 6, y = h - m - 52 - ch - 8;
        panel(g, x, y, cw, ch, col);
        text(g, font, "TARGET DATA ACQUIRED", x + 4, y + 4, alpha(col, 0.9f), 0.55f);
        text(g, font, e.getName().getString().toUpperCase(), x + 4, y + 13, BRIGHT, 0.9f);
        String kind = hostile ? "HOSTILE" : e instanceof Player ? "PLAYER" : e instanceof LivingEntity ? "NEUTRAL" : "OBJECT";
        text(g, font, kind + "   " + Math.round(p.distanceTo(e)) + " M", x + 4, y + 25, col, 0.65f);
        if (e instanceof LivingEntity le) {
            float f = Mth.clamp(le.getHealth() / Math.max(1f, le.getMaxHealth()), 0f, 1f);
            g.fill(x + 4, y + 36, x + cw - 4, y + 39, FAINT);
            g.fill(x + 4, y + 36, x + 4 + Math.round((cw - 8) * f), y + 39, col);
            text(g, font, "HP " + Math.round(le.getHealth()) + "/" + Math.round(le.getMaxHealth()), x + 4, y + 41, DIM, 0.55f);
            int th = com.example.edithglasses.feature.Analyse.threat(e, p);
            String bar = "THREAT " + "#".repeat(th) + "-".repeat(5 - th);
            text(g, font, th == 0 ? "THREAT NONE" : bar, x + 4, y + 50, th >= 4 ? RED : th >= 3 ? AMBER : GREEN, 0.6f);
        }
        HudLayout.end(g);
    }

    // ------------------------------------------------------------------ bottom: mode tabs

    private static void tabs(GuiGraphicsExtractor g, Font font, int cx, int y) {
        EdithConfig c = EdithConfig.get();
        Object[][] tabs = {
            {"NVG", Glasses.nightActive()}, {"THM", Glasses.thermalActive()}, {"XRY", OreScan.active()},
            {"SNT", Guard.enabled()}, {"HEL", AutoHeal.enabled()}, {"CLT", com.example.edithglasses.feature.Clutch.enabled()}, {"ALR", c.alerts},
            {"SUB", c.subtitles}, {"TRK", !Tracker.name().isEmpty()}, {"RCN", Recon.active()},
        };
        int tw = 26, gap = 3, total = tabs.length * tw + (tabs.length - 1) * gap;
        int x = cx - total / 2;
        for (Object[] t : tabs) {
            boolean on = (boolean) t[1];
            if (on) {
                g.fill(x, y, x + tw, y + 10, alpha(LINE, 0.55f));
                box(g, x, y, tw, 10, BRIGHT);
            } else {
                g.fill(x, y, x + tw, y + 10, 0x55041722);
                box(g, x, y, tw, 10, DIM);
            }
            centered(g, font, (String) t[0], x + tw / 2, y + 2, on ? 0xFF021018 : DIM, 0.7f);
            x += tw + gap;
        }
        g.fill(cx - total / 2, y - 2, cx + total / 2, y - 1, FAINT);
    }

    // ------------------------------------------------------------------ hotbar

    private static void hotbar(GuiGraphicsExtractor g, Font font, Player p, int cx, int y) {
        int slot = 18, gap = 2, total = 9 * slot + 8 * gap;
        int x0 = cx - total / 2;
        int sel = p.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            int x = x0 + i * (slot + gap);
            boolean on = i == sel;
            g.fill(x, y, x + slot, y + slot, on ? 0x99166486 : 0x77041722);
            box(g, x, y, slot, slot, on ? BRIGHT : DIM);
            if (on) corners(g, x - 2, y - 2, slot + 4, slot + 4, 4, BRIGHT);
            var stack = p.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                g.item(stack, x + 1, y + 1);
                g.itemDecorations(font, stack, x + 1, y + 1);
            }
            text(g, font, String.valueOf(i + 1), x + 2, y + 1, alpha(on ? BRIGHT : DIM, 0.8f), 0.5f);
        }
        var off = p.getOffhandItem();
        if (!off.isEmpty()) {
            int x = x0 - slot - 8;
            g.fill(x, y, x + slot, y + slot, 0x77041722);
            box(g, x, y, slot, slot, AMBER);
            g.item(off, x + 1, y + 1);
            g.itemDecorations(font, off, x + 1, y + 1);
        }
        var held = p.getInventory().getItem(sel);
        if (!held.isEmpty()) centered(g, font, held.getHoverName().getString().toUpperCase(), cx, y - 9, BRIGHT, 0.7f);
        if (p.getAirSupply() < p.getMaxAirSupply()) {
            float f = Mth.clamp(p.getAirSupply() / (float) p.getMaxAirSupply(), 0f, 1f);
            g.fill(x0, y - 16, x0 + total, y - 14, FAINT);
            g.fill(x0, y - 16, x0 + Math.round(total * f), y - 14, f < 0.3f ? RED : 0xFF60D0FF);
            text(g, font, "O2", x0 - 12, y - 18, f < 0.3f ? RED : DIM, 0.6f);
        }
    }

    // ------------------------------------------------------------------ assistant panel

    private static void drawAssistant(GuiGraphicsExtractor g, Font font, int w, int bottom, int cx, long time) {
        Assistant.State st = Assistant.state;
        boolean showReply = System.currentTimeMillis() < Assistant.replyUntil && !Assistant.reply.isEmpty();
        String pend = Assistant.pendingText();
        if (st == Assistant.State.IDLE && !showReply && pend == null) return;
        int maxW = Math.min(w / 2, 300);
        List<String> lines = new ArrayList<>();
        String head = switch (st) {
            case LISTENING -> ((time / 5) % 2 == 0 ? "* " : "o ") + "LISTENING";
            case THINKING -> "PROCESSING...";
            default -> "E.D.I.T.H.";
        };
        lines.add(head);
        if (st != Assistant.State.LISTENING && showReply) {
            StringBuilder cur = new StringBuilder();
            for (String word : Assistant.reply.split("\\s+")) {
                if (cur.length() > 0 && width(font, cur + " " + word, 0.85f) > maxW) {
                    lines.add(cur.toString());
                    cur.setLength(0);
                }
                if (cur.length() > 0) cur.append(' ');
                cur.append(word);
            }
            if (cur.length() > 0) lines.add(cur.toString());
        }
        int warnLines = 0;
        if (pend != null) {
            lines.add("CONFIRM: " + (pend.length() > 48 ? pend.substring(0, 48) + "..." : pend));
            lines.add("press Y to confirm, N to cancel (or say it)");
            warnLines = 2;
        }
        int lh = 10, pw = maxW + 16, ph = lines.size() * lh + 8;
        int x0 = cx - pw / 2, y1 = bottom, y0 = y1 - ph;
        panel(g, x0, y0, pw, ph, LINE);
        int y = y0 + 5;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? BRIGHT : (i >= lines.size() - warnLines ? AMBER : LINE);
            text(g, font, lines.get(i), x0 + 8, y, color, i == 0 ? 0.8f : 0.85f);
            y += lh;
        }
    }
}
