package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.mobdata.NbtPath;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuiMobAdditiveMatrixEditor extends Screen {
    public record Group(String label, List<Option> options, int mask) {
    }

    public record Option(String label, int value) {
    }

    private final Screen parent;
    private final Consumer<CompoundTag> setter;
    private final MobPropertyEditorState state;
    private final String fieldPath;
    private final String label;
    private final List<Group> groups;
    private final int defaultValue;

    private final String[] parts;
    private final int[] selection;

    public GuiMobAdditiveMatrixEditor(Screen parent, Consumer<CompoundTag> setter, MobPropertyEditorState state,
                                      String fieldPath, String label, List<Group> groups, int defaultValue) {
        super(Component.literal(label));
        this.parent = parent;
        this.setter = setter;
        this.state = state;
        this.fieldPath = fieldPath;
        this.label = label;
        this.groups = groups;
        this.defaultValue = defaultValue;
        this.parts = NbtPath.split(fieldPath);
        this.selection = new int[Math.max(0, groups == null ? 0 : groups.size())];
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2;
        int y = 35;

        int current = readCurrentValue();
        initSelectionFromValue(current);

        if (groups == null || groups.isEmpty()) {
            addRenderableWidget(new EEButton(cx - 100, y, 200, 20, Component.literal("No groups"), b -> {}));
        } else {
            for (int i = 0; i < groups.size(); i++) {
                int idx = i;
                Group g = groups.get(i);
                EEButton btn = new EEButton(cx - 100, y + i * 24, 200, 20, Component.literal(""), b -> openGroupSelector(idx));
                addRenderableWidget(btn);
                refreshGroupButton(btn, idx);
            }
        }

        addRenderableWidget(new EEButton(cx - 100, height - 25, 100, 20, Component.translatable("gui.ee.cancel"),
                b -> onCancel()));
        addRenderableWidget(new EEButton(cx + 1, height - 25, 99, 20, Component.translatable("gui.done"),
                b -> {
                    writeCurrentValue(computeValue());
                    setter.accept(state.tag);
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                    }
                }));
    }

    private void onCancel() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void refreshGroupButton(EEButton btn, int groupIndex) {
        Group g = groups.get(groupIndex);
        int v = selection[groupIndex];
        String optLabel = String.valueOf(v);
        for (Option o : g.options()) {
            if (o.value() == v) {
                optLabel = o.label();
                break;
            }
        }
        btn.setMessage(Component.literal(g.label() + ": " + optLabel));
    }

    private void openGroupSelector(int groupIndex) {
        Group g = groups.get(groupIndex);
        List<Tuple<String, Integer>> opts = new ArrayList<>();
        for (Option o : g.options()) {
            opts.add(new Tuple<>(o.label(), o.value()));
        }
        if (minecraft == null) return;
        minecraft.setScreen(new GuiButtonListSelector<>(this,
                Component.literal(g.label()), opts, val -> {
                    selection[groupIndex] = val == null ? 0 : val;
                    writeCurrentValue(computeValue());
                    setter.accept(state.tag);
                    init(); // rebuild labels
                    return this;
                }));
    }

    private int computeValue() {
        int out = 0;
        for (int v : selection) {
            out |= v;
        }
        return out;
    }

    private int readCurrentValue() {
        if (parts.length == 0) return defaultValue;
        CompoundTag parentTag = NbtPath.getParentExisting(state.tag, parts);
        if (parentTag == null) return defaultValue;
        return ItemUtils.getInt(parentTag, parts[parts.length - 1]);
    }

    private void writeCurrentValue(int value) {
        if (parts.length == 0) return;
        CompoundTag parentTag = NbtPath.getOrCreateParent(state.tag, parts);
        ItemUtils.putInt(parentTag, parts[parts.length - 1], value);
    }

    private void initSelectionFromValue(int value) {
        if (groups == null) return;
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            int masked = value & g.mask();
            int selected = g.options().isEmpty() ? 0 : g.options().get(0).value();
            for (Option o : g.options()) {
                if (o.value() == masked) {
                    selected = o.value();
                    break;
                }
            }
            selection[i] = selected;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(graphics, mouseX, mouseY, delta);
        GuiUtils.drawCenterString(graphics, font, label, width / 2, 10, 0xFFFFFFFF);
    }
}
