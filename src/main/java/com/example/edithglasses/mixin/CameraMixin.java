package com.example.edithglasses.mixin;

import com.example.edithglasses.Glasses;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void edith$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float z = Glasses.zoom();
        if (z > 1.0F) cir.setReturnValue(cir.getReturnValueF() / z);
    }
}
