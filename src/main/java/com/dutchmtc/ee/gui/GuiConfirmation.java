package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuiConfirmation extends GuiEE {
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private final Component message;

    public GuiConfirmation(Screen parent, Component message, Runnable onConfirm, Runnable onCancel) {
        super(parent, Component.translatable("gui.ee.confirmation"));
        this.message = message;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    public void init() {
        super.init();
        int y = height / 2;
        addRenderableWidget(new EEButton(width / 2 - 105, y, 100, 20, Component.translatable("gui.ee.discard"), b -> onConfirm.run()));
        addRenderableWidget(new EEButton(width / 2 + 5, y, 100, 20, Component.translatable("gui.ee.cancel"), b -> onCancel.run()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawCenterString(graphics, font, message.getString(), width / 2, height / 2 - 20, 0xFFFFFFFF);
    }
}
