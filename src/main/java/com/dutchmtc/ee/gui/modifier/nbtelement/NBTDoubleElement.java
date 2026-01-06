package com.dutchmtc.ee.gui.modifier.nbtelement;

import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.Tag;

public class NBTDoubleElement extends NBTNumericElement<Double> {

    public NBTDoubleElement(GuiListModifier<?> parent, String key, Double value) {
        this(parent, key, 200, 21, value);
    }

    public NBTDoubleElement(GuiListModifier<?> parent, String key, int sizeX, int sizeY, Double value) {
        super("double", parent, key, sizeX, sizeY, value);
    }

    @Override
    public Tag get(Double value) {
        return DoubleTag.valueOf(value);
    }

    @Override
    public Double parseValue(String text) {
        return text.isEmpty() ? 0 : Double.parseDouble(text);
    }

    @Override
    public void setNull() {
        setValue(0.0);
    }

}