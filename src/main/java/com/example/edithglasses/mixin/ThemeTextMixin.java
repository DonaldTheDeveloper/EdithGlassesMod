package com.example.edithglasses.mixin;

import com.example.edithglasses.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Container titles use dark grey text (made for light grey panels); on the dark EDITH panels they become light cyan. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ThemeTextMixin {
    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int edith$labelColor(int color) {
        return Theme.active() && color == -12566464 ? 0xFFBFEFFF : color;
    }
}
