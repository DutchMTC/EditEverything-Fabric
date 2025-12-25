package fr.atesab.act.gui.modifier;

import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.gui.selector.GuiButtonListSelector;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.Tuple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiActiveEffectsModifier extends GuiListModifier<List<CompoundTag>> {

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
            if (tag.contains("id", 8)) { // String
                 this.potion = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(tag.getString("id")));
            } else if (tag.contains("Id", 3)) { // Int (Legacy)
                 this.potion = BuiltInRegistries.MOB_EFFECT.byId(tag.getInt("Id"));
            }
            
            if (this.potion == null) {
                this.potion = MobEffects.MOVEMENT_SPEED.value();
            }

            this.amplifierValue = tag.getByte("Amplifier");
            this.durationTime = tag.getInt("Duration");
            this.ambient = tag.getBoolean("Ambient");
            this.showParticles = tag.contains("ShowParticles") ? tag.getBoolean("ShowParticles") : true;
            this.showIcon = tag.contains("ShowIcon") ? tag.getBoolean("ShowIcon") : true;

            int l = 5 + Math.max(font.width(I18n.get("gui.act.modifier.meta.potion.duration") + " : "),
                    font.width(I18n.get("gui.act.modifier.meta.potion.amplifier") + " : "));

            duration = new EditBox(font, l, 1, 150 - l, 18, Component.literal(""));
            duration.setValue(String.valueOf(durationTime));
            amplifier = new EditBox(font, l, 22, 150 - l, 18, Component.literal(""));
            amplifier.setValue(String.valueOf(amplifierValue));

            buttonList.add(type = new ACTButton(153, 0, 200, 20,
                    Component.translatable("gui.act.modifier.meta.potion.type"), b -> {
                List<Tuple<String, MobEffect>> pots = new ArrayList<>();
                BuiltInRegistries.MOB_EFFECT
                        .forEach(pot -> pots.add(new Tuple<>(I18n.get(pot.getDescriptionId()), pot)));
                mc.setScreen(new GuiButtonListSelector<>(parent,
                        Component.translatable("gui.act.modifier.meta.potion.type"), pots, pot -> {
                    potion = pot;
                    setButtonText();
                    return null;
                }));
            }));
            
            buttonList.add(new GuiBooleanButton(153, 21, 100, 20,
                    Component.translatable("gui.act.modifier.meta.potion.ambient"), b -> ambient = b,
                    () -> ambient));
            buttonList.add(new GuiBooleanButton(255, 21, 99, 20,
                    Component.translatable("gui.act.modifier.meta.potion.showParticles"), b -> showParticles = b,
                    () -> showParticles));
            
            buttonList.add(new RemoveElementButton(parent, 355, 0, 20, 20, this));
            buttonList.add(new AddElementButton(parent, 377, 0, 20, 20, this, parent.supplier));
            buttonList.add(new AddElementButton(parent, 355, 21, 43, 20, Component.translatable("gui.act.give.copy"),
                    this, () -> new ActiveEffectListElement(parent, getData())));
            
            setButtonText();
        }

        private void setButtonText() {
            type.setMessage(Component.translatable("gui.act.modifier.meta.potion.type").append(" (").append(
                            potion == null ? Component.literal("null") : Component.translatable(potion.getDescriptionId()))
                    .append(")"));
        }

        @Override
        public void draw(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            GuiUtils.drawRelative(graphics, amplifier, offsetX, offsetY, mouseX, mouseY, partialTicks);
            GuiUtils.drawRelative(graphics, duration, offsetX, offsetY, mouseX, mouseY, partialTicks);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.act.modifier.meta.potion.duration") + " : ", duration,
                    (errDur ? Color.RED : Color.WHITE).getRGB(), offsetX, offsetY);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.act.modifier.meta.potion.amplifier") + " : ", amplifier,
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
        public boolean charTyped(char key, int modifiers) {
            return amplifier.charTyped(key, modifiers) || duration.charTyped(key, modifiers)
                    || super.charTyped(key, modifiers);
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            return amplifier.keyPressed(key, scanCode, modifiers) || duration.keyPressed(key, scanCode, modifiers)
                    || super.keyPressed(key, scanCode, modifiers);
        }

        @Override
        public boolean match(String search) {
            return (potion == null ? "" : I18n.get(potion.getDescriptionId()).toLowerCase())
                    .contains(search.toLowerCase());
        }

        @Override
        public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
            if (GuiUtils.isHover(amplifier, (int) mouseX, (int) mouseY)) {
                amplifier.setFocused(true);
            }
            if (GuiUtils.isHover(duration, (int) mouseX, (int) mouseY)) {
                duration.setFocused(true);
            }
            amplifier.mouseClicked(mouseX, mouseY, mouseButton);
            duration.mouseClicked(mouseX, mouseY, mouseButton);
            super.mouseClicked(mouseX, mouseY, mouseButton);
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
                if (!(errAmp = i < -128 || i > 127))
                    amplifierValue = i;
            } catch (Exception e) {
                errAmp = true;
            }
            super.update();
        }

        public CompoundTag getData() {
            MobEffectInstance instance = new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(potion), durationTime, amplifierValue, ambient, showParticles, showIcon);
            return (CompoundTag) instance.save();
        }
    }

    private final Supplier<ListElement> supplier = () -> {
        MobEffectInstance instance = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0);
        CompoundTag tag = (CompoundTag) instance.save();
        return new ActiveEffectListElement(this, tag);
    };

    @SuppressWarnings("unchecked")
    public GuiActiveEffectsModifier(Screen parent, List<CompoundTag> effects, Consumer<List<CompoundTag>> setter) {
        super(parent, Component.translatable("gui.act.modifier.meta.potion"), new ArrayList<>(), setter, new Tuple[0]);
        effects.forEach(effect -> addListElement(new ActiveEffectListElement(this, effect)));
        addListElement(new AddElementList(this, supplier));
    }

    @Override
    protected List<CompoundTag> get() {
        List<CompoundTag> result = new ArrayList<>();
        getElements().stream().filter(le -> le instanceof ActiveEffectListElement).map(le -> (ActiveEffectListElement) le)
                .forEach(ale -> result.add(ale.getData()));
        return result;
    }
}
