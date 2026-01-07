package com.dutchmtc.ee.utils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PermissionCompat {
    private static final int OP_LEVEL_GAMEMASTER = 2;
    private static volatile Field permissionSetField;
    private static volatile boolean permissionSetFieldSearched;
    private static volatile Method levelCheckMethod;
    private static volatile boolean levelCheckMethodSearched;

    private PermissionCompat() {
    }

    public static boolean hasGamemasterPermissions(CommandSourceStack source) {
        if (source == null) {
            return false;
        }

        PermissionSet permissionSet = getPermissionSet(source);
        if (permissionSet != null) {
            return permissionSet.hasPermission(Permissions.COMMANDS_GAMEMASTER);
        }

        Method checkMethod = getLevelCheckMethod(source);
        if (checkMethod != null) {
            try {
                Object result = checkMethod.invoke(source, OP_LEVEL_GAMEMASTER);
                return result instanceof Boolean b && b;
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private static PermissionSet getPermissionSet(CommandSourceStack source) {
        Field field = permissionSetField;
        if (!permissionSetFieldSearched) {
            permissionSetFieldSearched = true;
            field = findPermissionSetField(source.getClass());
            permissionSetField = field;
        }

        if (field == null) {
            return null;
        }

        try {
            Object value = field.get(source);
            return value instanceof PermissionSet set ? set : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findPermissionSetField(Class<?> sourceClass) {
        try {
            for (Field field : sourceClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!PermissionSet.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method getLevelCheckMethod(CommandSourceStack source) {
        Method method = levelCheckMethod;
        if (!levelCheckMethodSearched) {
            levelCheckMethodSearched = true;
            method = findLevelCheckMethod(source.getClass());
            levelCheckMethod = method;
        }
        return method;
    }

    private static Method findLevelCheckMethod(Class<?> sourceClass) {
        try {
            for (Method method : sourceClass.getMethods()) {
                if (method.getReturnType() != boolean.class) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1 || params[0] != int.class) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
