package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiBooleanButton;
import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import com.dutchmtc.ee.mobdata.NbtPath;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class GuiMobChecklistEditor extends GuiListModifier<CompoundTag> {
    private final MobPropertyEditorState state;
    private final String fieldPath;
    private final String label;
    private final List<GuiMobFieldsEditor.Option> options;
    private final boolean storeInts;
    private final Set<String> originalValues;

    @SuppressWarnings("unchecked")
    public GuiMobChecklistEditor(Screen parent, Consumer<CompoundTag> setter, MobPropertyEditorState state,
                                 String fieldPath, String label, List<GuiMobFieldsEditor.Option> options, boolean storeInts) {
        super(parent, Component.literal(label), new ArrayList<>(), setter, true, true, new Tuple[0]);
        this.state = state;
        this.fieldPath = fieldPath;
        this.label = label;
        this.options = options;
        this.storeInts = storeInts;
        this.originalValues = new LinkedHashSet<>(readCurrentValues());
        rebuildElements();
        setNoAdaptativeSize(true);
        setPaddingTop(8);
    }

    @Override
    public boolean isModified() {
        return !readCurrentValues().equals(originalValues);
    }

    @Override
    protected CompoundTag get() {
        return state.tag;
    }

    @Override
    public void onCancel() {
        writeValues(originalValues);
        getMinecraft().setScreen(parent);
    }

    @Override
    public void init() {
        clearWidgets();

        EEButton done = new EEButton(width / 2 - 50, height - 25, 100, 20, Component.translatable("gui.done"), b -> {
            set(get());
            getMinecraft().setScreen(parent);
        });
        addRenderableWidget(done);

        super.init();
    }

    private void rebuildElements() {
        List<ListElement> current = new ArrayList<>(getElements());
        current.forEach(this::removeListElement);

        if (options == null || options.isEmpty()) {
            addListElement(new InfoElement("No options"));
            return;
        }

        for (GuiMobFieldsEditor.Option opt : options) {
            addListElement(new ChecklistOptionElement(opt));
        }
    }

    private Set<String> readCurrentValues() {
        String[] parts = NbtPath.split(fieldPath);
        Tag t = NbtPath.getTag(state.tag, parts);
        if (!(t instanceof ListTag list)) {
            return new LinkedHashSet<>();
        }
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            Tag entry = list.get(i);
            if (entry instanceof StringTag st) {
                out.add(st.value());
            } else if (entry instanceof NumericTag nt) {
                out.add(String.valueOf(nt.box()));
            } else {
                out.add(entry.toString());
            }
        }
        return out;
    }

    private void writeValues(Set<String> values) {
        String[] parts = NbtPath.split(fieldPath);
        if (parts.length == 0) return;
        CompoundTag parentTag = NbtPath.getOrCreateParent(state.tag, parts);
        String key = parts[parts.length - 1];

        if (values == null || values.isEmpty()) {
            ItemUtils.remove(parentTag, key);
            return;
        }

        ListTag list = new ListTag();
        for (String v : values) {
            if (v == null || v.isEmpty()) continue;
            if (storeInts) {
                try {
                    list.add(IntTag.valueOf(Integer.parseInt(v)));
                } catch (NumberFormatException e) {
                    list.add(StringTag.valueOf(v));
                }
            } else {
                list.add(StringTag.valueOf(v));
            }
        }
        parentTag.put(key, list);
    }

    private final class InfoElement extends ListElement {
        private final EEButton button;

        InfoElement(String text) {
            super(201, 21);
            button = new EEButton(0, 0, 200, 20, Component.literal(text), b -> {});
            button.active = false;
            buttonList.add(button);
        }

        @Override
        public boolean match(String search) {
            return true;
        }
    }

    private final class ChecklistOptionElement extends ListElement {
        private final GuiMobFieldsEditor.Option option;
        private final GuiBooleanButton toggle;

        ChecklistOptionElement(GuiMobFieldsEditor.Option option) {
            super(201, 21);
            this.option = option;
            String optLabel = option.label() == null || option.label().isEmpty() ? option.value() : option.label();
            toggle = new GuiBooleanButton(0, 0, 200, 20, Component.literal(optLabel),
                    val -> {
                        Set<String> current = readCurrentValues();
                        if (val) current.add(option.value());
                        else current.remove(option.value());
                        writeValues(current);
                    },
                    () -> readCurrentValues().contains(option.value()));
            buttonList.add(toggle);
        }

        @Override
        public boolean match(String search) {
            if (search == null || search.isEmpty()) return true;
            String s = search.toLowerCase(Locale.ROOT);
            return (option.label() != null && option.label().toLowerCase(Locale.ROOT).contains(s))
                    || (option.value() != null && option.value().toLowerCase(Locale.ROOT).contains(s));
        }
    }
}

