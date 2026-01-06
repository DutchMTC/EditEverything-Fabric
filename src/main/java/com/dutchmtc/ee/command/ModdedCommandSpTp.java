package com.dutchmtc.ee.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.util.Set;

public class ModdedCommandSpTp extends ModdedCommand {

    public ModdedCommandSpTp() {
        super("spectatortp", "cmd.ee.sptp", CommandClickOption.suggestCommand, true);
        addAlias("sptp");
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("player", EntityArgument.player()).executes(c -> {
            var source = c.getSource();
            var player = source.getPlayerOrException();
            var target = EntityArgument.getPlayer(c, "player");
            player.teleportTo(target.level(), target.getX(), target.getY(), target.getZ(),
                    Set.of(), target.getYRot(), target.getXRot(), false);
            source.sendSuccess(() -> Component.translatable("commands.teleport.success.entity.single",
                    player.getDisplayName(), target.getDisplayName()), false);
            return 1;
        }));
    }

}
