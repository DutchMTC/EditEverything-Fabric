package com.dutchmtc.ee.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiItemStackModifier;
import com.dutchmtc.ee.gui.modifier.GuiModifier;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemReader;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ItemUtilsClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.function.Consumer;

public class GuiGiver extends GuiModifier<String> {
    private Button giveButton, saveButton, doneButton;
    private EditBox code;
    private String preText;
    private ItemStack currentItemStack;
    private Consumer<String> setter;
    private boolean deleteButton;
    private String lastCodeValue;
    private String originalCodeValue;

    private HolderLookup.Provider registryAccess() {
        return mc.level != null ? mc.level.registryAccess() : VanillaRegistries.createLookup();
    }

    public GuiGiver(Screen parent) {
        super(parent, Component.translatable("gui.ee.give"), s -> {
        });
        if (mc.player != null) {
            this.currentItemStack = mc.player.getMainHandItem();
            this.preText = ItemUtils.getGiveCode(this.currentItemStack, registryAccess());
        }
    }

    public GuiGiver(Screen parent, ItemStack itemStack) {
        this(parent, itemStack, null, false);
    }

    public GuiGiver(Screen parent, ItemStack itemStack, Consumer<String> setter, boolean deleteButton) {
        super(parent, Component.translatable("gui.ee.give"), s -> {
        });
        this.preText = itemStack != null ? ItemUtils.getGiveCode(itemStack, registryAccess()) : "";
        this.currentItemStack = itemStack;
        this.setter = setter;
        this.deleteButton = deleteButton;
    }

    public GuiGiver(Screen parent, String preText) {
        this(parent, preText, s -> {
        }, false);
    }

    public GuiGiver(Screen parent, String preText, Consumer<String> setter, boolean deleteButton) {
        // Don't parse the item stack here: use the client's active registry access in tick/init.
        this(parent, (ItemStack) null);
        this.preText = preText;
        this.setter = setter;
        this.deleteButton = deleteButton;
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null && parent.isPauseScreen();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawCenterString(graphics, font, I18n.get("gui.ee.give"), width / 2, code.getY() - 21, Color.ORANGE.getRGB(), 20);
        if (currentItemStack != null) {
            GuiUtils.drawItemStack(graphics, currentItemStack, code.getX() + code.getWidth() + 5, code.getY() - 2);
            if (GuiUtils.isHover(code.getX() + code.getWidth() + 5, code.getY(), 20, 20, mouseX, mouseY))
                GuiUtils.setTooltipForNextFrame(graphics, font, currentItemStack, mouseX, mouseY);
        }
    }

    @Override
    public void init() {

        code = new EditBox(font, width / 2 - 178, height / 2 + 2, 356, 16, Component.literal(""));
        code.setMaxLength(Integer.MAX_VALUE);
        if (preText != null)
            code.setValue(ChatUtils.untranslateColorCodes(preText));
        originalCodeValue = code.getValue();
        addRenderableWidget(code);
        int s1 = deleteButton ? 120 : 180;
        int s2 = 120;
        addRenderableWidget(giveButton = new EEButton(width / 2 - 180, height / 2 + 21, s1, 20,
                Component.translatable("gui.ee.give.give"), b -> ItemUtilsClient.give(currentItemStack)));
        addRenderableWidget(new EEButton(width / 2 + s1 - 178, height / 2 + 21, s1 - 2, 20,
                Component.translatable("gui.ee.give.copy"), b -> GuiUtils.addToClipboard(code.getValue())));
        addRenderableWidget(
                new EEButton(width / 2 - 180 + (deleteButton ? 2 * s1 + 1 : 0), height / 2 + 21 + (deleteButton ? 0 : 21),
                        (deleteButton ? s1 - 1 : s2), 20, Component.translatable("gui.ee.give.editor"), b -> getMinecraft().gui.setScreen(new GuiItemStackModifier(this, currentItemStack,
                                this::setCurrent))));
        doneButton = addRenderableWidget(new EEButton(width / 2 - 179 + 2 * s2, height / 2 + 42, s2 - 1, 20,
                Component.translatable("gui.done"), b -> {
            if (setter != null && currentItemStack != null)
                setter.accept(code.getValue());
            getMinecraft().gui.setScreen(parent);
        }));
        if (setter != null)
            addRenderableWidget(new EEButton(width / 2 - 58, height / 2 + 42, s2 - 2, 20,
                    Component.translatable("gui.ee.cancel"), b -> onCancel()));
        else
            saveButton = addRenderableWidget(new EEButton(width / 2 - 58, height / 2 + 42, s2 - 2, 20,
                    Component.translatable("gui.ee.save"), b -> {
                if (parent instanceof GuiMenu)
                    ((GuiMenu) parent).get();
                EEMod.saveItem(code.getValue());
                getMinecraft().gui.setScreen(new GuiMenu(parent));
            }));
        if (deleteButton)
            addRenderableWidget(new EEButton(width / 2 - 180, height / 2 + 42, s2, 20,
                    Component.translatable("gui.ee.delete"), b -> {
                getMinecraft().gui.setScreen(new GuiConfirmation(this,
                        Component.translatable("gui.ee.menu.delete_question"),
                        Component.translatable("gui.ee.delete"),
                        () -> {
                            setter.accept(null);
                            getMinecraft().gui.setScreen(parent);
                        },
                        () -> getMinecraft().gui.setScreen(this)));
            }));

        super.init();
        tick();
    }

    @Override
    public boolean isModified() {
        return code != null && originalCodeValue != null && !code.getValue().equals(originalCodeValue);
    }

    private void setCurrent(ItemStack currentItemStack) {
        preText = ItemUtils.getGiveCode(this.currentItemStack = currentItemStack, registryAccess());
    }

    public void setCurrentItemStack(ItemStack currentItemStack) {
        this.currentItemStack = currentItemStack;
        this.preText = ItemUtils.getGiveCode(currentItemStack, registryAccess());
    }

    public void setPreText(String preText) {
        this.preText = preText;
        this.currentItemStack = preText != null && !preText.isEmpty()
                ? ItemUtils.getFromGiveCode(preText, registryAccess())
                : null;
    }

    @Override
    public void tick() {
        // code.tick();
        String codeValue = code.getValue();
        if (!codeValue.equals(lastCodeValue)) {
            this.lastCodeValue = codeValue;
            this.currentItemStack = ItemUtils
                    .getFromGiveCode(ChatUtils.translateColorCodes(codeValue), registryAccess());
        }
        this.giveButton.active = this.currentItemStack != null && getMinecraft().player != null
                && getMinecraft().player.isCreative();
        this.doneButton.active = setter == null || this.currentItemStack != null;
        if (saveButton != null)
            saveButton.active = this.currentItemStack != null;
        super.tick();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (code != null && code.isFocused() && event.hasControlDown() && event.key() == GLFW.GLFW_KEY_V) {
            String clipboard = GuiUtils.getClipboardText();
            if (clipboard == null || clipboard.isEmpty()) {
                return super.keyPressed(event);
            }
            String filtered = ItemReader.extractItemPart(clipboard);
            if (!filtered.isEmpty()) {
                code.insertText(filtered);
            }
            return true;
        }
        return super.keyPressed(event);
    }
}
