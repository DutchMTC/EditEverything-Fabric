package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.utils.PermissionCompat;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

public class ModdedCommandGamemodeQuick extends ModdedCommand {
    private final GameType gamemode;

    public ModdedCommandGamemodeQuick(String name, GameType gamemode) {
        super(name);
        this.gamemode = gamemode;
    }

    public ModdedCommandGamemodeQuick(String name, String description, boolean displayInHelp, GameType gamemode) {
        super(name, description, CommandClickOption.doCommand, displayInHelp);
        this.gamemode = gamemode;
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        command.requires(PermissionCompat::hasGamemasterPermissions);
        return command.then(Commands.argument("player", EntityArgument.player()).executes(c -> {
            var source = c.getSource();
            var target = EntityArgument.getPlayer(c, "player");
            target.setGameMode(gamemode);
            source.sendSuccess(() -> Component.translatable("commands.gamemode.success.other",
                    target.getDisplayName(), Component.translatable("gameMode." + gamemode.getName())), true);
            return 1;
        }));
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var source = c.getSource();
            var player = source.getPlayerOrException();
            player.setGameMode(gamemode);
            source.sendSuccess(() -> Component.translatable("commands.gamemode.success.self",
                    Component.translatable("gameMode." + gamemode.getName())), true);
            return 1;
        };
    }

}
