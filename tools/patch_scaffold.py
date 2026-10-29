import os
p = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/feature/Builder.java")
s = open(p, encoding="utf-8").read()


def rep(a, b):
    global s
    assert a in s, "missing: " + a[:90]
    s = s.replace(a, b, 1)


# ---- state
rep("    private static boolean steer = false, jumpNow = false, keysHeld = false;",
    "    private static boolean steer = false, jumpNow = false, keysHeld = false, jumpHold = false;\n"
    "    // scaffold pillar: EDITH stands on a temporary stack of blocks to reach high places, then takes it down again\n"
    "    private static int elev = 0, upLeft = 0, pendingElev = 0, downWait = 0;\n"
    "    private static BlockPos padPos = BlockPos.ZERO;\n"
    "    private static double pillarBaseY;\n"
    "    private static boolean goingDown = false, destroying = false;\n"
    "    private static Item scaffoldItem;")

# ---- pre(): jump-only mode for pillar jumping
rep('''        if (mode == Mode.BUILDING && steer && mc.gui.screen() == null) {
            me.setYRot(wantYaw);
            me.setXRot(Mth.clamp(me.getXRot(), -10f, 30f));
            mc.options.keyUp.setDown(true);
            mc.options.keySprint.setDown(false);
            mc.options.keyJump.setDown(jumpNow);
            keysHeld = true;
        } else if (keysHeld) {''', '''        if (mode == Mode.BUILDING && steer && mc.gui.screen() == null) {
            me.setYRot(wantYaw);
            me.setXRot(Mth.clamp(me.getXRot(), -10f, 30f));
            mc.options.keyUp.setDown(true);
            mc.options.keySprint.setDown(false);
            mc.options.keyJump.setDown(jumpNow);
            keysHeld = true;
        } else if (mode == Mode.BUILDING && jumpHold && mc.gui.screen() == null) {
            mc.options.keyUp.setDown(false);
            mc.options.keyJump.setDown(true);
            keysHeld = true;
        } else if (keysHeld) {''')

# ---- start: reset scaffold state
rep("        buildTicks = 0;\n        path.clear();\n        swaps.clear();",
    "        buildTicks = 0;\n        elev = 0;\n        upLeft = 0;\n        pendingElev = 0;\n        goingDown = false;\n        destroying = false;\n        path.clear();\n        swaps.clear();")

# ---- tick(): scaffold phases before the walking code
rep('''        steer = false;
        jumpNow = false;

        if (index >= queue.size()) {''', '''        steer = false;
        jumpNow = false;
        jumpHold = false;

        // taking the scaffold down / putting it up has priority over everything else
        if (goingDown || (index >= queue.size() && elev > 0)) {
            goingDown = true;
            if (tearDown(mc)) goingDown = false;
            return;
        }
        if (upLeft > 0) {
            pillarUp(mc);
            return;
        }

        if (index >= queue.size()) {''')

# ---- tick(): when elevated and the current target is out of reach, come down
rep('''        double reach = creative ? 4.9 : 4.4;
        Option o = findOption(mc, t.pos(), me.getEyePosition(), reach);
        if (o != null && !me.getBoundingBox().inflate(0.02).intersects(new net.minecraft.world.phys.AABB(t.pos()))) {
            place(mc, o);
            retries++;
            cooldown = creative ? 1 : 3;
            return;
        }
        if (!planWalk(mc, t.pos(), reach)) skipTarget(mc, "no way to reach it");''', '''        double reach = creative ? 4.9 : 4.4;
        Option o = findOption(mc, t.pos(), me.getEyePosition(), reach);
        if (o != null && !me.getBoundingBox().inflate(0.02).intersects(new net.minecraft.world.phys.AABB(t.pos()))) {
            place(mc, o);
            retries++;
            cooldown = creative ? 1 : 3;
            return;
        }
        if (elev > 0) {                 // cannot reach it from up here: come down first, then look for a new spot
            goingDown = true;
            return;
        }
        if (!planWalk(mc, t.pos(), reach)) skipTarget(mc, "no way to reach it");''')

# ---- when a walk ends and a pillar is wanted
rep('''            if (dist < (last ? 0.22 : 0.45) && Math.abs(next.y - me.getY()) < 1.3) {
                path.remove(0);
            } else {''', '''            if (dist < (last ? 0.22 : 0.45) && Math.abs(next.y - me.getY()) < 1.3) {
                path.remove(0);
                if (path.isEmpty() && pendingElev > 0) {
                    upLeft = pendingElev;
                    pendingElev = 0;
                    padPos = BlockPos.containing(me.getX(), me.getY() - 0.5, me.getZ());
                    pillarBaseY = me.getY();
                    scaffoldItem = items.get(Role.WALL) != null ? items.get(Role.WALL) : items.get(Role.FLOOR);
                }
            } else {''')

# ---- planWalk: also look for spots where a pillar of height 1..3 would make the block reachable
old_start = s.index("        // the closest standing spot (by walking order) from which the block can be placed")
old_end = s.index("    // ------------------------------------------------------------------ preview follows the crosshair")
new_block = '''        // the closest standing spot (by walking order) from which the block can be placed, standing on the ground or on a pillar
        for (int h = 0; h <= 3; h++) {
            if (h > 0 && (scaffoldFor() == null)) break;
            for (BlockPos c : order) {
                Vec3 eye = new Vec3(c.getX() + 0.5, c.getY() + 1.62 + h, c.getZ() + 0.5);
                if (eye.distanceTo(Vec3.atCenterOf(target)) > reach + 0.9) continue;
                if (c.equals(target)) continue;
                boolean clear = true;
                for (int k = 0; k <= h + 1; k++) if (c.above(k).equals(target)) clear = false;
                for (int k = 1; k <= h + 1 && clear; k++) if (blocking(mc, c.above(k))) clear = false;
                if (!clear) continue;
                if (findOption(mc, target, eye, reach) == null) continue;
                List<Vec3> route = new ArrayList<>();
                for (BlockPos q = c; q != null && !q.equals(start); q = from.get(q)) route.add(0, new Vec3(q.getX() + 0.5, q.getY(), q.getZ() + 0.5));
                if (route.isEmpty() && h == 0) continue;       // already here but not placeable
                path.clear();
                path.addAll(route);
                pendingElev = h;
                if (route.isEmpty() && h > 0) {                // already standing at the pillar spot
                    upLeft = h;
                    pendingElev = 0;
                    padPos = BlockPos.containing(me.getX(), me.getY() - 0.5, me.getZ());
                    pillarBaseY = me.getY();
                    scaffoldItem = scaffoldFor();
                }
                return true;
            }
        }
        return false;
    }

    private static Item scaffoldFor() {
        return items.get(Role.WALL) != null ? items.get(Role.WALL) : items.get(Role.FLOOR);
    }

    /** One step of building the pillar: jump, and at the top of the jump put a block under yourself. */
    private static void pillarUp(Minecraft mc) {
        var me = mc.player;
        if (scaffoldItem == null) scaffoldItem = scaffoldFor();
        if (scaffoldItem == null || (!creative && inventoryCounts(mc).getOrDefault(scaffoldItem, 0) <= 0)) {
            upLeft = 0;
            elev = Math.max(elev, 0);
            skipTarget(mc, "no blocks for a scaffold");
            return;
        }
        if (!ensureHeld(mc, scaffoldItem)) return;
        me.setXRot(90f);
        double restY = pillarBaseY + elev;
        if (me.onGround() && Math.abs(me.getY() - restY) < 0.2) {
            jumpHold = true;                                   // jump
        } else if (me.getY() >= restY + 1.05 && me.getDeltaMovement().y < 0.42) {
            BlockPos support = padPos.above(elev);
            Vec3 point = new Vec3(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
            mc.gameMode.useItemOn(me, InteractionHand.MAIN_HAND, new BlockHitResult(point, Direction.UP, support, false));
            me.swing(InteractionHand.MAIN_HAND);
            elev++;
            upLeft--;
            if (upLeft <= 0) cooldown = 3;
        } else if (!me.onGround()) {
            jumpHold = true;
        }
    }

    /** Takes the pillar down from the top: look down and break the block under your feet. Returns true when it is gone. */
    private static boolean tearDown(Minecraft mc) {
        var me = mc.player;
        if (elev <= 0) {
            destroying = false;
            return true;
        }
        me.setXRot(90f);
        BlockPos top = padPos.above(elev);
        BlockState st = mc.level.getBlockState(top);
        if (st.isAir()) {
            elev--;
            destroying = false;
            return elev <= 0;
        }
        if (!me.onGround()) return false;
        if (!destroying) {
            mc.gameMode.startDestroyBlock(top, Direction.UP);
            destroying = true;
        } else {
            mc.gameMode.continueDestroyBlock(top, Direction.UP);
        }
        me.swing(InteractionHand.MAIN_HAND);
        if (++downWait > 400) {
            downWait = 0;
            elev = 0;
            return true;
        }
        return false;
    }

'''
s = s[:old_start] + new_block + s[old_end:]
# the old planWalk body ended with "return false;\n    }\n\n" right before old_end: make sure no duplicate close
open(p, "w", encoding="utf-8").write(s)
print("patched")
