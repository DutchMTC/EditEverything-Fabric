package com.dutchmtc.ee.utils;

import com.dutchmtc.ee.mixin.AdventureModePredicateAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Persisted permission descriptions, kept separate from the user's editable lore. */
public final class AdventureBlockLore {
    private static final String KEY = "ee:adventure_rules";
    private static final List<DataComponentType<AdventureModePredicate>> TYPES = List.of(DataComponents.CAN_BREAK, DataComponents.CAN_PLACE_ON);
    private AdventureBlockLore() {}

    public record Rule(boolean except, boolean show, Set<String> blocks) {
        public Rule { blocks = Collections.unmodifiableSet(new LinkedHashSet<>(blocks)); }
    }
    private static String key(DataComponentType<AdventureModePredicate> type) { return type == DataComponents.CAN_BREAK ? "break" : "place"; }
    private static CompoundTag data(ItemStack stack) { return ItemUtils.getTag(stack).getCompound(KEY).orElseGet(CompoundTag::new); }

    /** Ignore stale metadata if another editor changed the actual permission. */
    public static Optional<Rule> rule(ItemStack stack, DataComponentType<AdventureModePredicate> type) {
        var entry = data(stack).getCompound(key(type));
        if (entry.isEmpty()) return Optional.empty();
        CompoundTag tag = entry.get();
        Set<String> listed = new LinkedHashSet<>();
        for (var value : tag.getListOrEmpty("blocks")) if (value instanceof StringTag id) listed.add(id.value());
        boolean except = tag.getBooleanOr("except", false);
        Set<String> expected = except ? allBlocks() : new LinkedHashSet<>(listed);
        if (except) expected.removeAll(listed);
        return expected.equals(allowed(stack.get(type))) ? Optional.of(new Rule(except, tag.getBooleanOr("show", true), listed)) : Optional.empty();
    }
    private static Set<String> allBlocks() {
        Set<String> all = new LinkedHashSet<>(); BuiltInRegistries.BLOCK.keySet().forEach(id -> all.add(id.toString())); return all;
    }
    private static Set<String> allowed(AdventureModePredicate permission) {
        Set<String> allowed = new LinkedHashSet<>();
        if (permission != null) for (var predicate : ((AdventureModePredicateAccessor) permission).ee$getPredicates()) {
            if (predicate.blocks().isEmpty()) return allBlocks();
            predicate.blocks().get().stream().forEach(holder -> allowed.add(BuiltInRegistries.BLOCK.getKey(holder.value()).toString()));
        }
        return allowed;
    }

    /** Strip only the exact suffix we generated; never remove user text by its wording. */
    public static List<Component> editableLore(ItemStack stack) {
        var lore = stack.get(DataComponents.LORE);
        List<Component> lines = lore == null ? List.of() : lore.lines();
        var tailTag = data(stack).get("tail");
        if (tailTag == null) return List.copyOf(lines);
        var tail = ItemLore.CODEC.parse(NbtOps.INSTANCE, tailTag).result().map(ItemLore::lines).orElse(List.of());
        int start = lines.size() - tail.size();
        return !tail.isEmpty() && start >= 0 && lines.subList(start, lines.size()).equals(tail)
                ? List.copyOf(lines.subList(0, start)) : List.copyOf(lines);
    }
    private static List<Component> section(DataComponentType<AdventureModePredicate> type, Rule rule) {
        if (!rule.except || !rule.show) return List.of();
        List<Component> result = new ArrayList<>();
        String translation = "gui.ee.modifier.meta.adventure.lore." + key(type) + (rule.blocks.isEmpty() ? ".all" : ".except");
        String fallback = (type == DataComponents.CAN_BREAK ? "Can break all blocks" : "Can be placed on all blocks")
                + (rule.blocks.isEmpty() ? "" : " except:");
        result.add(Component.translatableWithFallback(translation, fallback).withStyle(ChatFormatting.GRAY).withStyle(style -> style.withItalic(false)));
        rule.blocks.stream().sorted().forEach(id -> {
            Identifier parsed = Identifier.tryParse(id);
            Component name = parsed == null ? Component.literal(id) : BuiltInRegistries.BLOCK.getOptional(parsed)
                    .map(block -> (Component) block.getName()).orElseGet(() -> Component.literal(id));
            result.add(Component.literal(" ").append(name).withStyle(ChatFormatting.DARK_GRAY).withStyle(style -> style.withItalic(false)));
        });
        return result;
    }
    private static List<Component> generated(ItemStack stack, DataComponentType<AdventureModePredicate> replacementType, Rule replacement) {
        List<Component> result = new ArrayList<>();
        for (var type : TYPES) {
            Rule rule = type == replacementType ? replacement : rule(stack, type).orElse(null);
            List<Component> section = rule == null ? List.of() : section(type, rule);
            if (section.isEmpty()) continue;
            if (!result.isEmpty()) result.add(Component.empty());
            result.addAll(section);
        }
        return result;
    }
    public static int requiredLines(ItemStack stack, DataComponentType<AdventureModePredicate> type, Rule replacement) {
        int regular = editableLore(stack).size(), managed = generated(stack, type, replacement).size();
        return regular + managed + (regular > 0 && managed > 0 ? 1 : 0);
    }
    public static int editableLimit(ItemStack stack) {
        int managed = generated(stack, null, null).size();
        return Math.max(0, ItemLore.MAX_LINES - managed - (managed > 0 ? 1 : 0));
    }
    public static void writeRule(ItemStack stack, DataComponentType<AdventureModePredicate> type, Rule rule) {
        List<Component> regular = editableLore(stack);
        if (requiredLines(stack, type, rule) > ItemLore.MAX_LINES) throw new IllegalArgumentException("Permission lore exceeds Minecraft's lore limit");
        CompoundTag root = data(stack).copy(), entry = new CompoundTag();
        entry.putBoolean("except", rule.except); entry.putBoolean("show", rule.show);
        ListTag ids = new ListTag(); rule.blocks.forEach(id -> ids.add(StringTag.valueOf(id))); entry.put("blocks", ids);
        root.put(key(type), entry); putData(stack, root);
        writeLore(stack, regular);
    }
    public static ItemStack writeLore(ItemStack stack, List<Component> regular) {
        List<Component> tail = new ArrayList<>(generated(stack, null, null));
        if (!tail.isEmpty() && !regular.isEmpty()) tail.addFirst(Component.empty());
        if (regular.size() + tail.size() > ItemLore.MAX_LINES) throw new IllegalArgumentException("Lore exceeds Minecraft's lore limit");
        List<Component> combined = new ArrayList<>(regular); combined.addAll(tail);
        if (combined.isEmpty()) stack.remove(DataComponents.LORE); else stack.set(DataComponents.LORE, new ItemLore(List.copyOf(combined)));
        if (!tail.isEmpty() && !ItemUtils.isTooltipComponentVisible(stack, DataComponents.LORE))
            ItemUtils.setTooltipComponentVisible(stack, DataComponents.LORE, true);
        CompoundTag root = data(stack).copy(); root.remove("tail");
        for (var type : TYPES) {
            var rule = rule(stack, type);
            if (rule.isEmpty()) root.remove(key(type));
            else if (rule.get().except) ItemUtils.setTooltipComponentVisible(stack, type, false);
        }
        if (!tail.isEmpty()) root.put("tail", ItemLore.CODEC.encodeStart(NbtOps.INSTANCE, new ItemLore(tail)).getOrThrow());
        putData(stack, root);
        return stack;
    }
    private static void putData(ItemStack stack, CompoundTag rules) {
        CompoundTag tag = ItemUtils.getTag(stack);
        if (rules.isEmpty() && !tag.contains(KEY)) return;
        if (!rules.isEmpty() && tag.getCompound(KEY).filter(rules::equals).isPresent()) return;
        if (rules.isEmpty()) tag.remove(KEY); else tag.put(KEY, rules);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    /** Commit managed lore and metadata while preserving other custom data on the destination. */
    public static void copyManaged(ItemStack source, ItemStack destination) {
        putData(destination, data(source).copy());
        if (!java.util.Objects.equals(source.get(DataComponents.LORE), destination.get(DataComponents.LORE)))
            ItemUtils.setComponent(destination, DataComponents.LORE, source.get(DataComponents.LORE));
        if (data(source).contains("tail") && !ItemUtils.isTooltipComponentVisible(destination, DataComponents.LORE))
            ItemUtils.setTooltipComponentVisible(destination, DataComponents.LORE, true);
    }
}
