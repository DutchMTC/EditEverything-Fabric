package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public class ModdedCommandPalette extends ModdedCommand {

    public ModdedCommandPalette() {
        super("palette", "cmd.act.palette", CommandClickOption.doCommand);
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var compo = Component.translatable("cmd.act.palette").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(":").withStyle(ChatFormatting.DARK_GRAY));
            for (var cf : ChatFormatting.values()) {
                var t = "&" + cf.getChar();
                compo.append(t).append(" ").append(Component.literal(t).withStyle(cf)).append(" ");
            }
            c.getSource().sendSuccess(() -> compo, false);
            return 1;
        };
    }
}
