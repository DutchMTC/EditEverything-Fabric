package fr.atesab.act.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atesab.act.ACTMod;
import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.gui.modifier.GuiBooleanButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuiConfig extends GuiACT {

    public GuiConfig(Screen parent) {
        super(parent, Component.translatable("gui.act.config"));
    }

    @Override
    protected void init() {
        addRenderableWidget(new GuiBooleanButton(width / 2 - 100, height / 2 - 24, 200, 20,
                Component.translatable("gui.act.disableToolTip"), ACTMod::setDoesDisableToolTip,
                ACTMod::doesDisableToolTip));
        addRenderableWidget(
                new ACTButton(width / 2 - 100, height / 2, 200, 20, Component.translatable("gui.done"), b -> {
                    ACTMod.saveConfigs();
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
