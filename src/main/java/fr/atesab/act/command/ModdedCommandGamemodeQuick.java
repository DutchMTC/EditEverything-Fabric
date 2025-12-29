package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
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
            if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
