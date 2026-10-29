package com.example.edithglasses.mixin;

import com.example.edithglasses.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In a world, menus get a dark holographic backdrop with scan lines instead of the blurred vanilla one. */
@Mixin(Screen.class)
public abstract class ThemeBackgroundMixin {
    @Shadow protected net.minecraft.client.Minecraft minecraft;
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void edith$backdrop(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!Theme.active() || this.minecraft == null || this.minecraft.level == null) return;
        g.fill(0, 0, this.width, this.height, 0xB5020C16);
        g.fillGradient(0, 0, this.width, this.height / 3, 0x55082A44, 0x00082A44);
        g.fillGradient(0, this.height * 2 / 3, this.width, this.height, 0x00082A44, 0x55082A44);
        for (int y = 0; y < this.height; y += 3) g.fill(0, y, this.width, y + 1, 0x08CFF4FF);
        ci.cancel();
    }
}
