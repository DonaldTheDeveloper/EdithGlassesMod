import os
p = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/feature/Guard.java")
s = open(p, encoding="utf-8").read()

# ---- state
s = s.replace("    private static Player hurtBy;\n", """    private static Player hurtBy;

    // mob fighting state (keys are applied in pre())
    private static boolean fightActive = false, fwd, back, strafeL, strafeR, fjump, fblock;
    private static boolean wasFighting = false;
    private static int strafeTimer = 0, critWait = 0;
""", 1)

# ---- pre(): apply fight keys
old_pre = "        } else if (running) {\n            release(mc);\n        }\n    }\n"
new_pre = """        } else if (running) {
            release(mc);
        } else if (fightActive && mc.gui.screen() == null) {
            wasFighting = true;
            mc.options.keyUp.setDown(fwd);
            mc.options.keyDown.setDown(back);
            mc.options.keyLeft.setDown(strafeL);
            mc.options.keyRight.setDown(strafeR);
            mc.options.keyJump.setDown(fjump);
            mc.options.keySprint.setDown(fwd);
            if (fblock) mc.options.keyUse.setDown(true);
            else if (wasBlocking) mc.options.keyUse.setDown(false);
            wasBlocking = fblock;
        } else if (wasFighting) {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keySprint.setDown(false);
            if (wasBlocking) mc.options.keyUse.setDown(false);
            wasBlocking = false;
            wasFighting = false;
        }
    }

    private static boolean wasBlocking = false;
"""
assert old_pre in s
s = s.replace(old_pre, new_pre, 1)

# ---- tick(): fightActive must drop when something else happens
s = s.replace("        if (!EdithConfig.get().guard || !Glasses.equipped(me)) {\n            runLeft = 0;\n            return;\n        }",
              "        fightActive = false;\n        if (!EdithConfig.get().guard || !Glasses.equipped(me)) {\n            runLeft = 0;\n            return;\n        }", 1)

# ---- new fightMobs
a = s.index("    /** Defends against hostile mobs within reach.")
b = s.index("    private static boolean isFriend")
new_fight = '''    /**
     * Skilled fighting against hostile MOBS (never players): faces the target, closes in to the right range, strafes so
     * it misses, hits with critical strikes (jump, then attack while falling) once the weapon is charged, backs off when
     * too close, raises a shield against arrows, and runs from creepers.
     */
    private static void fightMobs(Minecraft mc) {
        if (!EdithConfig.get().guardFight || mc.gameMode == null) return;
        var me = mc.player;
        if (me.getHealth() <= 7f) return;
        Entity target = null;
        double best = 1e9;
        boolean shooter = false;
        for (Entity e : mc.level.getEntities(me, me.getBoundingBox().inflate(14))) {
            if (!(e instanceof Enemy) || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
            double d = me.distanceTo(e);
            if (e instanceof net.minecraft.world.entity.monster.RangedAttackMob && d > 4 && me.hasLineOfSight(e)) shooter = true;
            if (e.getType() == EntityTypes.CREEPER && d < 6) {
                Float yaw = pickDirection(mc, e.position());
                if (yaw != null) {
                    targetYaw = yaw;
                    runLeft = KEEP_RUNNING;
                    alert(mc, "SENTINEL: creeper - backing off", 0xFFFFC857);
                }
                return;
            }
            double score = d + le.getHealth() * 0.05;   // nearest first, weak ones slightly preferred
            if (d <= 5.0 && score < best) {
                best = score;
                target = e;
            }
        }
        boolean shield = me.getOffhandItem().is(Items.SHIELD);
        if (target == null) {
            // nobody close: only the shield for arrows
            if (shield && shooter) {
                fightActive = true;
                fwd = back = strafeL = strafeR = fjump = false;
                fblock = true;
            }
            return;
        }

        // best weapon in the hotbar
        int bestSlot = -1, bestScore = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack st = me.getInventory().getItem(i);
            int sc = st.is(Items.MACE) ? 5 : st.is(ItemTags.SWORDS) ? 4 : st.is(ItemTags.AXES) ? 3 : st.is(Items.TRIDENT) ? 2 : st.is(ItemTags.SPEARS) ? 2 : 0;
            if (sc > bestScore) {
                bestScore = sc;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0 && bestSlot != me.getInventory().getSelectedSlot()) me.getInventory().setSelectedSlot(bestSlot);

        // aim at the middle of the body, a little ahead of where it is moving
        Vec3 lead = target.getDeltaMovement().scale(2.0);
        Vec3 aim = target.getBoundingBox().getCenter().add(lead.x, 0, lead.z).subtract(me.getEyePosition());
        me.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        me.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))));

        double d = me.distanceTo(target);
        boolean ready = me.getAttackStrengthScale(0.5f) >= 0.95f;
        fightActive = true;
        fwd = d > 2.6;
        back = d < 1.5;
        if (--strafeTimer <= 0) {
            strafeL = !strafeL;
            strafeTimer = 10 + (int) (Math.random() * 8);
        }
        strafeR = !strafeL && d < 4.5;
        strafeL = strafeL && d < 4.5;
        fblock = shield && shooter && !ready;
        fjump = false;

        // critical hit: be in the air and falling when the swing lands
        if (ready && d <= 3.3) {
            boolean falling = !me.onGround() && me.getDeltaMovement().y < -0.03 && me.fallDistance > 0f;
            if (me.onGround() && critWait == 0) {
                fjump = true;
                critWait = 14;                       // give the jump time to peak, then fall back to a normal hit
            } else if (falling || critWait == 1) {
                mc.gameMode.attack(me, target);
                me.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                critWait = 0;
            }
        }
        if (critWait > 1) critWait--;
        else if (critWait == 1 && (!ready || me.onGround())) critWait = 0;
    }

'''
s = s[:a] + new_fight + s[b:]
open(p, "w", encoding="utf-8").write(s)
print("patched")
