package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.CommandBlockEditorData;
import com.dutchmtc.ee.utils.GuiUtils;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Native command input with a persistent item draft and compact block-specific controls. */
public final class GuiCommandBlockModifier extends GuiModifier<ItemStack> {
    private static final Item[] TYPES = {
        Items.COMMAND_BLOCK,
        Items.REPEATING_COMMAND_BLOCK,
        Items.CHAIN_COMMAND_BLOCK,
        Items.COMMAND_BLOCK_MINECART
    };
    private static final String[] TYPE_LABELS = {"Impulse", "Repeating", "Chain", "Minecart"};
    private static final String[] FACINGS = {"", "down", "up", "north", "south", "west", "east"};
    private static final String[] FACING_LABELS = {
        "On placement", "Down", "Up", "North", "South", "West", "East"
    };
    private final CommandBlockEditorData draft;
    private EETextField command;
    private StyledTextField name;
    private TextSelectionToolbar toolbar;
    private CommandSuggestions suggestions;
    private EEButton apply, activation, condition, output;
    private EEDropdownButton type, facing;
    private int left, top, panelWidth, panelHeight, inputX, inputWidth, commandY, nameY;
    private AbstractWidget menu;
    private String[] menuLabels;
    private Item[] menuIcons;
    private IntConsumer menuChoice;
    private int menuOffset, menuFocus;
    private final EEDropdownScrollbar menuScrollbar = new EEDropdownScrollbar();
    private final Tooltip commandHelp =
            Tooltip.create(
                    Component.literal(
                            "The command to execute. Tab completes commands using the server's"
                                    + " available suggestions."));

    public GuiCommandBlockModifier(Screen parent, Consumer<ItemStack> setter, ItemStack stack) {
        super(parent, Component.literal("Command block editor"), setter);
        draft =
                new CommandBlockEditorData(
                        stack,
                        mc.level == null
                                ? net.minecraft.data.registries.VanillaRegistries
                                        .createWorldLookup()
                                : mc.level.registryAccess());
    }

    @Override
    protected void repositionElements() {
        init();
    }

    @Override
    public void init() {
        int cursor = command == null ? draft.command.length() : command.getCursorPosition();
        int anchor = command == null ? cursor : ((EditBoxAccessor) command).ee$getHighlightPos();
        int display = command == null ? 0 : ((EditBoxAccessor) command).ee$getDisplayPos();
        boolean commandFocused = command != null && command.isFocused();
        int nameCursor = name == null ? draft.name.inputText().length() : name.getCursorPosition();
        int nameAnchor =
                name == null
                        ? nameCursor
                        : ((EditBoxAccessor) (net.minecraft.client.gui.components.EditBox) name)
                                .ee$getHighlightPos();
        boolean nameFocused = name != null && name.isFocused();
        suggestions = null;
        clearWidgets();
        clearFocus();
        menu = null;
        menuScrollbar.reset();
        panelWidth = Math.min(410, width - 16);
        panelHeight = 214;
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        inputX = left + 12;
        inputWidth = panelWidth - 24;
        commandY = top + 58;
        nameY = top + 99;
        int typeWidth = Math.min(112, inputWidth / 2);
        type =
                addRenderableWidget(
                        new EEDropdownButton(
                                left + panelWidth - 12 - typeWidth,
                                top + 12,
                                typeWidth,
                                20,
                                Component.literal(TYPE_LABELS[typeIndex()]),
                                b ->
                                        openMenu(
                                                type,
                                                TYPE_LABELS,
                                                TYPES,
                                                typeIndex(),
                                                i -> {
                                                    draft.type = TYPES[i];
                                                    init();
                                                })));
        type.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Choose the command block type. Your command and edits are"
                                        + " kept.")));
        command =
                new EETextField(
                        font,
                        inputX + 6,
                        commandY + 1,
                        inputWidth - 12,
                        18,
                        Component.literal("Command")) {
                    @Override
                    public void extractWidgetRenderState(
                            GuiGraphicsExtractor g, int mx, int my, float delta) {
                        // Native caret/selection updates reset borderless inputs to top alignment.
                        ((EditBoxAccessor) this)
                                .ee$setTextY(getY() + (getHeight() - font.lineHeight) / 2);
                        super.extractWidgetRenderState(g, mx, my, delta);
                    }
                };
        command.setBordered(false);
        command.setTextShadow(false);
        command.setTextColor(EETheme.LIGHT);
        command.setMaxLength(
                Math.max(CommandBlockEditorData.COMMAND_LIMIT, draft.command.length()));
        command.setValue(draft.command);
        command.setResponder(
                value -> {
                    draft.command = value;
                    refresh();
                    if (suggestions != null) suggestions.updateCommandInfo();
                });
        command.setTooltip(commandHelp);
        addRenderableWidget(command);
        name =
                new StyledTextField(
                        font,
                        inputX + 6,
                        nameY + 1,
                        inputWidth - 12,
                        18,
                        Component.literal("Custom name"),
                        draft.name,
                        Math.max(32767, draft.name.text().length()));
        ((EditBoxAccessor) (net.minecraft.client.gui.components.EditBox) name)
                .ee$setTextY(name.getY() + 5);
        name.setHint(Component.literal("Default: @").withColor(0xAEBBB5));
        name.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "The name shown when this command sends a message. Select text to"
                                        + " format it.")));
        addRenderableWidget(name);
        toolbar = name.createToolbar(this, inputX, inputX + inputWidth);
        name.moveCursorTo(Math.min(nameCursor, name.getValue().length()), false);
        name.setHighlightPos(Math.min(nameAnchor, name.getValue().length()));
        int half = (inputWidth - 6) / 2;
        if (!draft.minecart()) {
            activation =
                    addRenderableWidget(
                            toggle(
                                    inputX,
                                    top + 136,
                                    half,
                                    () -> {
                                        draft.alwaysActive = !draft.alwaysActive;
                                        refresh();
                                    }));
            condition =
                    addRenderableWidget(
                            toggle(
                                    inputX + half + 6,
                                    top + 136,
                                    inputWidth - half - 6,
                                    () -> {
                                        draft.conditional = !draft.conditional;
                                        refresh();
                                    }));
            facing =
                    addRenderableWidget(
                            new EEDropdownButton(
                                    inputX + half + 6,
                                    top + 160,
                                    inputWidth - half - 6,
                                    20,
                                    Component.literal(facingLabel()),
                                    b ->
                                            openMenu(
                                                    facing,
                                                    FACING_LABELS,
                                                    null,
                                                    facingIndex(),
                                                    i -> {
                                                        draft.facing = FACINGS[i];
                                                        refresh();
                                                    })));
            facing.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "The direction this block faces. On placement uses Minecraft's"
                                            + " normal placement direction.")));
        } else {
            activation = condition = null;
            facing = null;
        }
        output =
                addRenderableWidget(
                        toggle(
                                inputX,
                                top + 160,
                                half,
                                () -> {
                                    draft.trackOutput = !draft.trackOutput;
                                    refresh();
                                }));
        int footerY = top + panelHeight - 27;
        addRenderableWidget(
                new EEButton(
                        inputX, footerY, half, 20, Component.literal("Cancel"), b -> onCancel()));
        apply =
                addRenderableWidget(
                        new EEButton(
                                inputX + half + 6,
                                footerY,
                                inputWidth - half - 6,
                                20,
                                Component.literal("Apply"),
                                b -> applyChanges()));
        suggestions =
                new CommandSuggestions(
                        minecraft, this, command, font, true, true, 0, 5, false, EETheme.INPUT);
        suggestions.setAllowSuggestions(true);
        suggestions.updateCommandInfo();
        command.moveCursorTo(Math.min(cursor, draft.command.length()), false);
        command.setHighlightPos(Math.min(anchor, draft.command.length()));
        ((EditBoxAccessor) command).ee$setDisplayPos(Math.min(display, draft.command.length()));
        if (commandFocused) setFocused(command);
        else if (nameFocused) setFocused(name);
        refresh();
    }

    @Override
    protected void setInitialFocus() {}

    private EEButton toggle(int x, int y, int w, Runnable action) {
        return new EEButton(x, y, w, 20, Component.empty(), b -> action.run()) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                boolean on =
                        this == output
                                ? draft.trackOutput
                                : this == activation ? draft.alwaysActive : draft.conditional;
                EETheme.bevel(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        EETheme.controlColor(on, isHoveredOrFocused(), active),
                        false);
                g.text(
                        font,
                        getMessage(),
                        getX() + (getWidth() - font.width(getMessage())) / 2,
                        getY() + 6,
                        on ? EETheme.LIGHT : EETheme.TEXT,
                        false);
            }
        };
    }

    private void refresh() {
        if (activation != null) {
            activation.setMessage(
                    Component.literal(draft.alwaysActive ? "Always active" : "Needs redstone"));
            activation.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "Always active runs without redstone. Impulse blocks run once;"
                                        + " repeating blocks run each tick; chain blocks run when"
                                        + " triggered by the preceding block.")));
            condition.setMessage(
                    Component.literal(draft.conditional ? "Conditional" : "Unconditional"));
            condition.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "Conditional runs only if the command block behind this one"
                                            + " succeeded.")));
        }
        if (output != null) {
            output.setMessage(
                    Component.literal("Track output: " + (draft.trackOutput ? "On" : "Off")));
            output.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "Keep the last command result in Previous output. Turning this"
                                            + " off clears the saved output.")));
        }
        if (facing != null) facing.setMessage(Component.literal(facingLabel()));
        String error =
                draft.command.length() > CommandBlockEditorData.COMMAND_LIMIT
                        ? "Commands cannot exceed 32,500 characters."
                        : null;
        if (command != null) EETextField.validate(command, error);
        if (name != null && !name.validInput()) error = name.invalidReason();
        if (apply != null) {
            apply.active = error == null;
            apply.setTooltip(error == null ? null : Tooltip.create(Component.literal(error)));
        }
    }

    private void applyChanges() {
        refresh();
        if (!apply.active) return;
        set(draft.result());
        minecraft.gui.setScreen(parent);
    }

    private int typeIndex() {
        for (int i = 0; i < TYPES.length; i++) if (draft.type == TYPES[i]) return i;
        return 0;
    }

    private int facingIndex() {
        for (int i = 0; i < FACINGS.length; i++) if (FACINGS[i].equals(draft.facing)) return i;
        return 0;
    }

    private String facingLabel() {
        return "Facing: " + (facingIndex() == 0 ? "Placement" : FACING_LABELS[facingIndex()]);
    }

    @Override
    public boolean isModified() {
        return draft.modified();
    }

    @Override
    public void onClose() {
        onCancel();
    }

    private void openMenu(
            AbstractWidget anchor,
            String[] labels,
            Item[] icons,
            int selected,
            IntConsumer choose) {
        suggestions.hide();
        clearFocus();
        menu = anchor;
        menuLabels = labels;
        menuIcons = icons;
        menuChoice = choose;
        menuFocus = selected;
        menuOffset = Math.clamp(selected - menuRows() + 1, 0, labels.length - menuRows());
    }

    private int menuRows() {
        return Math.min(menuLabels.length, Math.max(1, Math.min(7, (panelHeight - 48) / 20)));
    }

    private int menuWidth() {
        return Math.min(inputWidth, Math.max(140, menu.getWidth()));
    }

    private int menuX() {
        return Math.min(menu.getX(), inputX + inputWidth - menuWidth());
    }

    private int menuY() {
        return Math.max(
                top + 38, Math.min(menu.getBottom() + 2, top + panelHeight - 33 - menuRows() * 20));
    }

    private void layoutScrollbar() {
        menuScrollbar.layout(
                menuX() + menuWidth() - EEDropdownScrollbar.WIDTH,
                menuY() + 2,
                menuRows() * 20,
                menuRows(),
                menuLabels.length,
                menuOffset,
                1);
    }

    private void scrollMenu(int offset) {
        menuOffset = Math.clamp(offset, 0, menuLabels.length - menuRows());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (menu != null) {
            layoutScrollbar();
            if (menuScrollbar.mouseClicked(e, this::scrollMenu)) return true;
            if (e.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && GuiUtils.isHover(
                            menuX(),
                            menuY() + 2,
                            menuWidth(),
                            menuRows() * 20,
                            (int) e.x(),
                            (int) e.y())) {
                var choose = menuChoice;
                int index = menuOffset + (int) (e.y() - menuY() - 2) / 20;
                menu = null;
                menuScrollbar.reset();
                choose.accept(index);
            } else menu = null;
            return true;
        }
        if (toolbar.click(e)) return true;
        if (command.isFocused() && suggestions.mouseClicked(e)) return true;
        boolean result = super.mouseClicked(e, twice);
        if (!command.isFocused()) suggestions.hide();
        toolbar.update();
        return result;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (menu != null) {
            layoutScrollbar();
            return menuScrollbar.mouseDragged(e, this::scrollMenu);
        }
        boolean result = super.mouseDragged(e, dx, dy);
        toolbar.update();
        return result;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (menuScrollbar.mouseReleased()) return true;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (menu != null) {
            scrollMenu(menuOffset - (int) Math.signum(vertical));
            return true;
        }
        return command.isFocused() && suggestions.mouseScrolled(vertical)
                || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (menu != null) {
            if (e.key() == InputConstants.KEY_ESCAPE) menu = null;
            else if (e.key() == InputConstants.KEY_UP || e.key() == InputConstants.KEY_DOWN) {
                menuFocus =
                        Math.clamp(
                                menuFocus + (e.key() == InputConstants.KEY_UP ? -1 : 1),
                                0,
                                menuLabels.length - 1);
                scrollMenu(Math.clamp(menuOffset, menuFocus - menuRows() + 1, menuFocus));
            } else if (e.key() == InputConstants.KEY_RETURN
                    || e.key() == InputConstants.KEY_SPACE) {
                var choose = menuChoice;
                int index = menuFocus;
                menu = null;
                choose.accept(index);
            }
            return true;
        }
        if (command.isFocused() && suggestions.keyPressed(e)) return true;
        return super.keyPressed(e);
    }

    @Override
    public void tick() {
        super.tick();
        toolbar.update();
        refresh();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        EETheme.bevel(g, inputX, top + 10, 28, 28, EETheme.INPUT, true);
        GuiUtils.drawItemStack(g, new ItemStack(draft.type), inputX + 6, top + 16);
        g.text(
                font,
                draft.minecart() ? "Command minecart" : "Command block",
                inputX + 36,
                top + 18,
                EETheme.TEXT,
                false);
        g.text(font, "Command", inputX, commandY - 12, EETheme.TEXT, false);
        g.text(font, "Custom name (optional)", inputX, nameY - 12, EETheme.TEXT, false);
        EETheme.bevel(g, inputX, commandY, inputWidth, 20, EETheme.INPUT, true);
        EETheme.bevel(g, inputX, nameY, inputWidth, 20, EETheme.INPUT, true);
        EETextField.validationFrame(command, inputX, commandY, inputWidth, 20);
        EETextField.validationFrame(name, inputX, nameY, inputWidth, 20);
        if (draft.minecart()) {
            var lines =
                    font.split(
                            Component.literal("Activated by a powered activator rail."),
                            inputWidth);
            for (int i = 0; i < lines.size(); i++)
                g.text(font, lines.get(i), inputX, top + 132 + i * 10, EETheme.MUTED, false);
        }
        boolean completionOpen = command.isFocused() && suggestions.isVisible();
        command.setTooltip(completionOpen ? null : commandHelp);
        boolean overlayOpen = menu != null || completionOpen;
        super.extractRenderState(g, overlayOpen ? -1 : mx, overlayOpen ? -1 : my, delta);
        if (menu == null) {
            toolbar.render(g, mx, my);
            if (command.isFocused()) suggestions.extractSuggestions(g, mx, my);
        } else {
            layoutScrollbar();
            EETheme.bevel(
                    g,
                    menuX() - 1,
                    menuY(),
                    menuWidth() + 2,
                    menuRows() * 20 + 4,
                    EETheme.OPTION,
                    false);
            int rowWidth = menuWidth() - (menuScrollbar.needed() ? EEDropdownScrollbar.WIDTH : 0);
            for (int i = 0; i < menuRows(); i++) {
                int index = menuOffset + i, y = menuY() + 2 + i * 20;
                EEDropdownButton.renderOption(
                        g,
                        font,
                        Component.literal(menuLabels[index]),
                        menuIcons == null ? ItemStack.EMPTY : new ItemStack(menuIcons[index]),
                        menuX(),
                        y,
                        rowWidth,
                        20,
                        index == menuFocus,
                        GuiUtils.isHover(menuX(), y, rowWidth, 20, mx, my));
            }
            menuScrollbar.render(g, mx, my);
        }
    }
}
