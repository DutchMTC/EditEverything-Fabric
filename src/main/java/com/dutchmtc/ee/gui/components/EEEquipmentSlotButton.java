package com.dutchmtc.ee.gui.components;

import com.dutchmtc.ee.utils.GuiUtils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Shared native item slot for the armor stand and mob equipment editors. */
public final class EEEquipmentSlotButton extends EEButton {
    private final Supplier<ItemStack> item;

    public EEEquipmentSlotButton(
            int x,
            int y,
            int width,
            int height,
            Component label,
            OnPress press,
            Tooltip tooltip,
            Supplier<ItemStack> item) {
        super(x, y, width, height, label, press, tooltip);
        this.item = item;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        EETheme.bevel(g, getX(), getY(), getWidth(), getHeight(), EETheme.INPUT, true);
        ItemStack stack = item.get();
        if (stack != null && !stack.isEmpty()) {
            GuiUtils.drawItemStack(
                    g, stack, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            if (isHoveredOrFocused())
                GuiUtils.setTooltipForNextFrame(
                        g, net.minecraft.client.Minecraft.getInstance().font, stack, mx, my);
        } else {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            g.text(
                    font,
                    "+",
                    getX() + (getWidth() - font.width("+")) / 2,
                    getY() + (getHeight() - font.lineHeight) / 2 + 1,
                    EETheme.LIGHT,
                    false);
        }
    }
}
