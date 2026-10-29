package com.example.edithglasses.feature;

import com.example.edithglasses.Glasses;
import com.example.edithglasses.mixin.CameraAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.example.edithglasses.feature.HudStyle.*;

/**
 * Advanced camera mode for the glasses: a viewfinder (grids, horizon, focal length, exposure), looks (cinematic, noir, vintage,
 * dream, miniature, neon...), frames, self timer, burst, a 360 degree panorama and a free camera that leaves your head.
 * Photos are taken without any HUD, then developed in the background (see PhotoFx) and saved in screenshots/edith.
 */
public final class CameraMode {
    public enum Shot { PHOTO, BURST, PANORAMA }

    private enum Cap { IDLE, WAIT, TAKING }

    private static boolean active = false, freecam = false, clean = false;
    private static int look = 0, frame = 0, grid = 1, timerIdx = 0, shots = 0, flash = 0, countdown = -1;
    private static double ev = 0;
    private static Shot shot = Shot.PHOTO;
    private static final int[] TIMERS = {0, 3, 5, 10};
    private static final String[] GRIDS = {"OFF", "THIRDS", "GOLDEN", "CENTRE"};

    private static Marker cam;
    private static Vec3 camOrigin = Vec3.ZERO;

    private static Cap cap = Cap.IDLE;
    private static int capWait = 0, burstLeft = 0, panoStep = 0, panoSteps = 0, panoStrip = 0;
    private static float panoYaw0 = 0, panoStepDeg = 0;
    private static final List<int[]> strips = new ArrayList<>();
    private static boolean savedHideGui = false;
    private static String lastSaved = "";
    private static final Map<Integer, Boolean> prev = new HashMap<>();

    private CameraMode() {
    }

    public static boolean active() {
        return active;
    }

    /** True while a frame is being captured: nothing of the HUD may be drawn. */
    public static boolean clean() {
        return clean;
    }

    public static boolean busy() {
        return cap != Cap.IDLE || countdown >= 0 || burstLeft > 0;
    }

    // ------------------------------------------------------------------ on / off

    public static String toggle(Minecraft mc) {
        if (active) {
            exit(mc);
            return "Camera mode off.";
        }
        if (mc.player == null || mc.level == null) return "Not in a world.";
        if (!Glasses.equipped(mc.player)) return "Put on the glasses first (hold them or /wear).";
        if (Recon.active() || Builder.busy()) return "Finish the recon drone or the building first.";
        active = true;
        prev.clear();
        return "Camera mode on. Enter or click takes a photo, C leaves.";
    }

    private static void exit(Minecraft mc) {
        if (freecam) setFreecam(mc, false);
        if (cap != Cap.IDLE || savedHideGui) restoreGui(mc);
        active = false;
        countdown = -1;
        burstLeft = 0;
        cap = Cap.IDLE;
        Glasses.setCameraZoomMode(false);
    }

    private static void setFreecam(Minecraft mc, boolean on) {
        if (on == freecam || mc.player == null || mc.level == null) return;
        if (on) {
            cam = new Marker(EntityTypes.MARKER, mc.level);
            camOrigin = mc.player.getEyePosition();
            cam.setPos(camOrigin);
            cam.setYRot(mc.player.getYRot());
            cam.setXRot(mc.player.getXRot());
            cam.setOldPosAndRot();
            mc.setCameraEntity(cam);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        } else {
            cam = null;
            mc.setCameraEntity(mc.player);
        }
        freecam = on;
    }

    // ------------------------------------------------------------------ input

    private static boolean down(Minecraft mc, int key) {
        return InputConstants.isKeyDown(mc.getWindow(), key);
    }

    /** True once per key press. */
    private static boolean edge(Minecraft mc, int key) {
        boolean now = down(mc, key);
        boolean was = prev.getOrDefault(key, false);
        prev.put(key, now);
        return now && !was;
    }

    private static boolean physical(Minecraft mc, KeyMapping km) {
        return InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(km.saveString()).getValue());
    }

    /** Before the player's tick: the camera mode owns the mouse buttons (shutter) and, with the free camera, the movement keys. */
    public static void pre(Minecraft mc) {
        if (!active || mc.player == null) return;
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        if (freecam) {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keyShift.setDown(false);
            mc.options.keySprint.setDown(false);
        }
    }

    public static void tick(Minecraft mc) {
        if (flash > 0) flash--;
        if (!active) return;
        var me = mc.player;
        if (me == null || mc.level == null) {
            active = false;
            freecam = false;
            cam = null;
            cap = Cap.IDLE;
            return;
        }
        if (!Glasses.equipped(me) && !freecam) {
            exit(mc);
            return;
        }
        Glasses.setCameraZoomMode(true);

        // capture state machine
        if (cap == Cap.WAIT) {
            if (--capWait <= 0) takeFrame(mc);
            return;
        }
        if (cap == Cap.TAKING) return;
        if (burstLeft > 0) {
            if (--capWait <= 0) beginFrame(mc, 3);
            return;
        }
        if (countdown >= 0) {
            if (countdown-- == 0) startShot(mc);
            return;
        }

        if (mc.gui.screen() != null) return;

        // free camera flight
        if (freecam && cam != null) {
            cam.setOldPosAndRot();
            cam.setYRot(me.getYRot());
            cam.setXRot(me.getXRot());
            Vec3 f = me.getLookAngle();
            Vec3 right = f.cross(new Vec3(0, 1, 0)).normalize();
            double sp = down(mc, GLFW.GLFW_KEY_LEFT_CONTROL) ? 0.9 : 0.22;
            Vec3 mv = Vec3.ZERO;
            if (physical(mc, mc.options.keyUp)) mv = mv.add(f);
            if (physical(mc, mc.options.keyDown)) mv = mv.subtract(f);
            if (physical(mc, mc.options.keyRight)) mv = mv.add(right);
            if (physical(mc, mc.options.keyLeft)) mv = mv.subtract(right);
            if (physical(mc, mc.options.keyJump)) mv = mv.add(0, 1, 0);
            if (physical(mc, mc.options.keyShift)) mv = mv.add(0, -1, 0);
            if (mv.lengthSqr() > 0) {
                Vec3 next = cam.position().add(mv.normalize().scale(sp));
                if (next.distanceTo(me.getEyePosition()) < 90) cam.setPos(next);
            }
        }

        // keys
        if (edge(mc, GLFW.GLFW_KEY_RIGHT)) look = (look + 1) % PhotoFx.LOOKS.length;
        if (edge(mc, GLFW.GLFW_KEY_LEFT)) look = (look + PhotoFx.LOOKS.length - 1) % PhotoFx.LOOKS.length;
        if (edge(mc, GLFW.GLFW_KEY_UP)) ev = Math.min(3, ev + 0.5);
        if (edge(mc, GLFW.GLFW_KEY_DOWN)) ev = Math.max(-3, ev - 0.5);
        if (edge(mc, GLFW.GLFW_KEY_PERIOD)) frame = (frame + 1) % PhotoFx.FRAMES.length;
        if (edge(mc, GLFW.GLFW_KEY_COMMA)) frame = (frame + PhotoFx.FRAMES.length - 1) % PhotoFx.FRAMES.length;
        if (edge(mc, GLFW.GLFW_KEY_H)) grid = (grid + 1) % GRIDS.length;
        if (edge(mc, GLFW.GLFW_KEY_T)) timerIdx = (timerIdx + 1) % TIMERS.length;
        if (edge(mc, GLFW.GLFW_KEY_M)) shot = Shot.values()[(shot.ordinal() + 1) % Shot.values().length];
        if (edge(mc, GLFW.GLFW_KEY_R)) {
            ev = 0;
            Glasses.resetZoom();
        }
        if (edge(mc, GLFW.GLFW_KEY_F)) setFreecam(mc, !freecam);
        boolean click = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clickEdge = click && !prev.getOrDefault(-1, false);
        prev.put(-1, click);
        if (edge(mc, GLFW.GLFW_KEY_ENTER) || clickEdge) shutter(mc);
    }

    // ------------------------------------------------------------------ shutter and capture

    public static void shutter(Minecraft mc) {
        if (!active || busy()) return;
        int secs = TIMERS[timerIdx];
        if (secs > 0) countdown = secs * 20;
        else startShot(mc);
    }

    private static void startShot(Minecraft mc) {
        countdown = -1;
        savedHideGui = true;
        switch (shot) {
            case PHOTO -> beginFrame(mc, 3);
            case BURST -> {
                burstLeft = 5;
                beginFrame(mc, 3);
            }
            case PANORAMA -> startPano(mc);
        }
    }

    private static void restoreGui(Minecraft mc) {
        savedHideGui = false;
        clean = false;
    }

    private static void beginFrame(Minecraft mc, int wait) {
        clean = true;
        cap = Cap.WAIT;
        capWait = wait;
    }

    private static void takeFrame(Minecraft mc) {
        cap = Cap.TAKING;
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), img -> {
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getPixels();
            int[] argb = px.clone();
            img.close();
            mc.execute(() -> onFrame(mc, argb, w, h));
        });
    }

    private static void onFrame(Minecraft mc, int[] argb, int w, int h) {
        cap = Cap.IDLE;
        if (!active) {
            restoreGui(mc);
            return;
        }
        switch (shot) {
            case PHOTO -> {
                save(mc, argb, w, h, "");
                finish(mc);
            }
            case BURST -> {
                save(mc, argb, w, h, "_burst" + (6 - burstLeft));
                burstLeft--;
                if (burstLeft > 0) capWait = 5;
                else finish(mc);
            }
            case PANORAMA -> {
                int x0 = Math.max(0, w / 2 - panoStrip / 2), sw = Math.min(panoStrip, w - x0);
                int[] strip = new int[sw * h];
                for (int y = 0; y < h; y++) System.arraycopy(argb, y * w + x0, strip, y * sw, sw);
                strips.add(strip);
                panoStep++;
                if (panoStep < panoSteps) {
                    mc.player.setYRot(panoYaw0 + panoStepDeg * panoStep);
                    beginFrame(mc, 3);
                } else {
                    mc.player.setYRot(panoYaw0);
                    stitch(mc, h, sw);
                    finish(mc);
                }
            }
        }
    }

    private static void finish(Minecraft mc) {
        restoreGui(mc);
        flash = 8;
        shots++;
        capWait = 0;
    }

    private static void startPano(Minecraft mc) {
        var cam = mc.gameRenderer.mainCamera();
        double vfov = Math.toRadians(((CameraAccessor) cam).edith$fov());
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double step = 2 * Math.atan(0.15 * aspect * Math.tan(vfov / 2));
        panoSteps = (int) Math.ceil(2 * Math.PI / step);
        panoStepDeg = 360f / panoSteps;
        panoStep = 0;
        panoYaw0 = mc.player.getYRot();
        panoStrip = (int) Math.ceil(mc.getWindow().getHeight() * Math.tan(Math.toRadians(panoStepDeg) / 2) / Math.tan(vfov / 2)) + 2;
        strips.clear();
        beginFrame(mc, 3);
    }

    private static void stitch(Minecraft mc, int h, int sw) {
        final List<int[]> parts = new ArrayList<>(strips);
        strips.clear();
        int totalW = 0;
        for (int[] p : parts) totalW += p.length / h;
        final int tw = totalW;
        int[] out = new int[tw * h];
        int x = 0;
        for (int[] p : parts) {
            int pw = p.length / h;
            for (int y = 0; y < h; y++) System.arraycopy(p, y * pw, out, y * tw + x, pw);
            x += pw;
        }
        save(mc, out, tw, h, "_pano360");
    }

    private static void save(Minecraft mc, int[] argb, int w, int h, String suffix) {
        final PhotoFx.Look lk = PhotoFx.LOOKS[look];
        final double ex = ev;
        final int fr = frame;
        var p = mc.player;
        final String caption = "E.D.I.T.H. GLASSES\n" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm"))
            + "\n" + (p == null ? "" : Math.round(p.getX()) + " " + Math.round(p.getY()) + " " + Math.round(p.getZ()) + "   " + lk.name());
        final File dir = new File(mc.gameDirectory, "screenshots/edith");
        Thread t = new Thread(() -> {
            try {
                BufferedImage img = PhotoFx.process(argb, w, h, lk, ex, fr, caption);
                dir.mkdirs();
                String name = "edith_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
                    + "_" + lk.name().toLowerCase(Locale.ROOT) + suffix + ".png";
                File f = new File(dir, name);
                javax.imageio.ImageIO.write(img, "png", f);
                lastSaved = name;
                mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(Component.literal(
                    "EDITH: photo saved (" + img.getWidth() + "x" + img.getHeight() + ", " + lk.name() + "): screenshots/edith/" + name)));
            } catch (Exception e) {
                mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(Component.literal("EDITH: could not save the photo: " + e.getMessage())));
            }
        }, "edith-darkroom");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ viewfinder

    public static void draw(GuiGraphicsExtractor g, Font font, int w, int h) {
        if (!active || clean) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PhotoFx.Look lk = PhotoFx.LOOKS[look];
        int cx = w / 2, cy = h / 2;

        // live preview (approximate): tint, exposure, vignette and the crop bars of the look
        int tint = switch (lk.name()) {
            case "CINEMATIC" -> 0x1F103038;
            case "NOIR" -> 0x38888888;
            case "VINTAGE" -> 0x38A07A40;
            case "DREAM" -> 0x26FFEAF4;
            case "NEON" -> 0x22A020FF;
            case "SUNSET" -> 0x30FF8020;
            case "VIVID" -> 0x0A00FFC0;
            default -> 0;
        };
        if (tint != 0) g.fill(0, 0, w, h, tint);
        if (ev < 0) g.fill(0, 0, w, h, ((int) Math.min(200, -ev * 48) << 24));
        else if (ev > 0) g.fill(0, 0, w, h, ((int) Math.min(160, ev * 36) << 24) | 0xFFFFFF);
        if (lk.vignette() > 0) {
            int a = (int) (lk.vignette() * 150);
            g.fillGradient(0, 0, w, h / 4, a << 24, 0);
            g.fillGradient(0, h - h / 4, w, h, 0, a << 24);
        }
        if (lk.crop() > 0) {
            int bar = (int) Math.max(0, (h - w / lk.crop()) / 2);
            g.fill(0, 0, w, bar, 0xEE000000);
            g.fill(0, h - bar, w, h, 0xEE000000);
        }

        // grid
        int gridCol = 0x66FFFFFF;
        switch (grid) {
            case 1 -> {
                for (int i = 1; i <= 2; i++) {
                    g.fill(w * i / 3, 0, w * i / 3 + 1, h, gridCol);
                    g.fill(0, h * i / 3, w, h * i / 3 + 1, gridCol);
                }
            }
            case 2 -> {
                int x1 = (int) (w * 0.382), x2 = (int) (w * 0.618), y1 = (int) (h * 0.382), y2 = (int) (h * 0.618);
                g.fill(x1, 0, x1 + 1, h, gridCol);
                g.fill(x2, 0, x2 + 1, h, gridCol);
                g.fill(0, y1, w, y1 + 1, gridCol);
                g.fill(0, y2, w, y2 + 1, gridCol);
            }
            case 3 -> {
                g.fill(cx, 0, cx + 1, h, gridCol);
                g.fill(0, cy, w, cy + 1, gridCol);
            }
            default -> {
            }
        }

        // horizon level and focus box
        float pitch = mc.player.getXRot();
        boolean level = Math.abs(pitch) < 1.5f;
        int hc = level ? GREEN : 0xCCFFFFFF;
        g.fill(cx - 60, cy, cx - 24, cy + 1, hc);
        g.fill(cx + 24, cy, cx + 60, cy + 1, hc);
        int fb = 26;
        corners(g, cx - fb, cy - fb, fb * 2, fb * 2, 8, 0xCCFFFFFF);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xCCFFFFFF);

        // frame brackets
        int m = 18;
        corners(g, m, m, w - 2 * m, h - 2 * m, 24, 0xDDFFFFFF);

        // info strip
        double mm = 24 * Glasses.targetZoom();
        boolean rec = (System.currentTimeMillis() / 500) % 2 == 0;
        g.fill(m + 6, m + 8, m + 12, m + 14, rec && !busy() ? 0xFFFF4040 : 0x66FF4040);
        String top = shot.name() + "   " + lk.name() + "   EV " + String.format("%+.1f", ev) + "   " + Math.round(mm) + " mm   FRAME " + PhotoFx.FRAMES[frame]
            + "   TIMER " + (TIMERS[timerIdx] == 0 ? "OFF" : TIMERS[timerIdx] + "s") + "   GRID " + GRIDS[grid] + (freecam ? "   FREE CAM" : "");
        text(g, font, top, m + 18, m + 7, 0xFFFFFFFF, 0.8f);
        String right = "SHOTS " + shots + (lastSaved.isEmpty() ? "" : "   LAST " + lastSaved.substring(Math.max(0, lastSaved.length() - 22)));
        text(g, font, right, w - m - width(font, right, 0.6f) - 6, m + 9, 0xCCFFFFFF, 0.6f);
        String hint = "ENTER / CLICK shutter   < > look   ^ v exposure   , . frame   H grid   T timer   M mode   F free camera   R reset   SCROLL zoom   C exit";
        centered(g, font, hint, cx, h - m - 10, 0xAAFFFFFF, 0.6f);
        if (freecam) centered(g, font, "WASD fly   SPACE up   SHIFT down   CTRL fast", cx, h - m - 20, 0xAA9AE0FF, 0.6f);

        // countdown and progress
        if (countdown >= 0) {
            String n = String.valueOf(countdown / 20 + 1);
            centered(g, font, n, cx, cy - 40, 0xFFFFFFFF, 6f);
        }
        if (cap != Cap.IDLE && shot == Shot.PANORAMA && panoSteps > 0) {
            int bw = 160;
            g.fill(cx - bw / 2, h - 60, cx + bw / 2, h - 57, FAINT);
            g.fill(cx - bw / 2, h - 60, cx - bw / 2 + bw * panoStep / panoSteps, h - 57, 0xFFFFFFFF);
        }
        if (flash > 0) g.fill(0, 0, w, h, ((int) (flash / 8f * 230) << 24) | 0xFFFFFF);
    }
}
