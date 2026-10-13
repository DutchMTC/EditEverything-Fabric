package com.dutchmtc.ee.utils;

import com.dutchmtc.ee.gui.modifier.StyledTextDocument;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.*;
import java.util.function.IntUnaryOperator;

/** Native book state, with rich editable drafts stored separately from signed-book components. */
public final class BookEditorData {
    public static final int MAX_PAGES = WritableBookContent.MAX_PAGES;
    public static final String DRAFT_KEY = "ee:book_draft";

    public static final class Page {
        public final StyledTextDocument document;
        private Filterable<Component> original;
        private Component originalEditing;

        public Page(Filterable<Component> value) {
            original = value;
            document = new StyledTextDocument(value.raw());
            originalEditing = document.component();
        }

        public Page(Component value) {
            this(Filterable.passThrough(value));
        }

        public Filterable<Component> content() {
            return document.component().equals(originalEditing)
                    ? original
                    : Filterable.passThrough(document.component());
        }

        public Page duplicate() {
            return new Page(content());
        }

        public void remapPageLinks(IntUnaryOperator positions) {
            original = original.map(value -> remap(value, positions));
            originalEditing = remap(originalEditing, positions);
            document.apply(0, document.text().length(), style -> remap(style, positions));
        }

        private static Style remap(Style style, IntUnaryOperator positions) {
            return style.getClickEvent() instanceof ClickEvent.ChangePage jump
                    ? style.withClickEvent(
                            new ClickEvent.ChangePage(positions.applyAsInt(jump.page())))
                    : style;
        }

        private static Component remap(Component value, IntUnaryOperator positions) {
            Style style = remap(value.getStyle(), positions);
            var siblings = value.getSiblings().stream().map(c -> remap(c, positions)).toList();
            if (style.equals(value.getStyle()) && siblings.equals(value.getSiblings()))
                return value;
            var result = value.plainCopy().withStyle(style);
            siblings.forEach(result::append);
            return result;
        }
    }

    public final List<Page> pages = new ArrayList<>();
    public String title, author;
    public int generation;
    public boolean locked, resolved;
    private final ItemStack source;
    private final Filterable<String> originalTitle;
    private final WritableBookContent originalWritable;
    private final ItemStack baseline;
    private final HolderLookup.Provider registries;

    public static boolean supports(ItemStack stack) {
        return stack.is(Items.WRITABLE_BOOK)
                || stack.is(Items.WRITTEN_BOOK)
                || stack.is(Items.BOOK);
    }

    public BookEditorData(ItemStack stack, String defaultAuthor) {
        this(stack, defaultAuthor, VanillaRegistries.createWorldLookup());
    }

    public BookEditorData(ItemStack stack, String defaultAuthor, HolderLookup.Provider registries) {
        this.registries = registries;
        source = stack.copy();
        locked = stack.is(Items.WRITTEN_BOOK);
        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (!locked && written == null) {
            var backup =
                    stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                            .copyTag()
                            .getString(DRAFT_KEY)
                            .orElse(null);
            if (backup != null) {
                try {
                    written =
                            WrittenBookContent.CODEC
                                    .parse(
                                            registries.createSerializationContext(JsonOps.INSTANCE),
                                            JsonParser.parseString(backup))
                                    .result()
                                    .orElse(null);
                } catch (RuntimeException ignored) {
                    // A malformed backup must not prevent editing the native book pages.
                }
            }
        }
        originalWritable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        originalTitle = written == null ? Filterable.passThrough("") : written.title();
        title = originalTitle.raw();
        author = written == null ? defaultAuthor : written.author();
        generation = written == null ? 0 : written.generation();
        resolved = written == null || written.resolved();
        if (written != null) written.pages().forEach(p -> pages.add(new Page(p)));
        if (!locked && originalWritable != null) {
            for (int i = 0; i < originalWritable.pages().size(); i++) {
                var raw = originalWritable.pages().get(i);
                if (i < pages.size()) {
                    // Vanilla can still edit an unlocked book. Merge those edits into the retained
                    // styles.
                    pages.get(i).document.replaceText(raw.raw());
                } else pages.add(new Page(raw.map(Component::literal)));
            }
            while (pages.size() > originalWritable.pages().size()) pages.removeLast();
        }
        if (pages.isEmpty()) pages.add(new Page(Component.empty()));
        baseline = result();
    }

    public WrittenBookContent writtenContent() {
        return new WrittenBookContent(
                title.equals(originalTitle.raw()) ? originalTitle : Filterable.passThrough(title),
                author,
                generation,
                pages.stream().map(Page::content).toList(),
                resolved);
    }

    public void remapPageLinks(IntUnaryOperator positions) {
        pages.forEach(page -> page.remapPageLinks(positions));
    }

    public String invalidReason(HolderLookup.Provider registries) {
        if (pages.size() > MAX_PAGES) return "Books can have at most " + MAX_PAGES + " pages.";
        if (title.length() > WrittenBookContent.TITLE_MAX_LENGTH)
            return "The title can have at most "
                    + WrittenBookContent.TITLE_MAX_LENGTH
                    + " characters.";
        if (locked && title.isBlank())
            return "Enter a title in Book settings before locking the book.";
        if (locked && author.isBlank())
            return "Enter an author in Book settings before locking the book.";
        if (generation < 0 || generation > WrittenBookContent.MAX_GENERATION)
            return "Choose a valid copy generation.";
        for (int i = 0; i < pages.size(); i++) {
            if (!locked
                    && pages.get(i).document.text().length() > WritableBookContent.PAGE_EDIT_LENGTH)
                return "Page "
                        + (i + 1)
                        + " exceeds the editable book's "
                        + WritableBookContent.PAGE_EDIT_LENGTH
                        + " character limit.";
            try {
                var encoded =
                        ComponentSerialization.CODEC
                                .encodeStart(
                                        registries.createSerializationContext(JsonOps.INSTANCE),
                                        pages.get(i).content().raw())
                                .getOrThrow();
                if (encoded.toString().length() > WrittenBookContent.PAGE_LENGTH)
                    return "Page " + (i + 1) + " has too much text or formatting.";
            } catch (RuntimeException e) {
                return "Page " + (i + 1) + ": " + e.getMessage();
            }
        }
        try {
            WrittenBookContent.CODEC
                    .encodeStart(
                            registries.createSerializationContext(JsonOps.INSTANCE),
                            writtenContent())
                    .getOrThrow();
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        return null;
    }

    public ItemStack result() {
        ItemStack result =
                ItemUtils.setItem(locked ? Items.WRITTEN_BOOK : Items.WRITABLE_BOOK, source.copy());
        int amount = Math.max(1, Math.min(99, source.getCount()));
        if (result.has(DataComponents.MAX_DAMAGE)) {
            amount = 1;
            result.set(DataComponents.MAX_STACK_SIZE, 1);
        } else if (amount > result.getMaxStackSize())
            result.set(DataComponents.MAX_STACK_SIZE, amount);
        result.setCount(amount);
        if (locked) {
            result.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenContent());
            result.remove(DataComponents.WRITABLE_BOOK_CONTENT);
            CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.remove(DRAFT_KEY));
        } else {
            result.remove(DataComponents.WRITTEN_BOOK_CONTENT);
            var backup =
                    WrittenBookContent.CODEC
                            .encodeStart(
                                    registries.createSerializationContext(JsonOps.INSTANCE),
                                    writtenContent())
                            .getOrThrow()
                            .toString();
            CustomData.update(
                    DataComponents.CUSTOM_DATA, result, tag -> tag.putString(DRAFT_KEY, backup));
            List<Filterable<String>> plain = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                String value = pages.get(i).document.text();
                plain.add(
                        originalWritable != null
                                        && i < originalWritable.pages().size()
                                        && originalWritable.pages().get(i).raw().equals(value)
                                ? originalWritable.pages().get(i)
                                : pages.get(i).content().map(Component::getString));
            }
            result.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(plain));
        }
        return result;
    }

    public boolean changed() {
        return !ItemStack.matches(baseline, result());
    }
}
