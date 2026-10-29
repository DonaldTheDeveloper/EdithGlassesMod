package com.example.chattranslator.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> chattranslator$getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int chattranslator$getScrollPos();

    @Invoker("getScale")
    double chattranslator$getScale();

    @Invoker("getWidth")
    int chattranslator$getWidth();
}
