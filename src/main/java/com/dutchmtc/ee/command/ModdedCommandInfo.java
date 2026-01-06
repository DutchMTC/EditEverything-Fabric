package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.net.URI;

public class ModdedCommandInfo extends ModdedCommand {

    public ModdedCommandInfo() {
        super("info", "cmd.ee.info", CommandClickOption.doCommand);
        addAlias("information");
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            CommandSourceStack src = c.getSource();
            src.sendSuccess(() -> Component.translatable("cmd.ee.info.title").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(EEMod.getModName()).withStyle(ChatFormatting.WHITE)), false);
            src.sendSuccess(() -> Component.translatable("cmd.ee.info.version").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(EEMod.getModVersion()).withStyle(ChatFormatting.WHITE)), false);
            src.sendSuccess(() -> Component.translatable("cmd.ee.info.authors").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(EEMod.getModAuthors()).withStyle(ChatFormatting.WHITE)), false);
            src.sendSuccess(() -> Component.translatable("cmd.ee.info.licence").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(EEMod.getModLicense()).withStyle(s -> s
                                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cmd.ee.info.link.open")
                                            .withStyle(ChatFormatting.YELLOW)))
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(EEMod.getModLicenseLink())))
                                    .withColor(ChatFormatting.WHITE))),
                    false);
            src.sendSuccess(() -> Component.translatable("cmd.ee.info.link").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal("modrinth.com").withStyle(s -> s
                                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cmd.ee.info.link.open")
                                            .withStyle(ChatFormatting.YELLOW)))
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(EEMod.getModLink())))
                                    .withColor(ChatFormatting.BLUE))),
                    false);
            return 5;
        };
    }
}
