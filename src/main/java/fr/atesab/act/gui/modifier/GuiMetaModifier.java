package fr.atesab.act.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.gui.modifier.nbt.GuiNBTModifier;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class GuiMetaModifier extends GuiModifier<ItemStack> {
    private final ItemStack stack;
    private final ItemStack originalStack;

    public GuiMetaModifier(Screen parent, Consumer<ItemStack> setter, ItemStack stack) {
        super(parent, Component.translatable("gui.act.modifier.meta"), setter);
        this.stack = stack;
        this.originalStack = stack.copy();
    }

    @Override
    public boolean isModified() {
        return !ItemStack.matches(stack, originalStack);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawItemStack(graphics, stack, width / 2 - 10, height / 2 - 21);
        if (GuiUtils.isHover(width / 2 - 10, height / 2 - 21, 20, 20, mouseX, mouseY))
            GuiUtils.renderTooltip(graphics, font, stack, mouseX, mouseY);
        reRenderWidgets(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void init() {
        int i = 0;
        addRenderableWidget(new GuiBooleanButton(width / 2 - 100, height / 2 - 21 + 21 * ++i,
                Component.translatable("item.unbreakable"), b -> ItemUtils.setUnbreakable(stack, b),
                () -> ItemUtils.isUnbreakable(stack)));

        addRenderableWidget(new ACTButton(width / 2 - 100, height / 2 - 21 + 21 * ++i, 200, 20,
                Component.translatable("gui.act.modifier.meta.canBreak"),
                b -> getMinecraft().setScreen(new GuiAdventureBlockModifier(GuiMetaModifier.this, stack,
                        GuiAdventureBlockModifier.Kind.CAN_BREAK))));

        addRenderableWidget(new ACTButton(width / 2 - 100, height / 2 - 21 + 21 * ++i, 200, 20,
                Component.translatable("gui.act.modifier.meta.canPlace"),
                b -> getMinecraft().setScreen(new GuiAdventureBlockModifier(GuiMetaModifier.this, stack,
                        GuiAdventureBlockModifier.Kind.CAN_PLACE))));

        addRenderableWidget(new ACTButton(width / 2 - 100, height / 2 - 21 + 21 * ++i, 200, 20,
                Component.translatable("gui.act.modifier.meta.dataComponents"),
                b -> getMinecraft().setScreen(new GuiDataComponentModifier(GuiMetaModifier.this, stack))));

        addRenderableWidget(new ACTButton(width / 2 - 100, height / 2 - 21 + 21 * ++i, 200, 20,
                Component.translatable("gui.act.modifier.tag.editor"), b -> getMinecraft().setScreen(new GuiNBTModifier(GuiMetaModifier.this, tag -> ItemUtils.setTag(stack, tag),
                ItemUtils.getTag(stack) != null ? ItemUtils.getTag(stack) : new CompoundTag()))));
        addRenderableWidget(new ACTButton(width / 2 - 100, height / 2 - 17 + 21 * ++i, 100, 20,
                Component.translatable("gui.done"), b -> {
            set(stack);
            getMinecraft().setScreen(parent);
        }));
        addRenderableWidget(new ACTButton(width / 2 + 1, height / 2 - 17 + 21 * i, 99, 20,
                Component.translatable("gui.act.cancel"), b -> onCancel()));
        super.init();
    }

}
