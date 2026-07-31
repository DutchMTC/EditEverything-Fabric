package com.dutchmtc.ee.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
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
            at = @At("HEAD"),
            cancellable = true
    )
    private void ee$allowGamemodeSwitcherWithoutPermissions(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event == null || minecraft == null || minecraft.getWindow() == null) {
            return;
        }

        // Avoid referencing Options keybind fields directly (they shift between patch versions).
        // Vanilla gamemode switcher is bound to F3+F4.
        if (event.key() != GLFW.GLFW_KEY_F4) {
            return;
        }

        long windowHandle = minecraft.getWindow().handle();
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_F3) != GLFW.GLFW_PRESS) {
            return;
        }
        if (!minecraft.canSwitchGameMode() || minecraft.level == null || minecraft.gui.screen() != null) {
            return;
        }

        minecraft.gui.setScreen(new GameModeSwitcherScreen());
        cir.setReturnValue(true);
    }
}
