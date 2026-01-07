package com.dutchmtc.ee.utils;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionCompat {
    private static volatile Boolean is12111OrNewer;
    private static final Pattern MC_VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private VersionCompat() {
    }

    public static boolean is12111OrNewer() {
        Boolean cached = is12111OrNewer;
        if (cached != null) {
            return cached;
        }

        // Prefer explicit Minecraft version detection (works reliably across patch versions).
        Boolean result = isMinecraftAtLeast(1, 21, 11);
        if (result == null) {
            // Fallback: signature-based reflection probe (avoid name-only checks; patch versions sometimes reuse names).
            result = hasMethod("net.minecraft.commands.CommandSourceStack", "permissions", 0,
                    "net.minecraft.commands.PermissionSet");
        }
        if (result == null) {
            result = false;
        }
        is12111OrNewer = result;
        return result;
    }

    private static Boolean isMinecraftAtLeast(int major, int minor, int patch) {
        try {
            Optional<String> versionOpt = FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString());
            if (versionOpt.isEmpty()) {
                return null;
            }
            MinecraftVersionParts parts = MinecraftVersionParts.parse(versionOpt.get());
            if (parts == null) {
                return null;
            }
            return parts.isAtLeast(major, minor, patch);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Boolean hasMethod(String className, String methodName, int paramCount, String returnTypeName) {
        try {
            Class<?> cls = Class.forName(className);
            for (var method : cls.getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                if (method.getParameterCount() != paramCount) {
                    continue;
                }
                if (returnTypeName != null && !method.getReturnType().getName().equals(returnTypeName)) {
                    continue;
                }
                return true;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private record MinecraftVersionParts(int major, int minor, int patch) {
        static MinecraftVersionParts parse(String raw) {
            if (raw == null) {
                return null;
            }
            Matcher m = MC_VERSION_PATTERN.matcher(raw);
            if (!m.find()) {
                return null;
            }
            try {
                int major = Integer.parseInt(m.group(1));
                int minor = Integer.parseInt(m.group(2));
                int patch = Integer.parseInt(m.group(3));
                return new MinecraftVersionParts(major, minor, patch);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        boolean isAtLeast(int major, int minor, int patch) {
            if (this.major != major) {
                return this.major > major;
            }
            if (this.minor != minor) {
                return this.minor > minor;
            }
            return this.patch >= patch;
        }
    }
}
