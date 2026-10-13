package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EESettingsScreen;
import com.dutchmtc.ee.gui.selector.GuiResourcePicker;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.Consumer;

/** Mooshroom stew effects support native effect IDs and durations, without unsupported levels. */
public final class GuiMobStewEffectsEditor extends EESettingsScreen<ListTag> {
    private final ListTag original;
    private final List<Tag> entries = new ArrayList<>();

    public GuiMobStewEffectsEditor(Screen parent, ListTag list, Consumer<ListTag> setter) {
        super(parent, "Suspicious stew effects", setter);
        original = list.copy();
        list.forEach(t -> entries.add(t.copy()));
    }

    @Override
    protected void buildRows() {
        for (int i = 0; i < entries.size(); i++) {
            int index = i;
            Tag entry = entries.get(i);
            String label =
                    entry instanceof CompoundTag c
                            ? c.getString("id").orElse("Imported effect")
                            : "Imported effect";
            var id = Identifier.tryParse(label);
            if (id != null)
                label =
                        BuiltInRegistries.MOB_EFFECT
                                .getOptional(id)
                                .map(e -> e.getDisplayName().getString())
                                .orElse(label);
            row(
                    label,
                    "Effect given by the next suspicious stew milked from this mooshroom.",
                    action(
                            "Edit",
                            () -> {
                                if (entry instanceof CompoundTag c)
                                    mc.gui.setScreen(
                                            new Entry(
                                                    this,
                                                    c,
                                                    out -> entries.set(index, out.copy())));
                            }),
                    100,
                    () -> {
                        entries.remove(index);
                        rebuild();
                    });
        }
        row(
                "",
                "Choose an effect for the next suspicious stew.",
                action(
                        "Add effect",
                        () ->
                                mc.gui.setScreen(
                                        new Entry(
                                                this,
                                                new CompoundTag(),
                                                out -> entries.add(out.copy())))),
                156);
    }

    @Override
    protected ListTag result() {
        var list = new ListTag();
        entries.forEach(t -> list.add(t.copy()));
        return list;
    }

    @Override
    public boolean isModified() {
        return !original.equals(result());
    }

    private static final class Entry extends EESettingsScreen<CompoundTag> {
        private final CompoundTag original, draft;

        Entry(Screen parent, CompoundTag source, Consumer<CompoundTag> setter) {
            super(parent, "Stew effect", setter);
            original = source.copy();
            draft = source.copy();
        }

        @Override
        protected void buildRows() {
            Identifier selected = Identifier.tryParse(draft.getString("id").orElse(""));
            String name =
                    selected == null
                            ? "Choose effect"
                            : BuiltInRegistries.MOB_EFFECT
                                    .getOptional(selected)
                                    .map(e -> e.getDisplayName().getString())
                                    .orElse(selected.toString());
            row(
                    "Effect",
                    "Choose a registered Minecraft effect.",
                    action(
                            name,
                            () -> {
                                Map<Identifier, String> choices = new LinkedHashMap<>();
                                BuiltInRegistries.MOB_EFFECT.forEach(
                                        e ->
                                                choices.put(
                                                        BuiltInRegistries.MOB_EFFECT.getKey(e),
                                                        e.getDisplayName().getString()));
                                mc.gui.setScreen(
                                        new GuiResourcePicker(
                                                this,
                                                "Stew effect",
                                                choices,
                                                selected,
                                                id -> draft.putString("id", id.toString()),
                                                "effect"));
                            }),
                    156);
            row(
                    "Duration (seconds)",
                    "How long the effect lasts after eating the stew.",
                    number(
                            "duration",
                            Double.toString(draft.getInt("duration").orElse(160) / 20.0),
                            .05,
                            Integer.MAX_VALUE / 20.0,
                            false,
                            v -> draft.putInt("duration", (int) Math.round(v * 20))),
                    120);
        }

        @Override
        protected String invalidReason() {
            var id = Identifier.tryParse(draft.getString("id").orElse(""));
            return id == null || BuiltInRegistries.MOB_EFFECT.getOptional(id).isEmpty()
                    ? "Choose an effect before saving."
                    : super.invalidReason();
        }

        @Override
        protected CompoundTag result() {
            var result = draft.copy();
            if (!result.contains("duration")) result.putInt("duration", 160);
            return result;
        }

        @Override
        public boolean isModified() {
            return !original.equals(result()) || !errors.isEmpty();
        }
    }
}
