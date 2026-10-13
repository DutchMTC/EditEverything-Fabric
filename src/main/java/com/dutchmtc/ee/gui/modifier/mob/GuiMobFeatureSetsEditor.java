package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.mobdata.FeatureSetRepository;
import com.dutchmtc.ee.mobdata.MobDataRepository;
import com.google.gson.JsonObject;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Property groups control which optional fields are shown; they do not add values to the mob. */
public class GuiMobFeatureSetsEditor extends GuiMobSettingsPage {
    public GuiMobFeatureSetsEditor(
            Screen parent, Consumer<CompoundTag> setter, MobPropertyEditorState state) {
        super(parent, setter, state, true);
    }

    @Override
    protected void buildRows() {
        JsonObject root = FeatureSetRepository.getFeatureSets();
        JsonObject groups = root == null ? null : root.getAsJsonObject("feature_sets");
        if (groups == null) {
            row("No extra properties available", "", null, null, 0);
            return;
        }
        Set<String> suggested = new HashSet<>();
        JsonObject entity = MobDataRepository.getEntity(state.entityPath);
        if (entity != null
                && entity.has("feature_sets")
                && entity.get("feature_sets").isJsonArray())
            entity.getAsJsonArray("feature_sets")
                    .forEach(
                            value -> {
                                if (value.isJsonPrimitive()) suggested.add(value.getAsString());
                            });
        groups.keySet().stream()
                .filter(suggested::contains)
                .filter(key -> !FeatureSetRepository.getFeatureSetFieldRefs(key).isEmpty())
                .sorted(
                        (a, b) -> {
                            int priority =
                                    Boolean.compare(suggested.contains(b), suggested.contains(a));
                            return priority == 0 ? a.compareTo(b) : priority;
                        })
                .forEach(
                        key -> {
                            String label =
                                    switch (key) {
                                        case "breedable" -> "Breeding";
                                        case "bucket" -> "Bucket data";
                                        case "tameable" -> "Taming";
                                        case "ageable" -> "Age";
                                        case "anger" -> "Anger";
                                        case "mob" -> "Mob behavior";
                                        case "health" -> "Health";
                                        case "trader" -> "Villager data";
                                        default -> friendly(key);
                                    };
                            String description =
                                    switch (key) {
                                        case "breedable" -> "Breeding and the player responsible";
                                        case "bucket" -> "Origin and bucket history";
                                        case "tameable" -> "Ownership";
                                        case "ageable" -> "Growth and breeding cooldown";
                                        case "anger" -> "Anger target and expiry";
                                        case "mob" -> "Home, loot and handedness";
                                        case "health" -> "Current health and absorption hearts";
                                        case "trader" -> "Profession, biome and level";
                                        default -> "Additional mob properties";
                                    };
                            String detail = description;
                            row(
                                    label,
                                    detail,
                                    "Show "
                                            + description.toLowerCase(Locale.ROOT)
                                            + " in Properties. This changes which fields are"
                                            + " visible; the mob changes when you edit those"
                                            + " fields.",
                                    toggle(
                                            label,
                                            () -> state.enabledFeatureSets.contains(key),
                                            value -> {
                                                if (value) state.enabledFeatureSets.add(key);
                                                else state.enabledFeatureSets.remove(key);
                                            }),
                                    52);
                        });
    }
}
