import os
j = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/")


def rw(p, f):
    s = open(j + p, encoding="utf-8").read()
    s = f(s)
    open(j + p, "w", encoding="utf-8").write(s)


def rep(s, a, b):
    assert a in s, "missing: " + a[:80]
    return s.replace(a, b, 1)


# ---------------------------------------------------------------- audio: speech can be stopped
def audio(s):
    s = rep(s, "    public static void play(Providers.Speech s) throws Exception {",
            '''    private static volatile int speechGen = 0;
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
        final int my = speechGen;''')
    s = rep(s, '''        out.open(fmt);
        out.start();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) out.write(chunk, 0, n);
        out.drain();
        out.close();''', '''        out.open(fmt);
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
        out.close();''')
    s = rep(s, '''            Process p = pb.start();
            p.getInputStream().readAllBytes();
            p.waitFor();''', '''            Process p = pb.start();
            sysProc = p;
            p.getInputStream().readAllBytes();
            p.waitFor();
            sysProc = null;''')
    return s


rw("ai/Audio.java", audio)


# ---------------------------------------------------------------- assistant
def assistant(s):
    # speech on its own thread
    s = rep(s, "    private static final List<Providers.Turn> history = new ArrayList<>();",
            '''    private static final ExecutorService SPEAK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edith-speech");
        t.setDaemon(true);
        return t;
    });
    private static final java.util.concurrent.atomic.AtomicInteger speaking = new java.util.concurrent.atomic.AtomicInteger();
    private static final List<Providers.Turn> history = new ArrayList<>();''')
    s = rep(s, '''        state = State.SPEAKING;
        try {
            Providers.Speech s = Providers.speak(text);
            if (s != null) Audio.play(s);
            else Audio.systemSpeak(text);
        } catch (Exception e) {
            Audio.systemSpeak(text);
        }
        state = State.IDLE;
    }''', '''        state = State.SPEAKING;
        final int g = Audio.speechGen();
        final String spoken = forSpeech(text);
        speaking.incrementAndGet();
        SPEAK.submit(() -> {
            try {
                if (Audio.speechGen() != g) return;
                Providers.Speech s = Providers.speak(spoken);
                if (Audio.speechGen() != g) return;
                if (s != null) Audio.play(s);
                else Audio.systemSpeak(spoken);
            } catch (Exception e) {
                if (Audio.speechGen() == g) Audio.systemSpeak(spoken);
            } finally {
                if (speaking.decrementAndGet() <= 0 && state == State.SPEAKING) state = State.IDLE;
            }
        });
    }

    /** What is said out loud: the first two sentences (the full text stays on screen and in chat). */
    private static String forSpeech(String text) {
        String t = text.trim();
        if (t.length() <= 170) return t;
        int sentences = 0, cut = -1;
        for (int i = 0; i < t.length() - 1; i++) {
            char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && t.charAt(i + 1) == ' ' && ++sentences == 2) {
                cut = i + 1;
                break;
            }
        }
        if (cut < 0 || cut > 240) {
            cut = t.lastIndexOf(' ', 200);
            if (cut < 0) cut = Math.min(t.length(), 200);
        }
        return t.substring(0, cut).trim();
    }

    /** Stops EDITH talking right now (push-to-talk, the N key and "stop" do this too). */
    public static void stopSpeaking() {
        Audio.stopSpeaking();
        if (state == State.SPEAKING) state = State.IDLE;
    }''')
    # PTT interrupts speech
    s = rep(s, "        if (down && !wasDown && state == State.IDLE) {\n            try {",
            "        if (down && !wasDown && state == State.SPEAKING) stopSpeaking();\n        if (down && !wasDown && state == State.IDLE) {\n            try {")
    s = rep(s, "    public static void cancel(Minecraft mc) {\n        pending = null;",
            "    public static void cancel(Minecraft mc) {\n        pending = null;\n        Audio.stopSpeaking();")
    # quick intents before the AI
    s = rep(s, '''            String user = "GAME STATE:\\n"''', '''            String low = text.toLowerCase(Locale.ROOT).trim().replaceAll("[.!?,]+$", "");
            if (low.matches("^(hey |ok |okay )?(edith[, ]+)?(stop|quiet|silence|shut up|enough|skip|be quiet|that's enough|never mind|nevermind)( talking)?$")) {
                stopSpeaking();
                state = State.IDLE;
                return;
            }
            if (low.matches(".*\\\\b(analy[sz]e|scan (it|that|him|her|them|this)|what('s| is) that|what am i looking at|tell me about (it|that|this))\\\\b.*")) {
                net.minecraft.world.entity.Entity at = aimed != null && aimed.isAlive() ? aimed : Glasses.target();
                say(mc, com.example.edithglasses.feature.Analyse.speech(mc, at));
                return;
            }
            String user = "GAME STATE:\\n"''')
    # strike by name
    s = rep(s, '''         "forget": null or "a keyword: saved notes that contain it are deleted"}''',
            '''         "forget": null or "a keyword: saved notes that contain it are deleted",
         "target": null or "the mob the user names as the strike target when they say its name, for example wither"}''')
    s = rep(s, '''        - Only set "chat" when''', '''        - When the user names the mob to strike ("target the wither"), set "target" to that name. EDITH finds the nearest one even if the user is not looking at it.
        - Only set "chat" when''')
    s = rep(s, "String wpAction = null, wpName = null, remember = null, forget = null;", "String wpAction = null, wpName = null, remember = null, forget = null, targetName = null;")
    s = rep(s, '                forget = str(o, "forget");', '                forget = str(o, "forget");\n                targetName = str(o, "target");')
    s = rep(s, '''                net.minecraft.world.entity.Entity t = aimed != null && aimed.isAlive() ? aimed : Glasses.target();
                if (t == null) {
                    say = "I don't have a target in sight.";''', '''                net.minecraft.world.entity.Entity named = targetName != null && !targetName.isBlank()
                    ? com.example.edithglasses.feature.Analyse.findNamed(mc, targetName) : null;
                net.minecraft.world.entity.Entity t = named != null ? named : aimed != null && aimed.isAlive() ? aimed : Glasses.target();
                if (t == null) {
                    say = targetName != null && !targetName.isBlank() ? "I can't find a " + targetName + " nearby." : "I don't have a target in sight.";''')
    return s


rw("ai/Assistant.java", assistant)


# ---------------------------------------------------------------- N key stops talking when nothing is pending
rw("EdithGlassesClient.java", lambda s: rep(s,
    "while (cancelKey.consumeClick()) if (Assistant.hasPending()) Assistant.cancel(mc);",
    "while (cancelKey.consumeClick()) { if (Assistant.hasPending()) Assistant.cancel(mc); else Assistant.stopSpeaking(); }"))


def client_cmd(s):
    return rep(s, '                .then(ClientCommands.literal("intro")',
               '                .then(ClientCommands.literal("analyse").executes(c -> { Minecraft mc = Minecraft.getInstance(); say(com.example.edithglasses.feature.Analyse.speech(mc, com.example.edithglasses.Glasses.target())); return 1; }))\n'
               '                .then(ClientCommands.literal("stop").executes(c -> { Assistant.stopSpeaking(); return 1; }))\n'
               '                .then(ClientCommands.literal("intro")')


rw("EdithGlassesClient.java", client_cmd)


# ---------------------------------------------------------------- GameContext: analysis of what you look at
rw("ai/GameContext.java", lambda s: rep(s,
    '''            sb.append("Looking at entity: ").append(e.getEntity().getName().getString()).append('\\n');''',
    '''            sb.append("Looking at entity: ").append(e.getEntity().getName().getString()).append('\\n');
            sb.append("Analysis of it: ").append(com.example.edithglasses.feature.Analyse.speech(mc, e.getEntity())).append('\\n');'''))


# ---------------------------------------------------------------- boss alerts
def alerts(s):
    return rep(s, "        if (now - lastVitals > 30_000) {", '''        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(60))) {
            var ty = e.getType();
            if (!e.isAlive() || !(ty == EntityTypes.WITHER || ty == EntityTypes.ENDER_DRAGON || ty == EntityTypes.WARDEN || ty == EntityTypes.ELDER_GUARDIAN)) continue;
            Long last = SEEN.get(e.getId() + 1_000_000);
            if (last != null && now - last < 45_000) continue;
            SEEN.put(e.getId() + 1_000_000, now);
            String nm = e.getName().getString();
            push(mc, "BOSS: " + nm.toUpperCase() + "  " + Math.round(p.distanceTo(e)) + " m", 0xFFFF5050, nm + " nearby.");
        }

        if (now - lastVitals > 30_000) {''')


rw("feature/Alerts.java", alerts)


# ---------------------------------------------------------------- HUD: threat line on the target card
def hud(s):
    s = rep(s, "int cw = 104, ch = 50,", "int cw = 104, ch = 60,")
    s = rep(s, '''            text(g, font, "HP " + Math.round(le.getHealth()) + "/" + Math.round(le.getMaxHealth()), x + 4, y + 41, DIM, 0.55f);
        }''', '''            text(g, font, "HP " + Math.round(le.getHealth()) + "/" + Math.round(le.getMaxHealth()), x + 4, y + 41, DIM, 0.55f);
            int th = com.example.edithglasses.feature.Analyse.threat(e, p);
            String bar = "THREAT " + "#".repeat(th) + "-".repeat(5 - th);
            text(g, font, th == 0 ? "THREAT NONE" : bar, x + 4, y + 50, th >= 4 ? RED : th >= 3 ? AMBER : GREEN, 0.6f);
        }''')
    return s


rw("EdithHud.java", hud)
rw("HudLayout.java", lambda s: rep(s, "case TARGET -> new int[]{m + 6, h - m - 110, 104, 50};", "case TARGET -> new int[]{m + 6, h - m - 120, 104, 60};"))
print("patched")
