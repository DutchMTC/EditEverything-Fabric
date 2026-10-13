package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.utils.ItemUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class GuiEntityAttributeModifier extends GuiAttributesEditor<List<CompoundTag>, GuiEntityAttributeModifier.Row> {
    static final class Row {
        final CompoundTag original;
        Attribute attribute;
        String id, value;
        final String initialId, initialValue;
        Row(CompoundTag tag) {
            original = tag.copy(); id = ItemUtils.getString(tag, "id");
            if (id.isEmpty()) id = ItemUtils.getString(tag, "Name");
            Identifier key = Identifier.tryParse(id);
            attribute = key == null ? null : BuiltInRegistries.ATTRIBUTE.get(key).map(h -> h.value()).orElse(null);
            double base = ItemUtils.hasTag(tag, "base", 99) ? ItemUtils.getDouble(tag, "base")
                    : ItemUtils.hasTag(tag, "Base", 99) ? ItemUtils.getDouble(tag, "Base") : attribute == null ? 0 : attribute.getDefaultValue();
            value = GuiAttributeModifier.display(base, Operation.ADD_VALUE);
            initialId = id; initialValue = value;
        }
        boolean changed() { return !id.equals(initialId) || !value.equals(initialValue); }
        CompoundTag output() {
            if (!changed() && (attribute == null || !original.contains("Name") && !original.contains("Base") && !original.contains("Modifiers")))
                return original.copy();
            CompoundTag result = original.copy();
            result.remove("Name"); result.remove("Base");
            ItemUtils.putString(result, "id", id);
            Double amount = GuiAttributeModifier.parse(value, Operation.ADD_VALUE);
            if (amount == null) throw new IllegalStateException("Invalid attribute base");
            ItemUtils.putDouble(result, "base", amount);
            if (!result.contains("modifiers") && result.contains("Modifiers")) {
                result.put("modifiers", result.get("Modifiers").copy()); result.remove("Modifiers");
            }
            return result;
        }
    }
    private final List<CompoundTag> original;
    public GuiEntityAttributeModifier(Screen parent, List<CompoundTag> attributes, Consumer<List<CompoundTag>> setter) {
        super(parent, setter); original = attributes.stream().map(CompoundTag::copy).toList();
        attributes.forEach(tag -> rows.add(new Row(tag))); if (!rows.isEmpty()) selected = 0;
    }
    @Override protected Attribute attribute(Row row) { return row.attribute; }
    @Override protected String cannotChoose(Attribute attribute, boolean adding) {
        for (Row row : rows) if (row.attribute == attribute && (adding || row != current()))
            return attributeName(attribute) + " is already in the list. Edit its existing base value.";
        return null;
    }
    @Override protected String name(Row row) { return row.attribute == null ? row.id : super.name(row); }
    @Override protected String summary(Row row) { return "Base: " + row.value; }
    @Override protected Row create(Attribute attribute) {
        CompoundTag tag = new CompoundTag(); ItemUtils.putString(tag, "id", BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString());
        ItemUtils.putDouble(tag, "base", attribute.getDefaultValue()); return new Row(tag);
    }
    @Override protected void changeAttribute(Row row, Attribute attribute) {
        row.attribute = attribute; row.id = BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString();
    }
    @Override protected void buildDetails(Row row, int x, int y, int w) {
        valueField = field(x + 6, y + 56, w - 106, 18, "Base value", row.value);
        valueField.setMaxLength(64); valueField.setResponder(s -> { row.value = s; refreshApply(); });
        valueField.setTooltip(Tooltip.create(Component.literal("The mob's starting value, before equipment and other modifiers.")));
        EEButton defaultButton = addRenderableWidget(new EEButton(x + w - 88, y + 55, 88, 20,
                Component.literal("Default"), b -> valueField.setValue(GuiAttributeModifier.display(row.attribute.getDefaultValue(), Operation.ADD_VALUE))));
        defaultButton.active = row.attribute != null;
        defaultButton.setTooltip(Tooltip.create(Component.literal("Use Minecraft's default base value for this attribute.")));
    }
    @Override protected String invalidReason(Row row) {
        if (row.attribute == null && !row.changed()) return null;
        Double amount = GuiAttributeModifier.parse(row.value, Operation.ADD_VALUE);
        if (amount == null) return "Enter a finite base value for " + name(row) + ".";
        if (row.attribute instanceof RangedAttribute range && (amount < range.getMinValue() || amount > range.getMaxValue()))
            return name(row) + " must be between " + range.getMinValue() + " and " + range.getMaxValue() + ".";
        for (Row other : rows) if (other != row && Objects.equals(row.id, other.id)) return "Each mob attribute can have only one base value. Remove the duplicate " + name(row) + ".";
        return null;
    }
    @Override protected void renderDetails(GuiGraphicsExtractor g, Row row, int x, int y, int w) {
        label(g, "Base value", x, y + 42); inputSurface(g, valueField, invalidReason(row) == null);
        String help = row.attribute == null ? "Unknown attribute. Its data is preserved."
                : "Minecraft default: " + GuiAttributeModifier.display(row.attribute.getDefaultValue(), Operation.ADD_VALUE);
        g.text(font, fit(help, w), x, y + 87, EETheme.MUTED, false);
        if (row.attribute instanceof RangedAttribute range) g.text(font,
                fit("Range: " + GuiAttributeModifier.display(range.getMinValue(), Operation.ADD_VALUE) + " to "
                        + GuiAttributeModifier.display(range.getMaxValue(), Operation.ADD_VALUE), w), x, y + 103, EETheme.MUTED, false);
        if (panelHeight >= 270) {
            int yy = y + 132;
            for (var line : font.split(Component.literal("Sets the mob's base value. Existing equipment and attribute modifiers still apply."), w)) {
                g.text(font, line, x, yy, EETheme.MUTED, false); yy += 12;
            }
        }
    }
    @Override public boolean isModified() {
        if (rows.size() != original.size()) return true;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).changed() || !rows.get(i).original.equals(original.get(i))) return true;
        return false;
    }
    @Override protected List<CompoundTag> get() {
        for (Row row : rows) if (invalidReason(row) != null) throw new IllegalStateException(invalidReason(row));
        return rows.stream().map(Row::output).toList();
    }
}
