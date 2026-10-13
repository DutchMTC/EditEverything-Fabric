package com.dutchmtc.ee.mixin;

import com.dutchmtc.ee.gui.GuiGiver;
import com.dutchmtc.ee.gui.modifier.GuiCommandBlockModifier;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla anchors command-block completions at y=72; our movable editor owns its input position.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow @Final private Screen screen;
    @Shadow @Final private EditBox input;

    @ModifyConstant(method = "showSuggestions", constant = @Constant(intValue = 72))
    private int ee$anchorEditorCompletions(int vanillaY) {
        return screen instanceof GuiCommandBlockModifier || screen instanceof GuiGiver
                ? input.getBottom() + 2
                : vanillaY;
    }

    @Redirect(
            method = {"updateCommandInfo", "fillNodeUsage"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/multiplayer/ClientPacketListener;getCommands()Lcom/mojang/brigadier/CommandDispatcher;"))
    private CommandDispatcher<ClientSuggestionProvider> ee$itemCodeGrammar(
            ClientPacketListener connection) {
        return screen instanceof GuiGiver giver
                ? giver.itemCommandDispatcher()
                : connection.getCommands();
    }
}
