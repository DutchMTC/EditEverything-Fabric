package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.utils.ChatUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lossless editor notation. Doubled &/§ characters escape literal code introducers. */
public final class LegacyTextCodes {
    public record Encoded(String source, int[] positions) {}
    public record Decoded(String text, List<Style> styles, int[] positions, Style trailingStyle) {}

    private LegacyTextCodes() {}

    public static Encoded encode(StyledTextDocument document) {
        StringBuilder out = new StringBuilder();
        int[] positions = new int[document.text().length() + 1];
        Style previous = Style.EMPTY;
        if (document.text().isEmpty()) prefix(out, document.styleAt(0));
        for (int i = 0; i < document.text().length(); i++) {
            Style style = document.styleAt(i + 1);
            if (!sameFormatting(previous, style)) {
                if (hasFormatting(previous)) out.append("&r");
                prefix(out, style);
            }
            positions[i] = out.length();
            char c = document.text().charAt(i);
            if (c == '&' || c == '§') out.append(c);
            out.append(c);
            previous = style;
        }
        positions[document.text().length()] = out.length();
        return new Encoded(out.toString(), positions);
    }

    private static boolean hasFormatting(Style style) {
        return style.getColor() != null || style.isBold() || style.isItalic() || style.isUnderlined()
                || style.isStrikethrough() || style.isObfuscated();
    }

    private static boolean sameFormatting(Style a, Style b) {
        return Objects.equals(a.getColor(), b.getColor()) && a.isBold() == b.isBold()
                && a.isItalic() == b.isItalic() && a.isUnderlined() == b.isUnderlined()
                && a.isStrikethrough() == b.isStrikethrough() && a.isObfuscated() == b.isObfuscated();
    }

    private static void prefix(StringBuilder out, Style style) {
        if (style.getColor() != null) {
            ChatFormatting vanilla = null;
            for (ChatFormatting color : ChatFormatting.values())
                if (ChatUtils.isColor(color) && Objects.equals(ChatUtils.colorRgb(color), style.getColor().getValue())) vanilla = color;
            if (vanilla != null) out.append('&').append(ChatUtils.formattingCode(vanilla));
            else out.append(String.format("&#%06X", style.getColor().getValue()));
        }
        if (style.isBold()) out.append("&l");
        if (style.isItalic()) out.append("&o");
        if (style.isUnderlined()) out.append("&n");
        if (style.isStrikethrough()) out.append("&m");
        if (style.isObfuscated()) out.append("&k");
    }

    public static Decoded decode(String source) {
        StringBuilder text = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        int[] positions = new int[source.length() + 1];
        Style style = Style.EMPTY;
        for (int i = 0; i < source.length();) {
            int consumed = 1;
            char c = source.charAt(i);
            boolean literal = true;
            if ((c == '&' || c == '§') && i + 1 < source.length()) {
                char code = Character.toLowerCase(source.charAt(i + 1));
                if (source.charAt(i + 1) == c) consumed = 2;
                else {
                    int hex = hexLength(source, i);
                    if (hex > 0) {
                        StringBuilder digits = new StringBuilder();
                        if (code == '#') digits.append(source, i + 2, i + 8);
                        else for (int j = i + 3; j < i + 14; j += 2) digits.append(source.charAt(j));
                        style = Style.EMPTY.withColor(Integer.parseInt(digits.toString(), 16));
                        consumed = hex;
                        literal = false;
                    } else {
                        ChatFormatting format = ChatFormatting.getByCode(code);
                        if (format != null) {
                            style = format == ChatFormatting.RESET ? Style.EMPTY
                                    : ChatUtils.isColor(format) ? Style.EMPTY.applyFormat(format) : style.applyFormat(format);
                            consumed = 2;
                            literal = false;
                        }
                    }
                }
            }
            for (int j = i; j < i + consumed; j++) positions[j] = text.length();
            if (literal) { text.append(c); styles.add(style); }
            i += consumed;
            positions[i] = text.length();
        }
        return new Decoded(text.toString(), styles, positions, style);
    }

    private static int hexLength(String source, int start) {
        char code = Character.toLowerCase(source.charAt(start + 1));
        if (code == '#' && start + 8 <= source.length()) {
            for (int i = start + 2; i < start + 8; i++) if (Character.digit(source.charAt(i), 16) < 0) return 0;
            return 8;
        }
        if (code == 'x' && start + 14 <= source.length()) {
            for (int i = start + 2; i < start + 14; i += 2)
                if ((source.charAt(i) != '&' && source.charAt(i) != '§') || Character.digit(source.charAt(i + 1), 16) < 0) return 0;
            return 14;
        }
        return 0;
    }

    /** Overlay only the formatting expressed by codes; keep fonts, interactions and other metadata. */
    public static Style applyFormatting(Style original, Style formatting) {
        return original.withColor((TextColor) formatting.getColor()).withBold(formatting.isBold())
                .withItalic(formatting.isItalic()).withUnderlined(formatting.isUnderlined())
                .withStrikethrough(formatting.isStrikethrough()).withObfuscated(formatting.isObfuscated());
    }
}
