package com.dutchmtc.ee.utils;

import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.internalcommand.InternalCommand;
import com.dutchmtc.ee.internalcommand.InternalCommandModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A set of tools to help to communicate in chat with the player
 *
 * @author ATE47
 * @since 2.0
 */
@InternalCommandModule(name = "chat")
public class ChatUtils {
    public static final char MODIFIER = '\u00a7';

    /**
     * Send an error
     *
     * @param error error message
     * @see ChatUtils#show(String)
     * @since 2.0
     */
    public static void error(String error) {
        send(getErrorPrefix().append(error));
    }

    /**
     * create an error prefix
     *
     * @return the prefix
     * @see ChatUtils#getPrefix()
     * @since 2.0
     */
    public static MutableComponent getErrorPrefix() {
        return getPrefix(Component.translatable("gui.ee.error").withStyle(ChatFormatting.RED),
                ChatFormatting.WHITE);
    }

    /**
     * create a default prefix
     *
     * @return the prefix
     * @see ChatUtils#getErrorPrefix()
     * @since 2.0
     */
    public static MutableComponent getPrefix() {
        return getPrefix(null, null);
    }

    private static MutableComponent getPrefix(Component notif, ChatFormatting endColor) {
        MutableComponent p = Component.literal("").withStyle(endColor != null ? endColor : ChatFormatting.WHITE)
                .append(Component.literal("[").withStyle(ChatFormatting.RED)
                        .append(Component.literal(EEMod.getModLittleName()).withStyle(ChatFormatting.GOLD)));
        if (notif != null)
            p.append(Component.literal("/").withStyle(ChatFormatting.WHITE)).append(notif);
        return p.append(Component.literal("]").withStyle(ChatFormatting.RED)).append(Component.literal(" "));
    }

    /**
     * Send a message to say an {@link ItemStack} has been given in the inventory
     *
     * @param itemStack the stack
     * @since 2.0
     */
    @InternalCommand(name = "showstack")
    public static void itemStack(ItemStack itemStack) {
        if (itemStack != null) {
            CompoundTag item = new CompoundTag();
            item.putString("id", ItemUtils.getRegistry(itemStack).toString());
            item.putInt("Count", itemStack.getCount());
            if (ItemUtils.getTag(itemStack) != null)
                item.put("tag", ItemUtils.getTag(itemStack));
            send(getPrefix().append(Component.translatable("gui.ee.give.msg").append(": ")
                    .withStyle(ChatFormatting.GOLD).append(itemStack.getDisplayName().copy().withStyle(style -> {
                        style.withHoverEvent(
                                new HoverEvent.ShowItem(itemStack));
                        style.withClickEvent(new ClickEvent.RunCommand(
                                "/" + EEMod.getModCommand().getName() + " " + EEMod.getModCommand().SC_OPEN_GIVER.getName()
                                        + " " + ItemUtils.getGiveCode(itemStack)));
                        return style;
                    }))));
        } else
            error(I18n.get("gui.ee.give.fail2"));
    }

    /**
     * Send a {@link Component} to chat
     *
     * @param message The {@link Component}
     * @see ChatUtils#show(String)
     * @see ChatUtils#error(String)
     * @since 2.0
     */
    @InternalCommand(name = "showraw")
    public static void send(Component message) {
        var mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(message);
        }
    }

    /**
     * Send a text message
     *
     * @param message The message
     * @see ChatUtils#error(String)
     * @since 2.0
     */
    @InternalCommand(name = "show")
    public static void show(String message) {
        send(getPrefix().append(message));
    }

    /**
     * Translate & to § only if it is followed by a valid color code
     * @param text the text to translate
     * @return the translated text
     */
    public static String translateColorCodes(String text) {
        if (text == null) return null;
        StringBuilder b = new StringBuilder();
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '&' && i + 1 < chars.length) {
                char next = chars[i + 1];
                if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f') || (next >= 'k' && next <= 'o') || next == 'r') {
                    b.append(MODIFIER);
                } else if ((next >= 'A' && next <= 'F') || (next >= 'K' && next <= 'O') || next == 'R') {
                    b.append(MODIFIER);
                    chars[i+1] = Character.toLowerCase(next);
                } else {
                    b.append(c);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private ChatUtils() {
    }
}
