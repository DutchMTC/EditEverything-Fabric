package com.dutchmtc.ee.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiBooleanButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuiConfig extends GuiEE {

    public GuiConfig(Screen parent) {
        super(parent, Component.translatable("gui.ee.config"));
    }

    @Override
    protected void init() {
        int baseY = height / 2 - 36;
        addRenderableWidget(new GuiBooleanButton(width / 2 - 100, baseY, 200, 20,
                Component.translatable("gui.ee.showEETooltips"), value -> EEMod.setDoesDisableEETooltips(!value),
                () -> !EEMod.doesDisableEETooltips()));
        addRenderableWidget(new GuiBooleanButton(width / 2 - 100, baseY + 24, 200, 20,
                Component.translatable("gui.ee.disableToolTip"), EEMod::setDoesDisableToolTip,
                EEMod::doesDisableToolTip));
        addRenderableWidget(
                new EEButton(width / 2 - 100, baseY + 48, 200, 20, Component.translatable("gui.done"), b -> {
                    EEMod.saveConfigs();
                    mc.gui.setScreen(parent);
                }));
        super.init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

}
