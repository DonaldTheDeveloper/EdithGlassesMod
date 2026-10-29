package com.example.edithglasses.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class DamageEventMixin {
    /** Remembers where the damage you take comes from (HUD arcs, "what hit me"). */
    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void edith$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        com.example.edithglasses.feature.DamageLog.record(Minecraft.getInstance(), packet);
    }
}
