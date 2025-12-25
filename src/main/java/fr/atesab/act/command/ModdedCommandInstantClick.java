package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atesab.act.ACTMod;
import fr.atesab.act.utils.ChatUtils;
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
            ACTMod.setInstantMineEnabled(!ACTMod.isInstantMineEnabled());
            c.getSource().sendSuccess(() ->
                    ChatUtils.getPrefix()
                            .append(Component.translatable("cmd.act.instantclick").withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.translatable(ACTMod.isInstantMineEnabled() ? "gui.act.yes" : "gui.act.no")
                                    .withStyle(ACTMod.isInstantMineEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED))
                    , true);
            return 1;
        };
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("instantclick", BoolArgumentType.bool()).executes(c -> {
            boolean instantclick = BoolArgumentType.getBool(c, "instantclick");
            ACTMod.setInstantMineEnabled(instantclick);
            c.getSource().sendSuccess(() ->
                    ChatUtils.getPrefix()
                            .append(Component.translatable("cmd.act.instantclick").withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.translatable(ACTMod.isInstantMineEnabled() ? "gui.act.yes" : "gui.act.no")
                                    .withStyle(ACTMod.isInstantMineEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED))
                    , true);
            return 1;
        }));
    }
}
