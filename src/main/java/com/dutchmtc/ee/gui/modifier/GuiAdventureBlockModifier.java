package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.selector.GuiBlockTypeListSelector;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GuiAdventureBlockModifier extends GuiModifier<ItemStack> {
    private static final int MARGIN = 10;
    private static final int PADDING = 10;
    private static final int HEADER_H = 36;
    private static final int ROW_H = 22;
    private static final int REMOVE_W = 18;
    private static final Component NOTE = Component.translatable("gui.ee.modifier.meta.adventure.note");

    public enum Kind {
        CAN_BREAK("gui.ee.modifier.meta.canBreak", DataComponents.CAN_BREAK, "CanDestroy"),
        CAN_PLACE("gui.ee.modifier.meta.canPlace", DataComponents.CAN_PLACE_ON, "CanPlaceOn");

        private final String titleKey;
        private final DataComponentType<AdventureModePredicate> componentType;
        private final String legacyNbtKey;

        Kind(String titleKey, DataComponentType<AdventureModePredicate> componentType, String legacyNbtKey) {
            this.titleKey = titleKey;
            this.componentType = componentType;
            this.legacyNbtKey = legacyNbtKey;
        }
    }

    private enum Mode {
        ONLY,
        EVERYTHING_EXCEPT
    }

    private final ItemStack stack;
    private final Kind kind;
    private final Set<String> allBlockIds;
    private final Set<String> originalAllowedIds;
    private final boolean originalTooltipVisible;

    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final List<String> filteredIds = new ArrayList<>();
    private final Map<String, ItemStack> displayStackCache = new HashMap<>();
    private final Map<String, String> displayNameCache = new HashMap<>();

    private Mode mode;
    private boolean tooltipVisible;
    private int scrollOffset = 0;
    private String lastQuery = "";

    private EditBox search;
    private EEButton modeButton;
    private EEButton tooltipButton;
    private String hoverTooltipId;

    public GuiAdventureBlockModifier(Screen parent, ItemStack stack, Kind kind) {
        super(parent, Component.translatable(kind.titleKey), s -> {
        });
        this.stack = stack;
        this.kind = kind;
        this.allBlockIds = getAllBlockIds();
        this.originalAllowedIds = readAllowedIds(stack, kind);
        this.originalTooltipVisible = ItemUtils.isTooltipComponentVisible(stack, kind.componentType);
        this.tooltipVisible = this.originalTooltipVisible;
        this.mode = computeInitialMode(this.allBlockIds, this.originalAllowedIds);
        setSelectedIds(initialSelectedIds(this.mode, this.allBlockIds, this.originalAllowedIds));
        refilter();
    }

    @Override
    public boolean isModified() {
        return !getAllowedIds().equals(originalAllowedIds) || tooltipVisible != originalTooltipVisible;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    protected void init() {
        clearWidgets();
        super.init();

        int left = panelLeft();
        int top = panelTop();
        int bottom = panelBottom();
        int listLeft = left + PADDING;
        int listWidth = listWidth();

        String searchLabel = I18n.get("gui.ee.search") + ":";
        int searchLabelW = font.width(searchLabel);
        search = new EditBox(font, listLeft + searchLabelW + 6, top + 18, Math.max(80, listWidth - searchLabelW - 12), 16,
                Component.literal(""));
        search.setMaxLength(256);
        search.setValue(lastQuery);
        search.setResponder(value -> {
            lastQuery = value == null ? "" : value;
            refilter();
        });
        addRenderableWidget(search);

        int actionsY = top + HEADER_H;
        int gap = 4;
        int addW = 86;
        int remaining = Math.max(120, listWidth - addW - gap * 2);
        int modeW = remaining / 2;
        int tooltipW = remaining - modeW;

        addRenderableWidget(new EEButton(listLeft, actionsY, addW, 18,
                Component.translatable("gui.ee.modifier.meta.adventure.add"), b -> openBlockSelector()));

        modeButton = addRenderableWidget(new EEButton(listLeft + addW + gap, actionsY, modeW, 18,
                getModeButtonText(), b -> toggleMode()));

        tooltipButton = addRenderableWidget(new EEButton(listLeft + addW + gap + modeW + gap, actionsY, tooltipW, 18,
                getTooltipButtonText(), b -> {
            tooltipVisible = !tooltipVisible;
            updateButtonTexts();
        }));

        addRenderableWidget(new EEButton(width / 2 - 96, bottom - 25, 94, 20,
                Component.translatable("gui.ee.cancel"), b -> onCancel()));
        addRenderableWidget(new EEButton(width / 2 + 2, bottom - 25, 94, 20,
                Component.translatable("gui.done"), b -> {
                    set(get());
                    mc.gui.setScreen(parent);
                }));
    }

    @Override
    public void tick() {
        super.tick();
        if (search == null) {
            return;
        }
        String q = search.getValue();
        if (!lastQuery.equals(q)) {
            lastQuery = q;
            refilter();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);

        int left = panelLeft();
        int top = panelTop();
        int right = panelRight();
        int bottom = panelBottom();

        GuiUtils.drawRect(graphics, left, top, right, bottom, GuiUtils.COLOR_CONTAINER_BORDER | 0xFF000000);
        GuiUtils.drawCenterString(graphics, font, getStringTitle(), width / 2, top + 6, 0xFFFFFFFF);
        GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.search") + ":", search.getX(), search.getY(), 0xFFFFA000, search.getHeight());

        int listLeft = left + PADDING;
        int listTop = top + HEADER_H + 34;
        int listRight = listLeft + listWidth();
        int listBottom = bottom - 36;

        GuiUtils.drawRect(graphics, listLeft - 2, listTop - 2, listRight + 2, listBottom + 2,
                GuiUtils.COLOR_CONTAINER_SLOT | 0xFF000000);
        String listTitle = mode == Mode.ONLY
                ? I18n.get("gui.ee.modifier.meta.adventure.modebtn.only")
                : I18n.get("gui.ee.modifier.meta.adventure.modebtn.except");
        GuiUtils.text(graphics, font, listTitle + " list", listLeft, listTop - 12,
                0xFFFFFFFF, font.lineHeight);
        renderSelectedList(graphics, listLeft, listTop, listRight, listBottom, mouseX, mouseY);

        int detailsLeft = listRight + PADDING;
        int detailsRight = right - PADDING;
        GuiUtils.drawRect(graphics, detailsLeft - 2, listTop - 2, detailsRight + 2, listBottom + 2,
                GuiUtils.COLOR_CONTAINER_SLOT | 0xFF000000);
        renderSummary(graphics, detailsLeft, listTop, detailsRight, listBottom);

        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int listLeft = panelLeft() + PADDING;
        int listTop = panelTop() + HEADER_H + 34;
        int listRight = listLeft + listWidth();
        int listBottom = panelBottom() - 36;

        if (GuiUtils.isHover(listLeft, listTop, listRight - listLeft, listBottom - listTop, mouseX, mouseY)) {
            int row = (mouseY - listTop) / ROW_H;
            int visible = visibleRows(listTop, listBottom);
            int idx = scrollOffset + row;
            if (row >= 0 && row < visible && idx >= 0 && idx < filteredIds.size()) {
                String id = filteredIds.get(idx);
                int y = listTop + row * ROW_H;
                int removeX = listRight - REMOVE_W - 4;
                boolean removeHover = GuiUtils.isHover(removeX, y + 2, REMOVE_W, ROW_H - 4, mouseX, mouseY);
                if (removeHover || event.button() == 1) {
                    selectedIds.remove(id);
                    refilter();
                    playClick();
                    return true;
                }
            }
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listLeft = panelLeft() + PADDING;
        int listTop = panelTop() + HEADER_H + 34;
        int listRight = listLeft + listWidth();
        int listBottom = panelBottom() - 36;

        if (!GuiUtils.isHover(listLeft, listTop, listRight - listLeft, listBottom - listTop, (int) mouseX, (int) mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int visible = visibleRows(listTop, listBottom);
        int maxOffset = Math.max(0, filteredIds.size() - visible);
        if (verticalAmount < 0) {
            scrollOffset = Math.min(maxOffset, scrollOffset + 1);
        } else if (verticalAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        }
        return true;
    }

    protected ItemStack get() {
        ItemUtils.setTooltipComponentVisible(stack, kind.componentType, tooltipVisible);
        Set<String> allowed = getAllowedIds();
        if (allowed.isEmpty()) {
            ItemUtils.setComponent(stack, kind.componentType, null);
        } else {
            List<BlockPredicate> predicates = new ArrayList<>();
            for (String id : allowed) {
                Identifier rid = Identifier.tryParse(id);
                if (rid == null) {
                    continue;
                }
                Optional<Holder.Reference<net.minecraft.world.level.block.Block>> holder = BuiltInRegistries.BLOCK.get(rid);
                if (holder.isEmpty()) {
                    continue;
                }
                BlockPredicate predicate = new BlockPredicate(
                        Optional.of(HolderSet.direct((Holder<net.minecraft.world.level.block.Block>) holder.get())),
                        Optional.empty(),
                        Optional.empty(),
                        DataComponentMatchers.ANY
                );
                predicates.add(predicate);
            }
            ItemUtils.setComponent(stack, kind.componentType, new AdventureModePredicate(predicates));
        }
        return stack;
    }

    private void renderSelectedList(GuiGraphicsExtractor graphics, int listLeft, int listTop, int listRight, int listBottom,
                                    int mouseX, int mouseY) {
        hoverTooltipId = null;
        int visible = visibleRows(listTop, listBottom);
        int maxOffset = Math.max(0, filteredIds.size() - visible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

        if (filteredIds.isEmpty()) {
            String label = selectedIds.isEmpty() ? "(no blocks)" : "(no search results)";
            GuiUtils.text(graphics, font, label, listLeft + 6, listTop + 6, 0xFFAAAAAA, font.lineHeight);
            return;
        }

        for (int row = 0; row < visible; row++) {
            int idx = scrollOffset + row;
            if (idx >= filteredIds.size()) {
                break;
            }

            String id = filteredIds.get(idx);
            ItemStack displayStack = displayStack(id);
            String name = displayName(id, displayStack);
            int y = listTop + row * ROW_H;

            boolean hoveredRow = GuiUtils.isHover(listLeft, y, listRight - listLeft, ROW_H - 1, mouseX, mouseY);
            int bg = hoveredRow ? 0x333A9BFF : 0x22000000;
            GuiUtils.drawRect(graphics, listLeft, y, listRight, y + ROW_H - 1, bg);

            if (!displayStack.isEmpty()) {
                graphics.item(displayStack, listLeft + 4, y + 2);
            }

            String label = displayStack.isEmpty() ? id : name + " (" + id + ")";
            int textX = listLeft + 26;
            int textMaxW = listRight - textX - (REMOVE_W + 10);
            GuiUtils.text(graphics, font, trimToWidth(label, textMaxW), textX, y + 7, 0xFFFFFFFF, font.lineHeight);

            int removeX = listRight - REMOVE_W - 4;
            boolean removeHover = GuiUtils.isHover(removeX, y + 2, REMOVE_W, ROW_H - 4, mouseX, mouseY);
            GuiUtils.drawRect(graphics, removeX, y + 2, removeX + REMOVE_W, y + ROW_H - 2, removeHover ? 0xAAAA3333 : 0x66332222);
            GuiUtils.drawCenterString(graphics, font, "-", removeX + REMOVE_W / 2, y + 6, 0xFFFFFFFF);

            if (hoveredRow && !removeHover) {
                hoverTooltipId = id;
            }
        }

        if (filteredIds.size() > visible) {
            String page = (scrollOffset + 1) + "-" + Math.min(filteredIds.size(), scrollOffset + visible) + " / " + filteredIds.size();
            GuiUtils.text(graphics, font, page, listLeft + 6, listBottom + 4, 0xFF7F7F7F, font.lineHeight);
        }
    }

    private void renderSummary(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom) {
        int y = top + 6;
        GuiUtils.text(graphics, font, "Summary", left + 4, y, 0xFFFFFFFF, font.lineHeight);
        y += 14;
        GuiUtils.text(graphics, font, modeLineText(), left + 4, y, 0xFFFFA000, font.lineHeight);
        y += 14;
        GuiUtils.text(graphics, font, countLineText(), left + 4, y, 0xFFD0D0D0, font.lineHeight);
        y += 14;
        GuiUtils.text(graphics, font,
                "Tooltip: " + I18n.get(tooltipVisible ? "gui.ee.yes" : "gui.ee.no"), left + 4, y, 0xFFD0D0D0,
                font.lineHeight);
        y += 18;

        List<String> notes = wrapToWidth(NOTE.getString(), Math.max(50, right - left - 8));
        for (String line : notes) {
            if (y > bottom - 8) {
                break;
            }
            GuiUtils.text(graphics, font, line, left + 4, y, 0xFFA0A0A0, font.lineHeight);
            y += 12;
        }
    }

    private void renderHoverTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoverTooltipId == null) {
            return;
        }
        ItemStack display = displayStack(hoverTooltipId);
        if (!display.isEmpty()) {
            GuiUtils.setTooltipForNextFrame(graphics, font, display, mouseX, mouseY);
            return;
        }
        GuiUtils.setTooltipForNextFrame(graphics, font, List.of(Component.literal(hoverTooltipId)), Optional.empty(), mouseX, mouseY);
    }

    private void toggleMode() {
        Set<String> selected = new LinkedHashSet<>(selectedIds);
        if (mode == Mode.ONLY) {
            Set<String> excluded = selected.isEmpty() ? new LinkedHashSet<>() : difference(allBlockIds, selected);
            mode = Mode.EVERYTHING_EXCEPT;
            setSelectedIds(excluded);
        } else {
            // Keep EXCEPT -> ONLY empty instead of expanding to "all blocks".
            Set<String> allowed = selected.isEmpty() ? new LinkedHashSet<>() : difference(allBlockIds, selected);
            mode = Mode.ONLY;
            setSelectedIds(allowed);
        }
        updateButtonTexts();
        refilter();
    }

    private void openBlockSelector() {
        Set<String> selected = new LinkedHashSet<>(selectedIds);
        var blocks = BuiltInRegistries.BLOCK.stream()
                .filter(block -> !selected.contains(ItemUtils.getRegistry(block).toString()));

        getMinecraft().gui.setScreen(new GuiBlockTypeListSelector(this,
                Component.translatable("gui.ee.modifier.meta.adventure.selectBlock"),
                block -> {
                    if (block == null) {
                        return this;
                    }
                    String id = ItemUtils.getRegistry(block).toString();
                    if (allBlockIds.contains(id)) {
                        selectedIds.add(id);
                        setSelectedIds(selectedIds);
                        refilter();
                    }
                    return this;
                }, blocks));
    }

    private void updateButtonTexts() {
        if (modeButton != null) {
            modeButton.setMessage(getModeButtonText());
        }
        if (tooltipButton != null) {
            tooltipButton.setMessage(getTooltipButtonText());
        }
    }

    private Component getModeButtonText() {
        return Component.literal("Mode: ")
                .append(Component.translatable(mode == Mode.ONLY
                        ? "gui.ee.modifier.meta.adventure.modebtn.only"
                        : "gui.ee.modifier.meta.adventure.modebtn.except"));
    }

    private Component getTooltipButtonText() {
        return Component.literal("Tooltip: ")
                .append(Component.translatable(tooltipVisible ? "gui.ee.yes" : "gui.ee.no"));
    }

    private String modeLineText() {
        String modeLabel = mode == Mode.ONLY
                ? I18n.get("gui.ee.modifier.meta.adventure.mode.only")
                : I18n.get("gui.ee.modifier.meta.adventure.mode.except");
        return I18n.get("gui.ee.modifier.meta.adventure.mode") + ": " + modeLabel;
    }

    private String countLineText() {
        int total = allBlockIds.size();
        if (mode == Mode.ONLY) {
            int allowed = getAllowedIds().size();
            return "Allowed: " + allowed + "/" + total;
        }
        int excluded = selectedIds.size();
        return "Excluded: " + excluded + ", Allowed: " + Math.max(0, total - excluded) + "/" + total;
    }

    private void setSelectedIds(Set<String> ids) {
        List<String> sorted = ids.stream()
                .filter(id -> id != null && !id.isBlank() && allBlockIds.contains(id))
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
        selectedIds.clear();
        selectedIds.addAll(sorted);
    }

    private void refilter() {
        String query = lastQuery == null ? "" : lastQuery.trim().toLowerCase(Locale.ROOT);
        filteredIds.clear();
        for (String id : selectedIds) {
            ItemStack stack = displayStack(id);
            String name = displayName(id, stack);
            if (query.isEmpty()
                    || id.toLowerCase(Locale.ROOT).contains(query)
                    || name.toLowerCase(Locale.ROOT).contains(query)) {
                filteredIds.add(id);
            }
        }
        scrollOffset = 0;
    }

    private Set<String> getAllowedIds() {
        Set<String> selected = new LinkedHashSet<>(selectedIds);
        Set<String> allowed = mode == Mode.ONLY ? selected : difference(allBlockIds, selected);
        allowed.retainAll(allBlockIds);
        return allowed;
    }

    private ItemStack displayStack(String id) {
        return displayStackCache.computeIfAbsent(id, GuiAdventureBlockModifier::toDisplayStack);
    }

    private String displayName(String id, ItemStack stack) {
        return displayNameCache.computeIfAbsent(id, k -> stack.isEmpty() ? id : stack.getHoverName().getString());
    }

    private static Set<String> initialSelectedIds(Mode mode, Set<String> all, Set<String> allowed) {
        return mode == Mode.ONLY ? new LinkedHashSet<>(allowed) : difference(all, allowed);
    }

    private static Mode computeInitialMode(Set<String> all, Set<String> allowed) {
        if (allowed.isEmpty()) {
            return Mode.ONLY;
        }
        Set<String> excluded = difference(all, allowed);
        return excluded.size() < allowed.size() ? Mode.EVERYTHING_EXCEPT : Mode.ONLY;
    }

    private int panelLeft() {
        return MARGIN;
    }

    private int panelTop() {
        return MARGIN;
    }

    private int panelRight() {
        return width - MARGIN;
    }

    private int panelBottom() {
        return height - MARGIN;
    }

    private int listWidth() {
        int available = panelRight() - panelLeft() - (PADDING * 3);
        int preferred = available / 2;
        return Math.max(160, Math.min(380, Math.min(available, preferred)));
    }

    private int visibleRows(int listTop, int listBottom) {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int dots = font.width("...");
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - dots)) + "...";
    }

    private List<String> wrapToWidth(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) <= maxWidth) {
                current = new StringBuilder(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
                current = new StringBuilder(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static Set<String> getAllBlockIds() {
        Set<String> all = new LinkedHashSet<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            all.add(id.toString());
        }
        return all;
    }

    private static Set<String> difference(Set<String> all, Set<String> minus) {
        Set<String> out = new LinkedHashSet<>(all);
        out.removeAll(minus);
        return out;
    }

    private static Set<String> readAllowedIds(ItemStack stack, Kind kind) {
        Set<String> allowed = new LinkedHashSet<>();
        AdventureModePredicate predicate = ItemUtils.getComponent(stack, kind.componentType);
        if (predicate != null) {
            for (BlockPredicate bp : getPredicates(predicate)) {
                bp.blocks().ifPresent(set -> {
                    var unwrapped = set.unwrap();
                    unwrapped.right().ifPresent(list -> list.forEach(holder -> {
                        Identifier id = BuiltInRegistries.BLOCK.getKey(holder.value());
                        if (id != null) {
                            allowed.add(id.toString());
                        }
                    }));
                });
            }
        }

        if (allowed.isEmpty()) {
            CompoundTag tag = ItemUtils.getTag(stack);
            ListTag list = ItemUtils.getList(tag, kind.legacyNbtKey, Tag.TAG_STRING);
            for (Tag t : list) {
                if (t instanceof StringTag st) {
                    String v = st.value();
                    if (v != null && !v.isBlank()) {
                        allowed.add(v);
                    }
                }
            }
        }

        allowed.retainAll(getAllBlockIds());
        return allowed;
    }

    private static ItemStack toDisplayStack(String id) {
        Identifier rid = Identifier.tryParse(id);
        if (rid == null) {
            return ItemStack.EMPTY;
        }
        var block = BuiltInRegistries.BLOCK.getValue(rid);
        if (block == null) {
            return ItemStack.EMPTY;
        }
        var item = block.asItem();
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    @SuppressWarnings("unchecked")
    private static List<BlockPredicate> getPredicates(AdventureModePredicate predicate) {
        try {
            for (Field f : AdventureModePredicate.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object v = f.get(predicate);
                    if (v instanceof List<?> list && (list.isEmpty() || list.get(0) instanceof BlockPredicate)) {
                        return (List<BlockPredicate>) list;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }
}
