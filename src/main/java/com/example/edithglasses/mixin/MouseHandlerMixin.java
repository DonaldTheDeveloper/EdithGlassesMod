package com.example.edithglasses.mixin;

import com.example.edithglasses.Glasses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    /** Shift + scroll zooms while the glasses are on, instead of changing the hotbar slot. */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void edith$zoom(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (handle != this.minecraft.getWindow().handle()) return;
        if (this.minecraft.gui.screen() != null || this.minecraft.gui.overlay() != null) return;
        if (this.minecraft.player == null || (!this.minecraft.hasShiftDown() && !com.example.edithglasses.feature.CameraMode.active())) return;
        if (!Glasses.equipped(this.minecraft.player)) return;
        double amount = yoffset != 0.0 ? yoffset : xoffset;
        if (amount != 0.0) Glasses.scroll(amount);
        ci.cancel();
    }
}
