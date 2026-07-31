package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTModifier;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class GuiMetaModifier extends GuiModifier<ItemStack> {
    private final ItemStack stack;
    private final ItemStack originalStack;

    public GuiMetaModifier(Screen parent, Consumer<ItemStack> setter, ItemStack stack) {
        super(parent, Component.translatable("gui.ee.modifier.meta"), setter);
        this.stack = stack;
        this.originalStack = stack.copy();
    }

    @Override
    public boolean isModified() {
        return !ItemStack.matches(stack, originalStack);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawItemStack(graphics, stack, width / 2 - 10, height / 2 - 75);
        if (GuiUtils.isHover(width / 2 - 10, height / 2 - 75, 20, 20, mouseX, mouseY))
            GuiUtils.setTooltipForNextFrame(graphics, font, stack, mouseX, mouseY);
    }

    @Override
    public void init() {
        int i = 0;
        addRenderableWidget(new GuiBooleanButton(width / 2 - 100, height / 2 - 75 + 21 * ++i,
                Component.translatable("item.unbreakable"), b -> ItemUtils.setUnbreakable(stack, b),
                () -> ItemUtils.isUnbreakable(stack)));

        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 - 75 + 21 * ++i, 200, 20,
                Component.translatable("gui.ee.modifier.meta.canBreak"),
                b -> getMinecraft().gui.setScreen(new GuiAdventureBlockModifier(GuiMetaModifier.this, stack,
                        GuiAdventureBlockModifier.Kind.CAN_BREAK))));

        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 - 75 + 21 * ++i, 200, 20,
                Component.translatable("gui.ee.modifier.meta.canPlace"),
                b -> getMinecraft().gui.setScreen(new GuiAdventureBlockModifier(GuiMetaModifier.this, stack,
                        GuiAdventureBlockModifier.Kind.CAN_PLACE))));

        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 - 75 + 21 * ++i, 200, 20,
                Component.translatable("gui.ee.modifier.meta.dataComponents"),
                b -> getMinecraft().gui.setScreen(new GuiDataComponentModifier(GuiMetaModifier.this, stack))));

        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 - 75 + 21 * ++i, 200, 20,
                Component.translatable("gui.ee.modifier.tag.editor"), b -> getMinecraft().gui.setScreen(new GuiNBTModifier(GuiMetaModifier.this, tag -> ItemUtils.setTag(stack, tag),
                ItemUtils.getTag(stack) != null ? ItemUtils.getTag(stack) : new CompoundTag()))));
        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 - 71 + 21 * ++i, 100, 20,
                Component.translatable("gui.ee.cancel"), b -> onCancel()));
        addRenderableWidget(new EEButton(width / 2 + 1, height / 2 - 71 + 21 * i, 99, 20,
                Component.translatable("gui.done"), b -> {
            set(stack);
            getMinecraft().gui.setScreen(parent);
        }));
        super.init();
    }

}
