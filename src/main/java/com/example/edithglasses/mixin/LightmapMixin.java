package com.example.edithglasses.mixin;

import com.example.edithglasses.Glasses;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** EDITH night vision: the same full-brightness light map the night vision potion uses. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
    @Shadow
    private boolean needsUpdate;

    @Inject(method = "extract", at = @At("HEAD"))
    private void edith$refresh(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (Glasses.nightChanged()) this.needsUpdate = true;
    }

    @Inject(method = "extract", at = @At("TAIL"))
    private void edith$night(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (Glasses.nightActive()) renderState.nightVisionEffectIntensity = 1.0F;
    }
}
