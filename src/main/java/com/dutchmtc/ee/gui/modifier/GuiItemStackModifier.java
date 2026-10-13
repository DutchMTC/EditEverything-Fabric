package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.gui.selector.GuiTypeListSelector;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.AdventureBlockLore;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;

public class GuiItemStackModifier extends GuiModifier<ItemStack> {
    private ItemStack currentItemStack;
    private ItemStack originalItemStack;
    private Function<ItemStack, String> saveValidation = value -> null;
    private Button saveButton;

    public GuiItemStackModifier withSaveValidation(Function<ItemStack, String> validation) {
        saveValidation = validation;
        return this;
    }

    private void refreshSave() {
        if (saveButton == null) return;
        String reason = saveValidation.apply(currentItemStack);
        saveButton.active = reason == null;
        saveButton.setTooltip(reason == null ? null : Tooltip.create(Component.literal(reason)));
    }

    @Override public void tick() { refreshSave(); }

    public GuiItemStackModifier(Screen parent, ItemStack currentItemStack, Consumer<ItemStack> setter) {
        super(parent, Component.translatable("gui.ee.give.editor"), setter);
        this.currentItemStack = currentItemStack != null ? currentItemStack : new ItemStack(Blocks.STONE);
        if (ItemUtils.getTag(this.currentItemStack) == null)
            ItemUtils.setTag(this.currentItemStack, new CompoundTag());
        this.originalItemStack = this.currentItemStack.copy();
    }

    @Override
    public boolean isModified() {
        return !ItemStack.matches(currentItemStack, originalItemStack);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);

        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        if (currentItemStack != null) {
            GuiUtils.drawItemStack(graphics, currentItemStack, width / 2 - 10, height / 2 - 63);
            if (GuiUtils.isHover(width / 2 - 10, height / 2 - 63, 20, 20, mouseX, mouseY))
                GuiUtils.setTooltipForNextFrame(graphics, font, currentItemStack, mouseX, mouseY);
        }
    }

    @Override
    public void init() {
        if (getMinecraft().player != null && !getMinecraft().player.isCreative()) {
            getMinecraft().player.sendSystemMessage(Component.translatable("gui.ee.nocreative").withStyle(ChatFormatting.RED));
            getMinecraft().gui.setScreen(parent);
            return;
        }

        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 - 42, 100, 20,
                Component.translatable("gui.ee.modifier.name"),
                b -> getMinecraft().gui.setScreen(new GuiStyledTextEditor(
                        GuiItemStackModifier.this,
                        "Item name",
                        ItemUtils.getComponent(currentItemStack, DataComponents.CUSTOM_NAME) != null
                                ? ItemUtils.getComponent(currentItemStack, DataComponents.CUSTOM_NAME)
                                : Component.literal(currentItemStack.getHoverName().getString()),
                        256, newName -> currentItemStack.set(DataComponents.CUSTOM_NAME,
                                newName.getString().isEmpty() ? null : newName)))));

        addRenderableWidget(new EEButton(width / 2 + 1, height / 2 - 42, 99, 20,
                Component.translatable("gui.ee.modifier.lore"), b -> {
            getMinecraft().gui.setScreen(new GuiLoreEditor(GuiItemStackModifier.this,
                    AdventureBlockLore.editableLore(currentItemStack), AdventureBlockLore.editableLimit(currentItemStack),
                    value -> ItemUtils.setLoreComponents(currentItemStack, value)));
        }));
        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 - 21, 100, 20,
                Component.translatable("gui.ee.modifier.ench"), b -> getMinecraft().gui.setScreen(
                new GuiEnchModifier(GuiItemStackModifier.this, ItemUtils.getEnchantments(currentItemStack),
                        list -> ItemUtils.setEnchantments(list, currentItemStack, false,
                                getMinecraft().level != null ? getMinecraft().level.registryAccess() : null)))));
        addRenderableWidget(new EEButton(width / 2 + 1, height / 2 - 21, 99, 20,
                Component.translatable("gui.ee.modifier.attr"), b -> getMinecraft().gui.setScreen(new GuiAttributeModifier(GuiItemStackModifier.this,
                ItemUtils.getAttributes(currentItemStack),
                list -> ItemUtils.setAttributes(list, currentItemStack)))));
        addRenderableWidget(new EEButton(width / 2 - 100, height / 2, 100, 20,
                Component.translatable("gui.ee.modifier.type"), b -> getMinecraft().gui.setScreen(new GuiTypeListSelector(GuiItemStackModifier.this,
                Component.translatable("gui.ee.modifier.type"), is -> {
            currentItemStack = is.copy();
            return GuiItemStackModifier.this;
        }, currentItemStack))));
        addRenderableWidget(
                new EEButton(width / 2 + 1, height / 2, 99, 20, Component.literal("Item settings"), b -> getMinecraft().gui.setScreen(new GuiItemSettingsEditor(GuiItemStackModifier.this,
                        currentItemStack, is -> currentItemStack = is.copy()))));
        int i = 1;
        if (ItemUtils.isLeatherArmor(currentItemStack))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.setColor"), b -> getMinecraft().gui.setScreen(new GuiColorModifier(GuiItemStackModifier.this,
                    color -> ItemUtils.setColor(currentItemStack, color),
                    ItemUtils.getColor(currentItemStack)))));
        else if (currentItemStack.getItem().equals(Items.ENCHANTED_BOOK))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.ench").append(" (")
                            .append(Items.ENCHANTED_BOOK.getName(new ItemStack(Items.ENCHANTED_BOOK))).append(")"),
                    b -> getMinecraft().gui.setScreen(new GuiEnchModifier(GuiItemStackModifier.this,
                            ItemUtils.getEnchantments(currentItemStack, true),
                            list -> ItemUtils.setEnchantments(list, currentItemStack, true,
                                    getMinecraft().level != null ? getMinecraft().level.registryAccess() : null), true))));
        else if (com.dutchmtc.ee.utils.BookEditorData.supports(currentItemStack))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.literal("Edit book"), b -> getMinecraft().gui.setScreen(new GuiBookEditor(this,
                    currentItemStack, edited -> currentItemStack = edited))));
        else if (currentItemStack.getItem().equals(Items.POTION)
                || currentItemStack.getItem().equals(Items.SPLASH_POTION)
                || currentItemStack.getItem().equals(Items.LINGERING_POTION)
                || currentItemStack.getItem().equals(Items.TIPPED_ARROW)) {
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.literal("Potion editor"), b -> getMinecraft().gui.setScreen(
                    new GuiPotionModifier(this, currentItemStack, edited -> currentItemStack = edited))));
        } else if (currentItemStack.getItem().equals(Items.PLAYER_HEAD))
            addRenderableWidget(
                    new EEButton(width / 2 - 100, height / 2 + 21, 200, 20, Items.PLAYER_HEAD.getName(new ItemStack(Items.PLAYER_HEAD)), b -> getMinecraft().gui.setScreen(new GuiHeadModifier(GuiItemStackModifier.this,
                            is -> currentItemStack = is, currentItemStack))));
        else if (currentItemStack.getItem().equals(Items.ARMOR_STAND))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.armorstand"),
                    b -> getMinecraft().gui.setScreen(new com.dutchmtc.ee.gui.GuiArmorStandItemEditor(GuiItemStackModifier.this, currentItemStack.copy(),
                            is -> currentItemStack = is))));
        else if (currentItemStack.getItem().equals(Items.COMMAND_BLOCK_MINECART)
                || currentItemStack.getItem().equals(Blocks.COMMAND_BLOCK.asItem())
                || currentItemStack.getItem().equals(Blocks.CHAIN_COMMAND_BLOCK.asItem())
                || currentItemStack.getItem().equals(Blocks.REPEATING_COMMAND_BLOCK.asItem())) {
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.command"), b -> getMinecraft().gui.setScreen(new GuiCommandBlockModifier(GuiItemStackModifier.this,
                    is -> currentItemStack = is, currentItemStack))));
        } else if (GuiSignEditor.supports(currentItemStack))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.literal("Edit sign"), b -> getMinecraft().gui.setScreen(
                    new GuiSignEditor(GuiItemStackModifier.this, currentItemStack,
                            edited -> currentItemStack = edited))));
        else if (currentItemStack.getItem() instanceof SpawnEggItem)
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.setEntity"), b -> getMinecraft().gui.setScreen(new GuiSpawnEggModifier(GuiItemStackModifier.this,
                    is -> currentItemStack = is, currentItemStack))));
        else if (currentItemStack.getItem().equals(Items.FIREWORK_ROCKET))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.fireworks"), b -> getMinecraft().gui.setScreen(new GuiFireworksModifer(GuiItemStackModifier.this, tag -> {
                ItemUtils.setFireworksFromTag(currentItemStack, tag);
            }, ItemUtils.getFireworksTag(currentItemStack)))));
        else if (currentItemStack.getItem().equals(Items.FIREWORK_STAR))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.explosion"), b -> getMinecraft().gui.setScreen(new GuiFireworksModifer.GuiExplosionModifier(this, exp -> {
                ItemUtils.setFireworkExplosionFromTag(currentItemStack, exp.getTag());
            }, ItemUtils.getExplosionInformation(ItemUtils.getFireworkExplosionTag(currentItemStack))))));
        else if (currentItemStack.getItem() instanceof BannerItem || currentItemStack.getItem() instanceof ShieldItem)
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.banner"),
                    b -> getMinecraft().gui.setScreen(new GuiBannerEditor(GuiItemStackModifier.this,
                            currentItemStack, is -> currentItemStack = is))));
        else if (currentItemStack.getItem().equals(Items.LIGHT)) {
            addRenderableWidget(new AbstractSliderButton(width / 2 - 100, height / 2 + 21, 200, 20, Component.empty(), ItemUtils.getLightLevel(currentItemStack) / 15.0) {
                {
                    updateMessage();
                }
                @Override
                protected void updateMessage() {
                    setMessage(Component.translatable("gui.ee.modifier.light")
                            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("" + (int) (value * 15)).withStyle(ChatFormatting.GOLD)));
                }

                @Override
                protected void applyValue() {
                    ItemUtils.setLightLevel(currentItemStack, (int) (value * 15));
                }

            });
        } else if (ItemUtils.isContainer(currentItemStack))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.inventory"), b -> getMinecraft()
                    .gui.setScreen(new GuiContainerModifier(this, currentItemStack.getHoverName(), data -> ItemUtils.setContainerData(currentItemStack, data), Objects.requireNonNull(ItemUtils.fetchContainerData(currentItemStack)),
                            currentItemStack.getItem() instanceof BlockItem blockItem
                                    && blockItem.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
                                    ? List.of(Component.literal("Input"), Component.literal("Fuel"), Component.literal("Output")) : null))));
        else
            i = 0;
        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 25 + 21 * i, 100, 20,
                Component.translatable("gui.ee.cancel"), b -> onCancel()));
        saveButton = addRenderableWidget(new EEButton(width / 2 + 1, height / 2 + 25 + 21 * i, 99, 20,
                Component.translatable("gui.done"), b -> {
            if (saveValidation.apply(currentItemStack) != null) return;
            set(currentItemStack);
            getMinecraft().gui.setScreen(parent);
        }));
        super.init();
        refreshSave();
    }
}
