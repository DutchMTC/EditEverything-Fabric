package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import fr.atesab.act.network.ACTNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ModdedCommandOpenMenu extends ModdedCommand {
    public ModdedCommandOpenMenu() {
        super("menu", "cmd.act.menu", CommandClickOption.doCommand);
        addAlias("om");
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var source = c.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.translatable("cmd.act.error.playeronly")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            ACTNetworking.sendOpenMenu(player, "");
            return 1;
        };
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("menuoptions", StringArgumentType.greedyString()).executes(c -> {
            var source = c.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.translatable("cmd.act.error.playeronly")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            ACTNetworking.sendOpenMenu(player, StringArgumentType.getString(c, "menuoptions"));
            return 1;
        }));
    }
}

