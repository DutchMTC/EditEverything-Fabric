package com.dutchmtc.ee.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Client-only initialization of detached previews, which never receive entity-data packets. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Invoker("setText")
    void ee$setText(Component value);
}
