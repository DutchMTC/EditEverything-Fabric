package com.dutchmtc.ee.mixin;

import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameModeSwitcherScreen.class)
public interface GameModeSwitcherScreenInvoker {
    @Invoker("switchToHoveredGameMode")
    void ee$invokeSwitchToHoveredGameMode();
}

