package com.dutchmtc.ee.mobdata;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;

import java.util.UUID;

/** Native value formats shared by the friendly entity-property editors. */
public final class MobPropertyValues {
    private MobPropertyValues() {}

    public static UUID uuid(Tag tag) {
        if (tag instanceof IntArrayTag array && array.getAsIntArray().length == 4)
            return UUIDUtil.uuidFromIntArray(array.getAsIntArray());
        if (tag instanceof StringTag text) {
            try {
                return UUID.fromString(text.value());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public static Tag uuid(UUID value) {
        return new IntArrayTag(UUIDUtil.uuidToIntArray(value));
    }

    public static UUID parseUUID(String input) {
        UUID value = UUID.fromString(input.trim());
        if (!value.toString().equalsIgnoreCase(input.trim()))
            throw new IllegalArgumentException("Incomplete UUID");
        return value;
    }

    public static int[] position(Tag tag) {
        if (tag instanceof IntArrayTag array && array.getAsIntArray().length == 3)
            return array.getAsIntArray().clone();
        if (tag instanceof ListTag list
                && list.size() == 3
                && list.stream().allMatch(NumericTag.class::isInstance))
            return list.stream().mapToInt(t -> ((NumericTag) t).box().intValue()).toArray();
        if (tag instanceof CompoundTag compound)
            return new int[] {
                compound.getInt("X").orElse(compound.getInt("x").orElse(0)),
                compound.getInt("Y").orElse(compound.getInt("y").orElse(0)),
                compound.getInt("Z").orElse(compound.getInt("z").orElse(0))
            };
        return new int[] {0, 0, 0};
    }

    public static Tag position(int x, int y, int z) {
        return new IntArrayTag(new int[] {x, y, z});
    }

    public static Tag number(String input, String type, Double minimum, Double maximum) {
        Tag result =
                switch (type) {
                    case "byte" -> ByteTag.valueOf(Byte.parseByte(input));
                    case "short" -> ShortTag.valueOf(Short.parseShort(input));
                    case "long" -> LongTag.valueOf(Long.parseLong(input));
                    case "float" -> FloatTag.valueOf(Float.parseFloat(input));
                    case "double" -> DoubleTag.valueOf(Double.parseDouble(input));
                    default -> IntTag.valueOf(Integer.parseInt(input));
                };
        double value = ((NumericTag) result).box().doubleValue();
        if (!Double.isFinite(value)
                || minimum != null && value < minimum
                || maximum != null && value > maximum)
            throw new IllegalArgumentException("Out of range");
        return result;
    }

    public static void set(CompoundTag root, String path, Tag value) {
        String[] parts = NbtPath.split(path);
        if (value == null) {
            CompoundTag parent = NbtPath.getParentExisting(root, parts);
            if (parent != null) parent.remove(parts[parts.length - 1]);
        } else NbtPath.getOrCreateParent(root, parts).put(parts[parts.length - 1], value.copy());
    }
}
