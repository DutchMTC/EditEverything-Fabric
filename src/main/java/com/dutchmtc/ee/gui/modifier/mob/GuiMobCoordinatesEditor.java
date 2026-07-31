package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.mobdata.NbtPath;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class GuiMobCoordinatesEditor extends Screen {
    private enum Mode {
        COMPOUND,
        LIST
    }

    private final Screen parent;
    private final Consumer<CompoundTag> setter;
    private final MobPropertyEditorState state;
    private final String fieldPath;
    private final String label;

    private final String[] parts;
    private Mode mode = Mode.COMPOUND;
    private String keyX = "X";
    private String keyY = "Y";
    private String keyZ = "Z";

    private EditBox xField;
    private EditBox yField;
    private EditBox zField;

    private int originalX;
    private int originalY;
    private int originalZ;

    public GuiMobCoordinatesEditor(Screen parent, Consumer<CompoundTag> setter, MobPropertyEditorState state,
                                   String fieldPath, String label) {
        super(Component.literal(label));
        this.parent = parent;
        this.setter = setter;
        this.state = state;
        this.fieldPath = fieldPath;
        this.label = label;
        this.parts = NbtPath.split(fieldPath);
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int top = 35;

        int[] xyz = read();
        originalX = xyz[0];
        originalY = xyz[1];
        originalZ = xyz[2];

        xField = new EditBox(font, cx - 100, top, 200, 20, Component.literal("X"));
        yField = new EditBox(font, cx - 100, top + 24, 200, 20, Component.literal("Y"));
        zField = new EditBox(font, cx - 100, top + 48, 200, 20, Component.literal("Z"));

        xField.setValue(String.valueOf(originalX));
        yField.setValue(String.valueOf(originalY));
        zField.setValue(String.valueOf(originalZ));

        xField.setResponder(v -> writeFromInputs());
        yField.setResponder(v -> writeFromInputs());
        zField.setResponder(v -> writeFromInputs());

        addRenderableWidget(xField);
        addRenderableWidget(yField);
        addRenderableWidget(zField);

        addRenderableWidget(new EEButton(cx - 100, height - 25, 100, 20, Component.translatable("gui.ee.cancel"),
                b -> onCancel()));
        addRenderableWidget(new EEButton(cx + 1, height - 25, 99, 20, Component.translatable("gui.done"),
                b -> {
                    setter.accept(state.tag);
                    if (minecraft != null) {
                        minecraft.gui.setScreen(parent);
                    }
                }));
    }

    private void onCancel() {
        // revert this field only
        write(originalX, originalY, originalZ);
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private int[] read() {
        Tag t = NbtPath.getTag(state.tag, parts);
        if (t instanceof ListTag list && list.size() >= 3) {
            mode = Mode.LIST;
            return new int[] { readNum(list.get(0)), readNum(list.get(1)), readNum(list.get(2)) };
        }
        if (t instanceof CompoundTag ct) {
            mode = Mode.COMPOUND;
            keyX = pickKey(ct, "X", "x");
            keyY = pickKey(ct, "Y", "y");
            keyZ = pickKey(ct, "Z", "z");
            return new int[] { ItemUtils.getInt(ct, keyX), ItemUtils.getInt(ct, keyY), ItemUtils.getInt(ct, keyZ) };
        }
        mode = Mode.COMPOUND;
        return new int[] { 0, 0, 0 };
    }

    private static int readNum(Tag t) {
        if (t instanceof NumericTag nt) {
            return nt.intValue();
        }
        return 0;
    }

    private static String pickKey(CompoundTag tag, String preferred, String fallback) {
        if (tag.getInt(preferred).isPresent()) return preferred;
        if (tag.getInt(fallback).isPresent()) return fallback;
        if (tag.contains(preferred)) return preferred;
        if (tag.contains(fallback)) return fallback;
        return preferred;
    }

    private void writeFromInputs() {
        int x = parseIntOr(xField.getValue(), originalX, xField);
        int y = parseIntOr(yField.getValue(), originalY, yField);
        int z = parseIntOr(zField.getValue(), originalZ, zField);
        write(x, y, z);
    }

    private static int parseIntOr(String s, int fallback, EditBox box) {
        if (s == null || s.isEmpty() || "-".equals(s)) {
            box.setTextColor(0xE0E0E0);
            return fallback;
        }
        try {
            int v = Integer.parseInt(s);
            box.setTextColor(0xE0E0E0);
            return v;
        } catch (NumberFormatException e) {
            box.setTextColor(0xFF0000);
            return fallback;
        }
    }

    private void write(int x, int y, int z) {
        if (parts.length == 0) return;
        CompoundTag parentTag = NbtPath.getOrCreateParent(state.tag, parts);
        String key = parts[parts.length - 1];

        if (mode == Mode.LIST) {
            ListTag list = new ListTag();
            list.add(IntTag.valueOf(x));
            list.add(IntTag.valueOf(y));
            list.add(IntTag.valueOf(z));
            parentTag.put(key, list);
            return;
        }

        CompoundTag coords = new CompoundTag();
        ItemUtils.putInt(coords, keyX, x);
        ItemUtils.putInt(coords, keyY, y);
        ItemUtils.putInt(coords, keyZ, z);
        parentTag.put(key, coords);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        GuiUtils.drawCenterString(graphics, font, label, width / 2, 10, 0xFFFFFFFF);
    }
}

