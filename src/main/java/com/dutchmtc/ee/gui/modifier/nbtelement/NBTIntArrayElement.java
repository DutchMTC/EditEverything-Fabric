package com.dutchmtc.ee.gui.modifier.nbtelement;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTIntArrayModifier;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public class NBTIntArrayElement extends NBTElement {
    private IntArrayTag value;

    public NBTIntArrayElement(GuiListModifier<?> parent, String key, IntArrayTag value) {
        super(parent, key, 200, 21);
        this.value = value;
        buttonList
                .add(new EEButton(0, 0, 200, 20, Component.translatable("gui.ee.modifier.tag.editor.intArray"), b -> mc.gui.setScreen(new GuiNBTIntArrayModifier(Component.literal(parent.getStringTitle() + key + "/"),
                        parent, tag -> NBTIntArrayElement.this.value = tag, value.copy()))));
    }

    @Override
    public NBTElement clone() {
        return new NBTIntArrayElement(parent, key, value.copy());
    }

    @Override
    public Tag get() {
        return value.copy();
    }

    @Override
    public String getType() {
        return I18n.get("gui.ee.modifier.tag.editor.intArray") + "[" + value.size() + "]";
    }

}
