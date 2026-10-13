package com.dutchmtc.ee.gui.components;

import com.dutchmtc.ee.gui.modifier.StyledTextDocument;
import com.dutchmtc.ee.gui.modifier.TextSelectionToolbar;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;

import java.util.Optional;
import java.util.function.Consumer;

/** A normal input field with per-character styles and an optional floating selection toolbar. */
public final class StyledTextField extends EETextField {
    private final StyledTextDocument document;
    private final Font font;
    private final int visibleLength;
    private int maxPixelWidth = Integer.MAX_VALUE;
    private boolean refreshing;
    private Consumer<Component> styledResponder = ignored -> {};

    public StyledTextField(Font font, int x, int y, int width, int height, Component label,
                           StyledTextDocument document, int maxLength) {
        super(font, x, y, width, height, label);
        this.font = font;
        this.document = document;
        this.visibleLength = maxLength;
        setBordered(false);
        setTextShadow(false);
        setMaxLength(document.legacyMode() ? codeLengthLimit() : maxLength);
        setValue(document.inputText());
        document.replaceInput(getValue());
        addFormatter((value, offset) -> document.legacyMode()
                ? FormattedCharSequence.forward(value, Style.EMPTY)
                : document.formattedSlice(value, offset, isFocused()));
        setResponder(value -> {
            if (refreshing) return;
            document.replaceInput(value);
            notifyStyledChange();
        });
    }

    public StyledTextDocument document() { return document; }
    @Override protected boolean preserveTextColors() { return true; }

    private int codeLengthLimit() { return Math.max(1024, visibleLength * 32 + 64); }
    public void setPixelLimit(int limit) { maxPixelWidth = limit; }
    public boolean validInput() {
        return document.text().length() <= visibleLength && font.width(document.editingComponent()) <= maxPixelWidth;
    }
    public String invalidReason() {
        return document.text().length() > visibleLength ? "Text exceeds the " + visibleLength + " character limit."
                : "The resulting text exceeds this line's " + maxPixelWidth + " px limit. Shorten it or remove bold.";
    }

    public void switchInputMode() {
        EditBoxAccessor accessor = (EditBoxAccessor) (EditBox) this;
        int cursor = getCursorPosition(), anchor = accessor.ee$getHighlightPos();
        int[] positions = document.switchInputMode();
        refreshing = true;
        try {
            setMaxLength(document.legacyMode() ? codeLengthLimit() : visibleLength);
            setValue(document.inputText());
            moveCursorTo(positions[Math.min(cursor, positions.length - 1)], false);
            setHighlightPos(positions[Math.min(anchor, positions.length - 1)]);
            accessor.ee$setDisplayPos(0);
        } finally { refreshing = false; }
        notifyStyledChange();
    }

    @Override public void insertText(String text) {
        if (document.legacyMode()) { super.insertText(text.replace('§', '&')); return; }
        if (maxPixelWidth != Integer.MAX_VALUE) {
            int anchor = ((EditBoxAccessor) (EditBox) this).ee$getHighlightPos();
            int start = Math.min(getCursorPosition(), anchor), end = Math.max(getCursorPosition(), anchor);
            String inserted = StringUtil.filterText(text);
            int available = Math.max(0, visibleLength - getValue().length() + end - start);
            if (inserted.length() > available) {
                if (available > 0 && Character.isHighSurrogate(inserted.charAt(available - 1))) available--;
                inserted = inserted.substring(0, available);
            }
            String candidate = getValue().substring(0, start) + inserted + getValue().substring(end);
            if (!document.canReplaceWithinWidth(candidate, font, maxPixelWidth)) return;
        }
        super.insertText(text);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        if (document.legacyMode() && isFocused() && event.codepoint() == '§') { insertText("&"); return true; }
        return super.charTyped(event);
    }

    public void keepCursorVisible() { keepStyledCursorVisible(); }
    private int width(int from, int to) {
        return document.legacyMode() ? font.width(FormattedCharSequence.forward(getValue().substring(from, to), Style.EMPTY))
                : font.width(document.editingComponent(from, to));
    }

    /** Called for typing and toolbar changes, preserving colors and styles in the saved component. */
    public void setStyledResponder(Consumer<Component> responder) { styledResponder = responder; }

    private void notifyStyledChange() { styledResponder.accept(document.component()); }

    private void keepStyledCursorVisible() {
        EditBoxAccessor accessor = (EditBoxAccessor) (EditBox) this;
        int cursor = getCursorPosition();
        int first = Math.max(0, Math.min(accessor.ee$getDisplayPos(), cursor));
        if (first > 0 && first < getValue().length() && Character.isLowSurrogate(getValue().charAt(first))) first--;
        while (first < cursor && width(first, cursor) > getInnerWidth())
            first += Character.charCount(getValue().codePointAt(first));
        accessor.ee$setDisplayPos(first);
    }

    /** The host handles toolbar clicks first and renders it after its other widgets, outside clipping. */
    public TextSelectionToolbar createToolbar(Screen host, int minX, int maxX) {
        return new TextSelectionToolbar(host, this, document, font, minX, maxX, this::notifyStyledChange);
    }

    private int indexAt(double screenX) {
        EditBoxAccessor accessor = (EditBoxAccessor) (EditBox) this;
        int first = Math.min(accessor.ee$getDisplayPos(), getValue().length());
        double x = screenX - accessor.ee$getTextX();
        if (x <= 0) return first;
        int width = 0;
        String value = getValue();
        for (int index = first; index < value.length();) {
            int next = index + Character.charCount(value.codePointAt(index));
            int nextWidth = width(first, next);
            if (x < (width + nextWidth) / 2.0) return index;
            index = next;
            width = nextWidth;
        }
        return value.length();
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int position = indexAt(event.x());
        if (doubleClick) {
            moveCursorTo(position, false);
            int wordStart = getWordPosition(-1), wordEnd = getWordPosition(1);
            moveCursorTo(wordStart, false);
            moveCursorTo(wordEnd, true);
        } else moveCursorTo(position, event.hasShiftDown());
    }

    @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        moveCursorTo(indexAt(event.x()), true);
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (!isVisible()) return;
        setError(validInput() ? null : invalidReason());
        keepStyledCursorVisible();
        EditBoxAccessor accessor = (EditBoxAccessor) (EditBox) this;
        int anchor = accessor.ee$getHighlightPos(), cursor = getCursorPosition();
        accessor.ee$setTextY(getY() + (getHeight() - font.lineHeight) / 2);
        int textX = accessor.ee$getTextX();
        g.enableScissor(textX, getY(), textX + getInnerWidth(), getY() + getHeight());
        try {
            // Vanilla measures highlights with plain text; the field displays styled glyph widths.
            if (anchor != cursor) accessor.ee$setHighlightPos(cursor);
            try {
                super.extractWidgetRenderState(g, mx, my, delta);
            } finally {
                if (anchor != cursor) accessor.ee$setHighlightPos(anchor);
            }
            if (anchor == cursor) return;
            int first = Math.min(accessor.ee$getDisplayPos(), getValue().length());
            int start = Math.max(first, Math.min(anchor, cursor));
            int end = Math.max(first, Math.max(anchor, cursor));
            int left = textX + width(first, start);
            int right = textX + width(first, end);
            left = Math.max(textX, Math.min(left, textX + getInnerWidth()));
            right = Math.max(textX, Math.min(right, textX + getInnerWidth()));
            if (right > left) {
                g.textHighlight(left, accessor.ee$getTextY() - 1, right,
                        accessor.ee$getTextY() + font.lineHeight + 1, true);
                if (document.legacyMode()) {
                    g.text(font, FormattedCharSequence.forward(getValue().substring(start, end), Style.EMPTY),
                            left, accessor.ee$getTextY(), 0xFF202020, false);
                    return;
                }
                MutableComponent selected = Component.empty();
                document.editingComponent(start, end).visit((style, segment) -> {
                    selected.append(Component.literal(segment).withStyle(style.withColor((TextColor) null)));
                    return Optional.empty();
                }, Style.EMPTY);
                g.text(font, selected, left, accessor.ee$getTextY(), 0xFF202020, false);
            }
        } finally {
            g.disableScissor();
            renderValidation(g);
        }
    }
}
