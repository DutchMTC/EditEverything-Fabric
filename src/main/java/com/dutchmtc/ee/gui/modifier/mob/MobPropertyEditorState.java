package com.dutchmtc.ee.gui.modifier.mob;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashSet;
import java.util.Set;

public final class MobPropertyEditorState {
    public final String entityPath;
    public final CompoundTag originalTag;
    public final CompoundTag tag;
    public final Set<String> enabledFeatureSets;

    public MobPropertyEditorState(String entityPath, CompoundTag originalTag, CompoundTag tag, Set<String> enabledFeatureSets) {
        this.entityPath = entityPath;
        this.originalTag = originalTag;
        this.tag = tag;
        this.enabledFeatureSets = enabledFeatureSets;
    }

    public static MobPropertyEditorState createDefault(String entityPath, CompoundTag tag, Set<String> defaultFeatureSets) {
        return new MobPropertyEditorState(entityPath, tag.copy(), tag, new LinkedHashSet<>(defaultFeatureSets));
    }
}
