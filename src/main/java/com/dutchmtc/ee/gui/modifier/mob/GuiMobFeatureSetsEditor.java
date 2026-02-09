package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiBooleanButton;
import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import com.dutchmtc.ee.mobdata.FeatureSetRepository;
import com.dutchmtc.ee.mobdata.MobDataRepository;
import com.dutchmtc.ee.utils.Tuple;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;

public class GuiMobFeatureSetsEditor extends GuiListModifier<CompoundTag> {
    private final MobPropertyEditorState state;

    @SuppressWarnings("unchecked")
    public GuiMobFeatureSetsEditor(Screen parent, Consumer<CompoundTag> setter, MobPropertyEditorState state) {
        super(parent, Component.literal("Feature Sets"), new ArrayList<>(), setter, true, true, new Tuple[0]);
        this.state = state;
        setTopControlsHeight(20);
        rebuildElements();
        setPaddingTop(8);
        setNoAdaptativeSize(true);
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

        EEButton fieldsTab = new EEButton(x0, y0, tabW, tabH, Component.literal("Fields"), b -> {
            getMinecraft().setScreen(new GuiMobFieldsEditor(parent, setter, state));
        });
        addRenderableWidget(fieldsTab);

        EEButton featuresTab = new EEButton(x0 + tabW + 2, y0, tabW, tabH, Component.literal("Feature Sets"), b -> {});
        featuresTab.active = false;
        addRenderableWidget(featuresTab);

        super.init();
    }

    private void rebuildElements() {
        List<ListElement> current = new ArrayList<>(getElements());
        current.forEach(this::removeListElement);

        JsonObject root = FeatureSetRepository.getFeatureSets();
        if (root == null) {
            addListElement(new InfoElement("Missing ee mobdata/feature_sets.json"));
            return;
        }

        Set<String> suggested = getSuggestedFeatureSets();
        JsonObject sets = root.getAsJsonObject("feature_sets");
        if (sets == null) {
            addListElement(new InfoElement("feature_sets missing in JSON"));
            return;
        }

        List<String> keys = new ArrayList<>(sets.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            boolean isSuggested = suggested.contains(key);
            addListElement(new FeatureSetElement(key, isSuggested));
        }
    }

    private Set<String> getSuggestedFeatureSets() {
        JsonObject entity = MobDataRepository.getEntity(state.entityPath);
        if (entity == null) return Collections.emptySet();
        if (!entity.has("feature_sets") || !entity.get("feature_sets").isJsonArray()) return Collections.emptySet();
        JsonArray arr = entity.getAsJsonArray("feature_sets");
        Set<String> out = new HashSet<>();
        for (int i = 0; i < arr.size(); i++) {
            if (arr.get(i).isJsonPrimitive()) {
                out.add(arr.get(i).getAsString());
            }
        }
        return out;
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

    private final class FeatureSetElement extends ListElement {
        private final String key;
        private final GuiBooleanButton toggle;
        private final boolean suggested;

        FeatureSetElement(String key, boolean suggested) {
            super(201, 21);
            this.key = key;
            this.suggested = suggested;

            Component label = Component.literal(key + (suggested ? " (suggested)" : ""));
            toggle = new GuiBooleanButton(0, 0, 200, 20, label,
                    val -> {
                        if (val) state.enabledFeatureSets.add(key);
                        else state.enabledFeatureSets.remove(key);
                    },
                    () -> state.enabledFeatureSets.contains(key));

            String help = switch (key) {
                case "bucket" -> "Adds bucket-related fields (e.g. FromBucket) to the Fields tab.";
                case "breedable" -> "Adds breed-related fields (limited by mobData.json).";
                case "tameable" -> "Adds tame-related fields (limited by mobData.json).";
                case "trader" -> "Adds villager trader fields (VillagerData.*) to the Fields tab.";
                case "raid" -> "No concrete fields are defined for raid in mobData.json.";
                default -> "Feature set";
            };
            toggle.setTooltip(Tooltip.create(Component.literal(help)));

            buttonList.add(toggle);
        }

        @Override
        public boolean match(String search) {
            if (search == null || search.isEmpty()) return true;
            String s = search.toLowerCase(Locale.ROOT);
            return key.toLowerCase(Locale.ROOT).contains(s);
        }
    }
}
