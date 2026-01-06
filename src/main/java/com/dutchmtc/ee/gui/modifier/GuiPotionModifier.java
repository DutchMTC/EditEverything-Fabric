package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ItemUtils.PotionInformation;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiPotionModifier extends GuiListModifier<PotionInformation> {
    private static class CustomPotionListElement extends ListElement {
        private final EditBox duration;
        private final EditBox amplifier;
        private MobEffect potion;
        private int durationTime, amplifierValue;
        private boolean ambient;
        private final boolean showIcon;
        private boolean showParticles;
        private boolean errDur = false, errAmp = false;
        private final Button type;

        public CustomPotionListElement(GuiPotionModifier parent, MobEffectInstance potionEffect) {
            super(400, 50);
            int l = 5 + Math.max(font.width(I18n.get("gui.ee.modifier.meta.potion.duration") + " : "),
                    font.width(I18n.get("gui.ee.modifier.meta.potion.amplifier") + " : "));
            potion = potionEffect.getEffect().value(); // Holder -> value
            durationTime = potionEffect.getDuration();
            amplifierValue = potionEffect.getAmplifier();
            ambient = potionEffect.isAmbient();
            showIcon = potionEffect.showIcon();
            showParticles = potionEffect.isVisible();

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
                mc.setScreen(new GuiButtonListSelector<>(parent,
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
                    this, () -> new CustomPotionListElement(parent, getEffect())));
            setButtonText();
        }

        @Override
        public void draw(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            GuiUtils.drawRelative(graphics, amplifier, offsetX, offsetY, mouseX, mouseY, partialTicks);
            GuiUtils.drawRelative(graphics, duration, offsetX, offsetY, mouseX, mouseY, partialTicks);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.meta.potion.duration") + " : ", duration,
                    (errDur ? Color.RED : Color.WHITE).getRGB(), offsetX, offsetY);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.meta.potion.amplifier") + " : ", amplifier,
                    (errAmp ? Color.RED : Color.WHITE).getRGB(), offsetX, offsetY);
            super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }

        public MobEffectInstance getEffect() {
            // MobEffectInstance constructor takes Holder<MobEffect>
            return new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(potion), durationTime, amplifierValue, ambient, showParticles, showIcon);
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
        public void update() {
            // amplifier.tick();
            // duration.tick();
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

    }

    private static class MainPotionListElement extends ListElement {
        private static String getPotionName(Potion pot) {
            String name = BuiltInRegistries.POTION.getKey(pot).getPath();
            String registry = ItemUtils.getRegistry(BuiltInRegistries.POTION, pot).toString();
            return name + (registry.contains("long_")
                    ? " (" + I18n.get("gui.ee.modifier.meta.potion.long") + ")"
                    : registry.contains("strong_") ? " II" : "");
        }

        private final GuiPotionModifier parent;

        private final Button type;

        public MainPotionListElement(GuiPotionModifier parent) {
            super(400, 29);
            this.parent = parent;
            buttonList.add(new EEButton(0, 0, 200, 20, Component.translatable("gui.ee.modifier.meta.setColor"),
                    b -> mc.setScreen(
                            new GuiColorModifier(parent, i -> parent.customColor = i, parent.customColor, true))));
            buttonList.add(type = new EEButton(201, 0, 199, 20, Component.literal(""), b -> {
                List<Tuple<String, Potion>> pots = new ArrayList<>();
                BuiltInRegistries.POTION.forEach(type -> pots.add(new Tuple<>(getPotionName(type), type)));
                mc.setScreen(new GuiButtonListSelector<>(parent,
                        Component.translatable("gui.ee.modifier.meta.potion.type"), pots, pot -> {
                    parent.main = pot;
                    defineButton();
                    return null;
                }));
            }));
            defineButton();
        }

        private void defineButton() {
            type.setMessage(Component.translatable("gui.ee.modifier.meta.potion.type").append(" (")
                    .append(getPotionName(parent.main)).append(")"));
        }

    }

    private OptionalInt customColor;

    private Potion main;

    private final Supplier<ListElement> supplier = () -> new CustomPotionListElement(this,
            new MobEffectInstance(MobEffects.SPEED));

    @SuppressWarnings("unchecked")
    public GuiPotionModifier(Screen parent, Consumer<PotionInformation> setter, PotionInformation info) {
        super(parent, Component.translatable("gui.ee.modifier.meta.potion"), new ArrayList<>(), setter,
                new Tuple[0]);
        this.customColor = info.getCustomColor();
        this.main = info.getMain();
        addListElement(new MainPotionListElement(this));
        info.getCustomEffects().forEach(t -> addListElement(new CustomPotionListElement(this, t)));
        addListElement(new AddElementList(this, supplier));
    }

    @Override
    protected PotionInformation get() {
        List<MobEffectInstance> customEffects = new ArrayList<>();
        getElements().stream().filter(le -> le instanceof CustomPotionListElement)
                .map(le -> (CustomPotionListElement) le).forEach(cpl -> customEffects.add(cpl.getEffect()));
        return new PotionInformation(customColor, main, customEffects);
    }

}
