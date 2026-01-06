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

public class ModdedCommandInstantPlace extends ModdedCommand {
    public ModdedCommandInstantPlace() {
        super("instantplace", "cmd.ee.instantplace", ModdedCommandHelp.CommandClickOption.doCommand, true);
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
            EENetworking.sendToggleInstantPlace(player);
            source.sendSuccess(() -> createPrefix(EEMod.getModLittleName(), ChatFormatting.GOLD, ChatFormatting.RED)
                    .append(Component.translatable("cmd.ee.instantplace").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("cmd.ee.toggle.requested").withStyle(ChatFormatting.YELLOW)), false);
            return 1;
        };
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("instantplace", BoolArgumentType.bool()).executes(c -> {
            var source = c.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.translatable("cmd.ee.error.playeronly").withStyle(ChatFormatting.RED));
                return 0;
            }
            boolean instantplace = BoolArgumentType.getBool(c, "instantplace");
            EENetworking.sendSetInstantPlace(player, instantplace);
            source.sendSuccess(() -> createPrefix(EEMod.getModLittleName(), ChatFormatting.GOLD, ChatFormatting.RED)
                    .append(Component.translatable("cmd.ee.instantplace").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(instantplace ? "gui.ee.yes" : "gui.ee.no")
                            .withStyle(instantplace ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            return 1;
        }));
    }
}
