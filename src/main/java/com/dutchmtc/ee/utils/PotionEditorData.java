package com.dutchmtc.ee.utils;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;

import java.util.*;

/** Potion form and contents are a single draft; unrelated item data remains intact. */
public final class PotionEditorData extends EffectListData {
    public static final List<Item> FORMS =
            List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW);
    public final ItemStack source;
    public Item item;
    public Potion potion;
    public OptionalInt color;
    private final HolderLookup.Provider registries;

    public PotionEditorData(ItemStack source, HolderLookup.Provider registries) {
        this.source = source.copy();
        this.registries = registries;
        item = source.getItem();
        var info = ItemUtils.getPotionInformation(source);
        potion = info.getMain();
        color = info.getCustomColor();
        addBase();
        info.getCustomEffects()
                .forEach(e -> effects.add(new Entry(EffectEditorData.of(e, registries), false)));
    }

    public static boolean supports(ItemStack stack) {
        return FORMS.contains(stack.getItem());
    }

    private void addBase() {
        if (potion != null)
            potion.getEffects()
                    .forEach(e -> effects.add(new Entry(EffectEditorData.of(e, registries), true)));
    }

    public void materialize() {
        potion = null;
        effects.forEach(e -> e.fromPotion = false);
    }

    public void choosePotion(Potion value) {
        if (value == null) {
            materialize();
            return;
        }
        List<Entry> custom = effects.stream().filter(e -> !e.fromPotion).toList();
        effects.clear();
        potion = value;
        addBase();
        effects.addAll(custom);
    }

    public ItemStack result() {
        ItemStack result = ItemUtils.setItem(item, source);
        var custom =
                effects.stream()
                        .filter(e -> !e.fromPotion)
                        .map(e -> e.effect.instance(registries))
                        .toList();
        ItemUtils.setPotionInformation(
                result, new ItemUtils.PotionInformation(color, potion, custom));
        int amount = Math.clamp(source.getCount(), 1, 99);
        if (result.has(DataComponents.MAX_DAMAGE)) {
            amount = 1;
            if (result.getMaxStackSize() > 1) result.set(DataComponents.MAX_STACK_SIZE, 1);
        } else if (amount > result.getMaxStackSize())
            result.set(DataComponents.MAX_STACK_SIZE, amount);
        result.setCount(amount);
        return result;
    }

    public boolean changed() {
        return effects.stream().anyMatch(Entry::rawChanged) || !ItemStack.matches(source, result());
    }

    public PotionContents contents() {
        var existing = source.get(DataComponents.POTION_CONTENTS);
        return new PotionContents(
                potion == null
                        ? Optional.empty()
                        : Optional.of(BuiltInRegistries.POTION.wrapAsHolder(potion)),
                color.isPresent() ? Optional.of(color.getAsInt()) : Optional.empty(),
                effects.stream()
                        .filter(e -> !e.fromPotion && e.effect.invalidReason() == null)
                        .map(e -> e.effect.instance(registries))
                        .toList(),
                existing == null ? Optional.empty() : existing.customName());
    }
}
