import os
base = os.path.expanduser("~/EdithGlassesMod/src/main")
j = base + "/java/com/example/edithglasses/"


def edit(path, pairs):
    s = open(path, encoding="utf-8").read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit("missing anchor in %s: %s" % (path, a[:70]))
        s = s.replace(a, b, 1)
    open(path, "w", encoding="utf-8").write(s)


edit(j + "EdithConfig.java", [("    /** EDITH answers out loud. */", """    /** Hide the normal hotbar, hearts, food, armor and experience while the EDITH HUD is showing (it draws its own). */
    public boolean hideVanillaHud = true;
    /** EDITH answers out loud. */""")])

# ---- one place that says whether the EDITH HUD is on screen right now
edit(j + "EdithHud.java", [("    private EdithHud() {\n    }\n", """    private EdithHud() {
    }

    /** True while the EDITH HUD is drawn (first person, glasses on, HUD mode allows it). */
    public static boolean visible() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Glasses.equipped(mc.player)) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        String mode = EdithConfig.get().hudMode;
        return "always".equals(mode) || ("zoom".equals(mode) && (Glasses.zoom() > 1.02F || mc.hasShiftDown()));
    }

    public static boolean hidesVanilla() {
        return EdithConfig.get().hideVanillaHud && visible();
    }
""")])

edit(j + "mixin/HudMixin.java", [
    ("        String mode = EdithConfig.get().hudMode;\n        boolean show = \"always\".equals(mode) || (\"zoom\".equals(mode) && (Glasses.zoom() > 1.02F || this.minecraft.hasShiftDown()));\n        if (show) EdithHud.draw(graphics);",
     "        if (EdithHud.visible()) EdithHud.draw(graphics);")])

open(j + "mixin/HudHideMixin.java", "w", encoding="utf-8").write('''package com.example.edithglasses.mixin;

import com.example.edithglasses.EdithHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the EDITH HUD is up, the normal hotbar, hearts, food, armor, experience and crosshair are not drawn (EDITH draws its own). */
@Mixin(Hud.class)
public abstract class HudHideMixin {
    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void edith$hideBars(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (EdithHud.hidesVanilla()) ci.cancel();
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void edith$hideCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (EdithHud.hidesVanilla()) ci.cancel();
    }
}
''')
edit(base + "/resources/edithglasses.mixins.json", [('"GlowMixin"', '"GlowMixin", "HudHideMixin"')])

# ---- EdithHud: own hotbar + armor/xp/air, layout shift
s = open(j + "EdithHud.java", encoding="utf-8").read()
s = s.replace("        tabs(g, font, cx, h - 66);\n", "        tabs(g, font, cx, h - 52);\n        if (EdithConfig.get().hideVanillaHud) hotbar(g, font, p, cx, h - m - 20);\n", 1)
s = s.replace("drawAssistant(g, font, w, h - m - 76, cx, time);", "drawAssistant(g, font, w, h - m - 62, cx, time);", 1)
s = s.replace("int cw = 104, ch = 50, x = m + 6, y = h - m - 66 - ch - 8;", "int cw = 104, ch = 50, x = m + 6, y = h - m - 52 - ch - 8;", 1)
s = s.replace("        int food = p.getFoodData().getFoodLevel();\n        text(g, font, \"FOOD \" + food, cx - r - 26, cy + 8, food <= 6 ? AMBER : DIM, 0.65f);",
              "        int food = p.getFoodData().getFoodLevel();\n        text(g, font, \"FOOD \" + food, cx - r - 26, cy + 8, food <= 6 ? AMBER : DIM, 0.65f);\n"
              "        text(g, font, \"ARMOR \" + p.getArmorValue(), cx - r - 26, cy + 16, DIM, 0.65f);\n"
              "        if (p.experienceLevel > 0) text(g, font, \"LVL \" + p.experienceLevel, cx - r - 26, cy + 24, GREEN, 0.65f);", 1)
s = s.replace("    // ------------------------------------------------------------------ assistant panel", '''    // ------------------------------------------------------------------ hotbar

    private static void hotbar(GuiGraphicsExtractor g, Font font, Player p, int cx, int y) {
        int slot = 18, gap = 2, total = 9 * slot + 8 * gap;
        int x0 = cx - total / 2;
        int sel = p.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            int x = x0 + i * (slot + gap);
            boolean on = i == sel;
            g.fill(x, y, x + slot, y + slot, on ? 0x99166486 : 0x77041722);
            box(g, x, y, slot, slot, on ? BRIGHT : DIM);
            if (on) corners(g, x - 2, y - 2, slot + 4, slot + 4, 4, BRIGHT);
            var stack = p.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                g.item(stack, x + 1, y + 1);
                g.itemDecorations(font, stack, x + 1, y + 1);
            }
            text(g, font, String.valueOf(i + 1), x + 2, y + 1, alpha(on ? BRIGHT : DIM, 0.8f), 0.5f);
        }
        var off = p.getOffhandItem();
        if (!off.isEmpty()) {
            int x = x0 - slot - 8;
            g.fill(x, y, x + slot, y + slot, 0x77041722);
            box(g, x, y, slot, slot, AMBER);
            g.item(off, x + 1, y + 1);
            g.itemDecorations(font, off, x + 1, y + 1);
        }
        var held = p.getInventory().getItem(sel);
        if (!held.isEmpty()) centered(g, font, held.getHoverName().getString().toUpperCase(), cx, y - 9, BRIGHT, 0.7f);
        if (p.getAirSupply() < p.getMaxAirSupply()) {
            float f = Mth.clamp(p.getAirSupply() / (float) p.getMaxAirSupply(), 0f, 1f);
            g.fill(x0, y - 16, x0 + total, y - 14, FAINT);
            g.fill(x0, y - 16, x0 + Math.round(total * f), y - 14, f < 0.3f ? RED : 0xFF60D0FF);
            text(g, font, "O2", x0 - 12, y - 18, f < 0.3f ? RED : DIM, 0.6f);
        }
    }

    // ------------------------------------------------------------------ assistant panel''', 1)
open(j + "EdithHud.java", "w", encoding="utf-8").write(s)
print("patched")
