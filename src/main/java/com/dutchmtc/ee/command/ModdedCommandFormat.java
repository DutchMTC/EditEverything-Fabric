package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.command.arguments.StringListArgumentType;
import com.dutchmtc.ee.utils.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class ModdedCommandFormat extends ModdedCommand {
    public static final int ELEMENT_PER_LINE = 8;

    public ModdedCommandFormat() {
        super("format", "cmd.ee.format", CommandClickOption.doCommand, true);
        addAlias("f");
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        return command.then(
                Commands.argument("formatname", StringListArgumentType.enumList(ChatFormatting.class)).executes(c -> {
                    ChatFormatting[] element = StringListArgumentType.getEnumList(ChatFormatting.class, c,
                            "formatname");
                    for (ChatFormatting f : element) {
                        c.getSource().sendSuccess(() ->
                                createText(ChatUtils.formattingName(f) + " (&" + ChatUtils.formattingCode(f) + ")", ChatFormatting.YELLOW)
                                        .append(createText(": ", ChatFormatting.DARK_GRAY))
                                        .append(createText(ChatUtils.formattingName(f), f)),
                                false);
                    }

                    return element.length;
                }));
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            MutableComponent text = Component.literal("");
            int element = 0;
            int line = 0;
            for (ChatFormatting format : ChatFormatting.values()) {
                HoverEvent he = new HoverEvent.ShowText(createText(
                        ChatUtils.formattingName(format) + " (&" + ChatUtils.formattingCode(format) + ")", ChatFormatting.YELLOW));
                text = text.append(
                        createText("&" + ChatUtils.formattingCode(format) + " ", ChatFormatting.RESET).withStyle(s -> {
                            s.withHoverEvent(he); // setHoverEvent
                            return s;
                        })); // applyTextStyle
                text = text.append(createText("&" + ChatUtils.formattingCode(format), format).withStyle(s -> {
                            s.withHoverEvent(he); // setHoverEvent
                            return s;
                        }) // applyTextStyle
                ).append(createText(" ", ChatFormatting.RESET));
                if (++element == ELEMENT_PER_LINE) {
                    Component finalText = text;
                    c.getSource().sendSuccess(() -> finalText, false);
                    text = Component.literal("");
                    element = 0;
                    line++;
                }
            }
            if (element != 0) {
                Component finalText = text;
                c.getSource().sendSuccess(() -> finalText, false);
                line++;
            }
            return line;
        };
    }

}
