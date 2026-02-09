package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiBooleanButton;
import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTIntArrayModifier;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.mobdata.FeatureSetRepository;
import com.dutchmtc.ee.mobdata.MobDataRepository;
import com.dutchmtc.ee.mobdata.NbtPath;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class GuiMobFieldsEditor extends GuiListModifier<CompoundTag> {
    private final MobPropertyEditorState state;

    @SuppressWarnings("unchecked")
    public GuiMobFieldsEditor(Screen parent, Consumer<CompoundTag> setter, MobPropertyEditorState state) {
        super(parent, Component.literal("Mob Fields"), new ArrayList<>(), setter, true, true, new Tuple[0]);
        this.state = state;
        setTopControlsHeight(20);
        rebuildElements();
        setPaddingTop(8);
    }

    @Override
    public boolean isModified() {
        return !state.tag.equals(state.originalTag);
    }

    @Override
    protected CompoundTag get() {
        return state.tag;
    }

    @Override
    public void init() {
        clearWidgets();

        int tabW = 110;
        int tabH = 18;
        int x0 = (width - tabW * 2 - 2) / 2;
        int y0 = 0;

        EEButton fieldsTab = new EEButton(x0, y0, tabW, tabH, Component.literal("Fields"), b -> {});
        fieldsTab.active = false;
        addRenderableWidget(fieldsTab);

        EEButton featuresTab = new EEButton(x0 + tabW + 2, y0, tabW, tabH, Component.literal("Feature Sets"), b -> {
            getMinecraft().setScreen(new GuiMobFeatureSetsEditor(parent, setter, state));
        });
        addRenderableWidget(featuresTab);

        super.init();
    }

    private void rebuildElements() {
        // Clear list elements
        // (GuiListModifier holds the backing list, so remove in-place.)
        List<ListElement> current = new ArrayList<>(getElements());
        current.forEach(this::removeListElement);

        JsonObject entity = MobDataRepository.getEntity(state.entityPath);
        if (entity == null) {
            addListElement(new InfoElement("No mob data for: " + state.entityPath));
            return;
        }

        JsonObject entityFields = MobDataRepository.getEntityFields();
        if (entityFields == null) {
            addListElement(new InfoElement("Missing ee mob data (entity_fields)."));
            return;
        }

        LinkedHashMap<String, JsonObject> defs = new LinkedHashMap<>();

        // Explicit fields on the entity
        JsonElement fieldsEl = entity.get("fields");
        addFieldArrayEntries(fieldsEl, entityFields, defs);

        // Feature-set fields (optional; from feature_sets.json + toggles)
        for (String featureSet : state.enabledFeatureSets) {
            for (String ref : FeatureSetRepository.getFeatureSetFieldRefs(featureSet)) {
                addFieldEntries(ref, entityFields, defs);
            }
        }

        if (defs.isEmpty()) {
            addListElement(new InfoElement("No editable fields for: " + state.entityPath));
            return;
        }

        for (JsonObject def : defs.values()) {
            FieldMeta meta = FieldMeta.from(def, entityFields);
            if (meta == null) continue;
            ListElement element = buildElement(meta);
            if (element != null) {
                addListElement(element);
            }
        }
    }

    private static void addFieldArrayEntries(@Nullable JsonElement fieldsEl, JsonObject entityFields, LinkedHashMap<String, JsonObject> out) {
        if (fieldsEl == null || fieldsEl.isJsonNull()) return;
        if (!fieldsEl.isJsonArray()) return;
        JsonArray arr = fieldsEl.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement entry = arr.get(i);
            addFieldEntries(entry, entityFields, out);
        }
    }

    private static void addFieldEntries(@Nullable String refOrNull, JsonObject entityFields, LinkedHashMap<String, JsonObject> out) {
        if (refOrNull == null) return;
        if (!refOrNull.startsWith("#")) return;
        JsonObject def = MobDataRepository.getEntityFieldDef(refOrNull.substring(1));
        if (def == null) return;
        String key = def.has("field") ? def.get("field").getAsString() : refOrNull;
        out.putIfAbsent(key, def);
    }

    private static void addFieldEntries(@Nullable JsonElement entry, JsonObject entityFields, LinkedHashMap<String, JsonObject> out) {
        if (entry == null || entry.isJsonNull()) return;
        if (entry.isJsonPrimitive()) {
            String s = entry.getAsString();
            addFieldEntries(s, entityFields, out);
            return;
        }
        if (!entry.isJsonObject()) return;
        JsonObject def = entry.getAsJsonObject();
        if (!def.has("field")) return;
        String key = def.get("field").getAsString();
        out.putIfAbsent(key, def);
    }

    private @Nullable ListElement buildElement(FieldMeta meta) {
        if ("static".equals(meta.type)) {
            return null;
        }
        if (meta.fieldPath.startsWith("ux.")) {
            return null;
        }

        return switch (meta.type) {
            case "bool" -> new BoolElement(meta);
            case "number" -> new IntElement(meta);
            case "text" -> new TextElement(meta);
            case "select" -> new SelectElement(meta);
            case "int-array" -> new IntArrayElement(meta);
            case "checklist" -> new ChecklistElement(meta);
            case "hr" -> new HrElement(meta);
            case "coordinates" -> new CoordinatesElement(meta);
            case "additive-matrix" -> new AdditiveMatrixElement(meta);
            case "text-autocomplete-blocks" -> new BlockAutocompleteElement(meta);
            default -> new UnsupportedElement(meta);
        };
    }

    private @Nullable CompoundTag getParentExisting(String[] parts) {
        return NbtPath.getParentExisting(state.tag, parts);
    }

    private CompoundTag getOrCreateParent(String[] parts) {
        return NbtPath.getOrCreateParent(state.tag, parts);
    }

    private static final class FieldMeta {
        final String fieldPath;
        final String type;
        final String label;
        final @Nullable String help;
        final @Nullable List<Option> options;
        final boolean optionsAllInt;
        final @Nullable List<GuiMobAdditiveMatrixEditor.Group> matrixGroups;
        final int matrixDefault;

        private FieldMeta(String fieldPath, String type, String label, @Nullable String help,
                          @Nullable List<Option> options, boolean optionsAllInt,
                          @Nullable List<GuiMobAdditiveMatrixEditor.Group> matrixGroups, int matrixDefault) {
            this.fieldPath = fieldPath;
            this.type = type;
            this.label = label;
            this.help = help;
            this.options = options;
            this.optionsAllInt = optionsAllInt;
            this.matrixGroups = matrixGroups;
            this.matrixDefault = matrixDefault;
        }

        static @Nullable FieldMeta from(JsonObject def, JsonObject entityFields) {
            if (!def.has("field")) return null;
            if (!def.has("type")) return null;
            String fieldPath = def.get("field").getAsString();
            String type = def.get("type").getAsString();
            String label = def.has("label") ? def.get("label").getAsString() : fieldPath;
            String help = def.has("help") ? def.get("help").getAsString() : null;

            List<Option> options = null;
            boolean allInt = false;
            if (("select".equals(type) || "checklist".equals(type)) && def.has("options")) {
                JsonElement opt = def.get("options");
                JsonArray optArr = null;
                if (opt.isJsonPrimitive()) {
                    String s = opt.getAsString();
                    if (s.startsWith("#")) {
                        JsonObject ref = MobDataRepository.getEntityFieldDef(s.substring(1));
                        if (ref != null && ref.has("options") && ref.get("options").isJsonArray()) {
                            optArr = ref.getAsJsonArray("options");
                        }
                    }
                } else if (opt.isJsonArray()) {
                    optArr = opt.getAsJsonArray();
                }
                if (optArr != null) {
                    options = new ArrayList<>();
                    allInt = true;
                    for (int i = 0; i < optArr.size(); i++) {
                        if (!optArr.get(i).isJsonObject()) continue;
                        JsonObject o = optArr.get(i).getAsJsonObject();
                        String oLabel = o.has("label") ? o.get("label").getAsString() : "";
                        String oValue = "";
                        if (o.has("value")) {
                            JsonElement v = o.get("value");
                            if (v != null && v.isJsonPrimitive()) {
                                var prim = v.getAsJsonPrimitive();
                                if (prim.isNumber()) {
                                    oValue = prim.getAsNumber().toString();
                                } else if (prim.isString()) {
                                    oValue = prim.getAsString();
                                } else if (prim.isBoolean()) {
                                    oValue = prim.getAsBoolean() ? "1" : "0";
                                } else {
                                    oValue = v.getAsString();
                                }
                            } else if (v != null) {
                                oValue = v.toString();
                            }
                        }
                        options.add(new Option(oLabel, oValue));
                        if (!oValue.isEmpty() && !isIntLike(oValue)) {
                            allInt = false;
                        }
                    }
                }
            }

            List<GuiMobAdditiveMatrixEditor.Group> groups = null;
            int defaultValue = 0;
            if ("additive-matrix".equals(type)) {
                if (def.has("default") && def.get("default").isJsonPrimitive() && def.get("default").getAsJsonPrimitive().isNumber()) {
                    defaultValue = def.get("default").getAsInt();
                }
                if (def.has("option_groups") && def.get("option_groups").isJsonArray()) {
                    groups = new ArrayList<>();
                    JsonArray gArr = def.getAsJsonArray("option_groups");
                    for (int i = 0; i < gArr.size(); i++) {
                        if (!gArr.get(i).isJsonObject()) continue;
                        JsonObject g = gArr.get(i).getAsJsonObject();
                        String gLabel = g.has("label") ? g.get("label").getAsString() : ("Group " + (i + 1));
                        if (!g.has("options") || !g.get("options").isJsonArray()) continue;
                        JsonArray gOpts = g.getAsJsonArray("options");
                        List<GuiMobAdditiveMatrixEditor.Option> parsed = new ArrayList<>();
                        int mask = 0;
                        for (int j = 0; j < gOpts.size(); j++) {
                            if (!gOpts.get(j).isJsonObject()) continue;
                            JsonObject o = gOpts.get(j).getAsJsonObject();
                            String oLabel = o.has("label") ? o.get("label").getAsString() : "";
                            int oValue = 0;
                            if (o.has("value") && o.get("value").isJsonPrimitive() && o.get("value").getAsJsonPrimitive().isNumber()) {
                                oValue = o.get("value").getAsInt();
                            }
                            mask |= oValue;
                            parsed.add(new GuiMobAdditiveMatrixEditor.Option(oLabel, oValue));
                        }
                        groups.add(new GuiMobAdditiveMatrixEditor.Group(gLabel, parsed, mask));
                    }
                }
            }

            return new FieldMeta(fieldPath, type, label, help, options, allInt, groups, defaultValue);
        }

        private static boolean isIntLike(String s) {
            if (s == null || s.isEmpty()) return false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (i == 0 && c == '-') continue;
                if (c < '0' || c > '9') return false;
            }
            return true;
        }
    }

    record Option(String label, String value) {
    }

    private abstract class BaseElement extends ListElement {
        protected final FieldMeta meta;

        protected BaseElement(FieldMeta meta) {
            super(201, 21);
            this.meta = meta;
        }

        @Override
        public boolean match(String search) {
            if (search == null || search.isEmpty()) return true;
            String s = search.toLowerCase(Locale.ROOT);
            return meta.label.toLowerCase(Locale.ROOT).contains(s) || meta.fieldPath.toLowerCase(Locale.ROOT).contains(s);
        }

        protected void applyHelp(AbstractWidgetWithTooltip widget) {
            if (meta.help != null && !meta.help.isEmpty()) {
                widget.setTooltip(Tooltip.create(Component.literal(meta.help)));
            }
        }
    }

    /**
     * Minimal adapter to call setTooltip on different widget types we use.
     */
    private interface AbstractWidgetWithTooltip {
        void setTooltip(@Nullable Tooltip tooltip);
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

    private final class BoolElement extends BaseElement {
        BoolElement(FieldMeta meta) {
            super(meta);
            String[] parts = NbtPath.split(meta.fieldPath);
            GuiBooleanButton btn = new GuiBooleanButton(0, 0, 200, 20, Component.literal(meta.label),
                    val -> {
                        CompoundTag parent = getOrCreateParent(parts);
                        ItemUtils.putBoolean(parent, parts[parts.length - 1], val);
                    },
                    () -> {
                        CompoundTag parent = getParentExisting(parts);
                        return parent != null && ItemUtils.getBoolean(parent, parts[parts.length - 1]);
                    });
            applyHelp(btn::setTooltip);
            buttonList.add(btn);
        }
    }

    private final class IntElement extends BaseElement {
        IntElement(FieldMeta meta) {
            super(meta);
            String[] parts = NbtPath.split(meta.fieldPath);

            EEButton label = new EEButton(0, 0, 100, 20, Component.literal(meta.label), b -> {});
            label.active = false;
            applyHelp(label::setTooltip);
            buttonList.add(label);

            EditBox box = new EditBox(font, 102, 0, 98, 20, Component.literal(meta.label));
            CompoundTag parent = getParentExisting(parts);
            int current = parent != null ? ItemUtils.getInt(parent, parts[parts.length - 1]) : 0;
            box.setValue(String.valueOf(current));
            box.setResponder(val -> {
                if (val == null || val.isEmpty() || "-".equals(val)) {
                    return;
                }
                try {
                    int i = Integer.parseInt(val);
                    CompoundTag p = getOrCreateParent(parts);
                    ItemUtils.putInt(p, parts[parts.length - 1], i);
                    box.setTextColor(0xE0E0E0);
                } catch (NumberFormatException e) {
                    box.setTextColor(0xFF0000);
                }
            });
            fieldList.add(box);
        }
    }

    private final class TextElement extends BaseElement {
        TextElement(FieldMeta meta) {
            super(meta);
            String[] parts = NbtPath.split(meta.fieldPath);

            EEButton label = new EEButton(0, 0, 100, 20, Component.literal(meta.label), b -> {});
            label.active = false;
            applyHelp(label::setTooltip);
            buttonList.add(label);

            EditBox box = new EditBox(font, 102, 0, 98, 20, Component.literal(meta.label));
            CompoundTag parent = getParentExisting(parts);
            String current = parent != null ? ItemUtils.getString(parent, parts[parts.length - 1]) : "";
            box.setValue(current);
            box.setResponder(val -> {
                CompoundTag p = getOrCreateParent(parts);
                if (val == null || val.isEmpty()) {
                    ItemUtils.remove(p, parts[parts.length - 1]);
                } else {
                    ItemUtils.putString(p, parts[parts.length - 1], val);
                }
            });
            fieldList.add(box);
        }
    }

    private final class SelectElement extends BaseElement {
        private final EEButton button;
        private final String[] parts;

        SelectElement(FieldMeta meta) {
            super(meta);
            this.parts = NbtPath.split(meta.fieldPath);
            this.button = new EEButton(0, 0, 200, 20, Component.literal(""), b -> openSelector());
            applyHelp(button::setTooltip);
            buttonList.add(button);
            refreshLabel();
        }

        private void refreshLabel() {
            String current = getCurrentValueString();
            String display = current;
            if (meta.options != null) {
                for (Option o : meta.options) {
                    if (Objects.equals(o.value, current)) {
                        display = o.label == null || o.label.isEmpty() ? current : o.label;
                        break;
                    }
                }
            }
            if (display == null || display.isEmpty()) {
                display = "(unset)";
            }
            button.setMessage(Component.literal(meta.label + ": " + display));
        }

        private String getCurrentValueString() {
            CompoundTag parent = getParentExisting(parts);
            if (parent == null) return "";
            Tag t = parent.get(parts[parts.length - 1]);
            if (t == null) return "";
            if (t instanceof StringTag st) return st.value();
            if (t instanceof NumericTag nt) return String.valueOf(nt.box());
            return t.toString();
        }

        private void openSelector() {
            List<Tuple<String, String>> options = new ArrayList<>();
            if (meta.options != null) {
                for (Option o : meta.options) {
                    String label = (o.label == null || o.label.isEmpty()) ? "(unset)" : o.label;
                    options.add(new Tuple<>(label, o.value == null ? "" : o.value));
                }
            }
            getMinecraft().setScreen(new GuiButtonListSelector<>(GuiMobFieldsEditor.this,
                    Component.literal(meta.label), options, selected -> {
                applySelection(selected);
                refreshLabel();
                return GuiMobFieldsEditor.this;
            }));
        }

        private void applySelection(String selected) {
            CompoundTag parent = getOrCreateParent(parts);
            String key = parts[parts.length - 1];
            if (selected == null || selected.isEmpty()) {
                ItemUtils.remove(parent, key);
                return;
            }

            boolean wantsInt = meta.optionsAllInt && FieldMeta.isIntLike(selected);
            if (wantsInt) {
                Tag existing = parent.get(key);
                int value = Integer.parseInt(selected);
                if (existing != null && existing.getId() == Tag.TAG_BYTE) {
                    ItemUtils.putByte(parent, key, (byte) value);
                } else if (existing != null && existing.getId() == Tag.TAG_SHORT) {
                    ItemUtils.putShort(parent, key, (short) value);
                } else {
                    ItemUtils.putInt(parent, key, value);
                }
            } else {
                ItemUtils.putString(parent, key, selected);
            }
        }
    }

    private final class ChecklistElement extends BaseElement {
        private final EEButton button;
        private final String[] parts;

        ChecklistElement(FieldMeta meta) {
            super(meta);
            this.parts = NbtPath.split(meta.fieldPath);
            this.button = new EEButton(0, 0, 200, 20, Component.literal(""), b -> openChecklist());
            applyHelp(button::setTooltip);
            buttonList.add(button);
            refreshLabel();
        }

        @Override
        public void update() {
            refreshLabel();
        }

        private void refreshLabel() {
            int selected = getSelectedCount();
            button.setMessage(Component.literal(meta.label + ": " + selected + " selected"));
        }

        private int getSelectedCount() {
            CompoundTag parent = getParentExisting(parts);
            if (parent == null) return 0;
            Tag t = parent.get(parts[parts.length - 1]);
            if (!(t instanceof net.minecraft.nbt.ListTag list)) return 0;
            return list.size();
        }

        private void openChecklist() {
            if (meta.options == null || meta.options.isEmpty()) {
                return;
            }
            getMinecraft().setScreen(new GuiMobChecklistEditor(GuiMobFieldsEditor.this, setter, state,
                    meta.fieldPath, meta.label, meta.options, meta.optionsAllInt));
        }
    }

    private final class HrElement extends ListElement {
        HrElement(FieldMeta meta) {
            super(201, 10);
        }

        @Override
        public boolean match(String search) {
            return true;
        }

        @Override
        public void draw(net.minecraft.client.gui.GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            int x1 = offsetX + 4;
            int x2 = offsetX + getSizeX() - 4;
            int y = offsetY + getSizeY() / 2;
            GuiUtils.drawRect(graphics, x1, y, x2, y + 1, 0x66FFFFFF);
        }
    }

    private final class IntArrayElement extends BaseElement {
        IntArrayElement(FieldMeta meta) {
            super(meta);
            String[] parts = NbtPath.split(meta.fieldPath);

            EEButton btn = new EEButton(0, 0, 200, 20, Component.literal(meta.label), b -> {
                CompoundTag parent = getParentExisting(parts);
                int[] arr = parent != null ? ItemUtils.getIntArray(parent, parts[parts.length - 1]) : new int[0];
                getMinecraft().setScreen(new GuiNBTIntArrayModifier(Component.literal(meta.label), GuiMobFieldsEditor.this,
                        out -> {
                            CompoundTag p = getOrCreateParent(parts);
                            ItemUtils.putIntArray(p, parts[parts.length - 1], out.getAsIntArray());
                        }, new IntArrayTag(arr)));
            });
            applyHelp(btn::setTooltip);
            buttonList.add(btn);
        }
    }

    private final class CoordinatesElement extends BaseElement {
        CoordinatesElement(FieldMeta meta) {
            super(meta);
            EEButton btn = new EEButton(0, 0, 200, 20, Component.literal(meta.label), b -> {
                getMinecraft().setScreen(new GuiMobCoordinatesEditor(GuiMobFieldsEditor.this, setter, state, meta.fieldPath, meta.label));
            });
            applyHelp(btn::setTooltip);
            buttonList.add(btn);
        }
    }

    private final class AdditiveMatrixElement extends BaseElement {
        private final EEButton button;
        private final String[] parts;

        AdditiveMatrixElement(FieldMeta meta) {
            super(meta);
            this.parts = NbtPath.split(meta.fieldPath);
            this.button = new EEButton(0, 0, 200, 20, Component.literal(""), b -> openEditor());
            applyHelp(button::setTooltip);
            buttonList.add(button);
            refreshLabel();
        }

        @Override
        public void update() {
            refreshLabel();
        }

        private int getCurrentValue() {
            CompoundTag parent = getParentExisting(parts);
            if (parent == null) return meta.matrixDefault;
            return ItemUtils.getInt(parent, parts[parts.length - 1]);
        }

        private void refreshLabel() {
            button.setMessage(Component.literal(meta.label + ": " + getCurrentValue()));
        }

        private void openEditor() {
            if (meta.matrixGroups == null || meta.matrixGroups.isEmpty()) {
                return;
            }
            getMinecraft().setScreen(new GuiMobAdditiveMatrixEditor(GuiMobFieldsEditor.this, setter, state,
                    meta.fieldPath, meta.label, meta.matrixGroups, meta.matrixDefault));
        }
    }

    private final class BlockAutocompleteElement extends BaseElement {
        private static @Nullable List<Tuple<String, String>> cachedBlocks;
        private final EEButton button;
        private final String[] parts;

        BlockAutocompleteElement(FieldMeta meta) {
            super(meta);
            this.parts = NbtPath.split(meta.fieldPath);
            this.button = new EEButton(0, 0, 200, 20, Component.literal(""), b -> openSelector());
            applyHelp(button::setTooltip);
            buttonList.add(button);
            refreshLabel();
        }

        @Override
        public void update() {
            refreshLabel();
        }

        private void refreshLabel() {
            String current = getCurrentValue();
            String display = (current == null || current.isEmpty()) ? "(unset)" : current;
            button.setMessage(Component.literal(meta.label + ": " + display));
        }

        private String getCurrentValue() {
            CompoundTag parent = getParentExisting(parts);
            if (parent == null) return "";
            return ItemUtils.getString(parent, parts[parts.length - 1]);
        }

        private static List<Tuple<String, String>> getBlocks() {
            if (cachedBlocks != null) return cachedBlocks;
            List<Tuple<String, String>> out = new ArrayList<>();
            out.add(new Tuple<>("(unset)", ""));
            BuiltInRegistries.BLOCK.keySet().stream()
                    .sorted(Comparator.comparing(Identifier::toString))
                    .forEach(id -> {
                        String s = id.toString();
                        out.add(new Tuple<>(s, s));
                    });
            cachedBlocks = out;
            return out;
        }

        private void openSelector() {
            getMinecraft().setScreen(new GuiButtonListSelector<>(GuiMobFieldsEditor.this,
                    Component.literal(meta.label), getBlocks(), selected -> {
                CompoundTag parent = getOrCreateParent(parts);
                String key = parts[parts.length - 1];
                if (selected == null || selected.isEmpty()) {
                    ItemUtils.remove(parent, key);
                } else {
                    ItemUtils.putString(parent, key, selected);
                }
                refreshLabel();
                return GuiMobFieldsEditor.this;
            }));
        }
    }

    private final class UnsupportedElement extends BaseElement {
        UnsupportedElement(FieldMeta meta) {
            super(meta);
            EEButton btn = new EEButton(0, 0, 200, 20, Component.literal(meta.label + " (unsupported: " + meta.type + ")"), b -> {});
            btn.active = false;
            buttonList.add(btn);
        }
    }
}
