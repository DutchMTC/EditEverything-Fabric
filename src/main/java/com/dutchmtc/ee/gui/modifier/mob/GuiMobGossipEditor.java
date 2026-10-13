package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EESettingsScreen;
import com.dutchmtc.ee.mobdata.MobPropertyValues;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;

import java.util.*;
import java.util.function.Consumer;

/** Villager reputation entries with native player references and gossip types. */
public final class GuiMobGossipEditor extends EESettingsScreen<ListTag> {
    private final ListTag original;
    private final List<Tag> entries = new ArrayList<>();

    public GuiMobGossipEditor(Screen parent, ListTag list, Consumer<ListTag> setter) {
        super(parent, "Villager reputation", setter);
        original = list.copy();
        list.forEach(t -> entries.add(t.copy()));
    }

    @Override
    protected void buildRows() {
        for (int i = 0; i < entries.size(); i++) {
            int index = i;
            Tag tag = entries.get(i);
            String label =
                    tag instanceof CompoundTag c
                            ? friendly(c.getString("Type").orElse("Imported entry"))
                            : "Imported entry";
            row(
                    label,
                    "Edit this player's reputation entry.",
                    action(
                            "Edit",
                            () -> {
                                if (tag instanceof CompoundTag c)
                                    mc.gui.setScreen(
                                            new Entry(
                                                            this,
                                                            c,
                                                            out -> entries.set(index, out.copy()))
                                                    .restrict(out -> duplicate(out, index)));
                            }),
                    100,
                    () -> {
                        entries.remove(index);
                        rebuild();
                    });
        }
        row(
                "",
                "Add a reputation entry for a player.",
                action(
                        "Add reputation",
                        () ->
                                mc.gui.setScreen(
                                        new Entry(
                                                        this,
                                                        new CompoundTag(),
                                                        out -> entries.add(out.copy()))
                                                .restrict(out -> duplicate(out, -1)))),
                156);
    }

    @Override
    protected ListTag result() {
        var result = new ListTag();
        entries.forEach(t -> result.add(t.copy()));
        return result;
    }

    @Override
    public boolean isModified() {
        return !original.equals(result());
    }

    private String duplicate(CompoundTag value, int skip) {
        var id = MobPropertyValues.uuid(value.get("Target"));
        if (id == null) return null;
        for (int i = 0; i < entries.size(); i++)
            if (i != skip
                    && entries.get(i) instanceof CompoundTag entry
                    && id.equals(MobPropertyValues.uuid(entry.get("Target")))
                    && entry.getString("Type")
                            .orElse("")
                            .equals(value.getString("Type").orElse("minor_positive")))
                return "This player already has an entry of this reputation type.";
        return null;
    }

    private static final class Entry extends EESettingsScreen<CompoundTag> {
        private final CompoundTag original, draft;
        private java.util.function.Function<CompoundTag, String> restriction = value -> null;

        Entry restrict(java.util.function.Function<CompoundTag, String> check) {
            restriction = check;
            return this;
        }

        private static final List<String> TYPES =
                List.of(
                        "major_positive",
                        "minor_positive",
                        "trading",
                        "minor_negative",
                        "major_negative");

        Entry(Screen parent, CompoundTag source, Consumer<CompoundTag> setter) {
            super(parent, "Player reputation", setter);
            original = source.copy();
            draft = source.copy();
        }

        @Override
        protected void buildRows() {
            var id = MobPropertyValues.uuid(draft.get("Target"));
            row(
                    "Player",
                    "Whose reputation this entry affects.",
                    action(
                            id == null ? "Choose player" : "Change player",
                            () ->
                                    mc.gui.setScreen(
                                            new GuiMobPlayerEditor(
                                                    this,
                                                    "Reputation player",
                                                    id,
                                                    value -> {
                                                        if (value == null) draft.remove("Target");
                                                        else
                                                            draft.put(
                                                                    "Target",
                                                                    MobPropertyValues.uuid(value));
                                                    }))),
                    156);
            String type = draft.getString("Type").orElse("minor_positive");
            row(
                    "Reputation",
                    "Positive reputation lowers prices; negative reputation increases them.",
                    dropdown(
                            friendly(type),
                            TYPES,
                            EESettingsScreen::friendly,
                            v -> draft.putString("Type", v)),
                    156);
            int maximum =
                    Arrays.stream(net.minecraft.world.entity.ai.gossip.GossipType.values())
                            .filter(t -> t.id.equals(type))
                            .mapToInt(t -> t.max)
                            .findFirst()
                            .orElse(200);
            row(
                    "Strength",
                    "Amount of this reputation, up to "
                            + maximum
                            + " for the selected type. Minecraft applies its normal decay.",
                    number(
                            "value",
                            Integer.toString(draft.getInt("Value").orElse(1)),
                            1,
                            maximum,
                            true,
                            v -> draft.putInt("Value", (int) v)),
                    120);
        }

        @Override
        protected String invalidReason() {
            String error =
                    MobPropertyValues.uuid(draft.get("Target")) == null
                            ? "Choose a player before saving."
                            : !TYPES.contains(draft.getString("Type").orElse("minor_positive"))
                                    ? "Choose a supported reputation type."
                                    : super.invalidReason();
            return error != null ? error : restriction.apply(draft);
        }

        @Override
        protected CompoundTag result() {
            var result = draft.copy();
            if (!result.contains("Type")) result.putString("Type", "minor_positive");
            if (!result.contains("Value")) result.putInt("Value", 1);
            return result;
        }

        @Override
        public boolean isModified() {
            return !original.equals(result()) || !errors.isEmpty();
        }
    }
}
