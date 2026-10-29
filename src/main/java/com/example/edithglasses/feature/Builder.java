package com.example.edithglasses.feature;

import com.example.edithglasses.feature.Blueprint.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.example.edithglasses.feature.HudStyle.*;

/**
 * House builder. Preview: a holographic copy of the plan follows your crosshair. Confirm: EDITH takes over your character,
 * walks around the site and places the blocks one by one like a player would (same reach, same click on a block face).
 * In survival it uses blocks from your inventory (and tells you what is missing), in creative it takes whatever blocks
 * the plan needs from the creative inventory. Only works on a clear, fairly flat spot. Many servers ban automatic building.
 */
public final class Builder {
    public enum Mode { IDLE, PREVIEW, BUILDING }

    private record Target(Blueprint.Cell cell, BlockPos pos) {
    }

    private record Option(BlockPos neighbor, Direction face, Vec3 point) {
    }

    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "cherry", "mangrove"};
    private static final String[] INTERACTIVE = {"chest", "barrel", "furnace", "crafting_table", "door", "trapdoor", "button", "lever", "bed", "anvil",
        "shulker", "hopper", "dispenser", "dropper", "note_block", "cauldron", "loom", "smoker", "enchanting", "brewing", "lectern", "sign", "gate"};

    private static Mode mode = Mode.IDLE;
    private static Blueprint plan;
    private static String wood = "", wall = "";
    private static int rot = 0;
    private static BlockPos origin = BlockPos.ZERO;
    private static BlockPos lastCheckedOrigin = null;
    private static int lastCheckedRot = -1;
    private static boolean siteOk = false;
    private static String siteMsg = "";
    private static boolean warned = false;

    private static final Map<Role, Item> items = new EnumMap<>(Role.class);
    private static final List<Target> queue = new ArrayList<>();
    private static int index, placed, skipped, retries, cooldown, settle, stuck, buildTicks;
    private static final List<Vec3> path = new ArrayList<>();
    private static Vec3 lastPos = Vec3.ZERO;
    private static boolean creative;
    private static int prevSlot = -1;
    private static ItemStack savedSlot8 = ItemStack.EMPTY;
    private static boolean savedCaptured = false;
    private static final List<Integer> swaps = new ArrayList<>();
    private static final List<String> notes = new ArrayList<>();
    private static final Map<BlockPos, Integer> defers = new HashMap<>();

    private static boolean steer = false, jumpNow = false, keysHeld = false, jumpHold = false;
    // scaffold pillar: EDITH stands on a temporary stack of blocks to reach high places, then takes it down again
    private static int elev = 0, upLeft = 0, pendingElev = 0, downWait = 0;
    private static BlockPos padPos = BlockPos.ZERO;
    private static double pillarBaseY;
    private static boolean goingDown = false, destroying = false;
    private static Item scaffoldItem;
    private static boolean savedMayfly = false;
    private static float wantYaw;

    private Builder() {
    }

    public static boolean previewing() {
        return mode == Mode.PREVIEW;
    }

    public static boolean active() {
        return mode == Mode.BUILDING;
    }

    public static boolean busy() {
        return mode != Mode.IDLE;
    }

    // ------------------------------------------------------------------ messages

    private static void say(Minecraft mc, String text) {
        mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(Component.literal("EDITH: " + text).withStyle(ChatFormatting.AQUA)));
    }

    // ------------------------------------------------------------------ preview

    /** Starts a hologram of a house that follows your crosshair. Words after the size pick the wood and wall (oak, spruce, brick, stone...). */
    public static String preview(Minecraft mc, String size, String palette) {
        Blueprint b = Blueprint.byName(size == null ? "" : size);
        if (b == null) return "Pick a house: starter, normal or luxury.";
        if (mc.player == null || mc.level == null) return "Not in a world.";
        if (mode == Mode.BUILDING) return "I am already building. Stop it first with N.";
        plan = b;
        wood = "";
        wall = "";
        if (palette != null) {
            for (String t : palette.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
                String w = t.replace("dark", "dark_oak").replace("dark_oak_oak", "dark_oak");
                for (String wd : WOODS) if (wd.equals(w)) wood = wd;
                if (t.startsWith("brick")) wall = "bricks";
                else if (t.equals("stone")) wall = "stone_bricks";
                else if (t.startsWith("cobble")) wall = "cobblestone";
                else if (t.startsWith("sand")) wall = "sandstone";
                else if (t.startsWith("plank") || t.equals("wood")) wall = "planks";
            }
        }
        var facing = Direction.fromYRot(mc.player.getYRot());
        rot = switch (facing.getOpposite()) {       // the door faces you
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
        lastCheckedOrigin = null;
        mode = Mode.PREVIEW;
        origin = mc.player.blockPosition().relative(facing, 6);
        return b.label + " hologram ready. Look where it should stand, then press Y to build (N cancels, /edith build rotate turns it).";
    }

    public static String rotate() {
        if (mode != Mode.PREVIEW) return "Start a preview first: /edith build starter";
        rot = (rot + 1) & 3;
        lastCheckedOrigin = null;
        return "Turned.";
    }

    private static BlockPos worldPos(Blueprint.Cell c) {
        int[] r = plan.rotate(c.x(), c.z(), rot);
        return origin.offset(r[0], c.y(), r[1]);
    }

    private static boolean interactive(BlockState st) {
        String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
        for (String s : INTERACTIVE) if (id.contains(s)) return true;
        return false;
    }

    private static boolean blocking(Minecraft mc, BlockPos p) {
        BlockState st = mc.level.getBlockState(p);
        if (!st.getFluidState().isEmpty()) return true;
        var shape = st.getCollisionShape(mc.level, p);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) > 0.1;
    }

    private static boolean supports(Minecraft mc, BlockPos p) {
        BlockState st = mc.level.getBlockState(p);
        if (!st.getFluidState().isEmpty()) return false;
        var shape = st.getCollisionShape(mc.level, p);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.5;
    }

    private static boolean standable(Minecraft mc, BlockPos feet) {
        return supports(mc, feet.below()) && !blocking(mc, feet) && !blocking(mc, feet.above());
    }

    /** Is the spot clear and (mostly) solid underneath? Fills siteOk / siteMsg. */
    private static void checkSite(Minecraft mc) {
        Set<BlockPos> planned = new HashSet<>();
        int obstruct = 0, floor = 0, unsupported = 0, maxY = 0;
        for (Blueprint.Cell c : plan.cells) {
            BlockPos p = worldPos(c);
            planned.add(p);
            maxY = Math.max(maxY, c.y());
            BlockState st = mc.level.getBlockState(p);
            if (!(st.isAir() || st.canBeReplaced()) || !st.getFluidState().isEmpty()) obstruct++;
            if (c.role() == Role.FLOOR) {
                floor++;
                if (!supports(mc, p.below())) unsupported++;
            }
        }
        for (int x = 0; x < plan.width(rot); x++) {
            for (int z = 0; z < plan.depth(rot); z++) {
                for (int y = 1; y <= maxY; y++) {
                    BlockPos p = origin.offset(x, y, z);
                    if (planned.contains(p)) continue;
                    BlockState st = mc.level.getBlockState(p);
                    if (!(st.isAir() || st.canBeReplaced()) || !st.getFluidState().isEmpty()) obstruct++;
                }
            }
        }
        siteOk = obstruct == 0 && unsupported <= floor * 0.4;
        siteMsg = obstruct > 0 ? obstruct + " blocks are in the way (clear them or move)"
            : unsupported > floor * 0.4 ? "ground is too uneven or missing" : "site is clear";
    }

    // ------------------------------------------------------------------ materials

    private static Item itemOf(String id) {
        Item it = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(id)).orElse(null);
        return it == null || it == net.minecraft.world.item.Items.AIR ? null : it;
    }

    private static String idFor(Role r) {
        String wd = wood.isEmpty() ? switch (plan.id) { case "starter" -> "oak"; case "normal" -> "spruce"; default -> "dark_oak"; } : wood;
        String wl = wall.isEmpty() ? switch (plan.id) { case "starter" -> "planks"; case "normal" -> "cobblestone"; default -> "stone_bricks"; } : wall;
        if (wl.equals("planks")) wl = wd + "_planks";
        boolean lux = plan.id.equals("luxury");
        return switch (r) {
            case FLOOR, ROOF -> wd + "_planks";
            case PILLAR -> wd + "_log";
            case WALL -> wl;
            case GLASS -> "glass";
            case TABLE -> "crafting_table";
            case FURNACE -> "furnace";
            case CHEST -> "chest";
            case CARPET -> lux ? "red_carpet" : "white_carpet";
            case LIGHT -> lux ? "lantern" : "torch";
            case DOOR -> wd + "_door";
        };
    }

    private static boolean structural(Role r) {
        return r == Role.FLOOR || r == Role.WALL || r == Role.ROOF || r == Role.PILLAR;
    }

    private static boolean buildBlock(Item it) {
        String id = BuiltInRegistries.ITEM.getKey(it).getPath();
        return id.matches(".*_planks|.*_log|cobblestone|stone|stone_bricks|bricks|sandstone|smooth_stone|andesite|diorite|granite|deepslate.*|cobbled_deepslate"
            + "|dirt|netherrack|.*terracotta|blackstone.*|.*_wool|mud_bricks|prismarine.*|end_stone.*|purpur.*|quartz_block|.*_concrete");
    }

    private static String nameOf(Item it) {
        return new ItemStack(it).getHoverName().getString();
    }

    private static Map<Item, Integer> inventoryCounts(Minecraft mc) {
        Map<Item, Integer> m = new HashMap<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) m.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return m;
    }

    /** Decides which item builds which part. Returns null when everything needed is there, else what is missing. */
    private static String resolveMaterials(Minecraft mc) {
        items.clear();
        Map<Role, Integer> need = plan.counts();
        if (creative) {
            for (Role r : need.keySet()) {
                Item it = itemOf(idFor(r));
                if (it != null) items.put(r, it);
            }
            return null;
        }
        Map<Item, Integer> left = new HashMap<>(inventoryCounts(mc));
        List<String> problems = new ArrayList<>();
        Role[] order = {Role.FLOOR, Role.WALL, Role.ROOF, Role.PILLAR, Role.GLASS, Role.DOOR, Role.TABLE, Role.FURNACE, Role.CHEST, Role.CARPET, Role.LIGHT};
        for (Role r : order) {
            int n = need.getOrDefault(r, 0);
            if (n == 0) continue;
            Item pref = itemOf(idFor(r));
            Item pick = null;
            if (pref != null && left.getOrDefault(pref, 0) >= n) pick = pref;
            if (pick == null && structural(r)) {
                int bestCount = Integer.MAX_VALUE;
                for (Map.Entry<Item, Integer> e : left.entrySet()) {
                    if (buildBlock(e.getKey()) && e.getValue() >= n && e.getValue() < bestCount) {
                        pick = e.getKey();
                        bestCount = e.getValue();
                    }
                }
                if (pick == null) {
                    int most = 0;
                    for (Map.Entry<Item, Integer> e : left.entrySet()) if (buildBlock(e.getKey())) most = Math.max(most, e.getValue());
                    problems.add((n - most) + " more building blocks for the " + r.name().toLowerCase(Locale.ROOT) + " (need " + n + ", best stack " + most + ")");
                }
            }
            if (pick == null && r == Role.GLASS && items.get(Role.WALL) != null) pick = items.get(Role.WALL);
            if (pick == null && !structural(r) && pref != null && left.getOrDefault(pref, 0) > 0) pick = pref;   // partial furniture
            if (pick != null) {
                left.merge(pick, -n, Integer::sum);
                items.put(r, pick);
            } else if (!structural(r)) {
                notes.add("no " + (pref == null ? r.name().toLowerCase(Locale.ROOT) : nameOf(pref)) + ", skipped");
            }
        }
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    // ------------------------------------------------------------------ start / stop

    public static String confirm(Minecraft mc) {
        if (mode != Mode.PREVIEW) return "Nothing to build. Start with /edith build starter, normal or luxury.";
        var me = mc.player;
        if (me == null || mc.level == null || mc.gameMode == null) return "Not in a world.";
        checkSite(mc);
        if (!siteOk) {
            say(mc, "I can't build here: " + siteMsg + ".");
            return "Blocked: " + siteMsg;
        }
        if (me.getAbilities().flying) {
            me.getAbilities().flying = false;
            me.onUpdateAbilities();
            return "I switched flying off. Wait until you have landed, then press Y again.";
        }
        if (!me.onGround()) return "Stand on the ground first, then press Y again.";
        creative = me.isCreative();
        savedMayfly = me.getAbilities().mayfly;
        if (creative) me.getAbilities().mayfly = false;       // jumping to build the scaffold must not toggle creative flight
        notes.clear();
        String missing = resolveMaterials(mc);
        if (missing != null) {
            say(mc, "I'm missing materials: " + missing + ".");
            return "Missing: " + missing;
        }
        queue.clear();
        defers.clear();
        for (Blueprint.Cell c : plan.cells) queue.add(new Target(c, worldPos(c)));
        index = 0;
        placed = 0;
        skipped = 0;
        retries = 0;
        cooldown = 0;
        settle = 0;
        stuck = 0;
        buildTicks = 0;
        elev = 0;
        upLeft = 0;
        pendingElev = 0;
        goingDown = false;
        destroying = false;
        path.clear();
        swaps.clear();
        savedCaptured = false;
        prevSlot = me.getInventory().getSelectedSlot();
        lastPos = me.position();
        mode = Mode.BUILDING;
        String warn = warned ? "" : " (Automatic building counts as cheating on many servers. Use it in single player or on your own server.)";
        warned = true;
        say(mc, "Building the " + plan.label.toLowerCase(Locale.ROOT) + ": " + queue.size() + " blocks. Press N to stop." + warn);
        return "Building.";
    }

    public static void cancel(Minecraft mc, String why) {
        if (mode == Mode.IDLE) return;
        if (System.getProperty("edith.dbg") != null) System.out.println("BUILDER cancel: " + why + " placed=" + placed + "/" + queue.size() + " skipped=" + skipped);
        boolean wasBuilding = mode == Mode.BUILDING;
        if (wasBuilding) {
            finish(mc);
            say(mc, "Building stopped: " + why + " (" + placed + " of " + queue.size() + " blocks placed).");
        } else {
            say(mc, "Hologram cancelled.");
        }
        mode = Mode.IDLE;
    }

    private static void finish(Minecraft mc) {
        releaseKeys(mc);
        var me = mc.player;
        if (me != null && creative) me.getAbilities().mayfly = savedMayfly;
        if (me != null && mc.gameMode != null) {
            try {
                if (creative && savedCaptured) {
                    me.getInventory().setItem(8, savedSlot8);
                    mc.gameMode.handleCreativeModeItemAdd(savedSlot8, 36 + 8);
                }
                for (int i = swaps.size() - 1; i >= 0; i--) {
                    mc.gameMode.handleContainerInput(me.inventoryMenu.containerId, swaps.get(i), 8, ContainerInput.SWAP, me);
                }
                if (prevSlot >= 0) me.getInventory().setSelectedSlot(prevSlot);
            } catch (Exception ignored) {
            }
        }
        swaps.clear();
        path.clear();
    }

    private static void releaseKeys(Minecraft mc) {
        if (!keysHeld) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
        keysHeld = false;
        steer = false;
    }

    // ------------------------------------------------------------------ per tick

    /** Before the player's own tick: the keys EDITH wants pressed. */
    public static void pre(Minecraft mc) {
        var me = mc.player;
        if (me == null) return;
        if (mode == Mode.BUILDING && steer && mc.gui.screen() == null) {
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
        } else if (keysHeld) {
            releaseKeys(mc);
        }
    }

    public static void tick(Minecraft mc) {
        var me = mc.player;
        if (me == null || mc.level == null) {
            mode = Mode.IDLE;
            return;
        }
        if (mode == Mode.PREVIEW) {
            updatePreview(mc);
            return;
        }
        if (mode != Mode.BUILDING) return;

        if (mc.gui.screen() != null && System.getProperty("edith.dbg") == null) {
            if (System.getProperty("edith.dbg") != null) System.out.println("BUILDER screen=" + mc.gui.screen().getClass().getName());
            cancel(mc, "a menu opened");
            return;
        }
        if (me.isDeadOrDying()) { cancel(mc, "you died"); return; }
        if (me.hurtTime > 0) { cancel(mc, "you were hurt"); return; }
        if (me.getAbilities().flying) { cancel(mc, "you are flying, land first"); return; }
        if (++buildTicks > 20 * 60 * 20) { cancel(mc, "it took too long"); return; }
        Vec3 centre = Vec3.atCenterOf(origin).add(plan.width(rot) / 2.0, 0, plan.depth(rot) / 2.0);
        if (me.position().distanceTo(centre) > 45) { cancel(mc, "you walked too far away"); return; }

        steer = false;
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

        if (index >= queue.size()) {
            finish(mc);
            mode = Mode.IDLE;
            if (System.getProperty("edith.dbg") != null) System.out.println("BUILDER done: placed=" + placed + " skipped=" + skipped + " notes=" + notes);
            String extra = skipped > 0 ? " " + skipped + " blocks could not be placed." : "";
            say(mc, "Done. " + plan.label + " finished, " + placed + " blocks placed." + extra + (notes.isEmpty() ? "" : " Note: " + String.join(", ", notes) + "."));
            Alerts.push(mc, plan.label.toUpperCase(Locale.ROOT) + " COMPLETE", 0xFF6FFFA0, null);
            return;
        }

        // walking
        if (!path.isEmpty()) {
            Vec3 next = path.get(0);
            double dx = next.x - me.getX(), dz = next.z - me.getZ();
            double dist = Math.hypot(dx, dz);
            boolean last = path.size() == 1;
            if (dist < (last ? 0.22 : 0.45) && Math.abs(next.y - me.getY()) < 1.3) {
                path.remove(0);
                if (path.isEmpty() && pendingElev > 0) {
                    upLeft = pendingElev;
                    pendingElev = 0;
                    padPos = BlockPos.containing(me.getX(), me.getY() - 0.5, me.getZ());
                    pillarBaseY = me.getY();
                    scaffoldItem = items.get(Role.WALL) != null ? items.get(Role.WALL) : items.get(Role.FLOOR);
                }
            } else {
                wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                steer = true;
                jumpNow = me.onGround() && (next.y > me.getY() + 0.4 || blockedAhead(mc, me, wantYaw));
            }
            if (me.position().distanceTo(lastPos) < 0.02) stuck++; else stuck = 0;
            lastPos = me.position();
            if (stuck > 40) {
                path.clear();
                stuck = 0;
                skipTarget(mc, "got stuck walking");
            }
            return;
        }
        if (me.getDeltaMovement().horizontalDistanceSqr() > 0.0009 && settle++ < 8) return;
        settle = 0;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        Target t = queue.get(index);
        BlockState st = mc.level.getBlockState(t.pos());
        if (!(st.isAir() || st.canBeReplaced()) && st.getFluidState().isEmpty()) {     // block is there
            placed++;
            index++;
            retries = 0;
            return;
        }
        Item item = items.get(t.cell().role());
        if (item == null) { skipTarget(mc, null); return; }
        if (!creative && inventoryCounts(mc).getOrDefault(item, 0) <= 0) {
            notes.add("ran out of " + nameOf(item));
            skipTarget(mc, null);
            return;
        }
        if (!ensureHeld(mc, item)) return;
        if (retries > 4) { skipTarget(mc, "placing failed"); return; }

        double reach = creative ? 4.9 : 4.4;
        Option o = findOption(mc, t.pos(), me.getEyePosition(), reach);
        if (o != null && !me.getBoundingBox().inflate(0.02).intersects(new net.minecraft.world.phys.AABB(t.pos()))) {
            place(mc, o);
            retries++;
            cooldown = creative ? 1 : 3;
            return;
        }
        if (reorderReachable(mc, reach)) return;      // something else is within reach from here: do that first
        if (elev > 0) {                 // cannot reach it from up here: come down first, then look for a new spot
            goingDown = true;
            return;
        }
        if (!planWalk(mc, t.pos(), reach)) {
            // maybe it hangs on a block that is still to come: try it again later, only give up after a few rounds
            int n = defers.merge(t.pos(), 1, Integer::sum);
            if (n <= 6 && index < queue.size() - 1) {
                queue.remove(index);
                queue.add(t);
            } else {
                skipTarget(mc, "no way to reach it");
            }
        }
    }

    /** Looks a little way ahead in the queue for a block that can be placed from where EDITH stands right now and moves it to the front. */
    private static boolean reorderReachable(Minecraft mc, double reach) {
        var me = mc.player;
        Vec3 eye = me.getEyePosition();
        int end = Math.min(queue.size(), index + 160);
        for (int j = index + 1; j < end; j++) {
            Target c = queue.get(j);
            BlockState st = mc.level.getBlockState(c.pos());
            if (!(st.isAir() || st.canBeReplaced())) continue;
            if (c.cell().role() == Role.DOOR && j < queue.size() - 3) continue;
            if (eye.distanceTo(Vec3.atCenterOf(c.pos())) > reach + 0.9) continue;
            if (findOption(mc, c.pos(), eye, reach) == null) continue;
            if (me.getBoundingBox().inflate(0.02).intersects(new net.minecraft.world.phys.AABB(c.pos()))) continue;
            java.util.Collections.swap(queue, index, j);
            return true;
        }
        return false;
    }

    private static void skipTarget(Minecraft mc, String why) {
        if (System.getProperty("edith.dbg") != null && index < queue.size()) System.out.println("BUILDER skip " + queue.get(index).cell().role() + " " + queue.get(index).pos() + " " + why + " elev=" + elev);
        skipped++;
        index++;
        retries = 0;
        if (why != null && !notes.contains(why) && notes.size() < 4) notes.add(why);
    }

    private static boolean blockedAhead(Minecraft mc, net.minecraft.client.player.LocalPlayer me, float yaw) {
        double r = Math.toRadians(yaw);
        BlockPos ahead = BlockPos.containing(me.getX() - Math.sin(r) * 0.8, me.getY(), me.getZ() + Math.cos(r) * 0.8);
        return blocking(mc, ahead) && !blocking(mc, ahead.above()) && !blocking(mc, ahead.above(2));
    }

    // ------------------------------------------------------------------ holding the right item

    private static boolean ensureHeld(Minecraft mc, Item item) {
        var me = mc.player;
        var inv = me.getInventory();
        if (inv.getItem(inv.getSelectedSlot()).is(item)) return true;
        if (creative) {
            if (!savedCaptured) {
                savedSlot8 = inv.getItem(8).copy();
                savedCaptured = true;
            }
            ItemStack give = new ItemStack(item, item.getDefaultMaxStackSize());
            inv.setItem(8, give);
            mc.gameMode.handleCreativeModeItemAdd(give, 36 + 8);
            inv.setSelectedSlot(8);
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(item)) {
                inv.setSelectedSlot(i);
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i).is(item)) {
                mc.gameMode.handleContainerInput(me.inventoryMenu.containerId, i, 8, ContainerInput.SWAP, me);
                swaps.add(i);
                inv.setSelectedSlot(8);
                return false;       // give the swap a tick
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ placing

    private static Option findOption(Minecraft mc, BlockPos p, Vec3 eye, double reach) {
        Option best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos n = p.relative(d);
            BlockState ns = mc.level.getBlockState(n);
            if (ns.isAir() || ns.canBeReplaced() || !ns.getFluidState().isEmpty() || interactive(ns)) continue;
            if (ns.getCollisionShape(mc.level, n).isEmpty()) continue;
            Direction face = d.getOpposite();
            Vec3 point = Vec3.atCenterOf(p).add(d.getStepX() * 0.5, d.getStepY() * 0.5, d.getStepZ() * 0.5);
            double dist = eye.distanceTo(point);
            if (dist > reach || dist >= bestDist) continue;
            Vec3 to = point.add(Vec3.atCenterOf(n).subtract(point).scale(0.2));
            var hit = mc.level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(n) || hit.getDirection() != face) continue;
            best = new Option(n, face, point);
            bestDist = dist;
        }
        return best;
    }

    private static void place(Minecraft mc, Option o) {
        var me = mc.player;
        Vec3 eye = me.getEyePosition();
        double dx = o.point().x - eye.x, dy = o.point().y - eye.y, dz = o.point().z - eye.z;
        me.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        me.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
        mc.gameMode.useItemOn(me, InteractionHand.MAIN_HAND, new BlockHitResult(o.point(), o.face(), o.neighbor(), false));
        me.swing(InteractionHand.MAIN_HAND);
    }

    // ------------------------------------------------------------------ finding somewhere to stand, and walking there

    private static boolean planWalk(Minecraft mc, BlockPos target, double reach) {
        var me = mc.player;
        BlockPos start = me.blockPosition();
        // breadth first search over standable cells (flat steps, one block up, up to two down)
        Map<BlockPos, BlockPos> from = new HashMap<>();
        List<BlockPos> frontier = new ArrayList<>();
        List<BlockPos> order = new ArrayList<>();
        frontier.add(start);
        from.put(start, null);
        int limit = 6000;
        int bound = Math.max(12, Math.max(Math.abs(start.getX() - target.getX()), Math.abs(start.getZ() - target.getZ())) + 4);
        while (!frontier.isEmpty() && order.size() < limit) {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos c : frontier) {
                order.add(c);
                for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    for (int dy : new int[]{0, 1, -1, -2}) {
                        BlockPos n = c.relative(d).offset(0, dy, 0);
                        if (from.containsKey(n) || Math.abs(n.getX() - target.getX()) > bound || Math.abs(n.getZ() - target.getZ()) > bound
                            || Math.abs(n.getY() - target.getY()) > 8) continue;
                        if (!standable(mc, n)) continue;
                        if (dy == 1 && blocking(mc, c.above(2))) continue;          // no room to jump
                        if (dy < 0 && (blocking(mc, c.relative(d).above()) || blocking(mc, c.relative(d).above(2)))) continue;
                        from.put(n, c);
                        next.add(n);
                        break;
                    }
                }
            }
            frontier = next;
        }
        Set<BlockPos> pending = new HashSet<>();
        for (int q = index; q < queue.size(); q++) pending.add(queue.get(q).pos());
        // the closest standing spot (by walking order) from which the block can be placed, standing on the ground or on a pillar
        for (int h = 0; h <= 3; h++) {
            if (h > 0 && (scaffoldFor() == null)) break;
            for (BlockPos c : order) {
                Vec3 eye = new Vec3(c.getX() + 0.5, c.getY() + 1.62 + h, c.getZ() + 0.5);
                if (eye.distanceTo(Vec3.atCenterOf(target)) > reach + 0.9) continue;
                if (c.equals(target)) continue;
                boolean clear = true;
                for (int k = 0; k <= h + 1; k++) if (c.above(k).equals(target)) clear = false;
                if (h > 0) for (int k = 0; k <= h + 1; k++) if (pending.contains(c.above(k))) clear = false;     // never build the scaffold where a wall still has to go
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
        if (System.getProperty("edith.dbg") != null) {
            int dist = 0, withOpt = 0;
            for (BlockPos c : order) {
                for (int h = 0; h <= 3; h++) {
                    Vec3 eye = new Vec3(c.getX() + 0.5, c.getY() + 1.62 + h, c.getZ() + 0.5);
                    if (eye.distanceTo(Vec3.atCenterOf(target)) > reach + 0.9) continue;
                    dist++;
                    if (findOption(mc, target, eye, reach) != null) withOpt++;
                }
            }
            System.out.println("BUILDER planWalk fail target=" + target + " start=" + start + " reachableCells=" + order.size() + " inRange=" + dist + " withOption=" + withOpt
                + " scaffoldItem=" + scaffoldFor() + " belowTarget=" + mc.level.getBlockState(target.below()).getBlock());
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

    // ------------------------------------------------------------------ preview follows the crosshair

    private static void updatePreview(Minecraft mc) {
        var me = mc.player;
        HitResult h = me.pick(30.0, 1.0F, false);
        if (h instanceof BlockHitResult b && h.getType() == HitResult.Type.BLOCK) {
            origin = b.getBlockPos().above().offset(-plan.width(rot) / 2, 0, -plan.depth(rot) / 2);
        }
        if (!origin.equals(lastCheckedOrigin) || rot != lastCheckedRot) {
            lastCheckedOrigin = origin;
            lastCheckedRot = rot;
            checkSite(mc);
        }
    }

    // ------------------------------------------------------------------ drawing

    private static int roleColor(Role r) {
        return switch (r) {
            case GLASS -> 0xFF9AE0FF;
            case ROOF -> 0xFF5CB8F0;
            case FLOOR -> 0xFF3A8DC0;
            case PILLAR -> 0xFFB0E8FF;
            case DOOR -> 0xFFFFC857;
            case TABLE, FURNACE, CHEST, CARPET, LIGHT -> 0xFFFFD27A;
            default -> 0xFF78D0FF;
        };
    }

    public static void draw(GuiGraphicsExtractor g, Font font, int w, int h) {
        if (mode == Mode.IDLE || plan == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) return;
        Project pr = new Project(w, h);
        int from = mode == Mode.BUILDING ? Math.max(0, index) : 0;
        boolean ok = mode == Mode.BUILDING || siteOk;
        int tint = ok ? 0 : 1;
        int drawn = 0;
        for (int i = (mode == Mode.BUILDING ? from : 0); i < plan.cells.size() && drawn < 900; i++) {
            Blueprint.Cell c = mode == Mode.BUILDING ? queue.get(i).cell() : plan.cells.get(i);
            BlockPos p = mode == Mode.BUILDING ? queue.get(i).pos() : worldPos(c);
            double[] a = pr.at(Vec3.atCenterOf(p));
            if (a == null || a[0] < -20 || a[0] > w + 20 || a[1] < -20 || a[1] > h + 20) continue;
            double[] b = pr.at(Vec3.atCenterOf(p).add(0, 0.5, 0));
            int size = b == null ? 3 : (int) Math.max(2, Math.min(40, Math.abs(a[1] - b[1]) * 2));
            int col = tint == 1 ? 0xFFFF6060 : roleColor(c.role());
            int x = (int) a[0] - size / 2, y = (int) a[1] - size / 2;
            if (size <= 4) {
                g.fill(x, y, x + size, y + size, alpha(col, 0.8f));
            } else {
                g.fill(x, y, x + size, y + size, alpha(col, 0.07f));
                box(g, x, y, size, size, alpha(col, 0.55f));
            }
            drawn++;
        }
        if (mode == Mode.BUILDING && index < queue.size()) {
            double[] a = pr.at(Vec3.atCenterOf(queue.get(index).pos()));
            if (a != null) corners(g, (int) a[0] - 10, (int) a[1] - 10, 20, 20, 5, BRIGHT);
        }
        // status panel
        int cx = w / 2, y0 = 36;
        String l1, l2;
        int col;
        if (mode == Mode.PREVIEW) {
            l1 = plan.label.toUpperCase(Locale.ROOT) + "  -  " + (siteOk ? "SITE CLEAR" : "BLOCKED");
            l2 = siteOk ? "Y build   N cancel   /edith build rotate" : siteMsg;
            col = siteOk ? GREEN : RED;
        } else {
            l1 = "BUILDING " + plan.label.toUpperCase(Locale.ROOT) + "  " + Math.min(index, queue.size()) + "/" + queue.size();
            l2 = "N to stop";
            col = AMBER;
        }
        int tw = Math.max(width(font, l1, 0.8f), width(font, l2, 0.6f)) + 16;
        g.fill(cx - tw / 2, y0, cx + tw / 2, y0 + 26, 0x99041722);
        box(g, cx - tw / 2, y0, tw, 26, alpha(col, 0.8f));
        centered(g, font, l1, cx, y0 + 4, col, 0.8f);
        centered(g, font, l2, cx, y0 + 15, DIM, 0.6f);
        if (mode == Mode.BUILDING) {
            int bw = tw - 8;
            g.fill(cx - bw / 2, y0 + 23, cx + bw / 2, y0 + 25, FAINT);
            g.fill(cx - bw / 2, y0 + 23, cx - bw / 2 + (int) (bw * (index / (float) Math.max(1, queue.size()))), y0 + 25, col);
        }
    }
}
