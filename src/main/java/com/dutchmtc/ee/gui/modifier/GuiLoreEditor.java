package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Ordered lore lines using the same text and style controls as signs and item names. */
public final class GuiLoreEditor extends Screen {
    private final Screen parent;
    private final Consumer<List<Component>> setter;
    private final List<Component> original;
    private final List<Component> lines;
    private final int maxLines;
    private int page, perPage, left, top, panelWidth, panelHeight;

    public GuiLoreEditor(Screen parent, List<Component> initial, Consumer<List<Component>> setter) {
        this(parent, initial, ItemLore.MAX_LINES, setter);
    }
    public GuiLoreEditor(Screen parent, List<Component> initial, int maxLines, Consumer<List<Component>> setter) {
        super(Component.literal("Item lore"));
        this.parent = parent;
        this.setter = setter;
        this.original = List.copyOf(initial);
        this.lines = new ArrayList<>(initial);
        this.maxLines = Math.max(0, maxLines);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(480, width - 16);
        panelHeight = Math.min(330, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        perPage = Math.max(1, (panelHeight - 100) / 28);
        page = Math.min(page, pageCount() - 1);
        int first = page * perPage;
        int end = Math.min(lines.size(), first + perPage);
        int listX = left + 12;
        int listWidth = panelWidth - 24;
        for (int i = first; i < end; i++) {
            int index = i;
            int rowY = top + 41 + (i - first) * 28;
            EEButton line = new EEButton(listX, rowY, listWidth - 24, 24,
                    Component.empty(), b -> minecraft.gui.setScreen(new GuiStyledTextEditor(
                    this, "Lore line " + (index + 1), lines.get(index), 1024,
                    value -> lines.set(index, value)))) {
                @Override
                protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    box(g, getX(), getY(), getWidth(), getHeight(),
                            isHoveredOrFocused() ? 0xFFAAB6A0 : 0xFF999999);
                    g.text(font, Integer.toString(index + 1) + ".", getX() + 5, getY() + 8, 0xFF252525, false);
                    Component value = lines.get(index);
                    if (value.getString().isEmpty())
                        g.text(font, "Click to add text", getX() + 28, getY() + 8, 0xFF555555, false);
                    else {
                        g.enableScissor(getX() + 28, getY(), getX() + getWidth() - 5, getY() + getHeight());
                        g.text(font, value, getX() + 28, getY() + 8, 0xFFFFFFFF, false);
                        g.disableScissor();
                    }
                }
            };
            addRenderableWidget(line);
            addRenderableWidget(new EEButton(listX + listWidth - 22, rowY, 22, 24,
                    Component.literal("✕"), b -> {
                lines.remove(index);
                rebuildWidgets();
            }));
        }

        int actionY = top + panelHeight - 53;
        Button add = addRenderableWidget(new EEButton(listX, actionY, 104, 20,
                Component.literal("+ Add line"), b -> {
            if (lines.size() >= maxLines) return;
            lines.add(Component.empty());
            page = (lines.size() - 1) / perPage;
            rebuildWidgets();
        }));
        add.active = lines.size() < maxLines;
        if (!add.active) add.setTooltip(Tooltip.create(Component.literal("Minecraft's lore limit has been reached, including automatic permission descriptions.")));
        Button previous = addRenderableWidget(new EEButton(left + panelWidth - 94, actionY, 42, 20,
                Component.literal("<"), b -> { page--; rebuildWidgets(); }));
        previous.active = page > 0;
        Button next = addRenderableWidget(new EEButton(left + panelWidth - 49, actionY, 42, 20,
                Component.literal(">"), b -> { page++; rebuildWidgets(); }));
        next.active = (page + 1) * perPage < lines.size();

        int footerY = top + panelHeight - 25;
        int half = (panelWidth - 28) / 2;
        addRenderableWidget(new EEButton(listX, footerY, half, 20,
                Component.literal("Cancel"), b -> cancel()));
        Button save = addRenderableWidget(new EEButton(listX + half + 4, footerY, half, 20,
                Component.literal("Save lore"), b -> {
            if (lines.size() > maxLines) return;
            setter.accept(List.copyOf(lines));
            minecraft.gui.setScreen(parent);
        }));
        save.active = lines.size() <= maxLines;
        if (!save.active) save.setTooltip(Tooltip.create(Component.literal("Remove some lore lines to fit Minecraft's limit, including automatic permission descriptions.")));
    }

    private int pageCount() {
        return Math.max(1, (lines.size() + perPage - 1) / perPage);
    }

    private void cancel() {
        if (!lines.equals(original))
            minecraft.gui.setScreen(new GuiConfirmation(this,
                    Component.translatable("gui.ee.discard_changes_question"),
                    () -> minecraft.gui.setScreen(parent),
                    () -> minecraft.gui.setScreen(this)));
        else minecraft.gui.setScreen(parent);
    }

    @Override public void onClose() { cancel(); }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xB0101010, 0xD0101010);
        box(g, left - 1, top - 1, panelWidth + 2, panelHeight + 2, 0xFF202020);
        box(g, left, top, panelWidth, panelHeight, 0xFFC6C6C6);
        g.text(font, "Item lore", left + 12, top + 10, 0xFF303030, false);
        g.text(font, "Click a line to edit its color and styles", left + 12, top + 25, 0xFF555555, false);
        if (lines.isEmpty()) g.text(font, "No lore yet. Add a line below.", left + 12, top + 48, 0xFF555555, false);
        String pageLabel = "Page " + (page + 1) + " / " + pageCount();
        int labelLeft = left + 120, labelRight = left + panelWidth - 98;
        if (font.width(pageLabel) > labelRight - labelLeft)
            pageLabel = (page + 1) + " / " + pageCount();
        if (labelRight > labelLeft)
            g.text(font, pageLabel, labelLeft + (labelRight - labelLeft - font.width(pageLabel)) / 2,
                    top + panelHeight - 47, 0xFF303030, false);
        super.extractRenderState(g, mx, my, delta);
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        GuiUtils.drawRect(g, x, y, x + width, y + height, color);
    }
}
