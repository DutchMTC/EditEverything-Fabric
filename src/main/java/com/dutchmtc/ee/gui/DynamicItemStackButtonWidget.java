package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class DynamicItemStackButtonWidget extends AbstractWidget {
    @FunctionalInterface
    public interface IItemStackPressable {
        void onPress(DynamicItemStackButtonWidget button);
    }

    private ItemStack stack;
    private final IItemStackPressable pressable;

    public DynamicItemStackButtonWidget(int x, int y, ItemStack stack, IItemStackPressable pressable) {
        super(x, y, 18, 18, Component.empty());
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        this.pressable = pressable;
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        pressable.onPress(this);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (stack != null && !stack.isEmpty()) {
            GuiUtils.drawItemStack(graphics, stack, getX() + 1, getY() + 1);
        }
        if (isHoveredOrFocused()) {
            GuiUtils.drawRect(graphics, getX(), getY(), getX() + 18, getY() + 18, 0x55FFFFFF);
            if (stack != null && !stack.isEmpty()) {
                GuiUtils.renderTooltip(graphics, Minecraft.getInstance().font, stack, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        if (stack != null) {
            out.add(NarratedElementType.TITLE, stack.getDisplayName());
        }
    }
}

