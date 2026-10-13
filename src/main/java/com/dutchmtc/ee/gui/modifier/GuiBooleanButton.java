package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.utils.GuiUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Boolean value button setter with color and text (for color blind) */
public class GuiBooleanButton extends EEButton {
    private static final Component YES = Component.translatable("gui.ee.yes");
    private static final Component NO = Component.translatable("gui.ee.no");
    private final Consumer<Boolean> setter;
    private final Supplier<Boolean> getter;
    private final Component textYes;
    private final Component textNo;

    public GuiBooleanButton(
            int x,
            int y,
            int widthIn,
            int heightIn,
            Component buttonText,
            Consumer<Boolean> setter,
            Supplier<Boolean> getter) {
        super(x, y, widthIn, heightIn, buttonText, GuiUtils.EMPTY_PRESS);
        this.setter = setter;
        this.getter = getter;
        this.textYes = buttonText.copy().append(" (").append(YES).append(")");
        this.textNo = buttonText.copy().append(" (").append(NO).append(")");
        updateDisplay();
    }

    public GuiBooleanButton(
            int x,
            int y,
            Component buttonText,
            Consumer<Boolean> setter,
            Supplier<Boolean> getter) {
        super(x, y, 200, 20, buttonText, GuiUtils.EMPTY_PRESS);
        this.setter = setter;
        this.getter = getter;
        this.textYes = buttonText.copy().append(" (").append(YES).append(")");
        this.textNo = buttonText.copy().append(" (").append(NO).append(")");
        updateDisplay();
    }

    private void updateDisplay() {
        boolean v = getter.get();
        setMessage(v ? textYes : textNo);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean on = getter.get();
        EETheme.bevel(
                g,
                getX(),
                getY(),
                getWidth(),
                getHeight(),
                EETheme.controlColor(on, isHoveredOrFocused(), active),
                false);
        var font = Minecraft.getInstance().font;
        String text = getMessage().getString();
        if (font.width(text) > getWidth() - 8)
            text =
                    font.plainSubstrByWidth(text, Math.max(0, getWidth() - 8 - font.width("...")))
                            + "...";
        g.text(
                font,
                text,
                getX() + (getWidth() - font.width(text)) / 2,
                getY() + (getHeight() - font.lineHeight) / 2 + 1,
                !active ? EETheme.MUTED : on ? EETheme.LIGHT : EETheme.TEXT,
                false);
    }

    /**
     * Re-evaluate the current value from the getter and refresh the button label/colors. Useful
     * when the underlying value changes externally (e.g. switching selection).
     */
    public void refreshDisplay() {
        updateDisplay();
    }

    @Override
    public void onPress(InputWithModifiers event) {
        boolean newValue = !getter.get();
        setter.accept(newValue);
        updateDisplay();
    }
}
