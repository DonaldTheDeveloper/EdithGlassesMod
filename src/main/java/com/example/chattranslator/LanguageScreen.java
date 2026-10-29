package com.example.chattranslator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Type to search (language name, code or country), click a language to pick it,
 * click the star to make it a favorite. Favorites are listed first when the search box is empty.
 */
public class LanguageScreen extends Screen {
    private static final int MAX_ROWS = 8;

    private final Screen parent;
    private final String heading;
    private final String current;
    private final Consumer<String> onPick;

    private EditBox search;
    private final Button[] rows = new Button[MAX_ROWS];
    private final Button[] stars = new Button[MAX_ROWS];
    private final String[] codes = new String[MAX_ROWS];
    private Button prev, next;
    private int perPage = MAX_ROWS;
    private int page = 0;
    private List<Languages.Lang> results = Languages.ALL;

    public LanguageScreen(Screen parent, String heading, String current, Consumer<String> onPick) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.current = current;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        int w = 230, x = width / 2 - w / 2, top = 28;
        perPage = Math.max(3, Math.min(MAX_ROWS, (height - top - 26 - 32) / 22));

        search = new EditBox(font, x, top, w, 20, Component.literal("Search"));
        search.setHint(Component.literal("Search: german, germany, ja ..."));
        search.setResponder(s -> { page = 0; update(); });
        addRenderableWidget(search);
        setInitialFocus(search);

        for (int i = 0; i < MAX_ROWS; i++) {
            int idx = i;
            int y = top + 26 + i * 22;
            rows[i] = addRenderableWidget(Button.builder(Component.empty(), b -> pick(idx)).bounds(x, y, w - 24, 20).build());
            stars[i] = addRenderableWidget(Button.builder(Component.empty(), b -> star(idx)).bounds(x + w - 20, y, 20, 20).build());
        }
        int by = top + 26 + perPage * 22 + 4;
        prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; update(); }).bounds(x, by, 50, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; update(); }).bounds(x + w - 50, by, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(x + 56, by, w - 112, 20).build());
        update();
    }

    private void update() {
        String q = search == null ? "" : search.getValue();
        List<Languages.Lang> found = Languages.search(q);
        if (q.isBlank()) {
            // favorites first, in the order you added them, then everything else
            List<Languages.Lang> ordered = new ArrayList<>();
            for (String f : Config.get().favorites) {
                Languages.Lang l = Languages.find(f);
                if (l != null) ordered.add(l);
            }
            for (Languages.Lang l : found) if (!ordered.contains(l)) ordered.add(l);
            found = ordered;
        }
        results = found;
        int pages = Math.max(1, (results.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < MAX_ROWS; i++) {
            int idx = page * perPage + i;
            boolean show = i < perPage && idx < results.size();
            rows[i].visible = show;
            rows[i].active = show;
            stars[i].visible = show;
            stars[i].active = show;
            if (show) {
                Languages.Lang l = results.get(idx);
                codes[i] = l.code();
                rows[i].setMessage(Component.literal(l.name() + " (" + l.code() + ")"
                    + (l.code().equalsIgnoreCase(current) ? "  <- current" : "")));
                stars[i].setMessage(Component.literal(Config.get().isFavorite(l.code()) ? "★" : "☆"));
            }
        }
        prev.active = page > 0;
        next.active = page + 1 < pages;
    }

    private void pick(int row) {
        if (codes[row] == null) return;
        onPick.accept(codes[row]);
        onClose();
    }

    private void star(int row) {
        if (codes[row] == null) return;
        Config.get().toggleFavorite(codes[row]);
        update();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, Component.literal(heading), width / 2, 12, 0xFFFFFFFF);
        if (results.isEmpty()) {
            g.centeredText(font, Component.literal("No language or country matches"), width / 2, 28 + 34, 0xFFFF8888);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
