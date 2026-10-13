package com.dutchmtc.ee.gui.components;

import net.minecraft.network.chat.Style;

import java.util.function.UnaryOperator;

/** Selection geometry for multiline editors sharing the inline text toolbar. */
public interface TextSelectionTarget {
    boolean isFocused();

    boolean isVisible();

    String inputText();

    int cursor();

    int anchor();

    int selectionX();

    int selectionY();

    void switchInputMode();

    default boolean supportsLegacyCodes() {
        return true;
    }

    boolean validInput();

    String invalidReason();

    boolean canApply(int from, int to, UnaryOperator<Style> change);

    String blockedReason();
}
