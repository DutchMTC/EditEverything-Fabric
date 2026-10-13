package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.gui.selector.*;
import com.dutchmtc.ee.utils.*;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;
import java.util.function.*;

/** Shared native-preview workbench for displays and mannequins. Nested changes remain local. */
public final class GuiVisualEntityEditor extends GuiModifier<CompoundTag> {
    private final VisualEntityEditorData data;
    private final Form form;
    private final String[] tabs;
    private int tab, left, top, panelWidth, panelHeight, previewWidth, bodyTop, bodyBottom;
    private EEButton apply;
    private Entity preview;
    private net.minecraft.client.multiplayer.ClientLevel previewLevel;
    private CompoundTag previewSnapshot;
    private CompoundTag rememberedBrightness;
    private float yaw = -25, pitch = -8, zoom = 1;
    private boolean draggingPreview, formFocus;
    private final Map<String, StyledTextDocument> documents = new HashMap<>();
    private TextSelectionToolbar toolbar;
    private String transformGroup = "Position", profileMode;

    public GuiVisualEntityEditor(
            Screen parent, EntityType<?> type, CompoundTag source, Consumer<CompoundTag> setter) {
        super(parent, Component.literal(type.getDescription().getString() + " editor"), setter);
        data = new VisualEntityEditorData(type, source);
        rememberedBrightness = data.tag.getCompound("brightness").orElse(new CompoundTag()).copy();
        if (!VisualEntityEditorData.supported(type))
            throw new IllegalArgumentException("Unsupported visual entity");
        tabs =
                data.mannequin()
                        ? new String[] {"Skin", "Pose", "Layers"}
                        : new String[] {"Content", "Transform", "Rendering", "Motion"};
        form = new Form();
        var profile =
                data.read(
                        "profile",
                        net.minecraft.world.item.component.ResolvableProfile.CODEC,
                        null);
        profileMode =
                profile == null
                        ? "Default"
                        : !profile.partialProfile().properties().get("textures").isEmpty()
                                ? "Custom texture"
                                : "Player";
        if (!data.profileTag().getStringOr("texture", "").isEmpty()) profileMode = "Resource pack";
    }

    @Override
    public boolean isModified() {
        return data.changed() || form.dirtyInputs();
    }

    @Override
    protected void repositionElements() {
        init();
    }

    @Override
    protected void setInitialFocus() {}

    @Override
    public void init() {
        EditBox previous = form.getFocused() instanceof EditBox field ? field : null;
        String focusKey = previous == null ? null : previous.getMessage().getString();
        int cursor = previous == null ? 0 : previous.getCursorPosition();
        int anchor =
                previous == null
                        ? 0
                        : ((com.dutchmtc.ee.mixin.EditBoxAccessor) previous).ee$getHighlightPos();
        boolean wasFormFocus = formFocus;
        clearWidgets();
        clearFocus();
        draggingPreview = false;
        formFocus = false;
        panelWidth = Math.min(460, width - 16);
        int preferredHeight =
                data.mannequin() || data.textDisplay() || tab == 2
                        ? 350
                        : tab == 1 ? 270 : data.itemDisplay() && tab == 0 ? 250 : 232;
        panelHeight = Math.min(preferredHeight, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        bodyTop = top + 59;
        bodyBottom = top + panelHeight - 39;
        previewWidth = Math.max(72, Math.min(154, panelWidth / 3 - 14));
        if (data.mannequin()) {
            var equipment =
                    addRenderableWidget(
                            new EEButton(
                                    left + panelWidth - 108,
                                    top + 7,
                                    96,
                                    20,
                                    Component.literal("Equipment"),
                                    b -> openEquipment()));
            equipment.setTooltip(Tooltip.create(Component.literal("Edit armor and held items.")));
        }
        int w = (panelWidth - 24 - (tabs.length - 1) * 4) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            var b =
                    addRenderableWidget(
                            new EEButton(
                                    left + 12 + i * (w + 4),
                                    top + 31,
                                    w,
                                    20,
                                    Component.literal(tabs[i]),
                                    button -> {
                                        tab = index;
                                        form.resetScroll();
                                        init();
                                    }));
            b.active = i != tab;
        }
        form.embed(
                left + previewWidth + 20,
                bodyTop,
                panelWidth - previewWidth - 32,
                bodyBottom - bodyTop,
                width,
                height);
        if (focusKey != null && wasFormFocus) {
            for (var child : form.children())
                if (child instanceof EditBox field
                        && field.getMessage().getString().equals(focusKey)) {
                    field.moveCursorTo(Math.min(cursor, field.getValue().length()), false);
                    field.setHighlightPos(Math.min(anchor, field.getValue().length()));
                    form.setFocused(field);
                    formFocus = true;
                }
        }
        addRenderableWidget(
                new EEButton(
                        left + 12,
                        bodyBottom - 20,
                        previewWidth,
                        20,
                        Component.literal("Reset view"),
                        b -> {
                            yaw = -25;
                            pitch = -8;
                            zoom = 1;
                        }));
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
                                Component.literal("Apply"),
                                b -> save()));
        refreshValidity();
    }

    private static String propertyHelp(String key) {
        return switch (key) {
            case "line_width" ->
                    "Maximum line width in Minecraft font pixels; text wraps automatically.";
            case "view_range" -> "Rendering-distance multiplier. The native default is 1.";
            case "shadow_radius" -> "Size of the shadow on blocks below this display.";
            case "shadow_strength" -> "Shadow opacity from 0 (transparent) to 1 (opaque).";
            case "width", "height" ->
                    "Visibility-culling bounds in blocks. Zero keeps the default unbounded"
                            + " display.";
            case "interpolation_duration" ->
                    "Duration in ticks for later transformation changes. 20 ticks = 1 second.";
            case "start_interpolation" ->
                    "Delay in ticks before a transformation change starts interpolating.";
            case "teleport_duration" -> "Duration of movement interpolation, from 0 to 59 ticks.";
            case "background_alpha" -> "Background opacity from 0 (transparent) to 255 (opaque).";
            default -> "Enter a finite number.";
        };
    }

    private void changed() {
        previewSnapshot = null;
        refreshValidity();
    }

    private void confirmResetTransform() {
        mc.gui.setScreen(
                new GuiConfirmation(
                        this,
                        Component.literal("Reset this transformation?"),
                        Component.literal("Reset transform"),
                        () -> {
                            data.tag.remove("transformation");
                            form.clearTransformInputs();
                            changed();
                            mc.gui.setScreen(this);
                        },
                        () -> mc.gui.setScreen(this)) {
                    @Override
                    public void onClose() {
                        mc.gui.setScreen(GuiVisualEntityEditor.this);
                    }
                });
    }

    private void refreshValidity() {
        if (apply != null) {
            String error = form.error();
            apply.active = error == null;
            apply.setTooltip(error == null ? null : Tooltip.create(Component.literal(error)));
        }
    }

    private void save() {
        if (form.error() != null) return;
        setter.accept(data.tag.copy());
        mc.gui.setScreen(parent);
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
                        Component.literal("Discard these changes?"),
                        Component.literal("Discard changes"),
                        () -> mc.gui.setScreen(parent),
                        () -> mc.gui.setScreen(this)) {
                    @Override
                    public void onClose() {
                        mc.gui.setScreen(GuiVisualEntityEditor.this);
                    }
                });
    }

    @Override
    public void tick() {
        form.tick();
        if (toolbar != null) toolbar.update();
        refreshValidity();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (toolbar != null && toolbar.click(e)) return true;
        if (form.hasOpenMenu()) {
            formFocus = true;
            return form.mouseClicked(e, twice);
        }
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && inPreview(e.x(), e.y())) {
            draggingPreview = true;
            return true;
        }
        if (e.x() > left + previewWidth + 18
                && e.x() < left + panelWidth - 12
                && e.y() >= bodyTop
                && e.y() < bodyBottom) {
            formFocus = true;
            return form.mouseClicked(e, twice);
        }
        formFocus = false;
        form.clearFocus();
        return super.mouseClicked(e, twice);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (draggingPreview) {
            yaw = (float) ((yaw - dx * 2) % 360);
            pitch = Math.clamp(pitch - (float) dy, -80, 80);
            return true;
        }
        return formFocus && form.mouseDragged(e, dx, dy) || super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        draggingPreview = false;
        return form.mouseReleased(e) || super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (form.hasOpenMenu()) return form.mouseScrolled(x, y, h, v);
        if (inPreview(x, y)) {
            zoom = Math.clamp(zoom + (float) v * .1f, .2f, 4f);
            return true;
        }
        return form.mouseScrolled(x, y, h, v) || super.mouseScrolled(x, y, h, v);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE && !form.hasOpenMenu()) {
            onCancel();
            return true;
        }
        return formFocus || form.hasOpenMenu() ? form.keyPressed(e) : super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return formFocus && form.charTyped(e) || super.charTyped(e);
    }

    private boolean inPreview(double x, double y) {
        return x >= left + 12
                && x < left + 12 + previewWidth
                && y >= bodyTop
                && y < bodyBottom - 24;
    }

    private void refreshPreview() {
        if (mc.level == null) {
            preview = null;
            return;
        }
        if (previewLevel == mc.level && previewSnapshot != null && previewSnapshot.equals(data.tag))
            return;
        previewLevel = mc.level;
        previewSnapshot = data.tag.copy();
        try {
            preview =
                    data.type.create(
                            mc.level, new EntitySpawnRequest(EntitySpawnReason.LOAD, true));
            if (preview != null) {
                var visual = data.tag.copy();
                visual.remove("Passengers");
                GuiEntityPreviewUtils.loadPreviewData(preview, visual);
                GuiEntityPreviewUtils.prepareStackPreview(preview);
                if (preview instanceof Display) preview.tick();
            }
        } catch (RuntimeException e) {
            preview = null;
            com.dutchmtc.ee.EEMod.LOGGER.debug("Visual entity preview unavailable", e);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int x, int y, float dt) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float dt) {
        super.extractBackground(g, x, y, dt);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        g.text(font, title, left + 12, top + 14, EETheme.TEXT, false);
        EETheme.bevel(
                g,
                left + 12,
                bodyTop,
                previewWidth,
                bodyBottom - bodyTop - 24,
                EETheme.INPUT,
                true);
        refreshPreview();
        if (preview != null) {
            int scale =
                    Math.max(
                            1,
                            (int)
                                    (Math.min(
                                                    (previewWidth - 12)
                                                            / Math.max(.8, preview.getBbWidth()),
                                                    (bodyBottom - bodyTop - 38)
                                                            / Math.max(1, preview.getBbHeight()))
                                            * .8
                                            * zoom));
            try {
                if (preview instanceof Display display)
                    GuiEntityPreviewUtils.renderDisplayInBox(
                            g,
                            left + 14,
                            bodyTop + 2,
                            previewWidth - 4,
                            bodyBottom - bodyTop - 28,
                            zoom,
                            yaw,
                            pitch,
                            display);
                else
                    GuiEntityPreviewUtils.renderEntityInBox(
                            g,
                            left + 14,
                            bodyTop + 2,
                            previewWidth - 4,
                            bodyBottom - bodyTop - 28,
                            scale,
                            0,
                            yaw,
                            pitch,
                            preview);
            } catch (RuntimeException e) {
                preview = null;
            }
        }
        if (preview == null)
            g.text(font, "No preview", left + 18, bodyTop + 12, EETheme.LIGHT, false);
        form.extractRenderState(g, x, y, dt);
        super.extractRenderState(g, form.hasOpenMenu() ? -1 : x, form.hasOpenMenu() ? -1 : y, dt);
        if (toolbar != null && !form.hasOpenMenu()) toolbar.render(g, x, y);
    }

    private final class Form extends EESettingsScreen<CompoundTag> {
        void clearTransformInputs() {
            Predicate<String> transformKey =
                    key ->
                            key.startsWith("Position.")
                                    || key.startsWith("Scale.")
                                    || key.startsWith("Rotation.")
                                    || key.startsWith("Before scale.");
            values.keySet().removeIf(transformKey);
            errors.keySet().removeIf(transformKey);
        }

        Form() {
            super(GuiVisualEntityEditor.this, "", v -> {});
        }

        void resetScroll() {
            scroll = 0;
        }

        String error() {
            return invalidReason();
        }

        boolean dirtyInputs() {
            return !errors.isEmpty();
        }

        @Override
        protected CompoundTag result() {
            return data.tag.copy();
        }

        @Override
        protected void refreshValidity() {
            super.refreshValidity();
            GuiVisualEntityEditor.this.refreshValidity();
        }

        @Override
        protected boolean relevant(String key) {
            if (key.equals("profile.player")) return profileMode.equals("Player");
            if (key.equals("profile.custom")) return profileMode.equals("Custom texture");
            if (key.startsWith("profile.")) return profileMode.equals("Resource pack");
            return !(key.startsWith("brightness.") && !data.tag.contains("brightness"));
        }

        @Override
        protected void buildRows() {
            toolbar = null;
            if (data.mannequin()) buildMannequin();
            else buildDisplay();
        }

        void numeric(
                String label,
                String key,
                double fallback,
                double min,
                double max,
                boolean integer,
                DoubleConsumer set) {
            row(
                    label,
                    propertyHelp(key),
                    number(
                            key,
                            numberValue(key, fallback),
                            min,
                            max,
                            integer,
                            value -> {
                                set.accept(value);
                                changed();
                            }),
                    100);
        }

        String numberValue(String key, double fallback) {
            Tag value = data.tag.get(key);
            return value instanceof NumericTag n ? format(n.box().doubleValue()) : format(fallback);
        }

        void nbtNumber(
                String label,
                String key,
                double fallback,
                double min,
                double max,
                boolean integer) {
            numeric(
                    label,
                    key,
                    fallback,
                    min,
                    max,
                    integer,
                    value -> {
                        if (integer) data.tag.putInt(key, (int) value);
                        else data.tag.putFloat(key, (float) value);
                    });
        }

        void flag(String label, String key, String help, boolean fallback) {
            row(
                    label,
                    help,
                    toggle(
                            () -> data.tag.getBooleanOr(key, fallback),
                            value -> {
                                data.tag.putBoolean(key, value);
                                changed();
                            }),
                    52);
        }

        void choice(String label, String key, String fallback, String help, List<String> choices) {
            row(
                    label,
                    help,
                    dropdown(
                            friendly(data.tag.getStringOr(key, fallback)),
                            choices,
                            Form::friendly,
                            value -> {
                                data.tag.putString(key, value);
                                changed();
                            }),
                    140);
        }

        void styled(String label, String key, Component fallback) {
            var document =
                    documents.computeIfAbsent(
                            key, k -> new StyledTextDocument(data.text(key, fallback)));
            var field =
                    new StyledTextField(
                            font,
                            0,
                            0,
                            140,
                            18,
                            Component.literal(label),
                            document,
                            Math.max(32767, document.text().length()));
            field.setStyledResponder(
                    value -> {
                        data.setText(key, value);
                        changed();
                    });
            row(label, "Select text to change its color or formatting.", field, 180);
            toolbar = field.createToolbar(GuiVisualEntityEditor.this, left, left + panelWidth);
        }

        void color(String label, String key, int value, IntConsumer set) {
            var button =
                    new EEButton(
                            0,
                            0,
                            100,
                            20,
                            Component.empty(),
                            b ->
                                    mc.gui.setScreen(
                                            new GuiColorModifier(
                                                    GuiVisualEntityEditor.this,
                                                    selected -> {
                                                        set.accept(selected);
                                                        changed();
                                                    },
                                                    value))) {
                        @Override
                        protected void extractContents(
                                GuiGraphicsExtractor g, int x, int y, float dt) {
                            EETheme.bevel(
                                    g, getX(), getY(), getWidth(), 20, EETheme.CONTROL, false);
                            EETheme.rect(
                                    g,
                                    getX() + 6,
                                    getY() + 5,
                                    getWidth() - 12,
                                    10,
                                    0xFF000000 | (value & 0xFFFFFF));
                        }
                    };
            row(label, "Choose a color.", button, 100);
        }

        void buildDisplay() {
            switch (GuiVisualEntityEditor.this.tab) {
                case 0 -> {
                    if (data.type == EntityTypes.BLOCK_DISPLAY) {
                        BlockState state = data.block();
                        row(
                                "Block",
                                "Choose the displayed block.",
                                action(state.getBlock().getName().getString(), () -> chooseBlock()),
                                140);
                        var properties = action("Edit properties", () -> blockProperties());
                        properties.active = !state.getProperties().isEmpty();
                        row(
                                "Properties",
                                properties.active
                                        ? "Facing, variant and other properties of this block."
                                        : "This block has no editable properties.",
                                properties,
                                140);
                    } else if (data.itemDisplay()) {
                        ItemStack item = data.item();
                        row(
                                "Item",
                                "Choose from all items, Saved items or your inventory.",
                                action(
                                        item.isEmpty()
                                                ? "Choose item..."
                                                : item.getHoverName().getString(),
                                        () -> chooseItem()),
                                140,
                                () -> {
                                    data.item(ItemStack.EMPTY);
                                    changed();
                                    rebuild();
                                });
                        var edit =
                                action(
                                        "Edit item",
                                        () ->
                                                mc.gui.setScreen(
                                                        new GuiItemStackModifier(
                                                                GuiVisualEntityEditor.this,
                                                                data.item(),
                                                                value -> {
                                                                    data.item(value);
                                                                    changed();
                                                                })));
                        edit.active = !item.isEmpty();
                        row(
                                "Settings",
                                "Edit the displayed item's name, components and appearance.",
                                edit,
                                140);
                        choice(
                                "Item pose",
                                "item_display",
                                "none",
                                "Minecraft's native item rendering pose.",
                                Arrays.stream(ItemDisplayContext.values())
                                        .map(ItemDisplayContext::getSerializedName)
                                        .toList());
                    } else {
                        row(
                                "Text",
                                "Edit all lines with colors, gradients and formatting.",
                                action(
                                        "Edit text",
                                        () ->
                                                mc.gui.setScreen(
                                                        new GuiVisualTextEditor(
                                                                GuiVisualEntityEditor.this,
                                                                data,
                                                                value -> {
                                                                    data.setText("text", value);
                                                                    changed();
                                                                }))),
                                140);
                        nbtNumber("Line width", "line_width", 200, 1, 32767, true);
                        choice(
                                "Alignment",
                                "alignment",
                                "center",
                                "Alignment of the wrapped text.",
                                List.of("left", "center", "right"));
                        row(
                                "Opacity",
                                "Text opacity from 0 (transparent) to 255 (opaque).",
                                number(
                                        "text_opacity",
                                        Integer.toString(data.opacity()),
                                        0,
                                        255,
                                        true,
                                        value -> {
                                            data.opacity((int) value);
                                            changed();
                                        }),
                                100);
                        flag("Shadow", "shadow", "Draw the native text shadow.", false);
                        flag(
                                "See through",
                                "see_through",
                                "Allow text to remain visible through blocks.",
                                false);
                        flag(
                                "Default bg",
                                "default_background",
                                "Use Minecraft's normal text-display background.",
                                false);
                        color("Background", "background", data.background(), data::backgroundColor);
                        numeric(
                                "Bg opacity",
                                "background_alpha",
                                data.background() >>> 24,
                                0,
                                255,
                                true,
                                value -> data.backgroundOpacity((int) value));
                    }
                }
                case 1 -> {
                    row(
                            "Change",
                            "Choose the part of the transformation to edit.",
                            dropdown(
                                    transformGroup,
                                    List.of("Position", "Scale", "Rotation", "Before scale"),
                                    v -> v,
                                    value -> {
                                        transformGroup = value;
                                    }),
                            140);
                    for (int i = 0; i < 3; i++) {
                        int axis = i;
                        String label =
                                transformGroup.equals("Rotation")
                                                || transformGroup.equals("Before scale")
                                        ? new String[] {"Pitch (X)", "Yaw (Y)", "Roll (Z)"}[i]
                                        : new String[] {"X", "Y", "Z"}[i];
                        String key = transformGroup + "." + i;
                        row(
                                label,
                                transformGroup + " along this axis. Rotation uses degrees.",
                                number(
                                        key,
                                        format(data.axis(transformGroup, i)),
                                        -30000000,
                                        30000000,
                                        false,
                                        value -> {
                                            data.axis(transformGroup, axis, value);
                                            changed();
                                        }),
                                100);
                    }
                    row(
                            "Reset",
                            "Restore the native identity transformation.",
                            action(
                                    "Reset transform",
                                    GuiVisualEntityEditor.this::confirmResetTransform),
                            140);
                }
                case 2 -> {
                    choice(
                            "Face camera",
                            "billboard",
                            "fixed",
                            "Fixed holds its rotation. Vertical turns sideways, Horizontal tilts,"
                                    + " Center faces the camera on both axes.",
                            List.of("fixed", "vertical", "horizontal", "center"));
                    row(
                            "Custom light",
                            "Override world lighting for this display.",
                            toggle(
                                    () -> data.tag.contains("brightness"),
                                    value -> {
                                        if (value) {
                                            CompoundTag light = rememberedBrightness.copy();
                                            if (!light.contains("block")) light.putInt("block", 15);
                                            if (!light.contains("sky")) light.putInt("sky", 15);
                                            data.tag.put("brightness", light);
                                        } else {
                                            rememberedBrightness =
                                                    data.tag
                                                            .getCompound("brightness")
                                                            .orElse(new CompoundTag())
                                                            .copy();
                                            data.tag.remove("brightness");
                                        }
                                        changed();
                                    }),
                            52);
                    if (data.tag.contains("brightness"))
                        for (String key : List.of("block", "sky")) {
                            var light =
                                    data.tag.getCompound("brightness").orElse(new CompoundTag());
                            row(
                                    friendly(key) + " light",
                                    "Light level from 0 to 15.",
                                    number(
                                            "brightness." + key,
                                            Integer.toString(light.getIntOr(key, 15)),
                                            0,
                                            15,
                                            true,
                                            value -> {
                                                var next =
                                                        data.tag
                                                                .getCompound("brightness")
                                                                .orElse(new CompoundTag())
                                                                .copy();
                                                next.putInt(key, (int) value);
                                                data.tag.put("brightness", next);
                                                changed();
                                            }),
                                    100);
                        }
                    flag("Outline", "Glowing", "Show a glowing outline around the display.", false);
                    color(
                            "Outline color",
                            "glow_color_override",
                            data.tag.getIntOr("glow_color_override", 0xFFFFFF),
                            value -> data.tag.putInt("glow_color_override", value & 0xFFFFFF));
                    nbtNumber("View range", "view_range", 1, 0, 30000000, false);
                    nbtNumber("Shadow size", "shadow_radius", 0, 0, 64, false);
                    nbtNumber("Shadow alpha", "shadow_strength", 1, 0, 1, false);
                    nbtNumber("Bounds width", "width", 0, 0, 30000000, false);
                    nbtNumber("Bounds height", "height", 0, 0, 30000000, false);
                }
                case 3 -> {
                    nbtNumber("Transform", "interpolation_duration", 0, 0, Integer.MAX_VALUE, true);
                    nbtNumber("Start delay", "start_interpolation", 0, -1, Integer.MAX_VALUE, true);
                    nbtNumber("Move ticks", "teleport_duration", 0, 0, 59, true);
                    row(
                            "Timing",
                            "Interpolation smooths later data changes; a placed display does not"
                                    + " animate by itself.",
                            readOnly("Ticks: 20 = 1 sec"),
                            140);
                }
            }
        }

        void buildMannequin() {
            switch (GuiVisualEntityEditor.this.tab) {
                case 0 -> {
                    row(
                            "Skin source",
                            "Use a player skin, a custom Minecraft texture or resource-pack"
                                    + " textures.",
                            dropdown(
                                    profileMode,
                                    List.of("Default", "Player", "Custom texture", "Resource pack"),
                                    v -> v,
                                    value -> {
                                        profileMode = value;
                                        if (value.equals("Default")) {
                                            data.tag.remove("profile");
                                            values.keySet().removeIf(k -> k.startsWith("profile"));
                                            errors.keySet().removeIf(k -> k.startsWith("profile"));
                                            changed();
                                        }
                                    }),
                            140);
                    if (!profileMode.equals("Default")) {
                        if (profileMode.equals("Player") || profileMode.equals("Custom texture")) {
                            boolean custom = profileMode.equals("Custom texture");
                            String key = custom ? "profile.custom" : "profile.player";
                            var head = new ItemStack(Items.PLAYER_HEAD);
                            var profile =
                                    data.read(
                                            "profile",
                                            net.minecraft.world.item.component.ResolvableProfile
                                                    .CODEC,
                                            null);
                            if (profile != null) head.set(DataComponents.PROFILE, profile);
                            var helper = new HeadEditorData(head);
                            var profileInput =
                                    text(
                                            key,
                                            custom ? helper.textureInput() : helper.playerInput(),
                                            value -> {
                                                String error = HeadEditorData.error(custom, value);
                                                if (error != null) errors.put(key, error);
                                                else {
                                                    errors.remove(key);
                                                    data.profile(
                                                            HeadEditorData.profile(custom, value));
                                                    changed();
                                                }
                                            });
                            profileInput.setMaxLength(32767);
                            String profileError =
                                    HeadEditorData.error(custom, profileInput.getValue());
                            if (profileError == null) errors.remove(key);
                            else errors.put(key, profileError);
                            EETextField.validate(profileInput, profileError);
                            row(
                                    custom ? "Texture" : "Player",
                                    "Player name/UUID, or a Minecraft texture URL, hash or Base64"
                                            + " value.",
                                    profileInput,
                                    180);
                            var own =
                                    action(
                                            "Use my skin",
                                            () -> {
                                                if (mc.player != null) {
                                                    var playerProfile = mc.player.getGameProfile();
                                                    data.profile(
                                                            playerProfile
                                                                            .properties()
                                                                            .get("textures")
                                                                            .isEmpty()
                                                                    ? net.minecraft.world.item
                                                                            .component
                                                                            .ResolvableProfile
                                                                            .createUnresolved(
                                                                                    playerProfile
                                                                                            .id())
                                                                    : net.minecraft.world.item
                                                                            .component
                                                                            .ResolvableProfile
                                                                            .createResolved(
                                                                                    playerProfile));
                                                    profileMode = "Player";
                                                    values.remove("profile.player");
                                                    changed();
                                                    rebuild();
                                                }
                                            });
                            own.active = mc.player != null;
                            row(
                                    "Current player",
                                    "Use the skin of your current Minecraft account.",
                                    own,
                                    140);
                        } else
                            for (String key : List.of("texture", "cape", "elytra")) {
                                row(
                                        friendly(key),
                                        "A resource-pack texture ID; leave blank to retain the"
                                                + " profile's default.",
                                        text(
                                                "profile." + key,
                                                data.profileTag().getStringOr(key, ""),
                                                value -> {
                                                    Identifier id =
                                                            value.isBlank()
                                                                    ? null
                                                                    : Identifier.tryParse(value);
                                                    if (!value.isBlank()
                                                            && (id == null
                                                                    || id.getPath().isEmpty()))
                                                        errors.put(
                                                                "profile." + key,
                                                                "Enter a complete resource ID, or"
                                                                        + " leave blank.");
                                                    else {
                                                        errors.remove("profile." + key);
                                                        data.profileField(key, value);
                                                        changed();
                                                    }
                                                }),
                                        180);
                            }
                        row(
                                "Model",
                                "Override the skin's arm width, or use its default.",
                                dropdown(
                                        friendly(data.profileTag().getStringOr("model", "default")),
                                        List.of("default", "wide", "slim"),
                                        Form::friendly,
                                        value -> {
                                            data.profileField(
                                                    "model", value.equals("default") ? "" : value);
                                            changed();
                                        }),
                                140);
                    }
                }
                case 1 -> {
                    choice(
                            "Pose",
                            "pose",
                            "standing",
                            "The mannequin's native pose.",
                            List.of(
                                    "standing",
                                    "crouching",
                                    "swimming",
                                    "fall_flying",
                                    "sleeping"));
                    choice(
                            "Main hand",
                            "main_hand",
                            "right",
                            "Which hand is the main hand.",
                            List.of("right", "left"));
                    flag("Immovable", "immovable", "Keep the mannequin fixed in place.", false);
                    styled(
                            "Description",
                            "description",
                            Component.translatable("entity.minecraft.mannequin.label"));
                    row(
                            "Show desc.",
                            "Show the description below the mannequin name.",
                            toggle(
                                    () -> !data.tag.getBooleanOr("hide_description", false),
                                    show -> {
                                        data.tag.putBoolean("hide_description", !show);
                                        changed();
                                    }),
                            52);
                }
                case 2 -> {
                    for (String layer :
                            List.of(
                                    "hat",
                                    "jacket",
                                    "left_sleeve",
                                    "right_sleeve",
                                    "left_pants_leg",
                                    "right_pants_leg",
                                    "cape")) {
                        row(
                                friendly(layer),
                                "Show this skin layer.",
                                toggle(
                                        () -> !hiddenLayers().contains(layer),
                                        show -> {
                                            ListTag list = new ListTag();
                                            var hidden = hiddenLayers();
                                            if (show) hidden.remove(layer);
                                            else hidden.add(layer);
                                            hidden.forEach(v -> list.add(StringTag.valueOf(v)));
                                            data.tag.put("hidden_layers", list);
                                            changed();
                                        }),
                                52);
                    }
                }
            }
        }
    }

    private Set<String> hiddenLayers() {
        Set<String> result = new LinkedHashSet<>();
        data.tag
                .getList("hidden_layers")
                .ifPresent(
                        list ->
                                list.forEach(
                                        value -> {
                                            if (value instanceof StringTag s) result.add(s.value());
                                        }));
        return result;
    }

    private void chooseBlock() {
        List<Tuple<String, Block>> entries =
                BuiltInRegistries.BLOCK.stream()
                        .map(b -> new Tuple<>(b.getName().getString(), b))
                        .sorted(Comparator.comparing(entry -> entry.a))
                        .toList();
        mc.gui.setScreen(
                new GuiButtonListSelector<>(
                                this,
                                Component.literal("Choose display block"),
                                entries,
                                block -> {
                                    data.block(block.defaultBlockState());
                                    changed();
                                    return null;
                                })
                        .withCurrent(data.block().getBlock())
                        .withIcons(block -> new ItemStack(block.asItem()))
                        .withSearchKeys(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
    }

    private void chooseItem() {
        mc.gui.setScreen(
                new GuiTypeListSelector(
                        this,
                        Component.literal("Choose displayed item"),
                        item -> {
                            data.item(item);
                            changed();
                            return null;
                        },
                        data.item()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void blockProperties() {
        mc.gui.setScreen(
                new EESettingsScreen<BlockState>(
                        this,
                        "Block properties",
                        state -> {
                            data.block(state);
                            changed();
                        }) {
                    BlockState state = data.block();

                    @Override
                    protected void buildRows() {
                        for (Property property : state.getProperties()) {
                            var possible = new ArrayList<>(property.getPossibleValues());
                            row(
                                    friendly(property.getName()),
                                    "A native property of this block.",
                                    dropdown(
                                            property.getName((Comparable) state.getValue(property)),
                                            possible,
                                            v -> property.getName((Comparable) v),
                                            value -> {
                                                state =
                                                        state.setValue(
                                                                property, (Comparable) value);
                                            }),
                                    140);
                        }
                    }

                    @Override
                    protected BlockState result() {
                        return state;
                    }

                    @Override
                    public boolean isModified() {
                        return !state.equals(data.block());
                    }
                });
    }

    private void openEquipment() {
        var egg = new ItemStack(Items.BAT_SPAWN_EGG);
        egg.set(DataComponents.ENTITY_DATA, TypedEntityData.of(data.type, data.tag.copy()));
        var helper = new GuiSpawnEggModifier(this, v -> {}, egg);
        var equipment = helper.getEquipmentData(data.tag);
        var chance = helper.getEquipmentDropChances(data.tag);
        var names =
                List.of("Main hand", "Off hand", "Head", "Chest", "Legs", "Feet", "Body", "Saddle")
                        .stream()
                        .map(Component::literal)
                        .map(v -> (Component) v)
                        .toList();
        mc.gui.setScreen(
                new GuiSpawnEggEquipmentModifier(
                        this,
                        Component.literal("Mannequin equipment"),
                        value -> {
                            helper.setEquipmentData(data.tag, value.equipment());
                            changed();
                        },
                        new GuiSpawnEggEquipmentModifier.EquipmentData(equipment, chance),
                        names,
                        egg));
    }
}
