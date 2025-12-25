package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import fr.atesab.act.utils.ChatUtils;
import fr.atesab.act.utils.ItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ModdedCommandRename extends ModdedCommand {

    public ModdedCommandRename() {
        super("rename", "cmd.act.rename", CommandClickOption.suggestCommand);
        addAlias("r");
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("itemname", StringArgumentType.greedyString()).executes(c -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return 0;
            }
            ItemStack is = player.getMainHandItem();
            is.set(DataComponents.CUSTOM_NAME, Component.literal(StringArgumentType.getString(c, "itemname")
                    .replaceAll("&([0-9a-fA-FrRk-oK-O])", ChatUtils.MODIFIER + "$1")
                    .replaceAll("&" + ChatUtils.MODIFIER, "&")));
            ItemUtils.give(is, 36 + player.getInventory().selected);
            return 0;
        }));
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return 0;
            }
            ItemStack is = player.getMainHandItem();
            is = is.copy();
            is.remove(DataComponents.CUSTOM_NAME);
            ItemUtils.give(is, 36 + player.getInventory().selected);
            return 0;
        };
    }

}
