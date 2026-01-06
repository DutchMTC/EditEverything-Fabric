package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ServerItemOps;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ModdedCommandUnbreakable extends ModdedCommand {

    public ModdedCommandUnbreakable() {
        super("unbreakable", "cmd.ee.unbreakable", CommandClickOption.doCommand, true);
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("unbreakable", BoolArgumentType.bool()).executes(c -> {
            var player = c.getSource().getPlayerOrException();
            var is = player.getMainHandItem().copy();
            ServerItemOps.setMainHand(player, ItemUtils.setUnbreakable(is, BoolArgumentType.getBool(c, "unbreakable")));
            return 1;
        }));
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var player = c.getSource().getPlayerOrException();
            var is = player.getMainHandItem().copy();
            ServerItemOps.setMainHand(player, ItemUtils.setUnbreakable(is, !ItemUtils.isUnbreakable(is)));
            return 1;
        };
    }

}
