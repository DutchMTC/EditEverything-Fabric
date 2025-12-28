package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import fr.atesab.act.utils.ItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ModdedCommandUnbreakable extends ModdedCommand {

    public ModdedCommandUnbreakable() {
        super("unbreakable", "cmd.act.unbreakable", CommandClickOption.doCommand, true);
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("unbreakable", BoolArgumentType.bool()).executes(c -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) {
                return 0;
            }
            var is = mc.player.getMainHandItem();
            ItemUtils.give(ItemUtils.setUnbreakable(is, BoolArgumentType.getBool(c, "unbreakable")),
                    36 + mc.player.getInventory().getSelectedSlot());
            return 0;
        }));
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) {
                return 0;
            }
            var is = mc.player.getMainHandItem();
            ItemUtils.give(ItemUtils.setUnbreakable(is, !ItemUtils.isUnbreakable(is)),
                    36 + mc.player.getInventory().getSelectedSlot());
            return 0;
        };
    }

}
