package com.dutchmtc.ee.gui.selector;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.dutchmtc.ee.utils.ItemUtils;

import java.util.function.Function;
import java.util.stream.Stream;

public class GuiTypeListSelector extends GuiItemBrowser<ItemStack> {
    private ItemStack currentItem;
    private boolean amountEnabled = true;
    public GuiTypeListSelector(Screen parent, Component name, Function<ItemStack, Screen> setter) {
        super(parent, browserTitle(name), ItemStack::copy, stack -> setter.apply(stack.copy()), null);
    }
    /** Change an existing item's type while retaining its edited components. */
    public GuiTypeListSelector(Screen parent, Component name, Function<ItemStack, Screen> setter, ItemStack currentItem) {
        this(parent, name, setter);
        this.currentItem = currentItem.copy();
    }
    public GuiTypeListSelector setAmountEnabled(boolean enabled) { amountEnabled = enabled; return this; }
    @Override protected boolean supportsAir() { return true; }
    @Override protected ItemStack initialSelection() { return currentItem; }
    @Override protected boolean supportsAmount() { return amountEnabled && unrestricted(); }
    @Override protected int defaultAmount(ItemStack stack) {
        return currentItem != null ? currentItem.getCount() : super.defaultAmount(stack);
    }
    @Override protected int amountLimit(ItemStack stack) {
        ItemStack result = currentItem != null ? ItemUtils.setItem(stack.getItem(), currentItem) : stack;
        return result.has(DataComponents.MAX_DAMAGE) ? 1 : Item.ABSOLUTE_MAX_STACK_SIZE;
    }
    @Override public void select(ItemStack selected) {
        super.select(selectionPreview(selected));
    }
    @Override protected ItemStack selectionPreview(ItemStack selected) {
        if (selected.isEmpty()) return selected.copy();
        ItemStack result = currentItem != null ? ItemUtils.setItem(selected.getItem(), currentItem) : selected.copy();
        if (supportsAmount()) {
            result.setCount(selected.getCount());
            if (result.has(DataComponents.MAX_DAMAGE)) {
                // A carried-over stack-size override must not make the new type invalid.
                if (result.getMaxStackSize() > 1) result.set(DataComponents.MAX_STACK_SIZE, 1);
            } else if (result.getCount() > result.getMaxStackSize()) {
                result.set(DataComponents.MAX_STACK_SIZE, result.getCount());
            }
        }
        return result;
    }
    @Override protected Component selectionLabel(ItemStack selected) {
        return currentItem != null && !selected.isEmpty() ? selected.getItem().getName(selected) : super.selectionLabel(selected);
    }
    public GuiTypeListSelector(Screen parent, Component name, Function<ItemStack, Screen> setter, NonNullList<ItemStack> stacks) {
        this(parent, name, setter, stacks.stream());
    }
    public GuiTypeListSelector(Screen parent, Component name, Function<ItemStack, Screen> setter, Stream<ItemStack> stacks) {
        super(parent, browserTitle(name), ItemStack::copy, stack -> setter.apply(stack.copy()),
                stacks.map(ItemStack::copy).toList());
    }
    private static Component browserTitle(Component name) {
        return name.getString().equals(Component.translatable("gui.ee.modifier.type").getString())
                ? Component.literal("Choose item type") : name;
    }
}
