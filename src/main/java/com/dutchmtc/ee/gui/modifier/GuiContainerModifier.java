package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.gui.GuiGiver;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ItemUtils.ContainerData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

public class GuiContainerModifier extends GuiModifier<ContainerData> {
    private final ContainerData data;
    private final ContainerData originalData;
    private final Component title;
    private final List<Component> slotNames;

    public GuiContainerModifier(Screen parent, Component title, Consumer<ContainerData> setter, ContainerData data) {
        this(parent, title, setter, data, null);
    }

    public GuiContainerModifier(Screen parent, Component title, Consumer<ContainerData> setter, ContainerData data, List<Component> slotNames) {
        super(parent, Component.translatable("gui.ee.modifier.inventory"), setter);
        this.originalData = data;
        this.data = data.copy();
        this.title = title;
        this.slotNames = slotNames;
    }

    @Override
    public boolean isModified() {
        return !data.equals(originalData);
    }

    @Override
    protected void init() {
        addRenderableWidget(
                new EEButton(width / 2 - 96, height / 2 + 60, 94, 20, Component.translatable("gui.ee.cancel"), b -> onCancel()));
        addRenderableWidget(
                new EEButton(width / 2 + 2, height / 2 + 60, 94, 20, Component.translatable("gui.done"), b -> {
                    set(data);
                    mc.gui.setScreen(parent);
                }));
        super.init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // do nothing
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        var size = data.size();
        var stacks = data.stacks();
        var cy = height / 2 - size.sizeY() * 18 / 2;
        var cx = width / 2 - size.sizeX() * 18 / 2;

        GuiUtils.drawRect(graphics, width / 2 - 104, height / 2 - 80, width / 2 + 104, height / 2 + 84,
                GuiUtils.COLOR_CONTAINER_BORDER | 0xFF000000);

        GuiUtils.drawCenterString(graphics, font, title.getString(), width / 2, height / 2 - 76, 0xFF7F7F7F);

        ItemStack hoverStack = null;
        int hoverSlot = -1;

        assert minecraft != null;
        // var ir = minecraft.getItemRenderer(); // Use GuiGraphicsExtractor
        for (var j = 0; j < size.sizeY(); j++) {
            for (var i = 0; i < size.sizeX(); i++) {
                var slot = size.indexOf(i, j);
                var item = stacks.get(slot);
                var sx = cx + 18 * i + 1;
                GuiUtils.drawRect(graphics, sx, cy + 1, sx + 16, cy + 1 + 16, GuiUtils.COLOR_CONTAINER_SLOT | 0xFF000000);
                graphics.item(item, sx, cy + 1);
                graphics.itemDecorations(font, item, sx, cy + 1);
                if (GuiUtils.isHover(sx, cy + 1, 16, 16, mouseX, mouseY)) {
                    GuiUtils.drawRect(graphics, sx, cy, sx + 18, cy + 18, GuiUtils.COLOR_CONTAINER_SLOT | 0x66000000);
                    hoverStack = item;
                    hoverSlot = slot;
                }
            }
            cy += 18;
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (hoverStack != null) {
                if (hoverStack.getItem() != Items.AIR) {
                    graphics.pose().pushMatrix();
                    GuiUtils.setTooltipForNextFrame(graphics, font, hoverStack, mouseX, mouseY);
                    graphics.pose().popMatrix();
                } else if (slotNames != null && hoverSlot >= 0 && hoverSlot < slotNames.size()) {
                    GuiUtils.setTooltipForNextFrame(graphics, font, List.of(slotNames.get(hoverSlot)), java.util.Optional.empty(), mouseX, mouseY);
                }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        var size = data.size();
        var stacks = data.stacks();
        var cy = height / 2 - size.sizeY() * 18 / 2;
        var cx = width / 2 - size.sizeX() * 18 / 2;

        for (var j = 0; j < size.sizeY(); j++) {
            for (var i = 0; i < size.sizeX(); i++) {
                var slot = size.indexOf(i, j);
                var item = stacks.get(slot);
                var sx = cx + 18 * i + 1;
                if (GuiUtils.isHover(sx, cy + 1, 16, 16, (int) mouseX, (int) mouseY)) {
                    playClick();
                    mc.gui.setScreen(new GuiItemStackModifier(this, item, newItem -> {
                        stacks.set(slot, newItem);
                    }));
                    return true;
                }
            }
            cy += 18;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void generateDev(List<ACTDevInfo> entries, int mouseX, int mouseY) {
        var size = data.size();
        entries.add(devInfo("Inventory", "(W,H) = (" + size.sizeX() + ", " + size.sizeY() + ")"));
        super.generateDev(entries, mouseX, mouseY);
    }
}
