package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import fr.atesab.act.network.ACTNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public class ModdedCommandEdit extends ModdedCommand {

    public ModdedCommandEdit() {
        super("edit", "cmd.act.edit", CommandClickOption.doCommand);
        addAlias("e");
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
            ACTNetworking.sendOpenEditor(player);
            return 1;
        };
    }

}
