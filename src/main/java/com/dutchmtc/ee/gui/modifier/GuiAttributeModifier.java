package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ItemUtils.AttributeData;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiAttributeModifier extends GuiListModifier<List<AttributeData>> {

    static class AttributeListElement extends ListElement {
        private final EditBox amount;
        private boolean errAmount = false;
        private double amountValue;
        private final AttributeData data;
        private int operationValue;
        private final Button slotButton;
        private final Button typeButton;
        private final Button operationButton;

        public AttributeListElement(GuiAttributeModifier parent, AttributeData data) {
            super(400, 50);
            this.data = data;
            int l = 5 + font.width(I18n.get("gui.ee.modifier.attr.amount") + " : ");
            amount = new EditBox(font, 202 + l, 1, 154 - l, 18, Component.literal(""));
            amount.setMaxLength(8);
            amount.setValue(String.valueOf(amountValue = data.getModifier().amount()));
            operationValue = data.getModifier().operation().id();
            buttonList.add(slotButton = new EEButton(2, 0, 198, 20, Component.literal(""), b -> {
                List<Tuple<String, EquipmentSlot>> slots = new ArrayList<>();
                slots.add(new Tuple<>(I18n.get("gui.ee.none"), null));
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    String s = I18n.get("item.modifiers." + slot.getName());
                    slots.add(new Tuple<>(s.endsWith(":") ? s.substring(0, s.length() - 1) : s, slot));
                }
                mc.gui.setScreen(new GuiButtonListSelector<>(parent,
                        Component.translatable("gui.ee.modifier.attr.slot"), slots, s -> {
                    data.setSlot(s);
                    defineButtonText();
                    return parent;
                }));
            }));
            buttonList.add(typeButton = new EEButton(2, 21, 198, 20, Component.literal(""), b -> {
                List<Tuple<String, Attribute>> attributes = new ArrayList<>();
                BuiltInRegistries.ATTRIBUTE.forEach(atr -> {
                    String desc = atr.getDescriptionId();
                    attributes.add(new Tuple<>(I18n.get(desc), atr));
                });
                mc.gui.setScreen(new GuiButtonListSelector<>(parent,
                        Component.translatable("gui.ee.modifier.attr.type"), attributes, atr -> {
                    data.setAttribute(atr);
                    defineButtonText();
                    return parent;
                }));
            }));
            buttonList.add(operationButton = new EEButton(202, 21, 157, 20, Component.literal(""), b -> {
                List<Tuple<String, Integer>> operations = new ArrayList<>();
                operations.add(new Tuple<>(I18n.get("gui.ee.modifier.attr.operation.0") + " (0)", 0));
                operations.add(new Tuple<>(I18n.get("gui.ee.modifier.attr.operation.1") + " (1)", 1));
                operations.add(new Tuple<>(I18n.get("gui.ee.modifier.attr.operation.2") + " (2)", 2));
                mc.gui.setScreen(new GuiButtonListSelector<>(parent,
                        Component.translatable("gui.ee.modifier.attr.operation"), operations, i -> {
                    AttributeListElement.this.operationValue = i;
                    defineButtonText();
                    return parent;
                }));
            }));
            buttonList.add(new RemoveElementButton(parent, 359, 0, 20, 20, this));
            buttonList.add(new AddElementButton(parent, 381, 0, 20, 20, this, parent.supplier));
            buttonList.add(new AddElementButton(parent, 359, 21, 43, 20, Component.translatable("gui.ee.give.copy"),
                    this, () -> new AttributeListElement(parent, getData())));
            defineButtonText();
        }

        private void defineButtonText() {
            String s = (data.getSlot() == null ? I18n.get("gui.ee.none")
                    : I18n.get("item.modifiers." + data.getSlot().getName()));
            slotButton.setMessage(Component.translatable("gui.ee.modifier.attr.slot").append(" - ")
                    .append((s.endsWith(":") ? s.substring(0, s.length() - 1) : s)));
            
            typeButton.setMessage(Component.translatable("gui.ee.modifier.attr.type").append(" - ")
                    .append(Component.translatable(data.getAttribute().getDescriptionId())));
            
            operationButton.setMessage(Component.translatable("gui.ee.modifier.attr.operation").append(" - ")
                    .append(Component.translatable("gui.ee.modifier.attr.operation." + operationValue)).append(" (")
                    .append(String.valueOf(operationValue)).append(")"));
        }

        @Override
        public void draw(GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            GuiUtils.drawRelative(graphics, amount, offsetX, offsetY, mouseX, mouseY, partialTicks);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.attr.amount") + " : ", amount,
                    (errAmount ? Color.RED : Color.WHITE).getRGB(), offsetX, offsetY);
            super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }

        @Override
        public void init() {
            amount.setFocused(false);
        }

        @Override
        public boolean isFocused() {
            return amount.isFocused();
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return amount.charTyped(event);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            amount.keyPressed(event);
            return super.keyPressed(event);
        }

        @Override
        public boolean match(String search) {
            return slotButton.getMessage().getString().toLowerCase().contains(search.toLowerCase())
                    || typeButton.getMessage().getString().toLowerCase().contains(search.toLowerCase())
                    || operationButton.getMessage().getString().toLowerCase().contains(search.toLowerCase());
        }

        @Override
        public void mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mouseX = event.x();
            double mouseY = event.y();
            int mouseButton = event.button();
            if (GuiUtils.isHover(amount, (int) mouseX, (int) mouseY)) {
                amount.setFocused(true);
            } else {
                amount.setFocused(false);
            }
            amount.mouseClicked(event, doubleClick);
            if (mouseButton == 1) {
                if (GuiUtils.isHover(amount, (int) mouseX, (int) mouseY))
                    amount.setValue("");
            }
            super.mouseClicked(event, doubleClick);
        }

        @Override
        public void update() {
            try {
                amountValue = amount.getValue().isEmpty() ? 0 : Double.parseDouble(amount.getValue());
                errAmount = false;
            } catch (NumberFormatException e) {
                errAmount = true;
            }
            super.update();
        }

        public AttributeData getData() {
            data.setModifier(new AttributeModifier(data.getModifier().id(),
                    amountValue, Operation.BY_ID.apply(operationValue)));
            return data;
        }
    }

    private final Supplier<ListElement> supplier = () -> new AttributeListElement(this,
            ItemUtils.AttributeModifierBuilder.ARMOR.buildData(EquipmentSlot.MAINHAND, 0, Operation.ADD_VALUE));

    private final List<AttributeData> originalAttributes;

    @SuppressWarnings("unchecked")
    public GuiAttributeModifier(Screen parent, List<AttributeData> attributes, Consumer<List<AttributeData>> setter) {
        super(parent, Component.translatable("gui.ee.modifier.attr"), new ArrayList<>(), setter, new Tuple[0]);
        this.originalAttributes = new ArrayList<>();
        for (AttributeData data : attributes) {
            this.originalAttributes.add(new AttributeData(data.getSlot(), data.getModifier(), data.getAttribute()));
        }
        attributes.forEach(attribute -> addListElement(new AttributeListElement(this, attribute)));
        addListElement(new AddElementList(this, supplier));
    }

    @Override
    public boolean isModified() {
        List<AttributeData> current = get();
        if (current.size() != originalAttributes.size()) return true;
        for (int i = 0; i < current.size(); i++) {
            if (!current.get(i).equals(originalAttributes.get(i))) return true;
        }
        return false;
    }

    @Override
    protected List<AttributeData> get() {
        List<AttributeData> result = new ArrayList<>();
        getElements().stream().filter(le -> le instanceof AttributeListElement).map(le -> (AttributeListElement) le)
                .forEach(ale -> result.add(ale.getData()));
        return result;
    }

}
