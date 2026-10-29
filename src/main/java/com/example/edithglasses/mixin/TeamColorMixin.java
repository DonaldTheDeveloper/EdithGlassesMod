package com.example.edithglasses.mixin;

import com.example.edithglasses.Glasses;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Thermal colours: hostile = red, players = cyan, everything else = orange. */
@Mixin(Entity.class)
public abstract class TeamColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void edith$thermalColor(CallbackInfoReturnable<Integer> cir) {
        if (!Glasses.thermalActive()) return;
        Object self = this;
        if (self instanceof Enemy) cir.setReturnValue(0xFF3030);
        else if (self instanceof Player) cir.setReturnValue(0x60D0FF);
        else cir.setReturnValue(0xFFA040);
    }
}
