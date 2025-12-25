package fr.atesab.act.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.utils.ChatUtils;
import fr.atesab.act.utils.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
        return !field.getValue().replaceAll("&", String.valueOf(ChatUtils.MODIFIER)).equals(originalValue);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawRightString(graphics, font, I18n.get("gui.act.text") + " : ", field.getX(), field.getY(), Color.ORANGE.getRGB(),
                field.getHeight());
    }

    @Override
    public void init() {
        field = new EditBox(font, width / 2 - 99, height / 2 - 20, 198, 18, Component.literal(""));
        field.setMaxLength(Integer.MAX_VALUE);
        field.setValue(value.replaceAll(String.valueOf(ChatUtils.MODIFIER), "&"));
        field.setFocused(true);
        field.setCanLoseFocus(false);
        addRenderableWidget(field);
        addRenderableWidget(
                new ACTButton(width / 2 - 100, height / 2, 200, 20, Component.translatable("gui.done"), b -> {
                    set(value = field.getValue().replaceAll("&", String.valueOf(ChatUtils.MODIFIER)));
                    getMinecraft().setScreen(parent);
                }));
        addRenderableWidget(new ACTButton(width / 2 - 100, height / 2 + 21, 200, 20,
                Component.translatable("gui.act.cancel"), b -> onCancel()));
        super.init();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (GuiUtils.isHover(field, (int) mouseX, (int) mouseY) && mouseButton == 1)
            field.setValue("");
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void tick() {
        value = field.getValue();
        // field.tick();
        super.tick();
    }
}
