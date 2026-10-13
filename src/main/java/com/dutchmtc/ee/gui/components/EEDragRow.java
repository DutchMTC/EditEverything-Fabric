package com.dutchmtc.ee.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared grip and floating-row treatment for reorderable editor lists. */
public final class EEDragRow {
    private EEDragRow() {}

    public static void grip(GuiGraphicsExtractor g, int x, int top, int height, int color) {
        int y = top + (height - 9) / 2;
        for (int dot = 0; dot < 3; dot++) {
            EETheme.rect(g, x, y + dot * 4, 1, 1, color);
            EETheme.rect(g, x + 3, y + dot * 4, 1, 1, color);
        }
    }

    public static int floatingTop(
            double pointerY, double grabOffset, int top, int bottom, int height) {
        return Math.clamp(
                (int) Math.round(pointerY - grabOffset),
                top + 3,
                Math.max(top + 3, bottom - height - 5));
    }

    public static void floatingFrame(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        EETheme.bevel(g, x, y, width, height, EETheme.CONTROL, false);
    }

    public static void dropMarker(
            GuiGraphicsExtractor g, int x, int boundary, int width, int top, int bottom) {
        EETheme.rect(
                g,
                x,
                Math.clamp(boundary, top + 1, Math.max(top + 1, bottom - 3)),
                width,
                2,
                EETheme.THUMB_HOVER);
    }
}
