package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.Glasses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import static com.example.edithglasses.feature.HudStyle.*;

/**
 * Hologram map: a small isometric terrain model of your surroundings that turns with you (what is ahead is up).
 * It is built from the blocks your game already loaded, scanning down from just above your head, so inside a cave
 * it shows the cave and not the hills above. Entities show as dots: players green (friends) or white, hostile mobs
 * red, others cyan. Costs a few thousand block lookups twice a second, nothing more.
 */
public final class HoloMap {
    private static final int MAX_R = 28;
    private static final int SPAN = MAX_R * 2 + 1;
    private static final int DEPTH = 22;

    private static final byte[] rel = new byte[SPAN * SPAN];     // height above the lowest scanned level
    private static final byte[] kind = new byte[SPAN * SPAN];    // 0 none, 1 solid, 2 water, 3 lava
    private static int scannedR = -1, baseY = 0, originX = 0, originZ = 0, age = 0;
    private static boolean valid = false;

    private HoloMap() {
    }

    public static boolean active() {
        return EdithConfig.get().holoMap && Glasses.equipped();
    }

    public static void tick(Minecraft mc) {
        if (!active() || mc.level == null || mc.player == null) return;
        if (++age < 10 && valid) return;
        age = 0;
        rescan(mc);
    }

    private static void rescan(Minecraft mc) {
        var me = mc.player;
        int r = Mth.clamp(EdithConfig.get().holoRadius, 8, MAX_R);
        BlockPos feet = me.blockPosition();
        baseY = feet.getY() - 12;
        originX = feet.getX();
        originZ = feet.getZ();
        scannedR = r;
        var pos = new BlockPos.MutableBlockPos();
        int top = feet.getY() + 6;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int i = (dz + MAX_R) * SPAN + dx + MAX_R;
                byte k = 0;
                int found = Integer.MIN_VALUE;
                for (int y = top; y > top - DEPTH; y--) {
                    pos.set(originX + dx, y, originZ + dz);
                    var st = mc.level.getBlockState(pos);
                    if (st.isAir()) continue;
                    var fl = st.getFluidState();
                    if (!fl.isEmpty()) {
                        k = (byte) (fl.is(net.minecraft.tags.FluidTags.LAVA) ? 3 : 2);
                        found = y;
                        break;
                    }
                    if (st.getCollisionShape(mc.level, pos).isEmpty()) continue;   // grass, flowers, torches...
                    k = 1;
                    found = y;
                    break;
                }
                kind[i] = k;
                rel[i] = (byte) (found == Integer.MIN_VALUE ? 0 : Mth.clamp(found - (top - DEPTH), 0, DEPTH));
            }
        }
        valid = true;
    }

    private record Cell(float sx, float depth, int i, int dx, int dz) {
    }

    private static final java.util.ArrayList<Cell> cells = new java.util.ArrayList<>();

    public static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
        if (!active() || !valid) return;
        Minecraft mc = Minecraft.getInstance();
        Player me = mc.player;
        int r = scannedR;
        int cell = Math.max(2, Math.min(4, (w - 8) / (2 * r + 1)));
        int cx = x + w / 2, cy = y + h / 2 + 8;

        panel(g, x, y, w, h, LINE);
        text(g, font, "HOLO MAP", x + 5, y + 4, LINE, 0.6f);
        String info = "R" + r + "   Y " + me.blockPosition().getY();
        text(g, font, info, x + w - 5 - width(font, info, 0.55f), y + 4, DIM, 0.55f);

        float yaw = me.getYRot();
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);        // forward
        double rx = -Math.cos(rad), rz = -Math.sin(rad);       // right
        // the model is offset from where you are now: the scan was taken at origin
        double offX = me.getX() - originX - 0.5, offZ = me.getZ() - originZ - 0.5;

        cells.clear();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dz * dz > r * r) continue;
                int i = (dz + MAX_R) * SPAN + dx + MAX_R;
                if (kind[i] == 0) continue;
                double ex = dx - offX, ez = dz - offZ;
                cells.add(new Cell((float) (ex * rx + ez * rz), (float) (ex * fx + ez * fz), i, dx, dz));
            }
        }
        cells.sort((a, b) -> Float.compare(b.depth, a.depth));   // far first

        g.enableScissor(x + 1, y + 10, x + w - 1, y + h - 1);
        float py = rel[(MAX_R) * SPAN + MAX_R];
        for (Cell c : cells) {
            int sx = cx + Math.round(c.sx * cell);
            int sy = cy - Math.round(c.depth * cell * 0.5f);
            int height = rel[c.i];
            int dh = height - (int) py;                         // blocks above (+) or below (-) your feet level
            int hy = Math.round(dh * cell * 0.9f);
            byte k = kind[c.i];
            float t = Mth.clamp(0.4f + dh / 12f, 0.05f, 1f);
            int top, bottom;
            if (k == 2) {
                top = 0xDD3A90FF;
                bottom = 0x881048B0;
            } else if (k == 3) {
                top = 0xEEFF9A40;
                bottom = 0x99902800;
            } else {
                top = 0xFF000000 | ((int) (30 + t * 150) << 16) | ((int) (150 + t * 105) << 8) | 255;
                bottom = 0xCC0A4A6E;
            }
            int colH = Mth.clamp(3 + dh * cell / 2 + 4, 3, 16);
            int ty = sy - hy;
            g.fillGradient(sx, ty, sx + cell, ty + colH, top, bottom);
            g.fill(sx, ty, sx + cell, ty + 1, alpha(top, 1f));
        }

        // entities as dots
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == me || !(e instanceof LivingEntity) || e.isInvisible()) continue;
                double ex = e.getX() - me.getX(), ez = e.getZ() - me.getZ();
                if (ex * ex + ez * ez > r * r) continue;
                double sxx = ex * rx + ez * rz, dep = ex * fx + ez * fz;
                double ey = (e.getY() - me.getY()) * cell * 0.45;
                int sx = cx + (int) Math.round(sxx * cell);
                int sy = cy - (int) Math.round(dep * cell * 0.5 + ey) - 2;
                int col = e instanceof Enemy ? RED : e instanceof Player pl ? FaceId.dotColor(pl) : 0xFF60D0FF;
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, col);
                g.fill(sx, sy + 2, sx + 1, sy + 3, alpha(col, 0.5f));
            }
        }

        // you: a marker pointing forward, with a pulse ring
        long t = System.currentTimeMillis();
        int pulse = (int) ((t / 90) % 14);
        ring(g, cx, cy, 3 + pulse, alpha(BRIGHT, 1f - pulse / 14f), 24);
        g.fill(cx - 1, cy - 4, cx + 2, cy + 2, BRIGHT);
        g.fill(cx, cy - 7, cx + 1, cy - 4, BRIGHT);
        g.disableScissor();

        // fixed compass tag: where north is on the model
        double nx = 0 * rx + (-1) * rz, nd = 0 * fx + (-1) * fz;
        int nxp = (int) Mth.clamp(cx + nx * (w / 2 - 12), x + 8, x + w - 8), nyp = (int) Mth.clamp(cy - nd * (h / 2 - 14) * 0.5, y + 14, y + h - 10);
        centered(g, font, "N", nxp, nyp - 3, AMBER, 0.7f);
    }
}
