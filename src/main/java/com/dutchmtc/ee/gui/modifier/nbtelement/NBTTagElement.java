package com.dutchmtc.ee.gui.modifier.nbtelement;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTModifier;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public class NBTTagElement extends NBTElement {
    private CompoundTag value;

    public NBTTagElement(GuiListModifier<?> parent, String key, CompoundTag value) {
        super(parent, key, 200, 21);
        this.value = value;
        buttonList.add(new EEButton(0, 0, 200, 20, Component.translatable("gui.ee.modifier.tag.editor.tag"), b -> mc.setScreen(new GuiNBTModifier(Component.literal(parent.getStringTitle() + key + "/"), parent,
                tag -> NBTTagElement.this.value = tag, value.copy()))));
    }

    @Override
    public NBTElement clone() {
        return new NBTTagElement(parent, key, value.copy());
    }

    @Override
    public Tag get() {
        return value.copy();
    }

    @Override
    public String getType() {
        return I18n.get("gui.ee.modifier.tag.editor.tag") + "[" + value.size() + "]";
    }

}