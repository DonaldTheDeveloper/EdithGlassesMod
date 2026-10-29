package com.example.edithglasses.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class UseItemMixin {
    /** The glasses are not a spyglass: right-click does nothing, so no vanilla scope or raised-arm pose. */
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void edith$noScope(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player.getItemInHand(hand).is(Items.SPYGLASS)) cir.setReturnValue(InteractionResult.PASS);
    }
}
