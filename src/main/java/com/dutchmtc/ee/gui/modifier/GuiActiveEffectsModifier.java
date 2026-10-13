package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.utils.EffectEditorData;
import com.dutchmtc.ee.utils.EffectListData;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;

/** Entity status effects retain their original tags, including hidden and unknown fields. */
public class GuiActiveEffectsModifier extends GuiEffectsEditor<List<CompoundTag>> {
    private final List<CompoundTag> original;

    public GuiActiveEffectsModifier(
            Screen parent, List<CompoundTag> effects, Consumer<List<CompoundTag>> setter) {
        super(parent, Component.literal("Entity effects"), setter, new EffectListData());
        effects.forEach(
                tag ->
                        data.effects.add(
                                new EffectListData.Entry(new EffectEditorData(tag), false)));
        original = result();
    }

    @Override
    protected boolean showStatusIconToggle() {
        return false;
    }

    @Override
    protected List<CompoundTag> result() {
        return data.effects.stream().map(e -> e.effect.tag()).toList();
    }

    @Override
    public boolean isModified() {
        return data.effects.stream().anyMatch(EffectListData.Entry::rawChanged)
                || !result().equals(original);
    }
}
