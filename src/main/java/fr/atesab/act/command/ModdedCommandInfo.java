package fr.atesab.act.command;

import com.mojang.brigadier.Command;
import fr.atesab.act.ACTMod;
import fr.atesab.act.command.ModdedCommandHelp.CommandClickOption;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.net.URI;

public class ModdedCommandInfo extends ModdedCommand {

    public ModdedCommandInfo() {
        super("info", "cmd.act.info", CommandClickOption.doCommand);
        addAlias("information");
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            CommandSourceStack src = c.getSource();
            src.sendSuccess(() -> Component.translatable("cmd.act.info.title").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(ACTMod.getModName()).withStyle(ChatFormatting.WHITE)), false);
            src.sendSuccess(() -> Component.translatable("cmd.act.info.version").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(ACTMod.getModVersion()).withStyle(ChatFormatting.WHITE)), false);
            src.sendSuccess(() -> Component.translatable("cmd.act.info.authors").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(ACTMod.getModAuthors()).withStyle(ChatFormatting.WHITE)), false);
            src.sendSuccess(() -> Component.translatable("cmd.act.info.licence").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(ACTMod.getModLicense()).withStyle(s -> s
                                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cmd.act.info.link.open")
                                            .withStyle(ChatFormatting.YELLOW)))
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(ACTMod.getModLicenseLink())))
                                    .withColor(ChatFormatting.WHITE))),
                    false);
            src.sendSuccess(() -> Component.translatable("cmd.act.info.link").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal("curseforge.com").withStyle(s -> s
                                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cmd.act.info.link.open")
                                            .withStyle(ChatFormatting.YELLOW)))
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(ACTMod.getModLink())))
                                    .withColor(ChatFormatting.BLUE))),
                    false);
            return 5;
        };
    }
}
