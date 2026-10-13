package com.dutchmtc.ee.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** A lossless effect draft: editing visible settings leaves hidden and custom data intact. */
public final class EffectEditorData {
    private final CompoundTag original;
    private final String originalId;
    private final int originalLevel, originalTicks;
    private final boolean originalAmbient, originalParticles, originalIcon;
    public String id;
    public int level, ticks;
    public boolean ambient, particles, icon;

    public EffectEditorData(CompoundTag source) {
        original = source.copy();
        id =
                source.getString("id")
                        .orElseGet(
                                () -> {
                                    var legacy =
                                            BuiltInRegistries.MOB_EFFECT.byId(
                                                    source.getInt("Id").orElse(-1));
                                    return legacy == null
                                            ? ""
                                            : BuiltInRegistries.MOB_EFFECT
                                                    .getKey(legacy)
                                                    .toString();
                                });
        level = numeric(source, "amplifier", "Amplifier", 0, true) + 1;
        ticks = numeric(source, "duration", "Duration", 0, false);
        ambient = bool(source, "ambient", "Ambient", false);
        particles = bool(source, "show_particles", "ShowParticles", true);
        icon = bool(source, "show_icon", "ShowIcon", particles);
        originalId = id;
        originalLevel = level;
        originalTicks = ticks;
        originalAmbient = ambient;
        originalParticles = particles;
        originalIcon = icon;
    }

    public static EffectEditorData of(MobEffectInstance effect, HolderLookup.Provider registries) {
        return new EffectEditorData(
                (CompoundTag)
                        MobEffectInstance.CODEC
                                .encodeStart(
                                        registries.createSerializationContext(NbtOps.INSTANCE),
                                        effect)
                                .getOrThrow());
    }

    public static EffectEditorData create(Holder<MobEffect> effect) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString());
        tag.putInt("duration", 1200);
        tag.putInt("amplifier", 0);
        return new EffectEditorData(tag);
    }

    private static int numeric(
            CompoundTag tag, String key, String legacy, int fallback, boolean unsigned) {
        Tag value = tag.get(tag.contains(key) ? key : legacy);
        return value instanceof ByteTag b && unsigned
                ? Byte.toUnsignedInt(b.value())
                : value instanceof NumericTag n ? n.intValue() : fallback;
    }

    private static boolean bool(CompoundTag tag, String key, String legacy, boolean fallback) {
        return tag.getBoolean(tag.contains(key) ? key : legacy).orElse(fallback);
    }

    public Holder<MobEffect> effect() {
        Identifier key = Identifier.tryParse(id);
        return key == null
                ? null
                : BuiltInRegistries.MOB_EFFECT
                        .getOptional(key)
                        .map(BuiltInRegistries.MOB_EFFECT::wrapAsHolder)
                        .orElse(null);
    }

    public String name() {
        var effect = effect();
        return effect == null ? "Unknown effect" : effect.value().getDisplayName().getString();
    }

    public String summary() {
        return "Level " + level + " · " + (ticks == -1 ? "Infinite" : seconds(ticks) + " s");
    }

    public static String seconds(int ticks) {
        return java.math.BigDecimal.valueOf(ticks)
                .divide(java.math.BigDecimal.valueOf(20))
                .stripTrailingZeros()
                .toPlainString();
    }

    public boolean modified() {
        return !id.equals(originalId)
                || level != originalLevel
                || ticks != originalTicks
                || ambient != originalAmbient
                || particles != originalParticles
                || icon != originalIcon;
    }

    public String invalidReason() {
        if (effect() == null) return "Choose a registered effect.";
        if (level < 1 || level > 256) return "Enter a level from 1 to 256.";
        if (ticks < -1) return "Duration must be positive or infinite.";
        return null;
    }

    public CompoundTag tag() {
        CompoundTag tag = original.copy();
        if (!modified() && !tag.contains("Id")) return tag;
        // Unknown imported entries are retained rather than silently becoming Speed.
        if (effect() == null && !modified()) return tag;
        for (String key :
                new String[] {
                    "Id", "Amplifier", "Duration", "Ambient", "ShowParticles", "ShowIcon"
                }) tag.remove(key);
        tag.putString("id", id);
        tag.putInt("amplifier", level - 1);
        tag.putInt("duration", ticks);
        tag.putBoolean("ambient", ambient);
        tag.putBoolean("show_particles", particles);
        tag.putBoolean("show_icon", icon);
        if (tag.getCompound("HiddenEffect").isPresent()) {
            tag.put(
                    "hidden_effect",
                    new EffectEditorData(tag.getCompound("HiddenEffect").orElseThrow()).tag());
            tag.remove("HiddenEffect");
        }
        return tag;
    }

    public EffectEditorData copy() {
        return new EffectEditorData(tag());
    }

    public MobEffectInstance instance(HolderLookup.Provider registries) {
        return MobEffectInstance.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag())
                .getOrThrow();
    }
}
