package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.HeadEditorData;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Two focused ways to choose a head, with native profile resolution and an isolated draft. */
public final class GuiHeadModifier extends GuiModifier<ItemStack> {
    private final HeadEditorData draft;
    private EETextField input;
    private EEButton apply, load, export;
    private boolean custom, exporting;
    private String playerInput, textureInput, status = "";
    private int left, top, panelWidth;

    public GuiHeadModifier(Screen parent, Consumer<ItemStack> setter, ItemStack stack) {
        super(parent, Component.literal("Player head editor"), setter);
        draft = new HeadEditorData(stack);
        playerInput = draft.playerInput();
        textureInput = draft.textureInput();
        custom = !textureInput.isEmpty();
    }

    @Override
    public boolean isModified() {
        return draft.changed() || draft.pending(custom, currentInput());
    }

    private String currentInput() {
        return custom ? textureInput : playerInput;
    }

    @Override
    protected void repositionElements() {
        init();
    }

    @Override
    public void init() {
        int cursor = input == null ? currentInput().length() : input.getCursorPosition();
        int anchor = input == null ? cursor : ((EditBoxAccessor) input).ee$getHighlightPos();
        boolean focused = input != null && input.isFocused();
        clearWidgets();
        clearFocus();
        panelWidth = Math.min(320, width - 16);
        left = (width - panelWidth) / 2;
        top = (height - 214) / 2;
        int x = left + 12, w = panelWidth - 24, half = (w - 6) / 2;
        addRenderableWidget(source(x, top + 82, half, "Player", false));
        addRenderableWidget(source(x + half + 6, top + 82, w - half - 6, "Custom texture", true));
        input =
                new EETextField(
                        font,
                        x + 6,
                        top + 121,
                        w - 12,
                        18,
                        Component.literal(custom ? "Texture" : "Player")) {
                    @Override
                    public void extractWidgetRenderState(
                            GuiGraphicsExtractor g, int mx, int my, float dt) {
                        ((EditBoxAccessor) this)
                                .ee$setTextY(getY() + (getHeight() - font.lineHeight) / 2);
                        super.extractWidgetRenderState(g, mx, my, dt);
                    }
                };
        input.setBordered(false);
        input.setTextShadow(false);
        input.setTextColor(EETheme.LIGHT);
        input.setMaxLength(Math.max(32767, currentInput().length()));
        input.setValue(currentInput());
        input.setHint(
                Component.literal(
                                custom ? "Paste texture URL or value..." : "Player name or UUID...")
                        .withColor(0xAEBBB5));
        input.setTooltip(
                Tooltip.create(
                        Component.literal(
                                custom
                                        ? "Paste a textures.minecraft.net skin URL, its texture"
                                                + " hash, or a Base64 texture value."
                                        : "Enter a Minecraft player name or a full UUID. Minecraft"
                                                + " loads the skin for the preview.")));
        input.setResponder(
                value -> {
                    if (custom) textureInput = value;
                    else playerInput = value;
                    status = "";
                    refresh();
                });
        EETextField.validationFrame(input, x, top + 120, w, 20);
        addRenderableWidget(input);
        input.moveCursorTo(Math.min(cursor, currentInput().length()), false);
        input.setHighlightPos(Math.min(anchor, currentInput().length()));
        if (focused) setFocused(input);
        load =
                addRenderableWidget(
                        action(
                                x,
                                top + 147,
                                half,
                                custom ? "Preview texture" : "Preview player",
                                b -> previewInput()));
        addRenderableWidget(
                action(
                        x + half + 6,
                        top + 147,
                        w - half - 6,
                        "Use my head",
                        b -> {
                            playerInput = mc.getGameProfile().name();
                            custom = false;
                            draft.usePlayer(mc.getGameProfile());
                            status = "Your head selected";
                            input = null;
                            init();
                        }));
        export =
                addRenderableWidget(
                        action(
                                left + panelWidth - 100,
                                top + 49,
                                88,
                                "Export skin",
                                b -> exportSkin()));
        addRenderableWidget(
                new EEButton(x, top + 182, half, 20, Component.literal("Cancel"), b -> onCancel()));
        apply =
                addRenderableWidget(
                        new EEButton(
                                x + half + 6,
                                top + 182,
                                w - half - 6,
                                20,
                                Component.literal("Apply"),
                                b -> applyChanges()));
        refresh();
    }

    private EEButton source(int x, int y, int w, String text, boolean mode) {
        return new EEButton(
                x,
                y,
                w,
                20,
                Component.literal(text),
                b -> {
                    if (custom == mode) return;
                    custom = mode;
                    status = "";
                    input = null;
                    init();
                }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float dt) {
                boolean selected = custom == mode;
                EETheme.bevel(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        selected
                                ? EETheme.THUMB
                                : isHoveredOrFocused() ? EETheme.CONTROL_HOVER : EETheme.CONTROL,
                        false);
                if (selected)
                    EETheme.rect(g, getX() + 1, getBottom() - 3, getWidth() - 2, 2, EETheme.ON);
                g.text(
                        font,
                        getMessage(),
                        getX() + (getWidth() - font.width(getMessage())) / 2,
                        getY() + 6,
                        EETheme.TEXT,
                        false);
            }
        };
    }

    private EEButton action(
            int x,
            int y,
            int w,
            String text,
            net.minecraft.client.gui.components.Button.OnPress press) {
        return new EEButton(x, y, w, 20, Component.literal(text), press) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float dt) {
                EETheme.bevel(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        isHoveredOrFocused() && active ? EETheme.CONTROL_HOVER : EETheme.CONTROL,
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

    private String invalidReason() {
        return draft.pending(custom, currentInput())
                ? HeadEditorData.error(custom, currentInput())
                : null;
    }

    private void refresh() {
        String reason = invalidReason();
        EETextField.validate(input, reason);
        if (apply != null) {
            apply.active = reason == null;
            apply.setTooltip(reason == null ? null : Tooltip.create(Component.literal(reason)));
        }
        if (load != null) {
            load.active = reason == null && !currentInput().isBlank();
            load.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    reason != null
                                            ? reason
                                            : load.active
                                                    ? "Update the head preview. Apply also saves"
                                                            + " the text you entered."
                                                    : "Enter a player or texture first.")));
        }
        if (export != null) {
            export.active = !exporting && !draft.textureUrl().isEmpty();
            export.setMessage(Component.literal(exporting ? "Exporting..." : "Export skin"));
            export.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    exporting
                                            ? "Saving the skin image..."
                                            : export.active
                                                    ? "Save this head's full skin image as a PNG in"
                                                            + " your Minecraft folder."
                                                    : "Preview a custom texture to export its skin"
                                                            + " image.")));
        }
    }

    private void previewInput() {
        refresh();
        if (invalidReason() != null || currentInput().isBlank()) return;
        draft.choose(custom, currentInput());
        status = "Preview updated";
        refresh();
    }

    private void applyChanges() {
        refresh();
        if (invalidReason() != null) return;
        if (draft.pending(custom, currentInput())) draft.choose(custom, currentInput());
        if (setter != null) set(draft.result());
        mc.gui.setScreen(parent);
    }

    private void exportSkin() {
        if (exporting || draft.textureUrl().isEmpty()) return;
        String url = draft.textureUrl();
        exporting = true;
        refresh();
        CompletableFuture.runAsync(
                () -> {
                    try {
                        byte[] bytes = HeadEditorData.downloadSkin(url, mc.getProxy());
                        var path =
                                mc.gameDirectory
                                        .toPath()
                                        .resolve(
                                                "head-skin-" + System.currentTimeMillis() + ".png");
                        Files.write(path, bytes);
                        mc.execute(
                                () -> {
                                    exporting = false;
                                    status = "Skin saved to Minecraft folder";
                                    refresh();
                                });
                    } catch (Exception e) {
                        mc.execute(
                                () -> {
                                    exporting = false;
                                    status = "Could not export skin";
                                    refresh();
                                });
                    }
                });
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractBackground(g, mx, my, dt);
        EETheme.bevel(g, left, top, panelWidth, 214, EETheme.PANEL, false);
        g.text(font, title, left + 12, top + 12, EETheme.TEXT, false);
        EETheme.bevel(g, left + 12, top + 29, 48, 48, EETheme.INPUT, true);
        NativeItemPreviewState.draw(g, draft.preview(), left + 14, top + 31, 44, 44);
        g.text(font, "Head preview", left + 70, top + 33, EETheme.TEXT, false);
        g.text(
                font,
                custom ? "Texture URL or value" : "Player name or UUID",
                left + 12,
                top + 108,
                EETheme.TEXT,
                false);
        EETheme.bevel(g, left + 12, top + 120, panelWidth - 24, 20, EETheme.INPUT, true);
        g.text(
                font,
                font.plainSubstrByWidth(status, panelWidth - 24),
                left + 12,
                top + 171,
                status.startsWith("Could not") ? EETheme.ERROR_LABEL : EETheme.MUTED,
                false);
        super.extractRenderState(g, mx, my, dt);
        if (GuiUtils.isHover(left + 12, top + 29, 48, 48, mx, my))
            GuiUtils.setTooltipForNextFrame(g, font, draft.preview(), mx, my);
    }
}
