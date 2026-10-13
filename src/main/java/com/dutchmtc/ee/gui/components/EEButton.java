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
        if (isRemovalControl()) setTooltip(Tooltip.create(Component.literal("Remove")));
    }

    public EEButton(
            int x, int y, int w, int h, Component message, OnPress pressAction, Tooltip tooltip) {
        super(x, y, w, h, message, pressAction, DEFAULT_NARRATION);
        setTooltip(
                tooltip != null || !isRemovalControl()
                        ? tooltip
                        : Tooltip.create(Component.literal("Remove")));
    }

    @Override
    protected void extractContents(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (isRemovalControl()) {
            EETheme.removeButton(
                    graphics,
                    getX(),
                    getY(),
                    getWidth(),
                    getHeight(),
                    isHoveredOrFocused(),
                    active);
            return;
        }
        extractDefaultSprite(graphics);
        extractDefaultLabel(
                graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }

    /** Icon semantics also cover subclasses and older list editors using this shared button. */
    protected final boolean isRemovalControl() {
        String label = getMessage().getString();
        return label.equals("×") || label.equals("✕") || label.equals("X");
    }
}
