package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import fr.atesab.act.command.arguments.StringListArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModdedCommandHelp extends ModdedCommand {
    public enum CommandClickOption {
        doCommand, suggestCommand
    }

    private final ChatFormatting titleColor;
    private final ChatFormatting commandColor;
    private final ChatFormatting textColor;
    private final String title;
    private final ModdedCommand mainCommand;

    public ModdedCommandHelp(ModdedCommand mainCommand, String title, ChatFormatting titleColor,
                             ChatFormatting commandColor, ChatFormatting textColor) {
        super("help", "cmd.act.help.cmd", CommandClickOption.suggestCommand);
        this.title = title;
        this.mainCommand = mainCommand;
        this.titleColor = titleColor;
        this.commandColor = commandColor;
        this.textColor = textColor;
        addAlias("?");
    }

    public List<ModdedCommand> getVisibleSubCommand(ModdedCommand mainCommand) {
        List<ModdedCommand> sc = new ArrayList<>();
        for (ModdedCommand c : mainCommand.getSubCommands())
            if (c.isDisplayInHelp())
                sc.add(c);
        return sc;
    }

    @Override
    protected LiteralArgumentBuilder<CommandSourceStack> onArgument(
            LiteralArgumentBuilder<CommandSourceStack> command, CommandBuildContext context) {
        // show command help
        return command
                .then(Commands
                        .argument("commandforhelp", StringListArgumentType
                                .stringList(mainCommand.getSubCommandsMap().keySet(), List.of(getName()), true))
                        .executes(c -> {
                            int count = 0;
                            for (String commandName : StringListArgumentType.getStringList(c, "commandforhelp")) {
                                ModdedCommand cmd = mainCommand.getSubCommandsMap().get(commandName);
                                CommandSourceStack src = c.getSource();
                                String parentName = mainCommand.getGlobalName();
                                if (cmd != null) {
                                    count += 3;
                                    src.sendSuccess(() -> createText("-- " + I18n.get("cmd.act.help", cmd.getName()) + " --",
                                            titleColor), false);
                                    src.sendSuccess(() -> createTranslatedText(cmd.getDescriptionTranslationKey(), textColor),
                                            false);
                                    src.sendSuccess(() ->
                                            createTranslatedText("cmd.act.aliases",
                                                    titleColor)
                                                    .append(createText(": ",
                                                            ChatFormatting.DARK_GRAY))
                                                    .append(createText(
                                                            cmd.getAliases().stream()
                                                                    .filter(s -> !s.equals(cmd.getName()))
                                                                    .collect(Collectors.joining(", ")),
                                                            textColor)), // appendSibling
                                            false);
                                    Map<CommandNode<CommandSourceStack>, String> usages = getDispatcher()
                                            .getSmartUsage(cmd.getNode(), src);
                                    if (usages.isEmpty()) { // no argument
                                        showCommand(cmd, src, "/" + parentName + " " + cmd.getName(), "", false);
                                        count++;
                                    } else // arguments
                                        for (String usage : usages.values()) {
                                            showCommand(cmd, src, "/" + parentName + " " + cmd.getName(), usage, false);
                                            count++;
                                        }

                                } else {
                                    src.sendFailure(createText(
                                            I18n.get("cmd.act.mc.invalid", "/" + parentName + " " + getName()),
                                            ChatFormatting.RED));
                                    return count + 1;
                                }
                            }
                            return count;
                        }));
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        // show command list
        return c -> {
            CommandSourceStack src = c.getSource();
            int count = 1;
            src.sendSuccess(() -> createText("-- " + I18n.get("cmd.act.help", title) + " --", titleColor), false);
            Map<CommandNode<CommandSourceStack>, String> usages;
            String parentName = mainCommand.getGlobalName();
            for (ModdedCommand command : mainCommand.getSubCommands()) {
                if (command.isDisplayInHelp()) {
                    usages = getDispatcher().getSmartUsage(command.getNode(), src);
                    if (usages.isEmpty()) { // no argument
                        showCommand(command, src, "/" + parentName + " " + command.getName(), "", true);
                        count++;
                    } else // arguments
                        for (String usage : usages.values()) {
                            showCommand(command, src, "/" + parentName + " " + command.getName(), usage, true);
                            count++;
                        }
                }
            }
            return count;
        };
    }

    private void showCommand(ModdedCommand command, CommandSourceStack src, String name, String usage,
                             boolean showDescription) {
        MutableComponent component;

        if (usage.isEmpty())
            component = createText(name, commandColor);
        else
            component = createText(name + " " + usage, commandColor);

        if (command.getClickOption() == CommandClickOption.doCommand)
            component = component.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(
                            createTranslatedText("cmd.act.help.do", titleColor)))
                    .withClickEvent(new ClickEvent.RunCommand(name)));
        else
            component = component.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(
                            createTranslatedText("cmd.act.help.click", titleColor)))
                    .withClickEvent(new ClickEvent.SuggestCommand(name + " ")));
        MutableComponent finalComponent = component;
        if (showDescription)
            src.sendSuccess(() -> finalComponent.append(createText(": ", ChatFormatting.DARK_GRAY))
                    .append(createTranslatedText(command.getDescriptionTranslationKey(), textColor)), false);
        else
            src.sendSuccess(() -> finalComponent, false);
    }

}
