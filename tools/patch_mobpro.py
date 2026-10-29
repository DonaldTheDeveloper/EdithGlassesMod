import os
p = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/feature/Guard.java")
s = open(p, encoding="utf-8").read()


def rep(a, b):
    global s
    if a not in s:
        raise SystemExit("missing anchor: " + a[:80])
    s = s.replace(a, b, 1)


# ---- state
rep("    private static boolean wasBlocking = false;\n", """    private static boolean wasBlocking = false;

    // pro tactics
    private static int pearlCooldown = 0;
    private static int drinkLeft = 0, drinkPrev = -1, buffCooldown = 0;
""")

# ---- shield also against melee while the weapon recharges
rep("        fblock = shield && shooter && !ready;", "        fblock = shield && (shooter || d < 3.4) && !ready;")

# ---- buffs before a crowd fight + pearl escape
rep("        // best weapon in the hotbar\n", """        // drink a missing buff (strength / speed / resistance) when a group is coming and nothing is on top of you yet
        if (buffCooldown > 0) buffCooldown--;
        if (drinkLeft > 0) {
            drinkLeft--;
            fightActive = true;
            fwd = false;
            back = me.distanceTo(target) < 2.5;
            strafeL = strafeR = fjump = false;
            fblock = true;                      // use key held = drinking
            if (drinkLeft == 0 && drinkPrev >= 0) me.getInventory().setSelectedSlot(drinkPrev);
            return;
        }
        int near = 0;
        for (Entity e2 : mc.level.getEntities(me, me.getBoundingBox().inflate(8))) {
            if (e2 instanceof Enemy && e2 instanceof LivingEntity l2 && l2.isAlive()) near++;
        }
        if (near >= 2 && buffCooldown == 0 && me.distanceTo(target) >= 4.5) {
            int slot = buffSlot(me);
            if (slot >= 0) {
                drinkPrev = me.getInventory().getSelectedSlot();
                me.getInventory().setSelectedSlot(slot);
                drinkLeft = 36;
                buffCooldown = 400;
                alert(mc, "SENTINEL: drinking a buff", 0xFF70FF90);
                return;
            }
        }

        // best weapon in the hotbar
""")

# helper methods before isFriend
rep("    private static boolean isFriend(Player o) {", """    /** Hotbar slot of a drinkable potion that gives strength, speed or resistance we do not have yet. */
    private static int buffSlot(Player me) {
        for (int i = 0; i < 9; i++) {
            ItemStack st = me.getInventory().getItem(i);
            if (!st.is(Items.POTION)) continue;
            var pc = st.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (pc == null) continue;
            for (var inst : pc.getAllEffects()) {
                var eff = inst.getEffect();
                boolean wanted = eff.equals(net.minecraft.world.effect.MobEffects.STRENGTH) || eff.equals(net.minecraft.world.effect.MobEffects.SPEED)
                    || eff.equals(net.minecraft.world.effect.MobEffects.RESISTANCE);
                if (wanted && !me.hasEffect(eff)) return i;
            }
        }
        return -1;
    }

    /** Last resort at low health: throw an ender pearl the way you are running. */
    private static void pearlEscape(Minecraft mc, float yaw) {
        var me = mc.player;
        if (pearlCooldown > 0 || mc.gameMode == null) return;
        int slot = -1;
        for (int i = 0; i < 9; i++) if (me.getInventory().getItem(i).is(Items.ENDER_PEARL)) slot = i;
        if (slot < 0) return;
        int prev = me.getInventory().getSelectedSlot();
        float pitch = me.getXRot();
        me.getInventory().setSelectedSlot(slot);
        me.setYRot(yaw);
        me.setXRot(-28f);
        mc.gameMode.useItem(me, net.minecraft.world.InteractionHand.MAIN_HAND);
        me.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        me.getInventory().setSelectedSlot(prev);
        me.setXRot(pitch);
        pearlCooldown = 200;
        alert(mc, "SENTINEL: ender pearl escape", 0xFFB070FF);
    }

    private static boolean isFriend(Player o) {""")

# ---- pearl hook in tick(): low health, threat found, direction chosen
rep("        if (fresh) alert(mc, \"SENTINEL: \" + t.why() + \" - running\", 0xFFFFC857);",
    "        if (pearlCooldown > 0) pearlCooldown--;\n"
    "        if (me.getHealth() <= 6f && me.distanceToSqr(t.pos()) < 49) pearlEscape(mc, targetYaw);\n"
    "        if (fresh) alert(mc, \"SENTINEL: \" + t.why() + \" - running\", 0xFFFFC857);")
open(p, "w", encoding="utf-8").write(s)
print("patched")
