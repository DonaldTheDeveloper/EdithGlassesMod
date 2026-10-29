package com.example.edithglasses.ai;

import com.example.chattranslator.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the short text snapshot of the game that is sent along with every question. */
public final class GameContext {
    private static final Deque<String> CHAT = new ArrayDeque<>();

    private GameContext() {
    }

    public static synchronized void remember(String line) {
        String s = line.trim();
        if (s.isEmpty() || s.startsWith("EDITH:")) return;
        if (s.length() > 220) s = s.substring(0, 220);
        CHAT.addLast(s);
        while (CHAT.size() > 10) CHAT.removeFirst();
    }

    public static String build(Minecraft mc) {
        Player p = mc.player;
        if (p == null || mc.level == null) return "(not in a world)";
        StringBuilder sb = new StringBuilder();
        BlockPos bp = p.blockPosition();
        sb.append("Player: ").append(p.getName().getString()).append('\n');
        sb.append("Position: ").append(bp.getX()).append(' ').append(bp.getY()).append(' ').append(bp.getZ())
            .append(" facing yaw ").append(Math.round(p.getYRot())).append('\n');
        sb.append("Dimension: ").append(mc.level.dimension().identifier()).append('\n');
        mc.level.getBiome(bp).unwrapKey().ifPresent(k -> sb.append("Biome: ").append(k.identifier().getPath()).append('\n'));
        long dt = mc.level.getDefaultClockTime() % 24000L;
        sb.append("Time of day: ").append(dt).append(" ticks (0=sunrise, 6000=noon, 13000=night)")
            .append(mc.level.isRaining() ? ", raining" : "").append('\n');
        sb.append("Health: ").append(Math.round(p.getHealth())).append('/').append(Math.round(p.getMaxHealth()))
            .append(", food ").append(p.getFoodData().getFoodLevel()).append("/20\n");
        sb.append("Main hand: ").append(name(p.getMainHandItem())).append(", off hand: ").append(name(p.getOffhandItem())).append('\n');
        sb.append("Armor: head ").append(name(p.getItemBySlot(EquipmentSlot.HEAD))).append(", chest ")
            .append(name(p.getItemBySlot(EquipmentSlot.CHEST))).append(", legs ").append(name(p.getItemBySlot(EquipmentSlot.LEGS)))
            .append(", feet ").append(name(p.getItemBySlot(EquipmentSlot.FEET))).append('\n');
        java.util.Map<String, Integer> inv = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!st.isEmpty()) inv.merge(st.getHoverName().getString(), st.getCount(), Integer::sum);
        }
        if (!inv.isEmpty()) {
            sb.append("Inventory: ");
            int n = 0;
            for (var e : inv.entrySet()) {
                if (n++ >= 36) break;
                sb.append(e.getKey()).append(" x").append(e.getValue()).append("; ");
            }
            sb.append('\n');
        }
        sb.append(com.example.edithglasses.feature.DamageLog.recent());
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult e) {
            sb.append("Looking at entity: ").append(e.getEntity().getName().getString()).append('\n');
            sb.append("Analysis of it: ").append(com.example.edithglasses.feature.Analyse.speech(mc, e.getEntity())).append('\n');
        } else if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) {
            sb.append("Looking at block: ").append(mc.level.getBlockState(b.getBlockPos()).getBlock().getName().getString()).append('\n');
        }
        Map<String, Integer> near = new LinkedHashMap<>();
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(16))) {
            if (e instanceof net.minecraft.world.entity.item.ItemEntity) continue;
            near.merge(e.getName().getString(), 1, Integer::sum);
        }
        if (!near.isEmpty()) {
            sb.append("Nearby (16 blocks): ");
            near.forEach((k, v) -> sb.append(k).append(" x").append(v).append("; "));
            sb.append('\n');
        }
        sb.append("Chat translator target language: ").append(Config.get().targetLang)
            .append(Config.get().autoTranslate ? " (auto-translate ON)" : " (auto-translate off)").append('\n');
        synchronized (GameContext.class) {
            if (!CHAT.isEmpty()) {
                sb.append("Recent chat (newest last; lines like [xx -> en] are translations):\n");
                for (String l : CHAT) sb.append("  ").append(l).append('\n');
            }
        }
        return sb.toString();
    }

    private static String name(ItemStack s) {
        return s.isEmpty() ? "empty" : s.getHoverName().getString();
    }
}
