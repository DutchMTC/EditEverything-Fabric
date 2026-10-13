package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EESettingsScreen;
import com.dutchmtc.ee.mobdata.MobPropertyValues;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.function.Consumer;

/** Optional native BlockPos values, with isolated drafts and an explicit clear action. */
public final class GuiMobPositionEditor extends EESettingsScreen<Tag> {
    private final Tag original;
    private boolean present, edited;
    private final int[] position;

    public GuiMobPositionEditor(Screen parent, String title, Tag current, Consumer<Tag> setter) {
        super(parent, title, setter);
        original = current == null ? null : current.copy();
        present = current != null;
        position = MobPropertyValues.position(current);
    }

    @Override
    protected void buildRows() {
        row(
                "Position",
                "Choose a custom block position, or leave it unset.",
                dropdown(
                        present ? "Custom" : "Not set",
                        List.of(false, true),
                        v -> v ? "Custom" : "Not set",
                        v -> {
                            present = v;
                            edited = true;
                        }),
                140);
        if (!present) return;
        String[] names = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            int axis = i;
            row(
                    names[i],
                    "Block coordinate on the " + names[i] + " axis.",
                    number(
                            names[i],
                            Integer.toString(position[i]),
                            Integer.MIN_VALUE,
                            Integer.MAX_VALUE,
                            true,
                            v -> {
                                position[axis] = (int) v;
                                edited = true;
                            }),
                    120);
        }
        row(
                "",
                "Use your current block position.",
                action(
                        "My position",
                        () -> {
                            if (mc.player == null) return;
                            var pos = mc.player.blockPosition();
                            position[0] = pos.getX();
                            position[1] = pos.getY();
                            position[2] = pos.getZ();
                            for (int i = 0; i < 3; i++) {
                                values.put(names[i], Integer.toString(position[i]));
                                errors.remove(names[i]);
                            }
                            edited = true;
                            rebuild();
                        }),
                140);
    }

    @Override
    protected boolean relevant(String key) {
        return present;
    }

    @Override
    protected int preferredWidth() {
        return 320;
    }

    @Override
    protected Tag result() {
        return !edited
                ? original == null ? null : original.copy()
                : present
                        ? MobPropertyValues.position(position[0], position[1], position[2])
                        : null;
    }

    @Override
    public boolean isModified() {
        return !java.util.Objects.equals(original, result()) || !errors.isEmpty();
    }
}
