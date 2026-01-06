package com.dutchmtc.ee.mixin;

import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen$GameModeIcon")
public interface GameModeIconAccessor {
    @Accessor("mode")
    GameType ee$getMode();
}

