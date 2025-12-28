package fr.atesab.act.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atesab.act.gui.ColorList;
import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.gui.selector.GuiButtonListSelector;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import fr.atesab.act.utils.ItemUtils.ExplosionInformation;
import fr.atesab.act.utils.Tuple;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.FireworkExplosion.Shape;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiFireworksModifer extends GuiListModifier<CompoundTag> {
    private static class FireworkMainListElement extends ListElement {
        private final EditBox flight;
        private int value;
        private boolean err;
        private String title;

        public FireworkMainListElement(int flight) {
            super(200, 29);
            title = I18n.get("item.minecraft.firework_rocket.flight");
            if (title.endsWith(":"))
                title = title.substring(0, title.length() - 1);
            title += " : ";
            int l = font.width(title + " : ") + 5;
            this.flight = new EditBox(font, l, 2, 196 - l, 16, Component.literal(""));
            this.flight.setMaxLength(6);
            this.flight.setValue("" + flight);

        }

        @Override
        public void init() {
            flight.setFocused(false);
            super.init();
        }

        @Override
        public boolean isFocused() {
            return flight.isFocused();
        }

        @Override
        public void draw(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            GuiUtils.drawRelative(graphics, flight, offsetX, offsetY, mouseX, mouseY, partialTicks);
            GuiUtils.drawString(graphics, font, title, offsetX, offsetY, (err ? Color.RED : Color.WHITE).getRGB(),
                    flight.getHeight());
            super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }

        @Override
        public void mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mouseX = event.x();
            double mouseY = event.y();
            int mouseButton = event.button();
            if (GuiUtils.isHover(flight, (int) mouseX, (int) mouseY)) {
                flight.setFocused(true);
            }
            flight.mouseClicked(event, doubleClick);
            if (GuiUtils.isHover(flight, (int) mouseX, (int) mouseY) && mouseButton == 1)
                flight.setValue("");
            super.mouseClicked(event, doubleClick);
        }

        @Override
        public void update() {
            // flight.tick();
            try {
                value = flight.getValue().isEmpty() ? 0 : Integer.parseInt(flight.getValue());
                err = false;
            } catch (NumberFormatException e) {
                err = true;
            }
            super.update();
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return flight.charTyped(event) || super.charTyped(event);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            return flight.keyPressed(event) || super.keyPressed(event);
        }
    }

    private static class ExplosionListElement extends ListElement {
        private ExplosionInformation exp;
        private final GuiFireworksModifer parent;

        public ExplosionListElement(GuiFireworksModifer parent, CompoundTag expData) {
            super(204, 29);
            this.exp = ItemUtils.getExplosionInformation(expData);
            this.parent = parent;
            buttonList.add(new ACTButton(0, 0, 100, 20, Component.translatable("gui.act.modifier.meta.explosion"),
                    b -> mc.setScreen(
                            new GuiExplosionModifier(parent, exp -> ExplosionListElement.this.exp = exp, exp))));
            buttonList.add(new RemoveElementButton(parent, 101, 0, 20, 20, this));
            buttonList.add(new AddElementButton(parent, 122, 0, 20, 20, this, parent.builder));
            buttonList.add(new AddElementButton(parent, 143, 0, 60, 20, Component.translatable("gui.act.give.copy"),
                    this, () -> new ExplosionListElement(parent, exp.getTag())));
        }

        @Override
        public boolean match(String search) {
            String s = search.toLowerCase();
            return I18n.get("gui.act.modifier.type").toLowerCase().contains(s)
                    || I18n.get("item.minecraft.firework_star.shape." + exp.getType().getSerializedName()).toLowerCase().contains(s)
                    || I18n.get("item.minecraft.firework_star.shape." + exp.getType().getId()).toLowerCase().contains(s)
                    || I18n.get("item.minecraft.firework_star.trail").toLowerCase().contains(s)
                    || I18n.get("item.minecraft.firework_star.flicker").toLowerCase().contains(s)
                    || I18n.get("gui.act.modifier.meta.explosion.color").toLowerCase().contains(s)
                    || I18n.get("gui.act.modifier.meta.explosion.fadeColor").toLowerCase().contains(s);
        }

        @Override
        public void drawNext(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                             float partialTicks) {
            if (GuiUtils.isHover(0, 0, 200, 20, mouseX, mouseY)) {
                List<String> data = new ArrayList<>();
                String type = I18n.get("gui.act.modifier.type") + " : " + ChatFormatting.YELLOW
                        + I18n.get("item.minecraft.firework_star.shape." + exp.getType().getSerializedName());
                String trail = I18n.get("item.minecraft.firework_star.trail");
                String flicker = I18n.get("item.minecraft.firework_star.flicker");
                String color = I18n.get("gui.act.modifier.meta.explosion.color") + " : ";
                String fadeColor = I18n.get("gui.act.modifier.meta.explosion.fadeColor") + " : ";
                data.add(type);
                int width = font.width(type);
                if (exp.isTrail()) {
                    data.add(trail);
                    width = Math.max(width, font.width(trail));
                }
                if (exp.isFlicker()) {
                    data.add(flicker);
                    width = Math.max(width, font.width(flicker));
                }
                if (exp.getColors().length != 0) {
                    data.add(color);
                    width = Math.max(width, (font.lineHeight + 1) * exp.getColors().length + font.width(color));
                }
                if (exp.getFadeColors().length != 0) {
                    data.add(fadeColor);
                    width = Math.max(width, (font.lineHeight + 1) * exp.getFadeColors().length + font.width(fadeColor));
                }
                int height = data.size() * (1 + font.lineHeight) + 2;
                width += 2;
                Tuple<Integer, Integer> pos = GuiUtils.getRelativeBoxPos(mouseX + offsetX, mouseY + offsetY, width,
                        height, parent.width, parent.height);
                GuiUtils.drawBox(graphics, pos.a, pos.b, width, height, parent.getZLevel());
                pos.a++;
                pos.b += 2;
                int i;
                for (i = 0; i < data.size(); i++)
                    graphics.drawString(font, data.get(i), pos.a, pos.b + i * (font.lineHeight + 1), 0xffffffff);
                if (exp.getFadeColors().length != 0) {
                    i -= 1;
                    int x = pos.a + font.width(fadeColor);
                    int y = pos.b + i * (font.lineHeight + 1);
                    for (int j = 0; j < exp.getFadeColors().length; j++) {
                        GuiUtils.drawGradientRect(graphics, x, y, x + font.lineHeight, y + font.lineHeight,
                                0xff000000 | exp.getFadeColors()[j], 0xff000000 | exp.getFadeColors()[j]);
                        x += font.lineHeight + 1;
                    }
                }
                if (exp.getColors().length != 0) {
                    i -= 1;
                    int x = pos.a + font.width(color);
                    int y = pos.b + i * (font.lineHeight + 1);
                    for (int j = 0; j < exp.getColors().length; j++) {
                        GuiUtils.drawGradientRect(graphics, x, y, x + font.lineHeight, y + font.lineHeight,
                                0xff000000 | exp.getColors()[j], 0xff000000 | exp.getColors()[j]);
                        x += font.lineHeight + 1;
                    }
                }

            }
            super.drawNext(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }
    }

    public static class GuiExplosionModifier extends GuiModifier<ExplosionInformation> {
        private final ExplosionInformation exp;
        private final ColorList colors;
        private final ColorList fadeColors;
        private Button type;

        public GuiExplosionModifier(Screen parent, Consumer<ExplosionInformation> setter, ExplosionInformation exp) {
            super(parent, Component.translatable("gui.act.modifier.meta.fireworks"), setter);
            this.exp = exp.clone();
            colors = new ColorList(this, 0, 0, 6, exp.getColors(), I18n.get("gui.act.modifier.meta.explosion.color"),
                    24);
            fadeColors = new ColorList(this, 0, 0, 6, exp.getFadeColors(),
                    I18n.get("gui.act.modifier.meta.explosion.fadeColor"), 24);
        }

        private void defineButton() {
            type.setMessage(Component.translatable("item.minecraft.firework_star.shape." + exp.getType().getSerializedName()));
        }

        @Override
        public void init() {
            colors.x = width / 2;
            colors.y = height / 2 - 42;
            fadeColors.x = width / 2 + 100;
            fadeColors.y = height / 2 - 42;
            addRenderableWidget(
                    type = new ACTButton(width / 2 - 200, height / 2 - 42, 199, 20, Component.literal(""), b -> {
                        List<Tuple<String, Shape>> elements = new ArrayList<>(Shape.values().length);
                        for (Shape s : Shape.values())
                            elements.add(new Tuple<>(I18n.get("item.minecraft.firework_star.shape." + s.getSerializedName()), s));
                        mc.setScreen(new GuiButtonListSelector<>(GuiExplosionModifier.this,
                                Component.translatable("gui.act.modifier.meta.explosion.shape"), elements, s -> {
                            exp.type(s);
                            defineButton();
                            return null;
                        }));
                    }));
            addRenderableWidget(new GuiBooleanButton(width / 2 - 200, height / 2 - 21, 199, 20,
                    Component.translatable("item.minecraft.firework_star.trail"), exp::trail, exp::isTrail));
            addRenderableWidget(new GuiBooleanButton(width / 2 - 200, height / 2, 199, 20,
                    Component.translatable("item.minecraft.firework_star.flicker"), exp::flicker, exp::isFlicker));

            addRenderableWidget(
                    new ACTButton(width / 2 - 100, height / 2 + 21, 99, 20, Component.translatable("gui.done"), b -> {
                        set(exp.colors(colors.getColors()).fadeColors(fadeColors.getColors()));
                        getMinecraft().setScreen(parent);
                    }));
            addRenderableWidget(new ACTButton(width / 2 - 200, height / 2 + 21, 99, 20,
                    Component.translatable("gui.act.cancel"), b -> getMinecraft().setScreen(parent)));
            defineButton();
            super.init();
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            // do nothing
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.renderBackground(graphics, mouseX, mouseY, partialTicks);
            super.render(graphics, mouseX, mouseY, partialTicks);
            colors.draw(graphics, mouseX, mouseY, getZLevel()); // Need to update ColorList
            fadeColors.draw(graphics, mouseX, mouseY, getZLevel());
            colors.drawNext(graphics, mouseX, mouseY, getZLevel());
            fadeColors.drawNext(graphics, mouseX, mouseY, getZLevel());
            reRenderWidgets(graphics, mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            colors.mouseClick((int) event.x(), (int) event.y(), event.button());
            fadeColors.mouseClick((int) event.x(), (int) event.y(), event.button());
            return super.mouseClicked(event, doubleClick);
        }
    }

    private final FireworkMainListElement main;
    private final Supplier<ListElement> builder = () -> new ExplosionListElement(this, null);

    @SuppressWarnings("unchecked")
    public GuiFireworksModifer(Screen parent, Consumer<CompoundTag> setter, CompoundTag tag) {
        super(parent, Component.translatable("gui.act.modifier.meta.fireworks"), new ArrayList<>(), setter,
                new Tuple[0]);
        addListElement(main = new FireworkMainListElement(tag.getInt("Flight").orElse(1)));
        tag.getList("Explosions").orElse(new ListTag())
                .forEach(base -> {
                    if (base instanceof CompoundTag ct) {
                        addListElement(new ExplosionListElement(this, ct));
                    }
                });
        addListElement(new AddElementList(this, builder));
    }

    @Override
    protected CompoundTag get() {
        CompoundTag newTag = new CompoundTag();
        newTag.putInt("Flight", main.value);
        ListTag explosions = new ListTag();
        getElements().stream().filter(le -> le instanceof ExplosionListElement)
                .forEach(le -> explosions.add(((ExplosionListElement) le).exp.getTag()));
        newTag.put(ItemUtils.NBT_CHILD_EXPLOSIONS, explosions);
        return newTag;
    }

}
