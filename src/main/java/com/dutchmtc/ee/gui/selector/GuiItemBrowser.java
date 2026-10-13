package com.dutchmtc.ee.gui.selector;

import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.gui.GuiEE;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EEDropdownButton;
import com.dutchmtc.ee.gui.components.EEDropdownScrollbar;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import com.dutchmtc.ee.gui.components.EETextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Shared item and block browser. Supplied choice lists stay restricted to their original choices. */
abstract class GuiItemBrowser<T> extends GuiEE {
    enum Source { CATALOG("All items"), SAVED("Saved items"), INVENTORY("Inventory");
        final String label;
        Source(String label) { this.label = label; }
    }
    private static final int CELL = 24, TEXT = EETheme.TEXT, MUTED = EETheme.MUTED;
    private final Function<ItemStack, T> mapper;
    private final Function<T, Screen> setter;
    private final List<ItemStack> restricted;
    private final EnumMap<Source, List<Entry>> sources = new EnumMap<>(Source.class);
    private final List<Category> categories = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private Source source = Source.CATALOG;
    private int category, scroll;
    private boolean categoryOpen, sourceOpen, draggingScroll;
    private int menuOffset, menuFocus;
    private final EEDropdownScrollbar menuScrollbar = new EEDropdownScrollbar();
    private String query = "";
    private Entry selected, air;
    private boolean selectionInitialized;
    private EditBox search, amount;
    private String amountText = "";
    private EEButton use, clearSearch, categoryButton, sourceButton;
    private int left, top, panelWidth, panelHeight, gridX, gridTop, gridBottom, columns;
    private int categoryX, categoryY, categoryWidth, menuRows;

    private final class Entry {
        final ItemStack stack;
        final T value;
        final String searchText;
        final List<String> tags;
        Entry(ItemStack stack, T value) {
            this.stack = stack.copy();
            this.value = value;
            var lore = stack.get(DataComponents.LORE);
            searchText = (stack.getHoverName().getString() + " " + stack.getItem().getName(stack).getString() + " "
                    + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " "
                    + (lore == null ? "" : String.join(" ", lore.lines().stream().map(Component::getString).toList())))
                    .toLowerCase(Locale.ROOT);
            tags = stack.typeHolder().tags().map(tag -> tag.location().toString().toLowerCase(Locale.ROOT)).toList();
        }
        boolean matches(String[] tokens) {
            for (String token : tokens) {
                if (token.startsWith("#")) {
                    if (tags.stream().noneMatch(tag -> tag.contains(token.substring(1)))) return false;
                } else if (token.startsWith("@")) {
                    if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().toLowerCase(Locale.ROOT)
                            .contains(token.substring(1))) return false;
                } else if (!searchText.contains(token)) return false;
            }
            return true;
        }
    }
    private record Category(Component label, ItemStack icon, Set<ItemStack> items) {}

    protected GuiItemBrowser(Screen parent, Component title, Function<ItemStack, T> mapper,
                             Function<T, Screen> setter, List<ItemStack> restricted) {
        super(parent, title);
        this.mapper = mapper;
        this.setter = setter;
        this.restricted = restricted;
    }
    protected boolean supportsAir() { return false; }
    protected boolean supportsAmount() { return false; }
    protected final boolean unrestricted() { return restricted == null; }
    protected int defaultAmount(ItemStack stack) { return stack.getCount(); }
    protected int amountLimit(ItemStack stack) { return stack.getMaxStackSize(); }
    protected ItemStack selectionPreview(ItemStack stack) { return stack; }
    protected Component selectionLabel(ItemStack stack) { return stack.getHoverName(); }
    protected ItemStack initialSelection() { return null; }

    private void loadSources() {
        if (!sources.isEmpty()) return;
        if (supportsAir() && restricted == null) {
            ItemStack empty = ItemStack.EMPTY;
            air = new Entry(empty, mapper.apply(empty));
        }
        var registryAccess = mc.level == null ? VanillaRegistries.createWorldLookup() : mc.level.registryAccess();
        try {
            CreativeModeTabs.tryRebuildTabContents(mc.level == null ? FeatureFlags.DEFAULT_FLAGS : mc.level.enabledFeatures(),
                    mc.player != null && mc.player.canUseGameMasterBlocks(), registryAccess);
        } catch (RuntimeException error) {
            EEMod.LOGGER.warn("Could not build creative categories for the item browser", error);
        }
        categories.add(new Category(Component.literal("All categories"), ItemStack.EMPTY, null));
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY || !tab.hasAnyItems()) continue;
            Set<ItemStack> items = ItemStackLinkedSet.createTypeAndComponentsSet();
            items.addAll(tab.getDisplayItems());
            categories.add(new Category(tab.getDisplayName(), tab.getIconItem(), items));
        }
        List<ItemStack> catalog = new ArrayList<>();
        if (restricted != null) catalog.addAll(restricted);
        else {
            for (Category group : categories) if (group.items != null) catalog.addAll(group.items);
            catalog.addAll(CreativeModeTabs.searchTab().getDisplayItems());
            BuiltInRegistries.ITEM.forEach(item -> catalog.add(new ItemStack(item)));
        }
        sources.put(Source.CATALOG, entries(catalog, restricted == null));
        categories.removeIf(group -> group.items != null && sources.get(Source.CATALOG).stream()
                .noneMatch(entry -> group.items.stream().anyMatch(stack -> stack.is(entry.stack.getItem()))));
        if (restricted != null) return;
        List<ItemStack> saved = new ArrayList<>();
        for (String code : EEMod.getCustomItems()) {
            ItemStack stack = ItemUtils.getFromGiveCode(ChatUtils.translateColorCodes(code), registryAccess);
            if (stack != null && !stack.isEmpty()) saved.add(stack);
        }
        sources.put(Source.SAVED, entries(saved, false));
        List<ItemStack> inventory = new ArrayList<>();
        if (mc.player != null) {
            for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = mc.player.getInventory().getItem(slot);
                if (!stack.isEmpty()) inventory.add(stack);
            }
        }
        sources.put(Source.INVENTORY, entries(inventory, false));
    }

    private List<Entry> entries(List<ItemStack> stacks, boolean deduplicate) {
        List<Entry> entries = new ArrayList<>();
        Set<ItemStack> seen = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || (deduplicate && !seen.add(stack))) continue;
            T value = mapper.apply(stack.copy());
            if (value != null) entries.add(new Entry(stack, value));
        }
        return entries;
    }

    @Override public void init() {
        loadSources();
        clearFocus(); clearWidgets();
        panelWidth = Math.min(600, width - 16);
        panelHeight = Math.min(400, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        gridX = left + 12;
        int searchY = top + 36;
        gridTop = searchY + 27;
        gridBottom = top + panelHeight - 65;
        columns = Math.max(1, (panelWidth - 33) / CELL);
        categoryWidth = Math.min(170, (panelWidth - 28) / 3);
        categoryX = left + panelWidth - categoryWidth - 12;
        categoryY = searchY;
        categoryOpen = sourceOpen = draggingScroll = false;
        menuScrollbar.reset();
        if (restricted == null) {
            sourceButton = addRenderableWidget(new EEDropdownButton(left + panelWidth - 136, top + 9, 124, 20, Component.empty(), b -> {
                sourceOpen = !sourceOpen; categoryOpen = false;
                menuScrollbar.reset();
                menuRows = Source.values().length; menuOffset = 0; menuFocus = source.ordinal();
            }));
            sourceButton.setTooltip(Tooltip.create(Component.literal("Browse all items, your saved items, or your inventory.")));
        }
        int searchWidth = categoryX - gridX - 6;
        search = new EETextField(font, gridX + 6, searchY + 1, searchWidth - 30, 18, Component.literal("Search items"));
        search.setBordered(false); search.setTextShadow(false); search.setMaxLength(128);
        search.setTextColor(0xFFF1F3EF);
        search.setHint(Component.literal("Search...").withColor(0xB2BEB6));
        search.setValue(query);
        addRenderableWidget(search);
        search.setResponder(value -> { query = value; scroll = 0; filter(); });
        search.setTooltip(Tooltip.create(Component.literal("Search names and IDs. Use # for item tags or @ for a namespace.")));
        clearSearch = addRenderableWidget(new BrowserButton(gridX + searchWidth - 22, searchY + 1, 20, 18, "×",
                () -> { search.setValue(""); setFocused(search); }));
        clearSearch.setTooltip(Tooltip.create(Component.literal("Clear search")));
        categoryButton = addRenderableWidget(new EEDropdownButton(categoryX, categoryY, categoryWidth, 20, Component.empty(), b -> {
            categoryOpen = !categoryOpen; sourceOpen = false;
            menuScrollbar.reset();
            menuRows = Math.max(1, Math.min(7, (gridBottom - gridTop - 4) / 18));
            menuFocus = category;
            menuOffset = Math.max(0, Math.min(category - menuRows / 2, categories.size() - menuRows));
        }));
        categoryButton.setTooltip(Tooltip.create(Component.literal("Filter by creative category")));
        int bottom = top + panelHeight;
        if (supportsAmount()) {
            int amountRight = left + panelWidth - 12;
            amount = new EETextField(font, amountRight - 40, bottom - 58, 34, 18,
                    Component.literal("Amount")) {
                @Override public boolean keyPressed(KeyEvent event) {
                    if (event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_DOWN) {
                        adjustAmount(event.key() == InputConstants.KEY_UP ? 1 : -1); return true;
                    }
                    return super.keyPressed(event);
                }
            };
            amount.setBordered(false); amount.setTextShadow(false); amount.setMaxLength(10);
            amount.setValue(amountText);
            amount.setResponder(value -> {
                amountText = value;
                refreshSelection();
            });
            addRenderableWidget(amount);
        }
        int buttonWidth = (panelWidth - 28) / 2;
        addRenderableWidget(new BrowserButton(gridX, bottom - 26, buttonWidth, 20, "Cancel", this::onClose));
        use = addRenderableWidget(new BrowserButton(gridX + buttonWidth + 4, bottom - 26, buttonWidth, 20,
                "Use item", this::applySelection));
        filter();
        if (!selectionInitialized) {
            selectionInitialized = true;
            ItemStack initial = initialSelection();
            if (initial != null) {
                Entry entry = initial.isEmpty() ? air : filtered.stream()
                        .filter(candidate -> ItemStack.isSameItemSameComponents(candidate.stack, initial))
                        .findFirst().orElseGet(() -> filtered.stream()
                                .filter(candidate -> candidate.stack.is(initial.getItem())).findFirst().orElse(null));
                if (entry != null) {
                    selectEntry(entry);
                    scrollToSelection();
                }
            }
        }
    }
    @Override protected void setInitialFocus() {}
    @Override public void onClose() { mc.gui.setScreen(parent); }

    private void switchSource(Source choice) {
        if (source == choice) return;
        source = choice; selected = null; scroll = 0; category = 0; categoryOpen = sourceOpen = false;
        filter();
    }

    private void filter() {
        filtered.clear();
        String[] tokens = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
        Set<ItemStack> group = categories.get(category).items;
        if (air != null && inCategory(air, group) && air.matches(tokens)) filtered.add(air);
        for (Entry entry : sources.get(source)) {
            if (inCategory(entry, group) && entry.matches(tokens)) filtered.add(entry);
        }
        if (!filtered.contains(selected)) selected = null;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        refreshSelection();
        if (sourceButton != null) sourceButton.setMessage(Component.literal(source.label));
        if (clearSearch != null) clearSearch.visible = !query.isEmpty();
        if (categoryButton != null) categoryButton.setMessage(categories.get(category).label);
    }

    private boolean inCategory(Entry entry, Set<ItemStack> group) {
        // Customized components must not hide saved items from their underlying creative category.
        return group == null || (source == Source.CATALOG && restricted == null ? group.contains(entry.stack)
                : group.stream().anyMatch(stack -> stack.is(entry.stack.getItem())));
    }

    private void selectEntry(Entry entry) {
        if (selected != entry) { selected = entry; resetAmount(); }
        clearFocus(); refreshSelection();
    }
    private void scrollToSelection() {
        int index = filtered.indexOf(selected);
        if (index < 0) return;
        int y = index / columns * CELL;
        if (y < scroll) scroll = y;
        if (y + CELL > scroll + gridBottom - gridTop) scroll = y + CELL - (gridBottom - gridTop);
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }
    private void resetAmount() {
        if (!supportsAmount() || selected == null || selected.stack.isEmpty()) return;
        setAmount(Math.max(1, Math.min(amountLimit(selected.stack), defaultAmount(selected.stack))));
    }
    private void setAmount(int count) {
        amountText = Integer.toString(count);
        if (amount != null) amount.setValue(amountText);
    }
    private void adjustAmount(int delta) {
        if (selected == null || selected.stack.isEmpty()) return;
        int current = readAmount() == null ? Math.max(1, defaultAmount(selected.stack)) : readAmount();
        setAmount((int) Math.max(1, Math.min(amountLimit(selected.stack), (long) current + delta)));
    }
    private Integer readAmount() {
        if (selected == null) return null;
        if (!supportsAmount() || selected.stack.isEmpty()) return 1;
        try {
            int count = Integer.parseInt(amountText);
            return count >= 1 && count <= amountLimit(selected.stack) ? count : null;
        } catch (NumberFormatException ignored) { return null; }
    }
    private int selectedNameRight() {
        return amount != null && amount.visible ? amount.getX() - 46 : left + panelWidth - 12;
    }
    private ItemStack selectedPreview() {
        if (selected == null || selected.stack.isEmpty()) return ItemStack.EMPTY;
        Integer count = readAmount();
        if (supportsAmount()) {
            if (count == null) count = Math.max(1, Math.min(amountLimit(selected.stack), defaultAmount(selected.stack)));
            return selectionPreview(selected.stack.copyWithCount(count));
        }
        return selectionPreview(selected.stack);
    }
    private void refreshSelection() {
        boolean hasItem = selected != null && !selected.stack.isEmpty();
        if (amount != null) {
            amount.visible = hasItem;
            EETextField.validate(amount, hasItem && readAmount() == null ? "Enter a whole amount from 1 to " + amountLimit(selected.stack) + "." : null);
            if (hasItem) amount.setTooltip(Tooltip.create(Component.literal(amountLimit(selected.stack) == 1
                    ? "Items with durability cannot stack in Minecraft."
                    : "Enter an amount from 1 to " + amountLimit(selected.stack)
                            + ", or scroll to adjust it. The stack limit increases only if needed.")));
        }
        if (use != null) {
            use.active = selected != null && readAmount() != null;
            use.setTooltip(selected != null && readAmount() == null
                    ? Tooltip.create(Component.literal(amountLimit(selected.stack) == 1
                            ? "Items with durability cannot stack in Minecraft. Use an amount of 1."
                            : "Enter an amount from 1 to " + amountLimit(selected.stack) + " before applying.")) : null);
        }
    }
    private void applySelection() {
        if (selected == null || readAmount() == null) return;
        T value = selected.value;
        if (supportsAmount() && !selected.stack.isEmpty()) value = mapper.apply(selected.stack.copyWithCount(readAmount()));
        select(value);
    }
    /** Preserve the selectors' callback/null-parent navigation contract. */
    public void select(T value) {
        playClick();
        Screen next = setter.apply(value);
        mc.gui.setScreen(next == null ? parent : next);
    }

    private int contentHeight() { return (filtered.size() + columns - 1) / columns * CELL; }
    private int maxScroll() { return Math.max(0, contentHeight() - (gridBottom - gridTop)); }
    private boolean insideGrid(double x, double y) {
        return x >= gridX && x < gridX + columns * CELL && y >= gridTop && y < gridBottom;
    }
    private int indexAt(double x, double y) {
        if (!insideGrid(x, y)) return -1;
        int index = ((int) (y - gridTop) + scroll) / CELL * columns + (int) (x - gridX) / CELL;
        return index < filtered.size() ? index : -1;
    }
    private int barX() { return left + panelWidth - 16; }
    private int thumbHeight() {
        int height = gridBottom - gridTop;
        return Math.max(18, height * height / Math.max(height, contentHeight()));
    }
    private int thumbY() {
        return gridTop + (gridBottom - gridTop - thumbHeight()) * scroll / Math.max(1, maxScroll());
    }
    private double dragOffset;
    private void dragScroll(double y) {
        int travel = gridBottom - gridTop - thumbHeight();
        if (travel > 0) scroll = (int) Math.round(Math.max(0, Math.min(1, (y - dragOffset - gridTop) / travel)) * maxScroll());
    }
    private boolean inMenu(double x, double y) {
        return x >= menuLeft() && x < menuLeft() + menuWidth() && y >= menuTop() && y < menuTop() + menuRows * 18 + 4;
    }
    private int menuLeft() { return sourceOpen ? sourceButton.getX() : Math.min(categoryX, left + panelWidth - menuWidth() - 12); }
    private int menuTop() { return sourceOpen ? sourceButton.getBottom() + 3 : gridTop; }
    private int menuWidth() { return sourceOpen ? sourceButton.getWidth() : Math.min(panelWidth - 24, Math.max(140, categoryWidth)); }
    private int optionCount() { return sourceOpen ? Source.values().length : categories.size(); }
    private void layoutMenuScrollbar() {
        menuScrollbar.layout(menuLeft() + menuWidth() - EEDropdownScrollbar.WIDTH, menuTop() + 2,
                menuRows * 18, menuRows, optionCount(), menuOffset, 1);
    }
    private void scrollMenu(int offset) {
        menuOffset = offset;
        menuFocus = Math.clamp(menuFocus, offset, offset + menuRows - 1);
    }
    private void chooseOption(int index) {
        if (sourceOpen) { sourceOpen = false; switchSource(Source.values()[index]); playClick(); }
        else chooseCategory(index);
    }
    private void chooseCategory(int index) {
        category = index; categoryOpen = false; scroll = 0; filter(); playClick();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (categoryOpen || sourceOpen) {
            layoutMenuScrollbar();
            if (menuScrollbar.mouseClicked(event, this::scrollMenu)) return true;
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inMenu(event.x(), event.y())) {
                int index = menuOffset + (int) (event.y() - menuTop() - 2) / 18;
                if (index >= 0 && index < optionCount()) chooseOption(index);
                return true;
            }
            categoryOpen = sourceOpen = false;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (maxScroll() > 0 && event.x() >= barX() - 3 && event.x() < barX() + 7
                    && event.y() >= gridTop && event.y() < gridBottom) {
                draggingScroll = true;
                dragOffset = event.y() >= thumbY() && event.y() < thumbY() + thumbHeight()
                        ? event.y() - thumbY() : thumbHeight() / 2.0;
                dragScroll(event.y()); return true;
            }
            int index = indexAt(event.x(), event.y());
            if (index >= 0) {
                Entry clicked = filtered.get(index);
                boolean same = selected == clicked;
                selectEntry(clicked);
                if (doubleClick && same) applySelection(); else playClick();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (categoryOpen || sourceOpen) {
            layoutMenuScrollbar();
            if (menuScrollbar.mouseDragged(event, this::scrollMenu)) return true;
        }
        if (draggingScroll) { dragScroll(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (menuScrollbar.mouseReleased()) return true;
        if (draggingScroll) { draggingScroll = false; return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (categoryOpen || sourceOpen) {
            scrollMenu(Math.max(0, Math.min(Math.max(0, optionCount() - menuRows), menuOffset - (int) Math.signum(vertical))));
            return true;
        }
        if (amount != null && amount.visible && amount.isMouseOver(x, y) && vertical != 0) {
            adjustAmount(vertical > 0 ? 1 : -1); return true;
        }
        if (x >= gridX && x < left + panelWidth - 8 && y >= gridTop && y < gridBottom) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (vertical * CELL * 2))); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (categoryOpen || sourceOpen) {
            if (key == InputConstants.KEY_ESCAPE) { categoryOpen = sourceOpen = false; return true; }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { chooseOption(menuFocus); return true; }
            if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
                menuFocus = Math.max(0, Math.min(optionCount() - 1, menuFocus + (key == InputConstants.KEY_UP ? -1 : 1)));
                menuOffset = Math.max(Math.min(menuOffset, menuFocus), menuFocus - menuRows + 1); return true;
            }
            return true;
        }
        if (event.hasControlDown() && key == InputConstants.KEY_F) { setFocused(search); return true; }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            if (search.isFocused() || amount != null && amount.isFocused() || getFocused() == null) { applySelection(); return true; }
        }
        if ((search.isFocused() && key == InputConstants.KEY_DOWN) || getFocused() == null) {
            int current = selected == null ? -1 : filtered.indexOf(selected);
            int next = switch (key) {
                case InputConstants.KEY_LEFT -> Math.max(0, current - 1);
                case InputConstants.KEY_RIGHT -> current + 1;
                case InputConstants.KEY_UP -> Math.max(0, current - columns);
                case InputConstants.KEY_DOWN -> current < 0 ? 0 : current + columns;
                case InputConstants.KEY_HOME -> 0;
                case InputConstants.KEY_END -> filtered.size() - 1;
                case InputConstants.KEY_PAGEUP -> Math.max(0, current - Math.max(1, (gridBottom - gridTop) / CELL) * columns);
                case InputConstants.KEY_PAGEDOWN -> current + Math.max(1, (gridBottom - gridTop) / CELL) * columns;
                default -> -1;
            };
            if (next >= 0 && !filtered.isEmpty()) {
                selectEntry(filtered.get(Math.min(filtered.size() - 1, next)));
                scrollToSelection(); return true;
            }
        }
        return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event) {
        return categoryOpen || sourceOpen || super.charTyped(event);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0x30101010, 0x60101010);
        bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        g.text(font, fit(title.getString(), panelWidth - (restricted == null ? 162 : 24)), gridX, top + 15, TEXT, false);
        int searchWidth = categoryX - gridX - 6;
        bevel(g, gridX, categoryY, searchWidth, 20, EETheme.INPUT, true);
        ((EditBoxAccessor) search).ee$setTextY(search.getY() + 5);
        bevel(g, gridX - 1, gridTop - 1, columns * CELL + 2, gridBottom - gridTop + 2, EETheme.INPUT, true);
        g.enableScissor(gridX, gridTop, gridX + columns * CELL, gridBottom);
        int first = scroll / CELL * columns;
        int last = Math.min(filtered.size(), ((scroll + gridBottom - gridTop + CELL - 1) / CELL) * columns);
        int hovered = categoryOpen || sourceOpen ? -1 : indexAt(mx, my);
        for (int index = first; index < last; index++) {
            Entry entry = filtered.get(index);
            int x = gridX + index % columns * CELL, y = gridTop + index / columns * CELL - scroll;
            if (entry == selected || index == hovered) {
                rect(g, x + 1, y + 1, CELL - 2, CELL - 2, entry == selected ? EETheme.THUMB : EETheme.OPTION_HOVER);
                frame(g, x + 1, y + 1, CELL - 2, CELL - 2, entry == selected ? 0xFFE2EDD9 : 0xFFCFD8C5);
            }
            if (entry == air) airIcon(g, x, y, CELL);
            else GuiUtils.drawItemStack(g, entry.stack, x + 4, y + 4);
        }
        g.disableScissor();
        if (filtered.isEmpty()) {
            String empty = query.isBlank() && category == 0 ? switch (source) {
                case SAVED -> "No saved items yet";
                case INVENTORY -> "No items in your inventory";
                default -> "No available items";
            } : "No matching items";
            g.text(font, empty, gridX + 8, gridTop + 12, EETheme.LIGHT, false);
            if (source == Source.SAVED && query.isBlank() && category == 0)
                g.text(font, fit("Save an item in EditEverything to find it here.", panelWidth - 48), gridX + 8, gridTop + 26, 0xFFB6C2BB, false);
        }
        if (maxScroll() > 0) {
            EETheme.scrollbar(g, barX(), gridTop, 4, gridBottom - gridTop, thumbY(), thumbHeight(),
                    draggingScroll || !(categoryOpen || sourceOpen) && mx >= barX() - 3 && mx < barX() + 7
                            && my >= gridTop && my < gridBottom);
        }
        int infoY = top + panelHeight - 58;
        rect(g, gridX, infoY - 3, panelWidth - 24, 1, 0xFF969E91);
        if (selected != null) {
            if (selected == air) airIcon(g, gridX + 5, infoY + 2, 16);
            else {
                GuiUtils.drawItemStack(g, selectedPreview(), gridX + 5, infoY + 2);
            }
            int nameWidth = selectedNameRight() - gridX - 29;
            g.text(font, fit(selectionLabel(selected.stack).getString(), nameWidth), gridX + 29, infoY + 6, TEXT, false);
        }
        if (amount != null && amount.visible) {
            EETextField.validationFrame(amount, amount.getX() - 4, amount.getY(), amount.getWidth() + 8, 18);
            g.text(font, "Amount", amount.getX() - 43, amount.getY() + 5, TEXT, false);
            bevel(g, amount.getX() - 4, amount.getY(), amount.getWidth() + 8, 18, EETheme.INPUT, true);
            ((EditBoxAccessor) amount).ee$setTextY(amount.getY() + 5);
        }
        super.extractRenderState(g, categoryOpen || sourceOpen ? -1 : mx, categoryOpen || sourceOpen ? -1 : my, delta);
        if (categoryOpen || sourceOpen) renderCategories(g, mx, my);
        else if (hovered >= 0 && filtered.get(hovered) == air)
            g.setTooltipForNextFrame(font, List.of(Component.literal("Air").getVisualOrderText(),
                    Component.literal("Clear the item").getVisualOrderText()), mx, my);
        else if (hovered >= 0) GuiUtils.setTooltipForNextFrame(g, font, filtered.get(hovered).stack, mx, my);
        else if (selected != null && mx >= gridX && mx < selectedNameRight() && my >= infoY && my < infoY + 23)
            GuiUtils.setTooltipForNextFrame(g, font, selectedPreview(), mx, my);
    }
    private void renderCategories(GuiGraphicsExtractor g, int mx, int my) {
        layoutMenuScrollbar();
        int x = menuLeft(), top = menuTop(), w = menuWidth();
        int optionWidth = w - 4 - (menuScrollbar.needed() ? EEDropdownScrollbar.WIDTH : 0);
        rect(g, x + 1, top + 1, w + 3, menuRows * 18 + 5, 0x55000000);
        bevel(g, x - 1, top - 1, w + 2, menuRows * 18 + 6, 0xFFBEC6B7, false);
        for (int row = 0; row < menuRows && menuOffset + row < optionCount(); row++) {
            int index = menuOffset + row, y = top + 2 + row * 18;
            Component label = sourceOpen ? Component.literal(Source.values()[index].label) : categories.get(index).label;
            ItemStack icon = sourceOpen ? ItemStack.EMPTY : categories.get(index).icon;
            boolean hover = mx >= x && mx < x + optionWidth + 2 && my >= y && my < y + 18;
            boolean selected = index == (sourceOpen ? source.ordinal() : category);
            EEDropdownButton.renderOption(g, font, label, icon, x + 2, y, optionWidth, 18, selected, hover || index == menuFocus);
            int iconWidth = icon.isEmpty() ? 0 : 20;
            if (hover && font.width(label) > optionWidth - iconWidth - 13) g.setTooltipForNextFrame(font, label, mx, my);
        }
        menuScrollbar.render(g, mx, my);
    }
    private String fit(String value, int width) {
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - font.width("..."))) + "...";
    }
    private static void airIcon(GuiGraphicsExtractor g, int x, int y, int size) {
        // Center the visible icon itself, independent of font advance and baseline padding.
        int span = 6, inset = (size - span) / 2;
        for (int pixel = 0; pixel < span; pixel++) {
            rect(g, x + inset + pixel, y + inset + pixel, 1, 1, 0xFFBB6666);
            rect(g, x + inset + span - pixel - 1, y + inset + pixel, 1, 1, 0xFFBB6666);
        }
    }
    private static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        GuiUtils.drawGradientRect(g, x, y, x + w, y + h, color, color);
    }
    private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        rect(g, x, y, w, 1, color); rect(g, x, y + h - 1, w, 1, color);
        rect(g, x, y, 1, h, color); rect(g, x + w - 1, y, 1, h, color);
    }
    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, boolean inset) {
        EETheme.bevel(g, x, y, w, h, color, inset);
    }
    private final class BrowserButton extends EEButton {
        BrowserButton(int x, int y, int width, int height, String label, Runnable action) {
            super(x, y, width, height, Component.literal(label), b -> action.run());
        }
    }
}
