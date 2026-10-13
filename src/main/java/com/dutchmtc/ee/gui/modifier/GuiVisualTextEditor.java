package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.utils.VisualEntityEditorData;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A reusable multiline rich-text editor using the same native input and toolbar as books. */
public final class GuiVisualTextEditor extends GuiModifier<Component> {
    private final Component original;
    private final StyledTextDocument document;
    private StyledPageField field;
    private TextSelectionToolbar toolbar;
    private final EEDropdownScrollbar scrollbar = new EEDropdownScrollbar();
    private EEButton apply;
    private int left, top, panelWidth, panelHeight;
    private int lineWidth = 200, opacity = 255, background;
    private String alignment = "center";
    private boolean shadow;

    public GuiVisualTextEditor(Screen parent, Component source, Consumer<Component> setter) {
        super(parent, Component.literal("Display text"), setter);
        original = source.copy();
        document = new StyledTextDocument(source);
    }

    public GuiVisualTextEditor(
            Screen parent, VisualEntityEditorData display, Consumer<Component> setter) {
        this(parent, display.text("text", Component.empty()), setter);
        lineWidth = display.tag.getIntOr("line_width", 200);
        alignment = display.tag.getStringOr("alignment", "center");
        opacity = display.opacity();
        shadow = display.tag.getBooleanOr("shadow", false);
        background =
                display.tag.getBooleanOr("default_background", false)
                        ? 0x40000000
                        : display.background();
    }

    @Override
    protected void setInitialFocus() {}

    @Override
    protected void repositionElements() {
        init();
    }

    @Override
    public void init() {
        int cursor = field == null ? 0 : field.cursor(),
                anchor = field == null ? 0 : field.anchor(),
                scroll = field == null ? 0 : field.scrollOffset();
        clearWidgets();
        clearFocus();
        panelWidth = Math.min(500, width - 16);
        panelHeight = Math.min(300, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        field =
                addRenderableWidget(
                        new StyledPageField(
                                font,
                                left + 18,
                                top + 40,
                                panelWidth - 48,
                                panelHeight - 87,
                                document,
                                Math.max(32767, original.getString().length()),
                                Integer.MAX_VALUE,
                                true,
                                0xFFFFFFFF,
                                this::refresh));
        field.displayLayout(lineWidth, alignment, opacity, shadow, background);
        field.helper()
                .setSelectionRange(
                        Math.min(cursor, field.inputText().length()),
                        Math.min(anchor, field.inputText().length()));
        field.scrollLines(scroll);
        setFocused(field);
        toolbar =
                new TextSelectionToolbar(
                        this,
                        field,
                        document,
                        font,
                        left,
                        left + panelWidth,
                        field::refresh,
                        () -> 0xFFFFFFFF,
                        null);
        int half = (panelWidth - 28) / 2;
        addRenderableWidget(
                new EEButton(
                        left + 12,
                        top + panelHeight - 27,
                        half,
                        20,
                        Component.literal("Cancel"),
                        b -> onCancel()));
        apply =
                addRenderableWidget(
                        new EEButton(
                                left + 16 + half,
                                top + panelHeight - 27,
                                half,
                                20,
                                Component.literal("Apply text"),
                                b -> {
                                    if (field.validInput()) {
                                        setter.accept(document.component());
                                        mc.gui.setScreen(parent);
                                    }
                                }));
        refresh();
    }

    private void refresh() {
        if (apply != null) {
            apply.active = field.validInput();
            apply.setTooltip(
                    apply.active ? null : Tooltip.create(Component.literal(field.invalidReason())));
        }
        layoutScrollbar();
    }

    private void layoutScrollbar() {
        if (field != null)
            scrollbar.layout(
                    left + panelWidth - 27,
                    top + 40,
                    field.getHeight(),
                    field.visibleLines(),
                    field.lines().size(),
                    field.scrollOffset(),
                    1);
    }

    @Override
    public boolean isModified() {
        return !document.component().equals(original);
    }

    @Override
    public void onClose() {
        onCancel();
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
                        Component.literal("Discard these text changes?"),
                        Component.literal("Discard text"),
                        () -> mc.gui.setScreen(parent),
                        () -> mc.gui.setScreen(this)) {
                    @Override
                    public void onClose() {
                        mc.gui.setScreen(GuiVisualTextEditor.this);
                    }
                });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        layoutScrollbar();
        if (toolbar.click(e) || scrollbar.mouseClicked(e, field::scrollLines)) return true;
        return super.mouseClicked(e, twice);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return scrollbar.mouseDragged(e, field::scrollLines) || super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        return scrollbar.mouseReleased() || super.mouseReleased(e);
    }

    @Override
    public void tick() {
        toolbar.update();
        refresh();
        super.tick();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int x, int y, float dt) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float dt) {
        super.extractBackground(g, x, y, dt);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        g.text(font, title, left + 12, top + 14, EETheme.TEXT, false);
        EETheme.bevel(
                g, left + 12, top + 34, panelWidth - 24, panelHeight - 75, EETheme.INPUT, true);
        if (!field.validInput())
            EETheme.invalidBorder(g, left + 12, top + 34, panelWidth - 24, panelHeight - 75);
        super.extractRenderState(g, x, y, dt);
        layoutScrollbar();
        scrollbar.render(g, x, y);
        toolbar.render(g, x, y);
    }
}
