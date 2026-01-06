package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.network.EENetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ModdedCommandInstantClick extends ModdedCommand {
    public ModdedCommandInstantClick() {
        super("instantclick", "cmd.ee.instantclick", ModdedCommandHelp.CommandClickOption.doCommand, true);
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var source = c.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.translatable("cmd.ee.error.playeronly").withStyle(ChatFormatting.RED));
                return 0;
            }
            EENetworking.sendToggleInstantClick(player);
            source.sendSuccess(() -> createPrefix(EEMod.getModLittleName(), ChatFormatting.GOLD, ChatFormatting.RED)
                    .append(Component.translatable("cmd.ee.instantclick").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("cmd.ee.toggle.requested").withStyle(ChatFormatting.YELLOW)), false);
            return 1;
        };
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("instantclick", BoolArgumentType.bool()).executes(c -> {
            var source = c.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.translatable("cmd.ee.error.playeronly").withStyle(ChatFormatting.RED));
                return 0;
            }
            boolean instantclick = BoolArgumentType.getBool(c, "instantclick");
            EENetworking.sendSetInstantClick(player, instantclick);
            source.sendSuccess(() -> createPrefix(EEMod.getModLittleName(), ChatFormatting.GOLD, ChatFormatting.RED)
                    .append(Component.translatable("cmd.ee.instantclick").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(instantclick ? "gui.ee.yes" : "gui.ee.no")
                            .withStyle(instantclick ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            return 1;
        }));
    }
}
