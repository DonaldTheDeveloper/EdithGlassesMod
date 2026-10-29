package com.example.edithglasses.feature;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.Glasses;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Sentinel. While the glasses are on it keeps you running (sprint + hops) away from danger for as long as the danger is
 * there: a player holding a sword/axe/spear/trident/mace/wind charge, a mace diving from above, incoming wind charges,
 * arrows or tridents, and whoever just hurt you. With "fight" on it also defends against hostile mobs, and runs when
 * your health gets low. It never attacks players. Many servers treat automatic movement as cheating.
 */
public final class Guard {
    private static final int KEEP_RUNNING = 24;     // ticks to keep running after the last danger was seen

    private static int runLeft = 0;
    private static int sinceRepick = 99;
    private static int hopTimer = 0;
    private static float targetYaw;
    private static Vec3 lastThreat = Vec3.ZERO;
    private static long lastActivity = System.currentTimeMillis();
    private static float lastYaw, lastPitch;
    private static boolean running = false;
    private static long lastAlert = 0;
    private static int hurtGrace = 0;
    private static Player hurtBy;

    // mob fighting state (keys are applied in pre())
    private static boolean fightActive = false, fwd, back, strafeL, strafeR, fjump, fblock;
    private static boolean wasFighting = false;
    private static int strafeTimer = 0, critWait = 0;

    private Guard() {
    }

    public static boolean active() {
        var mc = Minecraft.getInstance();
        EdithConfig c = EdithConfig.get();
        if (!c.guard || !Glasses.equipped(mc.player)) return false;
        return c.guardAfkSeconds <= 0 || System.currentTimeMillis() - lastActivity > c.guardAfkSeconds * 1000L;
    }

    public static boolean enabled() {
        return EdithConfig.get().guard && Glasses.equipped();
    }

    /** Runs before the player's own tick so the keys are already pressed when the game reads your input. */
    public static void pre(Minecraft mc) {
        var me = mc.player;
        if (me == null) return;
        if (runLeft > 0 && mc.gui.screen() == null && !me.isPassenger()) {
            running = true;
            // turn straight to the escape direction (no slow turning while someone is hitting you)
            float yaw = me.getYRot();
            me.setYRot(yaw + Mth.clamp(Mth.wrapDegrees(targetYaw - yaw), -90f, 90f));
            mc.options.keyUp.setDown(true);
            mc.options.keyDown.setDown(false);
            mc.options.keySprint.setDown(true);
            // bunny hop: hop every ~10 ticks, jump over blocks, keep swimming up in water
            boolean jump = me.onGround() && (hopTimer++ % 10 == 0 || blockedAhead(mc)) || me.isInWater() || me.isInLava();
            mc.options.keyJump.setDown(jump);
        } else if (running) {
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

    // pro tactics
    private static int pearlCooldown = 0;
    private static int drinkLeft = 0, drinkPrev = -1, buffCooldown = 0;

    private static void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
        running = false;
    }

    public static void tick(Minecraft mc) {
        var me = mc.player;
        if (me == null || mc.level == null) return;

        // activity tracking (used for "only when away")
        if (!running) {
            boolean keys = mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown() || mc.options.keyJump.isDown();
            boolean looked = Math.abs(Mth.wrapDegrees(me.getYRot() - lastYaw)) > 0.5f || Math.abs(me.getXRot() - lastPitch) > 0.5f;
            if (keys || looked || mc.gui.screen() != null) lastActivity = System.currentTimeMillis();
        }
        lastYaw = me.getYRot();
        lastPitch = me.getXRot();

        fightActive = false;
        if (Duel.active() && Glasses.equipped(me) && mc.gui.screen() == null && !me.isPassenger()) {
            runLeft = 0;
            Duel.tick(mc);
            return;
        }
        if (!EdithConfig.get().guard || !Glasses.equipped(me)) {
            runLeft = 0;
            return;
        }
        if (mc.gui.screen() != null || me.isPassenger() || me.isSpectator()) {
            runLeft = 0;
            return;
        }
        if (!active()) return;

        // remember who just hit us: they count as danger for 6 seconds, whatever they hold
        if (me.hurtTime > 0 && me.getLastHurtByMob() instanceof Player p && p != me && !isFriend(p)) {
            hurtBy = p;
            hurtGrace = 120;
        }
        if (hurtGrace > 0) hurtGrace--;

        Threat t = findThreat(mc);
        if (t == null) {
            if (runLeft > 0) runLeft--;
            if (runLeft == 0) fightMobs(mc);
            return;
        }

        lastThreat = t.pos();
        boolean fresh = runLeft == 0;
        runLeft = KEEP_RUNNING;
        sinceRepick++;
        if (fresh || sinceRepick >= 6) {
            Float yaw = pickDirection(mc, t.pos());
            if (yaw != null) {
                targetYaw = yaw;
                sinceRepick = 0;
            } else if (fresh) {
                runLeft = 0;
                alert(mc, "SENTINEL: no safe way out - " + t.why(), 0xFFFF5050);
                return;
            }
        }
        if (pearlCooldown > 0) pearlCooldown--;
        if (me.getHealth() <= 6f && me.distanceToSqr(t.pos()) < 49) pearlEscape(mc, targetYaw);
        if (fresh) alert(mc, "SENTINEL: " + t.why() + " - running", 0xFFFFC857);
    }

    private record Threat(Vec3 pos, String why) {
    }

    private static void alert(Minecraft mc, String text, int color) {
        long now = System.currentTimeMillis();
        if (now - lastAlert < 2500) return;
        lastAlert = now;
        Alerts.push(mc, text, color, null);
    }

    private static Threat findThreat(Minecraft mc) {
        var me = mc.player;
        Vec3 pos = null;
        String why = null;
        double best = Double.MAX_VALUE;

        for (Player o : mc.level.players()) {
            if (o == me || o.isSpectator() || o.isDeadOrDying() || isFriend(o) || Duel.isOpponent(o)) continue;
            double d = o.distanceTo(me);
            double horiz = Math.hypot(o.getX() - me.getX(), o.getZ() - me.getZ());
            ItemStack held = o.getMainHandItem();
            String kind = null;
            if (held.is(Items.MACE) && (d <= 13 || (o.getY() > me.getY() + 1.5 && o.getDeltaMovement().y < -0.2 && horiz <= 10))) kind = "Mace";
            else if (held.is(Items.WIND_CHARGE) && d <= 11) kind = "Wind charge";
            else if ((held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES) || held.is(ItemTags.SPEARS) || held.is(Items.TRIDENT)) && d <= 10) {
                kind = held.getHoverName().getString();
            } else if ((held.is(Items.BOW) || held.is(Items.CROSSBOW)) && d <= 20 && o.isUsingItem()) kind = "Bow";
            else if (o == hurtBy && hurtGrace > 0 && d <= 16) kind = "attacking you";
            if (kind != null && d < best) {
                best = d;
                pos = o.position();
                why = o.getName().getString() + " (" + kind + ") " + Math.round(d) + " m";
            }
        }
        for (Entity e : mc.level.getEntities(me, me.getBoundingBox().inflate(14))) {
            var type = e.getType();
            boolean charge = type == EntityTypes.WIND_CHARGE || type == EntityTypes.BREEZE_WIND_CHARGE;
            boolean arrow = type == EntityTypes.ARROW || type == EntityTypes.SPECTRAL_ARROW || type == EntityTypes.TRIDENT;
            if (!charge && !arrow) continue;
            Vec3 toMe = me.position().add(0, 1, 0).subtract(e.position());
            if (e.getDeltaMovement().dot(toMe) <= 0 || e.getDeltaMovement().lengthSqr() < 0.01) continue;
            double d = e.distanceTo(me);
            if (d < best) {
                best = d;
                pos = e.position();
                why = (charge ? "Incoming wind charge " : "Incoming projectile ") + Math.round(d) + " m";
            }
        }
        // low health while fighting mobs: back off
        if (pos == null && EdithConfig.get().guardFight && me.getHealth() <= 7f) {
            for (Entity e : mc.level.getEntities(me, me.getBoundingBox().inflate(8))) {
                if (!(e instanceof Enemy) || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
                double d = me.distanceTo(e);
                if (d < best) {
                    best = d;
                    pos = e.position();
                    why = "Low health - " + e.getName().getString();
                }
            }
        }
        return pos == null ? null : new Threat(pos, why);
    }

    /**
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

        engage(mc, target, shooter, shield);
    }

    /** The fighting skills: weapon choice, aim, range, strafing, critical hits, shield. Package-private so Duel can use it. */
    static void engage(Minecraft mc, Entity target, boolean shooter, boolean shield) {
        var me = mc.player;
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
        fblock = shield && (shooter || d < 3.4) && !ready;
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

    /** Hotbar slot of a drinkable potion that gives strength, speed or resistance we do not have yet. */
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

    private static boolean isFriend(Player o) {
        String f = EdithConfig.get().guardFriends;
        if (f == null || f.isBlank()) return false;
        for (String n : f.split(",")) if (n.trim().equalsIgnoreCase(o.getName().getString())) return true;
        return false;
    }

    /** Best safe running direction (yaw) away from the threat, or null if every direction is dangerous. */
    private static Float pickDirection(Minecraft mc, Vec3 threat) {
        var me = mc.player;
        double ax = me.getX() - threat.x, az = me.getZ() - threat.z;
        double len = Math.hypot(ax, az);
        if (len < 1e-3) {
            ax = -Math.sin(Math.toRadians(me.getYRot()));
            az = Math.cos(Math.toRadians(me.getYRot()));
            len = 1;
        }
        ax /= len;
        az /= len;
        Float best = null;
        double bestScore = -1e9;
        double curX = -Math.sin(Math.toRadians(targetYaw)), curZ = Math.cos(Math.toRadians(targetYaw));
        for (int a = -135; a <= 180; a += 45) {
            double c = Math.cos(Math.toRadians(a)), s = Math.sin(Math.toRadians(a));
            double dx = ax * c - az * s, dz = ax * s + az * c;
            double free = freeLength(mc, dx, dz);
            if (free < 3) continue;
            // away from the threat, lots of free road, and do not zig-zag
            double score = (dx * ax + dz * az) * 2.0 + Math.min(free, 10) * 0.3 + (running ? (dx * curX + dz * curZ) * 0.6 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = (float) Math.toDegrees(Math.atan2(-dx, dz));
            }
        }
        return best;
    }

    /** How many blocks you can run that way without a wall, a drop or something dangerous (max 10). */
    private static double freeLength(Minecraft mc, double dx, double dz) {
        var me = mc.player;
        for (int i = 1; i <= 10; i++) {
            BlockPos p = BlockPos.containing(me.getX() + dx * i, me.getY(), me.getZ() + dz * i);
            if (!passable(mc, p) || !passable(mc, p.above())) {
                // one step up is fine
                if (passable(mc, p.above()) && passable(mc, p.above(2)) && solid(mc, p)) continue;
                return i - 1;
            }
            int drop = 0;
            BlockPos g = p.below();
            while (drop < 4 && !solid(mc, g)) {
                g = g.below();
                drop++;
            }
            if (drop >= 3 || hazard(mc, g) || hazard(mc, p)) return i - 1;
        }
        return 10;
    }

    private static boolean passable(Minecraft mc, BlockPos p) {
        BlockState s = mc.level.getBlockState(p);
        return !s.blocksMotion() && !hazard(mc, p);
    }

    private static boolean solid(Minecraft mc, BlockPos p) {
        return mc.level.getBlockState(p).blocksMotion();
    }

    private static boolean hazard(Minecraft mc, BlockPos p) {
        BlockState s = mc.level.getBlockState(p);
        return s.is(Blocks.LAVA) || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS)
            || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE);
    }

    private static boolean blockedAhead(Minecraft mc) {
        var me = mc.player;
        double yaw = Math.toRadians(me.getYRot());
        BlockPos ahead = BlockPos.containing(me.getX() - Math.sin(yaw) * 0.9, me.getY() + 0.1, me.getZ() + Math.cos(yaw) * 0.9);
        return solid(mc, ahead);
    }

    public static String describe() {
        EdithConfig c = EdithConfig.get();
        return "Sentinel " + (c.guard ? "ON" : "OFF") + (c.guardAfkSeconds > 0 ? ", only after " + c.guardAfkSeconds + " s without input" : ", always while the glasses are on")
            + (c.guardFight ? ", fights hostile mobs" : "")
            + (c.guardFriends == null || c.guardFriends.isBlank() ? "" : ", friends: " + c.guardFriends.toLowerCase(Locale.ROOT));
    }
}
