package com.dutchmtc.ee.gui.components;

import com.dutchmtc.ee.gui.modifier.StyledTextDocument;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.*;
import java.util.function.UnaryOperator;

/** Direct rich-text editing on vanilla book paper, using native wrapping and clipboard handling. */
public final class StyledPageField extends AbstractWidget implements TextSelectionTarget {
    public record Line(int start, int end) {}

    private final Font font;
    private final StyledTextDocument document;
    private final TextFieldHelper helper;
    private final Runnable changed;
    private List<Line> lines = List.of();
    private int scroll;
    private final int characterLimit;
    private final int lineLimit;
    private int defaultColor;
    private final boolean legacyCodes;
    private int lineHeight = 9, wrapWidth, horizontalScroll, textBackground;
    private String alignment = "left";
    private boolean centeredVertically, textShadow;

    public StyledPageField(
            Font font,
            int x,
            int y,
            StyledTextDocument document,
            int characterLimit,
            Runnable changed) {
        this(font, x, y, 114, 126, document, characterLimit, 14, false, 0xFF000000, changed);
        setMessage(Component.literal("Book page"));
    }

    /** The same rich-text input can be used outside book paper with screen-owned sizing. */
    public StyledPageField(
            Font font,
            int x,
            int y,
            int width,
            int height,
            StyledTextDocument document,
            int characterLimit,
            int lineLimit,
            boolean legacyCodes,
            int defaultColor,
            Runnable changed) {
        super(x, y, width, height, Component.literal("Text"));
        this.lineLimit = lineLimit;
        this.defaultColor = defaultColor;
        this.legacyCodes = legacyCodes;
        this.font = font;
        this.document = document;
        this.changed = changed;
        this.characterLimit = characterLimit;
        Minecraft mc = Minecraft.getInstance();
        helper =
                new TextFieldHelper(
                        document::inputText,
                        value -> {
                            document.replaceInput(value);
                            reflow();
                            changed.run();
                        },
                        TextFieldHelper.createClipboardGetter(mc),
                        TextFieldHelper.createClipboardSetter(mc),
                        this::canReplace);
        reflow();
    }

    public StyledTextDocument document() {
        return document;
    }

    /** Native text-display layout; book fields retain their original paper layout. */
    public void displayLayout(
            int lineWidth, String alignment, int opacity, boolean shadow, int background) {
        wrapWidth = Math.max(1, lineWidth);
        this.alignment = alignment;
        defaultColor = (opacity << 24) | 0xFFFFFF;
        textShadow = shadow;
        textBackground = background;
        centeredVertically = true;
        lineHeight = 10;
        reflow();
    }

    private int contentWidth() {
        return lines.stream().mapToInt(line -> measure(line.start(), line.end())).max().orElse(0);
    }

    private int alignedOffset(int available, int used) {
        return switch (alignment) {
            case "right" -> available - used;
            case "center" -> (available - used) / 2;
            default -> 0;
        };
    }

    private int lineX(int row) {
        Line line = lines.get(row);
        int group = contentWidth();
        return getX()
                + alignedOffset(width, group)
                + alignedOffset(group, measure(line.start(), line.end()))
                - horizontalScroll;
    }

    private int verticalOffset() {
        return centeredVertically ? Math.max(0, (height - lines.size() * lineHeight) / 2) : 0;
    }

    private int lineY(int row) {
        return getY() + verticalOffset() + row * lineHeight - scroll;
    }

    public List<Line> lines() {
        reflow();
        return lines;
    }

    public TextFieldHelper helper() {
        return helper;
    }

    public void refresh() {
        reflow();
        keepCursorVisible();
        changed.run();
    }

    private List<Line> wrap(StyledTextDocument value) {
        String source = value.inputText();
        var wrapped =
                font.getSplitter()
                        .splitLines(
                                value.legacyMode()
                                        ? Component.literal(source)
                                        : value.editingComponent(),
                                wrapWidth > 0 && !value.legacyMode() ? wrapWidth : width,
                                Style.EMPTY);
        List<Line> result = new ArrayList<>();
        int offset = 0;
        for (var line : wrapped) {
            String text = line.getString();
            int first = source.indexOf(text, offset);
            if (first < 0) first = Math.min(offset, source.length());
            int end = Math.min(source.length(), first + text.length());
            result.add(new Line(first, end));
            offset = end;
            if (offset < source.length()
                    && (source.charAt(offset) == '\n' || source.charAt(offset) == ' ')) offset++;
        }
        if (result.isEmpty() || source.endsWith("\n"))
            result.add(new Line(source.length(), source.length()));
        return result;
    }

    private void reflow() {
        lines = wrap(document);
        scroll = Math.max(0, Math.min(scroll, lines.size() * lineHeight - height));
    }

    private boolean canReplace(String value) {
        if (document.legacyMode()) return value.length() <= Math.max(32767, characterLimit * 32);
        StyledTextDocument next = new StyledTextDocument(document.component());
        next.replaceText(value);
        int nextLines = wrap(next).size();
        return value.length() <= characterLimit && nextLines <= lineLimit
                || value.length() < document.text().length() && nextLines <= lines.size();
    }

    public boolean canApply(int from, int to, UnaryOperator<Style> change) {
        StyledTextDocument next = new StyledTextDocument(document.component());
        next.apply(from, to, change);
        return wrap(next).size() <= lineLimit || wrap(next).size() <= wrap(document).size();
    }

    public String blockedReason() {
        return lineLimit == 14
                ? "This formatting would make the text exceed the page. Shorten it or move text to"
                      + " another page."
                : "This formatting would exceed the text limit.";
    }

    public boolean validInput() {
        StyledTextDocument decoded = new StyledTextDocument(document.component());
        return decoded.text().length() <= characterLimit && wrap(decoded).size() <= lineLimit;
    }

    public String invalidReason() {
        return document.text().length() > characterLimit
                ? "The text exceeds " + characterLimit + " characters."
                : "The formatted text does not fit on one book page.";
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean capturesInput() {
        return isFocused();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        Minecraft.getInstance().onTextInputFocusChange(this, focused);
    }

    public String inputText() {
        return document.inputText();
    }

    public int cursor() {
        return helper.getCursorPos();
    }

    public int anchor() {
        return helper.getSelectionPos();
    }

    public int selectionX() {
        Line line = lines.get(lineAt(Math.min(cursor(), anchor())));
        int end = Math.min(Math.max(cursor(), anchor()), line.end());
        return lineX(lineAt(Math.min(cursor(), anchor())))
                + (measure(line.start(), Math.min(cursor(), anchor())) + measure(line.start(), end))
                        / 2;
    }

    public int selectionY() {
        return Math.max(getY(), lineY(lineAt(Math.min(cursor(), anchor()))));
    }

    public void switchInputMode() {
        if (!supportsLegacyCodes()) return;
        int c = cursor(), a = anchor();
        int[] map = document.switchInputMode();
        helper.setSelectionRange(
                map[Math.min(c, map.length - 1)], map[Math.min(a, map.length - 1)]);
        refresh();
    }

    @Override
    public boolean supportsLegacyCodes() {
        return legacyCodes;
    }

    public int scrollOffset() {
        return (scroll + lineHeight - 1) / lineHeight;
    }

    public int visibleLines() {
        return Math.max(1, height / lineHeight);
    }

    public void scrollLines(int value) {
        scroll = Math.clamp(value * lineHeight, 0, Math.max(0, lines.size() * lineHeight - height));
    }

    private int measure(int start, int end) {
        start = Math.max(0, Math.min(start, inputText().length()));
        end = Math.max(start, Math.min(end, inputText().length()));
        return document.legacyMode()
                ? font.width(
                        FormattedCharSequence.forward(
                                inputText().substring(start, end), Style.EMPTY))
                : font.width(document.editingComponent(start, end));
    }

    private int lineAt(int position) {
        for (int i = 0; i < lines.size() - 1; i++)
            if (position < lines.get(i + 1).start()) return i;
        return lines.size() - 1;
    }

    private int indexAt(double x, double y) {
        int row =
                Math.max(
                        0,
                        Math.min(
                                lines.size() - 1,
                                (int)
                                        Math.floor(
                                                (y - getY() - verticalOffset() + scroll)
                                                        / lineHeight)));
        Line line = lines.get(row);
        double target = x - lineX(row);
        int previous = 0;
        for (int i = line.start(); i < line.end(); ) {
            int next = i + Character.charCount(inputText().codePointAt(i));
            int right = measure(line.start(), next);
            if (target < (previous + right) / 2.0) return i;
            previous = right;
            i = next;
        }
        return line.end();
    }

    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        helper.setCursorPos(indexAt(event.x(), event.y()), event.hasShiftDown());
        if (doubleClick) {
            int c = cursor();
            int a = font.getSplitter().getWordPosition(inputText(), -1, c, true);
            int b = font.getSplitter().getWordPosition(inputText(), 1, c, true);
            helper.setSelectionRange(b, a);
        }
    }

    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        helper.setCursorPos(indexAt(event.x(), event.y()), true);
        keepCursorVisible();
    }

    private void keepCursorVisible() {
        int row = lineAt(cursor());
        int y = verticalOffset() + row * lineHeight;
        if (y < scroll) scroll = y;
        if (y + lineHeight > scroll + height) scroll = y + lineHeight - height;
        if (wrapWidth > 0) {
            int x = lineX(row) + measure(lines.get(row).start(), cursor());
            if (x < getX()) horizontalScroll -= getX() - x;
            if (x >= getRight()) horizontalScroll += x - getRight() + 1;
            if (contentWidth() < width) horizontalScroll = 0;
        }
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x, y)) return false;
        scroll =
                Math.max(
                        0,
                        Math.min(
                                Math.max(0, lines.size() * lineHeight - height),
                                scroll - (int) (vertical * lineHeight * 2)));
        return true;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!isFocused()) return false;
        boolean result = helper.charTyped(event);
        keepCursorVisible();
        return result;
    }

    public boolean keyPressed(KeyEvent event) {
        if (!isFocused()) return false;
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)
            helper.insertText("\n");
        else if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
            int row = lineAt(cursor()),
                    next =
                            Math.max(
                                    0,
                                    Math.min(
                                            lines.size() - 1,
                                            row + (key == InputConstants.KEY_UP ? -1 : 1)));
            helper.setCursorPos(
                    indexAt(
                            lineX(row) + measure(lines.get(row).start(), cursor()),
                            lineY(next) + 1),
                    event.hasShiftDown());
        } else if ((key == InputConstants.KEY_HOME || key == InputConstants.KEY_END)
                && !event.hasControlDown()) {
            Line line = lines.get(lineAt(cursor()));
            helper.setCursorPos(
                    key == InputConstants.KEY_HOME ? line.start() : line.end(),
                    event.hasShiftDown());
        } else if (!helper.keyPressed(event)) return false;
        keepCursorVisible();
        return true;
    }

    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        reflow();
        g.enableScissor(getX(), getY() - 1, getRight(), getBottom() + 1);
        int first = Math.min(cursor(), anchor()), last = Math.max(cursor(), anchor());
        if (textBackground != 0) {
            int group = contentWidth(), x = getX() + alignedOffset(width, group) - horizontalScroll;
            g.fill(
                    x - 1,
                    lineY(0) - 1,
                    x + group + 1,
                    lineY(lines.size() - 1) + lineHeight,
                    textBackground);
        }
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            int x = lineX(i), y = lineY(i);
            if (y + lineHeight < getY() || y >= getBottom()) continue;
            if (isFocused() && last > first) {
                int a = Math.max(first, line.start()), b = Math.min(last, line.end());
                if (b > a)
                    g.fill(
                            x + measure(line.start(), a),
                            y - 1,
                            x + measure(line.start(), b),
                            y + lineHeight,
                            EETheme.THUMB);
            }
            if (document.legacyMode())
                g.text(
                        font,
                        FormattedCharSequence.forward(
                                inputText().substring(line.start(), line.end()), Style.EMPTY),
                        x,
                        y,
                        defaultColor,
                        textShadow);
            else
                g.text(
                        font,
                        isFocused()
                                ? document.editingComponent(line.start(), line.end())
                                : document.component(line.start(), line.end()),
                        x,
                        y,
                        defaultColor,
                        textShadow);
            if (isFocused() && last > first) {
                int a = Math.max(first, line.start()), b = Math.min(last, line.end());
                if (b > a) {
                    var selected = Component.empty();
                    (document.legacyMode()
                                    ? Component.literal(inputText().substring(a, b))
                                    : document.editingComponent(a, b))
                            .visit(
                                    (style, segment) -> {
                                        selected.append(
                                                Component.literal(segment)
                                                        .withStyle(style.withColor(EETheme.TEXT)));
                                        return Optional.empty();
                                    },
                                    Style.EMPTY);
                    g.text(font, selected, x + measure(line.start(), a), y, EETheme.TEXT, false);
                }
            }
        }
        if (isFocused() && first == last && (net.minecraft.util.Util.getMillis() / 300) % 2 == 0) {
            int row = lineAt(cursor()),
                    x = lineX(row) + measure(lines.get(row).start(), cursor()),
                    y = lineY(row);
            g.fill(x, y, x + 1, y + lineHeight, wrapWidth > 0 ? 0xFFFFFFFF : defaultColor);
        }
        g.disableScissor();
    }

    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
