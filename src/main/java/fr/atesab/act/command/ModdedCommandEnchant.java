package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atesab.act.ACTMod;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import fr.atesab.act.utils.ItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Map;

public class ModdedCommandEnchant extends ModdedCommand {

    public ModdedCommandEnchant() {
        super("enchant", "cmd.act.enchant", CommandClickOption.suggestCommand);
        addAlias("en");
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(Commands.argument("enchantname", enchantmentArgument(context)).executes(c -> {
            Holder<Enchantment> e = ResourceArgument.getEnchantment(c, "enchantname");
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return 0;
            }
            ItemStack is = mc.player.getMainHandItem().copy();
            ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(is);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
            mutable.set(e, e.value().getMaxLevel());
            EnchantmentHelper.setEnchantments(is, mutable.toImmutable());
            ItemUtils.give(is, 36 + mc.player.getInventory().selected);
            return 0;
        }).then(Commands.argument("enchantlevel", IntegerArgumentType.integer()).executes(c -> {
            Holder<Enchantment> e = ResourceArgument.getEnchantment(c, "enchantname");
            int lvl = IntegerArgumentType.getInteger(c, "enchantlevel");
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return 0;
            }
            ItemStack is = mc.player.getMainHandItem().copy();
            ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(is);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
            mutable.set(e, lvl);
            EnchantmentHelper.setEnchantments(is, mutable.toImmutable());
            ItemUtils.give(is, 36 + mc.player.getInventory().selected);
            return 0;
        })));
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return 0;
            }
            ItemStack is = mc.player.getMainHandItem();
            is = is.copy();
            ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(is);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
            for (EnchantmentInstance data : EnchantmentHelper.selectEnchantment(ACTMod.RANDOM_SOURCE, is, 30, mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).listElements().map(e -> (Holder<Enchantment>) e))) {
                mutable.set(data.enchantment, data.level);
            }
            EnchantmentHelper.setEnchantments(is, mutable.toImmutable());
            ItemUtils.give(is, 36 + mc.player.getInventory().selected);
            return 0;
        };
    }
}
