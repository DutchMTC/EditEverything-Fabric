package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;

import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.Minecraft;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuiEnchModifier extends GuiListModifier<List<Tuple<Enchantment, Integer>>> {

    static class EnchListElement extends ListElement {
        private final Enchantment enchantment;
        private int level;
        private final EditBox textField;
        private boolean err = false;

        public EnchListElement(GuiEnchModifier parent, Enchantment enchantment, int level) {
            super(220, 21);
            this.enchantment = enchantment;
            this.level = level;
            this.textField = new EditBox(font, 112, 1, 46, 18, Component.literal(""));
            textField.setMaxLength(6);
            textField.setValue(String.valueOf(level == 0 ? "" : level));
            buttonList.add(new EEButton(160, 0, 40, 20, Component.translatable("gui.ee.modifier.ench.max"), b -> {
                try {
                    if (enchantment != null) {
                        textField.setValue(String.valueOf(enchantment.getMaxLevel()));
                    } else {
                        textField.setValue("255");
                    }
                } catch (Throwable e) {
                    textField.setValue("255");
                }
            }));
            buttonList.add(new RemoveElementButton(parent, 200, 0, 20, 20, this));
        }

        @Override
        public void draw(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            GuiUtils.drawRelative(graphics, textField, offsetX, offsetY, mouseX, mouseY, partialTicks);
            GuiUtils.drawRightString(graphics, font, enchantment.description().getString() + " : ", offsetX + textField.getX(),
                    offsetY + textField.getY(), (err ? Color.RED : level == 0 ? Color.GRAY : Color.WHITE).getRGB(),
                    textField.getHeight());
            super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }

        public void init() {
            textField.setFocused(false);
        }

        @Override
        public boolean isFocused() {
            return textField.isFocused();
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return textField.charTyped(event) || super.charTyped(event);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            return textField.keyPressed(event) || super.keyPressed(event);
        }

        @Override
        public boolean match(String search) {
            return enchantment.description().getString().toLowerCase().contains(search.toLowerCase());
        }

        @Override
        public void mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mouseX = event.x();
            double mouseY = event.y();
            int mouseButton = event.button();
            if (GuiUtils.isHover(textField, (int) mouseX, (int) mouseY)) {
                textField.setFocused(true);
            } else {
                textField.setFocused(false);
            }
            textField.mouseClicked(event, doubleClick);
            if (mouseButton == 1 && GuiUtils.isHover(textField, (int) mouseX, (int) mouseY))
                textField.setValue("");
            super.mouseClicked(event, doubleClick);
        }

        @Override
        public void update() {
            // textField.tick();
            try {
                level = textField.getValue().isEmpty() ? 0 : Integer.parseInt(textField.getValue());
                err = false;
            } catch (NumberFormatException e) {
                err = true;
            }
            super.update();
        }
    }

    private final List<Tuple<Enchantment, Integer>> originalEnch;

    @SuppressWarnings("unchecked")
    public GuiEnchModifier(Screen parent, List<Tuple<Enchantment, Integer>> ench,
                           Consumer<List<Tuple<Enchantment, Integer>>> setter) {
        super(parent, Component.translatable("gui.ee.modifier.ench"), new ArrayList<>(), setter, null);
        this.originalEnch = new ArrayList<>();
        for (Tuple<Enchantment, Integer> t : ench) {
            this.originalEnch.add(new Tuple<>(t.a, t.b));
        }
        buttons = new Tuple[]{new Tuple<String, Tuple<Runnable, Runnable>>(I18n.get("gui.ee.modifier.ench.max"),
                new Tuple<>(
                        () -> getElements().stream()
                                .filter(le -> le instanceof EnchListElement)
                                .map(le -> (EnchListElement) le)
                                .forEach(ele -> {
                                    try {
                                        if (ele.enchantment != null) {
                                            ele.textField.setValue(String.valueOf(ele.level = ele.enchantment.getMaxLevel()));
                                        } else {
                                            ele.textField.setValue(String.valueOf(ele.level = 255));
                                        }
                                    } catch (Throwable e) {
                                        ele.textField.setValue(String.valueOf(ele.level = 255));
                                    }
                                }),
                        () -> getElements().stream()
                                .filter(le -> le instanceof EnchListElement)
                                .map(le -> (EnchListElement) le)
                                .forEach(ele -> {
                            ele.textField.setValue("");
                            ele.level = 0;
                        })))};
        ench.forEach(e -> addListElement(new EnchListElement(this, e.a, e.b)));
        addListElement(new AddElementList(this, () -> {
            openEnchantmentSelector();
            return null;
        }));
    }

    private void openEnchantmentSelector() {
        Registry<Enchantment> registry = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Tuple<String, Enchantment>> list = new ArrayList<>();
        registry.entrySet().forEach(entry -> {
            Enchantment e = entry.getValue();
            String name = e.description().getString();
            list.add(new Tuple<>(name, e));
        });
        list.sort((a, b) -> a.a.compareToIgnoreCase(b.a));

        getMinecraft().setScreen(new GuiButtonListSelector<>(this, Component.translatable("gui.ee.modifier.ench"), list, e -> {
            List<Tuple<Enchantment, Integer>> current = get();
            current.add(new Tuple<>(e, 1));
            return new GuiEnchModifier(parent, current, setter);
        }));
    }

    @Override
    public boolean isModified() {
        List<Tuple<Enchantment, Integer>> current = get();
        if (current.size() != originalEnch.size()) return true;
        for (int i = 0; i < current.size(); i++) {
            Tuple<Enchantment, Integer> c = current.get(i);
            Tuple<Enchantment, Integer> o = originalEnch.get(i);
            if (!java.util.Objects.equals(c.a, o.a) || !java.util.Objects.equals(c.b, o.b)) return true;
        }
        return false;
    }

    @Override
    protected List<Tuple<Enchantment, Integer>> get() {
        List<Tuple<Enchantment, Integer>> list = new ArrayList<>();
        getElements().stream()
                .filter(le -> le instanceof EnchListElement)
                .map(le -> (EnchListElement) le)
                .forEach(ele -> {
            list.add(new Tuple<>(ele.enchantment, ele.level));
        });
        return list;
    }

}
