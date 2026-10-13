package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.utils.BookEditorData;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.input.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.*;
import java.util.function.Consumer;

/** Creative book editing on the native book texture, without the vanilla autosave packet. */
public final class GuiBookEditor extends BookEditScreen {
    private final Screen parent;
    private final Consumer<ItemStack> setter;
    private final BookEditorData data;
    private StyledPageField page;
    private TextSelectionToolbar toolbar;
    private int pageIndex;
    private Button apply;
    private PageButton back, forward;
    private EEDropdownButton options;
    private List<String> menu;
    private int menuFocus;
    private int savedCursor, savedAnchor;
    private boolean restoreSelection;
    private String signingTitle;

    public GuiBookEditor(Screen parent, ItemStack stack, Consumer<ItemStack> setter) {
        super(
                Objects.requireNonNull(Minecraft.getInstance().player),
                stack.copy(),
                InteractionHand.MAIN_HAND,
                WritableBookContent.EMPTY);
        this.parent = parent;
        this.setter = setter;
        data =
                new BookEditorData(
                        stack,
                        Minecraft.getInstance().player.getGameProfile().name(),
                        registries());
    }

    public BookEditorData data() {
        return data;
    }

    private HolderLookup.Provider registries() {
        return minecraft.level != null
                ? minecraft.level.registryAccess()
                : VanillaRegistries.createWorldLookup();
    }

    private int bookLeft() {
        return (width - 192) / 2;
    }

    private void remember() {
        if (page != null) {
            savedCursor = page.cursor();
            savedAnchor = page.anchor();
            restoreSelection = true;
        }
    }

    @Override
    public void removed() {
        remember();
        super.removed();
    }

    @Override
    public void resize(int width, int height) {
        remember();
        super.resize(width, height);
    }

    @Override
    protected void setInitialFocus() {
        if (page != null) setInitialFocus(page);
    }

    @Override
    protected void init() {
        clearWidgets();
        menu = null;
        pageIndex = Math.max(0, Math.min(pageIndex, data.pages.size() - 1));
        page =
                addRenderableWidget(
                        new StyledPageField(
                                font,
                                (width - 114) / 2,
                                28,
                                data.pages.get(pageIndex).document,
                                data.locked ? 32767 : WritableBookContent.PAGE_EDIT_LENGTH,
                                this::refresh));
        if (restoreSelection) {
            int length = page.inputText().length();
            page.helper()
                    .setSelectionRange(
                            Math.min(savedCursor, length), Math.min(savedAnchor, length));
            restoreSelection = false;
        }
        toolbar =
                new TextSelectionToolbar(
                        this,
                        page,
                        page.document(),
                        font,
                        8,
                        width - 8,
                        page::refresh,
                        () -> 0xFF000000,
                        this::openActions);
        back =
                addRenderableWidget(
                        new PageButton(bookLeft() + 43, 159, false, b -> turn(-1), true));
        forward =
                addRenderableWidget(
                        new PageButton(bookLeft() + 116, 159, true, b -> turn(1), true));
        int x = width / 2 - 100, y = 196;
        if (!data.locked)
            addRenderableWidget(
                    Button.builder(
                                    Component.translatable("book.signButton"),
                                    b -> {
                                        remember();
                                        minecraft.gui.setScreen(new Signing(this));
                                    })
                            .bounds(x, y, 98, 20)
                            .build());
        apply =
                addRenderableWidget(
                        Button.builder(CommonComponents.GUI_DONE, b -> save())
                                .bounds(
                                        data.locked ? x : width / 2 + 2,
                                        y,
                                        data.locked ? 200 : 98,
                                        20)
                                .build());
        options =
                addRenderableWidget(
                        new EEDropdownButton(
                                x,
                                y + 24,
                                200,
                                20,
                                Component.literal("Book tools"),
                                b -> {
                                    remember();
                                    menu =
                                            menu == null
                                                    ? List.of(
                                                            "Add page",
                                                            "Duplicate page",
                                                            "Move earlier",
                                                            "Move later",
                                                            "Delete page",
                                                            "Book settings",
                                                            "Preview",
                                                            data.locked
                                                                    ? "Unlock book..."
                                                                    : "Discard changes...")
                                                    : null;
                                    menuFocus = 0;
                                }));
        refresh();
        setInitialFocus(page);
    }

    private void save() {
        refresh();
        if (invalidReason() == null) {
            setter.accept(data.result());
            minecraft.gui.setScreen(parent);
        }
    }

    private String invalidReason() {
        String error = data.invalidReason(registries());
        if (error != null) return error;
        for (int i = 0; i < data.pages.size(); i++) {
            var document = data.pages.get(i).document;
            if (document.legacyMode()
                    && font.wordWrapHeight(document.editingComponent(), 114) > 126)
                return "Page " + (i + 1) + " does not fit after decoding its color codes.";
        }
        return null;
    }

    private void refresh() {
        if (apply == null) return;
        String reason = invalidReason();
        apply.active = reason == null;
        apply.setTooltip(reason == null ? null : Tooltip.create(Component.literal(reason)));
        if (back != null) back.visible = pageIndex > 0;
        if (forward != null)
            forward.visible =
                    pageIndex < data.pages.size() - 1
                            || data.pages.size() < BookEditorData.MAX_PAGES;
    }

    private void turn(int delta) {
        if (menu != null) {
            menu = null;
            return;
        }
        int target = pageIndex + delta;
        if (target == data.pages.size() && data.pages.size() < BookEditorData.MAX_PAGES)
            data.pages.add(new BookEditorData.Page(Component.empty()));
        if (target < 0 || target >= data.pages.size()) return;
        pageIndex = target;
        restoreSelection = false;
        rebuildWidgets();
    }

    private void openActions() {
        int first = toolbar.selectionStart(), last = toolbar.selectionEnd();
        if (first < 0 || last <= first) return;
        remember();
        minecraft.gui.setScreen(
                new GuiTextActionsEditor(
                        this,
                        page.document().styleAt(first + 1),
                        data.pages.size(),
                        registries(),
                        style -> {
                            data.pages
                                    .get(pageIndex)
                                    .document
                                    .apply(
                                            first,
                                            last,
                                            original ->
                                                    original.withClickEvent(style.getClickEvent())
                                                            .withHoverEvent(style.getHoverEvent())
                                                            .withInsertion(style.getInsertion()));
                        }));
    }

    private String disabled(int action) {
        if (action < 2 && data.pages.size() >= BookEditorData.MAX_PAGES)
            return "Books can have at most " + BookEditorData.MAX_PAGES + " pages.";
        if (action == 2 && pageIndex == 0) return "This is already the first page.";
        if (action == 3 && pageIndex == data.pages.size() - 1)
            return "This is already the last page.";
        if (action == 4 && data.pages.size() == 1) return "A book needs at least one page.";
        if (action == 7
                && data.locked
                && data.pages.stream()
                        .anyMatch(
                                p ->
                                        p.document.text().length()
                                                > WritableBookContent.PAGE_EDIT_LENGTH))
            return "Shorten pages to 1024 characters before converting this to a Book and Quill.";
        return null;
    }

    private void choose(int action) {
        menu = null;
        if (disabled(action) != null) return;
        if (action >= 5) {
            remember();
            if (action == 5) minecraft.gui.setScreen(new BookSettings(this));
            else if (action == 6) minecraft.gui.setScreen(new Preview(this));
            else if (!data.locked) onClose();
            else
                minecraft.gui.setScreen(
                        new GuiConfirmation(
                                this,
                                Component.literal(
                                        "Turn this signed book into an editable Book and Quill? Its"
                                            + " text, author and formatting will be kept. Use Sign"
                                            + " when you want to lock it again."),
                                Component.literal("Unlock book"),
                                () -> {
                                    data.locked = false;
                                    minecraft.gui.setScreen(this);
                                },
                                () -> minecraft.gui.setScreen(this)));
            return;
        }
        if (action == 4) {
            minecraft.gui.setScreen(
                    new GuiConfirmation(
                            this,
                            Component.literal("Delete page " + (pageIndex + 1) + "?"),
                            Component.literal("Delete page"),
                            () -> {
                                int deleted = pageIndex + 1;
                                data.pages.remove(pageIndex);
                                data.remapPageLinks(
                                        number ->
                                                number == deleted
                                                        ? Math.min(deleted, data.pages.size())
                                                        : number > deleted ? number - 1 : number);
                                pageIndex = Math.min(pageIndex, data.pages.size() - 1);
                                restoreSelection = false;
                                minecraft.gui.setScreen(this);
                            },
                            () -> minecraft.gui.setScreen(this)));
            return;
        }
        if (action < 2) {
            int inserted = pageIndex + 2;
            data.pages.add(
                    pageIndex + 1,
                    action == 0
                            ? new BookEditorData.Page(Component.empty())
                            : data.pages.get(pageIndex).duplicate());
            data.remapPageLinks(number -> number >= inserted ? number + 1 : number);
            pageIndex++;
        } else {
            int target = pageIndex + (action == 2 ? -1 : 1), a = pageIndex + 1, b = target + 1;
            Collections.swap(data.pages, pageIndex, target);
            data.remapPageLinks(number -> number == a ? b : number == b ? a : number);
            pageIndex = target;
        }
        restoreSelection = false;
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        remember();
        if (data.changed())
            minecraft.gui.setScreen(
                    new GuiConfirmation(
                            this,
                            Component.literal("Discard changes to this book?"),
                            Component.literal("Discard changes"),
                            () -> minecraft.gui.setScreen(parent),
                            () -> minecraft.gui.setScreen(this)));
        else minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (menu != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) menu = null;
            else if (event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_DOWN)
                menuFocus =
                        Math.floorMod(
                                menuFocus + (event.key() == InputConstants.KEY_UP ? -1 : 1),
                                menu.size());
            else if (event.key() == InputConstants.KEY_RETURN) choose(menuFocus);
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_S && event.hasControlDown()) {
            refresh();
            if (apply.active) apply.onPress(event);
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEUP) {
            turn(-1);
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEDOWN) {
            turn(1);
            return true;
        }
        if (event.key() == InputConstants.KEY_TAB) {
            var controls =
                    children().stream()
                            .filter(
                                    child ->
                                            child instanceof AbstractWidget widget
                                                    && widget.visible
                                                    && widget.active)
                            .map(child -> (AbstractWidget) child)
                            .toList();
            if (!controls.isEmpty()) {
                int next =
                        Math.floorMod(
                                controls.indexOf(getFocused()) + (event.hasShiftDown() ? -1 : 1),
                                controls.size());
                clearFocus();
                setInitialFocus(controls.get(next));
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private int menuY() {
        return options.getY() - menu.size() * 20 - 4;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return menu != null || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu != null) {
            int dx = (int) event.x() - options.getX(), dy = (int) event.y() - menuY();
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && dx >= 0
                    && dx < 200
                    && dy >= 0
                    && dy < menu.size() * 20) {
                choose(dy / 20);
                return true;
            }
            menu = null;
            return true;
        }
        if (toolbar.click(event)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        Component indicator =
                Component.translatable("book.pageIndicator", pageIndex + 1, data.pages.size());
        g.text(font, indicator, bookLeft() + 148 - font.width(indicator), 18, 0xFF000000, false);
        for (var child : children())
            if (child instanceof AbstractWidget widget) widget.extractRenderState(g, mx, my, delta);
        toolbar.render(g, mx, my);
        if (menu != null) {
            int x = options.getX(), y = menuY();
            EETheme.bevel(g, x - 2, y - 2, 204, menu.size() * 20 + 4, EETheme.PANEL, false);
            for (int i = 0; i < menu.size(); i++) {
                boolean hover =
                        mx >= x && mx < x + 200 && my >= y + i * 20 && my < y + (i + 1) * 20;
                String reason = disabled(i);
                EETheme.rect(
                        g,
                        x,
                        y + i * 20,
                        200,
                        20,
                        hover || i == menuFocus ? EETheme.OPTION_HOVER : EETheme.OPTION);
                g.text(
                        font,
                        menu.get(i),
                        x + 6,
                        y + i * 20 + 6,
                        reason == null ? EETheme.TEXT : EETheme.MUTED,
                        false);
                if (hover && reason != null)
                    g.setTooltipForNextFrame(font, Component.literal(reason), mx, my);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        return Component.literal(
                "Book editor, page " + (pageIndex + 1) + " of " + data.pages.size());
    }

    private static final class BookSettings extends EESettingsScreen<Void> {
        private final GuiBookEditor editor;
        private String bookTitle, author;
        private int generation;
        private boolean resolved;

        BookSettings(GuiBookEditor editor) {
            super(editor, "Book settings", ignored -> {});
            this.editor = editor;
            bookTitle = editor.data.title;
            author = editor.data.author;
            generation = editor.data.generation;
            resolved = editor.data.resolved;
            setter =
                    ignored -> {
                        editor.data.title = bookTitle;
                        editor.data.author = author;
                        editor.data.generation = generation;
                        editor.data.resolved = resolved;
                    };
        }

        protected void buildRows() {
            validateMetadata();
            EditBox title =
                    text(
                            "Title",
                            bookTitle,
                            v -> {
                                bookTitle = v;
                                validateMetadata();
                            });
            title.setMaxLength(128);
            if (editor.data.locked)
                row(
                        "Title",
                        "The book's title, up to 32 characters. Required for a locked book.",
                        title,
                        180);
            row(
                    "Author",
                    "The name displayed as the book's author. This is not a player UUID or access"
                            + " restriction.",
                    text(
                            "Author",
                            author,
                            v -> {
                                author = v;
                                validateMetadata();
                            }),
                    180);
            var generations = List.of("Original", "Copy of original", "Copy of a copy", "Tattered");
            if (editor.data.locked)
                row(
                        "Copy generation",
                        "Controls copying in a crafting table and the generation shown in the"
                                + " tooltip.",
                        dropdown(
                                generations.get(Math.max(0, Math.min(3, generation))),
                                generations,
                                v -> v,
                                v -> generation = generations.indexOf(v)),
                        180);
            if (editor.data.locked)
                row(
                        "Resolved text",
                        "On keeps text literal. Off lets Minecraft resolve imported selectors and"
                                + " scores when the written book is first read.",
                        toggle(() -> resolved, v -> resolved = v),
                        52);
            validateMetadata();
        }

        private void validateMetadata() {
            errors.remove("Title");
            errors.remove("Author");
            if (bookTitle.length() > 32)
                errors.put("Title", "The title can have at most 32 characters.");
            else if (editor.data.locked && bookTitle.isBlank())
                errors.put("Title", "Enter a title for the locked book.");
            if (author.isBlank()) errors.put("Author", "Enter an author for the locked book.");
            for (var child : children())
                if (child instanceof EditBox field)
                    EETextField.validate(field, errors.get(field.getMessage().getString()));
        }

        protected Void result() {
            return null;
        }

        public boolean isModified() {
            var d = editor.data;
            return !bookTitle.equals(d.title)
                    || !author.equals(d.author)
                    || generation != d.generation
                    || resolved != d.resolved;
        }
    }

    private static final class Signing extends BookSignScreen {
        private final GuiBookEditor editor;
        private EditBox titleInput;
        private Button finish;

        Signing(GuiBookEditor editor) {
            super(
                    editor,
                    Objects.requireNonNull(Minecraft.getInstance().player),
                    InteractionHand.MAIN_HAND,
                    editor.data.pages.stream().map(p -> p.document.text()).toList());
            this.editor = editor;
        }

        @Override
        protected void init() {
            super.init();
            titleInput =
                    children().stream()
                            .filter(c -> c instanceof EditBox)
                            .map(c -> (EditBox) c)
                            .findFirst()
                            .orElseThrow();
            for (var child : List.copyOf(children()))
                if (child instanceof Button) removeWidget(child);
            finish =
                    addRenderableWidget(
                            Button.builder(
                                            Component.translatable("book.finalizeButton"),
                                            b -> sign())
                                    .bounds(width / 2 - 100, 196, 98, 20)
                                    .build());
            addRenderableWidget(
                    Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                            .bounds(width / 2 + 2, 196, 98, 20)
                            .build());
            titleInput.setMaxLength(WrittenBookContent.TITLE_MAX_LENGTH);
            titleInput.setResponder(value -> refreshSigning());
            titleInput.setValue(
                    editor.signingTitle == null ? editor.data.title : editor.signingTitle);
            refreshSigning();
        }

        private void refreshSigning() {
            finish.active =
                    !titleInput.getValue().isBlank()
                            && !editor.data.author.isBlank()
                            && editor.invalidReason() == null;
            String reason =
                    titleInput.getValue().isBlank()
                            ? "Enter a title to sign the book."
                            : editor.data.author.isBlank()
                                    ? "Set an author in Book settings."
                                    : editor.invalidReason();
            finish.setTooltip(reason == null ? null : Tooltip.create(Component.literal(reason)));
        }

        private void sign() {
            refreshSigning();
            if (!finish.active) return;
            editor.data.title = titleInput.getValue().trim();
            editor.data.locked = true;
            editor.save();
        }

        @Override
        public void removed() {
            if (titleInput != null) editor.signingTitle = titleInput.getValue();
            super.removed();
        }

        @Override
        public void resize(int width, int height) {
            if (titleInput != null) editor.signingTitle = titleInput.getValue();
            super.resize(width, height);
        }

        @Override
        public void onClose() {
            minecraft.gui.setScreen(editor);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                onClose();
                return true;
            }
            if (event.isConfirmation() && titleInput.isFocused()) {
                sign();
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            super.extractBackground(g, mx, my, delta);
            for (var child : children())
                if (child instanceof AbstractWidget widget)
                    widget.extractRenderState(g, mx, my, delta);
            Component heading = Component.translatable("book.editTitle");
            Component author =
                    Component.translatable("book.byAuthor", editor.data.author)
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY);
            int left = (width - 192) / 2 + 36;
            g.text(font, heading, left + (114 - font.width(heading)) / 2, 34, 0xFF000000, false);
            g.text(font, author, left + (114 - font.width(author)) / 2, 60, 0xFF000000, false);
            g.textWithWordWrap(
                    font,
                    Component.translatable("book.finalizeWarning"),
                    left,
                    82,
                    114,
                    0xFF000000,
                    false);
        }
    }

    private static final class Preview extends BookViewScreen {
        private final GuiBookEditor editor;

        Preview(GuiBookEditor editor) {
            super(new BookAccess(editor.data.pages.stream().map(p -> p.content().raw()).toList()));
            this.editor = editor;
        }

        protected void init() {
            super.init();
            setPage(editor.pageIndex);
        }

        protected void createMenuControls() {
            addRenderableWidget(
                    new EEButton(
                            (width - 200) / 2,
                            menuControlsTop(),
                            200,
                            20,
                            Component.literal("Back to editor"),
                            b -> onClose()));
        }

        protected boolean handleClickEvent(ClickEvent event) {
            return event instanceof ClickEvent.ChangePage
                    ? super.handleClickEvent(event)
                    : event != null;
        }

        public void onClose() {
            minecraft.gui.setScreen(editor);
        }
    }
}
