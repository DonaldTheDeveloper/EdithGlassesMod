package com.example.chattranslator.mixin;

import com.example.chattranslator.ChatActions;
import com.example.chattranslator.QuickMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Right-click a chat line:           copy it.
 * Shift + right-click:               paste it into the chat box.
 * Ctrl  + right-click:               translate it.
 * Middle-click twice (moderators):   quick ban the player who wrote the line.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void chattranslator$onClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        int button = event.button();
        if (button != 1 && button != 2) {
            QuickMod.cancelPending(); // any other click cancels a pending quick ban
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String text = chattranslator$lineAt(mc, event.x(), event.y());
        if (text == null) {
            QuickMod.cancelPending();
            return;
        }

        if (button == 2) {
            QuickMod.click(text);
        } else if (mc.hasShiftDown()) {
            ChatActions.paste((ChatScreen) (Object) this, text);
        } else if (mc.hasControlDown()) {
            ChatActions.translate(text);
        } else {
            ChatActions.copy(text);
        }
        cir.setReturnValue(true);
    }

    @Shadow protected EditBox input;

    /** Quick-mute key (Delete by default) while hovering a chat line. Ignored while you are typing. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void chattranslator$onKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        KeyMapping key = QuickMod.muteKey;
        if (key == null || !key.matches(event)) return;
        if (input != null && !input.getValue().isEmpty()) return; // typing: let the key edit text as normal
        Minecraft mc = Minecraft.getInstance();
        String text = chattranslator$lineAt(mc, mc.mouseHandler.getScaledXPos(mc.getWindow()),
            mc.mouseHandler.getScaledYPos(mc.getWindow()));
        if (text == null) return;
        QuickMod.mute(text);
        cir.setReturnValue(true);
    }

    /** The full chat message (all its wrapped lines) under the mouse, or null. */
    private static String chattranslator$lineAt(Minecraft mc, double mx, double my) {
        ChatComponent chat = mc.gui.hud.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;

        // Mirror the layout used by ChatComponent when it draws the lines.
        double scale = acc.chattranslator$getScale();
        double spacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int) (9 * (spacing + 1.0));
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
        double cx = mx / scale - 4.0;
        double cy = my / scale;
        if (cx < -4.0 || cx > acc.chattranslator$getWidth() / scale + 8.0) return null;
        int lineIndex = (int) Math.floor((chatBottom - cy) / entryHeight);
        if (lineIndex < 0 || lineIndex >= chat.getLinesPerPage()) return null;

        List<GuiMessage.Line> lines = acc.chattranslator$getTrimmedMessages();
        int idx = lineIndex + acc.chattranslator$getScrollPos();
        if (idx >= lines.size()) return null;

        int top = idx;
        while (top + 1 < lines.size() && !lines.get(top + 1).endOfEntry()) top++;
        int bottom = idx;
        while (bottom > 0 && !lines.get(bottom).endOfEntry()) bottom--;

        StringBuilder sb = new StringBuilder();
        for (int i = top; i >= bottom; i--) {
            lines.get(i).content().accept((index, style, cp) -> { sb.appendCodePoint(cp); return true; });
        }
        String text = sb.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
