package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.Glasses;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Heals you when your health gets low and the glasses are on, using what is in your hotbar, fastest first:
 * a thrown splash potion of healing (instant), an enchanted golden apple, a golden apple, a potion of healing.
 * Also moves a totem of undying into your off hand so the game can use it. Hotbar and off hand only.
 */
public final class AutoHeal {
    private enum Plan { NONE, THROW, EAT }

    private static Plan plan = Plan.NONE;
    private static int slot = -1;
    private static int previousSlot = -1;
    private static int holdLeft = 0;
    private static int cooldown = 0;
    private static int totemCooldown = 0;

    private AutoHeal() {
    }

    public static boolean enabled() {
        return EdithConfig.get().autoHeal && Glasses.equipped();
    }

    /** Before the player's tick: keep the use key down while eating or drinking. */
    public static void pre(Minecraft mc) {
        if (mc.player == null) return;
        if (plan == Plan.EAT && holdLeft > 0 && mc.gui.screen() == null) {
            mc.options.keyUse.setDown(true);
        } else if (plan == Plan.EAT) {
            mc.options.keyUse.setDown(false);
        }
    }

    private static boolean force = false;

    /** Heal now with whatever is in the hotbar, whatever your health is. */
    public static void healNow(Minecraft mc) {
        force = true;
        cooldown = 0;
    }

    public static void tick(Minecraft mc) {
        var me = mc.player;
        if (me == null || mc.level == null || mc.gameMode == null) return;
        if (cooldown > 0) cooldown--;
        if (totemCooldown > 0) totemCooldown--;

        if (plan != Plan.NONE) {
            if (holdLeft > 0) holdLeft--;
            if (holdLeft == 0) finish(mc);
            return;
        }
        boolean now = force;
        if (now && mc.gui.screen() == null) force = false;
        if (mc.gui.screen() != null || me.isSpectator() || me.isCreative() || cooldown > 0) return;
        if (!now && !enabled()) return;
        float low = EdithConfig.get().autoHealAt;
        if (!now && me.getHealth() > low) return;

        // totem into the off hand so it can save you
        if (totemCooldown == 0 && !me.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            int t = find(mc, 3);
            if (t >= 0) {
                int old = me.getInventory().getSelectedSlot();
                // tell the server which slot we mean before swapping, then switch back
                me.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(t));
                me.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                me.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(old));
                totemCooldown = 100;
            }
        }

        int best = -1;
        Plan p = Plan.NONE;
        if ((best = find(mc, 0)) >= 0) p = Plan.THROW;
        else if ((best = find(mc, 1)) >= 0) p = Plan.EAT;      // enchanted / golden apple
        else if ((best = find(mc, 2)) >= 0) p = Plan.EAT;      // drinkable healing potion
        if (p == Plan.NONE) return;

        previousSlot = me.getInventory().getSelectedSlot();
        slot = best;
        me.getInventory().setSelectedSlot(slot);
        plan = p;
        if (p == Plan.THROW) {
            float pitch = me.getXRot();
            me.setXRot(90f); // throw it at your own feet
            mc.gameMode.useItem(me, InteractionHand.MAIN_HAND);
            me.swing(InteractionHand.MAIN_HAND);
            me.setXRot(pitch);
            holdLeft = 3;
        } else {
            holdLeft = 38; // eating / drinking takes 1.6 s
        }
        Alerts.push(mc, "HEALING", 0xFF70FF90, null);
    }

    private static void finish(Minecraft mc) {
        var me = mc.player;
        mc.options.keyUse.setDown(false);
        if (me != null && previousSlot >= 0 && previousSlot < 9) me.getInventory().setSelectedSlot(previousSlot);
        plan = Plan.NONE;
        slot = -1;
        cooldown = 30;
    }

    /** Hotbar slot of the best item of a kind: 0 splash healing, 1 apples, 2 drinkable healing, 3 totem. -1 if none. */
    private static int find(Minecraft mc, int kind) {
        var inv = mc.player.getInventory();
        int found = -1;
        int rank = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            int r = -1;
            switch (kind) {
                case 0 -> { if (s.is(Items.SPLASH_POTION) && healing(s)) r = 1; }
                case 1 -> r = s.is(Items.ENCHANTED_GOLDEN_APPLE) ? 2 : s.is(Items.GOLDEN_APPLE) ? 1 : -1;
                case 2 -> { if (s.is(Items.POTION) && healing(s)) r = 1; }
                default -> { if (s.is(Items.TOTEM_OF_UNDYING)) r = 1; }
            }
            if (r > rank) {
                rank = r;
                found = i;
            }
        }
        return found;
    }

    private static boolean healing(ItemStack s) {
        PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
        return pc != null && (pc.is(Potions.HEALING) || pc.is(Potions.STRONG_HEALING));
    }
}
