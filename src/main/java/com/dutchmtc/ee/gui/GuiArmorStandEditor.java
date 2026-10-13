package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.network.EEClientNetworking;
import com.dutchmtc.ee.utils.ArmorStandEditorUtils;
import com.dutchmtc.ee.utils.ArmorStandItemUtils;
import com.dutchmtc.ee.utils.ItemUtilsClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueOutput;

@Environment(EnvType.CLIENT)
public class GuiArmorStandEditor extends GuiArmorStandEditorScreen {
    private final int targetEntityId;
    private final boolean showGetAsItemButton;

    public GuiArmorStandEditor(Screen parent, int entityId) { this(parent, entityId, false); }

    public GuiArmorStandEditor(Screen parent, int entityId, boolean showGetAsItemButton) {
        super(parent);
        this.targetEntityId = entityId;
        this.showGetAsItemButton = showGetAsItemButton;
    }

    @Override protected boolean initializeData() {
        if (!(minecraft.level.getEntity(targetEntityId) instanceof ArmorStand target)) return false;
        if (originalData == null) originalData = ArmorStandEditorUtils.writeState(target, minecraft.level.registryAccess());
        if (currentData == null) currentData = originalData.copy();
        loadData(currentData);
        return true;
    }

    @Override protected void loadData(CompoundTag data) {
        preview = new ArmorStand(minecraft.level, 0, 0, 0);
        ArmorStandEditorUtils.applyState(preview, data, minecraft.level.registryAccess());
    }

    @Override protected CompoundTag captureData() { return ArmorStandEditorUtils.writeState(preview, minecraft.level.registryAccess()); }
    @Override protected void saveData() { EEClientNetworking.sendApplyArmorStandEdits(targetEntityId, currentData); }
    @Override protected boolean canGetItem() { return showGetAsItemButton && minecraft.player != null && minecraft.player.getAbilities().instabuild; }

    @Override protected void getItem() {
        if (!canGetItem()) return;
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, minecraft.level.registryAccess());
        preview.saveWithoutId(out);
        ItemStack item = new ItemStack(Items.ARMOR_STAND);
        ArmorStandItemUtils.setArmorStandEntityTag(item, out.buildResult());
        ItemUtilsClient.give(item);
    }
}
