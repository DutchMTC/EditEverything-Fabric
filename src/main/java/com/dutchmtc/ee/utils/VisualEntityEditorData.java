package com.dutchmtc.ee.utils;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Native visual-entity data, copied locally and changed only by explicit editor controls. */
public final class VisualEntityEditorData {
    public final EntityType<?> type;
    public final CompoundTag original, tag;

    public VisualEntityEditorData(EntityType<?> type, CompoundTag source) {
        this.type = type;
        original = source.copy();
        tag = source.copy();
    }

    public boolean mannequin() {
        return type == EntityTypes.MANNEQUIN;
    }

    public boolean textDisplay() {
        return type == EntityTypes.TEXT_DISPLAY;
    }

    public boolean itemDisplay() {
        return type == EntityTypes.ITEM_DISPLAY;
    }

    public static boolean supported(EntityType<?> type) {
        return type == EntityTypes.BLOCK_DISPLAY
                || type == EntityTypes.ITEM_DISPLAY
                || type == EntityTypes.TEXT_DISPLAY
                || type == EntityTypes.MANNEQUIN;
    }

    public boolean changed() {
        return !tag.equals(original);
    }

    public HolderLookup.Provider registry() {
        var level = Minecraft.getInstance().level;
        return level == null ? VanillaRegistries.createWorldLookup() : level.registryAccess();
    }

    public <T> T read(String key, Codec<T> codec, T fallback) {
        Tag value = tag.get(key);
        return value == null
                ? fallback
                : codec.parse(registry().createSerializationContext(NbtOps.INSTANCE), value)
                        .result()
                        .orElse(fallback);
    }

    public <T> void write(String key, Codec<T> codec, T value) {
        tag.put(
                key,
                codec.encodeStart(registry().createSerializationContext(NbtOps.INSTANCE), value)
                        .getOrThrow());
    }

    public Component text(String key, Component fallback) {
        return read(key, ComponentSerialization.CODEC, fallback);
    }

    public void setText(String key, Component value) {
        write(key, ComponentSerialization.CODEC, value);
    }

    public ItemStack item() {
        return read("item", ItemStack.CODEC, ItemStack.EMPTY).copy();
    }

    public void item(ItemStack value) {
        if (value.isEmpty()) tag.remove("item");
        else write("item", ItemStack.CODEC, value.copy());
    }

    public BlockState block() {
        return read("block_state", BlockState.CODEC, Blocks.AIR.defaultBlockState());
    }

    public void block(BlockState value) {
        write("block_state", BlockState.CODEC, value);
    }

    public Transformation transform() {
        return read("transformation", Transformation.EXTENDED_CODEC, Transformation.IDENTITY);
    }

    public void transform(Transformation value) {
        Tag encoded =
                Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        if (encoded instanceof CompoundTag next
                && tag.get("transformation") instanceof CompoundTag previous) {
            var merged = previous.copy();
            next.forEach((key, entry) -> merged.put(key, entry.copy()));
            tag.put("transformation", merged);
        } else tag.put("transformation", encoded);
    }

    public double axis(String group, int axis) {
        var value = transform();
        Vector3fc vector =
                group.equals("Position")
                        ? value.translation()
                        : group.equals("Scale")
                                ? value.scale()
                                : new Quaternionf(
                                                group.equals("Rotation")
                                                        ? value.leftRotation()
                                                        : value.rightRotation())
                                        .getEulerAnglesXYZ(new Vector3f())
                                        .mul(180f / (float) Math.PI);
        return axis == 0 ? vector.x() : axis == 1 ? vector.y() : vector.z();
    }

    public void axis(String group, int axis, double number) {
        if (!Double.isFinite(number) || Math.abs(number) > 30000000)
            throw new IllegalArgumentException("Non-finite transformation");
        var value = transform();
        var translation = new Vector3f(value.translation());
        var scale = new Vector3f(value.scale());
        var left = new Quaternionf(value.leftRotation());
        var right = new Quaternionf(value.rightRotation());
        if (group.equals("Position") || group.equals("Scale")) {
            var vector = group.equals("Position") ? translation : scale;
            vector.setComponent(axis, (float) number);
        } else {
            var angles =
                    (group.equals("Rotation") ? left : right).getEulerAnglesXYZ(new Vector3f());
            angles.setComponent(axis, (float) Math.toRadians(number));
            (group.equals("Rotation") ? left : right).rotationXYZ(angles.x, angles.y, angles.z);
        }
        transform(new Transformation(translation, left, scale, right));
    }

    public CompoundTag profileTag() {
        if (tag.get("profile") instanceof CompoundTag value) return value.copy();
        CompoundTag result = new CompoundTag();
        if (tag.get("profile") instanceof StringTag value) result.putString("name", value.value());
        return result;
    }

    public void profile(ResolvableProfile value) {
        write("profile", ResolvableProfile.CODEC, value);
    }

    public void profileField(String key, String value) {
        CompoundTag profile = profileTag();
        if (value.isBlank()) profile.remove(key);
        else profile.putString(key, value);
        if (profile.isEmpty()) tag.remove("profile");
        else tag.put("profile", profile);
    }

    public int opacity() {
        return tag.getIntOr("text_opacity", -1) & 255;
    }

    public void opacity(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("Opacity");
        tag.putByte("text_opacity", (byte) value);
    }

    public int background() {
        return tag.getIntOr("background", 0x40000000);
    }

    public void backgroundColor(int rgb) {
        tag.putInt("background", (background() & 0xFF000000) | (rgb & 0xFFFFFF));
    }

    public void backgroundOpacity(int alpha) {
        tag.putInt("background", (alpha << 24) | (background() & 0xFFFFFF));
    }
}
