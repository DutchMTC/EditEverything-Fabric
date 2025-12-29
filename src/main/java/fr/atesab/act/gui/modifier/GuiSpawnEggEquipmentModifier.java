package fr.atesab.act.gui.modifier;

import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import fr.atesab.act.utils.ItemUtils.ContainerData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dedicated editor for entity equipment inside a Spawn Egg. This screen edits:
 * - ItemStack for each equipment slot
 * - Stack count (amount) for each slot
 * - Drop chance (%) for each slot
 */
public class GuiSpawnEggEquipmentModifier extends GuiModifier<GuiSpawnEggEquipmentModifier.EquipmentData> {
    public static record EquipmentData(ContainerData equipment, float[] dropChances) {
        public EquipmentData copy() {
            return new EquipmentData(equipment.copy(), dropChances != null ? dropChances.clone() : new float[0]);
        }
    }

    private static final int SLOT_SIZE = 18;
    private static final int ICON_SIZE = 16;
    private static final int ROW_H = 24;

    private static final int PANEL_W = 308;
    private static final int PANEL_HEADER_Y_OFF = 22;
    private static final int PANEL_ROW_Y_OFF = 34;
    private static final int PANEL_PADDING_X = 12;

    private static final int SLIDER_W = 128;
    private static final int AMOUNT_W = 56;

    private final EquipmentData data;
    private final Component title;
    private final List<Component> slotNames;

    private final List<EditBox> amountFields = new ArrayList<>();
    private final List<PercentSlider> chanceSliders = new ArrayList<>();
    private boolean syncing;

    public GuiSpawnEggEquipmentModifier(Screen parent, Component title, Consumer<EquipmentData> setter,
            EquipmentData data, List<Component> slotNames) {
        super(parent, Component.literal("Equipment"), setter);
        this.data = data.copy();
        this.title = title;
        this.slotNames = slotNames;
    }

    private int panelLeft() {
        return width / 2 - PANEL_W / 2;
    }

    private int panelTop() {
        int panelH = panelHeight();
        return height / 2 - panelH / 2;
    }

    private int panelHeight() {
        return 40 + ROW_H * 8 + 36;
    }

    private int sliderX(int left) {
        return left + 96;
    }

    private int amountX(int left) {
        return left + PANEL_W - PANEL_PADDING_X - AMOUNT_W;
    }

    private int rowY(int top, int slot) {
        return top + PANEL_ROW_Y_OFF + slot * ROW_H + 2;
    }

    @Override
    protected void init() {
        super.init();
        amountFields.clear();
        chanceSliders.clear();

        int panelH = panelHeight();
        int left = panelLeft();
        int top = panelTop();

        addRenderableWidget(
                new ACTButton(width / 2 - 96, top + panelH - 26, 94, 20, Component.translatable("gui.done"), b -> {
                    applyAmountsFromFields();
                    set(data);
                    mc.setScreen(parent);
                }));
        addRenderableWidget(new ACTButton(width / 2 + 2, top + panelH - 26, 94, 20,
                Component.translatable("gui.cancel"), b -> mc.setScreen(parent)));

        for (int slot = 0; slot < 8; slot++) {
            int y = rowY(top, slot);

            int finalSlot = slot;
            EditBox amount = new EditBox(font, amountX(left), y, AMOUNT_W, 18, Component.literal("Amount"));
            amount.setMaxLength(3);
            amount.setFilter(v -> v.isEmpty() || v.chars().allMatch(Character::isDigit));
            amount.setResponder(v -> onAmountEdited(finalSlot, v));
            addRenderableWidget(amount);
            amountFields.add(amount);

            PercentSlider slider = new PercentSlider(sliderX(left), y, SLIDER_W, 18, Component.empty(),
                    () -> getChancePercent(finalSlot), p -> setChancePercent(finalSlot, p));
            addRenderableWidget(slider);
            chanceSliders.add(slider);
        }

        syncAllFields();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);

        int panelH = panelHeight();
        int left = panelLeft();
        int top = panelTop();
        int right = left + PANEL_W;
        int bottom = top + panelH;

        GuiUtils.drawRect(graphics, left, top, right, bottom, GuiUtils.COLOR_CONTAINER_BORDER | 0xFF000000);
        GuiUtils.drawCenterString(graphics, font, title.getString(), width / 2, top + 8, 0xFF7F7F7F);

        int headerY = top + PANEL_HEADER_Y_OFF;
        GuiUtils.drawString(graphics, font, "Slot", left + PANEL_PADDING_X, headerY, 0xFF7F7F7F, font.lineHeight);
        GuiUtils.drawString(graphics, font, "Drop %", sliderX(left), headerY, 0xFF7F7F7F, font.lineHeight);
        GuiUtils.drawString(graphics, font, "Amount", amountX(left), headerY, 0xFF7F7F7F, font.lineHeight);

        ItemStack hoverStack = null;
        Component hoverName = null;

        for (int slot = 0; slot < 8; slot++) {
            int y = rowY(top, slot);

            int iconX = left + PANEL_PADDING_X;
            int iconY = y;

            GuiUtils.drawRect(graphics, iconX - 1, iconY - 1, iconX - 1 + SLOT_SIZE, iconY - 1 + SLOT_SIZE,
                    GuiUtils.COLOR_CONTAINER_SLOT | 0xFF000000);

            ItemStack stack = data.equipment().stacks().get(slot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, iconX, iconY);
                graphics.renderItemDecorations(font, stack, iconX, iconY);
            }

            if (slotNames != null && slot < slotNames.size()) {
                GuiUtils.drawString(graphics, font, slotNames.get(slot).getString(), iconX + 22, y + 4, 0xFFE0E0E0,
                        font.lineHeight);
            }

            if (GuiUtils.isHover(iconX, iconY, ICON_SIZE, ICON_SIZE, mouseX, mouseY)) {
                if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
                    hoverStack = stack;
                } else if (slotNames != null && slot < slotNames.size()) {
                    hoverName = slotNames.get(slot);
                }
            }
        }

        super.render(graphics, mouseX, mouseY, delta);

        if (hoverStack != null) {
            graphics.pose().pushMatrix();
            GuiUtils.renderTooltip(graphics, font, hoverStack, mouseX, mouseY);
            graphics.pose().popMatrix();
        } else if (hoverName != null) {
            GuiUtils.renderTooltip(graphics, font, List.of(hoverName), java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int mouseButton = event.button();

        int left = panelLeft();
        int top = panelTop();

        for (int slot = 0; slot < 8; slot++) {
            int y = rowY(top, slot);
            int iconX = left + PANEL_PADDING_X;
            int iconY = y;
            if (GuiUtils.isHover(iconX, iconY, ICON_SIZE, ICON_SIZE, (int) mouseX, (int) mouseY)) {
                playClick();
                openItemEditor(slot);
                return true;
            }
        }

        for (EditBox amount : amountFields) {
            if (GuiUtils.isHover(amount, (int) mouseX, (int) mouseY)) {
                if (mouseButton == 1) {
                    amount.setFocused(true);
                    amount.setValue("");
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void openItemEditor(int slot) {
        ItemStack current = data.equipment().stacks().get(slot);
        mc.setScreen(new GuiItemStackModifier(this, current, newItem -> {
            ItemStack sanitized = newItem == null ? ItemStack.EMPTY : newItem;
            if (!sanitized.isEmpty() && sanitized.getCount() <= 0) {
                sanitized.setCount(1);
            }
            data.equipment().stacks().set(slot, sanitized);
            syncSlotFields(slot);
        }));
    }

    private void syncAllFields() {
        syncing = true;
        try {
            for (int slot = 0; slot < 8; slot++) {
                syncSlotFields(slot);
            }
        } finally {
            syncing = false;
        }
    }

    private void syncSlotFields(int slot) {
        syncing = true;
        try {
            ItemStack stack = data.equipment().stacks().get(slot);
            EditBox amount = amountFields.get(slot);
            amount.setEditable(!stack.isEmpty());
            amount.setValue(stack.isEmpty() ? "" : String.valueOf(stack.getCount()));
            amount.setTextColor(stack.isEmpty() ? 0xA0A0A0 : 0xE0E0E0);

            chanceSliders.get(slot).syncFromValue();
        } finally {
            syncing = false;
        }
    }

    private void onAmountEdited(int slot, String value) {
        if (syncing) {
            return;
        }

        EditBox amount = amountFields.get(slot);
        ItemStack stack = data.equipment().stacks().get(slot);
        if (stack == null || stack.isEmpty()) {
            amount.setTextColor(0xA0A0A0);
            return;
        }

        if (value == null || value.isBlank()) {
            amount.setTextColor(0xFF0000);
            return;
        }

        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                amount.setTextColor(0xFF0000);
                return;
            }
            int max = stack.getMaxStackSize();
            amount.setTextColor(parsed > max ? 0xFFFFA000 : 0xE0E0E0);
        } catch (NumberFormatException e) {
            amount.setTextColor(0xFF0000);
        }
    }

    private float getChancePercent(int slot) {
        if (data.dropChances() == null || slot < 0 || slot >= data.dropChances().length) {
            return 0.0f;
        }
        return data.dropChances()[slot] * 100.0f;
    }

    private void setChancePercent(int slot, float percent) {
        if (data.dropChances() == null || slot < 0 || slot >= data.dropChances().length) {
            return;
        }
        float clamped = Mth.clamp(percent, 0.0f, 100.0f);
        data.dropChances()[slot] = clamped / 100.0f;
    }

    private void applyAmountsFromFields() {
        syncing = true;
        try {
            for (int slot = 0; slot < 8; slot++) {
                ItemStack stack = data.equipment().stacks().get(slot);
                EditBox amount = amountFields.get(slot);
                String raw = amount.getValue();

                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                if (raw == null || raw.isBlank()) {
                    continue;
                }

                try {
                    int parsed = Integer.parseInt(raw.trim());
                    if (parsed <= 0) {
                        data.equipment().stacks().set(slot, ItemStack.EMPTY);
                        continue;
                    }
                    stack.setCount(Math.min(parsed, stack.getMaxStackSize()));
                } catch (NumberFormatException ignored) {
                    // keep existing stack count
                }
            }
        } finally {
            syncing = false;
        }
    }

    private static final class PercentSlider extends AbstractSliderButton {
        private final java.util.function.Supplier<Float> getter;
        private final java.util.function.Consumer<Float> setter;

        private PercentSlider(int x, int y, int width, int height, Component message,
                java.util.function.Supplier<Float> percentGetter,
                java.util.function.Consumer<Float> percentSetter) {
            super(x, y, width, height, message, 0.0);
            this.getter = percentGetter;
            this.setter = percentSetter;
            syncFromValue();
            updateMessage();
        }

        void syncFromValue() {
            float p = getter.get();
            value = Mth.clamp(p / 100.0f, 0.0, 1.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int pct = Math.round((float) (value * 100.0));
            setMessage(Component.literal(pct + "%"));
        }

        @Override
        protected void applyValue() {
            float percent = (float) (value * 100.0);
            setter.accept(percent);
            updateMessage();
        }
    }
}
