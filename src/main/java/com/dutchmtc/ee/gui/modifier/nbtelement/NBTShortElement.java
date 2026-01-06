package com.dutchmtc.ee.gui.modifier.nbtelement;

import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;

public class NBTShortElement extends NBTNumericElement<Short> {

    public NBTShortElement(GuiListModifier<?> parent, String key, int sizeX, int sizeY, Short value) {
        super("short", parent, key, sizeX, sizeY, value);
    }

    public NBTShortElement(GuiListModifier<?> parent, String key, Short value) {
        this(parent, key, 200, 21, value);
    }

    @Override
    public Tag get(Short value) {
        return ShortTag.valueOf(value);
    }

    @Override
    public void setNull() {
        setValue((short) 0);
    }

    @Override
    public Short parseValue(String text) {
        return text.isEmpty() ? 0 : Short.parseShort(text);
    }

}