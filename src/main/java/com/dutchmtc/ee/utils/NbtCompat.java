package com.dutchmtc.ee.utils;

import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

public final class NbtCompat {
    private static volatile Field compoundTagMapField;
    private static volatile boolean compoundTagMapFieldSearched;

    private NbtCompat() {
    }

    public static void remove(CompoundTag tag, String key) {
        if (tag == null || key == null) {
            return;
        }
        if (invokeRemove(tag, key)) {
            return;
        }
        removeViaBackingMap(tag, key);
    }

    private static boolean invokeRemove(CompoundTag tag, String key) {
        // Keep the intermediary fallback for jars migrated from pre-26.1 mappings.
        for (String name : new String[]{"remove", "method_10551"}) {
            try {
                Method method = tag.getClass().getMethod(name, String.class);
                method.invoke(tag, key);
                return true;
            } catch (Throwable ignored) {
            }
            try {
                Method method = tag.getClass().getDeclaredMethod(name, String.class);
                method.setAccessible(true);
                method.invoke(tag, key);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void removeViaBackingMap(CompoundTag tag, String key) {
        Field field = compoundTagMapField;
        if (!compoundTagMapFieldSearched) {
            compoundTagMapFieldSearched = true;
            field = findCompoundTagMapField(tag.getClass());
            compoundTagMapField = field;
        }

        if (field == null) {
            return;
        }

        try {
            Object value = field.get(tag);
            if (value instanceof Map<?, ?> map) {
                ((Map<Object, Object>) map).remove(key);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Field findCompoundTagMapField(Class<?> compoundTagClass) {
        try {
            for (Field field : compoundTagClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
