package fr.atesab.act.gui;

import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.utils.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuiConfirmation extends GuiACT {
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private final Component message;

    public GuiConfirmation(Screen parent, Component message, Runnable onConfirm, Runnable onCancel) {
        super(parent, Component.translatable("gui.act.confirmation"));
        this.message = message;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    public void init() {
        super.init();
        int y = height / 2;
        addRenderableWidget(new ACTButton(width / 2 - 105, y, 100, 20, Component.translatable("gui.act.discard"), b -> onConfirm.run()));
        addRenderableWidget(new ACTButton(width / 2 + 5, y, 100, 20, Component.translatable("gui.act.cancel"), b -> onCancel.run()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawCenterString(graphics, font, message.getString(), width / 2, height / 2 - 20, 0xFFFFFF);
    }
}
