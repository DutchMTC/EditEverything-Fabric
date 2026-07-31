package com.dutchmtc.ee.command;

import com.mojang.brigadier.Command;
import com.dutchmtc.ee.command.ModdedCommandHelp.CommandClickOption;
import com.dutchmtc.ee.utils.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class ModdedCommandPalette extends ModdedCommand {

    public ModdedCommandPalette() {
        super("palette", "cmd.ee.palette", CommandClickOption.doCommand);
    }

    @Override
    protected Command<CommandSourceStack> onNoArgument() {
        return c -> {
            c.getSource().sendSuccess(() -> Component.translatable("cmd.ee.palette").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(":").withStyle(ChatFormatting.DARK_GRAY)), false);
            
            MutableComponent row = Component.literal("");
            int count = 0;
            for (var cf : ChatFormatting.values()) {
                if (!ChatUtils.isColor(cf)) continue;
                
                String code = "&" + ChatUtils.formattingCode(cf);
                MutableComponent colorBlock = Component.literal(" \u2588 ").withStyle(cf);
                MutableComponent codeText = Component.literal(code).withStyle(ChatFormatting.WHITE);
                
                MutableComponent entry = Component.literal("[")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(colorBlock)
                        .append(codeText)
                        .append(Component.literal("] "))
                        .withStyle(ChatFormatting.DARK_GRAY);
                        
                entry.withStyle(s -> s.withClickEvent(new ClickEvent.CopyToClipboard(code))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy " + code))));
                
                row.append(entry);
                count++;
                if (count % 4 == 0) {
                    MutableComponent finalRow = row;
                    c.getSource().sendSuccess(() -> finalRow, false);
                    row = Component.literal("");
                }
            }
            if (count % 4 != 0) {
                MutableComponent finalRow = row;
                c.getSource().sendSuccess(() -> finalRow, false);
            }
            return 1;
        };
    }
}
