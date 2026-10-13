package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ItemUtils.ExplosionInformation;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion.Shape;
import net.minecraft.world.item.component.Fireworks;

import java.util.*;
import java.util.function.Consumer;

/** A rocket draft with a separate, focused form for each explosion. */
public class GuiFireworksModifer extends EESettingsScreen<CompoundTag> {
    private static final int ROW_HEIGHT = 44;
    private final List<ExplosionInformation> explosions = new ArrayList<>();
    private final CompoundTag original;
    private final List<EEButton> editButtons = new ArrayList<>(), removeButtons = new ArrayList<>();
    private int flight, selected = -1, listScroll, listTop, listBottom;
    private EditBox flightField;
    private EEButton applyButton, addButton;
    private EEDropdownButton optionsButton;
    private boolean draggingList, revealOnInit;
    private double dragGrab;

    public GuiFireworksModifer(Screen parent, Consumer<CompoundTag> setter, CompoundTag tag) {
        super(parent, "Firework rocket", setter);
        flight = ItemUtils.getFireworkFlight(tag, 1);
        tag.getList(ItemUtils.NBT_CHILD_EXPLOSIONS)
                .orElse(new ListTag())
                .forEach(
                        base -> {
                            if (base instanceof CompoundTag data)
                                explosions.add(copy(new ExplosionInformation(data)));
                        });
        original = get();
    }

    protected CompoundTag get() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Flight", flight);
        ListTag list = new ListTag();
        explosions.forEach(explosion -> list.add(explosion.getTag().copy()));
        tag.put(ItemUtils.NBT_CHILD_EXPLOSIONS, list);
        return tag;
    }

    @Override
    protected CompoundTag result() {
        return get();
    }

    @Override
    protected void buildRows() {}

    @Override
    public boolean isModified() {
        return !get().equals(original) || invalidReason() != null;
    }

    @Override
    protected String invalidReason() {
        String error = super.invalidReason();
        return error != null
                ? error
                : explosions.size() > Fireworks.MAX_EXPLOSIONS
                        ? "A rocket can have at most " + Fireworks.MAX_EXPLOSIONS + " explosions."
                        : null;
    }

    private static ExplosionInformation copy(ExplosionInformation exp) {
        return new ExplosionInformation(
                exp.getType(),
                exp.isTrail(),
                exp.isFlicker(),
                exp.getColors().clone(),
                exp.getFadeColors().clone());
    }

    private static ExplosionInformation newExplosion() {
        return new ExplosionInformation(
                Shape.SMALL_BALL,
                false,
                false,
                new int[] {DyeColor.RED.getFireworkColor()},
                new int[0]);
    }

    private static String shapeName(Shape shape) {
        return I18n.get("item.minecraft.firework_star.shape." + shape.getSerializedName());
    }

    private static ItemStack star(ExplosionInformation exp) {
        ItemStack stack = new ItemStack(Items.FIREWORK_STAR);
        stack.set(DataComponents.FIREWORK_EXPLOSION, exp.toFireworkExplosion());
        return stack;
    }

    private ItemStack rocket() {
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        if (explosions.size() <= Fireworks.MAX_EXPLOSIONS)
            stack.set(
                    DataComponents.FIREWORKS,
                    new Fireworks(
                            flight,
                            explosions.stream()
                                    .map(ExplosionInformation::toFireworkExplosion)
                                    .toList()));
        return stack;
    }

    private void editExplosion(int index) {
        selected = index;
        mc.gui.setScreen(
                new GuiExplosionModifier(
                        this,
                        value -> explosions.set(index, copy(value)),
                        explosions.get(index),
                        "Explosion " + (index + 1),
                        "Save explosion"));
    }

    private void addExplosion() {
        if (explosions.size() >= Fireworks.MAX_EXPLOSIONS) return;
        mc.gui.setScreen(
                new GuiExplosionModifier(
                        this,
                        value -> {
                            explosions.add(copy(value));
                            selected = explosions.size() - 1;
                            revealOnInit = true;
                        },
                        newExplosion(),
                        "New explosion",
                        "Add explosion"));
    }

    private void removeExplosion(int index) {
        explosions.remove(index);
        if (selected == index) selected = -1;
        else if (selected > index) selected--;
        init();
    }

    private List<String> options() {
        List<String> choices = new ArrayList<>();
        if (selected >= 0) {
            if (explosions.size() < Fireworks.MAX_EXPLOSIONS) choices.add("Duplicate explosion");
            if (selected > 0) choices.add("Move earlier");
            if (selected < explosions.size() - 1) choices.add("Move later");
        }
        if (!explosions.isEmpty()) choices.add("Clear explosions");
        return choices;
    }

    private void chooseOption(String option) {
        if (option.equals("Clear explosions")) {
            mc.gui.setScreen(
                    new GuiConfirmation(
                            this,
                            Component.literal("Remove every explosion from this rocket?"),
                            Component.literal("Clear"),
                            () -> {
                                explosions.clear();
                                selected = -1;
                                mc.gui.setScreen(this);
                            },
                            () -> mc.gui.setScreen(this)));
            return;
        }
        if (selected < 0) return;
        if (option.equals("Duplicate explosion")) {
            if (explosions.size() >= Fireworks.MAX_EXPLOSIONS) return;
            explosions.add(selected + 1, copy(explosions.get(selected)));
            selected++;
        } else {
            int destination = selected + (option.equals("Move earlier") ? -1 : 1);
            if (destination < 0 || destination >= explosions.size()) return;
            Collections.swap(explosions, selected, destination);
            selected = destination;
        }
        revealOnInit = true;
    }

    @Override
    public void init() {
        dismissMenu();
        clearFocus();
        clearWidgets();
        editButtons.clear();
        removeButtons.clear();
        draggingList = false;
        applyButton = addButton = null;
        optionsButton = null;
        panelWidth = Math.min(510, width - 16);
        panelHeight =
                Math.min(
                        height - 16,
                        Math.max(194, Math.min(330, 132 + explosions.size() * ROW_HEIGHT)));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        listTop = top + 65;
        listBottom = top + panelHeight - 67;
        viewTop = listTop;
        viewBottom = listBottom;
        flightField =
                number(
                        "flight",
                        Integer.toString(flight),
                        0,
                        255,
                        true,
                        value -> flight = (int) value);
        flightField.setPosition(left + 116, top + 37);
        flightField.setWidth(44);
        flightField.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "How long the rocket flies before exploding. Normal rockets use 1,"
                                        + " 2, or 3.")));
        addRenderableWidget(flightField);
        flightField.setValue(flightField.getValue());
        for (int i = 0; i < explosions.size(); i++) {
            int index = i;
            EEButton edit =
                    new EEButton(
                            0, 0, 42, 20, Component.literal("Edit"), b -> editExplosion(index));
            edit.setTooltip(
                    Tooltip.create(
                            Component.literal("Edit this explosion's shape, colors and effects.")));
            editButtons.add(addRenderableWidget(edit));
            EEButton remove =
                    new EEButton(0, 0, 20, 20, Component.literal("×"), b -> removeExplosion(index));
            remove.setTooltip(Tooltip.create(Component.literal("Remove this explosion.")));
            removeButtons.add(addRenderableWidget(remove));
        }
        int half = (panelWidth - 28) / 2, actionsY = top + panelHeight - 53;
        addButton =
                addRenderableWidget(
                        new EEButton(
                                left + 12,
                                actionsY,
                                half,
                                20,
                                Component.literal("Add explosion"),
                                b -> addExplosion()));
        optionsButton =
                addRenderableWidget(
                        dropdown("Options...", options(), value -> value, this::chooseOption));
        optionsButton.setPosition(left + 16 + half, actionsY);
        optionsButton.setWidth(half);
        addRenderableWidget(
                new EEButton(
                        left + 12,
                        top + panelHeight - 27,
                        half,
                        20,
                        Component.literal("Cancel"),
                        b -> onCancel()));
        applyButton =
                addRenderableWidget(
                        new EEButton(
                                left + 16 + half,
                                top + panelHeight - 27,
                                half,
                                20,
                                Component.literal("Apply"),
                                b -> applyRocket()));
        layoutList();
        if (revealOnInit) {
            revealOnInit = false;
            reveal();
        }
        refreshValidity();
    }

    private void applyRocket() {
        if (invalidReason() != null) return;
        setter.accept(get());
        mc.gui.setScreen(parent);
    }

    @Override
    protected void refreshValidity() {
        String error = invalidReason();
        if (applyButton != null) {
            applyButton.active = error == null;
            applyButton.setTooltip(error == null ? null : Tooltip.create(Component.literal(error)));
        }
        if (addButton != null) {
            addButton.active = explosions.size() < Fireworks.MAX_EXPLOSIONS;
            addButton.setTooltip(
                    addButton.active
                            ? null
                            : Tooltip.create(
                                    Component.literal(
                                            "A rocket can have at most "
                                                    + Fireworks.MAX_EXPLOSIONS
                                                    + " explosions.")));
        }
        if (optionsButton != null) optionsButton.active = !explosions.isEmpty();
    }

    private int maxListScroll() {
        return Math.max(0, explosions.size() * ROW_HEIGHT - (listBottom - listTop));
    }

    private int thumbHeight() {
        int height = listBottom - listTop;
        return Math.max(18, height * height / Math.max(height, explosions.size() * ROW_HEIGHT));
    }

    private double thumbPosition() {
        return listTop
                + (listBottom - listTop - thumbHeight())
                        * (double) listScroll
                        / Math.max(1, maxListScroll());
    }

    private int rowRight() {
        return left + panelWidth - (maxListScroll() > 0 ? 24 : 12);
    }

    private boolean inList(double x, double y) {
        return x >= left + 12 && x < rowRight() && y >= listTop && y < listBottom;
    }

    private void layoutList() {
        listScroll = Math.max(0, Math.min(maxListScroll(), listScroll));
        for (int i = 0; i < editButtons.size(); i++) {
            int y = listTop + i * ROW_HEIGHT - listScroll + 11;
            EEButton edit = editButtons.get(i), remove = removeButtons.get(i);
            edit.setPosition(rowRight() - 70, y);
            remove.setPosition(rowRight() - 24, y);
            edit.visible = remove.visible = y >= listTop && y + 20 <= listBottom;
        }
    }

    private void reveal() {
        if (selected < 0) return;
        int y = selected * ROW_HEIGHT;
        if (y < listScroll) listScroll = y;
        else if (y + ROW_HEIGHT > listScroll + listBottom - listTop)
            listScroll = y + ROW_HEIGHT - (listBottom - listTop);
        layoutList();
    }

    private void dragList(double y) {
        int travel = listBottom - listTop - thumbHeight();
        if (travel > 0)
            listScroll = (int) Math.round((y - dragGrab - listTop) / travel * maxListScroll());
        layoutList();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        GuiUtils.drawItemStack(g, rocket(), left + 12, top + 9);
        g.text(font, title, left + 36, top + 13, EETheme.TEXT, false);
        g.text(font, "Flight duration", left + 12, top + 42, EETheme.TEXT, false);
        EETextField.validationFrame(
                flightField,
                flightField.getX() - 4,
                flightField.getY(),
                flightField.getWidth() + 8,
                18);
        EETheme.bevel(
                g,
                flightField.getX() - 4,
                flightField.getY(),
                flightField.getWidth() + 8,
                18,
                EETheme.INPUT,
                true);
        ((EditBoxAccessor) flightField).ee$setTextY(flightField.getY() + 5);
        EETheme.bevel(
                g,
                left + 12,
                listTop,
                rowRight() - left - 12,
                listBottom - listTop,
                EETheme.INPUT,
                true);
        g.enableScissor(left + 13, listTop + 1, rowRight() - 1, listBottom - 1);
        if (explosions.isEmpty()) {
            g.text(font, "No explosions", left + 22, listTop + 17, EETheme.LIGHT, false);
            g.text(font, "This rocket won't explode.", left + 22, listTop + 32, 0xFFAEB8AB, false);
        }
        for (int i = 0; i < explosions.size(); i++) {
            int y = listTop + i * ROW_HEIGHT - listScroll;
            if (y + ROW_HEIGHT <= listTop || y >= listBottom) continue;
            boolean hover = !hasOpenMenu() && inList(mx, my) && my >= y && my < y + ROW_HEIGHT;
            boolean highlight = i == selected || hover;
            if (highlight)
                EETheme.rect(g, left + 13, y, rowRight() - left - 14, ROW_HEIGHT, EETheme.CONTROL);
            ExplosionInformation exp = explosions.get(i);
            GuiUtils.drawItemStack(g, star(exp), left + 19, y + 13);
            String name = (i + 1) + ". " + shapeName(exp.getType());
            g.text(
                    font,
                    fit(name, rowRight() - left - 116),
                    left + 40,
                    y + 7,
                    highlight ? EETheme.TEXT : EETheme.LIGHT,
                    false);
            int limit = rowRight() - 78;
            int x = swatches(g, exp.getColors(), left + 40, y + 23, limit);
            if (exp.getFadeColors().length > 0 && x + 25 < limit) {
                g.text(font, "→", x + 1, y + 23, highlight ? EETheme.TEXT : EETheme.LIGHT, false);
                swatches(g, exp.getFadeColors(), x + 13, y + 23, limit);
            }
            if (hover && mx < rowRight() - 74) g.setTooltipForNextFrame(font, star(exp), mx, my);
        }
        g.disableScissor();
        if (maxListScroll() > 0)
            EETheme.scrollbar(
                    g,
                    left + panelWidth - 17,
                    listTop,
                    4,
                    listBottom - listTop,
                    (int) Math.round(thumbPosition()),
                    thumbHeight(),
                    draggingList);
        EETheme.rect(g, left + 12, listBottom + 5, panelWidth - 24, 1, 0xFF969E91);
        renderWidgetsAndMenu(g, mx, my, delta);
    }

    private int swatches(GuiGraphicsExtractor g, int[] colors, int x, int y, int limit) {
        for (int color : colors.length == 0 ? new int[] {0} : colors) {
            if (x + 8 > limit) break;
            EETheme.rect(g, x, y, 8, 8, 0xFF000000 | color);
            x += 11;
        }
        return x;
    }

    private String fit(String text, int space) {
        return font.width(text) <= space
                ? text
                : font.plainSubstrByWidth(text, Math.max(0, space - font.width("..."))) + "...";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (hasOpenMenu()) return super.mouseClicked(event, doubleClick);
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (maxListScroll() > 0
                    && event.x() >= left + panelWidth - 21
                    && event.x() < left + panelWidth - 10
                    && event.y() >= listTop
                    && event.y() < listBottom) {
                clearFocus();
                draggingList = true;
                dragGrab =
                        event.y() >= thumbPosition() && event.y() < thumbPosition() + thumbHeight()
                                ? event.y() - thumbPosition()
                                : thumbHeight() / 2.0;
                dragList(event.y());
                return true;
            }
            if (inList(event.x(), event.y())) {
                int index = ((int) event.y() - listTop + listScroll) / ROW_HEIGHT;
                if (index < explosions.size()) {
                    EEButton edit = editButtons.get(index), remove = removeButtons.get(index);
                    if (edit.visible && edit.isMouseOver(event.x(), event.y()))
                        return edit.mouseClicked(event, doubleClick);
                    if (remove.visible && remove.isMouseOver(event.x(), event.y()))
                        return remove.mouseClicked(event, doubleClick);
                    selected = index;
                    init();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingList && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragList(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingList) {
            draggingList = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (hasOpenMenu()) return super.mouseScrolled(x, y, horizontal, vertical);
        if (flightField.isMouseOver(x, y)) {
            if (!errors.containsKey("flight"))
                flightField.setValue(
                        Integer.toString(Math.clamp(flight + (int) Math.signum(vertical), 0, 255)));
            return true;
        }
        if (x >= left + 12 && x < left + panelWidth - 10 && y >= listTop && y < listBottom) {
            listScroll -= (int) (vertical * ROW_HEIGHT);
            layoutList();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!hasOpenMenu() && event.hasControlDown() && event.key() == InputConstants.KEY_S) {
            applyRocket();
            return true;
        }
        if (!hasOpenMenu()
                && flightField.isFocused()
                && !errors.containsKey("flight")
                && (event.key() == InputConstants.KEY_UP
                        || event.key() == InputConstants.KEY_DOWN)) {
            flightField.setValue(
                    Integer.toString(
                            Math.clamp(
                                    flight + (event.key() == InputConstants.KEY_UP ? 1 : -1),
                                    0,
                                    255)));
            return true;
        }
        return super.keyPressed(event);
    }

    public static class GuiExplosionModifier extends EESettingsScreen<ExplosionInformation> {
        private final ExplosionInformation exp;
        private final CompoundTag original;
        private final List<Integer> colors = new ArrayList<>(), fadeColors = new ArrayList<>();
        private final String applyLabel;
        private final List<PlacedControl> controls = new ArrayList<>();
        private final EEButton[] addColors = new EEButton[2];
        private int contentHeight, paletteColumns, fadeTop, paletteScroll;
        private boolean paletteDragging;
        private double paletteGrab;
        private ColorPopup colorPopup;

        private record PlacedControl(AbstractWidget widget, int x, int y) {}

        private static final class ColorPopup {
            final int target, index, x, y, width, height;
            final List<EEButton> buttons = new ArrayList<>();
            int focus;

            ColorPopup(int target, int index, int x, int y, int width, int height) {
                this.target = target;
                this.index = index;
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
        }

        public GuiExplosionModifier(
                Screen parent, Consumer<ExplosionInformation> setter, ExplosionInformation exp) {
            this(parent, setter, exp, "Firework star", "Apply");
        }

        private GuiExplosionModifier(
                Screen parent,
                Consumer<ExplosionInformation> setter,
                ExplosionInformation exp,
                String title,
                String applyLabel) {
            super(parent, title, setter);
            this.exp = copy(exp);
            original = exp.getTag().copy();
            this.applyLabel = applyLabel;
            Arrays.stream(exp.getColors()).forEach(colors::add);
            Arrays.stream(exp.getFadeColors()).forEach(fadeColors::add);
        }

        @Override
        public boolean isModified() {
            return !result().getTag().equals(original);
        }

        @Override
        protected ExplosionInformation result() {
            return new ExplosionInformation(
                    exp.getType(),
                    exp.isTrail(),
                    exp.isFlicker(),
                    colors.stream().mapToInt(Integer::intValue).toArray(),
                    fadeColors.stream().mapToInt(Integer::intValue).toArray());
        }

        @Override
        protected ItemStack preview() {
            return star(result());
        }

        @Override
        protected void buildRows() {}

        private List<Integer> palette(int target) {
            return target == 0 ? colors : fadeColors;
        }

        @Override
        public void init() {
            dismissMenu();
            colorPopup = null;
            clearFocus();
            clearWidgets();
            controls.clear();
            paletteDragging = false;
            panelWidth = Math.min(420, width - 16);
            paletteColumns = Math.max(1, (panelWidth - 38) / 26);
            fadeTop = 50 + paletteHeight(colors) + 6;
            contentHeight = fadeTop + paletteHeight(fadeColors);
            panelHeight = Math.min(Math.min(350, height - 16), Math.max(194, contentHeight + 72));
            left = (width - panelWidth) / 2;
            top = (height - panelHeight) / 2;
            viewTop = top + 33;
            viewBottom = top + panelHeight - 39;
            var shape =
                    dropdown(
                            shapeName(exp.getType()),
                            List.of(Shape.values()),
                            GuiFireworksModifer::shapeName,
                            exp::type);
            shape.setWidth(Math.min(170, (panelWidth - 40) / 2));
            shape.setTooltip(Tooltip.create(Component.literal("The pattern of the explosion.")));
            place(shape, panelWidth - 26 - shape.getWidth(), 0);
            var trail = toggle(exp::isTrail, exp::trail);
            trail.setTooltip(
                    Tooltip.create(Component.literal("Leave trails behind the particles.")));
            place(trail, 68, 24);
            var twinkle = toggle(exp::isFlicker, exp::flicker);
            twinkle.setTooltip(
                    Tooltip.create(Component.literal("Particles sparkle as they fade.")));
            place(twinkle, panelWidth - 78, 24);
            paletteControls(0, 50);
            paletteControls(1, fadeTop);
            layoutControls();
            int half = (panelWidth - 28) / 2;
            addRenderableWidget(
                    new EEButton(
                            left + 12,
                            top + panelHeight - 27,
                            half,
                            20,
                            Component.literal("Cancel"),
                            b -> onCancel()));
            addRenderableWidget(
                    new EEButton(
                            left + 16 + half,
                            top + panelHeight - 27,
                            half,
                            20,
                            Component.literal(applyLabel),
                            b -> {
                                setter.accept(result());
                                mc.gui.setScreen(parent);
                            }));
        }

        private int paletteHeight(List<Integer> palette) {
            return 22
                    + Math.max(1, (palette.size() + 1 + paletteColumns - 1) / paletteColumns) * 24;
        }

        private void place(AbstractWidget widget, int x, int y) {
            controls.add(new PlacedControl(widget, x, y));
            addWidget(widget);
        }

        private static String colorName(int color) {
            for (DyeColor dye : DyeColor.values())
                if ((color & 0xFFFFFF) == dye.getFireworkColor())
                    return I18n.get("item.minecraft.firework_star." + dye.getName());
            return "Custom color";
        }

        private EEButton swatch(
                int x, int y, int value, boolean selected, String name, Runnable choose) {
            return new EEButton(x, y, 22, 22, Component.literal(name), b -> choose.run()) {
                @Override
                protected void extractContents(
                        GuiGraphicsExtractor g, int mx, int my, float delta) {
                    EETheme.bevel(
                            g,
                            getX(),
                            getY(),
                            getWidth(),
                            getHeight(),
                            EETheme.controlColor(selected, isHoveredOrFocused(), active),
                            false);
                    EETheme.bevel(
                            g,
                            getX() + 3,
                            getY() + 3,
                            getWidth() - 6,
                            getHeight() - 6,
                            0xFF000000 | value,
                            true);
                    if (selected) {
                        EETheme.rect(g, getRight() - 10, getBottom() - 10, 8, 8, EETheme.ON);
                        g.text(font, "✓", getRight() - 10, getBottom() - 11, EETheme.LIGHT, false);
                    }
                }
            };
        }

        private void paletteControls(int target, int sectionY) {
            List<Integer> palette = palette(target);
            for (int i = 0; i < palette.size(); i++) {
                int index = i, value = palette.get(i);
                String hex = String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF);
                EEButton color =
                        swatch(
                                0,
                                0,
                                value,
                                false,
                                colorName(value),
                                () -> openPalette(target, index));
                color.setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        colorName(value)
                                                + " · "
                                                + hex
                                                + "\nChange or remove this color.")));
                place(
                        color,
                        18 + (i % paletteColumns) * 26,
                        sectionY + 18 + (i / paletteColumns) * 24);
            }
            int index = palette.size();
            EEButton add =
                    new EEButton(
                            0, 0, 22, 22, Component.literal("+"), b -> openPalette(target, -1));
            add.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    target == 0 ? "Add explosion colors." : "Add fade colors.")));
            addColors[target] = add;
            place(
                    add,
                    palette.isEmpty() ? panelWidth - 48 : 18 + (index % paletteColumns) * 26,
                    sectionY + 18 + (index / paletteColumns) * 24);
        }

        private void openPalette(int target, int index) {
            openPalette(target, index, null);
        }

        private void openPalette(int target, int index, ColorPopup previous) {
            clearFocus();
            dismissMenu();
            AbstractWidget anchor = addColors[target];
            if (index >= 0) {
                int sectionY = target == 0 ? 50 : fadeTop;
                int anchorY =
                        viewTop + sectionY + 18 + (index / paletteColumns) * 24 - paletteScroll;
                int anchorX = left + 18 + (index % paletteColumns) * 26;
                for (PlacedControl c : controls)
                    if (c.widget.getX() == anchorX && c.widget.getY() == anchorY) {
                        anchor = c.widget;
                        break;
                    }
            }
            int w = 218, h = 106;
            int x =
                    Math.clamp(
                            previous == null ? anchor.getX() : previous.x,
                            left + 12,
                            left + panelWidth - w - 12);
            int y =
                    Math.clamp(
                            previous == null ? anchor.getBottom() + 3 : previous.y,
                            top + 31,
                            viewBottom - h);
            ColorPopup popup = new ColorPopup(target, index, x, y, w, h);
            colorPopup = popup;
            List<Integer> palette = palette(target);
            for (int i = 0; i < DyeColor.values().length; i++) {
                DyeColor dye = DyeColor.values()[i];
                int value = dye.getFireworkColor();
                boolean selected =
                        index < 0 ? palette.contains(value) : palette.get(index) == value;
                String name = colorName(value);
                EEButton button =
                        swatch(
                                x + 7 + i % 8 * 26,
                                y + 22 + i / 8 * 26,
                                value,
                                selected,
                                name,
                                () -> choosePreset(target, index, value));
                button.setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        (index < 0 ? selected ? "Remove " : "Add " : "Use ")
                                                + name.toLowerCase(Locale.ROOT)
                                                + ".")));
                popup.buttons.add(button);
                if (selected && index >= 0) popup.focus = i;
            }
            EEButton custom =
                    new EEButton(
                            x + 7,
                            y + 80,
                            index >= 0 ? 178 : 204,
                            20,
                            Component.literal("Custom color..."),
                            b -> customColor(target, index));
            custom.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "Choose a custom color using the wheel, hex code or recent"
                                        + " colors.")));
            popup.buttons.add(custom);
            if (index >= 0) {
                EEButton remove =
                        new EEButton(
                                x + 191,
                                y + 80,
                                20,
                                20,
                                Component.literal("×"),
                                b -> {
                                    palette(target).remove(index);
                                    rebuild();
                                });
                remove.setTooltip(Tooltip.create(Component.literal("Remove this color.")));
                popup.buttons.add(remove);
            }
        }

        private void choosePreset(int target, int index, int value) {
            ColorPopup previous = colorPopup;
            List<Integer> palette = palette(target);
            if (index >= 0) palette.set(index, value);
            else if (palette.contains(value)) palette.removeIf(color -> color == value);
            else palette.add(value);
            rebuild();
            if (index < 0) {
                openPalette(target, -1, previous);
                for (int i = 0; i < DyeColor.values().length; i++)
                    if (DyeColor.values()[i].getFireworkColor() == value) colorPopup.focus = i;
            }
        }

        private void customColor(int target, int index) {
            List<Integer> palette = palette(target);
            int initial =
                    index < 0
                            ? palette.isEmpty()
                                    ? DyeColor.RED.getFireworkColor()
                                    : palette.getLast()
                            : palette.get(index);
            colorPopup = null;
            mc.gui.setScreen(
                    new GuiColorModifier(
                            this,
                            value -> {
                                if (index < 0) palette.add(value);
                                else palette.set(index, value);
                            },
                            initial,
                            initial));
        }

        private int maxPaletteScroll() {
            return Math.max(0, contentHeight - (viewBottom - viewTop));
        }

        private int paletteThumbHeight() {
            int h = viewBottom - viewTop;
            return Math.max(18, h * h / Math.max(h, contentHeight));
        }

        private double paletteThumbY() {
            return viewTop
                    + (viewBottom - viewTop - paletteThumbHeight())
                            * (double) paletteScroll
                            / Math.max(1, maxPaletteScroll());
        }

        private boolean inPaletteView(double x, double y) {
            return x >= left + 12 && x < left + panelWidth - 22 && y >= viewTop && y < viewBottom;
        }

        private void layoutControls() {
            paletteScroll = Math.clamp(paletteScroll, 0, maxPaletteScroll());
            for (PlacedControl control : controls) {
                control.widget.setPosition(left + control.x, viewTop + control.y - paletteScroll);
                control.widget.visible =
                        control.widget.getBottom() > viewTop && control.widget.getY() < viewBottom;
                if (!control.widget.visible && control.widget.isFocused()) clearFocus();
            }
        }

        private void dragPalette(double y) {
            int travel = viewBottom - viewTop - paletteThumbHeight();
            if (travel > 0)
                paletteScroll =
                        (int) Math.round((y - paletteGrab - viewTop) / travel * maxPaletteScroll());
            layoutControls();
        }

        private void renderPalette(
                GuiGraphicsExtractor g,
                List<Integer> palette,
                boolean fade,
                int sectionY,
                int mx,
                int my) {
            int y = viewTop + sectionY - paletteScroll;
            g.text(
                    font,
                    fade ? "Fade colors" : "Explosion colors",
                    left + 16,
                    y,
                    EETheme.TEXT,
                    false);
            EETheme.bevel(
                    g,
                    left + 12,
                    y + 14,
                    panelWidth - 34,
                    paletteHeight(palette) - 14,
                    EETheme.INPUT,
                    true);
            if (palette.isEmpty())
                g.text(
                        font,
                        fade ? "No fade" : "Black by default",
                        left + 20,
                        y + 25,
                        EETheme.LIGHT,
                        false);
            if (colorPopup == null
                    && !hasOpenMenu()
                    && inPaletteView(mx, my)
                    && my >= y
                    && my < y + 14)
                g.setTooltipForNextFrame(
                        font,
                        font.split(
                                Component.literal(
                                        fade
                                                ? "Particles change to these colors as they fade."
                                                      + " Leave empty to keep the explosion colors."
                                                : "Particles start in these colors. With no colors"
                                                      + " selected, Minecraft uses black."),
                                Math.min(240, width - 24)),
                        mx,
                        my);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
            EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
            GuiUtils.drawItemStack(g, preview(), left + 12, top + 9);
            g.text(font, title, left + 36, top + 13, EETheme.TEXT, false);
            g.enableScissor(left + 12, viewTop, left + panelWidth - 22, viewBottom);
            int y = viewTop - paletteScroll;
            g.text(font, "Shape", left + 16, y + 6, EETheme.TEXT, false);
            g.text(font, "Trail", left + 16, y + 30, EETheme.TEXT, false);
            g.text(font, "Twinkle", left + panelWidth / 2 + 12, y + 30, EETheme.TEXT, false);
            renderPalette(g, colors, false, 50, mx, my);
            renderPalette(g, fadeColors, true, fadeTop, mx, my);
            boolean hover = colorPopup == null && !hasOpenMenu() && inPaletteView(mx, my);
            for (PlacedControl control : controls)
                if (control.widget.visible)
                    control.widget.extractRenderState(g, hover ? mx : -1, hover ? my : -1, delta);
            g.disableScissor();
            if (maxPaletteScroll() > 0)
                EETheme.scrollbar(
                        g,
                        left + panelWidth - 17,
                        viewTop,
                        4,
                        viewBottom - viewTop,
                        (int) Math.round(paletteThumbY()),
                        paletteThumbHeight(),
                        paletteDragging);
            EETheme.rect(g, left + 12, viewBottom + 6, panelWidth - 24, 1, 0xFF969E91);
            renderWidgetsAndMenu(
                    g, colorPopup == null ? mx : -1, colorPopup == null ? my : -1, delta);
            if (colorPopup != null) {
                ColorPopup popup = colorPopup;
                EETheme.bevel(
                        g, popup.x, popup.y, popup.width, popup.height, EETheme.OPTION, false);
                g.text(
                        font,
                        popup.index < 0
                                ? popup.target == 0 ? "Explosion colors" : "Fade colors"
                                : "Change color",
                        popup.x + 7,
                        popup.y + 7,
                        EETheme.TEXT,
                        false);
                for (int i = 0; i < popup.buttons.size(); i++) {
                    EEButton button = popup.buttons.get(i);
                    button.setFocused(i == popup.focus);
                    button.extractRenderState(g, mx, my, delta);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (colorPopup != null) {
                for (EEButton button : colorPopup.buttons)
                    if (button.isMouseOver(event.x(), event.y()))
                        return button.mouseClicked(event, doubleClick);
                if (event.x() < colorPopup.x
                        || event.x() >= colorPopup.x + colorPopup.width
                        || event.y() < colorPopup.y
                        || event.y() >= colorPopup.y + colorPopup.height) colorPopup = null;
                return true;
            }
            if (hasOpenMenu()) return super.mouseClicked(event, doubleClick);
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && maxPaletteScroll() > 0
                    && event.x() >= left + panelWidth - 21
                    && event.x() < left + panelWidth - 10
                    && event.y() >= viewTop
                    && event.y() < viewBottom) {
                clearFocus();
                paletteDragging = true;
                paletteGrab =
                        event.y() >= paletteThumbY()
                                        && event.y() < paletteThumbY() + paletteThumbHeight()
                                ? event.y() - paletteThumbY()
                                : paletteThumbHeight() / 2.0;
                dragPalette(event.y());
                return true;
            }
            if (!inPaletteView(event.x(), event.y()))
                for (PlacedControl control : controls) control.widget.visible = false;
            boolean handled = super.mouseClicked(event, doubleClick);
            layoutControls();
            return handled;
        }

        @Override
        public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            if (colorPopup != null) return true;
            if (hasOpenMenu()) return super.mouseScrolled(x, y, horizontal, vertical);
            if (x >= left + 12 && x < left + panelWidth - 10 && y >= viewTop && y < viewBottom) {
                paletteScroll -= (int) (vertical * 24);
                layoutControls();
                return true;
            }
            return super.mouseScrolled(x, y, horizontal, vertical);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
            if (paletteDragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                dragPalette(event.y());
                return true;
            }
            return super.mouseDragged(event, dx, dy);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            if (paletteDragging) {
                paletteDragging = false;
                return true;
            }
            return super.mouseReleased(event);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (colorPopup != null) {
                if (event.key() == InputConstants.KEY_ESCAPE) colorPopup = null;
                else if (event.key() == InputConstants.KEY_RETURN) {
                    EEButton button = colorPopup.buttons.get(colorPopup.focus);
                    button.mouseClicked(
                            new MouseButtonEvent(
                                    button.getX() + 10,
                                    button.getY() + 10,
                                    new net.minecraft.client.input.MouseButtonInfo(
                                            InputConstants.MOUSE_BUTTON_LEFT, 0)),
                            false);
                } else if (event.key() == InputConstants.KEY_TAB)
                    colorPopup.focus = (colorPopup.focus + 1) % colorPopup.buttons.size();
                else if (event.key() == InputConstants.KEY_LEFT
                        || event.key() == InputConstants.KEY_RIGHT
                        || event.key() == InputConstants.KEY_UP
                        || event.key() == InputConstants.KEY_DOWN) {
                    int direction =
                            event.key() == InputConstants.KEY_LEFT
                                    ? -1
                                    : event.key() == InputConstants.KEY_RIGHT
                                            ? 1
                                            : event.key() == InputConstants.KEY_UP ? -8 : 8;
                    colorPopup.focus =
                            Math.clamp(
                                    colorPopup.focus + direction, 0, colorPopup.buttons.size() - 1);
                }
                return true;
            }
            return super.keyPressed(event);
        }
    }
}
