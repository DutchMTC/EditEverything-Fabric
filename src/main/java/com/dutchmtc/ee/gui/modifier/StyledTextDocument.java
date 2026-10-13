package com.dutchmtc.ee.gui.modifier;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Editable plain text with a style for each character. Independent of where the text is stored. */
public final class StyledTextDocument {
    private String text;
    private final List<Style> styles = new ArrayList<>();
    private Style insertionStyle = Style.EMPTY;
    private String legacySource;

    public StyledTextDocument(Component value) {
        StringBuilder plain = new StringBuilder();
        if (value != null) value.visit((style, segment) -> {
            plain.append(segment);
            for (int i = 0; i < segment.length(); i++) styles.add(style);
            return Optional.empty();
        }, Style.EMPTY);
        text = plain.toString();
    }

    public String text() { return text; }

    public boolean legacyMode() { return legacySource != null; }
    public String inputText() { return legacyMode() ? legacySource : text; }

    /** Returns a map from old caret/selection offsets to offsets in the new input mode. */
    public int[] switchInputMode() {
        if (legacyMode()) {
            int[] positions = LegacyTextCodes.decode(legacySource).positions();
            legacySource = null;
            return positions;
        }
        LegacyTextCodes.Encoded encoded = LegacyTextCodes.encode(this);
        legacySource = encoded.source();
        return encoded.positions();
    }

    public void replaceInput(String next) {
        if (!legacyMode()) { replaceText(next); return; }
        if (legacySource.equals(next)) return;
        legacySource = next;
        LegacyTextCodes.Decoded decoded = LegacyTextCodes.decode(next);
        replaceText(decoded.text());
        for (int i = 0; i < styles.size(); i++)
            styles.set(i, LegacyTextCodes.applyFormatting(styles.get(i), decoded.styles().get(i)));
        insertionStyle = LegacyTextCodes.applyFormatting(insertionStyle, decoded.trailingStyle());
    }

    /** Scrambling is a display effect, not a reliable glyph layout for editing. */
    public Component editingComponent(int from, int to) {
        MutableComponent result = Component.empty();
        component(from, to).visit((style, segment) -> {
            result.append(Component.literal(segment).withStyle(style.withObfuscated(false)));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public Component editingComponent() { return editingComponent(0, text.length()); }

    public boolean canApplyWithinWidth(int from, int to, UnaryOperator<Style> change, Font font, int maxWidth) {
        return copy().applyWithinWidth(from, to, change, font, maxWidth);
    }

    /** Distribute colors by code point so a surrogate pair always has one color. */
    public void applyColor(int from, int to, com.dutchmtc.ee.utils.TextColorPreset preset) {
        int first = Math.max(0, Math.min(from, text.length()));
        int last = Math.max(first, Math.min(to, text.length()));
        int count = text.codePointCount(first, last), position = 0;
        for (int i = first; i < last;) {
            int next = Math.min(last, i + Character.charCount(text.codePointAt(i)));
            int color = preset.colorAt(count < 2 ? 0 : position / (double) (count - 1));
            for (int j = i; j < next; j++) styles.set(j, styles.get(j).withColor(color));
            i = next;
            position++;
        }
    }

    /** Check an EditBox candidate using the same styled font width used on the sign. */
    public boolean canReplaceWithinWidth(String next, Font font, int maxWidth) {
        StyledTextDocument preview = copy();
        preview.replaceText(next);
        int nextWidth = font.width(preview.editingComponent());
        return nextWidth <= maxWidth || nextWidth < font.width(editingComponent());
    }

    /** Reject a style change if it would make an existing line wider than its limit. */
    public boolean applyWithinWidth(int from, int to, UnaryOperator<Style> change, Font font, int maxWidth) {
        if (maxWidth == Integer.MAX_VALUE) {
            apply(from, to, change);
            return true;
        }
        List<Style> previousStyles = new ArrayList<>(styles);
        Style previousInsertionStyle = insertionStyle;
        int previousWidth = font.width(editingComponent());
        apply(from, to, change);
        int nextWidth = font.width(editingComponent());
        if (nextWidth > maxWidth && nextWidth > previousWidth) {
            styles.clear();
            styles.addAll(previousStyles);
            insertionStyle = previousInsertionStyle;
            return false;
        }
        return true;
    }

    private StyledTextDocument copy() {
        StyledTextDocument copy = new StyledTextDocument(null);
        copy.text = text;
        copy.styles.addAll(styles);
        copy.insertionStyle = insertionStyle;
        return copy;
    }

    public void replaceText(String next) {
        if (next.equals(text)) return;
        int prefix = 0;
        while (prefix < text.length() && prefix < next.length() && text.charAt(prefix) == next.charAt(prefix)) prefix++;
        int suffix = 0;
        while (suffix < text.length() - prefix && suffix < next.length() - prefix
                && text.charAt(text.length() - 1 - suffix) == next.charAt(next.length() - 1 - suffix)) suffix++;
        Style inserted = prefix > 0 ? styles.get(prefix - 1)
                : prefix < styles.size() ? styles.get(prefix) : insertionStyle;
        for (int i = text.length() - suffix - 1; i >= prefix; i--) styles.remove(i);
        for (int i = prefix; i < next.length() - suffix; i++) styles.add(i, inserted);
        text = next;
    }

    public void apply(int from, int to, UnaryOperator<Style> change) {
        if (styles.isEmpty()) {
            insertionStyle = change.apply(insertionStyle);
            return;
        }
        int start = Math.max(0, Math.min(from, styles.size()));
        int end = Math.max(start, Math.min(to, styles.size()));
        if (start == end) { start = 0; end = styles.size(); }
        for (int i = start; i < end; i++) styles.set(i, change.apply(styles.get(i)));
    }

    public Style styleAt(int cursor) {
        if (styles.isEmpty()) return insertionStyle;
        return styles.get(Math.max(0, Math.min(cursor == 0 ? 0 : cursor - 1, styles.size() - 1)));
    }

    /** Preserves per-character formatting while EditBox renders a visible substring. */
    public FormattedCharSequence formattedSlice(String value, int offset) {
        return formattedSlice(value, offset, false);
    }

    public FormattedCharSequence formattedSlice(String value, int offset, boolean editing) {
        if (value.isEmpty()) return FormattedCharSequence.EMPTY;
        List<FormattedCharSequence> parts = new ArrayList<>();
        int run = 0;
        for (int i = 1; i <= value.length(); i++) {
            if (i == value.length() || !styleAt(offset + i + 1).equals(styleAt(offset + run + 1))) {
                Style style = styleAt(offset + run + 1);
                if (editing) style = style.withObfuscated(false);
                parts.add(FormattedCharSequence.forward(value.substring(run, i), style));
                run = i;
            }
        }
        return FormattedCharSequence.composite(parts);
    }

    public Component component() {
        return component(0, text.length());
    }

    public Component component(int from, int to) {
        MutableComponent result = Component.empty();
        int first = Math.max(0, Math.min(from, text.length()));
        int last = Math.max(first, Math.min(to, text.length()));
        if (first == last) return result;
        int start = first;
        for (int i = first + 1; i <= last; i++) {
            if (i == last || !styles.get(i).equals(styles.get(start))) {
                result.append(Component.literal(text.substring(start, i)).withStyle(styles.get(start)));
                start = i;
            }
        }
        return result;
    }
}
