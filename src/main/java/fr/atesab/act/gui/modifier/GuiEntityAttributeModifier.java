package fr.atesab.act.gui.modifier;

import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.gui.selector.GuiButtonListSelector;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.Tuple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
            String name = this.data.getString("id");
            if (name.isEmpty()) {
                name = this.data.getString("Name");
            }
            this.attribute = BuiltInRegistries.ATTRIBUTE.get(ResourceLocation.tryParse(name));
            if (this.attribute == null) {
                this.attribute = Attributes.MAX_HEALTH.value(); // Default
            }
            
            // Load Base value
            if (this.data.contains("base", 99)) { // 99 = Any Number
                this.baseValue = this.data.getDouble("base");
            } else if (this.data.contains("Base", 99)) {
                this.baseValue = this.data.getDouble("Base");
            } else {
                this.baseValue = this.attribute.getDefaultValue();
            }

            int l = 5 + font.width(I18n.get("gui.act.modifier.attr.amount") + " : "); // Reusing translation key for "Amount" as "Base" is similar
            base = new EditBox(font, 202 + l, 1, 154 - l, 18, Component.literal(""));
            base.setMaxLength(16);
            base.setValue(String.valueOf(baseValue));

            buttonList.add(nameButton = new ACTButton(2, 0, 198, 20, Component.literal(""), b -> {
                List<Tuple<String, Attribute>> attributes = new ArrayList<>();
                BuiltInRegistries.ATTRIBUTE.forEach(
                        atr -> attributes.add(new Tuple<>(I18n.get(atr.getDescriptionId()), atr)));
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
        public boolean charTyped(char key, int modifiers) {
            return base.charTyped(key, modifiers);
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            base.keyPressed(key, scanCode, modifiers);
            return super.keyPressed(key, scanCode, modifiers);
        }

        @Override
        public boolean match(String search) {
            return nameButton.getMessage().getString().toLowerCase().contains(search.toLowerCase());
        }

        @Override
        public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
            if (GuiUtils.isHover(base, (int) mouseX, (int) mouseY)) {
                base.setFocused(true);
            }
            base.mouseClicked(mouseX, mouseY, mouseButton);
            if (mouseButton == 1) {
                if (GuiUtils.isHover(base, (int) mouseX, (int) mouseY))
                    base.setValue("");
            }
            super.mouseClicked(mouseX, mouseY, mouseButton);
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
            data.remove("Name");
            data.remove("Base");
            
            data.putString("id", BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString());
            data.putDouble("base", baseValue);
            
            // Migrate Modifiers if needed
            if (data.contains("Modifiers", 9)) {
                ListTag mods = data.getList("Modifiers", 10);
                data.remove("Modifiers");
                data.put("modifiers", mods);
            }
            
            // Preserve Modifiers list if it exists, otherwise we don't touch it
            if (!data.contains("modifiers", 9)) {
                data.put("modifiers", new ListTag());
            }
            return data;
        }
    }

    private final Supplier<ListElement> supplier = () -> {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.ATTRIBUTE.getKey(Attributes.MAX_HEALTH.value()).toString());
        tag.putDouble("base", 20.0);
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
