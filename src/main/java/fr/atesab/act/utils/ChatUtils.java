package fr.atesab.act.utils;

import fr.atesab.act.ACTMod;
import fr.atesab.act.internalcommand.InternalCommand;
import fr.atesab.act.internalcommand.InternalCommandModule;
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
        return getPrefix(Component.translatable("gui.act.error").withStyle(ChatFormatting.RED),
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
                        .append(Component.literal(ACTMod.getModLittleName()).withStyle(ChatFormatting.GOLD)));
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
            send(getPrefix().append(Component.translatable("gui.act.give.msg").append(": ")
                    .withStyle(ChatFormatting.GOLD).append(itemStack.getDisplayName().copy().withStyle(style -> {
                        style.withHoverEvent(
                                new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(itemStack)));
                        style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                ACTMod.getModCommand().getName() + " " + ACTMod.getModCommand().SC_OPEN_GIVER.getName()
                                        + " " + ItemUtils.getGiveCode(itemStack)));
                        return style;
                    }))));
        } else
            error(I18n.get("gui.act.give.fail2"));
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
        Player plr = Minecraft.getInstance().player;
        if (plr != null) {
            plr.sendSystemMessage(message);
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

    private ChatUtils() {
    }
}
