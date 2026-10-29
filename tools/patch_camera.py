import os
j = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/")
res = os.path.expanduser("~/EdithGlassesMod/src/main/resources/")


def rw(p, f, base=None):
    path = (base or j) + p
    s = open(path, encoding="utf-8").read()
    s = f(s)
    open(path, "w", encoding="utf-8").write(s)


def rep(s, a, b):
    assert a in s, "missing: " + a[:80]
    return s.replace(a, b, 1)


# ---- CameraMode: no hideGui option in this version, use the clean flag + a mixin; key lookup via its saved name
def cam(s):
    s = s.replace("        mc.options.hideGui = true;\n", "").replace("        mc.options.hideGui = false;\n", "")
    s = rep(s, "km.getKey().getValue()", "InputConstants.getKey(km.saveString()).getValue()")
    return s


rw("feature/CameraMode.java", cam)


# ---- Glasses: camera zoom range
def glasses(s):
    s = rep(s, "    public static final float MAX_ZOOM = 12.0F;", "    public static final float MAX_ZOOM = 12.0F;\n    private static boolean cameraZoom = false;\n\n"
            "    /** The camera mode zooms further (24x, like a long lens). */\n"
            "    public static void setCameraZoomMode(boolean on) {\n        cameraZoom = on;\n        if (!on && zoom > MAX_ZOOM) zoom = MAX_ZOOM;\n    }\n\n"
            "    public static void resetZoom() {\n        zoom = 1.0F;\n    }")
    s = rep(s, "zoom = Mth.clamp(zoom * (float) Math.pow(1.25, amount), 1.0F, MAX_ZOOM);",
            "zoom = Mth.clamp(zoom * (float) Math.pow(1.25, amount), 1.0F, cameraZoom ? 24.0F : MAX_ZOOM);")
    return s


rw("Glasses.java", glasses)

# ---- scroll zooms without Shift while the camera is on
rw("mixin/MouseHandlerMixin.java", lambda s: rep(s,
    "if (this.minecraft.player == null || !this.minecraft.hasShiftDown()) return;",
    "if (this.minecraft.player == null || (!this.minecraft.hasShiftDown() && !com.example.edithglasses.feature.CameraMode.active())) return;"))

# ---- a clean frame hides the whole HUD (new mixin)
open(j + "mixin/HudCleanMixin.java", "w", encoding="utf-8").write('''package com.example.edithglasses.mixin;

import com.example.edithglasses.feature.CameraMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the glasses camera takes a picture nothing of the HUD is drawn. */
@Mixin(Hud.class)
public abstract class HudCleanMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void edith$cleanShot(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CameraMode.clean()) ci.cancel();
    }
}
''')
rw("edithglasses.mixins.json", lambda s: rep(s, '"DamageEventMixin"', '"DamageEventMixin",\n    "HudCleanMixin"'), res)

# ---- hud: viewfinder replaces the EDITH HUD
rw("EdithHud.java", lambda s: rep(rep(s,
    "        if (HudLayout.editing()) return mc.player != null;",
    "        if (HudLayout.editing()) return mc.player != null;\n        if (com.example.edithglasses.feature.CameraMode.active()) return false;"),
    "        return EdithConfig.get().hideVanillaHud && visible();",
    "        if (com.example.edithglasses.feature.CameraMode.active()) return true;\n        return EdithConfig.get().hideVanillaHud && visible();"))
rw("mixin/HudMixin.java", lambda s: rep(s,
    "        com.example.edithglasses.feature.Builder.draw(graphics,",
    "        com.example.edithglasses.feature.CameraMode.draw(graphics, this.minecraft.font, graphics.guiWidth(), graphics.guiHeight());\n        com.example.edithglasses.feature.Builder.draw(graphics,"))


# ---- client: key, ticks, command
def client(s):
    s = rep(s, "        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Builder::pre);",
            "        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Builder::pre);\n"
            "        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.CameraMode::pre);\n"
            "        KeyMapping cameraKey = KeyMappingHelper.registerKeyMapping(\n"
            "            new KeyMapping(\"key.edithglasses.camera\", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));")
    s = rep(s, "            com.example.edithglasses.feature.Builder.tick(mc);\n",
            "            com.example.edithglasses.feature.Builder.tick(mc);\n"
            "            com.example.edithglasses.feature.CameraMode.tick(mc);\n"
            "            while (cameraKey.consumeClick()) if (mc.gui.screen() == null) say(com.example.edithglasses.feature.CameraMode.toggle(mc));\n")
    s = rep(s, '                .then(ClientCommands.literal("intro")',
            '                .then(ClientCommands.literal("camera")\n'
            '                    .executes(c -> { Minecraft mc = Minecraft.getInstance(); mc.execute(() -> say(com.example.edithglasses.feature.CameraMode.toggle(mc))); return 1; })\n'
            '                    .then(ClientCommands.literal("shoot").executes(c -> { Minecraft mc = Minecraft.getInstance(); mc.execute(() -> com.example.edithglasses.feature.CameraMode.shutter(mc)); return 1; })))\n'
            '                .then(ClientCommands.literal("intro")')
    return s


rw("EdithGlassesClient.java", client)

# ---- menu
rw("EdithMenuScreen.java", lambda s: rep(s, '        tools.add(action("Photo"',
    '        tools.add(action("Camera mode", "Advanced camera: viewfinder, looks (cinematic, noir, vintage, dream, miniature, neon), exposure, frames, timer, burst, 360 panorama and a free camera. Key: C.", "OPEN", () -> { onClose(); com.example.edithglasses.feature.CameraMode.toggle(minecraft); }));\n'
    '        tools.add(action("Photo"'))

# ---- language
rw("assets/edithglasses/lang/en_us.json", lambda s: rep(s, '"key.edithglasses.photo"', '"key.edithglasses.camera": "Advanced camera mode (glasses on)",\n  "key.edithglasses.photo"'), res)
print("patched")
