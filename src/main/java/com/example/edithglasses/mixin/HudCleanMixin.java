package com.example.edithglasses.mixin;

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
