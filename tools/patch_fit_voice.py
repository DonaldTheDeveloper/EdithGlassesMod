import os, re
base = os.path.expanduser("~/EdithGlassesMod")
j = base + "/src/main/java/com/example/edithglasses/"


def edit(path, pairs):
    s = open(path, encoding="utf-8").read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit("missing anchor in %s: %s" % (path, a[:70]))
        s = s.replace(a, b, 1)
    open(path, "w", encoding="utf-8").write(s)


# ---- 1. EDITH must not start strikes by herself
edit(j + "ai/Assistant.java", [
    ("private record Pending(String kind, String text, java.util.UUID id) {",
     "private static final java.util.regex.Pattern STRIKE_WORDS = java.util.regex.Pattern.compile(\n"
     "        \"(?i).*\\\\b(target|drone|strike|take (him|her|it|them) out|eliminate|kill|destroy|attack|neutrali[sz]e|fire at|engage)\\\\b.*\");\n"
     "    private static final long PENDING_MS = 20_000;\n\n"
     "    private record Pending(String kind, String text, java.util.UUID id, long created) {"),
    ("    public static boolean hasPending() {\n        return pending != null;\n    }",
     "    private static Pending live() {\n        Pending p = pending;\n        if (p != null && System.currentTimeMillis() - p.created() > PENDING_MS) {\n            pending = null;\n            return null;\n        }\n        return p;\n    }\n\n"
     "    public static boolean hasPending() {\n        return live() != null;\n    }"),
    ("    public static String pendingText() {\n        Pending p = pending;", "    public static String pendingText() {\n        Pending p = live();"),
    ("            Pending p = pending;\n            if (p != null) {\n                String t = text.toLowerCase(Locale.ROOT);",
     "            Pending p = live();\n            if (p != null) {\n                String t = text.toLowerCase(Locale.ROOT);"),
    ("pending = new Pending(\"strike\", t.getName().getString(), t.getUUID());",
     "pending = new Pending(\"strike\", t.getName().getString(), t.getUUID(), System.currentTimeMillis());"),
    ("pending = new Pending(\"command\", command, null);", "pending = new Pending(\"command\", command, null, System.currentTimeMillis());"),
    ("pending = new Pending(\"chat\", chat, null);", "pending = new Pending(\"chat\", chat, null, System.currentTimeMillis());"),
    ("    public static void confirm(Minecraft mc) {\n        Pending p = pending;", "    public static void confirm(Minecraft mc) {\n        Pending p = live();"),
    ("            if (wpAction != null) {",
     "            // never start a strike unless the words really asked for one (speech-to-text noise must not arm it)\n"
     "            if (strike && !STRIKE_WORDS.matcher(text).matches()) strike = false;\n"
     "            if (wpAction != null) {"),
])

# ---- 2. the glasses sit on the face layer of any skin: lower, and in front of the 3D outer head layer
open(base + "/tools/_head_fix.txt", "w").write("done")
p = base + "/tools/gen_model_from_glb.py"
s = open(p, encoding="utf-8").read()
if "worn_display[\"head\"]" not in s:
    s = s.replace("worn_display = old[\"display\"]\n",
                  "worn_display = old[\"display\"]\nworn_display[\"head\"] = {\"rotation\": [0, 0, 0], \"translation\": [0, -2.2, -7.3], \"scale\": [1, 1, 1]}  # eyes sit ~5px down; stay in front of the hat layer\n", 1)
    s = s.replace("    \"head\": worn_display[\"head\"],\n}", "    \"head\": worn_display[\"head\"],\n}", 1)
    open(p, "w", encoding="utf-8").write(s)

# ---- 3. voice: clearer default voice + subtle "digital sheen"
edit(j + "EdithConfig.java", [
    ('public String geminiVoice = "Kore";', 'public String geminiVoice = "Erinome";\n    /** A light digital shimmer on EDITH\'s voice (short echo), like an AI assistant. */\n    public boolean voiceEffect = true;'),
])
edit(j + "ai/Audio.java", [
    ("        AudioFormat fmt = in.getFormat();\n        SourceDataLine out",
     "        in = sheen(in);\n        AudioFormat fmt = in.getFormat();\n        SourceDataLine out"),
    ("    /** Windows built-in voice, used when no cloud voice is available. */",
     "    /** Adds a very short echo (comb filter) so the voice has a faint digital shimmer. Only for 16-bit PCM. */\n"
     "    private static AudioInputStream sheen(AudioInputStream in) {\n"
     "        try {\n"
     "            if (!com.example.edithglasses.EdithConfig.get().voiceEffect) return in;\n"
     "            AudioFormat f = in.getFormat();\n"
     "            if (f.getSampleSizeInBits() != 16 || f.isBigEndian() || f.getChannels() != 1) return in;\n"
     "            byte[] b = in.readAllBytes();\n"
     "            int n = b.length / 2, d = Math.max(1, (int) (f.getSampleRate() * 0.011));\n"
     "            short[] x = new short[n];\n"
     "            for (int i = 0; i < n; i++) x[i] = (short) ((b[2 * i + 1] << 8) | (b[2 * i] & 0xff));\n"
     "            byte[] o = new byte[b.length];\n"
     "            for (int i = 0; i < n; i++) {\n"
     "                double v = x[i] * 0.85 + (i >= d ? x[i - d] * 0.24 : 0) + (i >= 2 * d ? x[i - 2 * d] * 0.08 : 0);\n"
     "                int s = (int) Math.max(-32768, Math.min(32767, v));\n"
     "                o[2 * i] = (byte) s;\n"
     "                o[2 * i + 1] = (byte) (s >> 8);\n"
     "            }\n"
     "            return new AudioInputStream(new ByteArrayInputStream(o), f, n);\n"
     "        } catch (Exception e) {\n"
     "            return in;\n"
     "        }\n"
     "    }\n\n"
     "    /** Windows built-in voice, used when no cloud voice is available. */"),
])
edit(j + "EdithGlassesClient.java", [
    ('                .then(ClientCommands.literal("models")',
     '                .then(ClientCommands.literal("voicename").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {\n'
     '                    EdithConfig.get().geminiVoice = StringArgumentType.getString(c, "name");\n'
     '                    EdithConfig.save();\n'
     '                    say("EDITH voice set to " + EdithConfig.get().geminiVoice + " (try Erinome, Kore, Aoede, Leda, Sulafat, Vindemiatrix, Zephyr)");\n'
     '                    return 1;\n'
     '                })))\n'
     '                .then(ClientCommands.literal("voicefx")\n'
     '                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().voiceEffect = true; EdithConfig.save(); say("Voice shimmer ON"); return 1; }))\n'
     '                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().voiceEffect = false; EdithConfig.save(); say("Voice shimmer OFF"); return 1; })))\n'
     '                .then(ClientCommands.literal("models")'),
])
print("patched")
