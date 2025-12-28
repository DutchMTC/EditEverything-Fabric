package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import fr.atesab.act.command.arguments.StringListArgumentType;
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
        super("format", "cmd.act.format", CommandClickOption.doCommand, true);
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
                                createText(f.getName() + " (&" + f.toString().substring(1) + ")", ChatFormatting.YELLOW)
                                        .append(createText(": ", ChatFormatting.DARK_GRAY))
                                        .append(createText(f.getName(), f)),
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
                        format.getName() + " (&" + format.toString().substring(1) + ")", ChatFormatting.YELLOW));
                text = text.append(
                        createText("&" + format.toString().substring(1) + " ", ChatFormatting.RESET).withStyle(s -> {
                            s.withHoverEvent(he); // setHoverEvent
                            return s;
                        })); // applyTextStyle
                text = text.append(createText("&" + format.toString().substring(1), format).withStyle(s -> {
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
