package com.example.edithglasses.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes a plain spyglass use our own item definition (edithglasses:edith_glasses). Because it is a new id, no other
 * resource pack (Fullbright, a server pack, ...) can override it the way they can override minecraft:spyglass.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    private static final Identifier VANILLA = Identifier.withDefaultNamespace("spyglass");
    private static final Identifier GLASSES = Identifier.fromNamespaceAndPath("edithglasses", "edith_glasses");

    @Shadow
    private ClientItem.Properties getItemProperties(Identifier modelId) {
        throw new AssertionError();
    }

    @Shadow
    private ItemModel getItemModel(Identifier modelId) {
        throw new AssertionError();
    }

    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void edith$glasses(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext, @Nullable Level level,
                               @Nullable ItemOwner owner, int seed, CallbackInfo ci) {
        if (!item.is(Items.SPYGLASS)) return;
        Identifier current = item.get(DataComponents.ITEM_MODEL);
        if (current != null && !current.equals(VANILLA)) return; // a server gave it its own model: leave it alone
        output.setOversizedInGui(this.getItemProperties(GLASSES).oversizedInGui());
        this.getItemModel(GLASSES).update(output, item, (ItemModelResolver) (Object) this, displayContext,
            level instanceof ClientLevel clientLevel ? clientLevel : null, owner, seed);
        ci.cancel();
    }
}
