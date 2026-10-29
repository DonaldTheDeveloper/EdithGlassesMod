package com.example.edithglasses.mixin;

import com.example.edithglasses.EdithConfig;
import com.example.edithglasses.EdithHud;
import com.example.edithglasses.Glasses;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "extractCameraOverlays", at = @At("TAIL"))
    private void edith$hud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        com.example.edithglasses.feature.HudExtras.flash(graphics, graphics.guiWidth(), graphics.guiHeight());
        com.example.edithglasses.feature.CameraMode.draw(graphics, this.minecraft.font, graphics.guiWidth(), graphics.guiHeight());
        com.example.edithglasses.feature.Builder.draw(graphics, this.minecraft.font, graphics.guiWidth(), graphics.guiHeight());
        if (!com.example.edithglasses.HudLayout.editing()) {
            if (!this.minecraft.options.getCameraType().isFirstPerson()) return;
            if (!Glasses.equipped(this.minecraft.player)) return;
        }
        if (EdithHud.visible()) EdithHud.draw(graphics);
        com.example.edithglasses.feature.Intro.draw(graphics, this.minecraft.font, graphics.guiWidth(), graphics.guiHeight());
    }
}
