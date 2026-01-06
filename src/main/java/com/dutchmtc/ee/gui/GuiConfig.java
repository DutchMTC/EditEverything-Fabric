package com.dutchmtc.ee.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiBooleanButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuiConfig extends GuiEE {

    public GuiConfig(Screen parent) {
        super(parent, Component.translatable("gui.ee.config"));
    }

    @Override
    protected void init() {
        addRenderableWidget(new GuiBooleanButton(width / 2 - 100, height / 2 - 24, 200, 20,
                Component.translatable("gui.ee.disableToolTip"), EEMod::setDoesDisableToolTip,
                EEMod::doesDisableToolTip));
        addRenderableWidget(
                new EEButton(width / 2 - 100, height / 2, 200, 20, Component.translatable("gui.done"), b -> {
                    EEMod.saveConfigs();
                    mc.setScreen(parent);
                }));
        super.init();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

}
