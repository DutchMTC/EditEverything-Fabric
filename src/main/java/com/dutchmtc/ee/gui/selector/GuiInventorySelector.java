package com.dutchmtc.ee.gui.selector;

import com.dutchmtc.ee.gui.ItemStackButtonWidget;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.function.Function;

public class GuiInventorySelector extends GuiListSelector<ItemStack> {

    static class InventoryListElement extends ListElement {
        private final GuiInventorySelector parent;
        private final ItemStack itemStack;

        public InventoryListElement(GuiInventorySelector parent, ItemStack itemStack) {
            super(24, 24);
            this.parent = parent;
            this.itemStack = itemStack;
            buttonList.add(new ItemStackButtonWidget(0, 0, itemStack, b -> parent.select(b.getStack())));
        }

        @Override
        public boolean match(String search) {
            String s = search.toLowerCase();
            return itemStack.getDisplayName().getString().toLowerCase().contains(s)
                    || ItemUtils.getRegistry(itemStack).toString().toLowerCase().contains(s);
        }

        @Override
        public void drawNext(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                             float partialTicks) {
            if (com.dutchmtc.ee.utils.GuiUtils.isHover(0, 0, 18, 18, mouseX, mouseY)) {
                com.dutchmtc.ee.utils.GuiUtils.setTooltipForNextFrame(graphics, parent.getMinecraft().font, itemStack,
                        mouseX + offsetX, mouseY + offsetY);
            }
            super.drawNext(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }
    }

    @SuppressWarnings("unchecked")
    public GuiInventorySelector(Screen parent, Component name, Function<ItemStack, Screen> setter) {
        super(parent, name, new ArrayList<>(), setter, false, new Tuple[0]);
        if (getMinecraft().player != null) {
            var inventory = getMinecraft().player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    addListElement(new InventoryListElement(this, stack));
                }
            }
        }
    }
}
