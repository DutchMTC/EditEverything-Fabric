package fr.atesab.act.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atesab.act.ACTMod;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import fr.atesab.act.command.arguments.ConnectionPlayerArgument;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ModdedCommandSpTp extends ModdedCommand {

    public ModdedCommandSpTp() {
        super("spectatortp", "cmd.act.sptp", CommandClickOption.suggestCommand, true);
        addAlias("sptp");
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("player", ConnectionPlayerArgument.player()).executes(c -> {
            var to = ConnectionPlayerArgument.getPlayer(c, "player");
            c.getSource().sendSuccess(() -> Component.translatable("specttp.tp", to.getProfile().getName()), false);
            ACTMod.spectatorTeleport(to);
            return 1;
        }));
    }

}
