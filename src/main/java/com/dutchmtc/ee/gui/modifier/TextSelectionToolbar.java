package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.gui.components.StyledTextField;
import com.dutchmtc.ee.gui.components.TextSelectionTarget;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

/** Shared floating formatting toolbar with an optional color-code input mode. */
public final class TextSelectionToolbar {
    private static final List<ChatFormatting> COLORS =
            Arrays.stream(ChatFormatting.values()).filter(ChatUtils::isColor).toList();
    private static final String[] STYLES = {"B", "I", "U", "S", "M", "×"};
    private static final String[] STYLE_NAMES = {
        "Bold", "Italic", "Underline", "Strikethrough", "Obfuscated", "Reset formatting"
    };
    private static final int PALETTE_WIDTH = 112, PALETTE_HEIGHT = 109;
    private final Screen host;
    private final EditBox input;
    private TextSelectionTarget target;
    private Runnable selectionAction;
    private final StyledTextDocument document;
    private final Font font;
    private final Runnable changed;
    private final int minX, maxX;
    private final float textScale;
    private final int textCenterX;
    private final IntSupplier defaultColor;
    private final int maxPixelWidth;
    private final boolean centeredStyledText;
    private int start = -1, end = -1;
    private boolean paletteOpen;

    public TextSelectionToolbar(
            Screen host,
            EditBox input,
            StyledTextDocument document,
            Font font,
            int minX,
            int maxX,
            Runnable changed) {
        this(host, input, document, font, minX, maxX, changed, 1f, () -> 0xFFFFFFFF);
    }

    public TextSelectionToolbar(
            Screen host,
            EditBox input,
            StyledTextDocument document,
            Font font,
            int minX,
            int maxX,
            Runnable changed,
            float textScale,
            IntSupplier defaultColor) {
        this(
                host,
                input,
                document,
                font,
                minX,
                maxX,
                changed,
                textScale,
                defaultColor,
                Integer.MAX_VALUE);
    }

    public TextSelectionToolbar(
            Screen host,
            EditBox input,
            StyledTextDocument document,
            Font font,
            int minX,
            int maxX,
            Runnable changed,
            float textScale,
            IntSupplier defaultColor,
            int maxPixelWidth) {
        this(
                host,
                input,
                document,
                font,
                minX,
                maxX,
                changed,
                textScale,
                defaultColor,
                maxPixelWidth,
                false);
    }

    public TextSelectionToolbar(
            Screen host,
            EditBox input,
            StyledTextDocument document,
            Font font,
            int minX,
            int maxX,
            Runnable changed,
            float textScale,
            IntSupplier defaultColor,
            int maxPixelWidth,
            boolean centeredStyledText) {
        this.host = host;
        this.input = input;
        this.document = document;
        this.font = font;
        this.minX = minX;
        this.maxX = maxX;
        this.changed = changed;
        this.textScale = textScale;
        this.textCenterX = input.getX() + input.getWidth() / 2;
        this.defaultColor = defaultColor;
        this.maxPixelWidth = maxPixelWidth;
        this.centeredStyledText = centeredStyledText;
    }

    public TextSelectionToolbar(
            Screen host,
            TextSelectionTarget target,
            StyledTextDocument document,
            Font font,
            int minX,
            int maxX,
            Runnable changed,
            IntSupplier defaultColor,
            Runnable selectionAction) {
        this.host = host;
        this.input = null;
        this.target = target;
        this.document = document;
        this.font = font;
        this.minX = minX;
        this.maxX = maxX;
        this.changed = changed;
        this.defaultColor = defaultColor;
        this.selectionAction = selectionAction;
        this.textScale = 1;
        this.textCenterX = 0;
        this.maxPixelWidth = Integer.MAX_VALUE;
        this.centeredStyledText = false;
    }

    private boolean focused() {
        return target == null
                ? input.isFocused() && input.isVisible()
                : target.isFocused() && target.isVisible();
    }

    private String inputText() {
        return target == null ? input.getValue() : target.inputText();
    }

    private boolean validInput() {
        return target != null
                ? target.validInput()
                : !(input instanceof StyledTextField field) || field.validInput();
    }

    private String invalidReason() {
        return target != null ? target.invalidReason() : ((StyledTextField) input).invalidReason();
    }

    private boolean canApply(UnaryOperator<Style> change) {
        return document.canApplyWithinWidth(start, end, change, font, maxPixelWidth)
                && (target == null || target.canApply(start, end, change));
    }

    public void update() {
        if (!focused()) {
            start = end = -1;
            paletteOpen = false;
            return;
        }
        int cursor = target == null ? input.getCursorPosition() : target.cursor();
        int anchor =
                target == null ? ((EditBoxAccessor) input).ee$getHighlightPos() : target.anchor();
        start = Math.min(cursor, anchor);
        end = Math.max(cursor, anchor);
        if (start < 0
                || end <= start
                || end > inputText().length()
                || !document.legacyMode() && end > document.text().length()) {
            start = end = -1;
            paletteOpen = false;
        }
    }

    public boolean hasSelection() {
        return start >= 0 && end > start;
    }

    public int selectionStart() {
        return start;
    }

    public int selectionEnd() {
        return end;
    }

    public boolean paletteOpen() {
        return paletteOpen;
    }

    private boolean visible() {
        return focused() && hasSelection();
    }

    private int colorWidth() {
        return Math.max(22, Math.min(44, maxX - minX - 4 - styleControlsWidth()));
    }

    private boolean supportsLegacyCodes() {
        return target == null || target.supportsLegacyCodes();
    }

    private int styleControlsWidth() {
        return (supportsLegacyCodes() ? 7 : 6) * 23;
    }

    private int width() {
        return document.legacyMode()
                ? 116
                : colorWidth() + styleControlsWidth() + (selectionAction == null ? 0 : 56);
    }

    public boolean isMouseOver(double mx, double my) {
        update();
        return visible()
                && (inside((int) mx, (int) my, x() - 2, y() - 2, width() + 4, 26)
                        || paletteOpen
                                && inside(
                                        (int) mx,
                                        (int) my,
                                        paletteX() - 2,
                                        paletteY() - 2,
                                        PALETTE_WIDTH + 4,
                                        PALETTE_HEIGHT + 4));
    }

    private int x() {
        if (target != null)
            return Math.max(minX, Math.min(target.selectionX() - width() / 2, maxX - width()));
        int center =
                textCenterX
                        + Math.round(
                                ((textX(hasSelection() ? start : input.getCursorPosition())
                                                                + textX(
                                                                        hasSelection()
                                                                                ? end
                                                                                : input
                                                                                        .getCursorPosition()))
                                                        / 2f
                                                - textCenterX)
                                        * textScale);
        return Math.max(minX, Math.min(center - width() / 2, maxX - width()));
    }

    private int textX(int position) {
        position = Math.max(0, Math.min(position, input.getValue().length()));
        if (document.legacyMode()) {
            EditBoxAccessor accessor = (EditBoxAccessor) input;
            int first =
                    Math.max(0, Math.min(accessor.ee$getDisplayPos(), input.getValue().length()));
            int offset =
                    font.width(
                            net.minecraft.util.FormattedCharSequence.forward(
                                    input.getValue().substring(first, Math.max(first, position)),
                                    Style.EMPTY));
            int left = centeredStyledText ? input.getX() : accessor.ee$getTextX();
            return left + Math.min(offset, input.getInnerWidth());
        }
        if (centeredStyledText) {
            return textCenterX
                    - font.width(document.editingComponent()) / 2
                    + font.width(document.editingComponent(0, position));
        }
        EditBoxAccessor accessor = (EditBoxAccessor) input;
        int from = Math.max(0, Math.min(position, accessor.ee$getDisplayPos()));
        return accessor.ee$getTextX() + font.width(document.editingComponent(from, position));
    }

    private int y() {
        return Math.max(0, (target == null ? input.getY() : target.selectionY()) - 26);
    }

    private int paletteX() {
        return Math.max(minX, Math.min(x() - 86, maxX - PALETTE_WIDTH));
    }

    private int paletteY() {
        return target == null
                ? y() + 25
                : Math.max(2, Math.min(y() + 25, host.height - PALETTE_HEIGHT - 4));
    }

    public boolean click(MouseButtonEvent event) {
        update();
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !visible()) return false;
        int mx = (int) event.x(), my = (int) event.y();
        if (inside(mx, my, x(), y(), width(), 22)) {
            int offset = mx - x();
            if (!document.legacyMode()
                    && selectionAction != null
                    && offset >= colorWidth() + styleControlsWidth()) {
                paletteOpen = false;
                selectionAction.run();
            } else if (supportsLegacyCodes()
                    && (document.legacyMode() || offset >= colorWidth() + 6 * 23)) {
                if ((!document.legacyMode() || validInput())
                        && (target != null || input instanceof StyledTextField)) {
                    paletteOpen = false;
                    if (target != null) target.switchInputMode();
                    else ((StyledTextField) input).switchInputMode();
                    update();
                }
            } else if (offset < colorWidth()) paletteOpen = !paletteOpen;
            else {
                int index = Math.min(5, (offset - colorWidth()) / 23);
                if (index == 5) confirmReset();
                else apply(styleTransform(index));
            }
            return true;
        }
        if (paletteOpen && inside(mx, my, paletteX(), paletteY(), PALETTE_WIDTH, PALETTE_HEIGHT)) {
            int dx = mx - paletteX() - 5, dy = my - paletteY() - 5;
            if (dx >= 0 && dx < 4 * 25 && dy >= 0 && dy < 4 * 20) {
                int index = dy / 20 * 4 + dx / 25;
                if (index < COLORS.size()) {
                    int rgb = ChatUtils.colorRgb(COLORS.get(index));
                    apply(style -> style.withColor(rgb));
                }
            } else if (dy >= 82 && dy < 103) openWheel();
            return true;
        }
        paletteOpen = false;
        return false;
    }

    private void apply(UnaryOperator<Style> transform) {
        if (canApply(transform)
                && document.applyWithinWidth(start, end, transform, font, maxPixelWidth))
            changed.run();
    }

    private UnaryOperator<Style> styleTransform(int index) {
        Style selected = document.styleAt(start + 1);
        return switch (index) {
            case 0 -> style -> style.withBold(!selected.isBold());
            case 1 -> style -> style.withItalic(!selected.isItalic());
            case 2 -> style -> style.withUnderlined(!selected.isUnderlined());
            case 3 -> style -> style.withStrikethrough(!selected.isStrikethrough());
            default -> style -> style.withObfuscated(!selected.isObfuscated());
        };
    }

    private void confirmReset() {
        int from = start, to = end;
        paletteOpen = false;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(
                new GuiConfirmation(
                        host,
                        Component.literal("Reset formatting on the selected text?"),
                        Component.literal("Reset styling"),
                        () -> {
                            if (document.applyWithinWidth(
                                    from,
                                    to,
                                    style ->
                                            target == null
                                                    ? Style.EMPTY
                                                    : style.withColor(
                                                                    (net.minecraft.network.chat
                                                                                    .TextColor)
                                                                            null)
                                                            .withBold(false)
                                                            .withItalic(false)
                                                            .withUnderlined(false)
                                                            .withStrikethrough(false)
                                                            .withObfuscated(false),
                                    font,
                                    maxPixelWidth)) changed.run();
                            mc.gui.setScreen(host);
                        },
                        () -> mc.gui.setScreen(host)));
    }

    private void openWheel() {
        int from = start, to = end;
        Style style = document.styleAt(from + 1);
        int rgb =
                style.getColor() == null
                        ? defaultColor.getAsInt() & 0xFFFFFF
                        : style.getColor().getValue();
        Minecraft.getInstance()
                .gui
                .setScreen(
                        GuiColorModifier.forText(
                                host,
                                chosen -> {
                                    document.applyColor(from, to, chosen);
                                    changed.run();
                                },
                                rgb));
    }

    public void render(GuiGraphicsExtractor g, int mx, int my) {
        update();
        if (!visible()) return;
        int x = x(), y = y();
        bevel(g, x - 2, y - 2, width() + 4, 26, 0xFF343B39);
        if (document.legacyMode()) {
            modeButton(g, x, y, width(), mx, my);
            return;
        }
        bevel(
                g,
                x,
                y,
                colorWidth(),
                22,
                EETheme.controlColor(paletteOpen, inside(mx, my, x, y, colorWidth(), 22), true));
        Style selected = document.styleAt(start + 1);
        int selectedColor =
                selected.getColor() == null
                        ? defaultColor.getAsInt()
                        : 0xFF000000 | selected.getColor().getValue();
        box(g, x + 5, y + 4, colorWidth() - 10, 14, 0xFF202020);
        box(g, x + 7, y + 6, colorWidth() - 14, 10, selectedColor);
        if (inside(mx, my, x, y, colorWidth(), 22))
            g.setTooltipForNextFrame(
                    font, Component.literal("Choose a color for the selected text."), mx, my);
        Style style = selected;
        boolean[] active = {
            style.isBold(),
            style.isItalic(),
            style.isUnderlined(),
            style.isStrikethrough(),
            style.isObfuscated(),
            false
        };
        for (int i = 0; i < STYLES.length; i++) {
            int bx = x + colorWidth() + i * 23;
            boolean blocked = i < 5 && !canApply(styleTransform(i));
            if (i == 5) EETheme.removeButton(g, bx, y, 22, 22, inside(mx, my, bx, y, 22, 22), true);
            else
                bevel(
                        g,
                        bx,
                        y,
                        22,
                        22,
                        blocked
                                ? EETheme.BLOCKED
                                : EETheme.controlColor(
                                        active[i], inside(mx, my, bx, y, 22, 22), true));
            if (inside(mx, my, bx, y, 22, 22)) {
                String help =
                        blocked && target != null
                                ? target.blockedReason()
                                : blocked
                                        ? STYLE_NAMES[i]
                                                + " would exceed this line's "
                                                + maxPixelWidth
                                                + " px limit. Shorten the text first."
                                        : i == 4
                                                ? "Obfuscated: scramble the selected text."
                                                : i == 5
                                                        ? "Reset the selected text's color and"
                                                                + " formatting."
                                                        : STYLE_NAMES[i];
                g.setTooltipForNextFrame(font, Component.literal(help), mx, my);
            }
            Component glyph =
                    switch (i) {
                        case 0 -> Component.literal("B").withStyle(value -> value.withBold(true));
                        case 1 -> Component.literal("I").withStyle(value -> value.withItalic(true));
                        case 2 ->
                                Component.literal("U")
                                        .withStyle(value -> value.withUnderlined(true));
                        case 3 ->
                                Component.literal("S")
                                        .withStyle(value -> value.withStrikethrough(true));
                        case 4 ->
                                Component.literal("M")
                                        .withStyle(value -> value.withObfuscated(true));
                        default -> Component.literal("×");
                    };
            if (i != 5)
                g.text(
                        font,
                        glyph,
                        bx + (22 - font.width(glyph)) / 2,
                        y + 7,
                        i == 5 || active[i] ? 0xFFFFFFFF : 0xFF202020,
                        false);
        }
        if (supportsLegacyCodes()) modeButton(g, x + colorWidth() + 6 * 23, y, 22, mx, my);
        if (selectionAction != null) {
            int ax = x + colorWidth() + styleControlsWidth();
            bevel(
                    g,
                    ax,
                    y,
                    55,
                    22,
                    EETheme.controlColor(false, inside(mx, my, ax, y, 55, 22), true));
            g.text(
                    font,
                    "Actions",
                    ax + (55 - font.width("Actions")) / 2,
                    y + 7,
                    EETheme.TEXT,
                    false);
            if (inside(mx, my, ax, y, 55, 22))
                g.setTooltipForNextFrame(
                        font,
                        Component.literal("Click actions and hover details for the selected text."),
                        mx,
                        my);
        }
        if (paletteOpen) renderPalette(g, mx, my);
    }

    private void modeButton(GuiGraphicsExtractor g, int x, int y, int width, int mx, int my) {
        boolean invalid = document.legacyMode() && !validInput();
        boolean hovered = inside(mx, my, x, y, width, 22);
        bevel(
                g,
                x,
                y,
                width,
                22,
                invalid ? EETheme.BLOCKED : EETheme.controlColor(false, hovered, true));
        net.minecraft.util.FormattedCharSequence label =
                net.minecraft.util.FormattedCharSequence.forward(
                        document.legacyMode() ? "Formatted text" : "§", Style.EMPTY);
        g.text(font, label, x + (width - font.width(label)) / 2, y + 7, 0xFF202020, false);
        if (hovered) {
            String tooltip =
                    invalid
                            ? invalidReason()
                            : document.legacyMode()
                                    ? "Show formatted text"
                                    : "Edit color codes (&a, &l, &#RRGGBB). Double && for a literal"
                                            + " &.";
            g.setTooltipForNextFrame(font, Component.literal(tooltip), mx, my);
        }
    }

    private void renderPalette(GuiGraphicsExtractor g, int mx, int my) {
        int x = paletteX(), y = paletteY();
        bevel(g, x - 2, y - 2, PALETTE_WIDTH + 4, PALETTE_HEIGHT + 4, 0xFFC6C6C6);
        box(g, x, y, PALETTE_WIDTH, PALETTE_HEIGHT, 0xFFC6C6C6);
        for (int i = 0; i < COLORS.size(); i++) {
            int cx = x + 5 + i % 4 * 25, cy = y + 5 + i / 4 * 20;
            box(g, cx, cy, 23, 19, 0xFF303030);
            box(g, cx + 2, cy + 2, 19, 15, 0xFF000000 | ChatUtils.colorRgb(COLORS.get(i)));
            if (inside(mx, my, cx, cy, 23, 19)) {
                String name = ChatUtils.formattingName(COLORS.get(i)).replace('_', ' ');
                g.setTooltipForNextFrame(
                        font,
                        Component.literal(
                                Character.toUpperCase(name.charAt(0)) + name.substring(1)),
                        mx,
                        my);
            }
        }
        bevel(
                g,
                x + 4,
                y + 87,
                PALETTE_WIDTH - 8,
                18,
                EETheme.controlColor(
                        false, inside(mx, my, x + 4, y + 87, PALETTE_WIDTH - 8, 18), true));
        String label = "Wheel + hex";
        g.text(font, label, x + (PALETTE_WIDTH - font.width(label)) / 2, y + 92, 0xFF202020, false);
        if (inside(mx, my, x + 4, y + 87, PALETTE_WIDTH - 8, 18))
            g.setTooltipForNextFrame(
                    font,
                    Component.literal(
                            "Choose custom colors or a gradient using the wheel or a hex code."),
                    mx,
                    my);
    }

    private static boolean inside(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        GuiUtils.drawRect(g, x, y, x + w, y + h, color);
    }

    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        EETheme.bevel(g, x, y, w, h, fill, false);
    }
}
