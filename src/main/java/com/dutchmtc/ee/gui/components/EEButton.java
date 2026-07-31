package com.dutchmtc.ee.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * class to avoid builder
 *
 * @author ATE47
 */
public class EEButton extends Button {
    public EEButton(int x, int y, int w, int h, Component message, OnPress pressAction) {
        super(x, y, w, h, message, pressAction, DEFAULT_NARRATION);
    }

    public EEButton(int x, int y, int w, int h, Component message, OnPress pressAction, Tooltip tooltip) {
        super(x, y, w, h, message, pressAction, DEFAULT_NARRATION);
        setTooltip(tooltip);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractDefaultSprite(graphics);
        extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }
}
