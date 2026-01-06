package com.dutchmtc.ee.gui.modifier.nbtelement;

import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.Tag;

public class NBTByteElement extends NBTNumericElement<Byte> {

    public NBTByteElement(GuiListModifier<?> parent, String key, Byte value) {
        this(parent, key, 200, 21, value);
    }

    public NBTByteElement(GuiListModifier<?> parent, String key, int sizeX, int sizeY, Byte value) {
        super("byte", parent, key, sizeX, sizeY, value);
    }

    @Override
    public Tag get(Byte value) {
        return ByteTag.valueOf(value);
    }

    @Override
    public void setNull() {
        setValue((byte) 0);
    }

    @Override
    public Byte parseValue(String text) {
        return text.isEmpty() ? 0 : Byte.parseByte(text);
    }

}