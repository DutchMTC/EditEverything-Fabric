package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.utils.PermissionCompat;
import net.minecraft.commands.CommandSourceStack;
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
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var source = c.getSource();
            if (!PermissionCompat.hasGamemasterPermissions(source)) {
                return 0;
            }
            var player = source.getPlayerOrException();
            player.setGameMode(gamemode);
            source.sendSuccess(() -> Component.translatable("commands.gamemode.success.self",
                    Component.translatable("gameMode." + gamemode.getName())), true);
            return 1;
        };
    }

}
