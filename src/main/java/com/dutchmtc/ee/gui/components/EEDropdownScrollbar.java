package com.dutchmtc.ee.gui.components;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.IntConsumer;

/** Shared overflow affordance; menus retain their option lists, offsets and selection. */
public final class EEDropdownScrollbar {
    public static final int WIDTH = 12;
    private int x, y, height, visible, total, offset, step;
    private boolean dragging;
    private double grab;

    public void layout(int x, int y, int height, int visible, int total, int offset, int step) {
        this.x = x;
        this.y = y;
        this.height = height;
        this.visible = visible;
        this.total = total;
        this.offset = offset;
        this.step = step;
        if (total <= visible) dragging = false;
    }

    public boolean needed() {
        return total > visible;
    }

    private int max() {
        return Math.max(0, total - visible);
    }

    private int trackY() {
        return y + 8;
    }

    private int trackHeight() {
        return Math.max(1, height - 16);
    }

    private int thumbHeight() {
        return Math.min(trackHeight(), Math.max(8, trackHeight() * visible / Math.max(1, total)));
    }

    private int thumbY() {
        return trackY() + (trackHeight() - thumbHeight()) * offset / Math.max(1, max());
    }

    private void change(int value, IntConsumer changed) {
        offset = Math.clamp(value, 0, max());
        changed.accept(offset);
    }

    public boolean mouseClicked(MouseButtonEvent event, IntConsumer changed) {
        if (!needed()
                || event.button() != InputConstants.MOUSE_BUTTON_LEFT
                || event.x() < x
                || event.x() >= x + WIDTH
                || event.y() < y
                || event.y() >= y + height) return false;
        if (event.y() < trackY()) change(offset - step, changed);
        else if (event.y() >= y + height - 8) change(offset + step, changed);
        else {
            dragging = true;
            grab =
                    event.y() >= thumbY() && event.y() < thumbY() + thumbHeight()
                            ? event.y()
                                    - trackY()
                                    - (trackHeight() - thumbHeight())
                                            * offset
                                            / (double) Math.max(1, max())
                            : thumbHeight() / 2.0;
            mouseDragged(event, changed);
        }
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent event, IntConsumer changed) {
        if (!dragging || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        int travel = trackHeight() - thumbHeight();
        if (travel > 0)
            change(
                    (int)
                            Math.round(
                                    Math.clamp((event.y() - grab - trackY()) / travel, 0, 1)
                                            * max()),
                    changed);
        return true;
    }

    public boolean mouseReleased() {
        boolean handled = dragging;
        dragging = false;
        return handled;
    }

    public void reset() {
        dragging = false;
    }

    public void render(GuiGraphicsExtractor g, int mx, int my) {
        if (!needed()) return;
        EETheme.rect(g, x, y, WIDTH, height, EETheme.OPTION);
        boolean hover = mx >= x && mx < x + WIDTH && my >= y && my < y + height;
        arrow(g, y, false, offset > 0, hover && my < trackY());
        arrow(g, y + height - 8, true, offset < max(), hover && my >= y + height - 8);
        EETheme.scrollbar(
                g,
                x + 3,
                trackY(),
                6,
                trackHeight(),
                thumbY(),
                thumbHeight(),
                dragging || hover && my >= trackY() && my < y + height - 8);
    }

    private void arrow(
            GuiGraphicsExtractor g, int top, boolean down, boolean enabled, boolean hover) {
        if (enabled && hover) EETheme.rect(g, x + 1, top, WIDTH - 2, 8, EETheme.OPTION_HOVER);
        int color = enabled ? EETheme.TEXT : EETheme.TRACK;
        for (int row = 0; row < 3; row++)
            EETheme.rect(g, x + 3 + row, top + 2 + (down ? row : 2 - row), 5 - row * 2, 1, color);
    }
}
