package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EESettingsScreen;
import com.dutchmtc.ee.mobdata.MobPropertyValues;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;

import java.util.*;
import java.util.function.Consumer;

/** Warden anger is per target, unlike the single-target anger shared by other neutral mobs. */
public final class GuiMobAngerEditor extends EESettingsScreen<ListTag> {
    private final ListTag original;
    private final List<Tag> targets = new ArrayList<>();

    public GuiMobAngerEditor(Screen parent, ListTag list, Consumer<ListTag> setter) {
        super(parent, "Warden anger", setter);
        original = list.copy();
        list.forEach(t -> targets.add(t.copy()));
    }

    @Override
    protected void buildRows() {
        for (int i = 0; i < targets.size(); i++) {
            int index = i;
            Tag tag = targets.get(i);
            String label =
                    tag instanceof CompoundTag c
                            ? "Anger: " + c.getInt("anger").orElse(0)
                            : "Imported target";
            row(
                    label,
                    "Edit this target's anger. The warden attacks its angriest target once anger"
                        + " reaches 80.",
                    action(
                            "Edit",
                            () -> {
                                if (tag instanceof CompoundTag c)
                                    mc.gui.setScreen(
                                            new Entry(
                                                            this,
                                                            c,
                                                            out -> targets.set(index, out.copy()))
                                                    .restrict(out -> duplicate(out, index)));
                            }),
                    100,
                    () -> {
                        targets.remove(index);
                        rebuild();
                    });
        }
        row(
                "",
                "Add a target this warden is angry with.",
                action(
                        "Add target",
                        () ->
                                mc.gui.setScreen(
                                        new Entry(
                                                        this,
                                                        new CompoundTag(),
                                                        out -> targets.add(out.copy()))
                                                .restrict(out -> duplicate(out, -1)))),
                156);
    }

    @Override
    protected ListTag result() {
        var list = new ListTag();
        targets.forEach(t -> list.add(t.copy()));
        return list;
    }

    @Override
    public boolean isModified() {
        return !original.equals(result());
    }

    private String duplicate(CompoundTag value, int skip) {
        var id = MobPropertyValues.uuid(value.get("uuid"));
        if (id == null) return null;
        for (int i = 0; i < targets.size(); i++)
            if (i != skip
                    && targets.get(i) instanceof CompoundTag entry
                    && id.equals(MobPropertyValues.uuid(entry.get("uuid"))))
                return "This target is already in the anger list.";
        return null;
    }

    private static final class Entry extends EESettingsScreen<CompoundTag> {
        private final CompoundTag original, draft;
        private java.util.function.Function<CompoundTag, String> restriction = value -> null;

        Entry restrict(java.util.function.Function<CompoundTag, String> check) {
            restriction = check;
            return this;
        }

        Entry(Screen parent, CompoundTag source, Consumer<CompoundTag> setter) {
            super(parent, "Anger target", setter);
            original = source.copy();
            draft = source.copy();
        }

        @Override
        protected void buildRows() {
            var id = MobPropertyValues.uuid(draft.get("uuid"));
            row(
                    "Target",
                    "Choose a player, or enter the UUID of another entity.",
                    action(
                            id == null ? "Choose target" : "Change target",
                            () ->
                                    mc.gui.setScreen(
                                            new GuiMobPlayerEditor(
                                                    this,
                                                    "Anger target",
                                                    id,
                                                    value -> {
                                                        if (value == null) draft.remove("uuid");
                                                        else
                                                            draft.put(
                                                                    "uuid",
                                                                    MobPropertyValues.uuid(value));
                                                    }))),
                    156);
            row(
                    "Anger",
                    "0 is calm, 80 triggers attacks, and 150 is the normal maximum.",
                    number(
                            "anger",
                            Integer.toString(draft.getInt("anger").orElse(80)),
                            0,
                            150,
                            true,
                            v -> draft.putInt("anger", (int) v)),
                    120);
        }

        @Override
        protected String invalidReason() {
            String error =
                    MobPropertyValues.uuid(draft.get("uuid")) == null
                            ? "Choose a target before saving."
                            : super.invalidReason();
            return error != null ? error : restriction.apply(draft);
        }

        @Override
        protected CompoundTag result() {
            var result = draft.copy();
            if (!result.contains("anger")) result.putInt("anger", 80);
            return result;
        }

        @Override
        public boolean isModified() {
            return !original.equals(result()) || !errors.isEmpty();
        }
    }
}
