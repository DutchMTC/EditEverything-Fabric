package fr.atesab.act.utils;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import fr.atesab.act.command.ModdedCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * A reader to read {@link ItemStack} from giveCode
 */
public class ItemReader {
    private static class StackReference {
        ItemStack stack;
    }

    private static final String CMD = "give";
    private final CommandDispatcher<StackReference> dispatcher = new CommandDispatcher<>();

    public ItemReader(HolderLookup.Provider registryAccess) {
        CommandBuildContext context = Commands.createValidationContext(registryAccess);
        dispatcher.register(LiteralArgumentBuilder.<StackReference>literal(CMD)
                .then(RequiredArgumentBuilder.<StackReference, ItemInput>argument("item", ItemArgument.item(context))
                        .then(RequiredArgumentBuilder
                                .<StackReference, Integer>argument("count", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ctx.getSource().stack = ItemArgument.getItem(ctx, "item")
                                            .createItemStack(IntegerArgumentType.getInteger(ctx, "count"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().stack = ItemArgument.getItem(ctx, "item").createItemStack(1, false);
                            return 1;
                        })));
    }

    /**
     * read an item from a give code
     *
     * @param giveCode the give code
     * @return the parsed item
     */
    public ItemStack readItem(String giveCode) {
        StackReference ref = new StackReference();
        try {
            dispatcher.execute(CMD + " " + giveCode, ref);
        } catch (Exception e) {
            // ignore bad item
        }
        return ref.stack;
    }
}
