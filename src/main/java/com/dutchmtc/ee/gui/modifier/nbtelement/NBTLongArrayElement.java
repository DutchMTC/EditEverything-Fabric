package com.dutchmtc.ee.gui.modifier.nbtelement;

import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

public class NBTLongArrayElement extends NBTElement {
    private final LongArrayTag value;

    public NBTLongArrayElement(GuiListModifier<?> parent, String key, LongArrayTag value) {
        super(parent, key, 200, 21);
        this.value = value;
    }

    @Override
    public NBTElement clone() {
        return new NBTLongArrayElement(parent, key, value.copy());
    }

    @Override
    public Tag get() {
        return value.copy();
    }

    @Override
    public String getType() {
        return I18n.get("gui.ee.modifier.tag.editor.longArray");
    }

}