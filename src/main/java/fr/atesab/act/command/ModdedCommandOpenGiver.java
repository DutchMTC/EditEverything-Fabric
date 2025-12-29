package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import fr.atesab.act.utils.ItemUtils;
import fr.atesab.act.network.ACTNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

public class ModdedCommandOpenGiver extends ModdedCommand {

    public ModdedCommandOpenGiver() {
        super("opengiver", "cmd.act.opengiver", CommandClickOption.doCommand, true);
        addAlias("og");
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("giveroptions", StringArgumentType.greedyString()).executes(c -> {
            var source = c.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.translatable("cmd.act.error.playeronly")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            ACTNetworking.sendOpenGiver(player, StringArgumentType.getString(c, "giveroptions"));
            return 1;
        }));
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
            var code = ItemUtils.getGiveCode(player.getItemInHand(InteractionHand.MAIN_HAND));
            ACTNetworking.sendOpenGiver(player, code);
            return 1;
        };
    }

}
