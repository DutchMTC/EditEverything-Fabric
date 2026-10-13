package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EEDropdownScrollbar;
import com.dutchmtc.ee.gui.components.EEEquipmentSlotButton;
import com.dutchmtc.ee.gui.components.EETextField;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.GuiEntityPreviewUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils.ContainerData;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Armor-stand-style equipment rows, with a detached live mob preview and inline drop chances. */
public final class GuiSpawnEggEquipmentModifier
        extends GuiModifier<GuiSpawnEggEquipmentModifier.EquipmentData> {
    public record EquipmentData(ContainerData equipment, float[] dropChances) {
        public EquipmentData copy() {
            return new EquipmentData(
                    equipment.copy(), dropChances != null ? dropChances.clone() : new float[0]);
        }
    }

    // Persistence keeps its original slot order; only the presentation matches the armor stand.
    private static final int[] ORDER = {2, 3, 4, 5, 0, 1, 6, 7};
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.BODY,
        EquipmentSlot.SADDLE
    };
    private static final String[] NAMES = {
        "Main hand", "Off hand", "Head", "Chest", "Legs", "Feet", "Body", "Saddle"
    };
    private static final int ROW = 29, CHANCE_WIDTH = 44;
    private final EquipmentData originalData, data;
    private final ItemStack entityItem;
    private final boolean showDropChances;
    private final List<Component> slotNames;
    private final String[] chanceDrafts = new String[8], initialChanceText = new String[8];
    private final List<EEEquipmentSlotButton> itemButtons = new ArrayList<>();
    private final List<EEButton> removeButtons = new ArrayList<>();
    private final List<EETextField> chanceFields = new ArrayList<>();
    private final EEDropdownScrollbar scrollbar = new EEDropdownScrollbar();
    private EEButton apply;
    private int left,
            top,
            panelWidth,
            panelHeight,
            previewX,
            previewWidth,
            contentX,
            contentWidth,
            viewTop,
            viewBottom,
            scroll;
    private boolean previewDirty = true, draggingPreview;
    private float yaw = -25, pitch = -8, zoom = 1;
    private Entity preview;
    private net.minecraft.world.level.Level previewLevel;

    public GuiSpawnEggEquipmentModifier(
            Screen parent,
            Component title,
            Consumer<EquipmentData> setter,
            EquipmentData data,
            List<Component> slotNames,
            ItemStack entityItem) {
        super(parent, title, setter);
        this.originalData = data.copy();
        float[] chances =
                Arrays.copyOf(data.dropChances() == null ? new float[0] : data.dropChances(), 8);
        this.data = new EquipmentData(data.equipment().copy(), chances);
        this.slotNames = slotNames == null ? List.of() : List.copyOf(slotNames);
        this.entityItem = entityItem.copy();
        var entityData = entityItem.get(net.minecraft.core.component.DataComponents.ENTITY_DATA);
        showDropChances = entityData == null || entityData.type() != net.minecraft.world.entity.EntityTypes.MANNEQUIN;
        for (int slot = 0; slot < 8; slot++)
            chanceDrafts[slot] = initialChanceText[slot] = percentText(chances[slot]);
    }

    @Override
    public boolean isModified() {
        for (int slot = 0; slot < 8; slot++) {
            if (!ItemStack.matches(
                    data.equipment().stacks().get(slot),
                    originalData.equipment().stacks().get(slot))) return true;
            if (chanceError(slot, chanceDrafts[slot]) != null) return true;
        }
        return !Arrays.equals(
                data.dropChances(),
                Arrays.copyOf(
                        originalData.dropChances() == null
                                ? new float[0]
                                : originalData.dropChances(),
                        8));
    }

    @Override
    protected void init() {
        clearWidgets();
        clearFocus();
        itemButtons.clear();
        removeButtons.clear();
        chanceFields.clear();
        panelWidth = Math.min(500, width - 16);
        panelHeight = Math.min(320, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        previewX = left + 10;
        previewWidth = panelWidth >= 440 ? 130 : panelWidth >= 350 ? 100 : 76;
        contentX = previewX + previewWidth + 10;
        contentWidth = left + panelWidth - 10 - contentX;
        viewTop = top + 48;
        viewBottom = top + panelHeight - 40;
        draggingPreview = false;
        setDragging(false);
        scrollbar.reset();

        addRenderableWidget(
                new EEButton(
                        left + panelWidth - 82,
                        top + 10,
                        72,
                        18,
                        Component.literal("Reset view"),
                        b -> {
                            yaw = -25;
                            pitch = -8;
                            zoom = 1;
                        },
                        tip("Reset the preview camera.")));
        int footer = top + panelHeight - 28, unit = (panelWidth - 24) / 2;
        addRenderableWidget(
                new EEButton(
                        left + 10, footer, unit, 20, Component.literal("Cancel"), b -> onCancel()));
        apply =
                addRenderableWidget(
                        new EEButton(
                                left + 14 + unit,
                                footer,
                                panelWidth - 24 - unit,
                                20,
                                Component.literal("Apply"),
                                b -> applyChanges()));

        for (int row = 0; row < ORDER.length; row++) {
            int slot = ORDER[row];
            itemButtons.add(
                    addWidget(
                            new EEEquipmentSlotButton(
                                    0,
                                    0,
                                    22,
                                    22,
                                    Component.literal("Edit " + slotName(slot)),
                                    b -> openItemEditor(slot),
                                    tip(
                                            "Choose or edit the item for "
                                                    + slotName(slot).toLowerCase(Locale.ROOT)
                                                    + ". Change its amount in the item's Type"
                                                    + " screen."),
                                    () -> data.equipment().stacks().get(slot))));
            removeButtons.add(
                    addWidget(
                            new EEButton(
                                    0,
                                    0,
                                    24,
                                    20,
                                    Component.literal("×"),
                                    b -> {
                                        data.equipment().stacks().set(slot, ItemStack.EMPTY);
                                        previewDirty = true;
                                        position();
                                    },
                                    tip(
                                            "Remove the item from "
                                                    + slotName(slot).toLowerCase(Locale.ROOT)
                                                    + "."))));
            EETextField field =
                    new EETextField(
                            font,
                            0,
                            0,
                            CHANCE_WIDTH - 8,
                            18,
                            Component.literal(slotName(slot) + " drop chance"));
            field.setBordered(false);
            field.setTextShadow(false);
            field.setTextColor(EETheme.LIGHT);
            field.setMaxLength(Math.max(16, chanceDrafts[slot].length()));
            field.setValue(chanceDrafts[slot]);
            field.setTooltip(
                    tip("Chance for this item to drop when the mob dies. Enter 0 to 100%."));
            field.setResponder(
                    value -> {
                        chanceDrafts[slot] = value;
                        String error = chanceError(slot, value);
                        EETextField.validate(field, error);
                        if (error == null && !value.equals(initialChanceText[slot]))
                            data.dropChances()[slot] = Float.parseFloat(value.trim()) / 100;
                        else if (error == null) data.dropChances()[slot] = originalChance(slot);
                        refreshValidity();
                    });
            EETextField.validate(field, chanceError(slot, field.getValue()));
            chanceFields.add(addWidget(field));
        }
        position();
        refreshValidity();
    }

    private String slotName(int slot) {
        return slot < slotNames.size() ? slotNames.get(slot).getString() : NAMES[slot];
    }

    private int maxScroll() {
        return Math.max(0, ORDER.length * ROW - (viewBottom - viewTop));
    }

    private int rowRight() {
        return contentX + contentWidth - (maxScroll() > 0 ? EEDropdownScrollbar.WIDTH : 0);
    }

    private int chanceX() {
        return rowRight() - 27 - 6 - (showDropChances ? CHANCE_WIDTH : 0);
    }

    private int rowY(int row) {
        return viewTop + row * ROW - scroll;
    }

    private void position() {
        scroll = Math.clamp(scroll, 0, maxScroll());
        for (int row = 0; row < ORDER.length; row++) {
            int y = rowY(row);
            var item = itemButtons.get(row);
            var remove = removeButtons.get(row);
            var chance = chanceFields.get(row);
            item.setPosition(contentX + 3, y + 2);
            remove.setPosition(rowRight() - 27, y + 3);
            chance.setPosition(chanceX() + 4, y + 5);
            item.visible = item.getBottom() > viewTop && item.getY() < viewBottom;
            remove.visible = remove.getBottom() > viewTop && remove.getY() < viewBottom;
            chance.visible = showDropChances && chance.getBottom() > viewTop && chance.getY() < viewBottom;
            remove.active = !data.equipment().stacks().get(ORDER[row]).isEmpty();
            remove.setTooltip(
                    tip(
                            remove.active
                                    ? "Remove the item from "
                                            + slotName(ORDER[row]).toLowerCase(Locale.ROOT)
                                            + "."
                                    : "This slot is already empty."));
            if (!chance.visible && chance.isFocused()) clearFocus();
        }
        scrollbar.layout(
                contentX + contentWidth - EEDropdownScrollbar.WIDTH,
                viewTop,
                viewBottom - viewTop,
                viewBottom - viewTop,
                ORDER.length * ROW,
                scroll,
                ROW);
    }

    private float originalChance(int slot) {
        return originalData.dropChances() != null && slot < originalData.dropChances().length
                ? originalData.dropChances()[slot]
                : 0;
    }

    private static String percentText(float chance) {
        if (!Float.isFinite(chance)) return Float.toString(chance);
        return new BigDecimal(Float.toString(chance))
                .multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros()
                .toPlainString();
    }

    private String chanceError(int slot, String value) {
        if (!showDropChances) return null;
        try {
            float percent = Float.parseFloat(value.trim());
            // Preserve imported guaranteed-drop values without silently reducing them on an
            // unrelated edit.
            if (Float.isFinite(percent)
                    && percent >= 0
                    && (percent <= 100
                            || value.equals(initialChanceText[slot]) && originalChance(slot) > 1))
                return null;
        } catch (NumberFormatException ignored) {
        }
        return "Enter a finite drop chance from 0 to 100%.";
    }

    private void refreshValidity() {
        if (apply == null) return;
        String reason = null;
        for (int slot = 0; slot < 8 && reason == null; slot++)
            reason = chanceError(slot, chanceDrafts[slot]);
        apply.active = reason == null;
        apply.setTooltip(reason == null ? null : tip(reason));
    }

    private void applyChanges() {
        for (int slot = 0; slot < 8; slot++)
            if (chanceError(slot, chanceDrafts[slot]) != null) return;
        set(data.copy());
        mc.gui.setScreen(parent);
    }

    private void openItemEditor(int slot) {
        mc.gui.setScreen(
                new GuiItemStackModifier(
                        this,
                        data.equipment().stacks().get(slot).copy(),
                        item -> {
                            data.equipment()
                                    .stacks()
                                    .set(slot, item == null ? ItemStack.EMPTY : item.copy());
                            previewDirty = true;
                        }));
    }

    private boolean inList(double x, double y) {
        return x >= contentX && x < rowRight() && y >= viewTop && y < viewBottom;
    }

    private boolean inPreview(double x, double y) {
        return x >= previewX && x < previewX + previewWidth && y >= viewTop && y < viewBottom;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (scrollbar.mouseClicked(
                e,
                v -> {
                    scroll = v;
                    position();
                })) {
            clearFocus();
            return true;
        }
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && inPreview(e.x(), e.y())) {
            clearFocus();
            draggingPreview = true;
            setDragging(true);
            return true;
        }
        if (inList(e.x(), e.y())) {
            if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && e.x() < chanceX() - 6) {
                int row = Math.clamp(((int) e.y() - viewTop + scroll) / ROW, 0, ORDER.length - 1);
                playClick();
                openItemEditor(ORDER[row]);
                return true;
            }
            for (var field : chanceFields) {
                if (field.visible
                        && e.x() >= chanceX()
                        && e.x() < chanceX() + CHANCE_WIDTH
                        && e.y() >= field.getY()
                        && e.y() < field.getBottom()) {
                    if (GuiUtils.isRightMouseButton(e.button())) {
                        setFocused(field);
                        field.setValue("");
                        return true;
                    }
                    if (e.button() == InputConstants.MOUSE_BUTTON_LEFT
                            && !field.isMouseOver(e.x(), e.y())) {
                        setFocused(field);
                        field.setCursorPosition(
                                e.x() < field.getX() ? 0 : field.getValue().length());
                        return true;
                    }
                }
            }
            return super.mouseClicked(e, twice);
        }
        // Scrolled controls never consume clicks in the header/footer.
        for (var item : itemButtons) item.visible = false;
        for (var remove : removeButtons) remove.visible = false;
        for (var chance : chanceFields) chance.visible = false;
        boolean handled = super.mouseClicked(e, twice);
        position();
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (scrollbar.mouseDragged(
                e,
                v -> {
                    scroll = v;
                    position();
                })) return true;
        if (draggingPreview && e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            yaw -= (float) dx * 2;
            pitch = Math.clamp(pitch - (float) dy, -35, 35);
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (scrollbar.mouseReleased()) return true;
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && draggingPreview) {
            draggingPreview = false;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inPreview(x, y)) {
            zoom = Math.clamp(zoom + (float) vertical * .1f, .5f, 2);
            return true;
        }
        if (x >= contentX && x < contentX + contentWidth && y >= viewTop && y < viewBottom) {
            scroll -= (int) Math.round(vertical * ROW);
            position();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public void onClose() {
        onCancel();
    }

    private void refreshPreview() {
        if (!previewDirty && previewLevel == mc.level) return;
        previewDirty = false;
        previewLevel = mc.level;
        preview = null;
        if (mc.level == null) return;
        try {
            var type = SpawnEggItem.getType(entityItem);
            Entity entity =
                    type.create(mc.level, new EntitySpawnRequest(EntitySpawnReason.LOAD, true));
            if (entity == null) return;
            var stored = entityItem.get(DataComponents.ENTITY_DATA);
            CompoundTag tag = stored == null ? new CompoundTag() : stored.copyTagWithoutId();
            for (String key :
                    List.of("UUID", "Pos", "Motion", "Rotation", "Passengers", "Dimension"))
                tag.remove(key);
            TypedEntityData.of(type, tag).loadInto(entity);
            if (entity instanceof LivingEntity living)
                for (int slot = 0; slot < 8; slot++)
                    living.setItemSlot(SLOTS[slot], data.equipment().stacks().get(slot).copy());
            entity.setPos(0, 0, 0);
            entity.setCustomNameVisible(false);
            preview = entity;
        } catch (Exception ex) {
            com.dutchmtc.ee.EEMod.LOGGER.debug("Cannot preview mob equipment", ex);
        }
    }

    private String fit(String text, int width) {
        return font.width(text) <= width
                ? text
                : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        GuiUtils.drawItemStack(g, entityItem.copyWithCount(1), left + 10, top + 10);
        g.text(font, title, left + 31, top + 14, EETheme.TEXT, false);
        if (showDropChances) g.text(font, "Drop %", chanceX() + 2, top + 36, EETheme.MUTED, false);
        GuiUtils.drawGradientRect(
                g, previewX, viewTop, previewX + previewWidth, viewBottom, 0xFF46504A, 0xFF343B39);
        refreshPreview();
        if (preview != null) {
            try {
                float w = Math.max(.3f, preview.getBbWidth()),
                        h = Math.max(.3f, preview.getBbHeight());
                if (preview instanceof LivingEntity living) {
                    float scale = Math.max(.01f, living.getScale() * living.getAgeScale());
                    w /= scale;
                    h /= scale;
                }
                int scale =
                        Math.max(
                                1,
                                Math.round(
                                        Math.min(
                                                        (previewWidth - 14) / (w * 1.4f),
                                                        (viewBottom - viewTop - 14) / (h * 1.15f))
                                                * zoom));
                GuiEntityPreviewUtils.renderEntityInBox(
                        g,
                        previewX + 2,
                        viewTop + 2,
                        previewWidth - 4,
                        viewBottom - viewTop - 4,
                        scale,
                        0,
                        yaw,
                        pitch,
                        preview);
            } catch (Exception ex) {
                preview = null;
                com.dutchmtc.ee.EEMod.LOGGER.debug("Cannot render mob equipment", ex);
            }
        }
        if (preview == null)
            g.text(
                    font,
                    fit("No preview", previewWidth - 8),
                    previewX + 4,
                    viewTop + (viewBottom - viewTop) / 2,
                    EETheme.LIGHT,
                    false);
        g.enableScissor(contentX, viewTop, rowRight(), viewBottom);
        boolean hovered = inList(mx, my);
        for (int row = 0; row < 8; row++) {
            int y = rowY(row), slot = ORDER[row];
            if (y + ROW <= viewTop || y >= viewBottom) continue;
            ItemStack stack = data.equipment().stacks().get(slot);
            EETheme.rect(g, contentX, y, rowRight() - contentX, 27, EETheme.OPTION);
            g.text(
                    font,
                    fit(slotName(slot), chanceX() - contentX - 37),
                    contentX + 31,
                    y + (stack.isEmpty() ? 9 : 3),
                    EETheme.TEXT,
                    false);
            if (!stack.isEmpty())
                g.text(
                        font,
                        fit(stack.getHoverName().getString(), chanceX() - contentX - 37),
                        contentX + 31,
                        y + 15,
                        EETheme.MUTED,
                        false);
            if (hovered && mx >= contentX + 27 && mx < chanceX() - 6 && my >= y && my < y + 27) {
                if (!stack.isEmpty()) GuiUtils.setTooltipForNextFrame(g, font, stack, mx, my);
                else
                    g.setTooltipForNextFrame(
                            font,
                            Component.literal(
                                    "Choose an item for "
                                            + slotName(slot).toLowerCase(Locale.ROOT)
                                            + "."),
                            mx,
                            my);
            }
            var field = chanceFields.get(row);
            if (showDropChances) EETheme.bevel(g, chanceX(), y + 5, CHANCE_WIDTH, 18, EETheme.INPUT, true);
            EETextField.validationFrame(field, chanceX(), y + 5, CHANCE_WIDTH, 18);
            itemButtons.get(row).extractRenderState(g, hovered ? mx : -1, hovered ? my : -1, delta);
            removeButtons
                    .get(row)
                    .extractRenderState(g, hovered ? mx : -1, hovered ? my : -1, delta);
            ((EditBoxAccessor) field).ee$setTextY(field.getY() + 5);
            field.extractRenderState(g, hovered ? mx : -1, hovered ? my : -1, delta);
        }
        g.disableScissor();
        scrollbar.render(g, mx, my);
        EETheme.rect(g, left + 10, top + panelHeight - 35, panelWidth - 20, 1, EETheme.TRACK);
        super.extractRenderState(g, mx, my, delta);
    }

    private static Tooltip tip(String text) {
        return Tooltip.create(Component.literal(text));
    }
}
