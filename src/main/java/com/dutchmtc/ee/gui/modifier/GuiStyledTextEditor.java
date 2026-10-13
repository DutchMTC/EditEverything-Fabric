package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EETheme;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.StyledTextField;
import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** General rich-text editor for names, lore and other single-line components. */
public final class GuiStyledTextEditor extends Screen {
    private final Screen parent;
    private final Consumer<Component> setter;
    private final StyledTextDocument document;
    private final String heading;
    private final int maxLength, maxPixelWidth;
    private StyledTextField textBox;
    private TextSelectionToolbar toolbar;
    private Button applyButton;
    private int left, top, panelWidth, panelHeight;
    private int inputX, inputY, inputWidth, inputHeight;

    public GuiStyledTextEditor(Screen parent, String heading, Component initial, int maxLength,
                               Consumer<Component> setter) {
        this(parent, heading, initial, maxLength, -1, setter);
    }

    public GuiStyledTextEditor(Screen parent, String heading, Component initial, int maxLength,
                               int maxPixelWidth, Consumer<Component> setter) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.setter = setter;
        this.document = new StyledTextDocument(initial);
        this.maxLength = maxLength;
        this.maxPixelWidth = maxPixelWidth;
    }

    @Override protected void init() {
        panelWidth = Math.min(510, width - 16);
        panelHeight = Math.min(156, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        inputX = left + 12;
        inputY = top + 70;
        inputWidth = panelWidth - 24;
        inputHeight = 24;
        textBox = new StyledTextField(font, inputX + 6, inputY + 2,
                inputWidth - 12, inputHeight - 4, Component.literal("Text"), document, maxLength);
        if (maxPixelWidth > 0) textBox.setPixelLimit(maxPixelWidth);
        textBox.setStyledResponder(value -> { if (applyButton != null) applyButton.active = !tooWide(); });
        addRenderableWidget(textBox);
        toolbar = textBox.createToolbar(this, inputX, inputX + inputWidth);
        int footerY = top + panelHeight - 25, half = (panelWidth - 28) / 2;
        addRenderableWidget(new EEButton(left + 12, footerY, half, 20,
                Component.literal("Cancel"), b -> minecraft.gui.setScreen(parent)));
        applyButton = addRenderableWidget(new EEButton(left + 16 + half, footerY, half, 20,
                Component.literal("Apply text"), b -> {
            if (tooWide()) return;
            setter.accept(document.component());
            minecraft.gui.setScreen(parent);
        }));
        applyButton.active = !tooWide();
    }

    private boolean tooWide() {
        return !textBox.validInput();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (toolbar.click(event)) return true;
        boolean result = super.mouseClicked(event, doubleClick);
        toolbar.update();
        return result;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        boolean result = super.mouseDragged(event, dx, dy);
        toolbar.update();
        return result;
    }

    @Override public void tick() {
        super.tick();
        toolbar.update();
        if (applyButton != null) applyButton.active = !tooWide();
        if (applyButton != null) applyButton.setTooltip(tooWide()
                ? net.minecraft.client.gui.components.Tooltip.create(Component.literal(textBox.invalidReason())) : null);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        bevel(g, left, top, panelWidth, panelHeight, 0xFFC6C6C6, false);
        g.text(font, heading, left + 12, top + 12, 0xFF303030, false);
        if (!document.legacyMode())
            g.text(font, "Select text to change its color or style", left + 12, top + 30, 0xFF626262, false);
        bevel(g, inputX, inputY, inputWidth, inputHeight, EETheme.INPUT, true);
        com.dutchmtc.ee.gui.components.EETextField.validationFrame(textBox, inputX, inputY, inputWidth, inputHeight);
        if (maxPixelWidth > 0) {
            String limit = font.width(document.editingComponent()) + " / " + maxPixelWidth + " px";
            g.text(font, limit, inputX + inputWidth - font.width(limit), inputY + inputHeight + 6,
                    tooWide() ? EETheme.ERROR_LABEL : 0xFF555555, false);
        }
        super.extractRenderState(g, mx, my, delta);
        toolbar.render(g, mx, my);
    }

    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, boolean inset) {
        EETheme.bevel(g, x, y, w, h, fill, inset);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
