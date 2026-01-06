package com.dutchmtc.ee.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

public class ItemStackButtonWidget extends AbstractButton {
    @FunctionalInterface
    public interface IItemStackPressable {
        void onPress(ItemStackButtonWidget button);
    }

    private final ItemStack stack;
    private final IItemStackPressable pressable;

    public ItemStackButtonWidget(int x, int y, ItemStack stack,
                                 IItemStackPressable pressable) {
        super(x, y, 18, 18, stack.getDisplayName());
        this.stack = stack;
        this.pressable = pressable;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public void onPress(InputWithModifiers event) {
        pressable.onPress(this);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiUtils.drawItemStack(graphics, stack, getX() + 1, getY() + 1);
        if (isHoveredOrFocused())
            GuiUtils.drawRect(graphics, getX(), getY(), getX() + 18, getY() + 18, 0x55cccccc);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        this.defaultButtonNarrationText(out);
    }
}
