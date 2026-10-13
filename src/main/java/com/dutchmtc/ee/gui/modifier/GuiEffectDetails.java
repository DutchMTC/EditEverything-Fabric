package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EESettingsScreen;
import com.dutchmtc.ee.gui.selector.GuiResourcePicker;
import com.dutchmtc.ee.utils.EffectEditorData;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.Consumer;

/** Shared focused setup for item and entity effects, with human-facing levels and seconds. */
final class GuiEffectDetails extends EESettingsScreen<EffectEditorData> {
    private EffectEditorData draft;
    private final CompoundTag original;
    private final boolean adding;
    private boolean infinite;
    private int timedTicks;
    private final Consumer<EffectEditorData> duplicate;
    private EEButton copy;

    GuiEffectDetails(
            Screen parent,
            EffectEditorData entry,
            Consumer<EffectEditorData> setter,
            Consumer<EffectEditorData> duplicate) {
        super(parent, entry == null ? "Add effect" : "Edit effect", setter);
        adding = entry == null;
        this.duplicate = duplicate;
        if (entry == null) {
            CompoundTag empty = new CompoundTag();
            empty.putInt("duration", 1200);
            draft = new EffectEditorData(empty);
        } else draft = entry.copy();
        original = draft.tag();
        infinite = draft.ticks == -1;
        timedTicks = infinite ? 1200 : draft.ticks;
        if (draft.level < 1 || draft.level > 256)
            errors.put("level", "Enter a level from 1 to 256.");
        if (draft.ticks < -1) errors.put("duration", "Enter a positive duration.");
    }

    @Override
    public void init() {
        copy = null;
        super.init();
        if (!adding)
            copy =
                    addRenderableWidget(
                            new EEButton(
                                    left + panelWidth - 82,
                                    top + 9,
                                    70,
                                    20,
                                    Component.literal("Duplicate"),
                                    b -> {
                                        if (invalidReason() != null) return;
                                        duplicate.accept(result());
                                        mc.gui.setScreen(parent);
                                    }));
        refreshValidity();
    }

    @Override
    protected void refreshValidity() {
        super.refreshValidity();
        if (copy != null) {
            String error = invalidReason();
            copy.active = error == null;
            copy.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    error == null
                                            ? "Add a separate copy with these settings. The"
                                                  + " original effect stays unchanged."
                                            : error)));
        }
    }

    @Override
    protected String applyLabel() {
        return adding ? "Add effect" : "Save effect";
    }

    @Override
    protected EffectEditorData result() {
        return draft.copy();
    }

    @Override
    public boolean isModified() {
        return !draft.tag().equals(original) || !errors.isEmpty();
    }

    @Override
    protected String invalidReason() {
        String reason = super.invalidReason();
        return reason == null ? draft.invalidReason() : reason;
    }

    @Override
    protected boolean relevant(String key) {
        return !key.equals("duration") || !infinite;
    }

    private void choose() {
        Map<Identifier, String> choices = new LinkedHashMap<>();
        BuiltInRegistries.MOB_EFFECT.forEach(
                e ->
                        choices.put(
                                BuiltInRegistries.MOB_EFFECT.getKey(e),
                                e.getDisplayName().getString()));
        mc.gui.setScreen(
                new GuiResourcePicker(
                        this,
                        "Choose effect",
                        choices,
                        Identifier.tryParse(draft.id),
                        id -> draft.id = id.toString(),
                        "effect"));
    }

    @Override
    protected void buildRows() {
        row(
                "Effect",
                "Choose the potion effect to apply.",
                action(draft.effect() == null ? "Choose effect..." : draft.name(), this::choose),
                220);
        row(
                "Level",
                "Level 1 is the standard effect; creative levels can reach 256.",
                number("level", "" + draft.level, 1, 256, true, n -> draft.level = (int) n),
                80);
        row(
                "Duration",
                "Infinite effects last until removed.",
                dropdown(
                        infinite ? "Infinite" : "Timed",
                        List.of(false, true),
                        v -> v ? "Infinite" : "Timed",
                        v -> {
                            if (!infinite) timedTicks = draft.ticks;
                            infinite = v;
                            draft.ticks = v ? -1 : timedTicks;
                        }),
                100);
        if (!infinite)
            row(
                    "Seconds",
                    "20 game ticks equal one second. Decimals are allowed.",
                    number(
                            "duration",
                            EffectEditorData.seconds(draft.ticks),
                            0,
                            Integer.MAX_VALUE / 20.0,
                            false,
                            n -> draft.ticks = (int) Math.round(n * 20)),
                    100);
        row(
                "Particles",
                "Show particles around the entity.",
                toggle(() -> draft.particles, v -> draft.particles = v),
                52);
        row(
                "Status icon",
                "Show this effect in the inventory and on the player's screen.",
                toggle(() -> draft.icon, v -> draft.icon = v),
                52);
        row(
                "Ambient",
                "Use the subtle particles associated with beacon effects.",
                toggle(() -> draft.ambient, v -> draft.ambient = v),
                52);
    }
}
