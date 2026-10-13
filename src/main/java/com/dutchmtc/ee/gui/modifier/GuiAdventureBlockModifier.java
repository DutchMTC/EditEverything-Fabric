package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EEDropdownButton;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.mixin.AdventureModePredicateAccessor;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.AdventureBlockLore;
import com.dutchmtc.ee.utils.ItemUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

/** Shared draft editor for the two Adventure-mode block permissions. */
public class GuiAdventureBlockModifier extends GuiModifier<ItemStack> {
    private static final int ROW = 24;
    public enum Kind {
        CAN_BREAK("gui.ee.modifier.meta.canBreak", DataComponents.CAN_BREAK, "CanDestroy"),
        CAN_PLACE("gui.ee.modifier.meta.canPlace", DataComponents.CAN_PLACE_ON, "CanPlaceOn");
        private final String titleKey, legacyNbtKey;
        private final DataComponentType<AdventureModePredicate> componentType;
        Kind(String titleKey, DataComponentType<AdventureModePredicate> type, String legacyNbtKey) {
            this.titleKey = titleKey; this.componentType = type; this.legacyNbtKey = legacyNbtKey;
        }
    }
    private enum Mode {
        ONLY("Only these blocks"), EXCEPT("All except these");
        final String label;
        Mode(String label) { this.label = label; }
    }
    private record BlockEntry(String id, String name, ItemStack icon, String searchText) {}
    private final ItemStack original;
    private final Kind kind;
    private final Map<String, BlockEntry> blocks = new LinkedHashMap<>();
    private final Set<String> originalAllowed = new LinkedHashSet<>(), selected = new LinkedHashSet<>();
    private final List<BlockPredicate> originalPredicates;
    private final List<BlockEntry> filtered = new ArrayList<>();
    private final boolean originalTooltip;
    private final Mode originalMode;
    private Mode mode = Mode.ONLY;
    private boolean tooltipVisible, browsing, menuOpen, draggingScroll;
    private String query = "";
    private int left, top, panelWidth, panelHeight, viewTop, viewBottom, scroll, menuFocus, keyboardRow = -1;
    private double dragOffset;
    private EditBox search;
    private EEButton browseButton, clearSearch, apply;
    private EEDropdownButton modeButton;

    public GuiAdventureBlockModifier(Screen parent, ItemStack stack, Kind kind) {
        super(parent, Component.translatable(kind.titleKey), result -> {
            if (!Objects.equals(stack.get(kind.componentType), result.get(kind.componentType)))
                ItemUtils.setComponent(stack, kind.componentType, result.get(kind.componentType));
            boolean visible = ItemUtils.isTooltipComponentVisible(result, kind.componentType);
            if (visible != ItemUtils.isTooltipComponentVisible(stack, kind.componentType))
                ItemUtils.setTooltipComponentVisible(stack, kind.componentType, visible);
            AdventureBlockLore.copyManaged(result, stack);
            // Migrate old permission lists on Apply without touching other custom data.
            var tag = ItemUtils.getTag(stack).copy();
            if (tag.contains(kind.legacyNbtKey)) { tag.remove(kind.legacyNbtKey); ItemUtils.setTag(stack, tag); }
        });
        this.kind = kind; original = stack.copy();
        BuiltInRegistries.BLOCK.forEach(block -> {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString(), name = block.getName().getString();
            blocks.put(id, new BlockEntry(id, name, new ItemStack(block.asItem()), (name + " " + id).toLowerCase(Locale.ROOT)));
        });
        AdventureModePredicate permission = original.get(kind.componentType);
        originalPredicates = permission == null ? List.of()
                : List.copyOf(((AdventureModePredicateAccessor) permission).ee$getPredicates());
        if (permission != null) originalPredicates.forEach(predicate -> originalAllowed.addAll(ids(predicate)));
        else readLegacy();
        Set<String> excluded = difference(blocks.keySet(), originalAllowed);
        if (!originalAllowed.isEmpty() && excluded.size() < originalAllowed.size()) mode = Mode.EXCEPT;
        var stored = AdventureBlockLore.rule(original, kind.componentType);
        if (stored.isPresent()) mode = stored.get().except() ? Mode.EXCEPT : Mode.ONLY;
        originalMode = mode;
        originalTooltip = stored.filter(AdventureBlockLore.Rule::except).map(AdventureBlockLore.Rule::show)
                .orElseGet(() -> ItemUtils.isTooltipComponentVisible(original, kind.componentType));
        tooltipVisible = originalTooltip;
        selected.addAll(mode == Mode.ONLY ? originalAllowed : excluded);
        filter();
    }
    private void readLegacy() {
        for (Tag value : ItemUtils.getList(ItemUtils.getTag(original), kind.legacyNbtKey, Tag.TAG_STRING)) {
            if (!(value instanceof StringTag text)) continue;
            String entry = text.value();
            Identifier id = Identifier.tryParse(entry.startsWith("#") ? entry.substring(1) : entry);
            if (id == null) continue;
            if (entry.startsWith("#")) BuiltInRegistries.BLOCK.get(TagKey.create(Registries.BLOCK, id))
                    .ifPresent(set -> set.stream().forEach(holder -> originalAllowed.add(blockId(holder))));
            else if (blocks.containsKey(id.toString())) originalAllowed.add(id.toString());
        }
    }
    @Override public boolean isModified() { return mode != originalMode || tooltipVisible != originalTooltip || !allowed().equals(originalAllowed); }
    @Override public void onClose() { onCancel(); }
    @Override protected void setInitialFocus() {}
    @Override public void init() {
        clearFocus(); clearWidgets();
        apply = null;
        panelWidth = Math.min(480, width - 16); panelHeight = Math.min(350, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        viewTop = top + 106; viewBottom = top + panelHeight - 40;
        menuOpen = draggingScroll = false;
        int ruleWidth = (panelWidth - 28) / 2;
        modeButton = addRenderableWidget(new EEDropdownButton(left + 12, top + 33, ruleWidth, 20,
                Component.literal(mode.label), b -> { menuOpen = !menuOpen; menuFocus = mode.ordinal(); }));
        modeButton.setTooltip(Tooltip.create(Component.literal("Choose whether the listed blocks are allowed or excluded. Applies in Adventure mode.")));
        addRenderableWidget(new EEButton(left + 16 + ruleWidth, top + 33, panelWidth - 28 - ruleWidth, 20,
                Component.literal("Tooltip: " + (tooltipVisible ? "On" : "Off")), b -> {
                    tooltipVisible = !tooltipVisible; b.setMessage(Component.literal("Tooltip: " + (tooltipVisible ? "On" : "Off")));
                    refreshApply();
                }) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                EETheme.bevel(g, getX(), getY(), getWidth(), getHeight(), tooltipVisible
                        ? isHoveredOrFocused() ? EETheme.ON_HOVER : EETheme.ON
                        : isHoveredOrFocused() ? EETheme.CONTROL_HOVER : EETheme.CONTROL, false);
                g.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2,
                        getY() + (getHeight() - font.lineHeight) / 2 + 1, tooltipVisible ? EETheme.LIGHT : EETheme.TEXT, false);
            }
        }).setTooltip(Tooltip.create(Component.literal("Show the block permission. All except uses an automatic lore section instead of Minecraft's full block list.")));
        int searchWidth = panelWidth - 130;
        search = new EditBox(font, left + 18, top + 80, searchWidth - 30, 18, Component.literal("Search blocks"));
        search.setBordered(false); search.setTextShadow(false); search.setMaxLength(128);
        search.setTextColor(EETheme.LIGHT); search.setTextColorUneditable(0xFFAEBBB5);
        search.setHint(Component.literal("Search blocks...").withColor(0xAEBBB5)); search.setValue(query);
        search.setResponder(value -> { query = value; scroll = 0; keyboardRow = -1; filter(); });
        addRenderableWidget(search);
        clearSearch = addRenderableWidget(new EEButton(left + 12 + searchWidth - 21, top + 80, 18, 18,
                Component.literal("×").withColor(0xBB5555), b -> { search.setValue(""); setFocused(search); }));
        clearSearch.setTooltip(Tooltip.create(Component.literal("Clear search")));
        browseButton = addRenderableWidget(new EEButton(left + panelWidth - 112, top + 79, 100, 20,
                Component.empty(), b -> { browsing = !browsing; search.setValue(""); scroll = 0; keyboardRow = -1; filter(); }));
        browseButton.setTooltip(Tooltip.create(Component.literal("Choose several blocks, then return to your selected list.")));
        int footerWidth = (panelWidth - 28) / 2;
        addRenderableWidget(new EEButton(left + 12, top + panelHeight - 28, footerWidth, 20,
                Component.translatable("gui.ee.cancel"), b -> onCancel()));
        apply = addRenderableWidget(new EEButton(left + 16 + footerWidth, top + panelHeight - 28, footerWidth, 20,
                Component.literal("Apply"), b -> { if (b.active) { set(get()); mc.gui.setScreen(parent); } }));
        filter();
    }
    private void filter() {
        String[] tokens = query.strip().toLowerCase(Locale.ROOT).split("\\s+"); filtered.clear();
        for (BlockEntry block : blocks.values()) {
            if (!browsing && !selected.contains(block.id)) continue;
            boolean match = true;
            for (String token : tokens) if (!block.searchText.contains(token)) { match = false; break; }
            if (match) filtered.add(block);
        }
        filtered.sort(Comparator.comparing(BlockEntry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(BlockEntry::id));
        scroll = Math.max(0, Math.min(scroll, maxScroll())); keyboardRow = Math.min(keyboardRow, filtered.size() - 1);
        if (clearSearch != null) clearSearch.visible = !query.isEmpty();
        if (browseButton != null) browseButton.setMessage(Component.literal(browsing ? "Done choosing" : "Add blocks"));
        refreshApply();
    }
    private AdventureBlockLore.Rule loreRule() { return new AdventureBlockLore.Rule(mode == Mode.EXCEPT, tooltipVisible, selected); }
    private void refreshApply() {
        if (apply == null) return;
        apply.active = AdventureBlockLore.requiredLines(original, kind.componentType, loreRule()) <= ItemLore.MAX_LINES;
        apply.setTooltip(apply.active ? null : Tooltip.create(Component.literal("The automatic section and regular lore exceed Minecraft's 256-line limit. Remove some lines or turn this tooltip off.")));
    }
    private Set<String> allowed() { return mode == Mode.ONLY ? new LinkedHashSet<>(selected) : difference(blocks.keySet(), selected); }
    private static Set<String> difference(Set<String> all, Set<String> minus) {
        Set<String> result = new LinkedHashSet<>(all); result.removeAll(minus); return result;
    }
    private static String blockId(Holder<Block> block) { return BuiltInRegistries.BLOCK.getKey(block.value()).toString(); }
    private Set<String> ids(BlockPredicate predicate) {
        if (predicate.blocks().isEmpty()) return new LinkedHashSet<>(blocks.keySet());
        Set<String> result = new LinkedHashSet<>(); predicate.blocks().get().stream().forEach(holder -> result.add(blockId(holder))); return result;
    }
    private HolderSet<Block> holders(Set<String> ids) {
        return HolderSet.direct(ids.stream().map(id -> BuiltInRegistries.BLOCK.get(Identifier.parse(id)).orElseThrow())
                .map(holder -> (Holder<Block>) holder).toList());
    }
    /** Keep tag/state/NBT/component restrictions on every retained block. */
    protected ItemStack get() {
        ItemStack result = original.copy(); Set<String> allowed = allowed();
        if (!allowed.equals(originalAllowed) || original.get(kind.componentType) == null && !allowed.isEmpty()) {
            List<BlockPredicate> predicates = new ArrayList<>(); Set<String> retained = new LinkedHashSet<>();
            for (BlockPredicate predicate : originalPredicates) {
                Set<String> before = ids(predicate), after = new LinkedHashSet<>(before); after.retainAll(allowed);
                if (after.isEmpty()) continue;
                retained.addAll(after);
                predicates.add(after.equals(before) ? predicate : new BlockPredicate(Optional.of(holders(after)),
                        predicate.properties(), predicate.nbt(), predicate.components()));
            }
            Set<String> added = difference(allowed, retained);
            if (!added.isEmpty()) predicates.add(new BlockPredicate(Optional.of(holders(added)), Optional.empty(), Optional.empty(), DataComponentMatchers.ANY));
            ItemUtils.setComponent(result, kind.componentType, predicates.isEmpty() ? null : new AdventureModePredicate(predicates));
        }
        boolean nativeVisible = mode == Mode.ONLY && tooltipVisible;
        if (nativeVisible != ItemUtils.isTooltipComponentVisible(result, kind.componentType))
            ItemUtils.setTooltipComponentVisible(result, kind.componentType, nativeVisible);
        if (mode == Mode.EXCEPT || isModified() || AdventureBlockLore.rule(original, kind.componentType).isPresent())
            AdventureBlockLore.writeRule(result, kind.componentType, loreRule());
        var tag = ItemUtils.getTag(result).copy();
        if (tag.contains(kind.legacyNbtKey)) { tag.remove(kind.legacyNbtKey); ItemUtils.setTag(result, tag); }
        return result;
    }
    private int rowRight() { return left + panelWidth - 24; }
    private int barX() { return left + panelWidth - 17; }
    private int maxScroll() { return Math.max(0, filtered.size() * ROW - (viewBottom - viewTop)); }
    private int thumbHeight() { int h = viewBottom - viewTop; return Math.max(16, h * h / Math.max(h, filtered.size() * ROW)); }
    private int thumbY() { return viewTop + (viewBottom - viewTop - thumbHeight()) * scroll / Math.max(1, maxScroll()); }
    private boolean insideView(double x, double y) { return x >= left + 12 && x < rowRight() && y >= viewTop && y < viewBottom; }
    private int indexAt(double x, double y) {
        if (!insideView(x, y)) return -1;
        int index = ((int) y - viewTop + scroll) / ROW; return index >= 0 && index < filtered.size() ? index : -1;
    }
    private void dragTo(double y) {
        int travel = viewBottom - viewTop - thumbHeight();
        if (travel > 0) scroll = (int) Math.round(Math.max(0, Math.min(1, (y - dragOffset - viewTop) / travel)) * maxScroll());
    }
    private void toggleBlock(int index) {
        if (index < 0 || index >= filtered.size()) return;
        String id = filtered.get(index).id; if (!selected.remove(id)) selected.add(id); filter(); playClick();
    }
    private void chooseMode(int index) {
        mode = Mode.values()[index]; modeButton.setMessage(Component.literal(mode.label)); menuOpen = false; playClick();
        refreshApply();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menuOpen) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= modeButton.getX() && event.x() < modeButton.getRight()
                    && event.y() >= modeButton.getBottom() + 3 && event.y() < modeButton.getBottom() + 43)
                chooseMode((int) (event.y() - modeButton.getBottom() - 3) / 20);
            else menuOpen = false;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (maxScroll() > 0 && event.x() >= barX() - 3 && event.x() < barX() + 7 && event.y() >= viewTop && event.y() < viewBottom) {
                clearFocus(); draggingScroll = true;
                dragOffset = event.y() >= thumbY() && event.y() < thumbY() + thumbHeight() ? event.y() - thumbY() : thumbHeight() / 2.0;
                dragTo(event.y()); return true;
            }
            int index = indexAt(event.x(), event.y());
            if (index >= 0) { clearFocus(); keyboardRow = index; if (browsing || event.x() >= rowRight() - 23) toggleBlock(index); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScroll && event.button() == InputConstants.MOUSE_BUTTON_LEFT) { dragTo(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScroll) { draggingScroll = false; return true; } return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (menuOpen) return true;
        if (x >= left + 12 && x < left + panelWidth - 10 && y >= viewTop && y < viewBottom) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(vertical * ROW * 2))); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean charTyped(CharacterEvent event) { return menuOpen || super.charTyped(event); }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (menuOpen) {
            if (key == InputConstants.KEY_ESCAPE) menuOpen = false;
            else if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) menuFocus = 1 - menuFocus;
            else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) chooseMode(menuFocus);
            return true;
        }
        if (event.hasControlDown() && key == InputConstants.KEY_F) { setFocused(search); return true; }
        if ((search.isFocused() && key == InputConstants.KEY_DOWN) || getFocused() == null) {
            int next = switch (key) {
                case InputConstants.KEY_UP -> keyboardRow - 1;
                case InputConstants.KEY_DOWN -> keyboardRow + 1;
                case InputConstants.KEY_HOME -> 0;
                case InputConstants.KEY_END -> filtered.size() - 1;
                case InputConstants.KEY_PAGEUP -> keyboardRow - Math.max(1, (viewBottom - viewTop) / ROW);
                case InputConstants.KEY_PAGEDOWN -> keyboardRow + Math.max(1, (viewBottom - viewTop) / ROW);
                default -> -2;
            };
            if (next != -2 && !filtered.isEmpty()) {
                clearFocus(); keyboardRow = Math.max(0, Math.min(filtered.size() - 1, next));
                int y = keyboardRow * ROW;
                if (y < scroll) scroll = y;
                if (y + ROW > scroll + viewBottom - viewTop) scroll = y + ROW - (viewBottom - viewTop);
                scroll = Math.max(0, Math.min(maxScroll(), scroll)); return true;
            }
            if (key == InputConstants.KEY_DELETE || browsing && (key == InputConstants.KEY_SPACE
                    || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) { toggleBlock(keyboardRow); return true; }
        }
        return super.keyPressed(event);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0x90101010, 0xB0101010);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        GuiUtils.drawItemStack(g, original, left + 12, top + 10); g.text(font, title, left + 34, top + 14, EETheme.TEXT, false);
        Component context = Component.translatable("selectWorld.gameMode.adventure");
        g.text(font, context, left + panelWidth - 12 - font.width(context), top + 14, EETheme.MUTED, false);
        String explanation = kind == Kind.CAN_BREAK
                ? mode == Mode.ONLY ? "Break only the blocks below." : "Break any block except those below."
                : mode == Mode.ONLY ? "Place on only the blocks below." : "Place on any block except those below.";
        if (browsing) explanation = mode == Mode.EXCEPT ? "Choose blocks to exclude."
                : kind == Kind.CAN_BREAK ? "Choose blocks this item can break." : "Choose blocks this item can be placed on.";
        g.text(font, explanation, left + 12, top + 61, EETheme.MUTED, false);
        EETheme.bevel(g, left + 12, top + 79, panelWidth - 130, 20, EETheme.INPUT, true);
        ((EditBoxAccessor) search).ee$setTextY(search.getY() + 5);
        EETheme.bevel(g, left + 12, viewTop - 1, panelWidth - 36, viewBottom - viewTop + 2, EETheme.INPUT, true);
        int hovered = menuOpen ? -1 : indexAt(mx, my);
        g.enableScissor(left + 13, viewTop, rowRight() - 1, viewBottom);
        for (int index = scroll / ROW; index < filtered.size(); index++) {
            int y = viewTop + index * ROW - scroll; if (y >= viewBottom) break;
            BlockEntry block = filtered.get(index);
            boolean picked = selected.contains(block.id), highlight = index == hovered || index == keyboardRow;
            EETheme.rect(g, left + 13, y, panelWidth - 38, ROW, highlight ? 0xFF515F55 : 0xFF3F4843);
            if (browsing && picked) EETheme.rect(g, left + 13, y + 3, 2, ROW - 6, 0xFF76B660);
            if (!block.icon.isEmpty()) GuiUtils.drawItemStack(g, block.icon, left + 18, y + 4);
            g.text(font, fit(block.name, panelWidth - 91), left + 40, y + 8, EETheme.LIGHT, false);
            int controlX = rowRight() - 22;
            boolean controlHover = index == hovered && (browsing || mx >= controlX);
            if (browsing) EETheme.bevel(g, controlX, y + 3, 18, 18,
                    picked ? controlHover ? EETheme.ON_HOVER : EETheme.ON : controlHover ? EETheme.CONTROL_HOVER : EETheme.CONTROL, false);
            if (browsing && picked) {
                EETheme.rect(g, controlX + 5, y + 11, 2, 3, EETheme.LIGHT);
                for (int i = 0; i < 5; i++) EETheme.rect(g, controlX + 7 + i, y + 12 - i, 1, 2, EETheme.LIGHT);
            } else if (browsing) {
                EETheme.rect(g, controlX + 5, y + 11, 8, 2, EETheme.TEXT); EETheme.rect(g, controlX + 8, y + 8, 2, 8, EETheme.TEXT);
            } else EETheme.removeButton(g, controlX, y + 3, 18, 18, controlHover, true);
        }
        g.disableScissor();
        if (filtered.isEmpty()) {
            String empty = !query.isBlank() ? "No matching blocks." : browsing ? "No blocks available."
                    : mode == Mode.ONLY ? "No blocks allowed yet." : "All blocks are allowed.";
            g.text(font, empty, left + 24, viewTop + 13, 0xFFD2DAD0, false);
            if (query.isBlank() && !browsing) g.text(font, mode == Mode.ONLY
                    ? "Use Add blocks to choose blocks." : "Use Add blocks to add exceptions.", left + 24, viewTop + 29, 0xFFAEBBB5, false);
        }
        if (maxScroll() > 0) EETheme.scrollbar(g, barX(), viewTop, 4, viewBottom - viewTop, thumbY(), thumbHeight(),
                draggingScroll || mx >= barX() - 3 && mx < barX() + 7 && my >= viewTop && my < viewBottom);
        super.extractRenderState(g, menuOpen ? -1 : mx, menuOpen ? -1 : my, delta);
        if (hovered >= 0) {
            BlockEntry block = filtered.get(hovered);
            if (mx >= rowRight() - 23 || browsing) tooltip(g, (browsing && !selected.contains(block.id) ? "Add " : "Remove ") + block.name, mx, my);
            else {
                boolean restricted = originalPredicates.stream().anyMatch(predicate -> ids(predicate).contains(block.id)
                        && (predicate.properties().isPresent() || predicate.nbt().isPresent() || !predicate.components().isEmpty()));
                tooltip(g, block.name + (restricted ? "\nExisting state or data requirements are preserved." : ""), mx, my);
            }
        }
        if (menuOpen) {
            int y = modeButton.getBottom() + 3;
            EETheme.bevel(g, modeButton.getX() - 1, y - 1, modeButton.getWidth() + 2, 42, EETheme.PANEL, false);
            for (int i = 0; i < Mode.values().length; i++) EEDropdownButton.renderOption(g, font,
                    Component.literal(Mode.values()[i].label), ItemStack.EMPTY, modeButton.getX(), y + i * 20,
                    modeButton.getWidth(), 20, mode.ordinal() == i, menuFocus == i
                            || mx >= modeButton.getX() && mx < modeButton.getRight() && my >= y + i * 20 && my < y + (i + 1) * 20);
        }
    }
    private String fit(String text, int space) {
        return font.width(text) <= space ? text : font.plainSubstrByWidth(text, Math.max(0, space - font.width("..."))) + "...";
    }
    private void tooltip(GuiGraphicsExtractor g, String text, int x, int y) {
        g.setTooltipForNextFrame(font, font.split(Component.literal(text), Math.min(240, width - 24)), x, y);
    }
}
