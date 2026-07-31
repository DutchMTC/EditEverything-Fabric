package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiActiveEffectsModifier extends GuiListModifier<List<CompoundTag>> {
    private static final String AMPLIFIER_KEY = "amplifier";
    private static final String DURATION_KEY = "duration";
    private static final String AMBIENT_KEY = "ambient";
    private static final String SHOW_PARTICLES_KEY = "show_particles";
    private static final String SHOW_ICON_KEY = "show_icon";

    private final List<CompoundTag> originalEffects;

    static class ActiveEffectListElement extends ListElement {
        private final EditBox duration;
        private final EditBox amplifier;
        private MobEffect potion;
        private int durationTime, amplifierValue;
        private boolean ambient;
        private boolean showParticles;
        private boolean showIcon;
        private boolean errDur = false, errAmp = false;
        private final Button type;

        public ActiveEffectListElement(GuiActiveEffectsModifier parent, CompoundTag tag) {
            super(400, 50);
            
            // Parse Tag
            if (ItemUtils.hasTag(tag, "id", 8)) { // String
                 var id = Identifier.tryParse(ItemUtils.getString(tag, "id"));
                 this.potion = id != null ? BuiltInRegistries.MOB_EFFECT.get(id).map(Holder.Reference::value).orElse(null) : null;
            } else if (ItemUtils.hasTag(tag, "Id", 3)) { // Int (Legacy)
                 this.potion = BuiltInRegistries.MOB_EFFECT.byId(ItemUtils.getInt(tag, "Id"));
            }
            
            if (this.potion == null) {
                this.potion = MobEffects.SPEED.value();
            }

            this.amplifierValue = readAmplifier(tag);
            this.durationTime = readInt(tag, DURATION_KEY, "Duration", 0);
            this.ambient = readBoolean(tag, AMBIENT_KEY, "Ambient", false);
            this.showParticles = readBoolean(tag, SHOW_PARTICLES_KEY, "ShowParticles", true);
            this.showIcon = readBoolean(tag, SHOW_ICON_KEY, "ShowIcon", true);

            int l = 5 + Math.max(font.width(I18n.get("gui.ee.modifier.meta.potion.duration") + " : "),
                    font.width(I18n.get("gui.ee.modifier.meta.potion.amplifier") + " : "));

            duration = new EditBox(font, l, 1, 150 - l, 18, Component.literal(""));
            duration.setValue(String.valueOf(durationTime));
            amplifier = new EditBox(font, l, 22, 150 - l, 18, Component.literal(""));
            amplifier.setValue(String.valueOf(amplifierValue));

            buttonList.add(type = new EEButton(153, 0, 200, 20,
                    Component.translatable("gui.ee.modifier.meta.potion.type"), b -> {
                List<Tuple<String, MobEffect>> pots = new ArrayList<>();
                BuiltInRegistries.MOB_EFFECT.forEach(pot -> {
                    Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(pot);
                    String desc = "effect." + id.getNamespace() + "." + id.getPath().replace('/', '.');
                    pots.add(new Tuple<>(I18n.get(desc), pot));
                });
                mc.gui.setScreen(new GuiButtonListSelector<>(parent,
                        Component.translatable("gui.ee.modifier.meta.potion.type"), pots, pot -> {
                    potion = pot;
                    setButtonText();
                    return null;
                }));
            }));
            
            buttonList.add(new GuiBooleanButton(153, 21, 100, 20,
                    Component.translatable("gui.ee.modifier.meta.potion.ambient"), b -> ambient = b,
                    () -> ambient));
            buttonList.add(new GuiBooleanButton(255, 21, 99, 20,
                    Component.translatable("gui.ee.modifier.meta.potion.showParticles"), b -> showParticles = b,
                    () -> showParticles));
            
            buttonList.add(new RemoveElementButton(parent, 355, 0, 20, 20, this));
            buttonList.add(new AddElementButton(parent, 377, 0, 20, 20, this, parent.supplier));
            buttonList.add(new AddElementButton(parent, 355, 21, 43, 20, Component.translatable("gui.ee.give.copy"),
                    this, () -> new ActiveEffectListElement(parent, getData())));
            
            setButtonText();
        }

        private void setButtonText() {
            String desc = "null";
            if (potion != null) {
                Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(potion);
                desc = "effect." + id.getNamespace() + "." + id.getPath().replace('/', '.');
            }
            type.setMessage(Component.translatable("gui.ee.modifier.meta.potion.type").append(" (").append(
                            potion == null ? Component.literal("null") : Component.translatable(desc))
                    .append(")"));
        }

        @Override
        public void draw(GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            GuiUtils.drawRelative(graphics, amplifier, offsetX, offsetY, mouseX, mouseY, partialTicks);
            GuiUtils.drawRelative(graphics, duration, offsetX, offsetY, mouseX, mouseY, partialTicks);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.meta.potion.duration") + " : ", duration,
                    (errDur ? Color.RED : Color.WHITE).getRGB(), offsetX, offsetY);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.meta.potion.amplifier") + " : ", amplifier,
                    (errAmp ? Color.RED : Color.WHITE).getRGB(), offsetX, offsetY);
            super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }

        @Override
        public void init() {
            amplifier.setFocused(false);
            duration.setFocused(false);
            super.init();
        }

        @Override
        public boolean isFocused() {
            return amplifier.isFocused() || duration.isFocused();
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return amplifier.charTyped(event) || duration.charTyped(event)
                    || super.charTyped(event);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            return amplifier.keyPressed(event) || duration.keyPressed(event)
                    || super.keyPressed(event);
        }

        @Override
        public boolean match(String search) {
            if (potion == null) return "".contains(search.toLowerCase());
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(potion);
            String desc = "effect." + id.getNamespace() + "." + id.getPath().replace('/', '.');
            return I18n.get(desc).toLowerCase().contains(search.toLowerCase());
        }

        @Override
        public void mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mouseX = event.x();
            double mouseY = event.y();
            if (GuiUtils.isHover(amplifier, (int) mouseX, (int) mouseY)) {
                amplifier.setFocused(true);
                duration.setFocused(false);
            } else if (GuiUtils.isHover(duration, (int) mouseX, (int) mouseY)) {
                duration.setFocused(true);
                amplifier.setFocused(false);
            } else {
                amplifier.setFocused(false);
                duration.setFocused(false);
            }
            amplifier.mouseClicked(event, doubleClick);
            duration.mouseClicked(event, doubleClick);
            super.mouseClicked(event, doubleClick);
        }

        @Override
        public void update() {
            try {
                durationTime = Integer.parseInt(duration.getValue());
                errDur = false;
            } catch (Exception e) {
                errDur = true;
            }
            try {
                int i = Integer.parseInt(amplifier.getValue());
                if (!(errAmp = i < 0 || i > 255))
                    amplifierValue = i;
            } catch (Exception e) {
                errAmp = true;
            }
            super.update();
        }

        public CompoundTag getData() {
            CompoundTag tag = new CompoundTag();
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(potion);
            if (id != null) {
                tag.putString("id", id.toString());
            }
            tag.putInt(AMPLIFIER_KEY, amplifierValue);
            tag.putInt(DURATION_KEY, durationTime);
            tag.putBoolean(AMBIENT_KEY, ambient);
            tag.putBoolean(SHOW_PARTICLES_KEY, showParticles);
            tag.putBoolean(SHOW_ICON_KEY, showIcon);
            return tag;
        }
    }

    private final Supplier<ListElement> supplier = () -> {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.SPEED.value()).toString());
        tag.putInt(AMPLIFIER_KEY, 0);
        tag.putInt(DURATION_KEY, 200);
        tag.putBoolean(AMBIENT_KEY, false);
        tag.putBoolean(SHOW_PARTICLES_KEY, true);
        tag.putBoolean(SHOW_ICON_KEY, true);
        return new ActiveEffectListElement(this, tag);
    };

    @SuppressWarnings("unchecked")
    public GuiActiveEffectsModifier(Screen parent, List<CompoundTag> effects, Consumer<List<CompoundTag>> setter) {
        super(parent, Component.translatable("gui.ee.modifier.meta.potion"), new ArrayList<>(), setter, new Tuple[0]);
        this.originalEffects = normalizeOriginal(effects);
        effects.forEach(effect -> addListElement(new ActiveEffectListElement(this, effect)));
        addListElement(new AddElementList(this, supplier));
    }

    @Override
    public boolean isModified() {
        List<CompoundTag> current = get();
        if (current.size() != originalEffects.size()) {
            return true;
        }
        for (int i = 0; i < current.size(); i++) {
            if (!current.get(i).equals(originalEffects.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static List<CompoundTag> normalizeOriginal(List<CompoundTag> effects) {
        List<CompoundTag> normalized = new ArrayList<>();
        for (CompoundTag tag : effects) {
            normalized.add(normalizeEffectTag(tag));
        }
        return normalized;
    }

    private static CompoundTag normalizeEffectTag(CompoundTag tag) {
        MobEffect potion = null;

        if (ItemUtils.hasTag(tag, "id", 8)) { // String
            var id = Identifier.tryParse(ItemUtils.getString(tag, "id"));
            potion = id != null ? BuiltInRegistries.MOB_EFFECT.get(id).map(Holder.Reference::value).orElse(null) : null;
        } else if (ItemUtils.hasTag(tag, "Id", 3)) { // Int (Legacy)
            potion = BuiltInRegistries.MOB_EFFECT.byId(ItemUtils.getInt(tag, "Id"));
        }

        if (potion == null) {
            potion = MobEffects.SPEED.value();
        }

        CompoundTag normalized = new CompoundTag();
        Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(potion);
        if (id != null) {
            normalized.putString("id", id.toString());
        }
        normalized.putInt(AMPLIFIER_KEY, readAmplifier(tag));
        normalized.putInt(DURATION_KEY, readInt(tag, DURATION_KEY, "Duration", 0));
        normalized.putBoolean(AMBIENT_KEY, readBoolean(tag, AMBIENT_KEY, "Ambient", false));
        normalized.putBoolean(SHOW_PARTICLES_KEY,
                readBoolean(tag, SHOW_PARTICLES_KEY, "ShowParticles", true));
        normalized.putBoolean(SHOW_ICON_KEY, readBoolean(tag, SHOW_ICON_KEY, "ShowIcon", true));
        return normalized;
    }

    private static int readAmplifier(CompoundTag tag) {
        String key = tag.contains(AMPLIFIER_KEY) ? AMPLIFIER_KEY : "Amplifier";
        if (!tag.contains(key)) {
            return 0;
        }
        if (ItemUtils.hasTag(tag, key, net.minecraft.nbt.Tag.TAG_BYTE)) {
            return Byte.toUnsignedInt(ItemUtils.getByte(tag, key));
        }
        return ItemUtils.getInt(tag, key);
    }

    private static int readInt(CompoundTag tag, String modernKey, String legacyKey, int defaultValue) {
        if (tag.contains(modernKey)) {
            return ItemUtils.getInt(tag, modernKey);
        }
        if (tag.contains(legacyKey)) {
            return ItemUtils.getInt(tag, legacyKey);
        }
        return defaultValue;
    }

    private static boolean readBoolean(CompoundTag tag, String modernKey, String legacyKey, boolean defaultValue) {
        if (tag.contains(modernKey)) {
            return ItemUtils.getBoolean(tag, modernKey);
        }
        if (tag.contains(legacyKey)) {
            return ItemUtils.getBoolean(tag, legacyKey);
        }
        return defaultValue;
    }

    @Override
    protected List<CompoundTag> get() {
        List<CompoundTag> result = new ArrayList<>();
        getElements().stream().filter(le -> le instanceof ActiveEffectListElement).map(le -> (ActiveEffectListElement) le)
                .forEach(ale -> result.add(ale.getData()));
        return result;
    }
}
