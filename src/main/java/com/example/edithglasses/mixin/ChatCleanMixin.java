package com.example.edithglasses.mixin;

import com.example.edithglasses.feature.CameraMode;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No chat lines in a camera shot. */
@Mixin(ChatComponent.class)
public abstract class ChatCleanMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
        at = @At("HEAD"), cancellable = true)
    private void edith$cleanChat(CallbackInfo ci) {
        if (CameraMode.clean()) ci.cancel();
    }
}
