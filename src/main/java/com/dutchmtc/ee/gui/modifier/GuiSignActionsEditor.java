package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EETheme;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.SignClickActions;
import net.minecraft.core.HolderLookup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;

import java.util.function.Consumer;

/** Four ordered actions per face, matching the four root events vanilla signs execute. */
public final class GuiSignActionsEditor extends Screen {
    private final Screen parent;
    private final Consumer<SignText> setter;
    private final HolderLookup.Provider registries;
    private SignText text;
    private int left, top, panelWidth, panelHeight;

    public GuiSignActionsEditor(Screen parent, SignText text, boolean back,
                               HolderLookup.Provider registries, Consumer<SignText> setter) {
        super(Component.literal((back ? "Back" : "Front") + " · Click actions"));
        this.parent = parent; this.text = text; this.registries = registries; this.setter = setter;
    }

    @Override protected void init() {
        panelWidth = Math.min(450, width - 16); panelHeight = Math.min(290, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        int rowHeight = rowHeight();
        for (int i = 0; i < 4; i++) {
            final int slot = i;
            var event = SignClickActions.get(text, i);
            var draft = SignClickActions.describe(event, registries);
            String label = event == null ? "+ Add action" : draft == null
                    ? "Unsupported: " + event.action().getSerializedName() : draft.kind().label;
            String detail = event == null ? "" : !SignClickActions.executable(event)
                    ? "Not active on signs · edit to replace" : draft.value();
            int y = top + 51 + i * rowHeight;
            addRenderableWidget(new EEButton(left + 28, y, panelWidth - 70, rowHeight - 4,
                    Component.literal(label), b -> minecraft.gui.setScreen(new GuiSignActionEditor(this, event, registries, updated -> {
                        text = SignClickActions.set(text, slot, updated);
                    }))) {
                @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    bevel(g, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused() ? 0xFF56615B : 0xFF343B39);
                    int textY = getY() + (detail.isEmpty() ? (getHeight() - 8) / 2 : 5);
                    g.text(font, label, getX() + 8, textY, 0xFFF2F2F2, false);
                    if (!detail.isEmpty() && getHeight() >= 28)
                        g.text(font, font.plainSubstrByWidth(detail, getWidth() - 16), getX() + 8, getY() + 18, 0xFFBEC9C3, false);
                }
            });
            if (event != null) {
                EEButton remove = addRenderableWidget(new EEButton(left + panelWidth - 36, y, 24, rowHeight - 4,
                        Component.literal("×").withColor(0xFF5555), b -> {
                            text = SignClickActions.set(text, slot, null); rebuildWidgets();
                        }));
                remove.setTooltip(Tooltip.create(Component.literal("Remove action " + (i + 1))));
            }
        }
        int half = (panelWidth - 28) / 2, footer = top + panelHeight - 26;
        addRenderableWidget(new EEButton(left + 12, footer, half, 20, Component.literal("Cancel"), b -> onClose()));
        addRenderableWidget(new EEButton(left + 16 + half, footer, half, 20, Component.literal("Apply actions"), b -> {
            setter.accept(text); minecraft.gui.setScreen(parent);
        }));
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        panel(g, width, height, left, top, panelWidth, panelHeight);
        g.text(font, title, left + 12, top + 12, 0xFF303030, false);
        g.text(font, "Clicking this side runs its actions in order.", left + 12, top + 31, 0xFF626262, false);
        int rowHeight = rowHeight();
        for (int i = 0; i < 4; i++)
            g.text(font, Integer.toString(i + 1), left + 13, top + 51 + i * rowHeight + (rowHeight - 12) / 2, 0xFF626262, false);
        long dialogs = text.getMessages(false).stream().filter(c -> c.getStyle().getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.ShowDialog).count();
        drawPlacementWarning(g);
        if (dialogs > 1)
            g.text(font, "Multiple dialogs: only the last stays open.", left + 12, top + panelHeight - 39, 0xFF9E3D34, false);
        super.extractRenderState(g, mx, my, delta);
    }

    private int rowHeight() {
        return Math.min(40, (panelHeight - 139) / 4);
    }

    private void drawPlacementWarning(GuiGraphicsExtractor g) {
        int x = left + 12, y = top + panelHeight - 78, w = panelWidth - 24;
        GuiUtils.drawRect(g, x, y, x + w, y + 34, 0xFFAD6063);
        GuiUtils.drawGradientRect(g, x + 1, y + 1, x + w - 1, y + 33, 0xFF743A3E, 0xFF582C30);
        GuiUtils.drawRect(g, x + 1, y + 1, x + 4, y + 33, 0xFFEA8787);
        // Pixel triangle keeps the warning icon readable without relying on a font glyph.
        int center = x + 18, iconY = y + 9;
        for (int row = 0; row < 15; row++) {
            int half = row / 2;
            GuiUtils.drawRect(g, center - half, iconY + row, center + half + 1, iconY + row + 1, 0xFFFFD18A);
        }
        GuiUtils.drawRect(g, center, iconY + 5, center + 1, iconY + 10, 0xFF582C30);
        GuiUtils.drawRect(g, center, iconY + 12, center + 1, iconY + 13, 0xFF582C30);
        g.text(font, Component.literal("Operator permissions required").withStyle(style -> style.withBold(true)),
                x + 34, y + 6, 0xFFFFF1EE, false);
        g.text(font, "Creative mode is also required.", x + 34, y + 19, 0xFFF0C6C6, false);
    }

    static void panel(GuiGraphicsExtractor g, int width, int height, int x, int y, int w, int h) {
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        bevel(g, x, y, w, h, 0xFFC6C6C6);
    }
    static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        EETheme.bevel(g, x, y, w, h, fill, false);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
