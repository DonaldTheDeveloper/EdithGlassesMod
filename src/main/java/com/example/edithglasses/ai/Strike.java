package com.example.edithglasses.ai;

import com.example.edithglasses.EdithConfig;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * "EDITH, that is a target": after confirmation, plays your clip inside Minecraft (config droneVideo), then launches
 * a real drone at the entity (see DroneStrike). Never targets players.
 */
public final class Strike {
    public static volatile String phase = "";

    private Strike() {
    }

    public static void execute(Minecraft mc, UUID id, String name) {
        EdithConfig cfg = EdithConfig.get();
        phase = "VIDEO";
        try {
            playClip(mc, cfg);
        } catch (Exception ignored) {
        }
        phase = "DRONE";
        try {
            DroneStrike.launch(mc, id);
        } catch (Exception e) {
            mc.execute(() -> {
                if (mc.player != null) mc.player.connection.sendCommand("kill " + id);
            });
        }
        phase = "";
    }

    private static void playClip(Minecraft mc, EdithConfig cfg) throws Exception {
        String path = cfg.droneVideo == null ? "" : cfg.droneVideo.trim();
        File f = path.isEmpty() ? null : new File(path);
        if (f == null || !f.isFile()) {
            Thread.sleep(2000); // no clip configured: short countdown only
            return;
        }
        String ffmpeg = Tools.find("ffmpeg");
        String ffplay = Tools.find("ffplay");
        if (ffmpeg != null) {
            // play inside Minecraft
            CountDownLatch done = new CountDownLatch(1);
            mc.execute(() -> mc.gui.setScreen(new VideoScreen(ffmpeg, ffplay, f.getAbsolutePath(), done)));
            done.await(10, TimeUnit.MINUTES);
            return;
        }
        // no ffmpeg found: open the clip in the default player and wait
        new ProcessBuilder("cmd", "/c", "start", "", f.getAbsolutePath()).start();
        Thread.sleep(Math.max(1, cfg.droneSeconds) * 1000L);
    }
}
