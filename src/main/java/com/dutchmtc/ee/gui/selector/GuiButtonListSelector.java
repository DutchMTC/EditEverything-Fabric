package com.dutchmtc.ee.gui.selector;

import com.dutchmtc.ee.gui.GuiEE;
import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.Tuple;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ItemLike;

import java.util.*;
import java.util.function.Function;

/**
 * Shared single-choice picker. Choosing an option returns to the caller without an extra save step.
 */
public class GuiButtonListSelector<T> extends GuiEE {
    private static final int ROW = 22;
    private final Function<T, Screen> selector;
    protected final List<Tuple<String, T>> choices = new ArrayList<>();
    protected List<Tuple<String, T>> filtered = List.of();
    protected final List<EEButton> rows = new ArrayList<>();
    private final EEDropdownScrollbar scrollbar = new EEDropdownScrollbar();
    private Function<T, ItemStack> icons = GuiButtonListSelector::nativeIcon;
    private Function<T, Integer> swatches = value -> null;
    private Function<T, String> searchKeys = value -> "";
    private Function<T, Component> details = value -> null;
    protected T current;
    protected boolean hasCurrent;
    private boolean positioned, visuals;
    protected String query = "";
    protected EETextField search;
    protected int left, top, panelWidth, panelHeight, viewTop, visibleRows, offset;

    public GuiButtonListSelector(
            Screen parent,
            Component name,
            List<Tuple<String, T>> elements,
            Function<T, Screen> setter) {
        super(parent, name);
        selector = setter;
        if (elements != null) setElements(elements);
    }

    public void setElements(List<Tuple<String, T>> elements) {
        choices.clear();
        for (var entry : elements)
            choices.add(new Tuple<>(entry.a == null ? "" : entry.a, entry.b));
        positioned = false;
        if (search != null) {
            filter(false);
            init();
        }
    }

    public GuiButtonListSelector<T> withCurrent(T value) {
        current = value;
        hasCurrent = true;
        positioned = false;
        return this;
    }

    public GuiButtonListSelector<T> withIcons(Function<T, ItemStack> provider) {
        icons = provider;
        return this;
    }

    public GuiButtonListSelector<T> withSwatches(Function<T, Integer> provider) {
        swatches = provider;
        return this;
    }

    public GuiButtonListSelector<T> withSearchKeys(Function<T, String> provider) {
        searchKeys = provider;
        return this;
    }

    public GuiButtonListSelector<T> withDetails(Function<T, Component> provider) {
        details = provider;
        return this;
    }

    protected String searchHint() {
        return "Search...";
    }

    protected int extraHeight() {
        return 0;
    }

    protected void filterChanged() {}

    protected void focusedChoice(T value) {}

    protected void buildExtraControls() {}

    protected void renderExtras(GuiGraphicsExtractor g, int mx, int my, float dt) {}

    protected void buildFooter(int x, int y, int width) {
        addRenderableWidget(
                new EEButton(x, y, width, 20, Component.literal("Cancel"), b -> onClose()));
    }

    private static ItemStack nativeIcon(Object value) {
        if (value instanceof ItemStack item) return item.copyWithCount(1);
        if (value instanceof ItemLike item) return new ItemStack(item);
        if (value instanceof EntityType<?> type)
            return SpawnEggItem.byId(type)
                    .map(h -> new ItemStack(h.value()))
                    .orElseGet(
                            () ->
                                    BuiltInRegistries.ITEM
                                            .getOptional(BuiltInRegistries.ENTITY_TYPE.getKey(type))
                                            .map(ItemStack::new)
                                            .orElse(ItemStack.EMPTY));
        return ItemStack.EMPTY;
    }

    @Override
    protected void repositionElements() {
        init();
    }

    @Override
    public void init() {
        int cursor = search == null ? query.length() : search.getCursorPosition();
        int anchor = search == null ? cursor : ((EditBoxAccessor) search).ee$getHighlightPos();
        boolean searchFocus = search == null || search.isFocused();
        int currentIndex = -1;
        for (int i = 0; i < filtered.size(); i++)
            if (hasCurrent && Objects.equals(current, filtered.get(i).b)) {
                currentIndex = i;
                break;
            }
        boolean currentWasVisible = currentIndex >= offset && currentIndex < offset + visibleRows;
        Tuple<String, T> focused = null;
        for (int i = 0; i < rows.size(); i++)
            if (rows.get(i).isFocused() && offset + i < filtered.size())
                focused = filtered.get(offset + i);
        clearWidgets();
        clearFocus();
        rows.clear();
        scrollbar.reset();
        visuals =
                choices.stream()
                        .anyMatch(
                                choice -> {
                                    ItemStack icon = icons.apply(choice.b);
                                    return swatches.apply(choice.b) != null
                                            || icon != null && !icon.isEmpty();
                                });
        panelWidth = Math.min(360, width - 16);
        visibleRows =
                Math.max(
                        1,
                        Math.min(
                                Math.max(3, Math.min(14, choices.size())),
                                (height - 16 - 90 - extraHeight()) / ROW));
        panelHeight = 90 + extraHeight() + visibleRows * ROW;
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        viewTop = top + 54;
        search =
                new EETextField(
                        font,
                        left + 18,
                        top + 29,
                        panelWidth - 36,
                        18,
                        Component.literal("Search options")) {
                    @Override
                    public void extractWidgetRenderState(
                            GuiGraphicsExtractor g, int mx, int my, float dt) {
                        ((EditBoxAccessor) this)
                                .ee$setTextY(getY() + (getHeight() - font.lineHeight) / 2);
                        super.extractWidgetRenderState(g, mx, my, dt);
                    }
                };
        search.setBordered(false);
        search.setTextShadow(false);
        search.setTextColor(EETheme.LIGHT);
        search.setMaxLength(256);
        search.setHint(Component.literal(searchHint()).withColor(0xAEBBB5));
        search.setValue(query);
        EETextField.validationFrame(search, left + 12, top + 28, panelWidth - 24, 20);
        search.setResponder(
                value -> {
                    query = value;
                    filter(true);
                });
        addRenderableWidget(search);
        for (int i = 0; i < visibleRows; i++) {
            final int slot = i;
            rows.add(
                    addRenderableWidget(
                            new EEButton(
                                    left + 13,
                                    viewTop + i * ROW,
                                    1,
                                    ROW,
                                    Component.empty(),
                                    b -> {
                                        if (offset + slot < filtered.size())
                                            select(filtered.get(offset + slot).b);
                                    }) {
                                @Override
                                protected void extractContents(
                                        GuiGraphicsExtractor g, int mx, int my, float dt) {
                                    if (offset + slot >= filtered.size()) return;
                                    var choice = filtered.get(offset + slot);
                                    boolean chosen =
                                            hasCurrent && Objects.equals(current, choice.b);
                                    EETheme.rect(
                                            g,
                                            getX(),
                                            getY(),
                                            getWidth(),
                                            getHeight(),
                                            isHoveredOrFocused()
                                                    ? EETheme.OPTION_HOVER
                                                    : chosen ? EETheme.THUMB : EETheme.OPTION);
                                    if (chosen)
                                        EETheme.rect(
                                                g,
                                                getX() + 2,
                                                getY() + 4,
                                                2,
                                                getHeight() - 8,
                                                EETheme.ON);
                                    int textX = getX() + 8 + (visuals ? 22 : 0);
                                    Integer swatch = swatches.apply(choice.b);
                                    ItemStack icon = icons.apply(choice.b);
                                    if (swatch != null) {
                                        EETheme.bevel(
                                                g,
                                                getX() + 8,
                                                getY() + 4,
                                                14,
                                                14,
                                                EETheme.INPUT,
                                                true);
                                        EETheme.rect(
                                                g,
                                                getX() + 9,
                                                getY() + 5,
                                                12,
                                                12,
                                                0xFF000000 | swatch);
                                    } else if (icon != null && !icon.isEmpty()) {
                                        g.item(icon, getX() + 7, getY() + 3);
                                    }
                                    g.text(
                                            font,
                                            fit(choice.a, getRight() - textX - 6),
                                            textX,
                                            getY() + (getHeight() - font.lineHeight) / 2 + 1,
                                            EETheme.TEXT,
                                            false);
                                }
                            }));
        }
        buildExtraControls();
        buildFooter(left + 12, top + panelHeight - 32, panelWidth - 24);
        filter(false);
        if (!positioned) {
            positioned = true;
            if (hasCurrent)
                for (int i = 0; i < filtered.size(); i++)
                    if (Objects.equals(current, filtered.get(i).b)) {
                        offset = Math.clamp(i - visibleRows / 2, 0, maxOffset());
                        break;
                    }
        } else if (currentWasVisible && currentIndex >= offset + visibleRows)
            offset = Math.clamp(currentIndex - visibleRows + 1, 0, maxOffset());
        updateRows();
        search.moveCursorTo(Math.min(cursor, query.length()), false);
        search.setHighlightPos(Math.min(anchor, query.length()));
        if (searchFocus) setFocused(search);
        else if (focused != null) {
            int index = filtered.indexOf(focused);
            if (index >= 0) focusRow(index);
        }
    }

    private void filter(boolean reset) {
        String term = query.trim().toLowerCase(Locale.ROOT);
        filtered =
                choices.stream()
                        .filter(
                                c ->
                                        c.a.toLowerCase(Locale.ROOT).contains(term)
                                                || searchKeys
                                                        .apply(c.b)
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(term))
                        .toList();
        offset = reset ? 0 : Math.clamp(offset, 0, maxOffset());
        updateRows();
        filterChanged();
    }

    private int maxOffset() {
        return Math.max(0, filtered.size() - visibleRows);
    }

    private int rowWidth() {
        return panelWidth
                - 26
                - (filtered.size() > visibleRows ? EEDropdownScrollbar.WIDTH + 3 : 0);
    }

    private void updateRows() {
        offset = Math.clamp(offset, 0, maxOffset());
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            row.visible = offset + i < filtered.size();
            row.active = row.visible;
            row.setWidth(rowWidth());
            if (row.visible) {
                var entry = filtered.get(offset + i);
                row.setMessage(Component.literal(entry.a));
                Component help = details.apply(entry.b);
                row.setTooltip(
                        help != null
                                ? Tooltip.create(help)
                                : font.width(entry.a) > rowWidth() - 14 - (visuals ? 22 : 0)
                                        ? Tooltip.create(Component.literal(entry.a))
                                        : null);
            } else if (row.isFocused()) setFocused(search);
        }
        scrollbar.layout(
                left + panelWidth - 25,
                viewTop,
                visibleRows * ROW,
                visibleRows,
                filtered.size(),
                offset,
                1);
    }

    private void scrollTo(int value) {
        // A wheel/drag must not leave keyboard focus attached to a different recycled row.
        boolean rowFocused = rows.stream().anyMatch(EEButton::isFocused);
        offset = Math.clamp(value, 0, maxOffset());
        updateRows();
        if (rowFocused) setFocused(search);
    }

    protected void focusRow(int index) {
        if (filtered.isEmpty()) return;
        index = Math.clamp(index, 0, filtered.size() - 1);
        if (index < offset) offset = index;
        else if (index >= offset + visibleRows) offset = index - visibleRows + 1;
        updateRows();
        setFocused(rows.get(index - offset));
        focusedChoice(filtered.get(index).b);
    }

    public void select(T value) {
        Screen next = selector.apply(value);
        mc.gui.setScreen(next == null ? parent : next);
    }

    @Override
    public void onClose() {
        mc.gui.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (scrollbar.mouseClicked(event, this::scrollTo)) return true;
        return super.mouseClicked(event, twice);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return scrollbar.mouseDragged(event, this::scrollTo) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return scrollbar.mouseReleased() || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (x >= left + 12
                && x < left + panelWidth - 12
                && y >= viewTop
                && y < viewTop + visibleRows * ROW) {
            scrollTo(offset - (int) Math.signum(v) * 3);
            return true;
        }
        return super.mouseScrolled(x, y, h, v);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!search.isFocused()) setFocused(search);
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.hasControlDown() && key == InputConstants.KEY_F) {
            setFocused(search);
            return true;
        }
        int focused = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).isFocused()) focused = offset + i;
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)
                && (search.isFocused() || focused >= 0)) {
            if (!filtered.isEmpty()) {
                int index = focused >= 0 ? focused : 0;
                if (focused < 0 && query.isBlank() && hasCurrent)
                    for (int i = 0; i < filtered.size(); i++)
                        if (Objects.equals(current, filtered.get(i).b)) {
                            index = i;
                            break;
                        }
                playClick();
                select(filtered.get(index).b);
            }
            return true;
        }
        if (!filtered.isEmpty()
                && (focused >= 0
                        || search.isFocused()
                                && (key == InputConstants.KEY_DOWN
                                        || key == InputConstants.KEY_UP))) {
            int index = focused;
            if (index < 0) {
                index = offset;
                for (int i = 0; i < filtered.size(); i++)
                    if (hasCurrent && Objects.equals(current, filtered.get(i).b)) {
                        index = i;
                        break;
                    }
            }
            int target =
                    switch (key) {
                        case InputConstants.KEY_DOWN -> focused < 0 ? index : index + 1;
                        case InputConstants.KEY_UP -> focused < 0 ? index : index - 1;
                        case InputConstants.KEY_HOME -> 0;
                        case InputConstants.KEY_END -> filtered.size() - 1;
                        case InputConstants.KEY_PAGEUP -> index - visibleRows;
                        case InputConstants.KEY_PAGEDOWN -> index + visibleRows;
                        default -> Integer.MIN_VALUE;
                    };
            if (target != Integer.MIN_VALUE) {
                focusRow(Math.clamp(target, 0, filtered.size() - 1));
                return true;
            }
        }
        return super.keyPressed(event);
    }

    protected String fit(String value, int width) {
        return font.width(value) <= width
                ? value
                : font.plainSubstrByWidth(value, Math.max(0, width - font.width("..."))) + "...";
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractBackground(g, mx, my, dt);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        g.text(
                font,
                fit(title.getString(), panelWidth - 24),
                left + 12,
                top + 12,
                EETheme.TEXT,
                false);
        EETheme.bevel(g, left + 12, top + 28, panelWidth - 24, 20, EETheme.INPUT, true);
        if (search.isFocused() && query.isEmpty())
            g.text(
                    font,
                    fit(searchHint(), search.getWidth()),
                    search.getX(),
                    search.getY() + (search.getHeight() - font.lineHeight) / 2,
                    0xFFAEBBB5,
                    false);
        EETheme.bevel(
                g,
                left + 12,
                viewTop - 1,
                panelWidth - 24,
                visibleRows * ROW + 2,
                EETheme.INPUT,
                true);
        if (filtered.isEmpty())
            g.text(font, "No matching options", left + 20, viewTop + 9, EETheme.LIGHT, false);
        renderExtras(g, mx, my, dt);
        super.extractRenderState(g, mx, my, dt);
        scrollbar.render(g, mx, my);
    }
}
