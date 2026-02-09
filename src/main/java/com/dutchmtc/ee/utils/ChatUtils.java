package com.dutchmtc.ee.utils;

import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.internalcommand.InternalCommand;
import com.dutchmtc.ee.internalcommand.InternalCommandModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A set of tools to help to communicate in chat with the player
 *
 * @author ATE47
 * @since 2.0
 */
@InternalCommandModule(name = "chat")
public class ChatUtils {
    public static final char MODIFIER = '\u00a7';
    private static final Map<Integer, Character> LEGACY_COLOR_BY_RGB = createLegacyColorByRgb();

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
            Tag item = ItemUtils.saveStack(itemStack, Minecraft.getInstance().level.registryAccess());
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

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);

                // &#RRGGBB
                if (next == '#' && i + 7 < text.length()) {
                    int rgb = tryParseHexColor(text, i + 2);
                    if (rgb >= 0) {
                        out.append(MODIFIER).append('x');
                        for (int j = 0; j < 6; j++) {
                            out.append(MODIFIER).append(Character.toLowerCase(text.charAt(i + 2 + j)));
                        }
                        i += 7;
                        continue;
                    }
                }

                // &x&R&R&G&G&B&B
                if ((next == 'x' || next == 'X') && i + 13 < text.length()) {
                    int rgb = tryParseAmpersandHexColor(text, i);
                    if (rgb >= 0) {
                        out.append(MODIFIER).append('x');
                        for (int j = 0; j < 6; j++) {
                            out.append(MODIFIER).append(Character.toLowerCase(text.charAt(i + 3 + j * 2)));
                        }
                        i += 13;
                        continue;
                    }
                }

                // &0-9a-fk-or (case-insensitive)
                char lower = Character.toLowerCase(next);
                if ((lower >= '0' && lower <= '9')
                        || (lower >= 'a' && lower <= 'f')
                        || (lower >= 'k' && lower <= 'o')
                        || lower == 'r') {
                    out.append(MODIFIER).append(lower);
                    i++;
                    continue;
                }
            }

            out.append(c);
        }
        return out.toString();
    }

    /**
     * Parse a string containing legacy formatting (e.g. {@code &a}, {@code &#RRGGBB}) into a styled {@link Component}.
     * <p>
     * This is required for modern item components such as {@code minecraft:custom_name} and lore: storing raw {@code §}
     * codes inside a literal component will serialize as a plain string, not a proper text component.
     */
    public static Component parseLegacyFormattingComponent(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        // Normalize to § codes (supports & codes and &#RRGGBB).
        String formatted = translateColorCodes(input);

        StringBuilder buffer = new StringBuilder();
        MutableComponent out = Component.literal("");

        Style base = Style.EMPTY.withItalic(false);
        Style current = base;

        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (c == MODIFIER && i + 1 < formatted.length()) {
                char code = Character.toLowerCase(formatted.charAt(i + 1));

                // §x§R§R§G§G§B§B
                if (code == 'x' && i + 13 < formatted.length()) {
                    int rgb = tryParseSectionHexColor(formatted, i);
                    if (rgb >= 0) {
                        if (buffer.length() > 0) {
                            out.append(Component.literal(buffer.toString()).withStyle(current));
                            buffer.setLength(0);
                        }
                        // Hex color behaves like a color code: resets other formatting.
                        current = base.withColor(rgb);
                        i += 13;
                        continue;
                    }
                }

                ChatFormatting fmt = legacyCodeToFormatting(code);
                if (fmt != null) {
                    if (buffer.length() > 0) {
                        out.append(Component.literal(buffer.toString()).withStyle(current));
                        buffer.setLength(0);
                    }

                    if (fmt == ChatFormatting.RESET) {
                        current = base;
                    } else if (fmt.isColor()) {
                        current = base.applyFormat(fmt);
                    } else {
                        current = current.applyFormat(fmt);
                    }

                    i++; // skip code
                    continue;
                }
            }

            buffer.append(c);
        }

        if (buffer.length() > 0) {
            out.append(Component.literal(buffer.toString()).withStyle(current));
        }

        return out;
    }

    /**
     * Convert a styled component into legacy color codes for editor text fields.
     * This keeps visible formatting when reopening editors.
     */
    public static String componentToLegacyCodes(Component component) {
        if (component == null) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        final boolean[] firstSegment = { true };
        final boolean[] previousStyled = { false };

        component.visit((style, text) -> {
            if (text == null || text.isEmpty()) {
                return Optional.empty();
            }

            boolean currentStyled = styleHasFormatting(style);
            if (firstSegment[0]) {
                if (currentStyled) {
                    out.append(legacyPrefixForStyle(style, false));
                }
                firstSegment[0] = false;
            } else if (currentStyled) {
                out.append(legacyPrefixForStyle(style, true));
            } else if (previousStyled[0]) {
                out.append("&r");
            }

            out.append(text);
            previousStyled[0] = currentStyled;
            return Optional.empty();
        }, Style.EMPTY);

        return out.toString();
    }

    public static String untranslateColorCodes(String text) {
        if (text == null) return null;

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == MODIFIER && i + 1 < text.length()) {
                char next = text.charAt(i + 1);

                // §x§R§R§G§G§B§B
                if ((next == 'x' || next == 'X') && i + 13 < text.length()) {
                    String hex = tryReadSectionHex(text, i);
                    if (hex != null) {
                        out.append("&#").append(hex);
                        i += 13;
                        continue;
                    }
                }

                out.append('&').append(Character.toLowerCase(next));
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static int tryParseHexColor(String s, int start) {
        if (start + 6 > s.length()) return -1;
        int rgb = 0;
        for (int i = 0; i < 6; i++) {
            int d = Character.digit(s.charAt(start + i), 16);
            if (d < 0) return -1;
            rgb = (rgb << 4) | d;
        }
        return rgb;
    }

    private static int tryParseAmpersandHexColor(String s, int ampersandIndex) {
        // &x&R&R&G&G&B&B -> 14 chars
        if (ampersandIndex + 13 >= s.length()) return -1;
        if (s.charAt(ampersandIndex) != '&') return -1;
        if (Character.toLowerCase(s.charAt(ampersandIndex + 1)) != 'x') return -1;

        int rgb = 0;
        for (int j = 0; j < 6; j++) {
            int sepIndex = ampersandIndex + 2 + j * 2;
            int digitIndex = ampersandIndex + 3 + j * 2;
            if (s.charAt(sepIndex) != '&') return -1;
            int d = Character.digit(s.charAt(digitIndex), 16);
            if (d < 0) return -1;
            rgb = (rgb << 4) | d;
        }
        return rgb;
    }

    private static int tryParseSectionHexColor(String s, int sectionIndex) {
        // §x§R§R§G§G§B§B -> 14 chars total, starting at §
        if (sectionIndex + 13 >= s.length()) return -1;
        if (s.charAt(sectionIndex) != MODIFIER) return -1;
        if (Character.toLowerCase(s.charAt(sectionIndex + 1)) != 'x') return -1;

        int rgb = 0;
        for (int j = 0; j < 6; j++) {
            int sepIndex = sectionIndex + 2 + j * 2;
            int digitIndex = sectionIndex + 3 + j * 2;
            if (s.charAt(sepIndex) != MODIFIER) return -1;
            int d = Character.digit(s.charAt(digitIndex), 16);
            if (d < 0) return -1;
            rgb = (rgb << 4) | d;
        }
        return rgb;
    }

    private static ChatFormatting legacyCodeToFormatting(char code) {
        for (ChatFormatting f : ChatFormatting.values()) {
            String s = f.toString();
            if (s.length() >= 2 && Character.toLowerCase(s.charAt(1)) == Character.toLowerCase(code)) {
                return f;
            }
        }
        return null;
    }

    private static String tryReadSectionHex(String s, int sectionIndex) {
        // §x§R§R§G§G§B§B -> 14 chars total, starting at §
        if (sectionIndex + 13 >= s.length()) return null;
        if (s.charAt(sectionIndex) != MODIFIER) return null;
        if (Character.toLowerCase(s.charAt(sectionIndex + 1)) != 'x') return null;

        StringBuilder hex = new StringBuilder(6);
        for (int j = 0; j < 6; j++) {
            int sepIndex = sectionIndex + 2 + j * 2;
            int digitIndex = sectionIndex + 3 + j * 2;
            if (s.charAt(sepIndex) != MODIFIER) return null;
            char h = s.charAt(digitIndex);
            if (Character.digit(h, 16) < 0) return null;
            hex.append(Character.toLowerCase(h));
        }
        return hex.toString();
    }

    private static boolean styleHasFormatting(Style style) {
        if (style == null) return false;
        return style.getColor() != null || style.isBold() || style.isItalic() || style.isUnderlined()
                || style.isStrikethrough() || style.isObfuscated();
    }

    private static String legacyPrefixForStyle(Style style, boolean includeReset) {
        StringBuilder prefix = new StringBuilder();
        if (includeReset) {
            prefix.append("&r");
        }

        TextColor color = style.getColor();
        if (color != null) {
            int rgb = color.getValue() & 0xFFFFFF;
            Character legacy = LEGACY_COLOR_BY_RGB.get(rgb);
            if (legacy != null) {
                prefix.append('&').append(legacy);
            } else {
                prefix.append("&#");
                String hex = Integer.toHexString(rgb);
                for (int i = hex.length(); i < 6; i++) {
                    prefix.append('0');
                }
                prefix.append(hex);
            }
        }

        if (style.isObfuscated()) prefix.append("&k");
        if (style.isBold()) prefix.append("&l");
        if (style.isStrikethrough()) prefix.append("&m");
        if (style.isUnderlined()) prefix.append("&n");
        if (style.isItalic()) prefix.append("&o");

        return prefix.toString();
    }

    private static Map<Integer, Character> createLegacyColorByRgb() {
        Map<Integer, Character> map = new HashMap<>();
        for (ChatFormatting formatting : ChatFormatting.values()) {
            if (!formatting.isColor() || formatting.getColor() == null) continue;
            map.put(formatting.getColor() & 0xFFFFFF, formatting.getChar());
        }
        return map;
    }

    private ChatUtils() {
    }
}
