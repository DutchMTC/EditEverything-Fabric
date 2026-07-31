package com.dutchmtc.ee.gui.selector;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.ItemStackButtonWidget;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import com.dutchmtc.ee.utils.VersionCompat;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class GuiTypeListSelector extends GuiListSelector<ItemStack> {
    private BiFunction<ItemStack, Boolean, Screen> extendedSetter;

    static class TypeListElement extends ListElement {
        private final GuiTypeListSelector parent;
        private final ItemStack itemStack;

        public TypeListElement(GuiTypeListSelector parent, ItemStack itemStack) {
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
    public GuiTypeListSelector(Screen parent, Component name, Function<ItemStack, Screen> setter) {
        this(parent, name, (is, keep) -> setter.apply(is));
    }

    public GuiTypeListSelector(Screen parent, Component name, BiFunction<ItemStack, Boolean, Screen> setter) {
        super(parent, name, new ArrayList<>(), is -> setter.apply(is, true), false, new Tuple[]{
                new Tuple<>("gui.ee.inventory", new Tuple<Runnable, Runnable>(() -> {
                }, () -> {
                }))
        });
        // Fix button action because we need 'this' reference which is not available in super call
        this.buttons[0].b.a = () -> {
            getMinecraft().gui.setScreen(new GuiInventorySelector(this, Component.translatable("gui.ee.inventory"), invStack -> {
                if (setter == null) {
                    return null;
                }

                // Return the confirmation screen instead of navigating here.
                // GuiListSelector.select() will handle switching to the returned screen.
                return new GuiConfirmation(this, Component.translatable("gui.ee.copy_components_question"),
                        () -> { // Confirm -> Copy components (keep=false)
                            Screen s = setter.apply(invStack, false);
                            getMinecraft().gui.setScreen(s == null ? GuiTypeListSelector.this.parent : s);
                        },
                        () -> { // Cancel -> Keep existing components (keep=true)
                            Screen s = setter.apply(invStack, true);
                            getMinecraft().gui.setScreen(s == null ? GuiTypeListSelector.this.parent : s);
                        }
                ) {
                    @Override
                    public void init() {
                        super.init();
                        // Custom button text without relying on Screen internals (renderables list is private in newer MC)
                        if (getCancelButton() != null) {
                            getCancelButton().setMessage(Component.translatable("gui.ee.keep_components"));
                        }
                        if (getConfirmButton() != null) {
                            getConfirmButton().setMessage(Component.translatable("gui.ee.copy_components"));
                        }
                    }
                };
            }));
        };

        this.extendedSetter = setter;
        NonNullList<ItemStack> stacks = NonNullList.create();
        BuiltInRegistries.ITEM.forEach(i -> // Item.REGISTRY
                stacks.add(new ItemStack(i)));
        stacks.forEach(stack -> addListElement(new TypeListElement(this, stack)));
    }

    public GuiTypeListSelector(Screen parent, Component name, Function<ItemStack, Screen> setter,
                               NonNullList<ItemStack> stacks) {
        this(parent, name, setter, stacks.stream());
    }

    @SuppressWarnings("unchecked")
    public GuiTypeListSelector(Screen parent, Component name, Function<ItemStack, Screen> setter,
                               Stream<ItemStack> stacks) {
        super(parent, name, new ArrayList<>(), setter, false, new Tuple[0]);
        stacks.forEach(stack -> addListElement(new TypeListElement(this, stack)));
    }
}
