package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.gui.modifier.GuiItemStackModifier;
import com.dutchmtc.ee.gui.modifier.GuiModifier;
import com.dutchmtc.ee.mixin.CommandSuggestionsAccessor;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.*;

/** A literal item-code input with an explicit preview and contextual commit actions. */
public class GuiGiver extends GuiModifier<String> {
    private EETextField code;
    private CommandSuggestions suggestions;
    private ItemCodeParser<ClientSuggestionProvider> itemParser;
    private Tooltip codeHelp;
    private int suggestionCursor = -1;
    private EEButton giveButton, saveButton, doneButton, editButton, pasteButton;
    private EEDropdownButton options;
    private String preText = "", originalCodeValue, lastCodeValue, itemError, notice;
    private ItemStack currentItemStack;
    private boolean deleteButton, importing, menuOpen, initialized;
    private HolderLookup.Provider fallbackRegistry;
    private int left, top, panelWidth, menuFocus;
    private static final int PANEL_HEIGHT = 196;
    private final List<MenuAction> actions = new ArrayList<>();

    private record MenuAction(String label, Supplier<String> blocked, String help, Runnable run) {}

    private HolderLookup.Provider registryAccess() {
        if (mc.level != null) return mc.level.registryAccess();
        if (fallbackRegistry == null) fallbackRegistry = VanillaRegistries.createWorldLookup();
        return fallbackRegistry;
    }

    public GuiGiver(Screen parent) {
        this(parent, heldItem());
    }

    private static ItemStack heldItem() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem().copy();
    }

    public GuiGiver(Screen parent, ItemStack itemStack) {
        this(parent, itemStack, null, false);
    }

    public GuiGiver(
            Screen parent, ItemStack itemStack, Consumer<String> setter, boolean deleteButton) {
        super(parent, Component.literal("Item giver"), setter);
        preText =
                itemStack == null ? "" : ItemUtils.getGiveCode(itemStack.copy(), registryAccess());
        currentItemStack = itemStack == null ? null : itemStack.copy();
        this.deleteButton = deleteButton;
    }

    public GuiGiver(Screen parent, String preText) {
        this(parent, preText, null, false);
    }

    public GuiGiver(Screen parent, String preText, Consumer<String> setter, boolean deleteButton) {
        this(parent, (ItemStack) null, setter, deleteButton);
        this.preText = preText == null ? "" : preText;
    }

    /** Imports have a different destination from editing an existing code string. */
    public GuiGiver forImport() {
        importing = true;
        return this;
    }

    private String screenTitle() {
        return setter == null ? "Item giver" : importing ? "Import item" : "Edit item code";
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null && parent.isPauseScreen();
    }

    @Override
    protected void repositionElements() {
        init();
    }

    @Override
    protected void setInitialFocus() {}

    @Override
    public void init() {
        String draft = code == null ? preText : code.getValue();
        int cursor = code == null ? draft.length() : code.getCursorPosition();
        int anchor = code == null ? cursor : ((EditBoxAccessor) code).ee$getHighlightPos();
        boolean focused = !initialized || code != null && code.isFocused();
        initialized = true;
        clearWidgets();
        clearFocus();
        menuOpen = false;
        panelWidth = Math.min(400, width - 16);
        left = (width - panelWidth) / 2;
        top = (height - PANEL_HEIGHT) / 2;
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
        options.setTooltip(
                Tooltip.create(Component.literal("Copy code or use the item in your hand.")));
        int inputWidth = panelWidth - 76;
        code =
                new EETextField(
                        font,
                        left + 18,
                        top + 53,
                        inputWidth - 12,
                        18,
                        Component.literal("Item code")) {
                    @Override
                    public void extractWidgetRenderState(
                            GuiGraphicsExtractor g, int mx, int my, float dt) {
                        ((EditBoxAccessor) this).ee$setTextY(top + 52 + (20 - 8) / 2);
                        super.extractWidgetRenderState(g, mx, my, dt);
                    }
                };
        code.setBordered(false);
        code.setTextShadow(false);
        code.setTextColor(EETheme.LIGHT);
        code.setMaxLength(Integer.MAX_VALUE);
        code.setValue(draft);
        code.setHint(Component.literal("minecraft:stone 64").withColor(0xAEBBB5));
        codeHelp =
                Tooltip.create(
                        Component.literal(
                                "Enter an item ID with optional components and amount, or paste a"
                                        + " /give command."));
        code.setTooltip(codeHelp);
        EETextField.validationFrame(code, left + 12, top + 52, inputWidth, 20);
        if (originalCodeValue == null) originalCodeValue = draft;
        code.moveCursorTo(Math.clamp(cursor, 0, draft.length()), false);
        code.setHighlightPos(Math.clamp(anchor, 0, draft.length()));
        code.setResponder(
                value -> {
                    notice = null;
                    refresh();
                    updateSuggestions();
                });
        addRenderableWidget(code);
        itemParser = new ItemCodeParser<>(registryAccess());
        lastCodeValue = null;
        suggestions =
                mc.player == null
                        ? null
                        : new CommandSuggestions(
                                mc, this, code, font, true, false, 0, 5, false, EETheme.INPUT);
        if (suggestions != null) suggestions.setAllowSuggestions(true);
        pasteButton =
                addRenderableWidget(
                        action(
                                left + panelWidth - 60,
                                top + 52,
                                48,
                                "Paste",
                                this::pasteClipboard));
        pasteButton.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Replace the code with the item ID or /give command from your"
                                        + " clipboard.")));
        editButton =
                addRenderableWidget(
                        action(left + panelWidth - 82, top + 98, 64, "Edit item", this::editItem));
        int available = panelWidth - 24;
        giveButton = saveButton = doneButton = null;
        if (setter == null) {
            int w = (available - 8) / 3;
            addRenderableWidget(action(left + 12, top + 164, w, "Close", this::onClose));
            saveButton =
                    addRenderableWidget(
                            action(left + 16 + w, top + 164, w, "Save item", this::saveItem));
            giveButton =
                    addRenderableWidget(
                            action(
                                    left + 20 + w * 2,
                                    top + 164,
                                    available - w * 2 - 8,
                                    "Give item",
                                    this::giveItem));
        } else {
            int w = (available - 4) / 2;
            addRenderableWidget(action(left + 12, top + 164, w, "Cancel", this::onCancel));
            doneButton =
                    addRenderableWidget(
                            action(
                                    left + 16 + w,
                                    top + 164,
                                    available - w - 4,
                                    importing ? "Import item" : "Apply code",
                                    this::applyCode));
        }
        refresh();
        if (focused) setFocused(code);
        updateSuggestions();
    }

    public CommandDispatcher<ClientSuggestionProvider> itemCommandDispatcher() {
        return itemParser.dispatcher(code.getValue());
    }

    private void updateSuggestions() {
        suggestionCursor = code.getCursorPosition();
        if (suggestions != null) suggestions.updateCommandInfo();
    }

    private boolean completionCoversPointer(int x, int y) {
        var list = ((CommandSuggestionsAccessor) suggestions).ee$getSuggestions();
        return list != null
                && ((CommandSuggestionsAccessor.ListBounds) (Object) list)
                        .ee$getRect()
                        .contains(x, y);
    }

    private EEButton action(int x, int y, int w, String label, Runnable run) {
        return new EEButton(x, y, w, 20, Component.literal(label), b -> run.run()) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float dt) {
                EETheme.bevel(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        isHoveredOrFocused() && active ? EETheme.CONTROL_HOVER : EETheme.CONTROL,
                        false);
                g.text(
                        font,
                        getMessage(),
                        getX() + (getWidth() - font.width(getMessage())) / 2,
                        getY() + (getHeight() - font.lineHeight) / 2 + 1,
                        active ? EETheme.TEXT : EETheme.MUTED,
                        false);
            }
        };
    }

    private void refresh() {
        if (code == null) return;
        String value = code.getValue();
        if (!value.equals(lastCodeValue)) {
            lastCodeValue = value;
            var result =
                    itemParser.read(
                            value,
                            mc.player == null
                                    ? null
                                    : mc.player.connection.getSuggestionsProvider());
            itemError = result.error();
            currentItemStack =
                    result.item() == null
                            ? null
                            : ItemUtils.getFromGiveCode(value, registryAccess());
            if (itemError == null && currentItemStack == null)
                itemError = "Enter a valid item ID or /give command.";
        }
        EETextField.validate(code, value.isBlank() ? null : itemError);
        if (giveButton != null)
            validity(giveButton, giveReason(), "Add this item to an empty hotbar slot.");
        if (saveButton != null)
            validity(saveButton, itemError, "Save this creation in Saved items.");
        if (doneButton != null)
            validity(
                    doneButton,
                    itemError,
                    importing
                            ? "Add this creation to Saved items."
                            : "Apply the item code and return.");
        if (editButton != null)
            validity(
                    editButton,
                    editReason(),
                    "Open the item editor. Changes return to this code field.");
    }

    private void validity(EEButton button, String reason, String help) {
        button.active = reason == null;
        button.setTooltip(Tooltip.create(Component.literal(reason == null ? help : reason)));
    }

    private String giveReason() {
        if (itemError != null) return itemError;
        if (mc.player == null || !mc.player.isCreative())
            return "Giving items requires Creative mode.";
        return ItemUtilsClient.canGive(mc)
                ? null
                : "Make room in your hotbar before giving an item.";
    }

    private String editReason() {
        if (itemError != null) return itemError;
        return mc.player != null && mc.player.isCreative()
                ? null
                : "The item editor requires Creative mode.";
    }

    private void giveItem() {
        refresh();
        if (giveReason() != null) return;
        ItemUtilsClient.give(currentItemStack.copy());
        originalCodeValue = code.getValue();
        notice = "Added to your hotbar";
        refresh();
    }

    private void saveItem() {
        refresh();
        if (itemError != null) return;
        EEMod.saveItem(code.getValue());
        mc.gui.setScreen(parent instanceof GuiMenu ? parent : new GuiMenu(parent));
    }

    private void applyCode() {
        refresh();
        if (itemError != null || setter == null) return;
        setter.accept(code.getValue());
        mc.gui.setScreen(parent);
    }

    private void editItem() {
        refresh();
        if (editReason() != null) return;
        mc.gui.setScreen(
                new GuiItemStackModifier(this, currentItemStack.copy(), this::setCurrent)
                        .withSaveValidation(
                                item -> item.isEmpty() ? "Choose a non-empty item." : null));
    }

    private void pasteClipboard() {
        String value = ItemReader.extractItemPart(GuiUtils.getClipboardText());
        if (value == null || value.isEmpty()) {
            notice = "The clipboard is empty";
            return;
        }
        code.setValue(value);
        setFocused(code);
        code.moveCursorToEnd(false);
        refresh();
    }

    @Override
    public boolean isModified() {
        return code != null
                && originalCodeValue != null
                && !code.getValue().equals(originalCodeValue);
    }

    @Override
    public void onCancel() {
        if (!isModified()) {
            mc.gui.setScreen(parent);
            return;
        }
        mc.gui.setScreen(
                new GuiConfirmation(
                        this,
                        Component.literal("Discard the changes to this item code?"),
                        Component.literal("Discard changes"),
                        () -> mc.gui.setScreen(parent),
                        () -> mc.gui.setScreen(this)) {
                    @Override
                    public void onClose() {
                        mc.gui.setScreen(GuiGiver.this);
                    }
                });
    }

    private void setCurrent(ItemStack item) {
        setCurrentItemStack(item);
    }

    public void setCurrentItemStack(ItemStack item) {
        setPreText(item == null ? "" : ItemUtils.getGiveCode(item.copy(), registryAccess()));
    }

    public void setPreText(String value) {
        preText = value == null ? "" : value;
        lastCodeValue = null;
        if (code != null) {
            code.setValue(preText);
            refresh();
        }
    }

    @Override
    public void tick() {
        refresh();
        if (code.getCursorPosition() != suggestionCursor) updateSuggestions();
        super.tick();
    }

    private void openMenu() {
        actions.clear();
        actions.add(
                new MenuAction(
                        "Copy item code",
                        () -> code.getValue().isBlank() ? "Enter an item code first." : null,
                        "Copy the code exactly as entered.",
                        () -> {
                            GuiUtils.addToClipboard(code.getValue());
                            notice = "Item code copied";
                        }));
        actions.add(
                new MenuAction(
                        "Use held item",
                        () ->
                                mc.player == null || mc.player.getMainHandItem().isEmpty()
                                        ? "Hold an item in your main hand first."
                                        : null,
                        "Replace the code with the item in your main hand.",
                        () -> setCurrentItemStack(mc.player.getMainHandItem())));
        if (deleteButton && setter != null)
            actions.add(
                    new MenuAction(
                            "Delete saved item",
                            () -> null,
                            "Remove this saved item after confirmation.",
                            this::deleteItem));
        menuFocus = 0;
    }

    private void deleteItem() {
        mc.gui.setScreen(
                new GuiConfirmation(
                        this,
                        Component.literal("Delete this saved item?"),
                        Component.literal("Delete item"),
                        () -> {
                            setter.accept(null);
                            mc.gui.setScreen(parent);
                        },
                        () -> mc.gui.setScreen(this)) {
                    @Override
                    public void onClose() {
                        mc.gui.setScreen(GuiGiver.this);
                    }
                });
    }

    private int menuX() {
        return left + panelWidth - 174;
    }

    private int menuY() {
        return top + 32;
    }

    private void chooseAction(int index) {
        var action = actions.get(index);
        refresh();
        if (action.blocked.get() != null) return;
        menuOpen = false;
        playClick();
        action.run.run();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (menuOpen) {
            if (event.x() >= menuX() + 1
                    && event.x() < menuX() + 161
                    && event.y() >= menuY() + 1
                    && event.y() < menuY() + 1 + actions.size() * 20) {
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT)
                    chooseAction(((int) event.y() - menuY() - 1) / 20);
            } else menuOpen = false;
            return true;
        }
        if (code.isFocused() && suggestions != null && suggestions.mouseClicked(event)) return true;
        boolean handled = super.mouseClicked(event, twice);
        // The native screen focuses the clicked button after its onPress callback.
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && pasteButton.isMouseOver(event.x(), event.y())) setFocused(code);
        if (!code.isFocused() && suggestions != null) suggestions.hide();
        if (code.isFocused()) updateSuggestions();
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return menuOpen || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        return menuOpen
                || code.isFocused() && suggestions != null && suggestions.mouseScrolled(v)
                || super.mouseScrolled(x, y, h, v);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (menuOpen) {
            if (event.key() == InputConstants.KEY_ESCAPE) menuOpen = false;
            if (event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_DOWN)
                menuFocus =
                        Math.clamp(
                                menuFocus + (event.key() == InputConstants.KEY_DOWN ? 1 : -1),
                                0,
                                actions.size() - 1);
            if (event.key() == InputConstants.KEY_RETURN
                    || event.key() == InputConstants.KEY_NUMPADENTER) chooseAction(menuFocus);
            return true;
        }
        if (code.isFocused() && suggestions != null && suggestions.keyPressed(event)) return true;
        if (code != null
                && code.isFocused()
                && event.hasControlDown()
                && event.shortcutKey() == InputConstants.KEYCODE_V) {
            String value = ItemReader.extractItemPart(GuiUtils.getClipboardText());
            if (value != null && !value.isEmpty()) code.insertText(value);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return menuOpen || super.charTyped(event);
    }

    @Override
    public void onClose() {
        onCancel();
    }

    private String fit(String text, int w) {
        return font.width(text) <= w
                ? text
                : font.plainSubstrByWidth(text, Math.max(0, w - font.width("..."))) + "...";
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractBackground(g, mx, my, dt);
        EETheme.bevel(g, left, top, panelWidth, PANEL_HEIGHT, EETheme.PANEL, false);
        g.text(font, screenTitle(), left + 12, top + 16, EETheme.TEXT, false);
        g.text(font, "Item code", left + 12, top + 40, EETheme.TEXT, false);
        EETheme.bevel(g, left + 12, top + 52, panelWidth - 76, 20, EETheme.INPUT, true);
        if (code.isFocused() && code.getValue().isEmpty())
            g.text(font, "minecraft:stone 64", code.getX(), top + 58, 0xFFAEBBB5, false);
        EETheme.bevel(g, left + 12, top + 82, panelWidth - 24, 52, EETheme.OPTION, true);
        EETheme.bevel(g, left + 18, top + 88, 40, 40, EETheme.INPUT, true);
        if (itemError == null) {
            NativeItemPreviewState.draw(g, currentItemStack, left + 20, top + 90, 36, 36);
            g.text(
                    font,
                    fit(currentItemStack.getHoverName().getString(), panelWidth - 152),
                    left + 66,
                    top + 96,
                    EETheme.TEXT,
                    false);
            g.text(
                    font,
                    "Amount: " + currentItemStack.getCount(),
                    left + 66,
                    top + 112,
                    EETheme.MUTED,
                    false);
        } else {
            g.text(
                    font,
                    code.getValue().isBlank() ? "No item yet" : "Cannot read this item",
                    left + 66,
                    top + 104,
                    EETheme.MUTED,
                    false);
        }
        String status =
                notice != null
                        ? notice
                        : code.getValue().isBlank()
                                ? "Paste an item ID or /give command."
                                : itemError != null
                                        ? "Invalid item code"
                                        : setter == null && giveReason() != null
                                                ? giveReason()
                                                : "";
        g.text(
                font,
                fit(status, panelWidth - 24),
                left + 12,
                top + 144,
                itemError != null && !code.getValue().isBlank()
                        ? EETheme.ERROR_LABEL
                        : EETheme.MUTED,
                false);
        boolean completionOpen = code.isFocused() && suggestions != null && suggestions.isVisible();
        boolean completionHovered = completionOpen && completionCoversPointer(mx, my);
        code.setTooltip(completionOpen ? null : codeHelp);
        super.extractRenderState(
                g,
                menuOpen || completionHovered ? -1 : mx,
                menuOpen || completionHovered ? -1 : my,
                dt);
        if (!menuOpen
                && !completionHovered
                && itemError == null
                && mx >= left + 18
                && mx < left + panelWidth - 88
                && my >= top + 88
                && my < top + 128)
            GuiUtils.setTooltipForNextFrame(g, font, currentItemStack, mx, my);
        if (menuOpen) {
            EETheme.bevel(g, menuX(), menuY(), 162, actions.size() * 20 + 2, EETheme.PANEL, false);
            for (int i = 0; i < actions.size(); i++) {
                var a = actions.get(i);
                int y = menuY() + 1 + i * 20;
                boolean hover = mx >= menuX() + 1 && mx < menuX() + 161 && my >= y && my < y + 20;
                EETheme.rect(
                        g, menuX() + 1, y, 160, 20, hover ? EETheme.OPTION_HOVER : EETheme.OPTION);
                g.text(
                        font,
                        a.label,
                        menuX() + 8,
                        y + 6,
                        a.blocked.get() == null ? EETheme.TEXT : EETheme.MUTED,
                        false);
                if (a.label.equals("Delete saved item"))
                    EETheme.removeButton(g, menuX() + 139, y + 1, 20, 18, hover, true);
                if (i == menuFocus) EETheme.rect(g, menuX() + 2, y + 4, 2, 12, EETheme.ON);
                if (hover)
                    g.setTooltipForNextFrame(
                            font,
                            Component.literal(a.blocked.get() == null ? a.help : a.blocked.get()),
                            mx,
                            my);
            }
        }
        if (!menuOpen && code.isFocused() && suggestions != null)
            suggestions.extractSuggestions(g, mx, my);
    }
}
