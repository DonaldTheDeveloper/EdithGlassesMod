package com.example.edithglasses.ai;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.UUID;

/**
 * A real drone for everyone on the server: block_display entities (summoned with commands, so you need
 * permission to use /summon) glide from your position to the target, then boom, then /kill on the target.
 */
public final class DroneStrike {
    private DroneStrike() {
    }

    public static void launch(Minecraft mc, UUID targetId) throws InterruptedException {
        var p = mc.player;
        if (p == null || mc.level == null) return;
        Entity target = null;
        for (Entity e : mc.level.entitiesForRendering()) if (e.getUUID().equals(targetId)) { target = e; break; }
        if (target == null) {
            run(mc, "kill " + targetId);
            return;
        }
        String t = "e" + Long.toString(System.currentTimeMillis() % 100000000L, 36);
        String all = "edith_" + t, root = "edith_" + t + "_root";
        // start next to the player (slightly above and in front), as offsets from the target
        double ox = p.getX() - target.getX();
        double oy = p.getEyeY() + 1.2 - target.getY();
        double oz = p.getZ() - target.getZ();
        String at = "execute at " + targetId + " run ";

        // Few commands, spaced out: many servers kick players for sending commands too fast.
        run(mc, at + "summon minecraft:block_display ~" + f(ox) + " ~" + f(oy) + " ~" + f(oz) + " " + droneNbt(all, root));
        Thread.sleep(900); // let every client receive the drone before it moves
        run(mc, at + "tp @e[tag=" + root + ",limit=1] ~ ~1.4 ~"); // glides there over 40 ticks (2 s)

        for (int i = 1; i <= 3; i++) {
            double fr = i / 4.0;
            double x = ox * (1 - fr), y = oy * (1 - fr) + 1.4 * fr, z = oz * (1 - fr);
            Thread.sleep(delay(mc));
            run(mc, at + "particle minecraft:large_smoke ~" + f(x) + " ~" + f(y) + " ~" + f(z) + " 0.15 0.15 0.15 0.02 12 force");
        }
        Thread.sleep(delay(mc));
        run(mc, at + "particle minecraft:explosion_emitter ~ ~1 ~");
        Thread.sleep(delay(mc));
        run(mc, "kill " + targetId);
        Thread.sleep(delay(mc));
        run(mc, "kill @e[tag=" + all + "]");
    }

    private static long delay(Minecraft mc) {
        return Math.max(200, com.example.edithglasses.EdithConfig.get().commandDelayMs);
    }

    private static String droneNbt(String all, String root) {
        String tags = "Tags:[\"edith_drone\",\"" + all + "\",\"" + root + "\"]";
        String ptags = "Tags:[\"edith_drone\",\"" + all + "\"]";
        StringBuilder pass = new StringBuilder();
        pass.append(part("minecraft:black_concrete", 2.0, 0.07, 0.12, -1.0, 0.0, -0.06, ptags, false));
        pass.append(',').append(part("minecraft:black_concrete", 0.12, 0.07, 2.0, -0.06, 0.0, -1.0, ptags, false));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                pass.append(',').append(part("minecraft:light_gray_stained_glass", 0.6, 0.03, 0.6,
                    sx * 0.95 - 0.3, 0.07, sz * 0.95 - 0.3, ptags, false));
            }
        }
        pass.append(',').append(part("minecraft:redstone_block", 0.22, 0.22, 0.22, -0.11, -0.33, -0.11, ptags, true));
        return "{block_state:{Name:\"minecraft:iron_block\"},teleport_duration:40,view_range:4f," + tags + ","
            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[0.7f,0.22f,0.7f],translation:[-0.35f,-0.11f,-0.35f]},"
            + "Passengers:[" + pass + "]}";
    }

    private static String part(String block, double sx, double sy, double sz, double tx, double ty, double tz, String tags, boolean glow) {
        return "{id:\"minecraft:block_display\",block_state:{Name:\"" + block + "\"},view_range:4f," + tags
            + (glow ? ",brightness:{sky:15,block:15}" : "")
            + ",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[" + f(sx) + "f," + f(sy) + "f," + f(sz)
            + "f],translation:[" + f(tx) + "f," + f(ty) + "f," + f(tz) + "f]}}";
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static void run(Minecraft mc, String cmd) throws InterruptedException {
        mc.execute(() -> {
            if (mc.player != null) mc.player.connection.sendCommand(cmd);
        });
    }
}
