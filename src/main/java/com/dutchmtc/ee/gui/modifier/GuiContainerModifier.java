package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EEDropdownButton;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.gui.selector.GuiTypeListSelector;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils.ContainerData;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** A draft inventory with native item movement and contextual editing. */
public class GuiContainerModifier extends AbstractContainerScreen<ContainerEditorMenu> {
    private final Screen parent;
    private final Consumer<ContainerData> setter;
    private final List<Component> slotNames;
    private EEButton add, remove, apply, previousPage, nextPage;
    private EEDropdownButton options;
    private boolean optionsOpen, swallowRelease;
    private int selected = -1, menuFocus;

    public GuiContainerModifier(
            Screen parent, Component title, Consumer<ContainerData> setter, ContainerData data) {
        this(parent, title, setter, data, null);
    }

    public GuiContainerModifier(
            Screen parent,
            Component title,
            Consumer<ContainerData> setter,
            ContainerData data,
            List<Component> slotNames) {
        this(
                parent,
                title,
                setter,
                new ContainerEditorMenu(data, Minecraft.getInstance().player.getInventory()),
                slotNames);
    }

    private GuiContainerModifier(
            Screen parent,
            Component title,
            Consumer<ContainerData> setter,
            ContainerEditorMenu menu,
            List<Component> slotNames) {
        super(
                menu,
                Minecraft.getInstance().player.getInventory(),
                title,
                ContainerEditorMenu.WIDTH,
                menu.height());
        this.parent = parent;
        this.setter = setter;
        this.slotNames = slotNames == null ? null : List.copyOf(slotNames);
    }

    @Override
    protected void init() {
        clearWidgets();
        // Native init installs bundle actions that send packets for the player's real menu.
        // This editor owns local slots and deliberately installs no network actions.
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        optionsOpen = false;
        swallowRelease = false;
        add =
                addRenderableWidget(
                        action(
                                leftPos + imageWidth - 108,
                                topPos + 8,
                                76,
                                "Add item",
                                b -> chooseItem()));
        remove =
                addRenderableWidget(
                        new EEButton(
                                leftPos + imageWidth - 28,
                                topPos + 8,
                                20,
                                20,
                                Component.literal("\u00d7"),
                                b -> removeItem()));
        int pagingY = topPos + ContainerEditorMenu.GRID_TOP + (menu.rows * 18 - 20) / 2;
        previousPage =
                addRenderableWidget(action(leftPos + 12, pagingY, 20, "<", b -> changePage(-1)));
        nextPage =
                addRenderableWidget(
                        action(leftPos + imageWidth - 32, pagingY, 20, ">", b -> changePage(1)));
        int footer = topPos + imageHeight - 28;
        addRenderableWidget(action(leftPos + 12, footer, 72, "Cancel", b -> onClose()));
        options =
                addRenderableWidget(
                        new EEDropdownButton(
                                leftPos + 88,
                                footer,
                                80,
                                20,
                                Component.literal("Options"),
                                b -> {
                                    optionsOpen = !optionsOpen;
                                    menuFocus = 0;
                                }));
        options.setTooltip(
                Tooltip.create(
                        Component.literal("Edit the selected item or clear the container.")));
        apply =
                addRenderableWidget(
                        action(
                                leftPos + 172,
                                footer,
                                72,
                                "Apply",
                                b -> {
                                    if (!menu.getCarried().isEmpty()
                                            || menu.validationReason() != null) return;
                                    setter.accept(menu.result());
                                    minecraft.gui.setScreen(parent);
                                }));
        refreshActions();
    }

    private EEButton action(
            int x,
            int y,
            int w,
            String label,
            net.minecraft.client.gui.components.Button.OnPress action) {
        return new EEButton(x, y, w, 20, Component.literal(label), action) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                EETheme.bevel(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        EETheme.controlColor(false, isHoveredOrFocused(), active),
                        false);
                g.text(
                        font,
                        getMessage(),
                        getX() + (getWidth() - font.width(getMessage())) / 2,
                        getY() + 6,
                        active ? EETheme.TEXT : EETheme.MUTED,
                        false);
            }
        };
    }

    private Slot selectedSlot() {
        return selected >= 0 && selected < menu.slots.size() ? menu.slots.get(selected) : null;
    }

    private ItemStack editableItem() {
        if (!menu.getCarried().isEmpty()) return menu.getCarried();
        Slot slot = selectedSlot();
        return slot == null ? ItemStack.EMPTY : slot.getItem();
    }

    private void refreshActions() {
        boolean holding = !menu.getCarried().isEmpty();
        add.active = !holding;
        add.setTooltip(
                Tooltip.create(
                        Component.literal(
                                holding
                                        ? "Place or remove the held item before choosing another."
                                        : "Choose an item from all items, saved items or your"
                                                + " inventory.")));
        remove.active = !editableItem().isEmpty();
        remove.setTooltip(
                Tooltip.create(
                        Component.literal(
                                remove.active
                                        ? holding
                                                ? "Remove the held item."
                                                : "Remove the selected item."
                                        : "Select an item or pick it up to remove it.")));
        String invalid = menu.validationReason();
        apply.active = !holding && invalid == null;
        apply.setTooltip(
                holding
                        ? Tooltip.create(
                                Component.literal("Place or remove the held item before applying."))
                        : invalid == null ? null : Tooltip.create(Component.literal(invalid)));
        previousPage.visible = nextPage.visible = menu.pageCount() > 1;
        previousPage.active = menu.page() > 0;
        nextPage.active = menu.page() < menu.pageCount() - 1;
        previousPage.setTooltip(
                Tooltip.create(
                        Component.literal(
                                previousPage.active
                                        ? "Previous slots"
                                        : "This is the first page.")));
        nextPage.setTooltip(
                Tooltip.create(
                        Component.literal(
                                nextPage.active ? "Next slots" : "This is the last page.")));
    }

    private void changePage(int direction) {
        int previous = menu.page();
        menu.setPage(menu.page() + direction);
        if (previous != menu.page()) selected = -1;
        isQuickCrafting = false;
        quickCraftSlots.clear();
        hoveredSlot = null;
        refreshActions();
    }

    private void chooseItem() {
        if (!menu.getCarried().isEmpty()) return;
        minecraft.gui.setScreen(
                new GuiTypeListSelector(
                        this,
                        Component.literal("Choose item"),
                        stack -> {
                            menu.setCarried(stack.copy());
                            return this;
                        }));
    }

    private void editItem() {
        ItemStack stack = editableItem();
        if (stack.isEmpty()) return;
        boolean held = !menu.getCarried().isEmpty();
        Slot slot = selectedSlot();
        minecraft.gui.setScreen(
                new GuiItemStackModifier(
                        this,
                        stack.copy(),
                        result -> {
                            if (held) menu.setCarried(result.copy());
                            else slot.set(result.copy());
                        }));
    }

    private void removeItem() {
        if (!menu.getCarried().isEmpty()) menu.setCarried(ItemStack.EMPTY);
        else if (selectedSlot() != null) selectedSlot().set(ItemStack.EMPTY);
        refreshActions();
    }

    private void clearContainer() {
        minecraft.gui.setScreen(
                new GuiConfirmation(
                        this,
                        Component.literal("Clear all container slots?"),
                        Component.literal("Clear container"),
                        () -> {
                            menu.contents.clearContent();
                            minecraft.gui.setScreen(this);
                        },
                        () -> minecraft.gui.setScreen(this)));
    }

    public boolean isModified() {
        return menu.changed();
    }

    @Override
    public void onClose() {
        if (optionsOpen) {
            optionsOpen = false;
            return;
        }
        if (isModified())
            minecraft.gui.setScreen(
                    new GuiConfirmation(
                            this,
                            Component.translatable("gui.ee.discard_changes_question"),
                            () -> minecraft.gui.setScreen(parent),
                            () -> minecraft.gui.setScreen(this)));
        else minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        // Keep the draft and cursor through pickers, editing and confirmations.
        isQuickCrafting = false;
        quickCraftSlots.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean isInGameUi() {
        return false;
    }

    @Override
    protected void slotClicked(Slot slot, int index, int button, ContainerInput input) {
        if (slot != null) {
            index = slot.index;
            selected = index;
        }
        menu.clicked(index, button, input, minecraft.player);
        refreshActions();
    }

    @Override
    protected void handleSlotStateChanged(int slot, int containerId, boolean state) {
        // No server-side slot state belongs to this local editor.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        refreshActions();
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0x90101010, 0xB0101010);
        EETheme.bevel(g, leftPos, topPos, imageWidth, imageHeight, EETheme.PANEL, false);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            int x = leftPos + slot.x, y = topPos + slot.y;
            boolean isSelected = slot.index == selected;
            EETheme.bevel(
                    g, x - 1, y - 1, 18, 18, isSelected ? EETheme.CONTROL : EETheme.INPUT, true);
            if (isSelected) g.outline(x - 1, y - 1, 18, 18, EETheme.THUMB_HOVER);
        }
        super.extractRenderState(g, optionsOpen ? -1 : mx, optionsOpen ? -1 : my, delta);
        if (optionsOpen) renderOptions(g, mx, my);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        String name = title.getString();
        if (font.width(name) > imageWidth - 132)
            name = font.plainSubstrByWidth(name, imageWidth - 144) + "...";
        g.text(font, name, 12, 14, EETheme.TEXT, false);
        g.text(
                font,
                "Inventory",
                (imageWidth - 162) / 2,
                menu.inventoryTop - 12,
                EETheme.TEXT,
                false);
        if (menu.original.bundle()) {
            String fullness = menu.fullness();
            g.text(
                    font,
                    fullness,
                    (imageWidth + 162) / 2 - font.width(fullness),
                    menu.inventoryTop - 12,
                    menu.validationReason() == null ? EETheme.TEXT : EETheme.ERROR_LABEL,
                    false);
        }
        if (menu.pageCount() > 1) {
            String page = (menu.page() + 1) + "/" + menu.pageCount();
            g.text(
                    font,
                    page,
                    22 - font.width(page) / 2,
                    ContainerEditorMenu.GRID_TOP + (menu.rows * 18 - 20) / 2 + 24,
                    EETheme.TEXT,
                    false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mx, int my) {
        if (optionsOpen) return;
        super.extractTooltip(g, mx, my);
        if (hoveredSlot != null && menu.placementReason(hoveredSlot) != null)
            g.setTooltipForNextFrame(
                    font, Component.literal(menu.placementReason(hoveredSlot)), mx, my);
        if (mx >= leftPos + 12 && mx < add.getX() - 4 && my >= topPos + 8 && my < topPos + 28)
            g.setTooltipForNextFrame(font, title, mx, my);
        if (isHovering((imageWidth - 162) / 2, menu.inventoryTop - 14, 162, 12, mx, my))
            g.setTooltipForNextFrame(
                    font,
                    Component.literal("Copies of your items. Your inventory stays unchanged."),
                    mx,
                    my);
        if (hoveredSlot != null
                && !hoveredSlot.hasItem()
                && slotNames != null
                && hoveredSlot.index < slotNames.size())
            g.setTooltipForNextFrame(font, slotNames.get(hoveredSlot.index), mx, my);
    }

    private int optionsX() {
        return leftPos + 88;
    }

    private int optionsY() {
        return options.getY() - 44;
    }

    private String optionReason(int row) {
        if (row == 0)
            return editableItem().isEmpty() ? "Select an item or pick it up to edit it." : null;
        return menu.contents.isEmpty() ? "The container is already empty." : null;
    }

    private void chooseOption(int row) {
        if (optionReason(row) != null) return;
        optionsOpen = false;
        if (row == 0) editItem();
        else clearContainer();
    }

    private void renderOptions(GuiGraphicsExtractor g, int mx, int my) {
        int x = optionsX(), y = optionsY();
        EETheme.bevel(g, x - 1, y - 2, 154, 44, EETheme.OPTION, false);
        for (int row = 0; row < 2; row++) {
            boolean hover =
                    mx >= x && mx < x + 152 && my >= y + row * 20 && my < y + (row + 1) * 20;
            String
                    label =
                            row == 0
                                    ? menu.getCarried().isEmpty() ? "Edit item" : "Edit held item"
                                    : "Clear container",
                    reason = optionReason(row);
            EEDropdownButton.renderOption(
                    g,
                    font,
                    Component.literal(label),
                    ItemStack.EMPTY,
                    x,
                    y + row * 20,
                    152,
                    20,
                    menuFocus == row,
                    hover);
            if (reason != null) g.text(font, label, x + 8, y + row * 20 + 6, EETheme.MUTED, false);
            if (hover)
                g.setTooltipForNextFrame(
                        font,
                        Component.literal(
                                reason != null
                                        ? reason
                                        : row == 0
                                                ? "Edit the selected or held item's name, amount"
                                                        + " and components."
                                                : "Remove every item from the container."),
                        mx,
                        my);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (optionsOpen) {
            swallowRelease = true;
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && event.x() >= optionsX()
                    && event.x() < optionsX() + 152
                    && event.y() >= optionsY()
                    && event.y() < optionsY() + 40)
                chooseOption((int) (event.y() - optionsY()) / 20);
            else optionsOpen = false;
            return true;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        refreshActions();
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (optionsOpen) return true;
        boolean handled = super.mouseDragged(event, dx, dy);
        refreshActions();
        return handled;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (optionsOpen || swallowRelease) {
            swallowRelease = false;
            return true;
        }
        boolean handled = super.mouseReleased(event);
        refreshActions();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (optionsOpen) return true;
        if (menu.pageCount() > 1
                && vertical != 0
                && !isQuickCrafting
                && x >= leftPos
                && x < leftPos + imageWidth
                && y >= topPos + ContainerEditorMenu.GRID_TOP
                && y < topPos + menu.inventoryTop - 14) {
            changePage(vertical < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (optionsOpen) {
            if (event.key() == InputConstants.KEY_ESCAPE) optionsOpen = false;
            else if (event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_DOWN)
                menuFocus = 1 - menuFocus;
            else if (event.key() == InputConstants.KEY_RETURN) chooseOption(menuFocus);
            return true;
        }
        boolean handled = super.keyPressed(event);
        refreshActions();
        return handled;
    }
}
