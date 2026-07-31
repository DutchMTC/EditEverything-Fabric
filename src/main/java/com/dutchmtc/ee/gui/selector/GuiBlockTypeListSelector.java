package com.dutchmtc.ee.gui.selector;

import com.dutchmtc.ee.gui.ItemStackButtonWidget;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;

public class GuiBlockTypeListSelector extends GuiListSelector<Block> {
    static class BlockTypeListElement extends ListElement {
        private final GuiBlockTypeListSelector parent;
        private final Block block;
        private final ItemStack itemStack;
        private final Identifier id;

        public BlockTypeListElement(GuiBlockTypeListSelector parent, Block block) {
            super(24, 24);
            this.parent = parent;
            this.block = block;
            this.itemStack = new ItemStack(block.asItem());
            this.id = ItemUtils.getRegistry(block);
            buttonList.add(new ItemStackButtonWidget(0, 0, itemStack, b -> parent.select(block)));
        }

        @Override
        public boolean match(String search) {
            String s = search.toLowerCase();
            return itemStack.getDisplayName().getString().toLowerCase().contains(s)
                    || id.toString().toLowerCase().contains(s);
        }

        @Override
        public void drawNext(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                             float partialTicks) {
            if (GuiUtils.isHover(0, 0, 18, 18, mouseX, mouseY)) {
                GuiUtils.setTooltipForNextFrame(graphics, parent.getMinecraft().font, itemStack,
                        mouseX + offsetX, mouseY + offsetY);
            }
            super.drawNext(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }
    }

    @SuppressWarnings("unchecked")
    public GuiBlockTypeListSelector(Screen parent, Component name, Function<Block, Screen> setter) {
        this(parent, name, setter, BuiltInRegistries.BLOCK.stream());
    }

    @SuppressWarnings("unchecked")
    public GuiBlockTypeListSelector(Screen parent, Component name, Function<Block, Screen> setter, Stream<Block> blocks) {
        super(parent, name, new ArrayList<>(), setter, false, new Tuple[0]);
        blocks.filter(block -> block.asItem() != Items.AIR)
                .sorted(Comparator.comparing(block -> ItemUtils.getRegistry(block).toString()))
                .forEach(block -> addListElement(new BlockTypeListElement(this, block)));
    }
}
