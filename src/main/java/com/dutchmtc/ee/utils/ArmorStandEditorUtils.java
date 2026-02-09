package com.dutchmtc.ee.utils;

import com.dutchmtc.ee.mixin.ArmorStandAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Rotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

/**
 * Common (client + server) utilities for reading/writing/applying Armor Stand editor state.
 */
public final class ArmorStandEditorUtils {
    private ArmorStandEditorUtils() {
    }

    public static final String KEY_NAME = "CustomName";
    public static final String KEY_NAME_VISIBLE = "CustomNameVisible";
    public static final String KEY_INVISIBLE = "Invisible";
    public static final String KEY_GLOWING = "Glowing";
    public static final String KEY_NO_GRAVITY = "NoGravity";
    public static final String KEY_INVULNERABLE = "Invulnerable";

    public static final String KEY_SMALL = "Small";
    public static final String KEY_SHOW_ARMS = "ShowArms";
    public static final String KEY_NO_BASE_PLATE = "NoBasePlate";
    public static final String KEY_MARKER = "Marker";

    public static final String KEY_DISABLED_SLOTS = "DisabledSlots";
    public static final String KEY_POSE = "pose";
    public static final String KEY_EQUIPMENT = "equipment";

    public static final String POSE_HEAD = "head";
    public static final String POSE_BODY = "body";
    public static final String POSE_LEFT_ARM = "leftArm";
    public static final String POSE_RIGHT_ARM = "rightArm";
    public static final String POSE_LEFT_LEG = "leftLeg";
    public static final String POSE_RIGHT_LEG = "rightLeg";

    public static final String EQUIP_MAINHAND = "mainhand";
    public static final String EQUIP_OFFHAND = "offhand";
    public static final String EQUIP_HEAD = "head";
    public static final String EQUIP_CHEST = "chest";
    public static final String EQUIP_LEGS = "legs";
    public static final String EQUIP_FEET = "feet";

    public static CompoundTag writeState(ArmorStand stand, HolderLookup.Provider registryAccess) {
        CompoundTag tag = new CompoundTag();

        var name = stand.getCustomName();
        tag.putString(KEY_NAME, name != null ? name.getString() : "");
        tag.putBoolean(KEY_NAME_VISIBLE, stand.isCustomNameVisible());

        tag.putBoolean(KEY_INVISIBLE, stand.isInvisible());
        tag.putBoolean(KEY_GLOWING, stand.hasGlowingTag());
        tag.putBoolean(KEY_NO_GRAVITY, stand.isNoGravity());
        tag.putBoolean(KEY_INVULNERABLE, stand.isInvulnerable());

        tag.putBoolean(KEY_SMALL, stand.isSmall());
        tag.putBoolean(KEY_SHOW_ARMS, stand.showArms());
        tag.putBoolean(KEY_NO_BASE_PLATE, !stand.showBasePlate());
        tag.putBoolean(KEY_MARKER, stand.isMarker());

        tag.putInt(KEY_DISABLED_SLOTS, getDisabledSlots(stand));

        CompoundTag pose = new CompoundTag();
        pose.put(POSE_HEAD, writeRotations(stand.getHeadPose()));
        pose.put(POSE_BODY, writeRotations(stand.getBodyPose()));
        pose.put(POSE_LEFT_ARM, writeRotations(stand.getLeftArmPose()));
        pose.put(POSE_RIGHT_ARM, writeRotations(stand.getRightArmPose()));
        pose.put(POSE_LEFT_LEG, writeRotations(stand.getLeftLegPose()));
        pose.put(POSE_RIGHT_LEG, writeRotations(stand.getRightLegPose()));
        tag.put(KEY_POSE, pose);

        CompoundTag equipment = new CompoundTag();
        putStack(equipment, EQUIP_MAINHAND, stand.getItemBySlot(EquipmentSlot.MAINHAND), registryAccess);
        putStack(equipment, EQUIP_OFFHAND, stand.getItemBySlot(EquipmentSlot.OFFHAND), registryAccess);
        putStack(equipment, EQUIP_HEAD, stand.getItemBySlot(EquipmentSlot.HEAD), registryAccess);
        putStack(equipment, EQUIP_CHEST, stand.getItemBySlot(EquipmentSlot.CHEST), registryAccess);
        putStack(equipment, EQUIP_LEGS, stand.getItemBySlot(EquipmentSlot.LEGS), registryAccess);
        putStack(equipment, EQUIP_FEET, stand.getItemBySlot(EquipmentSlot.FEET), registryAccess);
        tag.put(KEY_EQUIPMENT, equipment);

        return tag;
    }

    public static void applyState(ArmorStand stand, CompoundTag tag, HolderLookup.Provider registryAccess) {
        if (tag == null) {
            return;
        }

        String raw = tag.getStringOr(KEY_NAME, "");
        if (!raw.isBlank()) {
            stand.setCustomName(ChatUtils.parseLegacyFormattingComponent(raw));
        } else {
            stand.setCustomName(null);
        }

        if (tag.getBoolean(KEY_NAME_VISIBLE).isPresent()) {
            stand.setCustomNameVisible(tag.getBooleanOr(KEY_NAME_VISIBLE, false));
        }

        if (tag.getBoolean(KEY_INVISIBLE).isPresent()) {
            stand.setInvisible(tag.getBooleanOr(KEY_INVISIBLE, false));
        }
        if (tag.getBoolean(KEY_GLOWING).isPresent()) {
            stand.setGlowingTag(tag.getBooleanOr(KEY_GLOWING, false));
        }
        if (tag.getBoolean(KEY_NO_GRAVITY).isPresent()) {
            stand.setNoGravity(tag.getBooleanOr(KEY_NO_GRAVITY, false));
        }
        if (tag.getBoolean(KEY_INVULNERABLE).isPresent()) {
            stand.setInvulnerable(tag.getBooleanOr(KEY_INVULNERABLE, false));
        }

        if (tag.getBoolean(KEY_SMALL).isPresent()) {
            setSmall(stand, tag.getBooleanOr(KEY_SMALL, false));
        }
        if (tag.getBoolean(KEY_SHOW_ARMS).isPresent()) {
            stand.setShowArms(tag.getBooleanOr(KEY_SHOW_ARMS, false));
        }
        if (tag.getBoolean(KEY_NO_BASE_PLATE).isPresent()) {
            stand.setNoBasePlate(tag.getBooleanOr(KEY_NO_BASE_PLATE, false));
        }
        if (tag.getBoolean(KEY_MARKER).isPresent()) {
            setMarker(stand, tag.getBooleanOr(KEY_MARKER, false));
        }

        if (tag.getInt(KEY_DISABLED_SLOTS).isPresent()) {
            setDisabledSlots(stand, tag.getIntOr(KEY_DISABLED_SLOTS, 0));
        }

        CompoundTag pose = tag.getCompoundOrEmpty(KEY_POSE);
        if (!pose.isEmpty()) {
            stand.setHeadPose(readRotations(pose, POSE_HEAD, ArmorStand.DEFAULT_HEAD_POSE));
            stand.setBodyPose(readRotations(pose, POSE_BODY, ArmorStand.DEFAULT_BODY_POSE));
            stand.setLeftArmPose(readRotations(pose, POSE_LEFT_ARM, ArmorStand.DEFAULT_LEFT_ARM_POSE));
            stand.setRightArmPose(readRotations(pose, POSE_RIGHT_ARM, ArmorStand.DEFAULT_RIGHT_ARM_POSE));
            stand.setLeftLegPose(readRotations(pose, POSE_LEFT_LEG, ArmorStand.DEFAULT_LEFT_LEG_POSE));
            stand.setRightLegPose(readRotations(pose, POSE_RIGHT_LEG, ArmorStand.DEFAULT_RIGHT_LEG_POSE));
        }

        CompoundTag equipment = tag.getCompoundOrEmpty(KEY_EQUIPMENT);
        if (!equipment.isEmpty()) {
            stand.setItemSlot(EquipmentSlot.MAINHAND, readStack(equipment, EQUIP_MAINHAND, registryAccess));
            stand.setItemSlot(EquipmentSlot.OFFHAND, readStack(equipment, EQUIP_OFFHAND, registryAccess));
            stand.setItemSlot(EquipmentSlot.HEAD, readStack(equipment, EQUIP_HEAD, registryAccess));
            stand.setItemSlot(EquipmentSlot.CHEST, readStack(equipment, EQUIP_CHEST, registryAccess));
            stand.setItemSlot(EquipmentSlot.LEGS, readStack(equipment, EQUIP_LEGS, registryAccess));
            stand.setItemSlot(EquipmentSlot.FEET, readStack(equipment, EQUIP_FEET, registryAccess));
        }
    }

    public static int getDisabledSlots(ArmorStand stand) {
        return ((ArmorStandAccessor) (Object) stand).ee$getDisabledSlots();
    }

    public static void setDisabledSlots(ArmorStand stand, int value) {
        ((ArmorStandAccessor) (Object) stand).ee$setDisabledSlots(value);
    }

    public static void setSmall(ArmorStand stand, boolean value) {
        ((ArmorStandAccessor) (Object) stand).ee$invokeSetSmall(value);
    }

    public static void setMarker(ArmorStand stand, boolean value) {
        ((ArmorStandAccessor) (Object) stand).ee$invokeSetMarker(value);
    }

    private static ListTag writeRotations(Rotations rotations) {
        ListTag list = new ListTag();
        list.add(FloatTag.valueOf(rotations.x()));
        list.add(FloatTag.valueOf(rotations.y()));
        list.add(FloatTag.valueOf(rotations.z()));
        return list;
    }

    private static Rotations readRotations(CompoundTag pose, String key, Rotations fallback) {
        if (pose == null) {
            return fallback;
        }
        ListTag list = pose.getList(key).orElse(null);
        if (list == null) {
            return fallback;
        }
        if (list.size() < 3) {
            return fallback;
        }
        return new Rotations(list.getFloatOr(0, fallback.x()), list.getFloatOr(1, fallback.y()), list.getFloatOr(2, fallback.z()));
    }

    private static void putStack(CompoundTag equipment, String key, ItemStack stack, HolderLookup.Provider registryAccess) {
        if (stack == null || stack.isEmpty()) {
            NbtCompat.remove(equipment, key);
            return;
        }
        Tag saved = ItemUtils.saveStack(stack, registryAccess);
        if (saved instanceof CompoundTag ct) {
            equipment.put(key, ct);
        }
    }

    private static ItemStack readStack(CompoundTag equipment, String key, HolderLookup.Provider registryAccess) {
        if (equipment == null) {
            return ItemStack.EMPTY;
        }
        return equipment.getCompound(key)
                .map(ct -> ItemUtils.parseStack(registryAccess, ct))
                .orElse(ItemStack.EMPTY);
    }
}
