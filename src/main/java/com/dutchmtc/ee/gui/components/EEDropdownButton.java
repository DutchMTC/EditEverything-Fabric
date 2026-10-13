package com.dutchmtc.ee.gui.components;

import com.dutchmtc.ee.utils.GuiUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The compact dropdown style shared by the editors and item browser. */
public class EEDropdownButton extends EEButton {
    private static final int TEXT = EETheme.TEXT;
    private boolean onState;

    public EEDropdownButton(int x, int y, int width, int height, Component label, OnPress action) {
        super(x, y, width, height, label, action);
    }

    public EEDropdownButton setOnState(boolean on) {
        onState = on;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        EETheme.bevel(
                g,
                getX(),
                getY(),
                getWidth(),
                getHeight(),
                EETheme.controlColor(onState, isHoveredOrFocused(), active),
                false);
        int color = active ? onState ? EETheme.LIGHT : TEXT : EETheme.MUTED;
        g.text(
                font,
                fit(font, getMessage().getString(), getWidth() - 24),
                getX() + 7,
                getY() + (getHeight() - font.lineHeight) / 2 + 1,
                color,
                false);
        int arrowX = getRight() - 12, arrowY = getY() + (getHeight() - 3) / 2;
        for (int row = 0; row < 3; row++)
            rect(g, arrowX + row, arrowY + row, 5 - row * 2, 1, color);
    }

    public static void renderOption(
            GuiGraphicsExtractor g,
            Font font,
            Component label,
            ItemStack icon,
            int x,
            int y,
            int width,
            int height,
            boolean selected,
            boolean hovered) {
        rect(g, x, y, width, height, hovered ? EETheme.OPTION_HOVER : EETheme.OPTION);
        if (selected) rect(g, x + 2, y + 4, 2, Math.max(1, height - 8), EETheme.ON);
        int indent = icon.isEmpty() ? 8 : 26;
        if (!icon.isEmpty()) GuiUtils.drawItemStack(g, icon, x + 7, y + (height - 16) / 2);
        g.text(
                font,
                fit(font, label.getString(), width - indent - 5),
                x + indent,
                y + (height - font.lineHeight) / 2 + 1,
                TEXT,
                false);
    }

    private static String fit(Font font, String value, int width) {
        return font.width(value) <= width
                ? value
                : font.plainSubstrByWidth(value, Math.max(0, width - font.width("..."))) + "...";
    }

    private static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        GuiUtils.drawGradientRect(g, x, y, x + w, y + h, color, color);
    }
}
