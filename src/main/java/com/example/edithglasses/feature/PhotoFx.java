package com.example.edithglasses.feature;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Darkroom for the glasses camera: takes the raw screenshot pixels and applies a "look" (colour grade, bloom, vignette, grain,
 * tilt-shift), exposure and a frame. Pure Java, runs on a worker thread after the shot, never touches the game.
 */
public final class PhotoFx {
    /** A look: saturation, contrast, brightness, shadow / highlight tint, faded blacks, bloom, vignette, grain, tilt-shift, black and white, crop aspect. */
    public record Look(String name, double sat, double contrast, double bright, double[] shadow, double[] light, double fade,
                       double bloom, double vignette, double grain, double tilt, boolean mono, double crop) {
    }

    private static final double[] NEUTRAL = {1, 1, 1};

    public static final Look[] LOOKS = {
        new Look("NATURAL", 1.0, 1.0, 0, NEUTRAL, NEUTRAL, 0, 0, 0, 0, 0, false, 0),
        new Look("VIVID", 1.4, 1.12, 0.01, NEUTRAL, NEUTRAL, 0, 0.12, 0.18, 0, 0, false, 0),
        new Look("CINEMATIC", 1.05, 1.16, -0.02, new double[]{0.92, 1.0, 1.08}, new double[]{1.08, 1.0, 0.9}, 0.03, 0.14, 0.38, 0.03, 0, false, 2.39),
        new Look("NOIR", 0, 1.35, -0.02, NEUTRAL, NEUTRAL, 0, 0.1, 0.5, 0.06, 0, true, 0),
        new Look("VINTAGE", 0.75, 0.88, 0.03, new double[]{1.05, 1.0, 0.85}, new double[]{1.08, 1.02, 0.88}, 0.1, 0.12, 0.42, 0.07, 0, false, 0),
        new Look("DREAM", 0.9, 0.92, 0.06, new double[]{1.0, 0.95, 1.08}, new double[]{1.06, 1.02, 1.04}, 0.04, 0.85, 0.2, 0.01, 0, false, 0),
        new Look("MINIATURE", 1.5, 1.12, 0.02, NEUTRAL, NEUTRAL, 0, 0.08, 0.1, 0, 1.0, false, 0),
        new Look("NEON", 1.6, 1.25, -0.05, new double[]{0.8, 0.9, 1.25}, new double[]{1.15, 0.95, 1.15}, 0, 0.7, 0.35, 0.02, 0, false, 0),
        new Look("SUNSET", 1.2, 1.1, 0.0, new double[]{0.9, 0.85, 1.05}, new double[]{1.18, 1.0, 0.8}, 0.02, 0.3, 0.3, 0.02, 0, false, 0),
    };

    public static final String[] FRAMES = {"NONE", "BORDER", "POLAROID", "FILM"};

    private PhotoFx() {
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** One horizontal then vertical box blur, applied {@code passes} times. */
    private static float[] blur(float[] src, int w, int h, int radius, int passes) {
        float[] a = src.clone(), b = new float[src.length];
        for (int p = 0; p < passes; p++) {
            final float[] in = a, out = b;
            IntStream.range(0, h).parallel().forEach(y -> {
                double sum = 0;
                int row = y * w;
                for (int x = -radius; x <= radius; x++) sum += in[row + Math.max(0, Math.min(w - 1, x))];
                for (int x = 0; x < w; x++) {
                    out[row + x] = (float) (sum / (2 * radius + 1));
                    sum += in[row + Math.min(w - 1, x + radius + 1)] - in[row + Math.max(0, x - radius)];
                }
            });
            final float[] in2 = out, out2 = in;
            IntStream.range(0, w).parallel().forEach(x -> {
                double sum = 0;
                for (int y = -radius; y <= radius; y++) sum += in2[Math.max(0, Math.min(h - 1, y)) * w + x];
                for (int y = 0; y < h; y++) {
                    out2[y * w + x] = (float) (sum / (2 * radius + 1));
                    sum += in2[Math.min(h - 1, y + radius + 1) * w + x] - in2[Math.max(0, y - radius) * w + x];
                }
            });
        }
        return a;
    }

    /** ARGB pixels in, finished picture out. {@code caption} is only drawn on a polaroid. */
    public static BufferedImage process(int[] argb, int w0, int h0, Look look, double ev, int frame, String caption) {
        // crop to the look's aspect ratio (cinematic bars)
        int w = w0, h = h0, y0 = 0;
        if (look.crop() > 0 && (double) w0 / h0 < look.crop() * 0.999) {
            h = (int) Math.round(w0 / look.crop());
            y0 = (h0 - h) / 2;
        }
        final int fw = w, fh = h, fy0 = y0;
        float[] r = new float[fw * fh], g = new float[fw * fh], b = new float[fw * fh];
        final double mul = Math.pow(2, ev);
        IntStream.range(0, fh).parallel().forEach(y -> {
            for (int x = 0; x < fw; x++) {
                int p = argb[(y + fy0) * w0 + x], i = y * fw + x;
                r[i] = (float) (((p >> 16) & 255) / 255.0 * mul);
                g[i] = (float) (((p >> 8) & 255) / 255.0 * mul);
                b[i] = (float) ((p & 255) / 255.0 * mul);
            }
        });

        float[] sr = null, sg = null, sb = null;           // blurred copy for tilt-shift
        if (look.tilt() > 0) {
            int rad = Math.max(4, fw / 220);
            sr = blur(r, fw, fh, rad, 2);
            sg = blur(g, fw, fh, rad, 2);
            sb = blur(b, fw, fh, rad, 2);
        }
        float[] hr = null, hg = null, hb = null;           // highlights for bloom
        if (look.bloom() > 0) {
            float[] tr = new float[r.length], tg = new float[r.length], tb = new float[r.length];
            for (int i = 0; i < r.length; i++) {
                float l = 0.299f * r[i] + 0.587f * g[i] + 0.114f * b[i];
                float k = (float) smooth(0.78, 1.05, l);
                tr[i] = r[i] * k;
                tg[i] = g[i] * k;
                tb[i] = b[i] * k;
            }
            int rad = Math.max(8, fw / 60);
            hr = blur(tr, fw, fh, rad, 4);
            hg = blur(tg, fw, fh, rad, 4);
            hb = blur(tb, fw, fh, rad, 4);
        }

        final float[] fsr = sr, fsg = sg, fsb = sb, fhr = hr, fhg = hg, fhb = hb;
        BufferedImage img = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_RGB);
        int[] out = new int[fw * fh];
        final Random seed = new Random(7);
        final long grainSeed = seed.nextLong();
        IntStream.range(0, fh).parallel().forEach(y -> {
            Random rnd = new Random(grainSeed + y * 7919L);
            for (int x = 0; x < fw; x++) {
                int i = y * fw + x;
                double cr = r[i], cg = g[i], cb = b[i];
                if (fsr != null) {                           // tilt-shift: sharp in a band around the middle, blurred above and below
                    double d = Math.abs((y + 0.5) / fh - 0.5) * 2;
                    double k = smooth(0.25, 0.85, d) * look.tilt();
                    cr += (fsr[i] - cr) * k;
                    cg += (fsg[i] - cg) * k;
                    cb += (fsb[i] - cb) * k;
                }
                if (fhr != null) {
                    cr += fhr[i] * look.bloom();
                    cg += fhg[i] * look.bloom();
                    cb += fhb[i] * look.bloom();
                }
                double lum = 0.299 * cr + 0.587 * cg + 0.114 * cb;
                if (look.mono()) {
                    cr = cg = cb = lum;
                } else {
                    cr = lum + (cr - lum) * look.sat();
                    cg = lum + (cg - lum) * look.sat();
                    cb = lum + (cb - lum) * look.sat();
                }
                double t = smooth(0.15, 0.85, lum);          // split tone: shadows towards one tint, highlights towards another
                cr *= look.shadow()[0] + (look.light()[0] - look.shadow()[0]) * t;
                cg *= look.shadow()[1] + (look.light()[1] - look.shadow()[1]) * t;
                cb *= look.shadow()[2] + (look.light()[2] - look.shadow()[2]) * t;
                cr = (cr - 0.5) * look.contrast() + 0.5 + look.bright();
                cg = (cg - 0.5) * look.contrast() + 0.5 + look.bright();
                cb = (cb - 0.5) * look.contrast() + 0.5 + look.bright();
                if (look.fade() > 0) {
                    cr = look.fade() + cr * (1 - look.fade());
                    cg = look.fade() + cg * (1 - look.fade());
                    cb = look.fade() + cb * (1 - look.fade());
                }
                if (look.vignette() > 0) {
                    double dx = (x + 0.5) / fw - 0.5, dy = (y + 0.5) / fh - 0.5;
                    double v = 1 - look.vignette() * Math.pow((dx * dx + dy * dy) * 2.2, 1.3);
                    v = Math.max(0.0, v);
                    cr *= v;
                    cg *= v;
                    cb *= v;
                }
                if (look.grain() > 0) {
                    double n = (rnd.nextDouble() - 0.5) * look.grain() * 2;
                    cr += n;
                    cg += n;
                    cb += n;
                }
                int ir = (int) Math.max(0, Math.min(255, cr * 255)), ig = (int) Math.max(0, Math.min(255, cg * 255)), ib = (int) Math.max(0, Math.min(255, cb * 255));
                out[i] = (ir << 16) | (ig << 8) | ib;
            }
        });
        img.setRGB(0, 0, fw, fh, out, 0, fw);
        return addFrame(img, frame, caption);
    }

    private static BufferedImage addFrame(BufferedImage in, int frame, String caption) {
        if (frame <= 0) return in;
        int w = in.getWidth(), h = in.getHeight(), m = Math.min(w, h);
        int left, top, right, bottom;
        Color bg;
        switch (frame) {
            case 1 -> { left = top = right = bottom = (int) (m * 0.03); bg = Color.WHITE; }
            case 2 -> { left = right = (int) (m * 0.05); top = left; bottom = (int) (m * 0.2); bg = new Color(245, 242, 232); }
            default -> { left = right = 0; top = bottom = (int) (m * 0.08); bg = new Color(12, 12, 12); }
        }
        BufferedImage out = new BufferedImage(w + left + right, h + top + bottom, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(in, left, top, null);
        if (frame == 2 && caption != null) {
            g.setColor(new Color(60, 55, 50));
            g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(12, bottom / 6)));
            int y = h + top + (int) (bottom * 0.4);
            for (String line : caption.split("\n")) {
                g.drawString(line, left, y);
                y += (int) (bottom * 0.24);
            }
        } else if (frame == 3) {
            g.setColor(new Color(235, 235, 225));
            int s = Math.max(6, top / 3), gap = s * 2;
            for (int x = gap / 2; x < out.getWidth() - s; x += gap * 2) {
                g.fillRoundRect(x, top / 3 - s / 2, s, s, 3, 3);
                g.fillRoundRect(x, out.getHeight() - top / 3 - s / 2, s, s, 3, 3);
            }
        }
        g.dispose();
        return out;
    }
}
