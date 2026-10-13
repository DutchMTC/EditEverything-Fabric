package com.dutchmtc.ee.gui.modifier.nbtelement;

import com.dutchmtc.ee.gui.components.EETextField;
import com.dutchmtc.ee.gui.modifier.GuiListModifier;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public abstract class NBTNumericElement<T extends Number> extends NBTElement {
    private T value;
    private final EditBox field;
    private final String type;

    public NBTNumericElement(
            String type, GuiListModifier<?> parent, String key, int sizeX, int sizeY, T value) {
        super(parent, key, Math.max(sizeX, 200), Math.max(sizeY, 21));
        this.value = value;
        this.type = type;
        fieldList.add(field = new EETextField(font, 2, 2, 196, 16, Component.literal("")));
        field.setValue(String.valueOf(value));
        field.setResponder(text -> readInput());
    }

    public NBTNumericElement(String type, GuiListModifier<?> parent, String key, T value) {
        this(type, parent, key, 200, 21, value);
    }

    @Override
    public NBTElement clone() {
        return new NBTNumericElement<>(type, parent, key, value) {
            @Override
            public Tag get(T value) {
                return NBTNumericElement.this.get(value);
            }

            @Override
            public T parseValue(String text) throws NumberFormatException {
                return NBTNumericElement.this.parseValue(text);
            }

            @Override
            public void setNull() {
                setValue(parseValue("0"));
            }
        };
    }

    @Override
    public Tag get() {
        return get(value);
    }

    public abstract Tag get(T value);

    @Override
    public String getType() {
        return I18n.get("gui.ee.modifier.tag.editor." + type);
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    @Override
    public boolean match(String search) {
        return field.getValue().toLowerCase().contains(search.toLowerCase()) || super.match(search);
    }

    private void readInput() {
        try {
            if (field.getValue().isEmpty()) setNull();
            else value = parseValue(field.getValue());
            EETextField.validate(field, null);
        } catch (NumberFormatException e) {
            EETextField.validate(
                    field, "Enter a valid " + getType() + " number within its supported range.");
        }
    }

    @Override
    public void update() {
        readInput();
        super.update();
    }

    public abstract void setNull();

    public abstract T parseValue(String text) throws NumberFormatException;

    public void updateValue(T value) {
        this.value = value;
        field.setValue("" + value);
        EETextField.validate(field, null);
    }
}
