package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EEEquipmentSlotButton;
import com.dutchmtc.ee.gui.components.EEDropdownButton;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.gui.components.StyledTextField;
import com.dutchmtc.ee.gui.modifier.GuiItemStackModifier;
import com.dutchmtc.ee.gui.modifier.StyledTextDocument;
import com.dutchmtc.ee.gui.modifier.TextSelectionToolbar;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.ArmorStandEditorUtils;
import com.dutchmtc.ee.utils.GuiEntityPreviewUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import com.dutchmtc.ee.gui.components.EETextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Rotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One editor for both armor stand items and existing armor stands; persistence is supplied by the host. */
abstract class GuiArmorStandEditorScreen extends Screen {
    enum Tab { POSE("Pose"), EQUIPMENT("Equipment"), LOCKS("Locks"), SETTINGS("Settings");
        final String label;
        Tab(String label) { this.label = label; }
    }
    enum Part { HEAD("Head"), BODY("Body"), LEFT_ARM("Left arm"), RIGHT_ARM("Right arm"),
        LEFT_LEG("Left leg"), RIGHT_LEG("Right leg");
        final String label;
        Part(String label) { this.label = label; }
    }
    private enum Menu { PART, RESET }
    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };
    private static final String[] SLOT_NAMES = { "Head", "Chest", "Legs", "Feet", "Main hand", "Off hand" };
    private static final ItemStack[] SLOT_ICONS = { new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.LEATHER_CHESTPLATE),
            new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD) };
    private static final int TEXT = 0xFF303830, MUTED = 0xFF596359;
    protected final Screen parent;
    protected ArmorStand preview;
    protected CompoundTag originalData, currentData;
    private CompoundTag initialSnapshot;
    private Tab tab = Tab.POSE;
    private Part part = Part.HEAD;
    private int lockSlot;
    private int left, top, panelWidth, panelHeight, previewX, previewY, previewWidth, previewHeight;
    private int contentX, contentWidth, viewTop, viewBottom, contentHeight, scroll;
    private float yaw = -25, pitch = -8, zoom = 1;
    private boolean draggingPreview, draggingScrollbar;
    private final List<Placed> controls = new ArrayList<>();
    private final List<Decoration> decorations = new ArrayList<>();
    private final List<EditBox> angleFields = new ArrayList<>();
    private EEButton apply, allSlots, partSelector, resetMenu;
    private Menu openMenu;
    private final List<EEButton> menuOptions = new ArrayList<>();
    private int menuX, menuY, menuWidth, menuHeight;
    private StyledTextField nameField;
    private TextSelectionToolbar nameToolbar;
    private StyledTextDocument nameDocument;

    protected GuiArmorStandEditorScreen(Screen parent) {
        super(Component.literal("Armor stand editor"));
        this.parent = parent;
    }

    protected abstract boolean initializeData();
    protected abstract void loadData(CompoundTag data);
    protected abstract CompoundTag captureData();
    protected abstract void saveData();
    protected boolean canGetItem() { return false; }
    protected void getItem() {}
    @Override public boolean isPauseScreen() { return true; }
    @Override protected void setInitialFocus() {}

    @Override public void resize(int width, int height) {
        List<String> draft = angleFields.stream().map(EditBox::getValue).toList();
        super.resize(width, height);
        for (int i = 0; i < Math.min(draft.size(), angleFields.size()); i++)
            angleFields.get(i).setValue(draft.get(i));
    }

    @Override protected void init() {
        if (minecraft == null || minecraft.level == null || !initializeData()) {
            if (minecraft != null) minecraft.gui.setScreen(parent);
            return;
        }
        if (initialSnapshot == null) initialSnapshot = captureData();
        panelWidth = Math.min(500, width - 16);
        panelHeight = Math.min(280, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        previewWidth = panelWidth >= 440 ? 130 : panelWidth >= 350 ? 100 : 76;
        previewX = left + 10;
        viewTop = top + 61;
        viewBottom = top + panelHeight - 42;
        previewY = viewTop;
        previewHeight = viewBottom - previewY;
        contentX = previewX + previewWidth + 10;
        contentWidth = left + panelWidth - contentX - 19;
        draggingPreview = draggingScrollbar = false;
        rebuildUi();
    }

    private record Placed(AbstractWidget widget, int x, int y) {}
    private record Decoration(int y, Consumer<GuiGraphicsExtractor> draw) {}
    private <T extends AbstractWidget> T place(T widget, int x, int y) {
        controls.add(new Placed(widget, x, y));
        addWidget(widget);
        return widget;
    }
    private void decorate(int y, Consumer<GuiGraphicsExtractor> draw) { decorations.add(new Decoration(y, draw)); }
    private int screenY(int y) { return viewTop + y - scroll; }

    private void rebuildUi() {
        clearFocus();
        clearWidgets();
        menuOptions.clear(); openMenu = null; partSelector = null;
        controls.clear(); decorations.clear(); angleFields.clear(); nameField = null; nameToolbar = null; allSlots = null;
        int tabWidth = (panelWidth - 26) / 4;
        for (Tab candidate : Tab.values()) {
            EEButton button = addRenderableWidget(new EEButton(left + 10 + candidate.ordinal() * (tabWidth + 2), top + 32,
                    tabWidth, 20, Component.literal(candidate.label), b -> {
                tab = candidate; scroll = 0; rebuildUi();
            }));
            button.active = tab != candidate;
        }
        contentHeight = switch (tab) {
            case POSE -> buildPose();
            case EQUIPMENT -> buildEquipment();
            case LOCKS -> buildLocks();
            case SETTINGS -> buildSettings();
        };
        resetMenu = addRenderableWidget(new EEButton(left + panelWidth - 74, top + 10, 64, 18,
                Component.literal("Reset..."), b -> toggleMenu(Menu.RESET), tip("Reset the pose, preview, or all changes.")));
        int bottom = top + panelHeight - 27;
        int small = canGetItem() ? 3 : 2;
        int unit = (panelWidth - 20 - (small - 1) * 4) / small;
        int x = left + 10;
        if (canGetItem()) {
            addRenderableWidget(new EEButton(x, bottom, unit, 20, Component.literal("Get as item"), b -> getItem(),
                    tip("Get an armor stand item with the changes shown here.")));
            x += unit + 4;
        }
        addRenderableWidget(new EEButton(x, bottom, unit, 20, Component.literal("Cancel"), b -> cancel()));
        x += unit + 4;
        apply = addRenderableWidget(new EEButton(x, bottom, left + panelWidth - 10 - x, 20,
                Component.literal("Apply"), b -> applyChanges()));
        layoutControls(); refreshValidity();
    }

    private int buildPose() {
        decorate(0, g -> g.text(font, "Body part", contentX + 1, screenY(0) + 6, TEXT, false));
        partSelector = place(new EEDropdownButton(0, 0, contentWidth - 66, 20, Component.literal(part.label),
                b -> toggleMenu(Menu.PART)), contentX + 66, 0);
        String[] labels = { "Pitch (X)", "Turn (Y)", "Tilt (Z)" };
        String[] help = { "Bend forward or backward.", "Turn left or right.", "Lean from side to side." };
        Rotations rotation = rotation(part);
        float[] values = { Mth.wrapDegrees(rotation.x()), Mth.wrapDegrees(rotation.y()), Mth.wrapDegrees(rotation.z()) };
        int spacing = Math.max(30, Math.min(44, (viewBottom - viewTop - 32) / 3));
        for (int axis = 0; axis < 3; axis++) {
            int index = axis, y = 28 + axis * spacing;
            decorate(y, g -> g.text(font, labels[index], contentX + 1, screenY(y), TEXT, false));
            EditBox field = new EETextField(font, 0, 0, 48, 18, Component.literal(labels[index] + " in degrees"));
            field.setBordered(false); field.setTextShadow(false); field.setTextColor(0xFFF1F3EF);
            field.setMaxLength(12); field.setValue(degrees(values[index]));
            field.setTooltip(tip("Enter an angle from -180 to 180 degrees."));
            angleFields.add(field);
            DegreeSlider slider = place(new DegreeSlider(contentWidth - 62, values[index], value -> {
                field.setValue(degrees(value));
            }), contentX, y + 12);
            slider.setTooltip(tip(help[index]));
            place(field, contentX + contentWidth - 52, y + 12);
            decorate(y + 12, g -> {
                EETextField.validationFrame(field, field.getX() - 4, field.getY(), field.getWidth() + 8, 18);
                inputFrame(g, field.getX() - 4, field.getY(), field.getWidth() + 8, 18);
            });
            field.setResponder(value -> {
                Float angle = parseAngle(value);
                EETextField.validate(field, angle == null ? "Enter a finite angle from -180 to 180 degrees." : null);
                if (angle != null) { slider.setDegrees(angle); setRotationAxis(index, angle); }
                refreshValidity();
            });
        }
        return 60 + 2 * spacing;
    }

    private void toggleMenu(Menu menu) {
        if (openMenu == menu) { closeMenu(); return; }
        closeMenu(); clearFocus(); openMenu = menu;
        EEButton anchor = menu == Menu.PART ? partSelector : resetMenu;
        menuWidth = menu == Menu.PART ? anchor.getWidth() : 166;
        int count = menu == Menu.PART ? Part.values().length : tab == Tab.POSE ? 4 : 3;
        menuHeight = count * 18 + 4;
        menuX = menu == Menu.PART ? anchor.getX() : anchor.getRight() - menuWidth;
        menuY = Math.min(anchor.getBottom() + 2, top + panelHeight - 29 - menuHeight);
        if (menu == Menu.PART) {
            for (Part candidate : Part.values()) addMenuOption(candidate.label, () -> {
                part = candidate; rebuildUi();
            }, candidate == part);
            setFocused(menuOptions.get(part.ordinal()));
        } else {
            if (tab == Tab.POSE) addMenuOption("Reset part", () -> {
                setRotation(part, defaultRotation(part)); changed(); rebuildUi();
            }, false);
            addMenuOption("Reset pose", () -> {
                for (Part candidate : Part.values()) setRotation(candidate, defaultRotation(candidate));
                changed(); rebuildUi();
            }, false);
            addMenuOption("Reset view", () -> { yaw = -25; pitch = -8; zoom = 1; }, false);
            addMenuOption("Reset all", () -> minecraft.gui.setScreen(new GuiConfirmation(this,
                    Component.literal("Reset all armor stand changes?"), Component.literal("Reset all"), () -> {
                currentData = originalData.copy(); loadData(currentData); nameDocument = null; scroll = 0; minecraft.gui.setScreen(this);
            }, () -> minecraft.gui.setScreen(this))), false);
            setFocused(menuOptions.getFirst());
        }
    }

    private void addMenuOption(String label, Runnable action, boolean selected) {
        EEButton option = new EEButton(menuX + 2, menuY + 2 + menuOptions.size() * 18, menuWidth - 4, 18,
                Component.literal(label), b -> { closeMenu(); action.run(); }) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                EEDropdownButton.renderOption(g, font, getMessage(), ItemStack.EMPTY, getX(), getY(), getWidth(), getHeight(),
                        selected, isHoveredOrFocused());
            }
        };
        menuOptions.add(option); addWidget(option);
    }

    private void closeMenu() {
        for (EEButton option : menuOptions) { option.visible = false; removeWidget(option); }
        if (!menuOptions.isEmpty()) clearFocus();
        menuOptions.clear(); openMenu = null;
    }

    private int buildEquipment() {
        for (int index = 0; index < SLOTS.length; index++) {
            int slot = index, y = index * 29;
            decorate(y, g -> {
                card(g, y, 27);
                ItemStack stack = preview.getItemBySlot(SLOTS[slot]);
                g.text(font, SLOT_NAMES[slot], contentX + 31, screenY(y) + (stack.isEmpty() ? 9 : 3), TEXT, false);
                if (!stack.isEmpty()) g.text(font, fit(stack.getHoverName().getString(), contentWidth - 89), contentX + 31,
                        screenY(y) + 15, MUTED, false);
            });
            place(new EEEquipmentSlotButton(0, 0, 22, 22, Component.literal("Edit " + SLOT_NAMES[slot]),
                    b -> editEquipment(slot), tip("Choose an item for the " + SLOT_NAMES[slot].toLowerCase(Locale.ROOT) + "."),
                    () -> preview.getItemBySlot(SLOTS[slot])), contentX + 3, y + 2);
            place(new EEButton(0, 0, 24, 20, Component.literal("×"), b -> {
                preview.setItemSlot(SLOTS[slot], ItemStack.EMPTY); changed();
            }, tip("Remove the item from " + SLOT_NAMES[slot].toLowerCase(Locale.ROOT) + ".")) {
                @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    active = !preview.getItemBySlot(SLOTS[slot]).isEmpty();
                    super.extractContents(g, mx, my, delta);
                }
            }, contentX + contentWidth - 27, y + 3);
        }
        if (!preview.showArms()) {
            place(new EEButton(0, 0, contentWidth, 20, Component.literal("Show arms"), b -> {
                preview.setShowArms(true); changed(); rebuildUi();
            }, tip("Show the stand's arms so its held items are visible.")), contentX, 180);
            return 202;
        }
        return 174;
    }

    private void editEquipment(int index) {
        minecraft.gui.setScreen(new GuiItemStackModifier(this, preview.getItemBySlot(SLOTS[index]).copy(), item -> {
            ItemStack value = item == null ? ItemStack.EMPTY : item.copy();
            if (!value.isEmpty() && value.getCount() < 1) value.setCount(1);
            preview.setItemSlot(SLOTS[index], value); changed();
        }));
    }

    private int buildLocks() {
        int slotWidth = (contentWidth - 10) / 6;
        for (int index = 0; index < SLOTS.length; index++) {
            int selected = index;
            place(new EEButton(0, 0, slotWidth, 26, Component.literal(SLOT_NAMES[index]), b -> {
                lockSlot = selected; rebuildUi();
            }, tip(SLOT_NAMES[index])) {
                @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    bevel(g, getX(), getY(), getWidth(), getHeight(), lockSlot == selected ? 0xFF8BB77C
                            : isHoveredOrFocused() ? 0xFFD1D9CD : 0xFFADB7A7);
                    GuiUtils.drawItemStack(g, SLOT_ICONS[selected], getX() + (getWidth() - 16) / 2, getY() + 5);
                }
            }, contentX + index * (slotWidth + 2), 0);
        }
        decorate(35, g -> g.text(font, SLOT_NAMES[lockSlot], contentX + 2, screenY(35), TEXT, false));
        lockRow("Lock slot", "Prevent all player interaction with this slot.", 0, 50);
        lockRow("Prevent removal", "Prevent players from removing or replacing an item in this slot.", 8, 80);
        lockRow("Prevent adding", "Prevent players from placing an item in an empty slot.", 16, 110);
        allSlots = place(new EEButton(0, 0, contentWidth, 20, Component.literal(allSlotsLabel()), b -> {
            int locks = 0;
            if (ArmorStandEditorUtils.getDisabledSlots(preview) == 0)
                for (EquipmentSlot slot : SLOTS)
                    for (int offset : new int[]{0, 8, 16}) locks |= 1 << slot.getFilterBit(offset);
            ArmorStandEditorUtils.setDisabledSlots(preview, locks); changed();
        }), contentX, 148);
        return 170;
    }

    private void lockRow(String label, String help, int offset, int y) {
        int mask = 1 << SLOTS[lockSlot].getFilterBit(offset);
        toggleRow(label, help, () -> (ArmorStandEditorUtils.getDisabledSlots(preview) & mask) != 0, value -> {
            int locks = ArmorStandEditorUtils.getDisabledSlots(preview);
            ArmorStandEditorUtils.setDisabledSlots(preview, value ? locks | mask : locks & ~mask);
        }, contentX, y, contentWidth);
    }

    private int buildSettings() {
        if (nameDocument == null) nameDocument = new StyledTextDocument(preview.getCustomName());
        decorate(1, g -> g.text(font, "Custom name (optional)", contentX + 1, screenY(1), TEXT, false));
        nameField = place(new StyledTextField(font, 0, 0, contentWidth - 8, 20, Component.literal("Custom name"),
                nameDocument, 256), contentX + 4, 14);
        nameField.setHint(Component.literal("Leave blank for no name").withColor(0xAEBBB5));
        nameField.setStyledResponder(value -> { preview.setCustomName(value.getString().isBlank() ? null : value); changed(); });
        nameToolbar = nameField.createToolbar(this, contentX, contentX + contentWidth);
        decorate(14, g -> {
            EETextField.validationFrame(nameField, contentX, screenY(14), contentWidth, 20);
            inputFrame(g, contentX, screenY(14), contentWidth, 20);
        });
        toggleRow("Show name", "Always show the custom name above the stand.", preview::isCustomNameVisible,
                preview::setCustomNameVisible, contentX, 40, contentWidth);
        boolean columns = contentWidth >= 280;
        int cellWidth = columns ? (contentWidth - 4) / 2 : contentWidth;
        int appearanceHeader = 73;
        int y = appearanceHeader + 10;
        decorate(appearanceHeader, g -> g.text(font, "Appearance", contentX + 1, screenY(appearanceHeader), MUTED, false));
        String[] labels = { "Small", "Show arms", "Base plate", "Invisible" };
        String[] help = { "Use a smaller armor stand.", "Display the stand's arms and held items.",
                "Show the stone base plate.", "Hide the stand itself; equipped items remain visible." };
        BooleanSupplier[] values = { preview::isSmall, preview::showArms, preview::showBasePlate, preview::isInvisible };
        List<Consumer<Boolean>> setters = List.of(v -> ArmorStandEditorUtils.setSmall(preview, v), preview::setShowArms,
                v -> preview.setNoBasePlate(!v), preview::setInvisible);
        for (int i = 0; i < 4; i++) toggleRow(labels[i], help[i], values[i], setters.get(i),
                contentX + (columns ? i % 2 * (cellWidth + 4) : 0), y + (columns ? i / 2 : i) * 30, cellWidth);
        y += (columns ? 2 : 4) * 30 + 13;
        int header = y - 10;
        decorate(header, g -> g.text(font, "Behavior", contentX + 1, screenY(header), MUTED, false));
        labels = new String[]{ "Gravity", "Invulnerable", "Glowing", "Marker" };
        help = new String[]{ "Allow the stand to fall under gravity.", "Protect the stand from damage.",
                "Give the stand the glowing effect.", "Remove the stand's collision and interaction hitbox." };
        values = new BooleanSupplier[]{ () -> !preview.isNoGravity(), preview::isInvulnerable, preview::hasGlowingTag, preview::isMarker };
        setters = List.of(v -> preview.setNoGravity(!v), preview::setPermanentlyInvulnerable, preview::setGlowingTag,
                v -> ArmorStandEditorUtils.setMarker(preview, v));
        for (int i = 0; i < 4; i++) toggleRow(labels[i], help[i], values[i], setters.get(i),
                contentX + (columns ? i % 2 * (cellWidth + 4) : 0), y + (columns ? i / 2 : i) * 30, cellWidth);
        return y + (columns ? 2 : 4) * 30;
    }

    private void toggleRow(String label, String help, BooleanSupplier getter, Consumer<Boolean> setter, int x, int y, int w) {
        decorate(y, g -> {
            rect(g, x, screenY(y), w, 28, 0xFFBBC2B5);
            g.text(font, fit(label, w - 67), x + 6, screenY(y) + 10, TEXT, false);
        });
        place(new EEButton(0, 0, 48, 20, Component.literal(label), b -> { setter.accept(!getter.getAsBoolean()); changed(); }, tip(help)) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                boolean on = getter.getAsBoolean();
                setMessage(Component.literal(label + ": " + (on ? "On" : "Off")));
                bevel(g, getX(), getY(), getWidth(), getHeight(), EETheme.controlColor(on, isHoveredOrFocused(), active));
                String text = on ? "On" : "Off";
                g.text(font, text, getX() + (getWidth() - font.width(text)) / 2, getY() + 6, on ? 0xFFFFFFFF : TEXT, false);
            }
        }, x + w - 53, y + 4);
        decorate(y, g -> {
            if (mouseInView(lastMouseX, lastMouseY) && lastMouseX >= x && lastMouseX < x + w - 58
                    && lastMouseY >= screenY(y) && lastMouseY < screenY(y) + 28)
                g.setTooltipForNextFrame(font, font.split(Component.literal(help), Math.min(240, width - 24)), lastMouseX, lastMouseY);
        });
    }

    private String allSlotsLabel() {
        return ArmorStandEditorUtils.getDisabledSlots(preview) == 0 ? "Lock all slots" : "Unlock all slots";
    }
    protected void changed() {
        currentData = captureData();
        if (allSlots != null) allSlots.setMessage(Component.literal(allSlotsLabel()));
    }
    private void refreshValidity() {
        if (apply != null) {
            apply.active = validAngles() && validName();
            apply.setTooltip(apply.active ? null : tip(!validName() ? "Custom name exceeds the 256 character limit."
                    : "Enter valid angles from -180 to 180 degrees before applying."));
        }
    }
    private boolean validAngles() { return angleFields.stream().allMatch(field -> parseAngle(field.getValue()) != null); }
    private boolean validName() { return nameDocument == null || nameDocument.text().length() <= 256; }
    private static Float parseAngle(String value) {
        try { float angle = Float.parseFloat(value); return Float.isFinite(angle) && angle >= -180 && angle <= 180 ? angle : null; }
        catch (NumberFormatException ignored) { return null; }
    }
    private static String degrees(float value) { return String.format(Locale.ROOT, "%.1f", value); }
    private void applyChanges() {
        if (!validAngles() || !validName()) return;
        changed(); saveData(); minecraft.gui.setScreen(parent);
    }
    private boolean modified() { return !captureData().equals(initialSnapshot); }
    private void cancel() {
        if (modified()) minecraft.gui.setScreen(new GuiConfirmation(this, Component.translatable("gui.ee.discard_changes_question"),
                () -> minecraft.gui.setScreen(parent), () -> minecraft.gui.setScreen(this)));
        else minecraft.gui.setScreen(parent);
    }
    private Rotations rotation(Part part) { return switch (part) {
        case HEAD -> preview.getHeadPose(); case BODY -> preview.getBodyPose();
        case LEFT_ARM -> preview.getLeftArmPose(); case RIGHT_ARM -> preview.getRightArmPose();
        case LEFT_LEG -> preview.getLeftLegPose(); case RIGHT_LEG -> preview.getRightLegPose();
    }; }
    private static Rotations defaultRotation(Part part) { return switch (part) {
        case HEAD -> ArmorStand.DEFAULT_HEAD_POSE; case BODY -> ArmorStand.DEFAULT_BODY_POSE;
        case LEFT_ARM -> ArmorStand.DEFAULT_LEFT_ARM_POSE; case RIGHT_ARM -> ArmorStand.DEFAULT_RIGHT_ARM_POSE;
        case LEFT_LEG -> ArmorStand.DEFAULT_LEFT_LEG_POSE; case RIGHT_LEG -> ArmorStand.DEFAULT_RIGHT_LEG_POSE;
    }; }
    private void setRotation(Part part, Rotations value) { switch (part) {
        case HEAD -> preview.setHeadPose(value); case BODY -> preview.setBodyPose(value);
        case LEFT_ARM -> preview.setLeftArmPose(value); case RIGHT_ARM -> preview.setRightArmPose(value);
        case LEFT_LEG -> preview.setLeftLegPose(value); case RIGHT_LEG -> preview.setRightLegPose(value);
    } }
    private void setRotationAxis(int axis, float value) {
        Rotations old = rotation(part);
        setRotation(part, new Rotations(axis == 0 ? value : old.x(), axis == 1 ? value : old.y(), axis == 2 ? value : old.z()));
        changed();
    }

    private void layoutControls() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        for (Placed placed : controls) {
            AbstractWidget widget = placed.widget();
            widget.setX(placed.x()); widget.setY(screenY(placed.y()));
            widget.visible = widget.getBottom() > viewTop && widget.getY() < viewBottom;
            if (!widget.visible && widget.isFocused()) clearFocus();
        }
    }
    private int maxScroll() { return Math.max(0, contentHeight - (viewBottom - viewTop)); }
    private boolean mouseInView(double x, double y) { return x >= contentX && x < contentX + contentWidth && y >= viewTop && y < viewBottom; }
    private boolean inPreview(double x, double y) { return x >= previewX && x < previewX + previewWidth && y >= previewY && y < previewY + previewHeight; }
    private void dragScroll(double y) {
        int height = viewBottom - viewTop, thumb = Math.max(16, height * height / Math.max(1, contentHeight));
        scroll = (int) ((y - viewTop - thumb / 2.0) * maxScroll() / Math.max(1, height - thumb)); layoutControls();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (openMenu != null) {
            for (EEButton option : menuOptions) if (option.mouseClicked(event, doubleClick)) return true;
            closeMenu(); return true;
        }
        if (resetMenu.mouseClicked(event, doubleClick)) return true;
        if (partSelector != null && partSelector.visible && mouseInView(event.x(), event.y())
                && partSelector.mouseClicked(event, doubleClick)) return true;
        if (nameToolbar != null && nameField.visible && nameToolbar.click(event)) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inPreview(event.x(), event.y())) {
            clearFocus(); draggingPreview = true; return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && maxScroll() > 0 && event.x() >= contentX + contentWidth + 3
                && event.x() < contentX + contentWidth + 10 && event.y() >= viewTop && event.y() < viewBottom) {
            clearFocus(); draggingScrollbar = true; dragScroll(event.y()); return true;
        }
        if (mouseInView(event.x(), event.y())) return super.mouseClicked(event, doubleClick);
        for (Placed placed : controls) placed.widget().visible = false;
        boolean handled = super.mouseClicked(event, doubleClick); layoutControls(); return handled;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingPreview && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            yaw = Mth.wrapDegrees(yaw - (float) dx * 2); pitch = Mth.clamp(pitch - (float) dy, -35, 35); return true;
        }
        if (draggingScrollbar && event.button() == InputConstants.MOUSE_BUTTON_LEFT) { dragScroll(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) draggingPreview = draggingScrollbar = false;
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (openMenu != null) return true;
        if (inPreview(x, y)) { zoom = Mth.clamp(zoom + (float) vertical * .1f, .65f, 1.6f); return true; }
        if (mouseInView(x, y)) { scroll += (int) Math.round(-vertical * 30); layoutControls(); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (openMenu != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) { closeMenu(); return true; }
            if (event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_DOWN
                    || event.key() == InputConstants.KEY_TAB) {
                int current = menuOptions.indexOf(getFocused());
                boolean backwards = event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_TAB && event.hasShiftDown();
                int next = Math.floorMod(current + (backwards ? -1 : 1), menuOptions.size());
                setFocused(menuOptions.get(next)); return true;
            }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER
                    || event.key() == InputConstants.KEY_SPACE) {
                if (getFocused() instanceof EEButton option && menuOptions.contains(option)) option.onPress(event);
                return true;
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) { cancel(); return true; }
        if (event.hasControlDown() && event.key() == InputConstants.KEY_S) { applyChanges(); return true; }
        return super.keyPressed(event);
    }
    @Override public void onClose() { cancel(); }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    private int lastMouseX, lastMouseY;
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int widgetMouseX = openMenu == null ? mx : -1, widgetMouseY = openMenu == null ? my : -1;
        lastMouseX = widgetMouseX; lastMouseY = widgetMouseY;
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        bevel(g, left, top, panelWidth, panelHeight, 0xFFC6C6C6);
        GuiUtils.drawItemStack(g, new ItemStack(Items.ARMOR_STAND), left + 10, top + 9);
        g.text(font, title, left + 31, top + 13, TEXT, false);
        rect(g, previewX - 1, previewY - 1, previewWidth + 2, previewHeight + 2, 0xFF727C6B);
        GuiUtils.drawGradientRect(g, previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF46504A, 0xFF343B39);
        if (preview != null) {
            int scale = (int) (Math.min((previewHeight - 18) / 2.05f, previewWidth / 1.55f) * zoom);
            GuiEntityPreviewUtils.renderEntityInBox(g, previewX, previewY, previewWidth, previewHeight,
                    Math.max(15, scale), 0, yaw, pitch, preview);
        }
        boolean toolbarHovered = nameToolbar != null && nameField.visible && nameToolbar.isMouseOver(mx, my);
        g.enableScissor(contentX, viewTop, contentX + contentWidth, viewBottom);
        for (Decoration decoration : decorations) if (screenY(decoration.y()) < viewBottom && screenY(decoration.y()) + 30 > viewTop)
            decoration.draw().accept(g);
        for (Placed placed : controls) if (placed.widget().visible) {
            if (placed.widget() instanceof EditBox field) ((EditBoxAccessor) field).ee$setTextY(field.getY() + 5);
            placed.widget().extractRenderState(g, mouseInView(widgetMouseX, widgetMouseY) && !toolbarHovered ? widgetMouseX : -1,
                    mouseInView(widgetMouseX, widgetMouseY) && !toolbarHovered ? widgetMouseY : -1, delta);
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int height = viewBottom - viewTop, thumb = Math.max(16, height * height / contentHeight);
            int barX = contentX + contentWidth + 5;
            EETheme.scrollbar(g, barX, viewTop, 4, height, viewTop + (height - thumb) * scroll / maxScroll(), thumb,
                    widgetMouseX >= barX - 3 && widgetMouseX < barX + 7 && widgetMouseY >= viewTop && widgetMouseY < viewBottom);
        }
        rect(g, left + 10, top + panelHeight - 35, panelWidth - 20, 1, 0xFF969E91);
        refreshValidity();
        super.extractRenderState(g, widgetMouseX, widgetMouseY, delta);
        if (openMenu == null && nameToolbar != null && nameField.visible && nameField.getY() >= viewTop) nameToolbar.render(g, mx, my);
        if (openMenu != null) {
            rect(g, menuX + 2, menuY + 2, menuWidth + 1, menuHeight + 1, 0x55000000);
            bevel(g, menuX, menuY, menuWidth, menuHeight, 0xFFBEC6B7);
            for (EEButton option : menuOptions) option.extractRenderState(g, mx, my, delta);
        }
    }

    private final class DegreeSlider extends AbstractSliderButton {
        private final Consumer<Float> setter;
        DegreeSlider(int width, float initial, Consumer<Float> setter) {
            super(0, 0, width, 18, Component.literal("Angle"), (Mth.clamp(initial, -180, 180) + 180) / 360.0);
            this.setter = setter; updateMessage();
        }
        void setDegrees(float degrees) { value = (degrees + 180) / 360.0; updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal(degrees((float) (value * 360 - 180)) + " degrees")); }
        @Override protected void applyValue() { setter.accept(Math.round((float) (value * 360 - 180) * 10) / 10f); }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            EETheme.slider(g, getX(), getY(), getWidth(), getHeight(), value, isMouseOver(mx, my) || isFocused(), true);
        }
    }
    private static Tooltip tip(String value) { return Tooltip.create(Component.literal(value)); }
    private String fit(String text, int width) { return font.width(text) <= width ? text
            : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "..."; }
    private void card(GuiGraphicsExtractor g, int y, int height) { rect(g, contentX, screenY(y), contentWidth, height, 0xFFBBC2B5); }
    private static void inputFrame(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        rect(g, x, y, width, height, 0xFF555D53); rect(g, x + 1, y + 1, width - 2, height - 2, 0xFF343B39);
        rect(g, x, y + height - 1, width, 1, 0xFFF1F3EF); rect(g, x + width - 1, y, 1, height, 0xFFF1F3EF);
    }
    private static void bevel(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        EETheme.bevel(g, x, y, width, height, color, false);
    }
    private static void rect(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        GuiUtils.drawRect(g, x, y, x + width, y + height, color);
    }
}
