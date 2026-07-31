package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuiConfirmation extends GuiEE {
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private final Component message;
    private final Component confirmLabel;
    private Button cancelButton;
    private Button confirmButton;

    public GuiConfirmation(Screen parent, Component message, Runnable onConfirm, Runnable onCancel) {
        this(parent, message, Component.translatable("gui.ee.discard"), onConfirm, onCancel);
    }

    public GuiConfirmation(Screen parent, Component message, Component confirmLabel, Runnable onConfirm, Runnable onCancel) {
        super(parent, Component.translatable("gui.ee.confirmation"));
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    public void init() {
        super.init();
        int y = height / 2;
        cancelButton = addRenderableWidget(new EEButton(width / 2 - 105, y, 100, 20, Component.translatable("gui.ee.cancel"), b -> onCancel.run()));
        confirmButton = addRenderableWidget(new EEButton(width / 2 + 5, y, 100, 20, confirmLabel, b -> onConfirm.run()));
    }

    public Button getCancelButton() {
        return cancelButton;
    }

    public Button getConfirmButton() {
        return confirmButton;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawCenterString(graphics, font, message.getString(), width / 2, height / 2 - 20, 0xFFFFFFFF);
    }
}
