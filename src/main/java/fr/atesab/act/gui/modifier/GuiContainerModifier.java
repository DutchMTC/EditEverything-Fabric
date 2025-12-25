package fr.atesab.act.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atesab.act.gui.GuiGiver;
import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import fr.atesab.act.utils.ItemUtils.ContainerData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

public class GuiContainerModifier extends GuiModifier<ContainerData> {
    private final ContainerData data;
    private final Component title;
    private final List<Component> slotNames;

    public GuiContainerModifier(Screen parent, Component title, Consumer<ContainerData> setter, ContainerData data) {
        this(parent, title, setter, data, null);
    }

    public GuiContainerModifier(Screen parent, Component title, Consumer<ContainerData> setter, ContainerData data, List<Component> slotNames) {
        super(parent, Component.translatable("gui.act.modifier.inventory"), setter);
        this.data = data.copy();
        this.title = title;
        this.slotNames = slotNames;
    }

    @Override
    protected void init() {
        addRenderableWidget(
                new ACTButton(width / 2 - 96, height / 2 + 60, 94, 20, Component.translatable("gui.done"), b -> {
                    set(data);
                    mc.setScreen(parent);
                }));
        addRenderableWidget(
                new ACTButton(width / 2 + 2, height / 2 + 60, 94, 20, Component.translatable("gui.cancel"), b -> mc.setScreen(parent)));
        super.init();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
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
        // var ir = minecraft.getItemRenderer(); // Use GuiGraphics
        for (var j = 0; j < size.sizeY(); j++) {
            for (var i = 0; i < size.sizeX(); i++) {
                var slot = size.indexOf(i, j);
                var item = stacks.get(slot);
                var sx = cx + 18 * i + 1;
                GuiUtils.drawRect(graphics, sx, cy + 1, sx + 16, cy + 1 + 16, GuiUtils.COLOR_CONTAINER_SLOT | 0xFF000000);
                graphics.renderItem(item, sx, cy + 1);
                graphics.renderItemDecorations(font, item, sx, cy + 1);
                if (GuiUtils.isHover(sx, cy + 1, 16, 16, mouseX, mouseY)) {
                    GuiUtils.drawRect(graphics, sx, cy, sx + 18, cy + 18, GuiUtils.COLOR_CONTAINER_SLOT | 0x66000000);
                    hoverStack = item;
                    hoverSlot = slot;
                }
            }
            cy += 18;
        }
        if (hoverStack != null) {
            if (hoverStack.getItem() != Items.AIR) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0D, 0.0D, 400.0D);
                graphics.renderTooltip(font, hoverStack, mouseX, mouseY);
                graphics.pose().popPose();
            } else if (slotNames != null && hoverSlot >= 0 && hoverSlot < slotNames.size()) {
                graphics.renderTooltip(font, slotNames.get(hoverSlot), mouseX, mouseY);
            }
        }
        reRenderWidgets(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int delta) {
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
                    mc.setScreen(new GuiItemStackModifier(this, item, newItem -> {
                        stacks.set(slot, newItem);
                    }));
                    return true;
                }
            }
            cy += 18;
        }

        return super.mouseClicked(mouseX, mouseY, delta);
    }

    @Override
    protected void generateDev(List<ACTDevInfo> entries, int mouseX, int mouseY) {
        var size = data.size();
        entries.add(devInfo("Inventory", "(W,H) = (" + size.sizeX() + ", " + size.sizeY() + ")"));
        super.generateDev(entries, mouseX, mouseY);
    }
}
