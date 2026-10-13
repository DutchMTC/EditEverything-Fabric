package com.dutchmtc.ee.utils;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Preserves saved records verbatim until an explicit edit, including unreadable imported records.
 */
public final class SavedItemsLibrary {
    public static final class Entry {
        private String code;
        private ItemStack stack;

        private Entry(String code, HolderLookup.Provider registry) {
            read(code, registry);
        }

        private void read(String value, HolderLookup.Provider registry) {
            code = value;
            stack = ItemStack.EMPTY;
            if (value == null || value.isBlank()) return;
            try {
                var parsed = ItemUtils.getFromGiveCode(value, registry);
                if (parsed == null)
                    parsed =
                            ItemUtils.getFromGiveCode(
                                    ChatUtils.translateColorCodes(value), registry);
                if (parsed != null && !parsed.isEmpty()) stack = parsed.copy();
            } catch (RuntimeException ignored) {
            }
        }

        public String code() {
            return code;
        }

        public boolean available() {
            return !stack.isEmpty();
        }

        public ItemStack stack() {
            return stack.copy();
        }

        public String name() {
            return available() ? stack.getHoverName().getString() : "Unavailable saved item";
        }

        public boolean matches(String query) {
            String name = name().toLowerCase(Locale.ROOT);
            String material =
                    available()
                            ? stack.getItem().getName(stack).getString().toLowerCase(Locale.ROOT)
                            : "";
            String id =
                    available() ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() : "";
            for (String token : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                if (token.startsWith("#")) {
                    if (!available()
                            || stack.typeHolder()
                                    .tags()
                                    .noneMatch(
                                            tag ->
                                                    tag.location()
                                                            .toString()
                                                            .contains(token.substring(1))))
                        return false;
                } else if (token.startsWith("@")) {
                    if (!available()
                            || !BuiltInRegistries.ITEM
                                    .getKey(stack.getItem())
                                    .getNamespace()
                                    .contains(token.substring(1))) return false;
                } else if (!name.contains(token)
                        && !material.contains(token)
                        && !id.contains(token)
                        && !(!available()
                                && code != null
                                && code.toLowerCase(Locale.ROOT).contains(token))) return false;
            }
            return true;
        }
    }

    private final Supplier<List<String>> source;
    private final Consumer<List<String>> save;
    private final Supplier<HolderLookup.Provider> registry;
    private final List<Entry> entries = new ArrayList<>();
    private List<String> snapshot;
    private HolderLookup.Provider previousRegistry;

    public SavedItemsLibrary(
            Supplier<List<String>> source,
            Consumer<List<String>> save,
            Supplier<HolderLookup.Provider> registry) {
        this.source = source;
        this.save = save;
        this.registry = registry;
        refresh();
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean refresh() {
        var codes = new ArrayList<>(source.get());
        var access = registry.get();
        if (snapshot != null && snapshot.equals(codes) && previousRegistry == access) return false;
        Map<String, Deque<Entry>> existing = new HashMap<>();
        for (var entry : entries)
            existing.computeIfAbsent(entry.code, k -> new ArrayDeque<>()).add(entry);
        entries.clear();
        for (String code : codes) {
            var queue = existing.get(code);
            var entry =
                    queue == null || queue.isEmpty()
                            ? new Entry(code, access)
                            : queue.removeFirst();
            if (previousRegistry != access) entry.read(code, access);
            entries.add(entry);
        }
        snapshot = codes;
        previousRegistry = access;
        return true;
    }

    private void persist() {
        snapshot = new ArrayList<>();
        for (var entry : entries) snapshot.add(entry.code);
        save.accept(new ArrayList<>(snapshot));
    }

    public Entry add(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return addCode(ItemUtils.getGiveCode(stack.copy(), registry.get()));
    }

    public Entry addCode(String code) {
        refresh();
        var entry = new Entry(code, registry.get());
        if (!entry.available()) return null;
        entries.add(0, entry);
        persist();
        return entry;
    }

    public boolean update(Entry entry, ItemStack value) {
        if (value == null || value.isEmpty()) return false;
        return updateCode(entry, ItemUtils.getGiveCode(value.copy(), registry.get()));
    }

    public boolean updateCode(Entry entry, String code) {
        refresh();
        if (!entries.contains(entry)) return false;
        var replacement = new Entry(code, registry.get());
        if (!replacement.available()) return false;
        entry.code = replacement.code;
        entry.stack = replacement.stack;
        persist();
        return true;
    }

    public Entry duplicate(Entry entry) {
        refresh();
        int index = entries.indexOf(entry);
        if (index < 0) return null;
        var duplicate = new Entry(entry.code, registry.get());
        entries.add(index + 1, duplicate);
        persist();
        return duplicate;
    }

    public boolean remove(Entry entry) {
        refresh();
        if (!entries.remove(entry)) return false;
        persist();
        return true;
    }
}
