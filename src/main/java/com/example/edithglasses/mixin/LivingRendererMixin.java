package com.example.edithglasses.mixin;

import com.example.edithglasses.Glasses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingRendererMixin {
    @Shadow @Final protected ItemModelResolver itemModelResolver;

    /** Client-side "/wear": draws the glasses on your own head even if the server never put them in the helmet slot. */
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void edith$localGlasses(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (!Glasses.localWorn() || entity != Minecraft.getInstance().player) return;
        if (state.wornHeadType != null || !entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) return;
        this.itemModelResolver.updateForLiving(state.headItem, new ItemStack(Items.SPYGLASS), ItemDisplayContext.HEAD, entity);
    }
}
