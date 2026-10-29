package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.Glasses;
import com.example.edithglasses.mixin.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * X-ray as an AR overlay: ores around you are marked on the HUD through the walls. It does not touch the world
 * rendering, it only projects block positions onto the screen. Many servers ban this: use it in single player.
 */
public final class OreScan {
    private record Hit(int x, int y, int z, int color, String name, boolean rare) {
    }

    private static volatile List<Hit> hits = new CopyOnWriteArrayList<>();
    private static int tick = 0;

    private OreScan() {
    }

    public static boolean active() {
        return Glasses.equipped() && EdithConfig.get().xray;
    }

    public static void tick(Minecraft mc) {
        if (!active() || mc.level == null || mc.player == null) {
            if (!hits.isEmpty()) hits = new CopyOnWriteArrayList<>();
            return;
        }
        if (tick++ % 20 != 0) return;
        int r = Math.max(4, Math.min(32, EdithConfig.get().xrayRadius));
        BlockPos c = mc.player.blockPosition();
        List<Hit> found = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = c.getX() - r; x <= c.getX() + r; x++) {
            for (int z = c.getZ() - r; z <= c.getZ() + r; z++) {
                for (int y = Math.max(mc.level.getMinY(), c.getY() - r); y <= Math.min(mc.level.getMaxY(), c.getY() + r); y++) {
                    BlockState s = mc.level.getBlockState(m.set(x, y, z));
                    if (s.isAir()) continue;
                    String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
                    if (!(id.endsWith("_ore") || id.equals("ancient_debris"))) continue;
                    found.add(new Hit(x, y, z, color(id), name(id), rare(id)));
                    if (found.size() > 400) break;
                }
            }
        }
        hits = new CopyOnWriteArrayList<>(found);
    }

    private static boolean rare(String id) {
        return id.contains("diamond") || id.contains("emerald") || id.contains("debris") || id.contains("gold");
    }

    private static String name(String id) {
        String n = id.replace("deepslate_", "").replace("nether_", "").replace("_ore", "").replace("ancient_debris", "debris");
        return n.isEmpty() ? id : Character.toUpperCase(n.charAt(0)) + n.substring(1).replace('_', ' ');
    }

    private static int color(String id) {
        if (id.contains("diamond")) return 0xFF40E8FF;
        if (id.contains("emerald")) return 0xFF40FF70;
        if (id.contains("debris")) return 0xFFB05030;
        if (id.contains("gold")) return 0xFFFFD040;
        if (id.contains("iron")) return 0xFFE0B090;
        if (id.contains("copper")) return 0xFFFF9040;
        if (id.contains("redstone")) return 0xFFFF4040;
        if (id.contains("lapis")) return 0xFF4070FF;
        if (id.contains("coal")) return 0xFF9098A0;
        if (id.contains("quartz")) return 0xFFF0F0F0;
        return 0xFFFFFFFF;
    }

    public static void draw(GuiGraphicsExtractor g, Font font, int w, int h) {
        if (!active() || hits.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 pos = cam.position();
        float yaw = cam.yRot(), pitch = cam.xRot();
        Vec3 f = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = f.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(f);
        double tan = Math.tan(Math.toRadians(((CameraAccessor) cam).edith$fov()) / 2.0);
        double aspect = (double) w / h;
        int drawn = 0;
        for (Hit hit : hits) {
            Vec3 d = new Vec3(hit.x() + 0.5 - pos.x, hit.y() + 0.5 - pos.y, hit.z() + 0.5 - pos.z);
            double z = d.dot(f);
            if (z < 0.6) continue;
            double sx = w / 2.0 + (d.dot(right) / (z * tan * aspect)) * (w / 2.0);
            double sy = h / 2.0 - (d.dot(up) / (z * tan)) * (h / 2.0);
            if (sx < 2 || sx > w - 2 || sy < 2 || sy > h - 2) continue;
            int size = (int) Math.max(4, Math.min(34, (1.0 / (z * tan)) * (h / 2.0)));
            int x0 = (int) sx - size / 2, y0 = (int) sy - size / 2;
            int col = hit.color();
            int t = 1;
            g.fill(x0, y0, x0 + size, y0 + t, col);
            g.fill(x0, y0 + size - t, x0 + size, y0 + size, col);
            g.fill(x0, y0, x0 + t, y0 + size, col);
            g.fill(x0 + size - t, y0, x0 + size, y0 + size, col);
            g.fill(x0 + size / 2, y0 + size / 2, x0 + size / 2 + 1, y0 + size / 2 + 1, col);
            if (hit.rare() && drawn < 14) {
                String label = hit.name() + " " + Math.round(Math.sqrt(d.lengthSqr())) + "m";
                g.text(font, label, x0 + size + 3, y0 + size / 2 - 4, col, false);
                drawn++;
            }
        }
    }
}
