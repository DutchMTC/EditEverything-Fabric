package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.selector.GuiTypeListSelector;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class GuiAdventureBlockModifier extends GuiListModifier<ItemStack> {
    private static final int LIST_WIDTH = 320;

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

    private static final Component NOTE = Component.translatable("gui.ee.modifier.meta.adventure.note");

    private static class SelectedBlockListElement extends ListElement {
        private final ItemStack displayStack;
        private final String id;

        public SelectedBlockListElement(GuiAdventureBlockModifier parent, ItemStack displayStack, String id) {
            super(LIST_WIDTH, 24);
            this.displayStack = displayStack;
            this.id = id;
            buttonList.add(new RemoveElementButton(parent, LIST_WIDTH - 22, 2, 20, 20, this));
        }

        @Override
        public boolean match(String search) {
            String s = search.toLowerCase();
            return id.toLowerCase().contains(s)
                    || (!displayStack.isEmpty() && displayStack.getHoverName().getString().toLowerCase().contains(s));
        }

        @Override
        public void draw(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            int textX = offsetX + 4;
            if (!displayStack.isEmpty()) {
                GuiUtils.drawItemStack(graphics, displayStack, offsetX + 4, offsetY + 4);
                textX = offsetX + 26;
            }
            String line = displayStack.isEmpty() ? id : (displayStack.getHoverName().getString() + " (" + id + ")");
            int maxWidth = offsetX + getSizeX() - 26 - textX;
            String shown = line;
            if (maxWidth > 0 && font.width(shown) > maxWidth) {
                int dots = font.width("...");
                shown = font.plainSubstrByWidth(shown, Math.max(0, maxWidth - dots)) + "...";
            }
            GuiUtils.drawString(graphics, font, shown, textX, offsetY, Color.WHITE.getRGB(), getSizeY());
            super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }
    }

    private static class AddBlockListElement extends ListElement {
        public AddBlockListElement(GuiAdventureBlockModifier parent, Component label) {
            super(LIST_WIDTH, 24);
            buttonList.add(new EEButton(0, 2, LIST_WIDTH, 20, label, b -> parent.openBlockSelector()));
        }
    }

    private final ItemStack stack;
    private final Kind kind;
    private final Set<String> allBlockIds;
    private final Set<String> originalAllowedIds;
    private final Mode mode;

    private GuiAdventureBlockModifier(Screen parent, ItemStack stack, Kind kind, Mode mode, Set<String> originalAllowedIds,
                                      List<String> selectedIds) {
        super(parent, Component.translatable(kind.titleKey), new ArrayList<>(), s -> {
        }, new Tuple[0]);
        this.stack = stack;
        this.kind = kind;
        this.allBlockIds = getAllBlockIds();
        this.originalAllowedIds = originalAllowedIds;
        this.mode = mode;

        selectedIds.stream()
                .filter(id -> !id.isBlank())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .forEach(id -> addListElement(new SelectedBlockListElement(this, toDisplayStack(id), id)));
        addListElement(new AddBlockListElement(this, Component.translatable("gui.ee.modifier.meta.adventure.add")));

        setPaddingLeft(5);
        setPaddingTop(8);
        setNoAdaptativeSize(true);
    }

    public GuiAdventureBlockModifier(Screen parent, ItemStack stack, Kind kind) {
        this(parent, stack, kind, computeInitialMode(stack, kind), readAllowedIds(stack, kind), computeInitialDisplayIds(stack, kind));
    }

    private GuiAdventureBlockModifier(Screen parent, ItemStack stack, Kind kind, Mode mode, Set<String> originalAllowedIds,
                                      Set<String> allowedIds) {
        this(parent, stack, kind, mode, originalAllowedIds, new ArrayList<>(
                mode == Mode.ONLY ? allowedIds : difference(getAllBlockIds(), allowedIds)));
    }

    @Override
    public boolean isModified() {
        return !getAllowedIds().equals(originalAllowedIds);
    }

    @Override
    protected ItemStack get() {
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

    @Override
    public void init() {
        super.init();

        // Keep this away from the "Cancel" button area to avoid mis-clicks / overlay issues.
        addRenderableWidget(new EEButton(width / 2 - 100, height - 42, 99, 20, getModeButtonText(), b -> toggleMode()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);

        int n = Math.min(600, width - 20);
        int left = (width - n) / 2 + 6;
        int right = (width + n) / 2 - 6;
        int y = 40;
        GuiUtils.drawString(graphics, font, getModeLine(), left, y, Color.ORANGE.getRGB(), font.lineHeight);
        GuiUtils.drawRightString(graphics, font, NOTE.getString(), right, y, Color.GRAY.getRGB(), font.lineHeight);
    }

    private String getModeLine() {
        String modeLabel = I18n.get(mode == Mode.ONLY
                ? "gui.ee.modifier.meta.adventure.mode.only"
                : "gui.ee.modifier.meta.adventure.mode.except");
        int total = allBlockIds.size();
        if (mode == Mode.ONLY) {
            int allowedCount = getAllowedIds().size();
            return I18n.get("gui.ee.modifier.meta.adventure.mode") + ": " + modeLabel
                    + " (" + allowedCount + "/" + total + ")";
        }
        int excludedCount = getSelectedIds().size();
        int allowedCount = Math.max(0, total - excludedCount);
        return I18n.get("gui.ee.modifier.meta.adventure.mode") + ": " + modeLabel
                + " (" + excludedCount + " excluded, " + allowedCount + "/" + total + " allowed)";
    }

    private Component getModeButtonText() {
        return Component.literal(I18n.get("gui.ee.modifier.meta.adventure.mode") + ": ")
                .append(Component.translatable(mode == Mode.ONLY
                        ? "gui.ee.modifier.meta.adventure.modebtn.only"
                        : "gui.ee.modifier.meta.adventure.modebtn.except"));
    }

    private void toggleMode() {
        Set<String> selected = getSelectedIds();
        Mode newMode = mode == Mode.ONLY ? Mode.EVERYTHING_EXCEPT : Mode.ONLY;

        if (newMode == Mode.EVERYTHING_EXCEPT) {
            // When switching from ONLY->EXCEPT, keep meaning, but if NOTHING was allowed, start with empty exclusions
            // so the user can pick exclusions (EXCEPT means "allow all except ...").
            Set<String> excluded = selected.isEmpty() ? new LinkedHashSet<>() : difference(allBlockIds, selected);
            getMinecraft().setScreen(new GuiAdventureBlockModifier(parent, stack, kind, newMode, originalAllowedIds,
                    new ArrayList<>(excluded)));
        } else {
            // Switching from EXCEPT->ONLY keeps meaning (may create a large list if exclusions are empty).
            Set<String> allowed = difference(allBlockIds, selected);
            getMinecraft().setScreen(new GuiAdventureBlockModifier(parent, stack, kind, newMode, originalAllowedIds,
                    new ArrayList<>(allowed)));
        }
    }

    private void openBlockSelector() {
        Set<String> selected = getSelectedIds();
        var stacks = BuiltInRegistries.BLOCK.keySet().stream()
                .map(id -> {
                    var block = BuiltInRegistries.BLOCK.getValue(id);
                    if (block == null) return ItemStack.EMPTY;
                    var item = block.asItem();
                    if (item == Items.AIR) return ItemStack.EMPTY;
                    var stack = new ItemStack(item);
                    // item id matches block id for block items (good enough for UI)
                    if (selected.contains(id.toString())) return ItemStack.EMPTY;
                    return stack;
                })
                .filter(s -> !s.isEmpty());

        getMinecraft().setScreen(new GuiTypeListSelector(this,
                Component.translatable("gui.ee.modifier.meta.adventure.selectBlock"),
                chosen -> {
                    if (chosen == null || chosen.isEmpty()) {
                        return new GuiAdventureBlockModifier(parent, stack, kind, mode, originalAllowedIds,
                                new ArrayList<>(getSelectedIds()));
                    }
                    String chosenId = ItemUtils.getRegistry(chosen).toString();
                    List<String> next = new ArrayList<>(getSelectedIds());
                    if (!next.contains(chosenId)) {
                        next.add(chosenId);
                    }
                    return new GuiAdventureBlockModifier(parent, stack, kind, mode, originalAllowedIds, next);
                }, stacks));
    }

    private Set<String> getAllowedIds() {
        Set<String> selected = getSelectedIds();
        Set<String> allowed = mode == Mode.ONLY ? new LinkedHashSet<>(selected) : difference(allBlockIds, selected);
        allowed.retainAll(allBlockIds);
        return allowed;
    }

    private Set<String> getSelectedIds() {
        Set<String> selected = new LinkedHashSet<>();
        getElements().stream()
                .filter(le -> le instanceof SelectedBlockListElement)
                .map(le -> ((SelectedBlockListElement) le).id)
                .forEach(selected::add);
        return selected;
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

        // Legacy fallback (older versions / external NBT)
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

    private static Mode computeInitialMode(ItemStack stack, Kind kind) {
        Set<String> all = getAllBlockIds();
        Set<String> allowed = readAllowedIds(stack, kind);
        Set<String> excluded = difference(all, allowed);
        if (allowed.isEmpty()) {
            return Mode.ONLY;
        }
        return excluded.size() < allowed.size() ? Mode.EVERYTHING_EXCEPT : Mode.ONLY;
    }

    private static List<String> computeInitialDisplayIds(ItemStack stack, Kind kind) {
        Set<String> all = getAllBlockIds();
        Set<String> allowed = readAllowedIds(stack, kind);
        Mode mode = computeInitialMode(stack, kind);
        if (mode == Mode.ONLY) {
            return new ArrayList<>(allowed);
        }
        return new ArrayList<>(difference(all, allowed));
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
