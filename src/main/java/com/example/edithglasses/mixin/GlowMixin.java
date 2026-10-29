package com.example.edithglasses.mixin;

import com.example.edithglasses.Glasses;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Thermal vision: living things within 48 blocks get the vanilla glowing outline, visible through walls. */
@Mixin(Minecraft.class)
public abstract class GlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void edith$thermal(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || entity == mc.player || !(entity instanceof LivingEntity)) return;
        if (!Glasses.thermalActive() || entity.distanceTo(mc.player) > 48) return;
        cir.setReturnValue(true);
    }
}
