package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public class ModdedCommandPalette extends ModdedCommand {

    public ModdedCommandPalette() {
        super("palette", "cmd.ee.palette", CommandClickOption.doCommand);
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var compo = Component.translatable("cmd.ee.palette").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(":").withStyle(ChatFormatting.DARK_GRAY));
            for (var cf : ChatFormatting.values()) {
                var t = "&" + cf.getChar();
                compo.append(t).append(" ").append(Component.literal(t).withStyle(cf)).append(" ");
            }
            c.getSource().sendSuccess(() -> compo, false);
            return 1;
        };
    }
}
