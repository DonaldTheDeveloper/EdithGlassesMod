package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.Glasses;
import com.example.edithglasses.ai.Assistant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Threat alerts: hostile mobs coming close, creepers, low health and hunger. Shown on the HUD with a chime. */
public final class Alerts {
    public record Alert(String text, long until, int color) {
    }

    public static final List<Alert> ACTIVE = new CopyOnWriteArrayList<>();
    private static final Map<Integer, Long> SEEN = new HashMap<>();
    private static long lastVitals = 0;
    private static int tick = 0;

    private Alerts() {
    }

    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(a -> a.until() < now);
        var p = mc.player;
        if (p == null || mc.level == null || !EdithConfig.get().alerts || !Glasses.equipped(p)) return;
        if (++tick % 5 != 0) return;
        SEEN.values().removeIf(t -> now - t > 60_000);

        List<Entity> near = mc.level.getEntities(p, p.getBoundingBox().inflate(14));
        for (Entity e : near) {
            if (!(e instanceof LivingEntity le) || !(e instanceof Enemy) || !le.isAlive()) continue;
            double d = p.distanceTo(e);
            boolean creeper = e.getType() == EntityTypes.CREEPER;
            double range = creeper ? 9 : 11;
            if (d > range) continue;
            Long last = SEEN.get(e.getId());
            if (last != null && now - last < (creeper ? 6000 : 20_000)) continue;
            SEEN.put(e.getId(), now);
            String text = (creeper ? "CREEPER " : "THREAT: " + e.getName().getString() + "  ") + Math.round(d) + " m";
            push(mc, text, creeper ? 0xFFFF5050 : 0xFFFFC857, creeper ? "Creeper close." : null);
        }

        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(60))) {
            var ty = e.getType();
            if (!e.isAlive() || !(ty == EntityTypes.WITHER || ty == EntityTypes.ENDER_DRAGON || ty == EntityTypes.WARDEN || ty == EntityTypes.ELDER_GUARDIAN)) continue;
            Long last = SEEN.get(e.getId() + 1_000_000);
            if (last != null && now - last < 45_000) continue;
            SEEN.put(e.getId() + 1_000_000, now);
            String nm = e.getName().getString();
            push(mc, "BOSS: " + nm.toUpperCase() + "  " + Math.round(p.distanceTo(e)) + " m", 0xFFFF5050, nm + " nearby.");
        }

        if (now - lastVitals > 30_000) {
            float hp = p.getHealth() / Math.max(1f, p.getMaxHealth());
            if (hp <= 0.3f) {
                lastVitals = now;
                push(mc, "VITALS CRITICAL", 0xFFFF5050, "Your vitals are critical.");
            } else if (p.getFoodData().getFoodLevel() <= 6) {
                lastVitals = now;
                push(mc, "HUNGER LOW", 0xFFFFC857, null);
            }
        }
    }

    public static void push(Minecraft mc, String text, int color, String speech) {
        ACTIVE.add(new Alert(text, System.currentTimeMillis() + 4500, color));
        while (ACTIVE.size() > 3) ACTIVE.remove(0);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.6f));
        if (speech != null && EdithConfig.get().speakAlerts) Assistant.announce(mc, speech);
    }

    public static List<Alert> visible() {
        return new ArrayList<>(ACTIVE);
    }
}
