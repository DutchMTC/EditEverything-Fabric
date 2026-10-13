package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEDropdownButton;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.utils.ItemUtils.AttributeData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class GuiAttributeModifier extends GuiAttributesEditor<List<AttributeData>, GuiAttributeModifier.Row> {
    static final class Row {
        final AttributeData data;
        String value;
        Row(AttributeData data) { this.data = data.copy(); value = display(data.getModifier().amount(), data.getModifier().operation()); }
        Double amount() {
            AttributeModifier modifier = data.getModifier();
            if (Double.isFinite(modifier.amount()) && value.equals(display(modifier.amount(), modifier.operation())))
                return modifier.amount();
            return parse(value, modifier.operation());
        }
        AttributeData output() {
            AttributeData copy = data.copy();
            Double amount = amount();
            if (amount == null) throw new IllegalStateException("Invalid attribute amount");
            copy.setModifier(new AttributeModifier(data.getModifier().id(), amount, data.getModifier().operation()));
            return copy;
        }
    }
    private static final List<String> OPERATIONS = List.of("Add amount", "Add base %", "Multiply total %");
    private static final String[] HELP = {
            "Adds or subtracts a fixed amount before percentage changes.",
            "Base percentages add together. Applied after fixed additions, before total multipliers.",
            "Multiplies the running total. Multiple total modifiers multiply together."
    };
    private final List<AttributeData> original;

    public GuiAttributeModifier(Screen parent, List<AttributeData> attributes, Consumer<List<AttributeData>> setter) {
        super(parent, setter);
        original = attributes.stream().map(AttributeData::copy).toList();
        attributes.forEach(data -> rows.add(new Row(data)));
        if (!rows.isEmpty()) selected = 0;
    }
    static String display(double value, Operation operation) {
        if (!Double.isFinite(value)) return Double.toString(value);
        BigDecimal number = BigDecimal.valueOf(value);
        if (operation != Operation.ADD_VALUE) number = number.movePointRight(2);
        number = number.stripTrailingZeros();
        return number.precision() - number.scale() <= 24 && number.scale() <= 24 ? number.toPlainString() : number.toString();
    }
    static Double parse(String text, Operation operation) {
        try {
            BigDecimal number = new BigDecimal(text.strip());
            if (operation != Operation.ADD_VALUE) number = number.movePointLeft(2);
            double value = number.doubleValue(); return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException | ArithmeticException ignored) { return null; }
    }
    static String slotName(EquipmentSlotGroup slot) {
        return switch (slot) {
            case ANY -> "Any slot";
            case MAINHAND -> "Main hand";
            case OFFHAND -> "Off hand";
            case HAND -> "Either hand";
            case FEET -> "Feet";
            case LEGS -> "Legs";
            case CHEST -> "Chest";
            case HEAD -> "Head";
            case ARMOR -> "Any armor slot";
            case BODY -> "Body";
            case SADDLE -> "Saddle";
        };
    }
    @Override protected Attribute attribute(Row row) { return row.data.getAttribute(); }
    @Override protected String summary(Row row) {
        Double amount = row.amount();
        return (amount == null ? "Invalid amount" : (amount >= 0 ? "+" : "") + row.value
                + switch (row.data.getModifier().operation()) { case ADD_VALUE -> ""; case ADD_MULTIPLIED_BASE -> "% base";
                    case ADD_MULTIPLIED_TOTAL -> "% total"; }) + " · " + slotName(row.data.getSlotGroup());
    }
    @Override protected Row create(Attribute attribute) {
        return new Row(new AttributeData(EquipmentSlotGroup.MAINHAND,
                new AttributeModifier(Identifier.fromNamespaceAndPath("ee", "attribute/" + UUID.randomUUID()), 0, Operation.ADD_VALUE),
                attribute, ItemAttributeModifiers.Display.attributeModifiers()));
    }
    @Override protected void changeAttribute(Row row, Attribute attribute) { row.data.setAttribute(attribute); }
    @Override protected boolean supportsDuplicate() { return true; }
    @Override protected void duplicateSelected() {
        Row row = current();
        if (row == null || row.amount() == null) return;
        AttributeData copy = row.output();
        copy.setModifier(new AttributeModifier(Identifier.fromNamespaceAndPath("ee", "attribute/" + UUID.randomUUID()),
                copy.getModifier().amount(), copy.getModifier().operation()));
        rows.add(selected + 1, new Row(copy)); selected++;
    }
    private void operation(Row row, int index) {
        Double amount = row.amount();
        if (amount == null) return;
        Operation next = Operation.values()[index];
        row.data.setModifier(new AttributeModifier(row.data.getModifier().id(), amount, next));
        row.value = display(amount, next); init();
    }
    @Override protected void buildDetails(Row row, int x, int y, int w) {
        int amountWidth = Math.min(100, w / 3);
        EEDropdownButton op = addRenderableWidget(new EEDropdownButton(x, y + 55, w - amountWidth - 8, 20,
                Component.literal(OPERATIONS.get(row.data.getModifier().operation().ordinal())), b -> {
                    if (row.amount() != null) openOptions((EEDropdownButton) b, OPERATIONS,
                            row.data.getModifier().operation().ordinal(), i -> operation(row, i));
                }));
        op.setTooltip(Tooltip.create(Component.literal(row.amount() == null ? "Enter a valid amount before changing the operation."
                : HELP[row.data.getModifier().operation().ordinal()])));
        valueField = field(x + w - amountWidth + 6, y + 56, amountWidth - 12, 18, "Amount", row.value);
        valueField.setMaxLength(64);
        valueField.setResponder(value -> { row.value = value; refreshApply(); op.active = row.amount() != null;
            op.setTooltip(Tooltip.create(Component.literal(row.amount() == null ? "Enter a valid amount before changing the operation."
                    : HELP[row.data.getModifier().operation().ordinal()]))); });
        op.active = row.amount() != null;
        valueField.setTooltip(Tooltip.create(Component.literal(row.data.getModifier().operation() == Operation.ADD_VALUE
                ? "A fixed amount. Negative values subtract."
                : "Enter a percentage: 10 means +10%; -10 means -10%. Stored as Minecraft's decimal multiplier.")));
        addRenderableWidget(new EEDropdownButton(x, y + 97, w, 20, Component.literal(slotName(row.data.getSlotGroup())),
                b -> openOptions((EEDropdownButton) b, Arrays.stream(EquipmentSlotGroup.values()).map(GuiAttributeModifier::slotName).toList(),
                        row.data.getSlotGroup().ordinal(), i -> { row.data.setSlotGroup(EquipmentSlotGroup.values()[i]); init(); })))
                .setTooltip(Tooltip.create(Component.literal("The modifier takes effect while the item is held or equipped in this slot.")));
    }
    @Override protected String invalidReason(Row row) {
        return row.amount() == null ? "Enter a finite number for " + name(row) + "." : null;
    }
    @Override protected void renderDetails(GuiGraphicsExtractor g, Row row, int x, int y, int w) {
        label(g, "Operation", x, y + 42);
        label(g, row.data.getModifier().operation() == Operation.ADD_VALUE ? "Amount" : "Percent", x + w - Math.min(100, w / 3), y + 42);
        label(g, "Active in", x, y + 84);
        inputSurface(g, valueField, row.amount() != null);
        Double amount = row.amount();
        String text;
        if (amount == null) text = "Enter a valid number.";
        else {
            double result = row.data.getModifier().operation() == Operation.ADD_VALUE ? 10 + amount : 10 * (1 + amount);
            text = Double.isFinite(result) ? "Example: 10 → " + display(result, Operation.ADD_VALUE) : "Example exceeds the number range.";
        }
        g.text(font, fit(text, w), x, y + 127, amount == null ? 0xFFAA4444 : EETheme.MUTED, false);
        if (panelHeight >= 270) {
            int yy = y + 147;
            for (var line : font.split(Component.literal(HELP[row.data.getModifier().operation().ordinal()]), w)) {
                g.text(font, line, x, yy, EETheme.MUTED, false); yy += 12;
            }
        }
    }
    @Override public boolean isModified() {
        if (rows.size() != original.size()) return true;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).amount() == null || !rows.get(i).output().equals(original.get(i))) return true;
        return false;
    }
    @Override protected List<AttributeData> get() { return rows.stream().map(Row::output).toList(); }
}
