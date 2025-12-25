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

public class ModdedCommandInstantPlace extends ModdedCommand {
    public ModdedCommandInstantPlace() {
        super("instantplace", "cmd.act.instantplace", ModdedCommandHelp.CommandClickOption.doCommand, true);
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            ACTMod.setInstantPlaceEnabled(!ACTMod.isInstantPlaceEnabled());
            c.getSource().sendSuccess(() ->
                    ChatUtils.getPrefix()
                            .append(Component.translatable("cmd.act.instantplace").withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.translatable(ACTMod.isInstantPlaceEnabled() ? "gui.act.yes" : "gui.act.no")
                                    .withStyle(ACTMod.isInstantPlaceEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED))
                    , true);
            return 1;
        };
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("instantplace", BoolArgumentType.bool()).executes(c -> {
            ACTMod.setInstantPlaceEnabled(BoolArgumentType.getBool(c, "instantplace"));
            c.getSource().sendSuccess(() ->
                    ChatUtils.getPrefix()
                            .append(Component.translatable("cmd.act.instantplace").withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.translatable(ACTMod.isInstantPlaceEnabled() ? "gui.act.yes" : "gui.act.no")
                                    .withStyle(ACTMod.isInstantPlaceEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED))
                    , true);
            return 1;
        }));
    }
}
