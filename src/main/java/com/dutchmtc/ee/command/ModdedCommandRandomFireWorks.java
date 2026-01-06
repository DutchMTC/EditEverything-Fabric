package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ServerItemOps;
import net.minecraft.commands.CommandSourceStack;

public class ModdedCommandRandomFireWorks extends ModdedCommand {
    public ModdedCommandRandomFireWorks() {
        super("randomfireworks", "cmd.ee.rfw", CommandClickOption.doCommand);
        addAlias("rfw");
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var player = c.getSource().getPlayerOrException();
            ServerItemOps.give(player, ItemUtils.getRandomFireworks());
            return 1;
        };
    }

}
