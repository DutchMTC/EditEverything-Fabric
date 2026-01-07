package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;

public class ItemStackButtonWidget extends AbstractWidget {
    @FunctionalInterface
    public interface IItemStackPressable {
        void onPress(ItemStackButtonWidget button);
    }

    private final ItemStack stack;
    private final IItemStackPressable pressable;

    public ItemStackButtonWidget(int x, int y, ItemStack stack,
                                 IItemStackPressable pressable) {
        // Fully transparent widget: only the item is rendered (no button background).
        super(x, y, 18, 18, net.minecraft.network.chat.Component.empty());
        this.stack = stack;
        this.pressable = pressable;
    }

    public ItemStack getStack() {
        return stack;
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
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, stack.getDisplayName());
    }
}
