package com.dutchmtc.ee.gui.selector;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;
import java.util.List;
import java.util.stream.Stream;

public class GuiBlockTypeListSelector extends GuiItemBrowser<Block> {
    public GuiBlockTypeListSelector(Screen parent, Component name, Function<Block, Screen> setter) {
        super(parent, name, stack -> stack.getItem() instanceof BlockItem item ? item.getBlock() : null,
                setter, null);
    }
    public GuiBlockTypeListSelector(Screen parent, Component name, Function<Block, Screen> setter, Stream<Block> blocks) {
        this(parent, name, setter, blocks.toList());
    }
    private GuiBlockTypeListSelector(Screen parent, Component name, Function<Block, Screen> setter, List<Block> blocks) {
        super(parent, name, stack -> blocks.stream().filter(block -> block.asItem() == stack.getItem()).findFirst().orElse(null),
                setter,
                blocks.stream().map(block -> new ItemStack(block.asItem())).toList());
    }
}
