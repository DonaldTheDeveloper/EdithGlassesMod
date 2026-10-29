package com.example.edithglasses.ai;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Microphone capture and speaker playback using the JDK's own audio API. */
public final class Audio {
    private static final AudioFormat MIC = new AudioFormat(16000f, 16, 1, true, false);
    private static final int MAX_BYTES = 16000 * 2 * 30; // 30 s

    private TargetDataLine line;
    private Thread thread;
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private final AtomicBoolean recording = new AtomicBoolean(false);

    public synchronized void startRecording() throws Exception {
        if (recording.get()) return;
        buf.reset();
        String want = com.example.edithglasses.EdithConfig.get().micDevice;
        line = null;
        if (want != null && !want.isBlank()) {
            for (javax.sound.sampled.Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (!mi.getName().toLowerCase().contains(want.toLowerCase())) continue;
                javax.sound.sampled.Mixer mx = AudioSystem.getMixer(mi);
                if (mx.isLineSupported(new DataLine.Info(TargetDataLine.class, MIC))) {
                    line = (TargetDataLine) mx.getLine(new DataLine.Info(TargetDataLine.class, MIC));
                    break;
                }
            }
        }
        if (line == null) line = AudioSystem.getTargetDataLine(MIC);
        line.open(MIC);
        line.start();
        recording.set(true);
        TargetDataLine l = line;
        thread = new Thread(() -> {
            byte[] chunk = new byte[3200];
            while (recording.get() && buf.size() < MAX_BYTES) {
                int n = l.read(chunk, 0, chunk.length);
                if (n > 0) synchronized (buf) { buf.write(chunk, 0, n); }
            }
        }, "edith-mic");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops recording and returns a WAV file, or null if nothing useful was captured. */
    public synchronized byte[] stopRecording() {
        if (!recording.getAndSet(false)) return null;
        try {
            thread.join(500);
        } catch (InterruptedException ignored) {
        }
        line.stop();
        line.close();
        byte[] pcm;
        synchronized (buf) { pcm = buf.toByteArray(); }
        if (pcm.length < 16000 * 2 * 0.4) return null; // shorter than 0.4 s: ignore
        double sum = 0;
        int n = pcm.length / 2;
        for (int i = 0; i < n; i++) {
            short v = (short) ((pcm[2 * i + 1] << 8) | (pcm[2 * i] & 0xff));
            sum += (double) v * v;
        }
        lastRms = Math.sqrt(sum / n) / 32768.0;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), MIC, pcm.length / 2),
                javax.sound.sampled.AudioFileFormat.Type.WAVE, out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Loudness of the last recording, 0..1 (speech is usually above 0.01). */
    public static volatile double lastRms = 0;

    public static java.util.List<String> micNames() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (javax.sound.sampled.Mixer.Info mi : AudioSystem.getMixerInfo()) {
            if (AudioSystem.getMixer(mi).isLineSupported(new DataLine.Info(TargetDataLine.class, MIC))) out.add(mi.getName());
        }
        return out;
    }

    public boolean isRecording() {
        return recording.get();
    }

    // ------------------------------------------------------------------ playback

    private static volatile int speechGen = 0;
    private static volatile SourceDataLine activeLine;
    private static volatile Process sysProc;

    public static int speechGen() {
        return speechGen;
    }

    /** Cuts off whatever EDITH is saying right now and drops everything queued behind it. */
    public static void stopSpeaking() {
        speechGen++;
        SourceDataLine l = activeLine;
        if (l != null) {
            try {
                l.stop();
                l.flush();
            } catch (Exception ignored) {
            }
        }
        Process p = sysProc;
        if (p != null) p.destroyForcibly();
    }

    public static void play(Providers.Speech s) throws Exception {
        final int my = speechGen;
        AudioInputStream in;
        if (s.wav()) {
            in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(s.data()));
        } else {
            AudioFormat f = new AudioFormat(s.rate() > 0 ? s.rate() : 24000, 16, 1, true, false);
            in = new AudioInputStream(new ByteArrayInputStream(s.data()), f, s.data().length / 2);
        }
        in = filmVoice(in);
        in = sheen(in);
        AudioFormat fmt = in.getFormat();
        SourceDataLine out = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
        out.open(fmt);
        activeLine = out;
        out.start();
        byte[] chunk = new byte[2048];
        int n;
        while ((n = in.read(chunk)) > 0) {
            if (speechGen != my) break;
            out.write(chunk, 0, n);
        }
        if (speechGen == my) out.drain();
        activeLine = null;
        out.close();
    }

    /** Shapes the voice with the configured ffmpeg filter chain (pitch and tone fitted to the film). Falls back to the plain voice. */
    private static AudioInputStream filmVoice(AudioInputStream in) {
        String chain = com.example.edithglasses.EdithConfig.get().voiceFilter;
        String ffmpeg = chain == null || chain.isBlank() ? null : Tools.find("ffmpeg");
        if (ffmpeg == null) return in;
        java.nio.file.Path src = null, dst = null;
        try {
            src = java.nio.file.Files.createTempFile("edith_in", ".wav");
            dst = java.nio.file.Files.createTempFile("edith_out", ".wav");
            try (java.io.OutputStream o = java.nio.file.Files.newOutputStream(src)) {
                AudioSystem.write(in, javax.sound.sampled.AudioFileFormat.Type.WAVE, o);
            }
            Process p = new ProcessBuilder(ffmpeg, "-v", "error", "-y", "-i", src.toString(), "-af", "aresample=24000," + chain,
                "-ac", "1", "-ar", "24000", dst.toString()).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0) return rewind(src);
            byte[] bytes = java.nio.file.Files.readAllBytes(dst);
            return AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            try { return src == null ? in : rewind(src); } catch (Exception ex) { return in; }
        } finally {
            try { if (src != null) java.nio.file.Files.deleteIfExists(src); } catch (Exception ignored) { }
            try { if (dst != null) java.nio.file.Files.deleteIfExists(dst); } catch (Exception ignored) { }
        }
    }

    private static AudioInputStream rewind(java.nio.file.Path wav) throws Exception {
        return AudioSystem.getAudioInputStream(new ByteArrayInputStream(java.nio.file.Files.readAllBytes(wav)));
    }

    /** Adds a very short echo (comb filter) so the voice has a faint digital shimmer. Only for 16-bit PCM. */
    private static AudioInputStream sheen(AudioInputStream in) {
        try {
            if (!com.example.edithglasses.EdithConfig.get().voiceEffect) return in;
            AudioFormat f = in.getFormat();
            if (f.getSampleSizeInBits() != 16 || f.isBigEndian() || f.getChannels() != 1) return in;
            byte[] b = in.readAllBytes();
            int n = b.length / 2, d = Math.max(1, (int) (f.getSampleRate() * 0.011));
            short[] x = new short[n];
            for (int i = 0; i < n; i++) x[i] = (short) ((b[2 * i + 1] << 8) | (b[2 * i] & 0xff));
            byte[] o = new byte[b.length];
            for (int i = 0; i < n; i++) {
                double v = x[i] * 0.85 + (i >= d ? x[i - d] * 0.24 : 0) + (i >= 2 * d ? x[i - 2 * d] * 0.08 : 0);
                int s = (int) Math.max(-32768, Math.min(32767, v));
                o[2 * i] = (byte) s;
                o[2 * i + 1] = (byte) (s >> 8);
            }
            return new AudioInputStream(new ByteArrayInputStream(o), f, n);
        } catch (Exception e) {
            return in;
        }
    }

    /** Windows built-in voice, used when no cloud voice is available. */
    public static void systemSpeak(String text) {
        text = Providers.spoken(text);
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak($env:EDITH_TEXT)");
            pb.environment().put("EDITH_TEXT", text);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            sysProc = p;
            p.getInputStream().readAllBytes();
            p.waitFor();
            sysProc = null;
        } catch (Exception ignored) {
        }
    }
}
