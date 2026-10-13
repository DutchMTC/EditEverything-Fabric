package com.dutchmtc.ee.gui.components;

import com.dutchmtc.ee.utils.GuiUtils;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared editor colors and resizable control rendering, in GUI-space pixels. */
public final class EETheme {
    public static final int TEXT = 0xFF303830, MUTED = 0xFF596359;
    public static final int PANEL = 0xFFC6C6C6, INPUT = 0xFF343B39;
    public static final int CONTROL = 0xFFADB7A7, CONTROL_HOVER = 0xFFD1D9CD;
    public static final int OPTION = 0xFFBEC6B7, OPTION_HOVER = 0xFFD7DFD0;
    public static final int THUMB = 0xFFB3C8A5, THUMB_HOVER = 0xFFE3EADC;
    public static final int TRACK = 0xFF879180, LIGHT = 0xFFF1F3EF, DARK = 0xFF777777;
    private static final int BEVEL_CORNER = 0xB4B5B3;
    public static final int REMOVE = 0xFFA35B5B, REMOVE_HOVER = 0xFFCD7777;
    public static final int ERROR = 0xFFFF9999, ERROR_BORDER = 0xFFCC6666, BLOCKED = 0xFFE7A0A0;
    public static final int ERROR_LABEL = 0xFF9C4141;
    public static final int ON = 0xFF4A9B3C, ON_HOVER = 0xFF5DB44C;

    private EETheme() {}

    public static int controlColor(boolean on, boolean hovered, boolean enabled) {
        return !enabled
                ? CONTROL
                : on ? hovered ? ON_HOVER : ON : hovered ? CONTROL_HOVER : CONTROL;
    }

    /** Shared removal/reset icon for native buttons and controls drawn inside scrolling rows. */
    public static void removeButton(
            GuiGraphicsExtractor g,
            int x,
            int y,
            int width,
            int height,
            boolean hovered,
            boolean enabled) {
        if (width <= 0 || height <= 0) return;
        bevel(g, x, y, width, height, enabled ? hovered ? REMOVE_HOVER : REMOVE : CONTROL, false);
        int size = Math.max(1, Math.min(5, Math.min(width, height) - 8));
        // Odd dimensions make the diagonals share one center pixel.
        if (size % 2 == 0) size--;
        g.pose().pushMatrix();
        try {
            // Keep equal margins for odd icons in even-sized controls.
            g.pose().translate(x + (width - size) / 2.0F, y + (height - size) / 2.0F);
            for (int i = 0; i < size; i++) {
                rect(g, i, i, 1, 1, enabled ? LIGHT : MUTED);
                if (i != size / 2) rect(g, i, size - 1 - i, 1, 1, enabled ? LIGHT : MUTED);
            }
        } finally {
            g.pose().popMatrix();
        }
    }

    public static void invalidBorder(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        rect(g, x, y, width, 1, ERROR_BORDER);
        rect(g, x, y + height - 1, width, 1, ERROR_BORDER);
        rect(g, x, y, 1, height, ERROR_BORDER);
        rect(g, x + width - 1, y, 1, height, ERROR_BORDER);
    }

    public static void rect(
            GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        if (width > 0 && height > 0)
            GuiUtils.drawGradientRect(g, x, y, x + width, y + height, color, color);
    }

    /** The caller chooses the surface color, including its alpha, and all dimensions. */
    public static void bevel(
            GuiGraphicsExtractor g, int x, int y, int width, int height, int color, boolean inset) {
        if (width <= 0 || height <= 0) return;
        rect(g, x, y, width, height, color);
        if (width == 1 || height == 1) return;
        int alpha = color & 0xFF000000;
        int light = alpha | (LIGHT & 0xFFFFFF), dark = alpha | (DARK & 0xFFFFFF);
        rect(g, x, y, width - 1, 1, inset ? dark : light);
        rect(g, x, y, 1, height - 1, inset ? dark : light);
        rect(g, x + 1, y + height - 1, width - 1, 1, inset ? light : dark);
        rect(g, x + width - 1, y + 1, 1, height - 1, inset ? light : dark);
        // Join the light and dark edges instead of leaving surface-colored corner gaps.
        rect(g, x + width - 1, y, 1, 1, alpha | BEVEL_CORNER);
        rect(g, x, y + height - 1, 1, 1, alpha | BEVEL_CORNER);
    }

    /** Rendering only: the owning screen retains its scroll position and input handling. */
    public static void scrollbar(
            GuiGraphicsExtractor g,
            int x,
            int y,
            int width,
            int height,
            int thumbY,
            int thumbHeight,
            boolean highlighted) {
        if (width <= 0 || height <= 0) return;
        int thumb = Math.max(1, Math.min(height, thumbHeight));
        int position = Math.max(y, Math.min(y + height - thumb, thumbY));
        bevel(g, x, y, width, height, TRACK, true);
        bevel(g, x, position, width, thumb, highlighted ? THUMB_HOVER : THUMB, false);
    }

    /** Eight-pixel thumb matches AbstractSliderButton's input travel. */
    public static void slider(
            GuiGraphicsExtractor g,
            int x,
            int y,
            int width,
            int height,
            double value,
            boolean highlighted,
            boolean centerMark) {
        if (width <= 0 || height <= 0) return;
        int rail = Math.min(4, height),
                knobWidth = Math.min(8, width),
                knobHeight = Math.max(1, height - 4);
        int centerY = y + height / 2;
        bevel(g, x, centerY - rail / 2, width, rail, TRACK, true);
        if (centerMark) rect(g, x + width / 2, y + 4, 1, Math.max(1, height - 8), MUTED);
        int knob = x + (int) Math.round(Math.max(0, Math.min(1, value)) * (width - knobWidth));
        bevel(
                g,
                knob,
                y + (height - knobHeight) / 2,
                knobWidth,
                knobHeight,
                highlighted ? THUMB_HOVER : THUMB,
                false);
    }
}
