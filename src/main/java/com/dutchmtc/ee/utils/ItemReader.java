package com.dutchmtc.ee.utils;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.dutchmtc.ee.command.ModdedCommand;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A reader to read {@link ItemStack} from giveCode
 */
public class ItemReader {
    private static class StackReference {
        ItemStack stack;
    }

    private static final String CMD = "give";
    private static final Pattern GIVE_PREFIX = Pattern.compile("^/?(?:(?:minecraft:)?give)\\s+\\S+\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);
    private final CommandDispatcher<StackReference> dispatcher = new CommandDispatcher<>();

    public ItemReader(HolderLookup.Provider registryAccess) {
        CommandBuildContext context = Commands.createValidationContext(registryAccess);
        dispatcher.register(LiteralArgumentBuilder.<StackReference>literal(CMD)
                .then(RequiredArgumentBuilder.<StackReference, ItemInput>argument("item", ItemArgument.item(context))
                        .then(RequiredArgumentBuilder
                                .<StackReference, Integer>argument("count", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ctx.getSource().stack = ItemArgument.getItem(ctx, "item")
                                            .createItemStack(IntegerArgumentType.getInteger(ctx, "count"));
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().stack = ItemArgument.getItem(ctx, "item").createItemStack(1);
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
        String normalized = extractItemPart(giveCode);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        StackReference ref = new StackReference();
        try {
            dispatcher.execute(CMD + " " + normalized, ref);
        } catch (Exception e) {
            // ignore bad item
        }
        return ref.stack;
    }

    /**
     * Accepts either an item argument (e.g. {@code minecraft:stone 64}) or a full give command
     * (e.g. {@code /give @p minecraft:stone 64}) and returns only the item + count part.
     */
    public static String extractItemPart(String input) {
        if (input == null) {
            return null;
        }

        String s = input.strip();
        if (s.isEmpty()) {
            return "";
        }

        int newline = s.indexOf('\n');
        if (newline >= 0) {
            s = s.substring(0, newline).strip();
        }
        newline = s.indexOf('\r');
        if (newline >= 0) {
            s = s.substring(0, newline).strip();
        }

        Matcher m = GIVE_PREFIX.matcher(s);
        if (m.matches()) {
            return m.group(1).strip();
        }
        return s;
    }
}
