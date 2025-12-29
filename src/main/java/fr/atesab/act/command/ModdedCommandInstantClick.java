package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atesab.act.ACTMod;
import fr.atesab.act.network.ACTNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ModdedCommandInstantClick extends ModdedCommand {
    public ModdedCommandInstantClick() {
        super("instantclick", "cmd.act.instantclick", ModdedCommandHelp.CommandClickOption.doCommand, true);
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var source = c.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.translatable("cmd.act.error.playeronly").withStyle(ChatFormatting.RED));
                return 0;
            }
            ACTNetworking.sendToggleInstantClick(player);
            source.sendSuccess(() -> createPrefix(ACTMod.getModLittleName(), ChatFormatting.GOLD, ChatFormatting.RED)
                    .append(Component.translatable("cmd.act.instantclick").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("cmd.act.toggle.requested").withStyle(ChatFormatting.YELLOW)), false);
            return 1;
        };
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("instantclick", BoolArgumentType.bool()).executes(c -> {
            var source = c.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.translatable("cmd.act.error.playeronly").withStyle(ChatFormatting.RED));
                return 0;
            }
            boolean instantclick = BoolArgumentType.getBool(c, "instantclick");
            ACTNetworking.sendSetInstantClick(player, instantclick);
            source.sendSuccess(() -> createPrefix(ACTMod.getModLittleName(), ChatFormatting.GOLD, ChatFormatting.RED)
                    .append(Component.translatable("cmd.act.instantclick").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(instantclick ? "gui.act.yes" : "gui.act.no")
                            .withStyle(instantclick ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            return 1;
        }));
    }
}
