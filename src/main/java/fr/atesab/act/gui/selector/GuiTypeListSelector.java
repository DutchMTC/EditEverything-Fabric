package fr.atesab.act.gui.selector;

import fr.atesab.act.gui.ItemStackButtonWidget;
import fr.atesab.act.utils.ItemUtils;
import fr.atesab.act.utils.Tuple;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Stream;

public class GuiTypeListSelector extends GuiListSelector<ItemStack> {

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
        public void drawNext(net.minecraft.client.gui.GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                             float partialTicks) {
            if (fr.atesab.act.utils.GuiUtils.isHover(0, 0, 18, 18, mouseX, mouseY)) {
                fr.atesab.act.utils.GuiUtils.renderTooltip(graphics, parent.getMinecraft().font, itemStack,
                        mouseX + offsetX, mouseY + offsetY);
            }
            super.drawNext(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }
    }

    @SuppressWarnings("unchecked")
    public GuiTypeListSelector(Screen parent, Component name, Function<ItemStack, Screen> setter) {
        super(parent, name, new ArrayList<>(), setter, false, new Tuple[0]);
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
