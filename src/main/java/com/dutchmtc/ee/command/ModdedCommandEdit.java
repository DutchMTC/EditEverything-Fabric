package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.network.EENetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public class ModdedCommandEdit extends ModdedCommand {

    public ModdedCommandEdit() {
        super("edit", "cmd.ee.edit", CommandClickOption.doCommand);
        addAlias("e");
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var source = c.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.translatable("cmd.ee.error.playeronly")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            EENetworking.sendOpenEditor(player);
            return 1;
        };
    }

}
