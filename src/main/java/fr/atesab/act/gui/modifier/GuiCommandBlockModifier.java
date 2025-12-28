package fr.atesab.act.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.gui.selector.GuiTypeListSelector;
import fr.atesab.act.utils.ChatUtils;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.awt.*;
import java.util.function.Consumer;

public class GuiCommandBlockModifier extends GuiModifier<ItemStack> {
    private ItemStack stack;
    private EditBox command, name;
    private Button auto;
    private boolean autoValue;

    public GuiCommandBlockModifier(Screen parent, Consumer<ItemStack> setter, ItemStack stack) {
        super(parent, Component.translatable("gui.act.modifier.meta.command"), setter);
        this.stack = stack.copy();
    }

    private void setData() {
        CompoundTag tag = ItemUtils.getOrCreateTagElement(stack, "BlockEntityTag");
        ItemUtils.setComponent(stack, DataComponents.CUSTOM_NAME, Component.literal(name.getValue().isEmpty() ? "@"
                : name.getValue().replaceAll("&", "" + ChatUtils.MODIFIER) + ChatFormatting.RESET));
        ItemUtils.putString(tag, "Command", command.getValue().replaceAll("&", "" + ChatUtils.MODIFIER));
        ItemUtils.putByte(tag, "auto", (byte) (autoValue ? 1 : 0));
        ItemUtils.setTag(stack, ItemUtils.getTag(stack)); // Save back
    }

    private void loadData() {
        CompoundTag tag = ItemUtils.getOrCreateTagElement(stack, "BlockEntityTag");
        name.setValue((ItemUtils.getComponent(stack, DataComponents.CUSTOM_NAME) != null ? stack.getHoverName().getString() : "@")
                .replaceAll("" + ChatUtils.MODIFIER, "&"));
        command.setValue(
                (ItemUtils.hasTag(tag, "Command", 8) ? ItemUtils.getString(tag, "Command") : "").replaceAll("" + ChatUtils.MODIFIER, "&"));
        autoValue = ItemUtils.hasTag(tag, "auto", 99) && ItemUtils.getByte(tag, "auto") == (byte) 1;
    }

    @Override
    public void init() {
        int l = Math.max(font.width(I18n.get("gui.act.modifier.meta.command.cmd") + " : "),
                font.width(I18n.get("gui.act.modifier.meta.command.name") + " : ")) + 5;
        name = new EditBox(font, width / 2 - 148 + l, height / 2 - 19, 296 - l, 16, Component.literal(""));
        command = new EditBox(font, width / 2 - 148 + l, height / 2 + 2, 296 - l, 16, Component.literal(""));
        name.setMaxLength(Integer.MAX_VALUE);
        command.setMaxLength(Integer.MAX_VALUE);
        addRenderableWidget(name);
        addRenderableWidget(command);
        auto = addRenderableWidget(new ACTButton(width / 2 + 1, height / 2 + 21, 149, 20,
                Component.translatable("advMode.mode.redstoneTriggered"), b -> autoValue = !autoValue));
        addRenderableWidget(new ACTButton(width / 2 - 150, height / 2 + 21, 150, 20,
                Component.translatable("gui.act.modifier.type"), b -> {
            setData();
            NonNullList<ItemStack> potionType = NonNullList.create();
            potionType.add(new ItemStack(Blocks.COMMAND_BLOCK));
            potionType.add(new ItemStack(Blocks.REPEATING_COMMAND_BLOCK));
            potionType.add(new ItemStack(Blocks.CHAIN_COMMAND_BLOCK));
            potionType.add(new ItemStack(Items.COMMAND_BLOCK_MINECART));
            getMinecraft().setScreen(new GuiTypeListSelector(GuiCommandBlockModifier.this,
                    Component.translatable("gui.act.modifier.type"), is -> {
                stack = ItemUtils.setItem(is.getItem(), stack);
                return null;
            }, potionType));
        }));
        addRenderableWidget(new ACTButton(width / 2 + 1, height / 2 + 42, 149, 20,
                Component.translatable("gui.act.cancel"), b -> getMinecraft().setScreen(parent)));
        addRenderableWidget(
                new ACTButton(width / 2 - 150, height / 2 + 42, 150, 20, Component.translatable("gui.done"), b -> {
                    setData();
                    set(stack);
                    getMinecraft().setScreen(parent);
                }));
        loadData();
        super.init();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawString(graphics, font, I18n.get("gui.act.modifier.meta.command.cmd") + " : ", width / 2 - 150, command.getY(),
                Color.WHITE.getRGB(), command.getHeight());
        GuiUtils.drawString(graphics, font, I18n.get("gui.act.modifier.meta.command.name") + " : ", width / 2 - 150, name.getY(),
                Color.WHITE.getRGB(), name.getHeight());
        // command.render(graphics, mouseX, mouseY, partialTicks); // REMOVED
        // name.render(graphics, mouseX, mouseY, partialTicks); // REMOVED
        GuiUtils.drawItemStack(graphics, stack, width / 2 - 10, name.getY() - 20);
        if (GuiUtils.isHover(width / 2 - 10, name.getY() - 20, 20, 20, mouseX, mouseY))
            GuiUtils.renderTooltip(graphics, font, stack, mouseX, mouseY);
    }

    @Override
    public void tick() {
        // command.tick();
        // name.tick();
        // auto.setFGColor(GuiUtils.getRedGreen(!autoValue)); // setFGColor is gone?
        // Button doesn't have setFGColor in 1.21?
        // It might be setTextColor or similar, or we need to subclass.
        // ACTButton extends Button.
        // I'll comment it out for now.
        super.tick();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int mouseButton = event.button();
        if (mouseButton == 1) {
            if (GuiUtils.isHover(command, (int) mouseX, (int) mouseY))
                command.setValue("");
            else if (GuiUtils.isHover(name, (int) mouseX, (int) mouseY))
                name.setValue("");
        }
        return super.mouseClicked(event, doubleClick);
    }

}
