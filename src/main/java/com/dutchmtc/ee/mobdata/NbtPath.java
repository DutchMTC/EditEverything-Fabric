package com.dutchmtc.ee.mobdata;

import com.dutchmtc.ee.utils.ItemUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

public final class NbtPath {
    private NbtPath() {
    }

    public static String[] split(String path) {
        return path == null ? new String[0] : path.split("\\.");
    }

    public static @Nullable CompoundTag getParentExisting(CompoundTag root, String[] parts) {
        if (parts.length == 0) return null;
        CompoundTag current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String key = parts[i];
            if (!ItemUtils.hasTag(current, key, Tag.TAG_COMPOUND)) {
                return null;
            }
            current = ItemUtils.getCompound(current, key);
        }
        return current;
    }

    public static CompoundTag getOrCreateParent(CompoundTag root, String[] parts) {
        CompoundTag current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String key = parts[i];
            CompoundTag next = ItemUtils.getCompound(current, key);
            if (next.isEmpty() && !ItemUtils.hasTag(current, key, Tag.TAG_COMPOUND)) {
                next = new CompoundTag();
                current.put(key, next);
            }
            current = next;
        }
        return current;
    }

    public static void remove(CompoundTag root, String[] parts) {
        if (parts.length == 0) return;
        CompoundTag parent = getOrCreateParent(root, parts);
        ItemUtils.remove(parent, parts[parts.length - 1]);
    }

    public static @Nullable Tag getTag(CompoundTag root, String[] parts) {
        if (parts.length == 0) return null;
        CompoundTag current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!ItemUtils.hasTag(current, parts[i], Tag.TAG_COMPOUND)) {
                return null;
            }
            current = ItemUtils.getCompound(current, parts[i]);
        }
        return current.get(parts[parts.length - 1]);
    }
}
