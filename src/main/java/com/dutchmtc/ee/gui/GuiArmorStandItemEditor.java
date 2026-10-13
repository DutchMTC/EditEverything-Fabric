package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.utils.ArmorStandItemUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class GuiArmorStandItemEditor extends GuiArmorStandEditorScreen {
    private final ItemStack item;
    private final Consumer<ItemStack> setter;

    public GuiArmorStandItemEditor(Screen parent, ItemStack armorStandStack, Consumer<ItemStack> setter) {
        super(parent);
        this.item = armorStandStack == null ? ItemStack.EMPTY : armorStandStack.copy();
        this.setter = setter;
    }

    @Override protected boolean initializeData() {
        if (originalData == null) originalData = ArmorStandItemUtils.getArmorStandEntityTag(item);
        if (currentData == null) currentData = originalData.copy();
        loadData(currentData);
        return true;
    }

    @Override protected void loadData(CompoundTag data) {
        preview = new ArmorStand(minecraft.level, 0, 0, 0);
        TypedEntityData.of(EntityTypes.ARMOR_STAND, ArmorStandItemUtils.sanitizeArmorStandEntityTag(data)).loadInto(preview);
    }

    @Override protected CompoundTag captureData() {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, minecraft.level.registryAccess());
        preview.saveWithoutId(out);
        return ArmorStandItemUtils.sanitizeArmorStandEntityTag(out.buildResult());
    }

    @Override protected void saveData() {
        ArmorStandItemUtils.setArmorStandEntityTag(item, currentData);
        setter.accept(item.copy());
    }
}
