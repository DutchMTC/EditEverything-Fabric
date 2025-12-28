package fr.atesab.act.gui.modifier;

import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.utils.GuiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Boolean value button setter with color and text (for color blind)
 */
public class GuiBooleanButton extends ACTButton {
    private static final Component YES = Component.translatable("gui.act.yes");
    private static final Component NO = Component.translatable("gui.act.no");
    private final Consumer<Boolean> setter;
    private final Supplier<Boolean> getter;
    private final Component textYes;
    private final Component textNo;

    public GuiBooleanButton(int x, int y, int widthIn, int heightIn, Component buttonText, Consumer<Boolean> setter,
                            Supplier<Boolean> getter) {
        super(x, y, widthIn, heightIn, buttonText, GuiUtils.EMPTY_PRESS);
        this.setter = setter;
        this.getter = getter;
        this.textYes = buttonText.copy().append(" (").append(YES).append(")");
        this.textNo = buttonText.copy().append(" (").append(NO).append(")");
        updateDisplay();
    }

    public GuiBooleanButton(int x, int y, Component buttonText, Consumer<Boolean> setter, Supplier<Boolean> getter) {
        super(x, y, 200, 20, buttonText, GuiUtils.EMPTY_PRESS);
        this.setter = setter;
        this.getter = getter;
        this.textYes = buttonText.copy().append(" (").append(YES).append(")");
        this.textNo = buttonText.copy().append(" (").append(NO).append(")");
        updateDisplay();
    }

    private void updateDisplay() {
        boolean v = getter.get();
        // packedFGColor = GuiUtils.getRedGreen(v); // Removed
        if (v)
            setMessage(textYes.copy().withStyle(ChatFormatting.GREEN));
        else
            setMessage(textNo.copy().withStyle(ChatFormatting.RED));
    }

    @Override
    public void onPress(InputWithModifiers event) {
        boolean newValue = !getter.get();
        setter.accept(newValue);
        updateDisplay();
    }
}
