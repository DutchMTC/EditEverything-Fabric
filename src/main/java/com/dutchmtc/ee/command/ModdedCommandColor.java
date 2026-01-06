package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.network.EENetworking;
import com.dutchmtc.ee.utils.ColorMath;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ServerItemOps;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.OptionalInt;

public class ModdedCommandColor extends ModdedCommand {

    public ModdedCommandColor() {
        super("color", "cmd.ee.color", CommandClickOption.doCommand, true);

        registerSubCommand(new ModdedCommand("info", "cmd.ee.color.info", CommandClickOption.doCommand) {
            @Override
            protected Command<CommandSourceStack> onNoArgument() {
                return c -> {
                    var source = c.getSource();
                    var player = source.getPlayerOrException();
                    var is = player.getMainHandItem();
                    if (!ItemUtils.canGlobalColorIt(is)) {
                        source.sendFailure(Component.translatable("cmd.ee.color.error.notcolorable")
                                .withStyle(ChatFormatting.RED));
                        return 0;
                    }

                    OptionalInt color = ItemUtils.getGlobalColor(is);
                    if (color.isEmpty()) {
                        source.sendSuccess(() -> Component.translatable("cmd.ee.color.color")
                                        .withStyle(ChatFormatting.YELLOW)
                                        .append(Component.literal(":").withStyle(ChatFormatting.DARK_GRAY))
                                        .append(Component.translatable("cmd.ee.color.error.nocolor")
                                                .withStyle(ChatFormatting.WHITE)),
                                false);
                    } else {
                        source.sendSuccess(() -> Component.translatable("cmd.ee.color.color")
                                        .withStyle(ChatFormatting.YELLOW)
                                        .append(Component.literal(":").withStyle(ChatFormatting.DARK_GRAY))
                                        .append(Component.literal("\u2589\u2589\u2589\u2589")
                                                .withStyle(s -> s.withColor(TextColor.fromRgb(color.getAsInt()))))
                                        .append(Component.literal(" 0x" + Integer.toHexString(color.getAsInt()).toUpperCase())
                                                .withStyle(ChatFormatting.GRAY)),
                                false);
                    }
                    return 1;
                };
            }
        });

        registerSubCommand(new ModdedCommand("remove", "cmd.ee.color.remove", CommandClickOption.doCommand) {
            @Override
            protected Command<CommandSourceStack> onNoArgument() {
                return c -> {
                    var player = c.getSource().getPlayerOrException();
                    var is = player.getMainHandItem().copy();
                    ServerItemOps.setMainHand(player, ItemUtils.removeColor(is));
                    return 1;
                };
            }
        });

        registerSubCommand(new ModdedCommand("gui", "cmd.ee.color.set.gui", CommandClickOption.doCommand) {
            @Override
            protected Command<CommandSourceStack> onNoArgument() {
                return c -> {
                    var source = c.getSource();
                    var player = source.getPlayer();
                    if (player == null) {
                        source.sendFailure(Component.translatable("cmd.ee.error.playeronly")
                                .withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    EENetworking.sendOpenColorPicker(player);
                    return 1;
                };
            }
        });

        registerSubCommand(new ModdedCommand("set", "cmd.ee.color.set", CommandClickOption.suggestCommand) {
            @Override
            protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
                    LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
                return command
                        .then(Commands.literal("rgb")
                                .then(Commands.argument("red", IntegerArgumentType.integer(0, 255))
                                        .then(Commands.argument("green", IntegerArgumentType.integer(0, 255))
                                                .then(Commands.argument("blue", IntegerArgumentType.integer(0, 255))
                                                        .executes(c -> {
                                                            int r = IntegerArgumentType.getInteger(c, "red");
                                                            int g = IntegerArgumentType.getInteger(c, "green");
                                                            int b = IntegerArgumentType.getInteger(c, "blue");
                                                            applyColor(c.getSource(), ColorMath.rgb(r, g, b));
                                                            return 1;
                                                        })))))
                        .then(Commands.literal("hsl")
                                .then(Commands.argument("hue", IntegerArgumentType.integer(0, 360))
                                        .then(Commands.argument("saturation", IntegerArgumentType.integer(0, 100))
                                                .then(Commands.argument("lightness", IntegerArgumentType.integer(0, 100))
                                                        .executes(c -> {
                                                            int h = IntegerArgumentType.getInteger(c, "hue");
                                                            int s = IntegerArgumentType.getInteger(c, "saturation");
                                                            int l = IntegerArgumentType.getInteger(c, "lightness");
                                                            applyColor(c.getSource(), ColorMath.fromHsl(h, s, l));
                                                            return 1;
                                                        })))))
                        .then(Commands.literal("hex")
                                .then(Commands.argument("hexcode", StringArgumentType.word()).executes(c -> {
                                    String h = StringArgumentType.getString(c, "hexcode");
                                    if (h.startsWith("#")) {
                                        h = h.substring(1);
                                    }
                                    int rgb;
                                    try {
                                        rgb = Integer.parseInt(h, 16) & 0xFFFFFF;
                                    } catch (NumberFormatException e) {
                                        c.getSource().sendFailure(Component.translatable("cmd.ee.color.error.valid")
                                                .withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    applyColor(c.getSource(), rgb);
                                    return 1;
                                })))
                        .then(Commands.literal("random").executes(c -> {
                            applyColor(c.getSource(), ColorMath.randomRgb(EEMod.RANDOM));
                            return 1;
                        }));
            }
        });

        for (ChatFormatting formatting : ChatFormatting.values()) {
            if (!formatting.isColor() || formatting.getColor() == null) {
                continue;
            }
            String name = formatting.getName().toLowerCase();
            int rgb = formatting.getColor() & 0xFFFFFF;
            registerSubCommand(new ModdedCommand(name) {
                @Override
                protected Command<CommandSourceStack> onNoArgument() {
                    return c -> {
                        applyColor(c.getSource(), rgb);
                        return 1;
                    };
                }
            });
        }
    }

    private static void applyColor(CommandSourceStack source, int rgb) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("cmd.ee.error.playeronly")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        var is = player.getMainHandItem();
        if (!ItemUtils.canGlobalColorIt(is)) {
            source.sendFailure(Component.translatable("cmd.ee.color.error.notcolorable")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        var copy = is.copy();
        ServerItemOps.setMainHand(player, ItemUtils.setGlobalColor(copy, rgb));
    }
}
