package com.example.edithglasses;

import com.example.chattranslator.SettingsScreen;
import com.example.edithglasses.feature.AutoHeal;
import com.example.edithglasses.feature.Duel;
import com.example.edithglasses.feature.Photo;
import com.example.edithglasses.feature.Recon;
import com.example.edithglasses.feature.Tracker;
import com.example.edithglasses.feature.Waypoints;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static com.example.edithglasses.feature.HudStyle.*;

/** The EDITH control panel: a holographic menu with categories, switches, sliders and action buttons. */
public class EdithMenuScreen extends Screen {
    private enum Kind { TOGGLE, CYCLE, SLIDER, ACTION, INFO }

    private static final class Ctl {
        final Kind kind;
        final String label, desc;
        Supplier<String> value = () -> "";
        Runnable click = () -> { };
        Runnable back = null;
        java.util.function.BooleanSupplier on = () -> false;
        DoubleSupplier get;
        DoubleConsumer set;
        double min, max, step;
        float anim = -1f;
        // layout of the last frame
        int x, y, w;

        Ctl(Kind kind, String label, String desc) {
            this.kind = kind;
            this.label = label;
            this.desc = desc;
        }
    }

    private static final String[] CATS = {"VISION", "PROTECTION", "TOOLS", "VOICE", "AI", "SYSTEM"};
    private static final String[] HUD_MODES = {"always", "zoom", "off"};
    private static final int[] AFK = {0, 15, 30, 60, 120};
    private static final String[][] VOICE_INFO = {
        {"Kore", "firm"}, {"Sulafat", "warm"}, {"Aoede", "breezy"}, {"Leda", "youthful"}, {"Erinome", "clear"}, {"Zephyr", "bright"},
        {"Achernar", "soft"}, {"Despina", "smooth"}, {"Callirrhoe", "easy-going"}, {"Autonoe", "bright"}, {"Gacrux", "mature"},
        {"Vindemiatrix", "gentle"}, {"Laomedeia", "upbeat"}, {"Pulcherrima", "forward"}, {"Achird", "friendly"}, {"Schedar", "even"},
        {"Puck", "upbeat, male"}, {"Charon", "informative, male"}, {"Fenrir", "excitable, male"}, {"Orus", "firm, male"},
        {"Enceladus", "breathy, male"}, {"Iapetus", "clear, male"}, {"Umbriel", "easy-going, male"}, {"Algieba", "smooth, male"},
        {"Algenib", "gravelly, male"}, {"Rasalgethi", "informative, male"}, {"Alnilam", "firm, male"}, {"Zubenelgenubi", "casual, male"},
        {"Sadachbia", "lively, male"}, {"Sadaltager", "knowledgeable, male"}};
    private static final String[] VOICES = java.util.Arrays.stream(VOICE_INFO).map(v -> v[0]).toArray(String[]::new);

    private final Screen parent;
    private final List<List<Ctl>> pages = new ArrayList<>();
    private int cat = 0;
    private int scroll = 0;
    private Ctl dragging;
    private long opened = System.currentTimeMillis();
    private int px, py, pw, ph, cx0, cy0, cw, ch;

    public EdithMenuScreen(Screen parent) {
        super(Component.literal("E.D.I.T.H. Glasses"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ building the pages

    private static Ctl toggle(String label, String desc, java.util.function.BooleanSupplier get, Runnable flip) {
        Ctl c = new Ctl(Kind.TOGGLE, label, desc);
        c.on = get;
        c.click = flip;
        return c;
    }

    private static Ctl cycle(String label, String desc, Supplier<String> value, Runnable next, Runnable prev) {
        Ctl c = new Ctl(Kind.CYCLE, label, desc);
        c.value = value;
        c.click = next;
        c.back = prev;
        return c;
    }

    private static Ctl slider(String label, String desc, double min, double max, double step, DoubleSupplier get, DoubleConsumer set, java.util.function.DoubleFunction<String> fmt) {
        Ctl c = new Ctl(Kind.SLIDER, label, desc);
        c.min = min;
        c.max = max;
        c.step = step;
        c.get = get;
        c.set = set;
        c.value = () -> fmt.apply(get.getAsDouble());
        return c;
    }

    private static Ctl action(String label, String desc, String button, Runnable run) {
        Ctl c = new Ctl(Kind.ACTION, label, desc);
        c.value = () -> button;
        c.click = run;
        return c;
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return 0;
    }

    private static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equalsIgnoreCase(v)) return i;
        return 0;
    }

    private void build() {
        pages.clear();
        EdithConfig c = EdithConfig.get();

        List<Ctl> vision = new ArrayList<>();
        vision.add(cycle("HUD", "When the EDITH heads-up display shows: always, only while zoomed or holding Shift, or never.",
            () -> c.hudMode.toUpperCase(),
            () -> c.hudMode = HUD_MODES[(indexOf(HUD_MODES, c.hudMode) + 1) % HUD_MODES.length],
            () -> c.hudMode = HUD_MODES[(indexOf(HUD_MODES, c.hudMode) + HUD_MODES.length - 1) % HUD_MODES.length]));
        vision.add(toggle("Hide normal hotbar & hearts", "While the EDITH HUD shows, EDITH draws its own hotbar, health, armor and food instead of Minecraft's.",
            () -> c.hideVanillaHud, () -> c.hideVanillaHud = !c.hideVanillaHud));
        vision.add(toggle("Night vision", "Full brightness everywhere while the glasses are on.", () -> c.nightVision, () -> c.nightVision = !c.nightVision));
        vision.add(toggle("Thermal vision", "Living things glow through walls. Many servers forbid this.", () -> c.thermal, () -> c.thermal = !c.thermal));
        vision.add(toggle("X-ray (ores)", "Ores are marked on screen through walls. Many servers ban this: use single player.", () -> c.xray, () -> c.xray = !c.xray));
        vision.add(slider("X-ray range", "How many blocks around you are scanned for ores.", 8, 32, 2, () -> c.xrayRadius, v -> c.xrayRadius = (int) v, v -> (int) v + " blocks"));
        vision.add(slider("Target lock range", "How far the HUD can lock onto a mob or player you look at.", 32, 192, 16, () -> c.scanRange, v -> c.scanRange = v, v -> (int) v + " blocks"));
        vision.add(toggle("Hologram map", "A small 3D terrain model of your surroundings on the HUD. It turns with you and shows players, mobs and caves.", () -> c.holoMap, () -> c.holoMap = !c.holoMap));
        vision.add(slider("Map radius", "How many blocks around you the hologram map covers.", 8, 28, 2, () -> c.holoRadius, v -> c.holoRadius = (int) v, v -> (int) v + " blocks"));
        vision.add(toggle("Facial recognition", "Scan boxes on players in view. After a short scan EDITH tags them FRIEND or UNKNOWN with distance, health and what they hold.", () -> c.faceId, () -> c.faceId = !c.faceId));
        vision.add(toggle("Boot animation", "The film-style start-up sequence when you put the glasses on.", () -> c.intro, () -> c.intro = !c.intro));
        vision.add(toggle("Threat alerts", "Banner and chime when hostile mobs, creepers or low health need attention.", () -> c.alerts, () -> c.alerts = !c.alerts));
        vision.add(toggle("Translation subtitles", "Chat Translator output shown as subtitles on the HUD.", () -> c.subtitles, () -> c.subtitles = !c.subtitles));
        pages.add(vision);

        List<Ctl> prot = new ArrayList<>();
        prot.add(toggle("Sentinel (run away)", "Runs you away from players with weapons, a mace or wind charges, and from arrows. Many servers treat this as cheating.",
            () -> c.guard, () -> c.guard = !c.guard));
        prot.add(cycle("Sentinel active", "Always while the glasses are on, or only after you have been away from the keyboard for a while.",
            () -> c.guardAfkSeconds == 0 ? "ALWAYS" : "AFTER " + c.guardAfkSeconds + "s",
            () -> c.guardAfkSeconds = AFK[(indexOf(AFK, c.guardAfkSeconds) + 1) % AFK.length],
            () -> c.guardAfkSeconds = AFK[(indexOf(AFK, c.guardAfkSeconds) + AFK.length - 1) % AFK.length]));
        prot.add(toggle("Water bucket clutch", "When a fall would hurt, EDITH places water just before you land and picks it up again. Takes the bucket from your inventory if it is not in the hotbar.",
            () -> c.clutch, () -> c.clutch = !c.clutch));
        prot.add(toggle("Fight hostile mobs", "Defends against mobs that reach you: aims, strafes, hits criticals, blocks with a shield. Never attacks players.",
            () -> c.guardFight, () -> c.guardFight = !c.guardFight));
        prot.add(toggle("Auto-heal", "Uses splash healing, golden apples or healing potions from your hotbar when health is low, and moves a totem to your off hand.",
            () -> c.autoHeal, () -> c.autoHeal = !c.autoHeal));
        prot.add(slider("Heal at", "Health at which auto-heal starts.", 1, 10, 0.5, () -> c.autoHealAt / 2.0, v -> c.autoHealAt = (float) (v * 2), v -> String.format("%.1f hearts", v)));
        prot.add(action("Heal now", "Heal right now with whatever healing item is in your hotbar.", "HEAL", () -> {
            onClose();
            AutoHeal.healNow(minecraft);
        }));
        pages.add(prot);

        List<Ctl> tools = new ArrayList<>();
        tools.add(action("Move HUD parts", "Drag the radar, map, hotbar, tabs and other HUD parts to where you want them.", "EDIT", () -> minecraft.gui.setScreen(new HudEditScreen(this))));
        tools.add(action("Tag a player", "Give a player your own identification tag such as Law Leader or Enemy. Facial recognition then shows it instead of UNKNOWN. " + c.playerTags.size() + " tagged.", "TAG", () -> minecraft.gui.setScreen(new TagScreen(this))));
        tools.add(action("Memory", com.example.edithglasses.feature.Memory.noteCount() + " notes and " + com.example.edithglasses.feature.Memory.boxCount() + " containers remembered. Ask EDITH where you put something. Click to forget everything.", "FORGET ALL", () -> com.example.edithglasses.feature.Memory.forget("all")));
        tools.add(action("Build starter house", "Shows a hologram that follows your crosshair. Press Y to let EDITH build it (5x5, one room).", "PREVIEW", () -> { onClose(); com.example.edithglasses.feature.Builder.preview(minecraft, "starter", ""); }));
        tools.add(action("Build normal house", "7x9 house with pillars, windows, a two step roof and furniture.", "PREVIEW", () -> { onClose(); com.example.edithglasses.feature.Builder.preview(minecraft, "normal", ""); }));
        tools.add(action("Build luxury house", "11x13 house with two rooms, big windows, skylights, lanterns and furniture. Needs lots of blocks in survival.", "PREVIEW", () -> { onClose(); com.example.edithglasses.feature.Builder.preview(minecraft, "luxury", ""); }));
        tools.add(action("Stop building", "Cancels the hologram or stops EDITH while she builds.", "STOP", () -> com.example.edithglasses.feature.Builder.cancel(minecraft, "stopped from the menu")));
        tools.add(action("Add friend", "Adds the player you are looking at to your friends list (recognition tags them FRIEND and the Sentinel ignores them).", "ADD", () -> {
            if (com.example.edithglasses.Glasses.target() instanceof net.minecraft.world.entity.player.Player pl) com.example.edithglasses.feature.FaceId.add(pl.getName().getString());
        }));
        tools.add(action("Play boot animation", "Plays the start-up sequence now.", "PLAY", () -> { onClose(); com.example.edithglasses.feature.Intro.play(minecraft); }));
        tools.add(action("Camera mode", "Advanced camera: viewfinder, looks (cinematic, noir, vintage, dream, miniature, neon), exposure, frames, timer, burst, 360 panorama and a free camera. Key: C.", "OPEN", () -> { onClose(); com.example.edithglasses.feature.CameraMode.toggle(minecraft); }));
        tools.add(action("Photo", "Take a screenshot with the EDITH recording overlay.", "CAPTURE", () -> {
            onClose();
            Photo.requestAfter(8);
        }));
        tools.add(action("Recon drone", "Your view flies forward for up to 20 seconds, then snaps back.", Recon.active() ? "RECALL" : "LAUNCH", () -> {
            onClose();
            Recon.toggle(minecraft);
        }));
        tools.add(action("Mark waypoint here", "Saves your position as a waypoint and shows it on the compass.", "MARK", () -> {
            String name = "Mark " + (Waypoints.list(minecraft).split(",").length + 1);
            minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal(Waypoints.set(minecraft, name)));
        }));
        tools.add(action("Tracking", Tracker.name().isEmpty() ? "Use /edith track <player> to follow someone on the HUD." : "Following " + Tracker.name() + ".", Tracker.name().isEmpty() ? "NONE" : "STOP", () -> Tracker.set("")));
        tools.add(action("Duel assist", Duel.active() ? "Duel assist is ON against " + Duel.opponentName() + "." : "Start with /edith duel <player> (they must agree).", Duel.active() ? "STOP" : "OFF", () -> Duel.stop(minecraft, "you stopped it.")));
        pages.add(tools);

        List<Ctl> voice = new ArrayList<>();
        voice.add(toggle("EDITH speaks", "Answers out loud when you talk to her.", () -> c.speak, () -> c.speak = !c.speak));
        voice.add(toggle("Speak typed answers", "Also answer out loud when you type to EDITH in chat.", () -> c.speakTypedReplies, () -> c.speakTypedReplies = !c.speakTypedReplies));
        voice.add(toggle("Speak alerts", "EDITH says the warning out loud for creepers and low health.", () -> c.speakAlerts, () -> c.speakAlerts = !c.speakAlerts));
        voice.add(toggle("Read translations aloud", "Chat Translator output is read out loud.", () -> c.speakTranslations, () -> c.speakTranslations = !c.speakTranslations));
        voice.add(cycle("Voice", "Which Gemini voice EDITH uses (30 to choose from, female and male). Picking Kore uses the film filter, any other voice uses the light filter so it really sounds different.",
            () -> { for (String[] v : VOICE_INFO) if (v[0].equalsIgnoreCase(c.geminiVoice)) return v[0] + " - " + v[1]; return c.geminiVoice; },
            () -> EdithConfig.applyVoice(VOICES[(indexOf(VOICES, c.geminiVoice) + 1) % VOICES.length]),
            () -> EdithConfig.applyVoice(VOICES[(indexOf(VOICES, c.geminiVoice) + VOICES.length - 1) % VOICES.length])));
        voice.add(action("Preview voice", "Hear the current voice and filter.", "PLAY",
            () -> com.example.edithglasses.ai.Assistant.announce(minecraft, "Hello. This is how I sound.")));
        voice.add(cycle("Voice filter", "FILM is tuned to the film EDITH and pulls every voice towards that one sound. LIGHT keeps each voice's own character. OFF is the plain voice. Needs ffmpeg.",
            () -> c.voiceFilter == null || c.voiceFilter.isBlank() ? "OFF" : c.voiceFilter.equals(new EdithConfig().voiceFilter) ? "FILM" : "LIGHT",
            () -> c.voiceFilter = (c.voiceFilter == null || c.voiceFilter.isBlank()) ? new EdithConfig().voiceFilter : c.voiceFilter.equals(new EdithConfig().voiceFilter) ? EdithConfig.LIGHT_FILTER : "",
            () -> c.voiceFilter = (c.voiceFilter == null || c.voiceFilter.isBlank()) ? EdithConfig.LIGHT_FILTER : c.voiceFilter.equals(new EdithConfig().voiceFilter) ? "" : new EdithConfig().voiceFilter));
        voice.add(toggle("Digital shimmer", "A very short echo that adds a faint digital sheen.", () -> c.voiceEffect, () -> c.voiceEffect = !c.voiceEffect));
        pages.add(voice);

        // voice cache
        voice.add(toggle("Save voice lines", "Every line EDITH speaks is generated once and then replayed from your disk, so repeats cost nothing.",
            () -> c.voiceCache, () -> c.voiceCache = !c.voiceCache));
        voice.add(action("Clear voice cache", com.example.edithglasses.ai.Providers.cacheCount() + " saved lines. Clearing makes EDITH generate them again next time.", "CLEAR",
            () -> com.example.edithglasses.ai.Providers.cacheClear()));

        List<Ctl> aiPage = new ArrayList<>();
        java.util.List<String> prefIds = new ArrayList<>();
        prefIds.add("auto");
        for (var a : com.example.edithglasses.ai.Ais.ALL) prefIds.add(a.id());
        aiPage.add(cycle("Preferred AI", "Which AI EDITH asks first. The others are used automatically when it fails or runs out. Auto tries them in the order below.",
            () -> c.preferredAi.toUpperCase(),
            () -> c.preferredAi = prefIds.get((Math.max(0, prefIds.indexOf(c.preferredAi.toLowerCase())) + 1) % prefIds.size()),
            () -> c.preferredAi = prefIds.get((Math.max(0, prefIds.indexOf(c.preferredAi.toLowerCase())) + prefIds.size() - 1) % prefIds.size())));
        for (var a : com.example.edithglasses.ai.Ais.ALL) {
            boolean has = com.example.edithglasses.ai.Keys.has(a.id());
            boolean local = a.id().equals("local");
            aiPage.add(action(a.label() + (has ? (local ? "  [on]" : "  [saved]") : ""),
                (local ? (has ? "On. Runs a model on your own PC, no internet or key needed for thinking (the voice still comes from a cloud voice). Click to change the address or switch off."
                    : "Runs a model on this PC. Install Ollama (ollama.com), run: ollama pull qwen2.5:3b, then click here to switch it on. Slower and less smart than the cloud AIs, so it works best as a fallback.")
                    : has ? "Key saved. Click to change or remove it." : "No key yet. Click to paste one. Get it at " + a.site() + ".")
                    + (a.id().equals("groq") || a.id().equals("gemini") ? " Also does voice." : a.id().equals("openai") ? " Also does voice." : ""),
                has ? "CHANGE" : (local ? "SET UP" : "ADD KEY"), () -> minecraft.gui.setScreen(new KeyEntryScreen(this, a))));
        }
        aiPage.add(action("Check models", "Ask every AI with a key which models it offers right now (EDITH always picks the newest).", "CHECK", () -> {
            Thread t = new Thread(com.example.edithglasses.ai.Models::refresh, "edith-models");
            t.setDaemon(true);
            t.start();
        }));
        pages.add(aiPage);

        List<Ctl> sys = new ArrayList<>();
        sys.add(slider("Drone command spacing", "Pause between the commands of a drone strike. Raise it if a server kicks you for command spam.", 300, 2000, 50,
            () -> c.commandDelayMs, v -> c.commandDelayMs = (int) v, v -> (int) v + " ms"));
        sys.add(action("Chat Translator", "Open the Chat Translator settings.", "OPEN", () -> minecraft.gui.setScreen(new SettingsScreen(this))));
        sys.add(action("Reset window", "Scroll back to the top of this page.", "TOP", () -> scroll = 0));
        Ctl info = new Ctl(Kind.INFO, "E.D.I.T.H. Glasses", "Menu key: G.  Talk: V.  Confirm / cancel: Y / N.  Type 'edith, ...' in chat to ask.");
        info.value = () -> "v1.0";
        sys.add(info);
        pages.add(sys);
    }

    @Override
    protected void init() {
        build();
        layout();
    }

    private void layout() {
        pw = Math.min(width - 16, 380);
        ph = Math.min(height - 12, 236);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx0 = px + 92;
        cy0 = py + 32;
        cw = pw - 92 - 10;
        ch = ph - 32 - 30;
    }

    private int rowH() {
        return 21;
    }

    private int maxScroll() {
        return Math.max(0, pages.get(cat).size() * rowH() - ch);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // own background instead of the blurred vanilla one
        float t = Mth.clamp((System.currentTimeMillis() - opened) / 220f, 0f, 1f);
        g.fill(0, 0, width, height, ((int) (0xB0 * t) << 24) | 0x020C16);
        g.fillGradient(0, 0, width, height / 3, alpha(0x40082A44, t), 0x00082A44);
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x08CFF4FF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        extractBackground(g, mx, my, delta);
        layout();
        var font = this.font;
        long now = System.currentTimeMillis();
        scroll = Mth.clamp(scroll, 0, maxScroll());

        // window
        panel(g, px, py, pw, ph, LINE);
        corners(g, px - 3, py - 3, pw + 6, ph + 6, 10, BRIGHT);
        // header
        g.fill(px + 1, py + 1, px + pw - 1, py + 22, 0x66104868);
        text(g, font, "E.D.I.T.H.", px + 8, py + 6, BRIGHT, 1.0f);
        text(g, font, "// CONTROL", px + 8 + width("E.D.I.T.H.", 1.0f) + 4, py + 8, DIM, 0.7f);
        int sweep = (int) ((now / 12) % (pw - 2));
        g.fill(px + 1 + sweep, py + 21, px + 1 + Math.min(pw - 2, sweep + 30), py + 22, BRIGHT);
        boolean closeHover = mx >= px + pw - 18 && mx < px + pw - 4 && my >= py + 4 && my < py + 18;
        box(g, px + pw - 18, py + 4, 14, 14, closeHover ? RED : LINE);
        centered(g, font, "X", px + pw - 11, py + 8, closeHover ? RED : BRIGHT, 0.8f);

        // sidebar
        g.fill(px + 84, py + 24, px + 85, py + ph - 8, DIM);
        for (int i = 0; i < CATS.length; i++) {
            int y = py + 28 + i * 24;
            boolean sel = i == cat;
            boolean hover = mx >= px + 6 && mx < px + 82 && my >= y && my < y + 20;
            if (sel) {
                g.fill(px + 6, y, px + 82, y + 20, 0x77166486);
                g.fill(px + 6, y, px + 8, y + 20, BRIGHT);
            } else if (hover) {
                g.fill(px + 6, y, px + 82, y + 20, 0x44104868);
            }
            text(g, font, CATS[i], px + 14, y + 6, sel ? BRIGHT : (hover ? LINE : DIM), 0.85f);
            if (sel) text(g, font, ">", px + 72, y + 6, BRIGHT, 0.85f);
        }

        // content
        List<Ctl> list = pages.get(cat);
        g.enableScissor(cx0 - 4, cy0, cx0 + cw + 4, cy0 + ch);
        Ctl hovered = null;
        for (int i = 0; i < list.size(); i++) {
            Ctl c = list.get(i);
            int y = cy0 + i * rowH() - scroll;
            c.x = cx0;
            c.y = y;
            c.w = cw;
            if (y + rowH() < cy0 || y > cy0 + ch) continue;
            boolean hover = mx >= cx0 - 2 && mx < cx0 + cw + 2 && my >= y && my < y + rowH() - 1 && my >= cy0 && my < cy0 + ch;
            if (hover) hovered = c;
            drawRow(g, font, c, y, hover, mx);
        }
        g.disableScissor();

        // scrollbar
        if (maxScroll() > 0) {
            int track = ch;
            int knob = Math.max(14, track * ch / (list.size() * rowH()));
            int ky = cy0 + (int) ((track - knob) * (scroll / (double) maxScroll()));
            g.fill(cx0 + cw + 5, cy0, cx0 + cw + 6, cy0 + track, DIM);
            g.fill(cx0 + cw + 4, ky, cx0 + cw + 7, ky + knob, LINE);
        }

        // footer: description of the hovered control
        g.fill(px + 90, py + ph - 26, px + pw - 8, py + ph - 25, DIM);
        String desc = hovered != null ? hovered.desc : "Click a switch to toggle it. Right-click a selector to go back. Drag a slider.";
        drawWrapped(g, font, desc, px + 92, py + ph - 22, pw - 104, hovered != null ? BRIGHT : DIM);
    }

    private void drawWrapped(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String s, int x, int y, int maxW, int color) {
        StringBuilder cur = new StringBuilder();
        int lines = 0;
        for (String word : s.split(" ")) {
            if (cur.length() > 0 && width(cur + " " + word, 0.7f) > maxW) {
                text(g, font, cur.toString(), x, y + lines * 8, color, 0.7f);
                lines++;
                cur.setLength(0);
                if (lines >= 2) return;
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0 && lines < 2) text(g, font, cur.toString(), x, y + lines * 8, color, 0.7f);
    }

    private int width(String s, float scale) {
        return HudStyle_width(s, scale);
    }

    private int HudStyle_width(String s, float scale) {
        return com.example.edithglasses.feature.HudStyle.width(this.font, s, scale);
    }

    private void drawRow(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Ctl c, int y, boolean hover, int mx) {
        int x = c.x, w = c.w;
        g.fill(x - 2, y, x + w + 2, y + rowH() - 2, hover ? 0x66104868 : 0x330A2A40);
        if (hover) g.fill(x - 2, y, x, y + rowH() - 2, BRIGHT);
        text(g, font, c.label, x + 4, y + 6, hover ? BRIGHT : LINE, 0.85f);
        int rx = x + w - 4;
        switch (c.kind) {
            case TOGGLE -> {
                boolean on = c.on.getAsBoolean();
                if (c.anim < 0) c.anim = on ? 1f : 0f;
                c.anim += ((on ? 1f : 0f) - c.anim) * 0.3f;
                int tw = 26, th = 10, tx = rx - tw, ty = y + 4;
                g.fill(tx, ty, tx + tw, ty + th, on ? alpha(GREEN, 0.5f) : 0x66041722);
                box(g, tx, ty, tw, th, on ? GREEN : DIM);
                int kx = tx + 1 + Math.round((tw - th) * c.anim);
                g.fill(kx, ty + 1, kx + th - 2, ty + th - 1, on ? BRIGHT : DIM);
                text(g, font, on ? "ON" : "OFF", tx - 18, y + 6, on ? GREEN : DIM, 0.65f);
            }
            case CYCLE -> {
                String v = c.value.get();
                int vw = width(v, 0.8f);
                text(g, font, v, rx - vw - 10, y + 6, BRIGHT, 0.8f);
                text(g, font, "<", rx - vw - 18, y + 6, hover ? LINE : DIM, 0.8f);
                text(g, font, ">", rx - 6, y + 6, hover ? LINE : DIM, 0.8f);
            }
            case SLIDER -> {
                int tw = 78, tx = rx - tw, ty = y + 9;
                double f = (c.get.getAsDouble() - c.min) / (c.max - c.min);
                g.fill(tx, ty, tx + tw, ty + 2, DIM);
                g.fill(tx, ty, tx + (int) (tw * f), ty + 2, LINE);
                int kx = tx + (int) (tw * f);
                g.fill(kx - 1, ty - 3, kx + 2, ty + 5, BRIGHT);
                String v = c.value.get();
                text(g, font, v, tx - width(v, 0.7f) - 6, y + 6, BRIGHT, 0.7f);
            }
            case ACTION -> {
                String v = c.value.get();
                int bw = Math.max(40, width(v, 0.75f) + 14), bx = rx - bw;
                g.fill(bx, y + 3, bx + bw, y + 15, hover ? 0x99166486 : 0x55041722);
                box(g, bx, y + 3, bw, 12, hover ? BRIGHT : LINE);
                centered(g, font, v, bx + bw / 2, y + 6, hover ? BRIGHT : LINE, 0.75f);
            }
            case INFO -> {
                String v = c.value.get();
                text(g, font, v, rx - width(v, 0.8f), y + 6, DIM, 0.8f);
            }
        }
    }

    // ------------------------------------------------------------------ input

    private Ctl rowAt(double mx, double my) {
        if (my < cy0 || my >= cy0 + ch || mx < cx0 - 2 || mx >= cx0 + cw + 2) return null;
        for (Ctl c : pages.get(cat)) {
            if (my >= c.y && my < c.y + rowH() - 1) return c;
        }
        return null;
    }

    private void setSlider(Ctl c, double mx) {
        int tw = 78, tx = c.x + c.w - 4 - tw;
        double f = Mth.clamp((mx - tx) / tw, 0.0, 1.0);
        double v = c.min + f * (c.max - c.min);
        v = Math.round(v / c.step) * c.step;
        c.set.accept(Mth.clamp(v, c.min, c.max));
        EdithConfig.save();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (mx >= px + pw - 18 && mx < px + pw - 4 && my >= py + 4 && my < py + 18) {
            onClose();
            return true;
        }
        for (int i = 0; i < CATS.length; i++) {
            int y = py + 28 + i * 24;
            if (mx >= px + 6 && mx < px + 82 && my >= y && my < y + 20) {
                cat = i;
                scroll = 0;
                return true;
            }
        }
        Ctl c = rowAt(mx, my);
        if (c == null) return super.mouseClicked(event, doubleClick);
        switch (c.kind) {
            case SLIDER -> {
                dragging = c;
                setSlider(c, mx);
            }
            case INFO -> { }
            default -> {
                if (button == 1 && c.back != null) c.back.run();
                else c.click.run();
                EdithConfig.save();
                if (c.kind == Kind.ACTION && minecraft.gui.screen() == this) build();
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            setSlider(dragging, event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(sy) * 14, 0, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        EdithConfig.save();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
