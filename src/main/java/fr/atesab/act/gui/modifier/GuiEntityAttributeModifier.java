package fr.atesab.act.gui.modifier;

import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.gui.selector.GuiButtonListSelector;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import fr.atesab.act.utils.Tuple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiEntityAttributeModifier extends GuiListModifier<List<CompoundTag>> {

    static class EntityAttributeListElement extends ListElement {
        private final EditBox base;
        private boolean errBase = false;
        private double baseValue;
        private CompoundTag data;
        private Attribute attribute;
        private final Button nameButton;

        public EntityAttributeListElement(GuiEntityAttributeModifier parent, CompoundTag data) {
            super(400, 50);
            this.data = data.copy(); // Work on a copy
            
            // Load attribute from NBT
            String name = ItemUtils.getString(this.data, "id");
            if (name.isEmpty()) {
                name = ItemUtils.getString(this.data, "Name");
            }
            var id = Identifier.tryParse(name);
            this.attribute = id != null ? BuiltInRegistries.ATTRIBUTE.get(id).map(Holder.Reference::value).orElse(null) : null;
            if (this.attribute == null) {
                this.attribute = Attributes.MAX_HEALTH.value(); // Default
            }
            
            // Load Base value
            if (ItemUtils.hasTag(this.data, "base", 99)) { // 99 = Any Number
                this.baseValue = ItemUtils.getDouble(this.data, "base");
            } else if (ItemUtils.hasTag(this.data, "Base", 99)) {
                this.baseValue = ItemUtils.getDouble(this.data, "Base");
            } else {
                this.baseValue = this.attribute.getDefaultValue();
            }

            int l = 5 + font.width(I18n.get("gui.act.modifier.attr.amount") + " : "); // Reusing translation key for "Amount" as "Base" is similar
            base = new EditBox(font, 202 + l, 1, 154 - l, 18, Component.literal(""));
            base.setMaxLength(16);
            base.setValue(String.valueOf(baseValue));

            buttonList.add(nameButton = new ACTButton(2, 0, 198, 20, Component.literal(""), b -> {
                List<Tuple<String, Attribute>> attributes = new ArrayList<>();
                BuiltInRegistries.ATTRIBUTE.forEach(atr -> {
                    String desc = atr.getDescriptionId();
                    attributes.add(new Tuple<>(I18n.get(desc), atr));
                });
                mc.setScreen(new GuiButtonListSelector<>(parent,
                        Component.translatable("gui.act.modifier.attr.type"), attributes, atr -> {
                    this.attribute = atr;
                    // Reset base to default if changed? No, keep user value but maybe warn? 
                    // For now just update attribute.
                    defineButtonText();
                    return parent;
                }));
            }));

            buttonList.add(new RemoveElementButton(parent, 359, 0, 20, 20, this));
            buttonList.add(new AddElementButton(parent, 381, 0, 20, 20, this, parent.supplier));
            buttonList.add(new AddElementButton(parent, 359, 21, 43, 20, Component.translatable("gui.act.give.copy"),
                    this, () -> new EntityAttributeListElement(parent, getData())));
            
            defineButtonText();
        }

        private void defineButtonText() {
            nameButton.setMessage(Component.translatable("gui.act.modifier.attr.type").append(" - ")
                    .append(Component.translatable(attribute.getDescriptionId())));
        }

        @Override
        public void draw(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            GuiUtils.drawRelative(graphics, base, offsetX, offsetY, mouseX, mouseY, partialTicks);
            // Using "Base :" label
            GuiUtils.drawRightString(graphics, font, "Base : ", base,
                    (errBase ? Color.RED : Color.WHITE).getRGB(), offsetX, offsetY);
            super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }

        @Override
        public void init() {
            base.setFocused(false);
        }

        @Override
        public boolean isFocused() {
            return base.isFocused();
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return base.charTyped(event);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            base.keyPressed(event);
            return super.keyPressed(event);
        }

        @Override
        public boolean match(String search) {
            return nameButton.getMessage().getString().toLowerCase().contains(search.toLowerCase());
        }

        @Override
        public void mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mouseX = event.x();
            double mouseY = event.y();
            int mouseButton = event.button();
            if (GuiUtils.isHover(base, (int) mouseX, (int) mouseY)) {
                base.setFocused(true);
            }
            base.mouseClicked(event, doubleClick);
            if (mouseButton == 1) {
                if (GuiUtils.isHover(base, (int) mouseX, (int) mouseY))
                    base.setValue("");
            }
            super.mouseClicked(event, doubleClick);
        }

        @Override
        public void update() {
            try {
                baseValue = base.getValue().isEmpty() ? 0 : Double.parseDouble(base.getValue());
                errBase = false;
            } catch (NumberFormatException e) {
                errBase = true;
            }
            super.update();
        }

        public CompoundTag getData() {
            ItemUtils.remove(data, "Name");
            ItemUtils.remove(data, "Base");
            
            ItemUtils.putString(data, "id", BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString());
            ItemUtils.putDouble(data, "base", baseValue);
            
            // Migrate Modifiers if needed
            if (ItemUtils.hasTag(data, "Modifiers", 9)) {
                ListTag mods = ItemUtils.getList(data, "Modifiers", 10);
                ItemUtils.remove(data, "Modifiers");
                data.put("modifiers", mods);
            }
            
            // Preserve Modifiers list if it exists, otherwise we don't touch it
            if (!ItemUtils.hasTag(data, "modifiers", 9)) {
                data.put("modifiers", new ListTag());
            }
            return data;
        }
    }

    private final Supplier<ListElement> supplier = () -> {
        CompoundTag tag = new CompoundTag();
        ItemUtils.putString(tag, "id", BuiltInRegistries.ATTRIBUTE.getKey(Attributes.MAX_HEALTH.value()).toString());
        ItemUtils.putDouble(tag, "base", 20.0);
        return new EntityAttributeListElement(this, tag);
    };

    @SuppressWarnings("unchecked")
    public GuiEntityAttributeModifier(Screen parent, List<CompoundTag> attributes, Consumer<List<CompoundTag>> setter) {
        super(parent, Component.translatable("gui.act.modifier.attr"), new ArrayList<>(), setter, new Tuple[0]);
        attributes.forEach(attribute -> addListElement(new EntityAttributeListElement(this, attribute)));
        addListElement(new AddElementList(this, supplier));
    }

    @Override
    protected List<CompoundTag> get() {
        List<CompoundTag> result = new ArrayList<>();
        getElements().stream().filter(le -> le instanceof EntityAttributeListElement).map(le -> (EntityAttributeListElement) le)
                .forEach(ale -> result.add(ale.getData()));
        return result;
    }

}
