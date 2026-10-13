package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EETextField;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTIntArrayModifier;
import com.dutchmtc.ee.gui.selector.GuiBlockTypeListSelector;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.mobdata.FeatureSetRepository;
import com.dutchmtc.ee.mobdata.MobDataRepository;
import com.dutchmtc.ee.mobdata.MobPropertyValues;
import com.dutchmtc.ee.mobdata.NbtPath;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class GuiMobFieldsEditor extends GuiMobSettingsPage {
    private final List<Runnable> dependentRows = new ArrayList<>();

    public GuiMobFieldsEditor(
            Screen parent, Consumer<CompoundTag> setter, MobPropertyEditorState state) {
        super(parent, setter, state, false);
    }

    @Override
    protected void buildRows() {
        dependentRows.clear();
        JsonObject entity = MobDataRepository.getEntity(state.entityPath);
        JsonObject fields = MobDataRepository.getEntityFields();
        if (entity == null || fields == null) {
            row(
                    "No specific properties available",
                    "",
                    "This mob has no property definitions yet.",
                    null,
                    0);
            return;
        }
        LinkedHashMap<String, JsonObject> defs = new LinkedHashMap<>();
        Set<String> supported = new HashSet<>();
        if (entity.has("feature_sets"))
            entity.getAsJsonArray("feature_sets")
                    .forEach(value -> supported.add(value.getAsString()));
        if (supported.contains("tameable") && state.enabledFeatureSets.contains("tameable"))
            for (String ref : FeatureSetRepository.getFeatureSetFieldRefs("tameable"))
                addFieldEntries(ref, fields, defs);
        addFieldArrayEntries(entity.get("fields"), fields, defs);
        for (String group : state.enabledFeatureSets)
            if (supported.contains(group))
                for (String ref : FeatureSetRepository.getFeatureSetFieldRefs(group))
                    addFieldEntries(ref, fields, defs);
        addFieldEntries("#invulnerable_time", fields, defs);
        for (JsonObject def : defs.values()) {
            FieldMeta meta = FieldMeta.from(def, fields);
            if (meta != null) buildRow(meta);
        }
        if (rows.isEmpty()) row("No specific properties available", "", null, null, 0);
    }

    private static void addFieldArrayEntries(
            @Nullable JsonElement fieldsEl,
            JsonObject entityFields,
            LinkedHashMap<String, JsonObject> out) {
        if (fieldsEl == null || fieldsEl.isJsonNull()) return;
        if (!fieldsEl.isJsonArray()) return;
        JsonArray arr = fieldsEl.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement entry = arr.get(i);
            addFieldEntries(entry, entityFields, out);
        }
    }

    private static void addFieldEntries(
            @Nullable String refOrNull,
            JsonObject entityFields,
            LinkedHashMap<String, JsonObject> out) {
        if (refOrNull == null) return;
        if (!refOrNull.startsWith("#")) return;
        JsonObject def = MobDataRepository.getEntityFieldDef(refOrNull.substring(1));
        if (def == null) return;
        String key = def.has("field") ? def.get("field").getAsString() : refOrNull;
        out.putIfAbsent(key, def);
    }

    private static void addFieldEntries(
            @Nullable JsonElement entry,
            JsonObject entityFields,
            LinkedHashMap<String, JsonObject> out) {
        if (entry == null || entry.isJsonNull()) return;
        if (entry.isJsonPrimitive()) {
            String s = entry.getAsString();
            addFieldEntries(s, entityFields, out);
            return;
        }
        if (!entry.isJsonObject()) return;
        JsonObject def = entry.getAsJsonObject();
        if (!def.has("field")) return;
        String key = def.get("field").getAsString();
        out.putIfAbsent(key, def);
    }

    private static final class FieldMeta {
        final String fieldPath;
        final String type;
        final String label;
        final @Nullable String help;
        final @Nullable List<Option> options;
        final boolean optionsAllInt;
        final @Nullable List<GuiMobAdditiveMatrixEditor.Group> matrixGroups;
        final int matrixDefault;
        JsonObject definition;

        private FieldMeta(
                String fieldPath,
                String type,
                String label,
                @Nullable String help,
                @Nullable List<Option> options,
                boolean optionsAllInt,
                @Nullable List<GuiMobAdditiveMatrixEditor.Group> matrixGroups,
                int matrixDefault) {
            this.fieldPath = fieldPath;
            this.type = type;
            this.label = label;
            this.help = help;
            this.options = options;
            this.optionsAllInt = optionsAllInt;
            this.matrixGroups = matrixGroups;
            this.matrixDefault = matrixDefault;
        }

        static @Nullable FieldMeta from(JsonObject def, JsonObject entityFields) {
            if (!def.has("field")) return null;
            if (!def.has("type")) return null;
            String fieldPath = def.get("field").getAsString();
            String type = def.get("type").getAsString();
            String label = def.has("label") ? def.get("label").getAsString() : fieldPath;
            String help = def.has("help") ? def.get("help").getAsString() : null;

            List<Option> options = null;
            boolean allInt = false;
            if (("select".equals(type) || "checklist".equals(type)) && def.has("options")) {
                JsonElement opt = def.get("options");
                JsonArray optArr = null;
                if (opt.isJsonPrimitive()) {
                    String s = opt.getAsString();
                    if (s.startsWith("#")) {
                        JsonObject ref = MobDataRepository.getEntityFieldDef(s.substring(1));
                        if (ref != null && ref.has("options") && ref.get("options").isJsonArray()) {
                            optArr = ref.getAsJsonArray("options");
                        }
                    }
                } else if (opt.isJsonArray()) {
                    optArr = opt.getAsJsonArray();
                }
                if (optArr != null) {
                    options = new ArrayList<>();
                    allInt = true;
                    for (int i = 0; i < optArr.size(); i++) {
                        if (!optArr.get(i).isJsonObject()) continue;
                        JsonObject o = optArr.get(i).getAsJsonObject();
                        String oLabel = o.has("label") ? o.get("label").getAsString() : "";
                        String oValue = "";
                        if (o.has("value")) {
                            JsonElement v = o.get("value");
                            if (v != null && v.isJsonPrimitive()) {
                                var prim = v.getAsJsonPrimitive();
                                if (prim.isNumber()) {
                                    oValue = prim.getAsNumber().toString();
                                } else if (prim.isString()) {
                                    oValue = prim.getAsString();
                                } else if (prim.isBoolean()) {
                                    oValue = prim.getAsBoolean() ? "1" : "0";
                                } else {
                                    oValue = v.getAsString();
                                }
                            } else if (v != null) {
                                oValue = v.toString();
                            }
                        }
                        options.add(new Option(oLabel, oValue));
                        if (!oValue.isEmpty() && !isIntLike(oValue)) {
                            allInt = false;
                        }
                    }
                }
            }

            List<GuiMobAdditiveMatrixEditor.Group> groups = null;
            int defaultValue = 0;
            if ("additive-matrix".equals(type)) {
                if (def.has("default")
                        && def.get("default").isJsonPrimitive()
                        && def.get("default").getAsJsonPrimitive().isNumber()) {
                    defaultValue = def.get("default").getAsInt();
                }
                if (def.has("option_groups") && def.get("option_groups").isJsonArray()) {
                    groups = new ArrayList<>();
                    JsonArray gArr = def.getAsJsonArray("option_groups");
                    for (int i = 0; i < gArr.size(); i++) {
                        if (!gArr.get(i).isJsonObject()) continue;
                        JsonObject g = gArr.get(i).getAsJsonObject();
                        String gLabel =
                                g.has("label")
                                        ? g.get("label").getAsString()
                                        : ("Group " + (i + 1));
                        if (!g.has("options") || !g.get("options").isJsonArray()) continue;
                        JsonArray gOpts = g.getAsJsonArray("options");
                        List<GuiMobAdditiveMatrixEditor.Option> parsed = new ArrayList<>();
                        int mask = 0;
                        for (int j = 0; j < gOpts.size(); j++) {
                            if (!gOpts.get(j).isJsonObject()) continue;
                            JsonObject o = gOpts.get(j).getAsJsonObject();
                            String oLabel = o.has("label") ? o.get("label").getAsString() : "";
                            int oValue = 0;
                            if (o.has("value")
                                    && o.get("value").isJsonPrimitive()
                                    && o.get("value").getAsJsonPrimitive().isNumber()) {
                                oValue = o.get("value").getAsInt();
                            }
                            mask |= oValue;
                            parsed.add(new GuiMobAdditiveMatrixEditor.Option(oLabel, oValue));
                        }
                        groups.add(new GuiMobAdditiveMatrixEditor.Group(gLabel, parsed, mask));
                    }
                }
            }

            FieldMeta meta =
                    new FieldMeta(
                            fieldPath, type, label, help, options, allInt, groups, defaultValue);
            meta.definition = def.deepCopy();
            return meta;
        }

        private static boolean isIntLike(String s) {
            if (s == null || s.isEmpty()) return false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (i == 0 && c == '-') continue;
                if (c < '0' || c > '9') return false;
            }
            return true;
        }
    }

    record Option(String label, String value) {}

    private void buildRow(FieldMeta meta) {
        if ("static".equals(meta.type) || meta.fieldPath.startsWith("ux.")) return;
        String label = friendly(meta.label);
        String[] parts = NbtPath.split(meta.fieldPath);
        if (parts.length == 0) return;
        switch (meta.type) {
            case "hr" -> separator();
            case "bool" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            toggle(
                                    label,
                                    () -> {
                                        CompoundTag p = getParentExisting(parts);
                                        return p != null && p.contains(parts[parts.length - 1])
                                                ? ItemUtils.getBoolean(p, parts[parts.length - 1])
                                                : meta.definition.has("default")
                                                        && meta.definition
                                                                .get("default")
                                                                .getAsBoolean();
                                    },
                                    value -> {
                                        if (meta.fieldPath.equals("ChestedHorse")
                                                && !value
                                                && state.tag
                                                        .getList("Items")
                                                        .map(list -> !list.isEmpty())
                                                        .orElse(false)) {
                                            mc.gui.setScreen(
                                                    new com.dutchmtc.ee.gui.GuiConfirmation(
                                                            this,
                                                            Component.literal(
                                                                    "Remove this mob's chest and"
                                                                            + " its contents?"),
                                                            Component.literal("Remove chest"),
                                                            () -> {
                                                                state.tag.putBoolean(
                                                                        "ChestedHorse", false);
                                                                state.tag.remove("Items");
                                                                mc.gui.setScreen(this);
                                                            },
                                                            () -> mc.gui.setScreen(this)));
                                        } else
                                            ItemUtils.putBoolean(
                                                    getOrCreateParent(parts),
                                                    parts[parts.length - 1],
                                                    value);
                                    }),
                            52);
            case "number" -> numberRow(meta, parts);
            case "anger-timer" -> {
                meta.definition.addProperty("nbt_type", "long");
                meta.definition.addProperty("min", 0);
                meta.definition.addProperty("default", 0);
                numberRow(meta, parts);
            }
            case "text", "identifier" -> {
                CompoundTag p = getParentExisting(parts);
                EditBox box =
                        input(
                                label,
                                state.inputDrafts.getOrDefault(
                                        meta.fieldPath,
                                        p == null
                                                ? ""
                                                : ItemUtils.getString(p, parts[parts.length - 1])));
                box.setResponder(
                        value -> {
                            state.inputDrafts.put(meta.fieldPath, value);
                            if (meta.type.equals("identifier")
                                    && !value.isBlank()
                                    && Identifier.tryParse(value) == null) {
                                EETextField.validate(
                                        box,
                                        "Enter a resource ID such as minecraft:entities/zombie, or"
                                                + " clear it for the default.");
                                refreshValidity();
                                return;
                            }
                            EETextField.validate(box, null);
                            if (value.isEmpty()) {
                                CompoundTag existing = getParentExisting(parts);
                                if (existing != null)
                                    ItemUtils.remove(existing, parts[parts.length - 1]);
                            } else
                                ItemUtils.putString(
                                        getOrCreateParent(parts), parts[parts.length - 1], value);
                        });
                row(
                        label,
                        meta.fieldPath,
                        meta.help,
                        box,
                        156,
                        () ->
                                !meta.type.equals("identifier")
                                        || box.getValue().isBlank()
                                        || Identifier.tryParse(box.getValue()) != null);
                if (meta.type.equals("identifier")
                        && !box.getValue().isBlank()
                        && Identifier.tryParse(box.getValue()) == null)
                    EETextField.validate(box, "Enter a valid resource ID.");
            }
            case "select" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> selectionLabel(meta, parts),
                                    () -> openSelection(meta, parts)),
                            156);
            case "registry" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> registryLabel(currentValue(parts)),
                                    () -> openRegistry(meta, parts)),
                            156);
            case "uuid" -> {
                var control =
                        valueButton(
                                () -> playerLabel(NbtPath.getTag(state.tag, parts)),
                                () ->
                                        mc.gui.setScreen(
                                                new GuiMobPlayerEditor(
                                                        this,
                                                        label,
                                                        MobPropertyValues.uuid(
                                                                NbtPath.getTag(state.tag, parts)),
                                                        value ->
                                                                MobPropertyValues.set(
                                                                        state.tag,
                                                                        meta.fieldPath,
                                                                        value == null
                                                                                ? null
                                                                                : MobPropertyValues
                                                                                        .uuid(
                                                                                                value)),
                                                        meta.definition.has("mob_reference")
                                                                && meta.definition
                                                                        .get("mob_reference")
                                                                        .getAsBoolean())));
                row(label, meta.fieldPath, meta.help, control, 156);
                if (meta.definition.has("requires_nonnegative")) {
                    Runnable update =
                            () -> {
                                Tag requirement =
                                        NbtPath.getTag(
                                                state.tag,
                                                NbtPath.split(
                                                        meta.definition
                                                                .get("requires_nonnegative")
                                                                .getAsString()));
                                control.active =
                                        requirement instanceof NumericTag n
                                                && n.box().longValue() >= 0;
                                control.setTooltip(
                                        Tooltip.create(
                                                Component.literal(
                                                        control.active
                                                                ? meta.help
                                                                : "Set a cure time first. Minecraft"
                                                                        + " only saves the curing"
                                                                        + " player while the mob is"
                                                                        + " being cured.")));
                            };
                    dependentRows.add(update);
                    update.run();
                }
            }
            case "uuid-list" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> {
                                        Tag current = NbtPath.getTag(state.tag, parts);
                                        return current instanceof ListTag list && !list.isEmpty()
                                                ? list.size() + " players"
                                                : "None";
                                    },
                                    () -> {
                                        Tag current = NbtPath.getTag(state.tag, parts);
                                        mc.gui.setScreen(
                                                new GuiMobPlayersEditor(
                                                        this,
                                                        label,
                                                        current instanceof ListTag list
                                                                ? list
                                                                : new ListTag(),
                                                        meta.definition.has("max_entries")
                                                                ? meta.definition
                                                                        .get("max_entries")
                                                                        .getAsInt()
                                                                : 64,
                                                        value ->
                                                                MobPropertyValues.set(
                                                                        state.tag,
                                                                        meta.fieldPath,
                                                                        value.isEmpty()
                                                                                ? null
                                                                                : value)));
                                    }),
                            156);
            case "gossip" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> "Edit reputation",
                                    () -> {
                                        Tag current = NbtPath.getTag(state.tag, parts);
                                        mc.gui.setScreen(
                                                new GuiMobGossipEditor(
                                                        this,
                                                        current instanceof ListTag list
                                                                ? list
                                                                : new ListTag(),
                                                        value ->
                                                                MobPropertyValues.set(
                                                                        state.tag,
                                                                        meta.fieldPath,
                                                                        value.isEmpty()
                                                                                ? null
                                                                                : value)));
                                    }),
                            156);
            case "anger-suspects" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> "Edit targets",
                                    () -> {
                                        Tag current = NbtPath.getTag(state.tag, parts);
                                        mc.gui.setScreen(
                                                new GuiMobAngerEditor(
                                                        this,
                                                        current instanceof ListTag list
                                                                ? list
                                                                : new ListTag(),
                                                        value ->
                                                                MobPropertyValues.set(
                                                                        state.tag,
                                                                        meta.fieldPath,
                                                                        value.isEmpty()
                                                                                ? null
                                                                                : value)));
                                    }),
                            156);
            case "inventory" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(() -> "Edit contents", () -> openInventory(meta)),
                            156);
            case "leash" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () ->
                                            NbtPath.getTag(state.tag, parts) == null
                                                    ? "Not leashed"
                                                    : "Attached",
                                    () ->
                                            mc.gui.setScreen(
                                                    new GuiMobLeashEditor(
                                                            this,
                                                            NbtPath.getTag(state.tag, parts),
                                                            value ->
                                                                    MobPropertyValues.set(
                                                                            state.tag,
                                                                            meta.fieldPath,
                                                                            value)))),
                            156);
            case "stew-effects" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> "Edit effects",
                                    () -> {
                                        Tag current = NbtPath.getTag(state.tag, parts);
                                        mc.gui.setScreen(
                                                new GuiMobStewEffectsEditor(
                                                        this,
                                                        current instanceof ListTag list
                                                                ? list
                                                                : new ListTag(),
                                                        value ->
                                                                MobPropertyValues.set(
                                                                        state.tag,
                                                                        meta.fieldPath,
                                                                        value.isEmpty()
                                                                                ? null
                                                                                : value)));
                                    }),
                            156);
            case "block-state" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> {
                                        Tag current = NbtPath.getTag(state.tag, parts);
                                        return current == null
                                                ? "None"
                                                : net.minecraft.world.level.block.state.BlockState
                                                        .CODEC
                                                        .parse(NbtOps.INSTANCE, current)
                                                        .result()
                                                        .map(
                                                                block ->
                                                                        block.getBlock()
                                                                                .getName()
                                                                                .getString())
                                                        .orElse("Imported block");
                                    },
                                    () ->
                                            mc.gui.setScreen(
                                                    new GuiBlockTypeListSelector(
                                                            this,
                                                            Component.literal(label),
                                                            block -> {
                                                                Tag value =
                                                                        block == null
                                                                                        || block
                                                                                                == net
                                                                                                        .minecraft
                                                                                                        .world
                                                                                                        .level
                                                                                                        .block
                                                                                                        .Blocks
                                                                                                        .AIR
                                                                                ? null
                                                                                : net.minecraft
                                                                                        .world.level
                                                                                        .block.state
                                                                                        .BlockState
                                                                                        .CODEC
                                                                                        .encodeStart(
                                                                                                NbtOps
                                                                                                        .INSTANCE,
                                                                                                block
                                                                                                        .defaultBlockState())
                                                                                        .getOrThrow();
                                                                MobPropertyValues.set(
                                                                        state.tag,
                                                                        meta.fieldPath,
                                                                        value);
                                                                return this;
                                                            }))),
                            156);
            case "block-position" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> {
                                        Tag current = NbtPath.getTag(state.tag, parts);
                                        if (current == null) return "Not set";
                                        int[] xyz = MobPropertyValues.position(current);
                                        return xyz[0] + ", " + xyz[1] + ", " + xyz[2];
                                    },
                                    () ->
                                            mc.gui.setScreen(
                                                    new GuiMobPositionEditor(
                                                            this,
                                                            label,
                                                            NbtPath.getTag(state.tag, parts),
                                                            value -> {
                                                                MobPropertyValues.set(
                                                                        state.tag,
                                                                        meta.fieldPath,
                                                                        value);
                                                                if (meta.definition.has(
                                                                                "controls_home")
                                                                        && meta.definition
                                                                                .get(
                                                                                        "controls_home")
                                                                                .getAsBoolean()) {
                                                                    if (value == null) {
                                                                        state.tag.remove(
                                                                                "home_radius");
                                                                        state.inputDrafts.remove(
                                                                                "home_radius");
                                                                    } else if (state.tag
                                                                                    .getInt(
                                                                                            "home_radius")
                                                                                    .orElse(-1)
                                                                            < 0) {
                                                                        state.tag.putInt(
                                                                                "home_radius", 16);
                                                                        state.inputDrafts.remove(
                                                                                "home_radius");
                                                                    }
                                                                }
                                                            }))),
                            156);
            case "checklist" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> {
                                        Tag current = NbtPath.getTag(state.tag, parts);
                                        return (current instanceof ListTag list ? list.size() : 0)
                                                + " selected";
                                    },
                                    () -> {
                                        if (meta.options != null && !meta.options.isEmpty())
                                            mc.gui.setScreen(
                                                    new GuiMobChecklistEditor(
                                                            this,
                                                            ignored -> {},
                                                            state,
                                                            meta.fieldPath,
                                                            label,
                                                            meta.options,
                                                            meta.optionsAllInt));
                                    }),
                            156);
            case "int-array" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> {
                                        CompoundTag p = getParentExisting(parts);
                                        return (p == null
                                                        ? 0
                                                        : ItemUtils.getIntArray(
                                                                        p, parts[parts.length - 1])
                                                                .length)
                                                + " values";
                                    },
                                    () -> {
                                        CompoundTag p = getParentExisting(parts);
                                        int[] values =
                                                p == null
                                                        ? new int[0]
                                                        : ItemUtils.getIntArray(
                                                                p, parts[parts.length - 1]);
                                        mc.gui.setScreen(
                                                new GuiNBTIntArrayModifier(
                                                        Component.literal(label),
                                                        this,
                                                        out ->
                                                                ItemUtils.putIntArray(
                                                                        getOrCreateParent(parts),
                                                                        parts[parts.length - 1],
                                                                        out.getAsIntArray()),
                                                        new IntArrayTag(values)));
                                    }),
                            156);
            case "coordinates" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> "Edit coordinates",
                                    () ->
                                            mc.gui.setScreen(
                                                    new GuiMobCoordinatesEditor(
                                                            this,
                                                            ignored -> {},
                                                            state,
                                                            meta.fieldPath,
                                                            label))),
                            156);
            case "additive-matrix" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> {
                                        CompoundTag p = getParentExisting(parts);
                                        return Integer.toString(
                                                p == null
                                                        ? meta.matrixDefault
                                                        : ItemUtils.getInt(
                                                                p, parts[parts.length - 1]));
                                    },
                                    () -> {
                                        if (meta.matrixGroups != null
                                                && !meta.matrixGroups.isEmpty())
                                            mc.gui.setScreen(
                                                    new GuiMobAdditiveMatrixEditor(
                                                            this,
                                                            ignored -> {},
                                                            state,
                                                            meta.fieldPath,
                                                            label,
                                                            meta.matrixGroups,
                                                            meta.matrixDefault));
                                    }),
                            156);
            case "text-autocomplete-blocks" ->
                    row(
                            label,
                            meta.fieldPath,
                            meta.help,
                            valueButton(
                                    () -> blockLabel(parts),
                                    () ->
                                            mc.gui.setScreen(
                                                    new GuiBlockTypeListSelector(
                                                            this,
                                                            Component.literal(label),
                                                            block -> {
                                                                if (block == null) {
                                                                    if (meta.fieldPath.equals(
                                                                            "equipment.body.id")) {
                                                                        MobPropertyValues.set(
                                                                                state.tag,
                                                                                "equipment.body",
                                                                                null);
                                                                        state.inputDrafts.remove(
                                                                                "equipment.body.count");
                                                                        return this;
                                                                    }
                                                                    CompoundTag p =
                                                                            getParentExisting(
                                                                                    parts);
                                                                    if (p != null)
                                                                        ItemUtils.remove(
                                                                                p,
                                                                                parts[
                                                                                        parts.length
                                                                                                - 1]);
                                                                } else
                                                                    ItemUtils.putString(
                                                                            getOrCreateParent(
                                                                                    parts),
                                                                            parts[parts.length - 1],
                                                                            ItemUtils.getRegistry(
                                                                                            block)
                                                                                    .toString());
                                                                return this;
                                                            }))),
                            156);
            default ->
                    row(label, meta.fieldPath, "This property type cannot be edited yet.", null, 0);
        }
    }

    @Override
    public void tick() {
        super.tick();
        dependentRows.forEach(Runnable::run);
    }

    private void numberRow(FieldMeta meta, String[] parts) {
        if (meta.definition.has("health_limit")) {
            double maximum = maximumHealth();
            meta.definition.addProperty("max", maximum);
            meta.definition.addProperty("default", maximum);
        }
        Tag current = NbtPath.getTag(state.tag, parts);
        String initial =
                current == null
                        ? ""
                        : current instanceof NumericTag n ? n.box().toString() : current.toString();
        if (meta.type.equals("anger-timer") && current instanceof NumericTag n)
            initial =
                    Long.toString(
                            Math.max(
                                    0,
                                    n.box().longValue()
                                            - (mc.level == null ? 0 : mc.level.getGameTime())));
        EditBox box =
                input(
                        friendly(meta.label),
                        state.inputDrafts.getOrDefault(meta.fieldPath, initial));
        box.setMaxLength(128);
        if (meta.fieldPath.equals("equipment.body.count")
                && NbtPath.getTag(state.tag, NbtPath.split("equipment.body.id")) == null) {
            box.active = false;
            box.setTooltip(Tooltip.create(Component.literal("Choose an absorbed block first.")));
            row(
                    friendly(meta.label),
                    meta.fieldPath,
                    "Choose an absorbed block before editing its count.",
                    box,
                    110);
            return;
        }
        if (meta.definition.has("default"))
            box.setHint(
                    Component.literal(meta.definition.get("default").getAsString())
                            .withColor(0xAEBBB5));
        boolean[] valid = {true};
        row(friendly(meta.label), meta.fieldPath, meta.help, box, 110, () -> valid[0]);
        Consumer<String> validate =
                value -> {
                    try {
                        if (!value.isBlank())
                            MobPropertyValues.number(
                                    value,
                                    meta.definition.has("nbt_type")
                                            ? meta.definition.get("nbt_type").getAsString()
                                            : "int",
                                    meta.definition.has("min")
                                            ? meta.definition.get("min").getAsDouble()
                                            : null,
                                    meta.definition.has("max")
                                            ? meta.definition.get("max").getAsDouble()
                                            : null);
                        valid[0] = true;
                        EETextField.validate(box, null);
                        box.setTooltip(
                                meta.help == null
                                        ? null
                                        : Tooltip.create(Component.literal(meta.help)));
                    } catch (IllegalArgumentException ex) {
                        valid[0] = false;
                        String reason =
                                "Enter a valid "
                                        + (meta.definition.has("nbt_type")
                                                        && Set.of("float", "double")
                                                                .contains(
                                                                        meta.definition
                                                                                .get("nbt_type")
                                                                                .getAsString())
                                                ? "finite number"
                                                : "whole number")
                                        + (meta.definition.has("min")
                                                ? " at least "
                                                        + meta.definition.get("min").getAsString()
                                                : "")
                                        + (meta.definition.has("max")
                                                ? " and no more than "
                                                        + meta.definition.get("max").getAsString()
                                                : "")
                                        + ", or clear it for the default.";
                        EETextField.validate(box, reason);
                    }
                    refreshValidity();
                };
        box.setResponder(
                value -> {
                    state.inputDrafts.put(meta.fieldPath, value);
                    validate.accept(value);
                    if (valid[0]) {
                        Tag parsed =
                                value.isBlank()
                                        ? null
                                        : MobPropertyValues.number(
                                                value,
                                                meta.definition.has("nbt_type")
                                                        ? meta.definition
                                                                .get("nbt_type")
                                                                .getAsString()
                                                        : "int",
                                                null,
                                                null);
                        if (meta.type.equals("anger-timer") && parsed instanceof NumericTag n) {
                            try {
                                parsed =
                                        LongTag.valueOf(
                                                Math.addExact(
                                                        mc.level == null
                                                                ? 0
                                                                : mc.level.getGameTime(),
                                                        n.box().longValue()));
                            } catch (ArithmeticException ex) {
                                valid[0] = false;
                                EETextField.validate(box, "This duration is too large.");
                                refreshValidity();
                                return;
                            }
                        }
                        if (meta.fieldPath.equals("equipment.body.count")
                                && parsed instanceof NumericTag n) {
                            var body =
                                    (CompoundTag)
                                            NbtPath.getTag(
                                                    state.tag, NbtPath.split("equipment.body"));
                            var normalized = body.copy();
                            normalized.putInt("count", 1);
                            var ops =
                                    (mc.level != null
                                                    ? mc.level.registryAccess()
                                                    : mc.getConnection().registryAccess())
                                            .createSerializationContext(NbtOps.INSTANCE);
                            var stack =
                                    net.minecraft.world.item.ItemStack.CODEC
                                            .parse(ops, normalized)
                                            .result()
                                            .orElse(null);
                            if (stack == null) {
                                valid[0] = false;
                                EETextField.validate(box, "Choose a valid absorbed block first.");
                                refreshValidity();
                                return;
                            }
                            int amount = n.box().intValue();
                            if (amount > stack.getMaxStackSize())
                                stack.set(
                                        net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,
                                        amount);
                            stack.setCount(amount);
                            MobPropertyValues.set(
                                    state.tag,
                                    "equipment.body",
                                    net.minecraft.world.item.ItemStack.CODEC
                                            .encodeStart(ops, stack)
                                            .getOrThrow());
                        } else MobPropertyValues.set(state.tag, meta.fieldPath, parsed);
                        if (meta.fieldPath.equals("home_radius")
                                && parsed instanceof NumericTag n
                                && n.box().intValue() >= 0
                                && !state.tag.contains("home_pos")) {
                            var pos =
                                    mc.player == null
                                            ? net.minecraft.core.BlockPos.ZERO
                                            : mc.player.blockPosition();
                            state.tag.put(
                                    "home_pos",
                                    MobPropertyValues.position(pos.getX(), pos.getY(), pos.getZ()));
                        }
                    }
                });
        validate.accept(box.getValue());
    }

    private double maximumHealth() {
        var type =
                BuiltInRegistries.ENTITY_TYPE
                        .getOptional(Identifier.fromNamespaceAndPath("minecraft", state.entityPath))
                        .orElse(null);
        if (type == null || mc.level == null) return Float.MAX_VALUE;
        var entity = type.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living))
            return Float.MAX_VALUE;
        var access = mc.level.registryAccess();
        var ops = access.createSerializationContext(NbtOps.INSTANCE);
        living.load(
                net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING, access, state.tag.copy()));
        var attributes = state.tag.get("attributes");
        if (attributes != null)
            net.minecraft.world.entity.ai.attributes.AttributeInstance.Packed.LIST_CODEC
                    .parse(ops, attributes)
                    .result()
                    .ifPresent(living.getAttributes()::apply);
        state.tag
                .getList("active_effects")
                .ifPresent(
                        effects ->
                                effects.forEach(
                                        effect ->
                                                net.minecraft.world.effect.MobEffectInstance.CODEC
                                                        .parse(ops, effect)
                                                        .result()
                                                        .ifPresent(
                                                                instance ->
                                                                        instance.getEffect()
                                                                                .value()
                                                                                .addAttributeModifiers(
                                                                                        living
                                                                                                .getAttributes(),
                                                                                        instance
                                                                                                .getAmplifier()))));
        return living.getMaxHealth();
    }

    private String playerLabel(Tag value) {
        var id = MobPropertyValues.uuid(value);
        if (id == null) return value == null ? "None" : "Imported value";
        if (mc.player != null && id.equals(mc.player.getUUID())) return "Me";
        if (mc.getConnection() != null)
            for (var info : mc.getConnection().getOnlinePlayers())
                if (info.getProfile().id().equals(id)) return info.getProfile().name();
        return "Offline player";
    }

    private void openInventory(FieldMeta meta) {
        boolean slotItems =
                meta.definition.has("slot_items")
                        && meta.definition.get("slot_items").getAsBoolean();
        int columns =
                meta.definition.has("llama")
                        ? Math.clamp(state.tag.getInt("Strength").orElse(1), 1, 5)
                        : 5;
        int slots = slotItems ? columns * 3 : meta.definition.get("slots").getAsInt();
        var stacks =
                net.minecraft.core.NonNullList.withSize(
                        slots, net.minecraft.world.item.ItemStack.EMPTY);
        var preserved = new ArrayList<Tag>();
        Tag current = NbtPath.getTag(state.tag, NbtPath.split(meta.fieldPath));
        var ops =
                (mc.level != null ? mc.level.registryAccess() : mc.getConnection().registryAccess())
                        .createSerializationContext(NbtOps.INSTANCE);
        if (current instanceof ListTag list)
            for (int i = 0; i < list.size(); i++) {
                if (slotItems) {
                    var packed =
                            net.minecraft.world.ItemStackWithSlot.CODEC
                                    .parse(ops, list.get(i))
                                    .result()
                                    .orElse(null);
                    if (packed != null && packed.isValidInContainer(slots))
                        stacks.set(packed.slot(), packed.stack().copy());
                    else preserved.add(list.get(i).copy());
                } else {
                    var stack =
                            net.minecraft.world.item.ItemStack.CODEC
                                    .parse(ops, list.get(i))
                                    .result()
                                    .orElse(null);
                    if (i < slots && stack != null) stacks.set(i, stack.copy());
                    else preserved.add(list.get(i).copy());
                }
            }
        var data =
                new ItemUtils.ContainerData(
                        slotItems
                                ? new ItemUtils.ContainerSize(columns, 3)
                                : new ItemUtils.ContainerSize(Math.min(9, slots), (slots + 8) / 9),
                        stacks);
        mc.gui.setScreen(
                new com.dutchmtc.ee.gui.modifier.GuiContainerModifier(
                        this,
                        Component.literal(meta.label),
                        out -> {
                            boolean unchanged = true;
                            for (int i = 0; i < slots; i++)
                                if (!net.minecraft.world.item.ItemStack.matches(
                                        stacks.get(i), out.stacks().get(i))) {
                                    unchanged = false;
                                    break;
                                }
                            if (unchanged) return;
                            var list = new ListTag();
                            for (int i = 0; i < out.stacks().size(); i++) {
                                var stack = out.stacks().get(i);
                                if (!stack.isEmpty())
                                    list.add(
                                            slotItems
                                                    ? net.minecraft.world.ItemStackWithSlot.CODEC
                                                            .encodeStart(
                                                                    ops,
                                                                    new net.minecraft.world
                                                                            .ItemStackWithSlot(
                                                                            i, stack))
                                                            .getOrThrow()
                                                    : net.minecraft.world.item.ItemStack.CODEC
                                                            .encodeStart(ops, stack)
                                                            .getOrThrow());
                            }
                            preserved.forEach(t -> list.add(t.copy()));
                            MobPropertyValues.set(
                                    state.tag, meta.fieldPath, list.isEmpty() ? null : list);
                            if (slotItems && !list.isEmpty())
                                state.tag.putBoolean("ChestedHorse", true);
                        },
                        data));
    }

    private String registryLabel(String value) {
        return value.isEmpty()
                ? "Default"
                : friendly(value.contains(":") ? value.substring(value.indexOf(':') + 1) : value);
    }

    private void openRegistry(FieldMeta meta, String[] parts) {
        var key =
                ResourceKey.<Object>createRegistryKey(
                        Identifier.parse(meta.definition.get("registry").getAsString()));
        var access =
                mc.level != null ? mc.level.registryAccess() : mc.getConnection().registryAccess();
        Map<Identifier, String> choices = new LinkedHashMap<>();
        access.lookup(key)
                .ifPresent(
                        registry ->
                                registry.listElements()
                                        .forEach(
                                                holder -> {
                                                    var id = holder.key().identifier();
                                                    choices.put(id, registryLabel(id.toString()));
                                                }));
        Identifier candidate = Identifier.fromNamespaceAndPath("ee", "default");
        for (int suffix = 1; choices.containsKey(candidate); suffix++)
            candidate = Identifier.fromNamespaceAndPath("ee", "default_" + suffix);
        Identifier clear = candidate;
        List<Tuple<String, Identifier>> entries = new ArrayList<>();
        entries.add(new Tuple<>("Default", clear));
        choices.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .forEach(
                        entry -> {
                            String label = entry.getValue();
                            if (label.equalsIgnoreCase("Default")
                                    || choices.values().stream()
                                                    .filter(label::equalsIgnoreCase)
                                                    .count()
                                            > 1) label += " (" + entry.getKey() + ")";
                            entries.add(new Tuple<>(label, entry.getKey()));
                        });
        String saved = currentValue(parts);
        mc.gui.setScreen(
                new GuiButtonListSelector<>(
                                this,
                                Component.literal(friendly(meta.label)),
                                entries,
                                value -> {
                                    MobPropertyValues.set(
                                            state.tag,
                                            meta.fieldPath,
                                            value.equals(clear)
                                                    ? null
                                                    : StringTag.valueOf(value.toString()));
                                    return this;
                                })
                        .withCurrent(saved.isEmpty() ? clear : Identifier.tryParse(saved))
                        .withSearchKeys(Identifier::toString));
    }

    private String selectionLabel(FieldMeta meta, String[] parts) {
        String current = currentValue(parts);
        if (meta.options != null)
            for (Option option : meta.options)
                if (Objects.equals(option.value, current)
                        || current.startsWith("minecraft:")
                                && Objects.equals(option.value, current.substring(10)))
                    return option.label == null || option.label.isEmpty()
                            ? "Not set"
                            : option.label;
        return current.isEmpty() ? "Not set" : current;
    }

    private String blockLabel(String[] parts) {
        String value = currentValue(parts);
        if (value.isEmpty()) return "Not set";
        Identifier id = Identifier.tryParse(value);
        return id == null
                ? value
                : BuiltInRegistries.BLOCK
                        .getOptional(id)
                        .map(block -> block.getName().getString())
                        .orElse(value);
    }

    private String currentValue(String[] parts) {
        Tag tag = NbtPath.getTag(state.tag, parts);
        if (tag == null) return "";
        if (tag instanceof StringTag text) return text.value();
        if (tag instanceof NumericTag number) return number.box().toString();
        return tag.toString();
    }

    private void openSelection(FieldMeta meta, String[] parts) {
        List<Tuple<String, String>> options = new ArrayList<>();
        if (meta.options != null)
            for (Option option : meta.options)
                options.add(
                        new Tuple<>(
                                option.label == null || option.label.isEmpty()
                                        ? "Not set"
                                        : option.label,
                                option.value == null ? "" : option.value));
        GuiButtonListSelector<String> picker =
                new GuiButtonListSelector<>(
                                this,
                                Component.literal(friendly(meta.label)),
                                options,
                                selected -> {
                                    CompoundTag p = getOrCreateParent(parts);
                                    String key = parts[parts.length - 1];
                                    if (selected == null || selected.isEmpty())
                                        ItemUtils.remove(p, key);
                                    else if (meta.optionsAllInt && FieldMeta.isIntLike(selected)) {
                                        Tag existing = p.get(key);
                                        int number = Integer.parseInt(selected);
                                        if (meta.definition.has("nbt_type"))
                                            p.put(
                                                    key,
                                                    MobPropertyValues.number(
                                                            selected,
                                                            meta.definition
                                                                    .get("nbt_type")
                                                                    .getAsString(),
                                                            null,
                                                            null));
                                        else if (existing != null
                                                && existing.getId() == Tag.TAG_BYTE)
                                            ItemUtils.putByte(p, key, (byte) number);
                                        else if (existing != null
                                                && existing.getId() == Tag.TAG_SHORT)
                                            ItemUtils.putShort(p, key, (short) number);
                                        else ItemUtils.putInt(p, key, number);
                                    } else ItemUtils.putString(p, key, selected);
                                    return this;
                                })
                        .withCurrent(currentValue(parts));
        JsonElement source = meta.definition.get("options");
        if (source != null && source.isJsonPrimitive() && "#colors16".equals(source.getAsString()))
            picker.withSwatches(
                    value ->
                            value == null || value.isEmpty()
                                    ? null
                                    : net.minecraft.world.item.DyeColor.byId(
                                                    Integer.parseInt(value))
                                            .getTextureDiffuseColor());
        mc.gui.setScreen(picker);
    }

    private @Nullable CompoundTag getParentExisting(String[] parts) {
        return NbtPath.getParentExisting(state.tag, parts);
    }

    private CompoundTag getOrCreateParent(String[] parts) {
        return NbtPath.getOrCreateParent(state.tag, parts);
    }
}
