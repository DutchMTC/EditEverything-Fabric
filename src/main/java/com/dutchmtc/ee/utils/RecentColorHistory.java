package com.dutchmtc.ee.utils;

import com.dutchmtc.ee.EEMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Custom solid colors and text gradients confirmed in the shared picker, newest first. */
public final class RecentColorHistory {
    public static final int LIMIT = 15;

    private final Path path;
    private final List<TextColorPreset> colors = new ArrayList<>();
    private boolean loaded;

    public RecentColorHistory(Path path) {
        this.path = path;
    }

    public static RecentColorHistory shared() {
        return Shared.INSTANCE;
    }

    private static final class Shared {
        private static final RecentColorHistory INSTANCE = new RecentColorHistory(
                FabricLoader.getInstance().getConfigDir().resolve(EEMod.MOD_ID + "-recent-colors.txt"));
    }

    public synchronized List<Integer> colors() {
        load();
        return colors.stream().filter(value -> !value.gradient()).map(TextColorPreset::start).toList();
    }

    public synchronized List<TextColorPreset> presets(boolean allowGradients) {
        load();
        return colors.stream().filter(value -> allowGradients || !value.gradient()).toList();
    }

    public synchronized void record(int color) {
        record(TextColorPreset.solid(color));
    }

    public synchronized void record(TextColorPreset preset) {
        load();
        if (!preset.gradient() && isVanillaColor(preset.start())) return;
        if (!colors.isEmpty() && colors.getFirst().equals(preset)) return;
        colors.remove(preset);
        colors.addFirst(preset);
        if (colors.size() > LIMIT) colors.removeLast();
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, colors.stream()
                    .map(TextColorPreset::encode)
                    .toList(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            EEMod.LOGGER.warn("Could not save recent colors", e);
        }
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(path)) return;
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String hex = line.trim();
                TextColorPreset preset = TextColorPreset.parse(hex);
                if (preset == null || !preset.gradient() && isVanillaColor(preset.start())) continue;
                if (!colors.contains(preset)) colors.add(preset);
                if (colors.size() == LIMIT) break;
            }
        } catch (IOException e) {
            EEMod.LOGGER.warn("Could not load recent colors", e);
        }
    }

    private static boolean isVanillaColor(int rgb) {
        for (ChatFormatting formatting : ChatFormatting.values()) {
            Integer textColor = ChatUtils.colorRgb(formatting);
            if (textColor != null && textColor == rgb) return true;
        }
        for (DyeColor dye : DyeColor.values()) {
            if ((dye.getFireworkColor() & 0xFFFFFF) == rgb || (dye.getTextColor() & 0xFFFFFF) == rgb) return true;
        }
        return false;
    }
}
