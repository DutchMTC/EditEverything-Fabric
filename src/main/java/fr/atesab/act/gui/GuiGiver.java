package fr.atesab.act.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atesab.act.ACTMod;
import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.gui.modifier.GuiItemStackModifier;
import fr.atesab.act.gui.modifier.GuiModifier;
import fr.atesab.act.utils.ChatUtils;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
import java.util.function.Consumer;

public class GuiGiver extends GuiModifier<String> {
    private Button giveButton, saveButton, doneButton;
    private EditBox code;
    private String preText;
    private ItemStack currentItemStack;
    private Consumer<String> setter;
    private boolean deleteButton;

    public GuiGiver(Screen parent) {
        super(parent, Component.translatable("gui.act.give"), s -> {
        });
        if (mc.player != null) {
            this.currentItemStack = mc.player.getMainHandItem();
            this.preText = ItemUtils.getGiveCode(this.currentItemStack);
        }
    }

    public GuiGiver(Screen parent, ItemStack itemStack) {
        this(parent, itemStack, null, false);
    }

    public GuiGiver(Screen parent, ItemStack itemStack, Consumer<String> setter, boolean deleteButton) {
        super(parent, Component.translatable("gui.act.give"), s -> {
        });
        this.preText = itemStack != null ? ItemUtils.getGiveCode(itemStack) : "";
        this.currentItemStack = itemStack;
        this.setter = setter;
        this.deleteButton = deleteButton;
    }

    public GuiGiver(Screen parent, String preText) {
        this(parent, preText, s -> {
        }, false);
    }

    public GuiGiver(Screen parent, String preText, Consumer<String> setter, boolean deleteButton) {
        this(parent, preText != null && !preText.isEmpty() ? ItemUtils.getFromGiveCode(preText) : null);
        this.preText = preText;
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null && parent.isPauseScreen();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawCenterString(graphics, font, I18n.get("gui.act.give"), width / 2, code.getY() - 21, Color.ORANGE.getRGB(), 20);
        if (currentItemStack != null) {
            GuiUtils.drawItemStack(graphics, currentItemStack, code.getX() + code.getWidth() + 5, code.getY() - 2);
            if (GuiUtils.isHover(code.getX() + code.getWidth() + 5, code.getY(), 20, 20, mouseX, mouseY))
                graphics.renderTooltip(font, currentItemStack, mouseX, mouseY);
        }
    }

    @Override
    public void init() {

        code = new EditBox(font, width / 2 - 178, height / 2 + 2, 356, 16, Component.literal(""));
        code.setMaxLength(Integer.MAX_VALUE);
        if (preText != null)
            code.setValue(preText.replaceAll(String.valueOf(ChatUtils.MODIFIER), "&"));
        addRenderableWidget(code);
        int s1 = deleteButton ? 120 : 180;
        int s2 = 120;
        addRenderableWidget(giveButton = new ACTButton(width / 2 - 180, height / 2 + 21, s1, 20,
                Component.translatable("gui.act.give.give"), b -> ItemUtils.give(currentItemStack)));
        addRenderableWidget(new ACTButton(width / 2 + s1 - 178, height / 2 + 21, s1 - 2, 20,
                Component.translatable("gui.act.give.copy"), b -> GuiUtils.addToClipboard(code.getValue())));
        addRenderableWidget(
                new ACTButton(width / 2 - 180 + (deleteButton ? 2 * s1 + 1 : 0), height / 2 + 21 + (deleteButton ? 0 : 21),
                        (deleteButton ? s1 - 1 : s2), 20, Component.translatable("gui.act.give.editor"), b -> getMinecraft().setScreen(new GuiItemStackModifier(this, currentItemStack,
                                this::setCurrent))));
        doneButton = addRenderableWidget(new ACTButton(width / 2 - 179 + 2 * s2, height / 2 + 42, s2 - 1, 20,
                Component.translatable("gui.done"), b -> {
            if (setter != null && currentItemStack != null)
                setter.accept(code.getValue());
            getMinecraft().setScreen(parent);
        }));
        if (setter != null)
            addRenderableWidget(new ACTButton(width / 2 - 58, height / 2 + 42, s2 - 2, 20,
                    Component.translatable("gui.act.cancel"), b -> getMinecraft().setScreen(parent)));
        else
            saveButton = addRenderableWidget(new ACTButton(width / 2 - 58, height / 2 + 42, s2 - 2, 20,
                    Component.translatable("gui.act.save"), b -> {
                if (parent instanceof GuiMenu)
                    ((GuiMenu) parent).get();
                ACTMod.saveItem(code.getValue());
                getMinecraft().setScreen(new GuiMenu(parent));
            }));
        if (deleteButton)
            addRenderableWidget(new ACTButton(width / 2 - 180, height / 2 + 42, s2, 20,
                    Component.translatable("gui.act.delete"), b -> {
                setter.accept(null);
                getMinecraft().setScreen(parent);
            }));

        super.init();
        tick();
    }

    private void setCurrent(ItemStack currentItemStack) {
        preText = ItemUtils.getGiveCode(this.currentItemStack = currentItemStack);
    }

    public void setCurrentItemStack(ItemStack currentItemStack) {
        this.currentItemStack = currentItemStack;
        this.preText = ItemUtils.getGiveCode(currentItemStack);
    }

    public void setPreText(String preText) {
        this.preText = preText;
        this.currentItemStack = preText != null && !preText.isEmpty() ? ItemUtils.getFromGiveCode(preText) : null;
    }

    @Override
    public void tick() {
        // code.tick();
        this.currentItemStack = ItemUtils
                .getFromGiveCode(code.getValue().replaceAll("&", String.valueOf(ChatUtils.MODIFIER)));
        this.giveButton.active = this.currentItemStack != null && getMinecraft().player != null
                && getMinecraft().player.isCreative();
        this.doneButton.active = setter == null || this.currentItemStack != null;
        if (saveButton != null)
            saveButton.active = this.currentItemStack != null;
        super.tick();
    }
}
