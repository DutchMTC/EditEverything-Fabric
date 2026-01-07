package com.dutchmtc.ee.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;

public final class ArmorStandItemUtils {
    private ArmorStandItemUtils() {
    }

    /**
     * Entity tags saved from a live entity include runtime-only fields (Pos/Motion/Rotation/etc).
     * Those break placement (e.g. forcing the spawned stand to (0,0,0)).
     * This keeps only data that is meaningful for an Armor Stand item.
     */
    public static CompoundTag sanitizeArmorStandEntityTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return new CompoundTag();
        }
        CompoundTag out = tag.copy();

        // Never persist runtime state / location into an item.
        NbtCompat.remove(out, "Pos");
        NbtCompat.remove(out, "Motion");
        NbtCompat.remove(out, "Rotation");
        NbtCompat.remove(out, "FallDistance");
        NbtCompat.remove(out, "OnGround");
        NbtCompat.remove(out, "Air");
        NbtCompat.remove(out, "Fire");
        NbtCompat.remove(out, "PortalCooldown");
        NbtCompat.remove(out, "UUID");
        NbtCompat.remove(out, "UUIDMost");
        NbtCompat.remove(out, "UUIDLeast");
        NbtCompat.remove(out, "Dimension");

        // Also drop any leftover id, just in case.
        NbtCompat.remove(out, "id");

        return out;
    }

    public static boolean isArmorStandEntityData(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        TypedEntityData<EntityType<?>> data = stack.get(DataComponents.ENTITY_DATA);
        return data != null && data.type() == EntityType.ARMOR_STAND;
    }

    public static CompoundTag getArmorStandEntityTag(ItemStack stack) {
        if (stack == null) {
            return new CompoundTag();
        }
        TypedEntityData<EntityType<?>> data = stack.get(DataComponents.ENTITY_DATA);
        if (data == null || data.type() != EntityType.ARMOR_STAND) {
            return new CompoundTag();
        }
        return sanitizeArmorStandEntityTag(data.copyTagWithoutId());
    }

    public static void setArmorStandEntityTag(ItemStack stack, CompoundTag tag) {
        if (stack == null) {
            return;
        }
        CompoundTag safe = sanitizeArmorStandEntityTag(tag);
        stack.set(DataComponents.ENTITY_DATA, TypedEntityData.of(EntityType.ARMOR_STAND, safe));
    }
}
