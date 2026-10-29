package com.example.edithglasses.mixin;

import com.example.edithglasses.feature.CameraMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No pop-up notifications (advancements, recipes, chat warnings) in a camera shot. */
@Mixin(ToastManager.class)
public abstract class ToastCleanMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void edith$cleanToasts(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (CameraMode.clean()) ci.cancel();
    }
}
