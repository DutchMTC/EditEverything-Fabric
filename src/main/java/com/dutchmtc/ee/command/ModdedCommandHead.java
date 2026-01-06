package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ServerItemOps;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class ModdedCommandHead extends ModdedCommand {

    public ModdedCommandHead() {
        super("head", "cmd.ee.head", CommandClickOption.suggestCommand);
        addAlias("h");
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("players", GameProfileArgument.gameProfile()).executes(c -> {
            var player = c.getSource().getPlayerOrException();
            var profiles = GameProfileArgument.getGameProfiles(c, "players");
            var names = profiles.stream().map(p -> p.name() != null ? p.name() : "").toArray(String[]::new);
            try {
                for (var stack : ItemUtils.getHeads(names)) {
                    ServerItemOps.give(player, stack);
                }
            } catch (Exception e) {
                c.getSource().sendFailure(Component.literal(e.getClass().getSimpleName() + ": " + e.getMessage())
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            return names.length;
        }));
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var player = c.getSource().getPlayerOrException();
            try {
                for (var stack : ItemUtils.getHeads(player.getScoreboardName())) {
                    ServerItemOps.give(player, stack);
                }
            } catch (Exception e) {
                c.getSource().sendFailure(Component.literal(e.getClass().getSimpleName() + ": " + e.getMessage())
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            return 1;
        };
    }

}
