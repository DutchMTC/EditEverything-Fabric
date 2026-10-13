package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EESettingsScreen;
import com.dutchmtc.ee.mobdata.MobPropertyValues;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;

import java.util.*;
import java.util.function.Consumer;

/** Leashes use either a holder UUID compound or a native fence BlockPos array. */
public final class GuiMobLeashEditor extends EESettingsScreen<Tag> {
    private final Tag original;
    private int mode;
    private UUID holder;
    private Tag fence;
    private boolean edited;

    public GuiMobLeashEditor(Screen parent, Tag source, Consumer<Tag> setter) {
        super(parent, "Leash", setter);
        original = source == null ? null : source.copy();
        if (source instanceof CompoundTag c) {
            holder = MobPropertyValues.uuid(c.get("UUID"));
            mode = holder == null ? 4 : 1;
        } else if (source instanceof IntArrayTag a && a.getAsIntArray().length == 3) {
            mode = 3;
            fence = source.copy();
        } else mode = source == null ? 0 : 4;
    }

    @Override
    protected int preferredWidth() {
        return 320;
    }

    @Override
    protected void buildRows() {
        var names = List.of("Not leashed", "Player", "Mob", "Fence", "Imported value");
        row(
                "Attach to",
                "The holder or fence must exist in the world where this mob is placed.",
                dropdown(
                        names.get(mode),
                        List.of(0, 1, 2, 3),
                        names::get,
                        value -> {
                            mode = value;
                            edited = true;
                        }),
                156);
        if (mode == 1 || mode == 2)
            row(
                    "Holder",
                    "Choose a nearby holder or enter its UUID.",
                    action(
                            holder == null ? "Choose holder" : "Change holder",
                            () ->
                                    mc.gui.setScreen(
                                            new GuiMobPlayerEditor(
                                                            this,
                                                            "Leash holder",
                                                            holder,
                                                            value -> {
                                                                holder = value;
                                                                edited = true;
                                                            },
                                                            mode == 2)
                                                    .restrict(
                                                            value ->
                                                                    value == null
                                                                            ? "Choose a holder"
                                                                                  + " before"
                                                                                  + " saving."
                                                                            : null))),
                    156);
        if (mode == 3)
            row(
                    "Fence position",
                    "Coordinates of an existing fence in the same dimension.",
                    action(
                            fence == null ? "Choose position" : "Change position",
                            () ->
                                    mc.gui.setScreen(
                                            new GuiMobPositionEditor(
                                                    this,
                                                    "Fence position",
                                                    fence,
                                                    value -> {
                                                        fence = value == null ? null : value.copy();
                                                        edited = true;
                                                    }))),
                    156);
    }

    @Override
    protected String invalidReason() {
        if ((mode == 1 || mode == 2) && holder == null) return "Choose a leash holder.";
        if (mode == 3 && fence == null) return "Choose a fence position.";
        if (mode == 3 && mc.level != null) {
            var xyz = MobPropertyValues.position(fence);
            var pos = new net.minecraft.core.BlockPos(xyz[0], xyz[1], xyz[2]);
            if (mc.level.hasChunkAt(pos)
                    && !mc.level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FENCES))
                return "This loaded position is not a fence.";
        }
        return super.invalidReason();
    }

    @Override
    protected Tag result() {
        if (!edited) return original == null ? null : original.copy();
        if (mode == 3) return fence == null ? null : fence.copy();
        if ((mode == 1 || mode == 2) && holder != null) {
            var value = new CompoundTag();
            value.put("UUID", MobPropertyValues.uuid(holder));
            return value;
        }
        return null;
    }

    @Override
    public boolean isModified() {
        return !Objects.equals(original, result())
                || edited && (mode == 1 || mode == 2) && holder == null;
    }
}
