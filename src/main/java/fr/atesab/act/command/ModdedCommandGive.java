package fr.atesab.act.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import fr.atesab.act.utils.ServerItemOps;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;

public class ModdedCommandGive extends ModdedCommand {
    public ModdedCommandGive() {
        super("give", "cmd.act.give", CommandClickOption.suggestCommand, true);
        addAlias("g");
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("giveoption", ItemArgument.item(context))
                .then(Commands.argument("givecount", IntegerArgumentType.integer()).executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    ServerItemOps.give(player, ItemArgument.getItem(c, "giveoption")
                            .createItemStack(IntegerArgumentType.getInteger(c, "givecount"), false));
                    return 1;
                })).executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    ServerItemOps.give(player, ItemArgument.getItem(c, "giveoption").createItemStack(1, false));
                    return 1;
                }));
    }

}
