package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EESettingsScreen;
import com.dutchmtc.ee.mobdata.MobPropertyValues;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;

import java.util.*;
import java.util.function.Consumer;

/** Native UUID lists; imported entries survive unless explicitly edited or removed. */
public final class GuiMobPlayersEditor extends EESettingsScreen<ListTag> {
    private final ListTag original;
    private final List<Tag> entries = new ArrayList<>();
    private final int maximum;

    public GuiMobPlayersEditor(
            Screen parent, String title, ListTag list, int maximum, Consumer<ListTag> setter) {
        super(parent, title, setter);
        this.maximum = maximum;
        original = list.copy();
        list.forEach(t -> entries.add(t.copy()));
    }

    @Override
    protected void buildRows() {
        for (int i = 0; i < entries.size(); i++) {
            int index = i;
            UUID id = MobPropertyValues.uuid(entries.get(i));
            String name = id == null ? "Imported entry" : "Choose player";
            if (id != null && mc.getConnection() != null)
                for (var info : mc.getConnection().getOnlinePlayers())
                    if (info.getProfile().id().equals(id)) name = info.getProfile().name();
            row(
                    "Player " + (i + 1),
                    id == null
                            ? "This imported value is preserved until you edit or remove it."
                            : id.toString(),
                    action(
                            name,
                            () ->
                                    mc.gui.setScreen(
                                            new GuiMobPlayerEditor(
                                                            this,
                                                            "Trusted player",
                                                            id,
                                                            value -> {
                                                                if (value == null)
                                                                    entries.remove(index);
                                                                else
                                                                    entries.set(
                                                                            index,
                                                                            MobPropertyValues.uuid(
                                                                                    value));
                                                            })
                                                    .restrict(
                                                            value ->
                                                                    value != null
                                                                                    && java.util
                                                                                            .stream
                                                                                            .IntStream
                                                                                            .range(
                                                                                                    0,
                                                                                                    entries
                                                                                                            .size())
                                                                                            .anyMatch(
                                                                                                    j ->
                                                                                                            j
                                                                                                                            != index
                                                                                                                    && value
                                                                                                                            .equals(
                                                                                                                                    MobPropertyValues
                                                                                                                                            .uuid(
                                                                                                                                                    entries
                                                                                                                                                            .get(
                                                                                                                                                                    j))))
                                                                            ? "This player is"
                                                                                  + " already"
                                                                                  + " trusted."
                                                                            : null))),
                    156,
                    () -> {
                        entries.remove(index);
                        rebuild();
                    });
        }
        var add =
                action(
                        "Add player",
                        () ->
                                mc.gui.setScreen(
                                        new GuiMobPlayerEditor(
                                                        this,
                                                        "Trusted player",
                                                        null,
                                                        value -> {
                                                            if (value != null
                                                                    && entries.size() < maximum
                                                                    && entries.stream()
                                                                            .noneMatch(
                                                                                    t ->
                                                                                            value
                                                                                                    .equals(
                                                                                                            MobPropertyValues
                                                                                                                    .uuid(
                                                                                                                            t))))
                                                                entries.add(
                                                                        MobPropertyValues.uuid(
                                                                                value));
                                                        })
                                                .restrict(
                                                        value ->
                                                                value == null
                                                                        ? "Choose a player before"
                                                                              + " adding."
                                                                        : entries.stream()
                                                                                        .anyMatch(
                                                                                                t ->
                                                                                                        value
                                                                                                                .equals(
                                                                                                                        MobPropertyValues
                                                                                                                                .uuid(
                                                                                                                                        t)))
                                                                                ? "This player is"
                                                                                      + " already"
                                                                                      + " trusted."
                                                                                : null)));
        add.active = entries.size() < maximum;
        row(
                "",
                add.active
                        ? "Add a player this mob trusts."
                        : "Minecraft supports up to " + maximum + " trusted players.",
                add,
                156);
    }

    @Override
    protected ListTag result() {
        var result = new ListTag();
        entries.forEach(t -> result.add(t.copy()));
        return result;
    }

    @Override
    protected int preferredWidth() {
        return 320;
    }

    @Override
    public boolean isModified() {
        return !original.equals(result());
    }
}
