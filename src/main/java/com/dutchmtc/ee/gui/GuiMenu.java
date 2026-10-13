package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.gui.modifier.GuiItemStackModifier;
import com.dutchmtc.ee.gui.selector.GuiTypeListSelector;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.*;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.function.Supplier;

/** Saved creations, with explicit actions and local item-editor drafts. */
public class GuiMenu extends GuiEE {
    private static final int CELL = 24;
    private final SavedItemsLibrary library;
    private HolderLookup.Provider fallbackRegistry;
    private List<SavedItemsLibrary.Entry> filtered = List.of();
    private SavedItemsLibrary.Entry selected;
    private EETextField search;
    private EEButton give, edit, remove, create;
    private EEDropdownButton options;
    private final EEDropdownScrollbar scrollbar = new EEDropdownScrollbar(),
            menuScrollbar = new EEDropdownScrollbar();
    private String query = "";
    private int left,
            top,
            panelWidth,
            panelHeight,
            columns,
            gridRows,
            gridX,
            gridTop,
            gridBottom,
            offset;
    private boolean menuOpen;
    private int menuOffset, menuFocus;
    private final List<MenuAction> actions = new ArrayList<>();

    private record MenuAction(String label, Supplier<String> blocked, Runnable run) {}

    public GuiMenu(Screen parent) {
        super(parent, Component.literal("Saved items"));
        library =
                new SavedItemsLibrary(
                        () -> new ArrayList<>(EEMod.getCustomItems()),
                        codes -> {
                            EEMod.getCustomItems()
                                    .applyUpdate(
                                            list -> {
                                                list.clear();
                                                list.addAll(codes);
                                            });
                            EEMod.saveConfigs();
                        },
                        this::registryAccess);
    }

    GuiMenu(Screen parent, SavedItemsLibrary library) {
        super(parent, Component.literal("Saved items"));
        this.library = library;
    }

    private HolderLookup.Provider registryAccess() {
        if (mc.level != null) return mc.level.registryAccess();
        if (fallbackRegistry == null) fallbackRegistry = VanillaRegistries.createWorldLookup();
        return fallbackRegistry;
    }

    @Override
    protected void repositionElements() {
        init();
    }

    @Override
    public void init() {
        int cursor = search == null ? query.length() : search.getCursorPosition(),
                anchor = search == null ? cursor : ((EditBoxAccessor) search).ee$getHighlightPos();
        boolean focused = search != null && search.isFocused();
        library.refresh();
        clearWidgets();
        clearFocus();
        scrollbar.reset();
        menuScrollbar.reset();
        menuOpen = false;
        panelWidth = Math.min(480, width - 16);
        columns = Math.max(1, (panelWidth - 39) / CELL);
        int maximum = Math.max(1, Math.min(8, (height - 16 - 142) / CELL));
        gridRows =
                Math.min(maximum, Math.max(3, (library.entries().size() + columns - 1) / columns));
        panelHeight = 142 + gridRows * CELL;
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        gridX = left + 12;
        gridTop = top + 60;
        gridBottom = gridTop + gridRows * CELL;
        create =
                addRenderableWidget(
                        action(left + panelWidth - 166, top + 10, 72, "New item", () -> newItem()));
        options =
                addRenderableWidget(
                        new EEDropdownButton(
                                left + panelWidth - 88,
                                top + 10,
                                76,
                                20,
                                Component.literal("Options"),
                                b -> {
                                    menuOpen = !menuOpen;
                                    if (menuOpen) openMenu();
                                }));
        search =
                new EETextField(
                        font,
                        left + 18,
                        top + 35,
                        panelWidth - 36,
                        18,
                        Component.literal("Search saved items")) {
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
        search.setValue(query);
        EETextField.validationFrame(search, left + 12, top + 34, panelWidth - 24, 20);
        search.setHint(Component.literal("Search saved items...").withColor(0xAEBBB5));
        search.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Search saved names and item types. Use # for item tags or @ for a"
                                        + " namespace.")));
        search.setResponder(
                value -> {
                    query = value;
                    offset = 0;
                    filter();
                });
        addRenderableWidget(search);
        int w = panelWidth - 24, half = (w - 32) / 2, y = gridBottom + 26;
        give = addRenderableWidget(action(left + 12, y, half, "Give item", this::giveSelected));
        edit =
                addRenderableWidget(
                        action(
                                left + 18 + half,
                                y,
                                w - half - 32,
                                "Edit item",
                                this::editSelected));
        remove =
                addRenderableWidget(
                        new EEButton(
                                left + panelWidth - 32,
                                y,
                                20,
                                20,
                                Component.literal("×"),
                                b -> deleteSelected()));
        addRenderableWidget(
                new EEButton(
                        left + 12,
                        gridBottom + 50,
                        w,
                        20,
                        Component.literal("Done"),
                        b -> onClose()));
        filter();
        revealSelection();
        search.moveCursorTo(Math.min(cursor, query.length()), false);
        search.setHighlightPos(Math.min(anchor, query.length()));
        if (focused) setFocused(search);
    }

    private EEButton action(int x, int y, int w, String name, Runnable run) {
        return new EEButton(x, y, w, 20, Component.literal(name), b -> run.run()) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float dt) {
                EETheme.bevel(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        active && isHoveredOrFocused() ? EETheme.CONTROL_HOVER : EETheme.CONTROL,
                        false);
                g.text(
                        font,
                        getMessage(),
                        getX() + (getWidth() - font.width(getMessage())) / 2,
                        getY() + 6,
                        active ? EETheme.TEXT : EETheme.MUTED,
                        false);
            }
        };
    }

    private void filter() {
        filtered = library.entries().stream().filter(e -> e.matches(query)).toList();
        if (!filtered.contains(selected)) selected = null;
        offset = Math.clamp(offset, 0, maxOffset());
        layoutBar();
        refresh();
    }

    private int totalRows() {
        return (filtered.size() + columns - 1) / columns;
    }

    private int maxOffset() {
        return Math.max(0, totalRows() - gridRows);
    }

    private void layoutBar() {
        scrollbar.layout(
                left + panelWidth - 25, gridTop, gridRows * CELL, gridRows, totalRows(), offset, 1);
    }

    private void scrollTo(int row) {
        offset = Math.clamp(row, 0, maxOffset());
        layoutBar();
    }

    private void revealSelection() {
        int index = filtered.indexOf(selected);
        if (index < 0) return;
        int row = index / columns;
        if (row < offset) offset = row;
        else if (row >= offset + gridRows) offset = row - gridRows + 1;
        scrollTo(offset);
    }

    private String unavailable() {
        return selected == null
                ? "Select a saved item first."
                : !selected.available()
                        ? "This saved item could not be loaded. Repair its item code in Options."
                        : null;
    }

    private String giveReason() {
        String reason = unavailable();
        if (reason != null) return reason;
        if (mc.player == null || !mc.player.isCreative())
            return "Giving items requires Creative mode.";
        return ItemUtilsClient.canGive(mc)
                ? null
                : "Make room in your hotbar before giving an item.";
    }

    private String editReason() {
        String reason = unavailable();
        return reason != null
                ? reason
                : mc.player != null && !mc.player.isCreative()
                        ? "The item editor requires Creative mode."
                        : null;
    }

    private void enabled(EEButton button, String reason, String help) {
        if (button == null) return;
        button.active = reason == null;
        button.setTooltip(Tooltip.create(Component.literal(reason == null ? help : reason)));
    }

    private void refresh() {
        enabled(create, createReason(), "Choose an item and edit it before saving a new creation.");
        enabled(give, giveReason(), "Give a copy of this saved item.");
        enabled(
                edit,
                editReason(),
                "Edit a copy. The saved item changes only when you finish the editor.");
        enabled(
                remove,
                selected == null ? "Select a saved item first." : null,
                "Delete this saved item.");
    }

    private String createReason() {
        return mc.player != null && mc.player.isCreative()
                ? null
                : "The item editor requires Creative mode.";
    }

    @Override
    public void tick() {
        if (library.refresh()) filter();
        refresh();
    }

    private void select(SavedItemsLibrary.Entry entry) {
        selected = entry;
        clearFocus();
        refresh();
    }

    private void giveSelected() {
        refresh();
        if (giveReason() == null) ItemUtilsClient.give(selected.stack());
    }

    private void keepVisible(SavedItemsLibrary.Entry entry) {
        selected = entry;
        if (entry != null && !entry.matches(query)) {
            query = "";
            if (search != null) search.setValue("");
        }
        filter();
        selected = entry;
        revealSelection();
        refresh();
    }

    private void editSelected() {
        refresh();
        if (editReason() != null) return;
        var entry = selected;
        mc.gui.setScreen(
                new GuiItemStackModifier(
                                this,
                                entry.stack(),
                                value -> {
                                    if (library.update(entry, value)) keepVisible(entry);
                                    else filter();
                                })
                        .withSaveValidation(
                                value ->
                                        value.isEmpty()
                                                ? "Saved items cannot be empty."
                                                : !library.entries().contains(entry)
                                                        ? "This saved item was removed while"
                                                                + " editing."
                                                        : null));
    }

    private void newItem() {
        if (createReason() != null) return;
        mc.gui.setScreen(
                new GuiTypeListSelector(
                        this,
                        Component.literal("Choose item"),
                        item -> {
                            if (item == null || item.isEmpty()) return this;
                            return new GuiItemStackModifier(
                                            this,
                                            item.copy(),
                                            value -> keepVisible(library.add(value)))
                                    .withSaveValidation(
                                            value ->
                                                    value.isEmpty()
                                                            ? "Saved items cannot be empty."
                                                            : null);
                        }) {
                    @Override
                    protected boolean supportsAir() {
                        return false;
                    }
                });
    }

    private void duplicate() {
        if (selected == null) return;
        keepVisible(library.duplicate(selected));
    }

    private void deleteSelected() {
        if (selected == null) return;
        var entry = selected;
        mc.gui.setScreen(
                new GuiConfirmation(
                        this,
                        Component.literal("Delete this saved item?"),
                        Component.literal("Delete"),
                        () -> {
                            library.remove(entry);
                            selected = null;
                            filter();
                            mc.gui.setScreen(this);
                        },
                        () -> mc.gui.setScreen(this)) {
                    @Override
                    public void onClose() {
                        mc.gui.setScreen(GuiMenu.this);
                    }
                });
    }

    private void importCode() {
        mc.gui.setScreen(
                new GuiGiver(this, "", code -> keepVisible(library.addCode(code)), false)
                        .forImport());
    }

    private void editCode() {
        if (selected == null) return;
        var entry = selected;
        mc.gui.setScreen(
                new GuiGiver(
                        this,
                        entry.code() == null ? "" : entry.code(),
                        code -> {
                            if (library.updateCode(entry, code)) keepVisible(entry);
                            else filter();
                        },
                        false));
    }

    private void templates() {
        mc.gui.setScreen(
                new GuiTypeListSelector(
                        this,
                        Component.literal("Choose template"),
                        item -> {
                            String code = ItemUtils.getCustomTag(item, EEMod.TEMPLATE_TAG_NAME, "");
                            return new GuiGiver(
                                            this,
                                            code,
                                            value -> keepVisible(library.addCode(value)),
                                            false)
                                    .forImport();
                        },
                        EEMod.getTemplates()));
    }

    private String heldReason() {
        return mc.player == null || mc.player.getMainHandItem().isEmpty()
                ? "Hold an item in your main hand first."
                : null;
    }

    private void saveHeld() {
        if (heldReason() == null) keepVisible(library.add(mc.player.getMainHandItem().copy()));
    }

    private void editHeld() {
        if (heldReason() != null || !mc.player.isCreative()) return;
        int slot = mc.player.getInventory().getSelectedSlot();
        mc.gui.setScreen(
                new GuiItemStackModifier(
                        this,
                        mc.player.getMainHandItem().copy(),
                        value -> {
                            if (mc.player != null && mc.player.isCreative())
                                ItemUtilsClient.give(value.copy(), 36 + slot);
                        }));
    }

    private void openMenu() {
        menuOpen = true;
        actions.clear();
        actions.add(
                new MenuAction(
                        "Duplicate",
                        () -> selected == null ? "Select a saved item first." : null,
                        this::duplicate));
        actions.add(new MenuAction("Save held item", this::heldReason, this::saveHeld));
        actions.add(new MenuAction("Import item code...", () -> null, this::importCode));
        actions.add(
                new MenuAction(
                        "Edit item code...",
                        () -> selected == null ? "Select a saved item first." : null,
                        this::editCode));
        actions.add(
                new MenuAction(
                        "Copy item code",
                        () -> selected == null ? "Select a saved item first." : null,
                        () ->
                                GuiUtils.addToClipboard(
                                        selected.code() == null ? "" : selected.code())));
        actions.add(new MenuAction("Templates...", () -> null, this::templates));
        actions.add(
                new MenuAction(
                        "Edit held item...",
                        () ->
                                heldReason() != null
                                        ? heldReason()
                                        : !mc.player.isCreative()
                                                ? "The item editor requires Creative mode."
                                                : null,
                        this::editHeld));
        actions.add(
                new MenuAction(
                        "Settings", () -> null, () -> mc.gui.setScreen(new GuiConfig(this))));
        menuOffset = menuFocus = 0;
        menuScrollbar.reset();
        menuLayout();
    }

    private int menuX() {
        return left + panelWidth - 190;
    }

    private int menuY() {
        return top + 32;
    }

    private int menuRows() {
        return Math.max(1, Math.min(actions.size(), (gridBottom + 46 - menuY() - 4) / 20));
    }

    private int menuWidth() {
        return 178;
    }

    private void menuLayout() {
        menuScrollbar.layout(
                menuX() + menuWidth() - 13,
                menuY() + 1,
                menuRows() * 20,
                menuRows(),
                actions.size(),
                menuOffset,
                1);
    }

    private void menuScroll(int value) {
        menuOffset = Math.clamp(value, 0, Math.max(0, actions.size() - menuRows()));
        menuLayout();
    }

    private void chooseAction(int index) {
        if (index < 0 || index >= actions.size()) return;
        var a = actions.get(index);
        if (a.blocked.get() != null) return;
        menuOpen = false;
        playClick();
        a.run.run();
    }

    private int indexAt(double x, double y) {
        if (x < gridX || x >= gridX + columns * CELL || y < gridTop || y >= gridBottom) return -1;
        int index = ((int) (y - gridTop) / CELL + offset) * columns + (int) (x - gridX) / CELL;
        return index < filtered.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (menuOpen) {
            if (options.isMouseOver(event.x(), event.y())) {
                menuOpen = false;
                return true;
            }
            if (menuScrollbar.mouseClicked(event, this::menuScroll)) return true;
            int right = menuX() + menuWidth() - (menuScrollbar.needed() ? 15 : 0);
            if (event.x() >= menuX() + 1
                    && event.x() < right
                    && event.y() >= menuY() + 1
                    && event.y() < menuY() + 1 + menuRows() * 20) {
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT)
                    chooseAction(menuOffset + ((int) event.y() - menuY() - 1) / 20);
                return true;
            }
            menuOpen = false;
            return true;
        }
        if (scrollbar.mouseClicked(event, this::scrollTo)) return true;
        int index = indexAt(event.x(), event.y());
        if (index >= 0) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                select(filtered.get(index));
                playClick();
                if (twice) giveSelected();
                return true;
            }
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                select(filtered.get(index));
                openMenu();
                return true;
            }
        }
        return super.mouseClicked(event, twice);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return (menuOpen
                        ? menuScrollbar.mouseDragged(event, this::menuScroll)
                        : scrollbar.mouseDragged(event, this::scrollTo))
                || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return menuScrollbar.mouseReleased()
                || scrollbar.mouseReleased()
                || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (menuOpen) {
            menuScroll(menuOffset - (int) Math.signum(v));
            return true;
        }
        if (x >= gridX && x < left + panelWidth - 12 && y >= gridTop && y < gridBottom) {
            scrollTo(offset - (int) Math.signum(v) * 2);
            return true;
        }
        return super.mouseScrolled(x, y, h, v);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (menuOpen) {
            if (key == InputConstants.KEY_ESCAPE) {
                menuOpen = false;
                return true;
            }
            if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
                menuFocus =
                        Math.clamp(
                                menuFocus + (key == InputConstants.KEY_DOWN ? 1 : -1),
                                0,
                                actions.size() - 1);
                if (menuFocus < menuOffset) menuScroll(menuFocus);
                else if (menuFocus >= menuOffset + menuRows())
                    menuScroll(menuFocus - menuRows() + 1);
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)
                chooseAction(menuFocus);
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.hasControlDown() && key == InputConstants.KEY_F) {
            setFocused(search);
            return true;
        }
        if (!search.isFocused() && event.hasControlDown() && key == InputConstants.KEY_D) {
            duplicate();
            return true;
        }
        if (!search.isFocused() && key == InputConstants.KEY_DELETE) {
            deleteSelected();
            return true;
        }
        if (getFocused() == null || search.isFocused() && key == InputConstants.KEY_DOWN) {
            int index = filtered.indexOf(selected);
            int target =
                    switch (key) {
                        case InputConstants.KEY_LEFT -> index - 1;
                        case InputConstants.KEY_RIGHT -> index + 1;
                        case InputConstants.KEY_UP -> index - columns;
                        case InputConstants.KEY_DOWN -> index < 0 ? 0 : index + columns;
                        case InputConstants.KEY_HOME -> 0;
                        case InputConstants.KEY_END -> filtered.size() - 1;
                        case InputConstants.KEY_PAGEUP -> index - gridRows * columns;
                        case InputConstants.KEY_PAGEDOWN -> index + gridRows * columns;
                        default -> Integer.MIN_VALUE;
                    };
            if (target != Integer.MIN_VALUE && !filtered.isEmpty()) {
                select(filtered.get(Math.clamp(target, 0, filtered.size() - 1)));
                revealSelection();
                return true;
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                giveSelected();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (menuOpen) return true;
        if (getFocused() == null) setFocused(search);
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        mc.gui.setScreen(parent);
    }

    private String fit(String value, int width) {
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
        g.text(font, title, left + 12, top + 16, EETheme.TEXT, false);
        EETheme.bevel(g, left + 12, top + 34, panelWidth - 24, 20, EETheme.INPUT, true);
        if (search.isFocused() && query.isEmpty())
            g.text(
                    font,
                    "Search saved items...",
                    search.getX(),
                    search.getY() + 4,
                    0xFFAEBBB5,
                    false);
        EETheme.bevel(
                g,
                gridX - 1,
                gridTop - 1,
                panelWidth - 22,
                gridRows * CELL + 2,
                EETheme.INPUT,
                true);
        for (int i = offset * columns;
                i < Math.min(filtered.size(), (offset + gridRows) * columns);
                i++) {
            int slot = i - offset * columns,
                    x = gridX + slot % columns * CELL,
                    y = gridTop + slot / columns * CELL;
            var entry = filtered.get(i);
            boolean hover = !menuOpen && indexAt(mx, my) == i;
            if (entry == selected) {
                EETheme.rect(g, x + 1, y + 1, CELL - 2, CELL - 2, EETheme.CONTROL);
                EETheme.rect(g, x, y, CELL, 1, EETheme.LIGHT);
                EETheme.rect(g, x, y, 1, CELL, EETheme.LIGHT);
                EETheme.rect(g, x, y + CELL - 1, CELL, 1, EETheme.LIGHT);
                EETheme.rect(g, x + CELL - 1, y, 1, CELL, EETheme.LIGHT);
            } else if (hover)
                EETheme.rect(g, x + 1, y + 1, CELL - 2, CELL - 2, EETheme.CONTROL_HOVER);
            var icon = entry.available() ? entry.stack() : new ItemStack(Items.BARRIER);
            GuiUtils.drawItemStack(g, icon, x + 4, y + 4);
        }
        if (filtered.isEmpty())
            g.text(
                    font,
                    library.entries().isEmpty() ? "No saved items yet" : "No matching saved items",
                    gridX + 8,
                    gridTop + 9,
                    EETheme.LIGHT,
                    false);
        if (selected != null) {
            var icon = selected.available() ? selected.stack() : new ItemStack(Items.BARRIER);
            g.item(icon, left + 14, gridBottom + 5);
            g.text(
                    font,
                    fit(selected.name(), panelWidth - 52),
                    left + 36,
                    gridBottom + 9,
                    selected.available() ? EETheme.TEXT : EETheme.ERROR_LABEL,
                    false);
        }
        super.extractRenderState(g, menuOpen ? -1 : mx, menuOpen ? -1 : my, dt);
        scrollbar.render(g, menuOpen ? -1 : mx, menuOpen ? -1 : my);
        if (menuOpen) renderMenu(g, mx, my);
        else {
            int index = indexAt(mx, my);
            if (index >= 0) tooltip(g, filtered.get(index), mx, my);
            else if (selected != null
                    && mx >= left + 12
                    && mx < left + panelWidth - 12
                    && my >= gridBottom + 4
                    && my < gridBottom + 22) tooltip(g, selected, mx, my);
        }
    }

    private void tooltip(GuiGraphicsExtractor g, SavedItemsLibrary.Entry entry, int mx, int my) {
        if (entry.available()) GuiUtils.setTooltipForNextFrame(g, font, entry.stack(), mx, my);
        else
            g.setTooltipForNextFrame(
                    font,
                    font.split(
                            Component.literal(
                                    "This saved item could not be loaded in this version or world."
                                        + " Its original code is preserved. Use Edit item code in"
                                        + " Options to repair it."),
                            Math.min(240, width - 24)),
                    mx,
                    my);
    }

    private void renderMenu(GuiGraphicsExtractor g, int mx, int my) {
        menuLayout();
        int rows = menuRows(), w = menuWidth() - (menuScrollbar.needed() ? 15 : 0);
        EETheme.bevel(g, menuX(), menuY(), menuWidth(), rows * 20 + 2, EETheme.PANEL, false);
        for (int i = 0; i < rows; i++) {
            int index = menuOffset + i, y = menuY() + 1 + i * 20;
            var a = actions.get(index);
            String reason = a.blocked.get();
            boolean hover = mx >= menuX() + 1 && mx < menuX() + w && my >= y && my < y + 20;
            EETheme.rect(
                    g, menuX() + 1, y, w - 1, 20, hover ? EETheme.OPTION_HOVER : EETheme.OPTION);
            g.text(
                    font,
                    fit(a.label, w - 14),
                    menuX() + 8,
                    y + 6,
                    reason == null ? EETheme.TEXT : EETheme.MUTED,
                    false);
            if (index == menuFocus) EETheme.rect(g, menuX() + 2, y + 4, 2, 12, EETheme.ON);
            if (hover && reason != null)
                g.setTooltipForNextFrame(font, Component.literal(reason), mx, my);
        }
        menuScrollbar.render(g, mx, my);
    }
}
