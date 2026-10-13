package com.dutchmtc.ee.utils;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/** The native /give item grammar, also available directly without a command prefix. */
public final class ItemCodeParser<S> {
    private final CommandDispatcher<S> dispatcher = new CommandDispatcher<>();
    private final CommandDispatcher<S> commands = new CommandDispatcher<>();
    private final CommandBuildContext context;

    public record Result(ItemStack item, String error) {}

    public ItemCodeParser(HolderLookup.Provider registry) {
        context = Commands.createValidationContext(registry);
        dispatcher.getRoot().addChild(itemArgument().build());
        for (String name : new String[] {"give", "minecraft:give"}) {
            var command =
                    LiteralArgumentBuilder.<S>literal(name)
                            .then(
                                    RequiredArgumentBuilder.<S, EntitySelector>argument(
                                                    "targets", EntityArgument.players())
                                            .then(itemArgument()));
            dispatcher.register(command);
            commands.register(command);
        }
    }

    private RequiredArgumentBuilder<S, ItemInput> itemArgument() {
        return RequiredArgumentBuilder.<S, ItemInput>argument("item", ItemArgument.item(context))
                .executes(command -> 1)
                .then(
                        RequiredArgumentBuilder.<S, Integer>argument(
                                        "count", IntegerArgumentType.integer(1))
                                .executes(command -> 1));
    }

    public CommandDispatcher<S> dispatcher(String value) {
        return value.startsWith("/") ? commands : dispatcher;
    }

    public Result read(String value, S source) {
        if (value.isBlank()) return new Result(null, "Enter an item ID or /give command.");
        var reader = new StringReader(value);
        if (reader.canRead() && reader.peek() == '/') reader.skip();
        var selected = dispatcher(value);
        var parse = selected.parse(reader, source);
        try {
            // Only this local grammar executes, and its handlers perform no game actions.
            selected.execute(parse);
            var command = parse.getContext().build(value);
            int count = 1;
            try {
                count = IntegerArgumentType.getInteger(command, "count");
            } catch (IllegalArgumentException absent) {
                /* The native default is one. */
            }
            var stack = ItemArgument.getItem(command, "item").createItemStack(count);
            return new Result(
                    stack,
                    stack.isEmpty() ? "Choose a non-empty item with a positive amount." : null);
        } catch (CommandSyntaxException exception) {
            return new Result(null, exception.getMessage());
        }
    }
}
