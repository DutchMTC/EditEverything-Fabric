package com.dutchmtc.ee.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.utils.PermissionCompat;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

public class ModdedCommandGamemode extends ModdedCommand {

    public ModdedCommandGamemode(String name) {
        super(name);
    }

    public ModdedCommandGamemode(String name, String description, CommandClickOption clickOption) {
        super(name, description, clickOption);
    }

    public ModdedCommandGamemode(String name, String description, CommandClickOption clickOption,
                                 boolean displayInHelp) {
        super(name, description, clickOption, displayInHelp);
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        command.requires(PermissionCompat::hasGamemasterPermissions);
        // /gm <gamemode>
        for (GameType gametype : GameType.values()) {
            command.then(Commands.literal(gametype.getName())
                    .executes(c -> {
                        var source = c.getSource();
                        var player = source.getPlayerOrException();
                        player.setGameMode(gametype);
                        source.sendSuccess(() -> Component.translatable("commands.gamemode.success.self",
                                Component.translatable("gameMode." + gametype.getName())), true);
                        return 1;
                    })
                    .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                        var source = c.getSource();
                        var target = EntityArgument.getPlayer(c, "player");
                        target.setGameMode(gametype);
                        source.sendSuccess(() -> Component.translatable("commands.gamemode.success.other",
                                target.getDisplayName(), Component.translatable("gameMode." + gametype.getName())),
                                true);
                        return 1;
                    })));
        }
        // /gm 0,1,2,3
        return command.then(Commands
                .argument("gamemodeid", IntegerArgumentType.integer(0, GameType.values().length - 1))
                .executes(c -> {
                    var source = c.getSource();
                    var player = source.getPlayerOrException();
                    var gametype = GameType.byId(IntegerArgumentType.getInteger(c, "gamemodeid"));
                    player.setGameMode(gametype);
                    source.sendSuccess(() -> Component.translatable("commands.gamemode.success.self",
                            Component.translatable("gameMode." + gametype.getName())), true);
                    return 1;
                })
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    var source = c.getSource();
                    var target = EntityArgument.getPlayer(c, "player");
                    var gametype = GameType.byId(IntegerArgumentType.getInteger(c, "gamemodeid"));
                    target.setGameMode(gametype);
                    source.sendSuccess(() -> Component.translatable("commands.gamemode.success.other",
                            target.getDisplayName(), Component.translatable("gameMode." + gametype.getName())),
                            true);
                    return 1;
                })));
    }

}
