package com.example.edithglasses.ai;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * Plays a video inside Minecraft: ffmpeg decodes frames in real time into a texture, ffplay plays the sound.
 * Esc skips. Closes itself at the end of the clip.
 */
public final class VideoScreen extends Screen {
    private static final int W = 960, H = 540, FRAME = W * H * 4;

    private final String ffmpeg, ffplay, clip;
    private final CountDownLatch done;
    private final Object lock = new Object();
    private final byte[] latest = new byte[FRAME];
    private final ByteBuffer upload = MemoryUtil.memAlloc(FRAME);
    private volatile boolean fresh, finished, stopped, gotFrame;
    private Process video, audio;
    private DynamicTexture texture;
    private Identifier id;
    private long startedAt;
    private boolean closed;

    public VideoScreen(String ffmpeg, String ffplay, String clip, CountDownLatch done) {
        super(Component.literal("EDITH"));
        this.ffmpeg = ffmpeg;
        this.ffplay = ffplay;
        this.clip = clip;
        this.done = done;
    }

    @Override
    protected void init() {
        if (texture != null) return;
        texture = new DynamicTexture("edith_video", W, H, true) {
            {
                // smooth scaling instead of the blocky nearest-neighbour default
                this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(
                    com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE, com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                    com.mojang.blaze3d.textures.FilterMode.LINEAR, com.mojang.blaze3d.textures.FilterMode.LINEAR, false);
            }
        };
        id = Identifier.fromNamespaceAndPath("edithglasses", "video/frame");
        this.minecraft.getTextureManager().register(id, texture);
        startedAt = System.currentTimeMillis();
        try {
            video = new ProcessBuilder(ffmpeg, "-loglevel", "quiet", "-re", "-i", clip, "-an", "-f", "rawvideo",
                "-pix_fmt", "rgba", "-vf", "scale=" + W + ":" + H + ":flags=lanczos", "-").start();
            if (ffplay != null) {
                audio = new ProcessBuilder(ffplay, "-nodisp", "-autoexit", "-loglevel", "quiet", clip).start();
            }
        } catch (Exception e) {
            finished = true;
            return;
        }
        Thread t = new Thread(() -> {
            try (InputStream in = video.getInputStream()) {
                byte[] frame = new byte[FRAME];
                while (!stopped) {
                    int off = 0;
                    while (off < FRAME) {
                        int n = in.read(frame, off, FRAME - off);
                        if (n < 0) { finished = true; return; }
                        off += n;
                    }
                    synchronized (lock) {
                        System.arraycopy(frame, 0, latest, 0, FRAME);
                        fresh = true;
                        gotFrame = true;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                finished = true;
            }
        }, "edith-video");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, this.width, this.height, 0xFF000000);
        if (texture == null) return;
        boolean newFrame;
        synchronized (lock) {
            newFrame = fresh;
            if (newFrame) {
                upload.clear();
                upload.put(latest, 0, FRAME);
                upload.flip();
                fresh = false;
            }
        }
        if (newFrame) {
            NativeImage img = texture.getPixels();
            MemoryUtil.memCopy(MemoryUtil.memAddress(upload), img.getPointer(), FRAME);
            texture.upload();
        }
        if (gotFrame) {
            int dw = this.width, dh = dw * H / W;
            if (dh > this.height) { dh = this.height; dw = dh * W / H; }
            int x = (this.width - dw) / 2, y = (this.height - dh) / 2;
            g.blit(id, x, y, x + dw, y + dh, 0f, 1f, 0f, 1f);
        }
        long age = System.currentTimeMillis() - startedAt;
        if (finished || (!gotFrame && age > 8000)) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (closed) return;
        closed = true;
        stopped = true;
        if (video != null) video.destroy();
        if (audio != null) audio.destroy();
        if (id != null) {
            // release two ticks later: this frame's draw call still uses the texture
            Identifier released = id;
            this.minecraft.schedule(() -> this.minecraft.schedule(() -> this.minecraft.getTextureManager().release(released)));
        }
        MemoryUtil.memFree(upload);
        this.minecraft.gui.setScreen(null);
        done.countDown();
    }
}
