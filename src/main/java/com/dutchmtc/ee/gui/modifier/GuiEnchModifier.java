package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EEDropdownButton;
import com.dutchmtc.ee.gui.components.EETextField;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.Tuple;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.*;
import java.util.function.Consumer;

/** Inline levels and a searchable catalog share one draft, including across resizing. */
public class GuiEnchModifier extends GuiModifier<List<Tuple<Enchantment, Integer>>> {
    private static final int ROW_HEIGHT = 32;
    private final List<Tuple<Enchantment, Integer>> original = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Enchantment> catalog = new ArrayList<>(), filtered = new ArrayList<>();
    private final boolean stored;
    private EditBox search;
    private EEButton apply;
    private EEDropdownButton options;
    private String query = "", savedQuery = "";
    private int left, top, panelWidth, panelHeight, listTop, listBottom, listRight;
    private int scroll, savedScroll, keyboardRow = -1, optionRow;
    private boolean choosing, dragging, menu;
    private double grabOffset;

    private static final class Entry {
        final Enchantment enchantment;
        String level;
        EditBox field;
        EEButton remove;

        Entry(Enchantment enchantment, int level) {
            this.enchantment = enchantment;
            this.level = Integer.toString(level);
        }

        int value() {
            try {
                return Integer.parseInt(level);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        boolean valid() {
            return value() >= 1 && value() <= 255;
        }
    }

    public GuiEnchModifier(
            Screen parent,
            List<Tuple<Enchantment, Integer>> enchantments,
            Consumer<List<Tuple<Enchantment, Integer>>> setter) {
        this(parent, enchantments, setter, false);
    }

    public GuiEnchModifier(
            Screen parent,
            List<Tuple<Enchantment, Integer>> enchantments,
            Consumer<List<Tuple<Enchantment, Integer>>> setter,
            boolean stored) {
        super(parent, Component.translatable("gui.ee.modifier.ench"), setter);
        this.stored = stored;
        for (var pair : enchantments) {
            if (pair == null || pair.a == null) continue;
            int level = pair.b == null ? 1 : pair.b;
            original.add(new Tuple<>(pair.a, level));
            Entry existing = entry(pair.a);
            if (existing == null) entries.add(new Entry(pair.a, level));
            else existing.level = Integer.toString(level);
        }
        if (mc.level != null)
            mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).forEach(catalog::add);
        catalog.sort(Comparator.comparing(GuiEnchModifier::name, String.CASE_INSENSITIVE_ORDER));
    }

    private static String name(Enchantment e) {
        return e.description().getString();
    }

    private Entry entry(Enchantment enchantment) {
        return entries.stream()
                .filter(e -> Objects.equals(e.enchantment, enchantment))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected void setInitialFocus() {}

    @Override
    protected void rebuildWidgets() {
        // init saves the active entry before clearing widgets, including during GUI scaling.
        init();
    }

    @Override
    public void onClose() {
        if (choosing) back();
        else onCancel();
    }

    @Override
    public void init() {
        Entry focused =
                entries.stream()
                        .filter(e -> e.field != null && e.field.isFocused())
                        .findFirst()
                        .orElse(null);
        clearFocus();
        clearWidgets();
        menu = dragging = false;
        panelWidth = Math.min(460, width - 16);
        panelHeight = Math.min(330, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        listTop = top + 80;
        listBottom = top + panelHeight - 38;
        listRight = left + panelWidth - 22;
        options = null;
        apply = null;
        if (!choosing) {
            options =
                    addRenderableWidget(
                            new EEDropdownButton(
                                    left + panelWidth - 100,
                                    top + 10,
                                    88,
                                    20,
                                    Component.literal("Options"),
                                    b -> {
                                        clearFocus();
                                        menu = !menu;
                                        optionRow = 0;
                                    }));
            options.active = !entries.isEmpty();
            addRenderableWidget(
                    new EEButton(
                            left + panelWidth - 134,
                            top + 40,
                            122,
                            20,
                            Component.literal("Add enchantment"),
                            b -> beginAdding()));
        }
        search =
                addRenderableWidget(
                        input(
                                left + 18,
                                top + 41,
                                panelWidth - (choosing ? 36 : 162),
                                "Search...",
                                query));
        search.setResponder(
                value -> {
                    query = value;
                    scroll = 0;
                    keyboardRow = -1;
                    filter();
                });
        if (!choosing)
            for (Entry e : entries) {
                e.field = addWidget(input(listRight - 74, listTop + 6, 42, "Level", e.level));
                e.field.setMaxLength(8);
                e.field.setResponder(
                        value -> {
                            e.level = value;
                            refreshValidity();
                        });
                e.remove =
                        addWidget(
                                new EEButton(
                                        listRight - 24,
                                        listTop + 5,
                                        18,
                                        20,
                                        Component.literal("×").withColor(0xFF5555),
                                        b -> {
                                            entries.remove(e);
                                            init();
                                        }) {
                                    @Override
                                    protected void extractContents(
                                            GuiGraphicsExtractor g, int mx, int my, float delta) {
                                        super.extractContents(g, mx, my, delta);
                                    }
                                });
                e.remove.setTooltip(
                        Tooltip.create(Component.literal("Remove " + name(e.enchantment))));
            }
        int footerY = top + panelHeight - 28;
        if (choosing)
            addRenderableWidget(
                    new EEButton(
                            left + 12,
                            footerY,
                            panelWidth - 24,
                            20,
                            Component.literal("Back"),
                            b -> back()));
        else {
            int w = (panelWidth - 28) / 2;
            addRenderableWidget(
                    new EEButton(
                            left + 12,
                            footerY,
                            w,
                            20,
                            Component.translatable("gui.ee.cancel"),
                            b -> onCancel()));
            apply =
                    addRenderableWidget(
                            new EEButton(
                                    left + 16 + w,
                                    footerY,
                                    w,
                                    20,
                                    Component.literal("Apply"),
                                    b -> {
                                        if (entries.stream().allMatch(Entry::valid)) {
                                            set(get());
                                            mc.gui.setScreen(parent);
                                        }
                                    }));
        }
        filter();
        refreshValidity();
        if (!choosing && focused != null && filtered.contains(focused.enchantment)) {
            int index = filtered.indexOf(focused.enchantment);
            scroll =
                    Math.clamp(
                            scroll,
                            Math.max(0, (index + 1) * ROW_HEIGHT - (listBottom - listTop)),
                            Math.min(maxScroll(), index * ROW_HEIGHT));
            positionRows();
            setFocused(focused.field);
        }
    }

    private EditBox input(int x, int y, int width, String hint, String value) {
        EditBox box = new EETextField(font, x, y, width, 18, Component.literal(hint));
        box.setBordered(false);
        box.setTextShadow(false);
        box.setMaxLength(128);
        box.setTextColor(EETheme.LIGHT);
        box.setHint(Component.literal(hint).withColor(0xAEBBB5));
        box.setValue(value);
        return box;
    }

    private void refreshValidity() {
        for (Entry e : entries)
            if (e.field != null)
                EETextField.validate(
                        e.field, e.valid() ? null : "Enter a whole level from 1 to 255.");
        Entry invalid = entries.stream().filter(e -> !e.valid()).findFirst().orElse(null);
        if (apply != null) {
            apply.active = invalid == null;
            apply.setTooltip(
                    invalid == null
                            ? null
                            : Tooltip.create(
                                    Component.literal(
                                            name(invalid.enchantment)
                                                    + ": enter a level from 1 to 255.")));
        }
    }

    private void filter() {
        filtered.clear();
        String[] tokens = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
        List<Enchantment> source =
                choosing ? catalog : entries.stream().map(e -> e.enchantment).toList();
        for (Enchantment e : source) {
            String text = name(e).toLowerCase(Locale.ROOT);
            boolean match = true;
            for (String token : tokens)
                if (!text.contains(token)) {
                    match = false;
                    break;
                }
            if (match) filtered.add(e);
        }
        scroll = Math.clamp(scroll, 0, maxScroll());
        positionRows();
    }

    private void positionRows() {
        if (choosing) return;
        for (Entry e : entries) {
            int index = filtered.indexOf(e.enchantment), y = listTop + index * ROW_HEIGHT - scroll;
            boolean visible = index >= 0 && y + 25 > listTop && y + 5 < listBottom;
            e.field.setY(y + 6);
            e.remove.setY(y + 5);
            e.field.visible = e.remove.visible = visible;
            if (!visible && e.field.isFocused()) {
                e.field.setFocused(false);
                if (getFocused() == e.field) setFocused(null);
            }
        }
    }

    private void beginAdding() {
        savedQuery = query;
        savedScroll = scroll;
        query = "";
        scroll = 0;
        keyboardRow = -1;
        choosing = true;
        init();
        setFocused(search);
    }

    private void back() {
        choosing = false;
        query = savedQuery;
        scroll = savedScroll;
        keyboardRow = -1;
        init();
    }

    private void choose(int index) {
        if (index < 0 || index >= filtered.size() || entry(filtered.get(index)) != null) return;
        Entry e = new Entry(filtered.get(index), 1);
        entries.add(e);
        choosing = false;
        query = "";
        scroll = 0;
        keyboardRow = -1;
        init();
        int row = filtered.indexOf(e.enchantment);
        scroll = Math.clamp((row + 1) * ROW_HEIGHT - (listBottom - listTop), 0, maxScroll());
        positionRows();
        setFocused(e.field);
        e.field.moveCursorToEnd(false);
        e.field.setHighlightPos(0);
        playClick();
    }

    private int maxScroll() {
        return Math.max(0, filtered.size() * ROW_HEIGHT - (listBottom - listTop));
    }

    private int thumbHeight() {
        int h = listBottom - listTop;
        return Math.max(16, h * h / Math.max(h, filtered.size() * ROW_HEIGHT));
    }

    private int thumbY() {
        return listTop + (listBottom - listTop - thumbHeight()) * scroll / Math.max(1, maxScroll());
    }

    private int at(double x, double y) {
        if (x < left + 12 || x >= listRight || y < listTop || y >= listBottom) return -1;
        int row = ((int) y - listTop + scroll) / ROW_HEIGHT;
        return row < filtered.size() ? row : -1;
    }

    private void dragTo(double y) {
        int travel = listBottom - listTop - thumbHeight();
        if (travel > 0)
            scroll =
                    (int)
                            Math.round(
                                    Math.clamp((y - grabOffset - listTop) / travel, 0, 1)
                                            * maxScroll());
        positionRows();
    }

    private int menuX() {
        return left + panelWidth - 188;
    }

    private int menuY() {
        return top + 33;
    }

    private void selectOption(int index) {
        menu = false;
        mc.gui.setScreen(
                new GuiConfirmation(
                        this,
                        Component.literal(
                                index == 0
                                        ? "Set all enchantments to their normal maximum?"
                                        : "Remove all enchantments?"),
                        Component.literal(index == 0 ? "Set levels" : "Remove all"),
                        () -> {
                            if (index == 0)
                                for (Entry e : entries)
                                    e.level =
                                            Integer.toString(
                                                    Math.clamp(
                                                            e.enchantment.getMaxLevel(), 1, 255));
                            else entries.clear();
                            mc.gui.setScreen(this);
                        },
                        () -> mc.gui.setScreen(this)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && event.x() >= menuX()
                    && event.x() < menuX() + 176
                    && event.y() >= menuY()
                    && event.y() < menuY() + 40) selectOption(((int) event.y() - menuY()) / 20);
            else menu = false;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (maxScroll() > 0
                    && event.x() >= listRight + 2
                    && event.x() < listRight + 10
                    && event.y() >= listTop
                    && event.y() < listBottom) {
                clearFocus();
                dragging = true;
                grabOffset =
                        event.y() >= thumbY() && event.y() < thumbY() + thumbHeight()
                                ? event.y() - thumbY()
                                : thumbHeight() / 2.0;
                dragTo(event.y());
                return true;
            }
            int index = at(event.x(), event.y());
            if (choosing && index >= 0) {
                clearFocus();
                choose(index);
                return true;
            }
            if (!choosing && index >= 0 && event.x() < listRight - 80) {
                Entry e = entry(filtered.get(index));
                if (e.field.visible) {
                    setFocused(e.field);
                    e.field.moveCursorToEnd(false);
                    e.field.setHighlightPos(0);
                }
                return true;
            }
        }
        // Rows are rendered inside a scissor; their clipped portions must not receive clicks.
        if (!choosing && (event.y() < listTop || event.y() >= listBottom))
            for (Entry e : entries) e.field.visible = e.remove.visible = false;
        boolean handled = super.mouseClicked(event, doubleClick);
        positionRows();
        if (getFocused() != null && !children().contains(getFocused()))
            setFocused(choosing ? search : null);
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (menu) return true;
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return menu || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (menu) return true;
        if (!choosing && y >= listTop && y < listBottom)
            for (Entry e : entries)
                if (e.field.visible
                        && x >= e.field.getX() - 6
                        && x < e.field.getRight() + 6
                        && y >= e.field.getY() - 1
                        && y < e.field.getBottom() + 1) {
                    changeLevel(e, vertical > 0 ? 1 : vertical < 0 ? -1 : 0);
                    return true;
                }
        if (x >= left + 12 && x < listRight + 10 && y >= listTop && y < listBottom) {
            scroll =
                    Math.clamp(
                            scroll - (int) Math.round(vertical * ROW_HEIGHT * 2), 0, maxScroll());
            positionRows();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private void changeLevel(Entry e, int delta) {
        if (delta != 0 && e.valid())
            e.field.setValue(Integer.toString(Math.clamp(e.value() + delta, 1, 255)));
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return menu || super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (menu) {
            if (key == InputConstants.KEY_ESCAPE) menu = false;
            else if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN)
                optionRow = key == InputConstants.KEY_UP ? 0 : 1;
            else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)
                selectOption(optionRow);
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE && choosing) {
            back();
            return true;
        }
        if (event.hasControlDown() && key == InputConstants.KEY_F) {
            setFocused(search);
            return true;
        }
        if (!choosing && (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN))
            for (Entry e : entries)
                if (e.field.isFocused()) {
                    changeLevel(e, key == InputConstants.KEY_UP ? 1 : -1);
                    return true;
                }
        if (choosing
                && (getFocused() == null || search.isFocused() && key == InputConstants.KEY_DOWN)) {
            if (!filtered.isEmpty()
                    && (key == InputConstants.KEY_DOWN
                            || key == InputConstants.KEY_UP
                            || key == InputConstants.KEY_HOME
                            || key == InputConstants.KEY_END)) {
                clearFocus();
                keyboardRow =
                        Math.clamp(
                                key == InputConstants.KEY_HOME
                                        ? 0
                                        : key == InputConstants.KEY_END
                                                ? filtered.size() - 1
                                                : keyboardRow
                                                        + (key == InputConstants.KEY_UP ? -1 : 1),
                                0,
                                filtered.size() - 1);
                scroll =
                        Math.clamp(
                                scroll,
                                Math.max(
                                        0, (keyboardRow + 1) * ROW_HEIGHT - (listBottom - listTop)),
                                Math.min(maxScroll(), keyboardRow * ROW_HEIGHT));
                return true;
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                choose(keyboardRow);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void inputSurface(GuiGraphicsExtractor g, EditBox box, boolean valid) {
        EETextField.validationFrame(box, box.getX() - 6, box.getY() - 1, box.getWidth() + 12, 20);
        EETheme.bevel(
                g, box.getX() - 6, box.getY() - 1, box.getWidth() + 12, 20, EETheme.INPUT, true);
        ((EditBoxAccessor) box).ee$setTextY(box.getY() + 5);
    }

    private String fit(String text, int space) {
        return font.width(text) <= space
                ? text
                : font.plainSubstrByWidth(text, Math.max(0, space - font.width("..."))) + "...";
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0x90101010, 0xB0101010);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        GuiUtils.drawItemStack(g, new ItemStack(Items.ENCHANTED_BOOK), left + 12, top + 12);
        g.text(
                font,
                choosing ? "Add enchantment" : stored ? "Stored enchantments" : title.getString(),
                left + 34,
                top + 16,
                EETheme.TEXT,
                false);
        inputSurface(g, search, true);
        if (search.isFocused() && search.getValue().isEmpty())
            g.text(font, "Search...", search.getX(), search.getY() + 5, 0xFFAEBBB5, false);
        g.text(font, "Enchantment", left + 12, top + 67, EETheme.MUTED, false);
        if (!choosing) g.text(font, "Level", listRight - 74, top + 67, EETheme.MUTED, false);
        EETheme.bevel(
                g,
                left + 12,
                listTop - 1,
                listRight - left - 12,
                listBottom - listTop + 2,
                EETheme.INPUT,
                true);
        int hover = menu ? -1 : at(mx, my);
        g.enableScissor(left + 13, listTop, listRight - 1, listBottom);
        for (int i = scroll / ROW_HEIGHT; i < filtered.size(); i++) {
            int y = listTop + i * ROW_HEIGHT - scroll;
            if (y >= listBottom) break;
            Enchantment enchantment = filtered.get(i);
            Entry e = entry(enchantment);
            boolean highlighted =
                    hover == i || choosing && keyboardRow == i || !choosing && e.field.isFocused();
            if (highlighted)
                EETheme.rect(g, left + 13, y, listRight - left - 14, ROW_HEIGHT, 0xFF48544C);
            int textWidth = listRight - left - (choosing ? 75 : 108);
            g.text(
                    font,
                    fit(name(enchantment), textWidth),
                    left + 20,
                    y + 5,
                    choosing && e != null ? 0xFFAEBBB5 : EETheme.LIGHT,
                    false);
            g.text(
                    font,
                    "Normal max: " + enchantment.getMaxLevel(),
                    left + 20,
                    y + 18,
                    0xFFAEBBB5,
                    false);
            if (choosing)
                g.text(
                        font,
                        e == null ? "+" : "Added",
                        listRight - (e == null ? 18 : 38),
                        y + 11,
                        e == null ? 0xFFB3C8A5 : 0xFFAEBBB5,
                        false);
            else if (e.field.visible) {
                inputSurface(g, e.field, e.valid());
                int rowMouseX = !menu && my >= listTop && my < listBottom ? mx : -1;
                int rowMouseY = rowMouseX == -1 ? -1 : my;
                e.field.extractRenderState(g, rowMouseX, rowMouseY, delta);
                e.remove.extractRenderState(g, rowMouseX, rowMouseY, delta);
            }
        }
        g.disableScissor();
        if (filtered.isEmpty()) {
            g.text(
                    font,
                    query.isBlank() ? "No enchantments added." : "No matches.",
                    left + 22,
                    listTop + 12,
                    EETheme.LIGHT,
                    false);
            if (!choosing && query.isBlank())
                g.text(
                        font,
                        "Add an enchantment to get started.",
                        left + 22,
                        listTop + 28,
                        0xFFAEBBB5,
                        false);
        }
        if (maxScroll() > 0)
            EETheme.scrollbar(
                    g,
                    listRight + 4,
                    listTop,
                    4,
                    listBottom - listTop,
                    thumbY(),
                    thumbHeight(),
                    dragging);
        super.extractRenderState(g, menu ? -1 : mx, menu ? -1 : my, delta);
        if (hover >= 0) {
            Enchantment enchantment = filtered.get(hover);
            Entry e = entry(enchantment);
            String tooltip = null;
            if (choosing)
                tooltip =
                        name(enchantment)
                                + (e == null
                                        ? "\nAdd at level 1."
                                        : "\nAlready added. Change its level in the list.");
            else if (mx >= listRight - 80 && mx < listRight - 26)
                tooltip =
                        e.valid()
                                ? "Level: 1–255. Normal maximum: " + enchantment.getMaxLevel() + "."
                                : "Enter a whole level from 1 to 255.";
            else if (mx < listRight - 80 && font.width(name(enchantment)) > listRight - left - 108)
                tooltip = name(enchantment);
            if (tooltip != null)
                g.setTooltipForNextFrame(font, font.split(Component.literal(tooltip), 220), mx, my);
        }
        if (menu) {
            EETheme.bevel(g, menuX() - 1, menuY() - 1, 178, 42, EETheme.PANEL, false);
            for (int i = 0; i < 2; i++)
                EEDropdownButton.renderOption(
                        g,
                        font,
                        Component.literal(
                                i == 0 ? "Set normal maximums" : "Remove all enchantments"),
                        ItemStack.EMPTY,
                        menuX(),
                        menuY() + i * 20,
                        176,
                        20,
                        false,
                        optionRow == i
                                || mx >= menuX()
                                        && mx < menuX() + 176
                                        && my >= menuY() + i * 20
                                        && my < menuY() + (i + 1) * 20);
        }
    }

    protected List<Tuple<Enchantment, Integer>> get() {
        List<Tuple<Enchantment, Integer>> result = new ArrayList<>();
        for (Entry e : entries) result.add(new Tuple<>(e.enchantment, e.value()));
        return result;
    }

    @Override
    public boolean isModified() {
        if (entries.size() != original.size()) return true;
        for (int i = 0; i < entries.size(); i++)
            if (!Objects.equals(entries.get(i).enchantment, original.get(i).a)
                    || !Objects.equals(entries.get(i).level, Integer.toString(original.get(i).b)))
                return true;
        return false;
    }
}
