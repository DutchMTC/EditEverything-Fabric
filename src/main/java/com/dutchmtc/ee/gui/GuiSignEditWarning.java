package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Confirm the vanilla editor's lossy plain-text save, or keep the rich sign as an item. */
public final class GuiSignEditWarning extends Screen {
    private final Runnable exitWithoutSaving, savePlain, getItem;
    private final boolean creative, canGetItem;
    private int left, top, panelWidth, panelHeight;

    public GuiSignEditWarning(Runnable exitWithoutSaving, Runnable savePlain, Runnable getItem,
                              boolean creative, boolean canGetItem) {
        super(Component.literal("Remove sign styling?"));
        this.exitWithoutSaving = exitWithoutSaving;
        this.savePlain = savePlain;
        this.getItem = getItem;
        this.creative = creative;
        this.canGetItem = canGetItem;
    }

    @Override protected void init() {
        panelWidth = Math.min(370, width - 16);
        left = (width - panelWidth) / 2;
        panelHeight = creative ? 168 : 142;
        top = (height - panelHeight) / 2;
        int half = (panelWidth - 28) / 2;
        addRenderableWidget(new EEButton(left + 12, top + 111, half, 20,
                Component.literal("Exit without saving"), b -> exitWithoutSaving.run()));
        addRenderableWidget(new EEButton(left + 16 + half, top + 111, half, 20,
                Component.literal("Remove styling"), b -> savePlain.run()));
        if (creative) {
            EEButton item = addRenderableWidget(new EEButton(left + 12, top + 137, panelWidth - 24, 20,
                    Component.literal("Get sign item · keep styling"), b -> getItem.run()));
            item.active = canGetItem;
            if (!canGetItem) item.setTooltip(Tooltip.create(Component.literal("Free a hotbar slot to get this sign.")));
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        GuiUtils.drawRect(g, left, top, left + panelWidth, top + panelHeight, 0xFFC6C6C6);
        GuiUtils.drawRect(g, left, top, left + panelWidth, top + 1, 0xFFFFFFFF);
        GuiUtils.drawRect(g, left, top, left + 1, top + panelHeight, 0xFFFFFFFF);
        g.text(font, title, left + 12, top + 12, 0xFF922B2B, false);
        int y = top + 32;
        for (var line : font.split(Component.literal("The vanilla editor will remove the custom colors and formatting on this side when you save."), panelWidth - 24)) {
            g.text(font, line, left + 12, y, 0xFF303030, false);
            y += 11;
        }
        y += 7;
        String explanation = creative
                ? "Exit without saving to keep the original sign, or get a sign item to edit and replace it."
                : "Exit without saving to keep the original text and styling.";
        for (var line : font.split(Component.literal(explanation), panelWidth - 24)) {
            g.text(font, line, left + 12, y, 0xFF555555, false);
            y += 11;
        }
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { exitWithoutSaving.run(); }
    @Override public boolean isPauseScreen() { return false; }
}
