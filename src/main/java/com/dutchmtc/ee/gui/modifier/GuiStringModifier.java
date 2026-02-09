package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.function.Consumer;

public class GuiStringModifier extends GuiModifier<String> {
    private EditBox field;
    private String value;
    private String originalValue;

    public GuiStringModifier(Screen parent, Component name, String value, Consumer<String> setter) {
        super(parent, name, setter);
        this.value = value;
        this.originalValue = value;
    }

    @Override
    public boolean isModified() {
        if (field == null) return false;
        return !ChatUtils.translateColorCodes(field.getValue()).equals(originalValue);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.text") + " : ", field.getX(), field.getY(), Color.ORANGE.getRGB(),
                field.getHeight());
    }

    @Override
    public void init() {
        field = new EditBox(font, width / 2 - 99, height / 2 - 20, 198, 18, Component.literal(""));
        field.setMaxLength(Integer.MAX_VALUE);
        field.setValue(ChatUtils.untranslateColorCodes(value));
        field.setFocused(true);
        field.setCanLoseFocus(false);
        addRenderableWidget(field);
        int btnY = height / 2 + 2;
        addRenderableWidget(new EEButton(width / 2 - 100, btnY, 100, 20,
                Component.translatable("gui.ee.cancel"), b -> onCancel()));
        addRenderableWidget(new EEButton(width / 2 + 1, btnY, 99, 20, Component.translatable("gui.done"), b -> {
            set(value = ChatUtils.translateColorCodes(field.getValue()));
            getMinecraft().setScreen(parent);
        }));
        super.init();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int mouseButton = event.button();
        if (GuiUtils.isHover(field, (int) mouseX, (int) mouseY) && mouseButton == 1)
            field.setValue("");
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void tick() {
        value = field.getValue();
        // field.tick();
        super.tick();
    }
}
