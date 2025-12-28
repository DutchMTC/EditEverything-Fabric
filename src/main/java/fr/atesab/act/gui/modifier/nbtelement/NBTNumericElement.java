package fr.atesab.act.gui.modifier.nbtelement;

import fr.atesab.act.gui.modifier.GuiListModifier;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public abstract class NBTNumericElement<T extends Number> extends NBTElement {
    private T value;
    private final EditBox field;
    private final String type;

    public NBTNumericElement(String type, GuiListModifier<?> parent, String key, int sizeX, int sizeY, T value) {
        super(parent, key, Math.max(sizeX, 200), Math.max(sizeY, 21));
        this.value = value;
        this.type = type;
        fieldList.add(field = new EditBox(font, 2, 2, 196, 16, Component.literal("")));
        field.setValue(String.valueOf(value));
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
                NBTNumericElement.this.setNull();
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
        return I18n.get("gui.act.modifier.tag.editor." + type);
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

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (super.charTyped(event)) {
            try {
                if (field.getValue().isEmpty())
                    setNull();
                else
                    value = parseValue(field.getValue());
                field.setTextColor(0xffffffff);
            } catch (NumberFormatException e) {
                field.setTextColor(0xffff0000);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            try {
                if (field.getValue().isEmpty())
                    setNull();
                else
                    value = parseValue(field.getValue());
                field.setTextColor(0xffffffff);
            } catch (NumberFormatException e) {
                field.setTextColor(0xffff0000);
            }
            return true;
        }
        return false;
    }

    public abstract void setNull();

    public abstract T parseValue(String text) throws NumberFormatException;

    public void updateValue(T value) {
        this.value = value;
        field.setValue("" + value);
        field.setTextColor(0xffffffff);
    }

}
