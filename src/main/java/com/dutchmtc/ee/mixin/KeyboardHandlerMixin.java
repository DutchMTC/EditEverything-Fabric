package com.dutchmtc.ee.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Shadow
    private Minecraft minecraft;

    @Inject(
            method = "handleDebugKeys(Lnet/minecraft/client/input/KeyEvent;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/permissions/PermissionCheck;check(Lnet/minecraft/server/permissions/PermissionSet;)Z"
            ),
            cancellable = true
    )
    private void ee$allowGamemodeSwitcherWithoutPermissions(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!minecraft.options.keyDebugSwitchGameMode.matches(event)) {
            return;
        }
        if (!minecraft.canSwitchGameMode() || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        minecraft.setScreen(new GameModeSwitcherScreen());
        cir.setReturnValue(true);
    }
}
