package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EETheme;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.SignClickActions;
import com.dutchmtc.ee.utils.SignClickActions.Kind;
import net.minecraft.core.HolderLookup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import com.dutchmtc.ee.gui.components.EETextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.function.Consumer;

public final class GuiSignActionEditor extends Screen {
    private final Screen parent;
    private final HolderLookup.Provider registries;
    private final Consumer<ClickEvent> setter;
    private final EnumMap<Kind, SignClickActions.Draft> drafts = new EnumMap<>(Kind.class);
    private Kind kind;
    private ClickEvent candidate;
    private EditBox value, payload;
    private EEButton apply;
    private String error = "";
    private int left, top, panelWidth, panelHeight;

    public GuiSignActionEditor(Screen parent, ClickEvent event, HolderLookup.Provider registries, Consumer<ClickEvent> setter) {
        super(Component.literal(event == null ? "Add click action" : "Edit click action"));
        this.parent = parent; this.registries = registries; this.setter = setter;
        var draft = SignClickActions.describe(event, registries);
        if (draft == null) draft = new SignClickActions.Draft(Kind.COMMAND, "", "");
        kind = draft.kind(); drafts.put(kind, draft);
    }

    @Override protected void init() {
        panelWidth = Math.min(450, width - 16); panelHeight = Math.min(kind == Kind.CUSTOM ? 280 : 236, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        int buttonWidth = (panelWidth - 32) / 3;
        for (Kind option : Kind.values()) {
            int index = option.ordinal();
            EEButton type = addRenderableWidget(new EEButton(left + 12 + (index % 3) * (buttonWidth + 4),
                    top + 35 + (index / 3) * 24, buttonWidth, 20, Component.literal(option.label), b -> {
                kind = option; rebuildWidgets();
            }) {
                @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    GuiSignActionsEditor.bevel(g, getX(), getY(), getWidth(), getHeight(),
                            EETheme.controlColor(option == kind, isHoveredOrFocused(), active));
                    g.text(font, option.label, getX() + (getWidth() - font.width(option.label)) / 2, getY() + 6,
                            option == kind ? 0xFFFFFFFF : 0xFF303030, false);
                }
            });
            type.setTooltip(Tooltip.create(Component.literal(option.description)));
        }
        var draft = drafts.getOrDefault(kind, new SignClickActions.Draft(kind, "", ""));
        value = addRenderableWidget(new EETextField(font, left + 12, top + 104, panelWidth - 24, 20, Component.literal(kind.fieldLabel)));
        value.setMaxLength(32767); value.setValue(draft.value()); value.setHint(Component.literal(kind.example));
        payload = kind == Kind.CUSTOM ? addRenderableWidget(new EETextField(font, left + 12, top + 148, panelWidth - 24, 20,
                Component.literal("Optional payload (SNBT)"))) : null;
        if (payload != null) {
            payload.setMaxLength(32767); payload.setValue(draft.payload()); payload.setHint(Component.literal("{key: \"value\"}"));
            payload.setResponder(v -> validate());
        }
        value.setResponder(v -> validate());
        int half = (panelWidth - 28) / 2, footer = top + panelHeight - 26;
        addRenderableWidget(new EEButton(left + 12, footer, half, 20, Component.literal("Cancel"), b -> onClose()));
        apply = addRenderableWidget(new EEButton(left + 16 + half, footer, half, 20, Component.literal("Apply action"), b -> {
            validate();
            if (candidate != null) { setter.accept(candidate); minecraft.gui.setScreen(parent); }
        }));
        validate(); setInitialFocus(value);
    }

    private void validate() {
        var draft = new SignClickActions.Draft(kind, value.getValue(), payload == null ? "" : payload.getValue());
        drafts.put(kind, draft);
        try { candidate = SignClickActions.create(draft, registries); error = ""; }
        catch (Exception e) { candidate = null; error = e.getMessage() == null ? "Check the action details." : e.getMessage(); }
        EETextField.validate(value, error.isEmpty() ? null : error);
        if (payload != null) EETextField.validate(payload, error.isEmpty() ? null : error);
        if (apply != null) { apply.active = candidate != null; apply.setTooltip(error.isEmpty() ? null : Tooltip.create(Component.literal(error))); }
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiSignActionsEditor.panel(g, width, height, left, top, panelWidth, panelHeight);
        g.text(font, title, left + 12, top + 12, 0xFF303030, false);
        g.text(font, kind.fieldLabel, left + 12, top + 91, 0xFF303030, false);
        if (payload != null) g.text(font, "Optional payload (SNBT)", left + 12, top + 135, 0xFF303030, false);
        int y = top + (payload == null ? 136 : 178);
        for (var line : font.split(Component.literal(kind.description), panelWidth - 24)) {
            if (y + 9 > top + panelHeight - 56) break;
            g.text(font, line, left + 12, y, 0xFF626262, false); y += 11;
        }
        if (!error.isEmpty()) {
            int errorY = top + panelHeight - 52;
            var wrapped = font.split(Component.literal(error), panelWidth - 24);
            for (int i = 0; i < Math.min(2, wrapped.size()); i++)
                g.text(font, wrapped.get(i), left + 12, errorY + i * 11, EETheme.ERROR_LABEL, false);
        }
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
