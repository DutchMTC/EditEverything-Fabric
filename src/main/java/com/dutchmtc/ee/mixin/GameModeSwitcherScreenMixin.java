package com.dutchmtc.ee.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameModeSwitcherScreen.class)
public class GameModeSwitcherScreenMixin {
    @Unique
    private boolean ee$seenDebugModifierDown;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"))
    private void ee$closeAndApplyWhenDebugKeyReleased(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return;
        }

        long windowHandle = minecraft.getWindow().handle();
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS) {
            ee$seenDebugModifierDown = true;
            return;
        }

        if (ee$seenDebugModifierDown) {
            ((GameModeSwitcherScreenInvoker) (Object) this).ee$invokeSwitchToHoveredGameMode();
            minecraft.setScreen(null);
        }
    }

    @Inject(
            method = "switchToHoveredGameMode(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/debug/GameModeSwitcherScreen$GameModeIcon;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void ee$switchGamemodeClientSide(Minecraft minecraft, @Coerce Object icon, CallbackInfo ci) {
        if (minecraft == null || icon == null || !minecraft.canSwitchGameMode()) {
            ci.cancel();
            return;
        }

        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            ci.cancel();
            return;
        }

        GameType current = minecraft.gameMode.getPlayerMode();
        GameType desired = ((GameModeIconAccessor) icon).ee$getMode();
        if (desired != current) {
            connection.sendCommand("gamemode " + desired.getName());
        }

        ci.cancel();
    }
}
