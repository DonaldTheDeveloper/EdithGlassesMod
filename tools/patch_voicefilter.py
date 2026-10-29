import os
base = os.path.expanduser("~/EdithGlassesMod")
j = base + "/src/main/java/com/example/edithglasses/"


def edit(path, pairs):
    s = open(path, encoding="utf-8").read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit("missing anchor in %s: %s" % (path, a[:70]))
        s = s.replace(a, b, 1)
    open(path, "w", encoding="utf-8").write(s)


FILTER = ("highpass=f=110,equalizer=f=280:t=q:w=1.0:g=-6.0,equalizer=f=650:t=q:w=1.0:g=3.6,equalizer=f=1400:t=q:w=0.8:g=5.4,"
          "equalizer=f=3000:t=q:w=0.8:g=-0.1,equalizer=f=5500:t=q:w=0.8:g=-1.5,"
          "acompressor=threshold=-20dB:ratio=3:attack=5:release=80:makeup=2,asetrate=23259,aresample=24000,atempo=1.0318")

edit(j + "EdithConfig.java", [
    ('public String geminiVoice = "Erinome";', 'public String geminiVoice = "Kore";'),
    ("public boolean voiceEffect = true;", "public boolean voiceEffect = false;\n"
     "    /** ffmpeg audio filter that shapes the voice towards the film EDITH (fitted by tools/voice_fit.py). Empty = off. Needs ffmpeg. */\n"
     '    public String voiceFilter = "' + FILTER + '";'),
])

edit(j + "ai/Audio.java", [
    ("        in = sheen(in);\n", "        in = filmVoice(in);\n        in = sheen(in);\n"),
    ("    /** Adds a very short echo (comb filter)",
     "    /** Shapes the voice with the configured ffmpeg filter chain (pitch and tone fitted to the film). Falls back to the plain voice. */\n"
     "    private static AudioInputStream filmVoice(AudioInputStream in) {\n"
     "        String chain = com.example.edithglasses.EdithConfig.get().voiceFilter;\n"
     "        String ffmpeg = chain == null || chain.isBlank() ? null : Tools.find(\"ffmpeg\");\n"
     "        if (ffmpeg == null) return in;\n"
     "        java.nio.file.Path src = null, dst = null;\n"
     "        try {\n"
     "            src = java.nio.file.Files.createTempFile(\"edith_in\", \".wav\");\n"
     "            dst = java.nio.file.Files.createTempFile(\"edith_out\", \".wav\");\n"
     "            try (java.io.OutputStream o = java.nio.file.Files.newOutputStream(src)) {\n"
     "                AudioSystem.write(in, javax.sound.sampled.AudioFileFormat.Type.WAVE, o);\n"
     "            }\n"
     "            Process p = new ProcessBuilder(ffmpeg, \"-v\", \"error\", \"-y\", \"-i\", src.toString(), \"-af\", \"aresample=24000,\" + chain,\n"
     "                \"-ac\", \"1\", \"-ar\", \"24000\", dst.toString()).redirectErrorStream(true).start();\n"
     "            p.getInputStream().readAllBytes();\n"
     "            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0) return rewind(src);\n"
     "            byte[] bytes = java.nio.file.Files.readAllBytes(dst);\n"
     "            return AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));\n"
     "        } catch (Exception e) {\n"
     "            try { return src == null ? in : rewind(src); } catch (Exception ex) { return in; }\n"
     "        } finally {\n"
     "            try { if (src != null) java.nio.file.Files.deleteIfExists(src); } catch (Exception ignored) { }\n"
     "            try { if (dst != null) java.nio.file.Files.deleteIfExists(dst); } catch (Exception ignored) { }\n"
     "        }\n"
     "    }\n\n"
     "    private static AudioInputStream rewind(java.nio.file.Path wav) throws Exception {\n"
     "        return AudioSystem.getAudioInputStream(new ByteArrayInputStream(java.nio.file.Files.readAllBytes(wav)));\n"
     "    }\n\n"
     "    /** Adds a very short echo (comb filter)"),
])

edit(j + "EdithGlassesClient.java", [
    ('                .then(ClientCommands.literal("voicefx")',
     '                .then(ClientCommands.literal("voicefilter")\n'
     '                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().voiceFilter = ""; EdithConfig.save(); say("Film voice filter OFF (plain voice)"); return 1; }))\n'
     '                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().voiceFilter = new EdithConfig().voiceFilter; EdithConfig.save(); say("Film voice filter ON"); return 1; })))\n'
     '                .then(ClientCommands.literal("voicefx")'),
])
print("patched")
