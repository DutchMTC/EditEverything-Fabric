package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.dutchmtc.ee.utils.ServerItemOps;

public class ModdedCommandRename extends ModdedCommand {

    public ModdedCommandRename() {
        super("rename", "cmd.ee.rename", CommandClickOption.suggestCommand);
        addAlias("r");
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("itemname", StringArgumentType.greedyString()).executes(c -> {
            var player = c.getSource().getPlayerOrException();
            ItemStack stack = player.getMainHandItem().copy();
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(StringArgumentType.getString(c, "itemname")
                    .replaceAll("&([0-9a-fA-FrRk-oK-O])", EEMod.FORMAT_CHAR + "$1")
                    .replaceAll("&" + EEMod.FORMAT_CHAR, "&")));
            ServerItemOps.setMainHand(player, stack);
            return 1;
        }));
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var player = c.getSource().getPlayerOrException();
            ItemStack stack = player.getMainHandItem().copy();
            stack.remove(DataComponents.CUSTOM_NAME);
            ServerItemOps.setMainHand(player, stack);
            return 1;
        };
    }

}
