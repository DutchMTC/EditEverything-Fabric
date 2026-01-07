package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.gui.selector.GuiTypeListSelector;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
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

public class GuiItemStackModifier extends GuiModifier<ItemStack> {
    private ItemStack currentItemStack;
    private ItemStack originalItemStack;

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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);

        super.render(graphics, mouseX, mouseY, partialTicks);
        if (currentItemStack != null) {
            GuiUtils.drawItemStack(graphics, currentItemStack, width / 2 - 10, height / 2 - 63);
            if (GuiUtils.isHover(width / 2 - 10, height / 2 - 63, 20, 20, mouseX, mouseY))
                GuiUtils.renderTooltip(graphics, font, currentItemStack, mouseX, mouseY);
        }
    }

    @Override
    public void init() {

        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 - 42, 100, 20,
                Component.translatable("gui.ee.modifier.name"), b -> getMinecraft().setScreen(new GuiStringModifier(GuiItemStackModifier.this,
                Component.translatable("gui.ee.modifier.name"),
                currentItemStack.getHoverName().getString().replaceAll("" + ChatUtils.MODIFIER, "&"),
                name -> currentItemStack.set(DataComponents.CUSTOM_NAME, name.isEmpty() ? null
                        : Component.literal(name.replaceAll("&", String.valueOf(ChatUtils.MODIFIER))))))));

        addRenderableWidget(new EEButton(width / 2 + 1, height / 2 - 42, 99, 20,
                Component.translatable("gui.ee.modifier.lore"), b -> getMinecraft().setScreen(new GuiStringArrayModifier(GuiItemStackModifier.this,
                Component.translatable("gui.ee.modifier.lore"), ItemUtils.getLore(currentItemStack),
                value -> {
                    for (int i = 0; i < value.length; i++)
                        value[i] = value[i].replaceAll("&", String.valueOf(ChatUtils.MODIFIER));
                    ItemUtils.setLore(currentItemStack, value);
                }))));
        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 - 21, 100, 20,
                Component.translatable("gui.ee.modifier.ench"), b -> getMinecraft().setScreen(
                new GuiEnchModifier(GuiItemStackModifier.this, ItemUtils.getEnchantments(currentItemStack),
                        list -> ItemUtils.setEnchantments(list, currentItemStack, false,
                                getMinecraft().level != null ? getMinecraft().level.registryAccess() : null)))));
        addRenderableWidget(new EEButton(width / 2 + 1, height / 2 - 21, 99, 20,
                Component.translatable("gui.ee.modifier.attr"), b -> getMinecraft().setScreen(new GuiAttributeModifier(GuiItemStackModifier.this,
                ItemUtils.getAttributes(currentItemStack),
                list -> ItemUtils.setAttributes(list, currentItemStack)))));
        addRenderableWidget(new EEButton(width / 2 - 100, height / 2, 100, 20,
                Component.translatable("gui.ee.modifier.type"), b -> getMinecraft().setScreen(new GuiTypeListSelector(GuiItemStackModifier.this,
                Component.translatable("gui.ee.modifier.type"), is -> {
            currentItemStack = ItemUtils.setItem(is.getItem(), currentItemStack);
            if (currentItemStack.getCount() == 0) {
                currentItemStack.setCount(1);
            }
            return GuiItemStackModifier.this;
        }))));
        addRenderableWidget(
                new EEButton(width / 2 + 1, height / 2, 99, 20, Component.translatable("gui.ee.modifier.meta"), b -> getMinecraft().setScreen(new GuiMetaModifier(GuiItemStackModifier.this, is -> currentItemStack = is,
                        currentItemStack.copy()))));
        int i = 1;
        if (ItemUtils.isLeatherArmor(currentItemStack))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.setColor"), b -> getMinecraft().setScreen(new GuiColorModifier(GuiItemStackModifier.this,
                    color -> ItemUtils.setColor(currentItemStack, color),
                    ItemUtils.getColor(currentItemStack)))));
        else if (currentItemStack.getItem().equals(Items.ENCHANTED_BOOK))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.ench").append(" (")
                            .append(Items.ENCHANTED_BOOK.getName()).append(")"),
                    b -> getMinecraft().setScreen(new GuiEnchModifier(GuiItemStackModifier.this,
                            ItemUtils.getEnchantments(currentItemStack, true),
                            list -> ItemUtils.setEnchantments(list, currentItemStack, true,
                                    getMinecraft().level != null ? getMinecraft().level.registryAccess() : null)))));
        else if (currentItemStack.getItem().equals(Items.POTION)
                || currentItemStack.getItem().equals(Items.SPLASH_POTION)
                || currentItemStack.getItem().equals(Items.LINGERING_POTION)
                || currentItemStack.getItem().equals(Items.TIPPED_ARROW)) {
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 100, 20,
                    Component.translatable("gui.ee.modifier.meta.potion"), b -> getMinecraft().setScreen(new GuiPotionModifier(GuiItemStackModifier.this,
                    pi -> ItemUtils.setPotionInformation(currentItemStack, pi),
                    ItemUtils.getPotionInformation(currentItemStack)))));
            addRenderableWidget(new EEButton(width / 2 + 1, height / 2 + 21, 99, 20,
                    Component.translatable("gui.ee.modifier.meta.potionType"), b -> {
                NonNullList<ItemStack> potionType = NonNullList.create();
                potionType.add(new ItemStack(Items.POTION));
                potionType.add(new ItemStack(Items.SPLASH_POTION));
                potionType.add(new ItemStack(Items.LINGERING_POTION));
                potionType.add(new ItemStack(Items.TIPPED_ARROW));
                getMinecraft().setScreen(new GuiTypeListSelector(GuiItemStackModifier.this,
                        Component.translatable("gui.ee.modifier.meta.potionType"), is -> {
                    currentItemStack = ItemUtils.setItem(is.getItem(), currentItemStack);
                    if (currentItemStack.getCount() == 0) {
                        currentItemStack.setCount(1);
                    }
                    return GuiItemStackModifier.this;
                }, potionType));
            }));
        } else if (currentItemStack.getItem().equals(Items.PLAYER_HEAD))
            addRenderableWidget(
                    new EEButton(width / 2 - 100, height / 2 + 21, 200, 20, Items.PLAYER_HEAD.getName(), b -> getMinecraft().setScreen(new GuiHeadModifier(GuiItemStackModifier.this,
                            is -> currentItemStack = is, currentItemStack))));
        else if (currentItemStack.getItem().equals(Items.ARMOR_STAND))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.armorstand"),
                    b -> getMinecraft().setScreen(new com.dutchmtc.ee.gui.GuiArmorStandItemEditor(GuiItemStackModifier.this, currentItemStack.copy(),
                            is -> currentItemStack = is))));
        else if (currentItemStack.getItem().equals(Items.COMMAND_BLOCK_MINECART)
                || currentItemStack.getItem().equals(Blocks.COMMAND_BLOCK.asItem())
                || currentItemStack.getItem().equals(Blocks.CHAIN_COMMAND_BLOCK.asItem())
                || currentItemStack.getItem().equals(Blocks.REPEATING_COMMAND_BLOCK.asItem())) {
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.command"), b -> getMinecraft().setScreen(new GuiCommandBlockModifier(GuiItemStackModifier.this,
                    is -> currentItemStack = is, currentItemStack))));
        } else if (currentItemStack.getItem() instanceof SpawnEggItem)
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.setEntity"), b -> getMinecraft().setScreen(new GuiSpawnEggModifier(GuiItemStackModifier.this,
                    is -> currentItemStack = is, currentItemStack))));
        else if (currentItemStack.getItem().equals(Items.FIREWORK_ROCKET))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.fireworks"), b -> getMinecraft().setScreen(new GuiFireworksModifer(GuiItemStackModifier.this, tag -> {
                ItemUtils.setFireworksFromTag(currentItemStack, tag);
            }, ItemUtils.getFireworksTag(currentItemStack)))));
        else if (currentItemStack.getItem().equals(Items.FIREWORK_STAR))
            addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 21, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.explosion"), b -> getMinecraft().setScreen(new GuiFireworksModifer.GuiExplosionModifier(this, exp -> {
                ItemUtils.setFireworkExplosionFromTag(currentItemStack, exp.getTag());
            }, ItemUtils.getExplosionInformation(ItemUtils.getFireworkExplosionTag(currentItemStack))))));
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
                    .setScreen(new GuiContainerModifier(this, currentItemStack.getHoverName(), data -> ItemUtils.setContainerData(currentItemStack, data), Objects.requireNonNull(ItemUtils.fetchContainerData(currentItemStack))))));
        else
            i = 0;
        addRenderableWidget(new EEButton(width / 2 + 1, height / 2 + 25 + 21 * i, 99, 20,
                Component.translatable("gui.ee.cancel"), b -> onCancel()));
        addRenderableWidget(new EEButton(width / 2 - 100, height / 2 + 25 + 21 * i, 100, 20,
                Component.translatable("gui.done"), b -> {
            set(currentItemStack);
            getMinecraft().setScreen(parent);
        }));
        super.init();
    }
}
