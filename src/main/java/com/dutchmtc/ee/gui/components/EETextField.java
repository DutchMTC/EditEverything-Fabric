package com.dutchmtc.ee.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Common validation feedback; the screen still owns its input surface and dimensions. */
public class EETextField extends EditBox {
    private String error;
    private Tooltip help;
    private int frameOffsetX, frameOffsetY, frameExtraWidth, frameExtraHeight;

    public EETextField(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
    }

    public static void validate(EditBox field, String error) {
        if (field instanceof EETextField input) input.setError(error);
    }

    public static String error(EditBox field) {
        return field instanceof EETextField input ? input.error : null;
    }

    /** Bind feedback to the visible frame without changing text layout or pointer hit areas. */
    public static void validationFrame(EditBox field, int x, int y, int width, int height) {
        if (field instanceof EETextField input) {
            input.frameOffsetX = x - field.getX();
            input.frameOffsetY = y - field.getY();
            input.frameExtraWidth = width - field.getWidth();
            input.frameExtraHeight = height - field.getHeight();
        }
    }

    public void setError(String reason) {
        String next = reason == null || reason.isBlank() ? null : reason;
        if (java.util.Objects.equals(error, next)) return;
        error = next;
        if (!preserveTextColors()) setTextColor(error == null ? EETheme.LIGHT : EETheme.ERROR);
        super.setTooltip(error == null ? help : Tooltip.create(Component.literal(error)));
    }

    protected boolean preserveTextColors() {
        return false;
    }

    @Override
    public void setTooltip(Tooltip tooltip) {
        help = tooltip;
        super.setTooltip(error == null ? help : Tooltip.create(Component.literal(error)));
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractWidgetRenderState(g, mx, my, delta);
        renderValidation(g);
    }

    protected final void renderValidation(GuiGraphicsExtractor g) {
        if (error != null)
            EETheme.invalidBorder(
                    g,
                    getX() + frameOffsetX,
                    getY() + frameOffsetY,
                    getWidth() + frameExtraWidth,
                    getHeight() + frameExtraHeight);
    }
}
