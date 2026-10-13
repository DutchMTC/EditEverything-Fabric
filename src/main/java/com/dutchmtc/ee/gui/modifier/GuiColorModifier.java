package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EETheme;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.ColorMath;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.RecentColorHistory;
import com.dutchmtc.ee.utils.TextColorPreset;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import com.dutchmtc.ee.gui.components.EETextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;

public class GuiColorModifier extends GuiModifier<OptionalInt> {

    private enum DragState {
        SV, H, STOP, NONE
    }

    private static final int PICKER_TEXTURE_SIZE = 200;
    private static final int PICKER_HUE_TEXTURE_WIDTH = 20;
    private static final int PICKER_MIN_SIZE = 96;
    private static final int PICKER_MIN_FALLBACK_SIZE = 64;
    private static final int PICKER_MIN_HUE_WIDTH = 12;
    private static final int PICKER_GAP = 4;
    private static final int DYE_CELL_SIZE = 19;
    private static final int DYE_CELL_SMALL_SIZE = 16;
    private static final int DYE_COLUMNS = 2;
    private static final int DYE_ROWS = 8;
    private static final int RECENT_COLUMNS = 3;
    private static final int RECENT_ROWS = 5;
    private static final int RECENT_LABEL_HEIGHT = 12;
    private static final int RECENT_GAP = 8;
    private static final int PREVIEW_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final Identifier PICKER_SV_RESOURCE = Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "picker_sv");
    private static final Identifier PICKER_H_RESOURCE = Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "picker_h");

    private record PickerLayout(
            int pickerX,
            int pickerY,
            int pickerSize,
            int hueX,
            int hueY,
            int hueWidth,
            int bodyTop,
            int bodyHeight,
            int previewY,
            int dyeX,
            int dyeY,
            int dyeCellSize,
            boolean showDyes,
            int recentX,
            int recentY,
            int recentCellSize,
            boolean showRecent,
            int randomX,
            int randomY,
            int randomWidth,
            int deleteX,
            int deleteY,
            int deleteWidth,
            int buttonsX,
            int buttonsY,
            int buttonWidth,
            int buttonGap) {
    }
    
    private DynamicTexture pickerImageSV;
    private DynamicTexture pickerImageH;

    private static final int RANDOM_PICKER_FREQUENCY = 3600;

    private static ItemStack updatePicker() {
        ItemStack stack = new ItemStack(Items.POTION);
        ItemUtils.setGlobalColor(stack, GuiUtils.getTimeColor(RANDOM_PICKER_FREQUENCY, 100, 50));
        return stack;
    }

    // Saved state for defaults
    private static int savedHue;
    private static int savedSaturation;
    private static int savedValue;

    // Texture state
    private int texHue = -1;

    private void updatePickerTexture(int hue) {
        if (pickerImageSV == null || pickerImageH == null) {
            initTextures();
        }

        // regen PICKER_IMAGE_SV if hue changed
        if (hue != texHue) {
            texHue = hue;
            var pixels = Objects.requireNonNull(pickerImageSV.getPixels());
            for (var x = 0; x < pixels.getWidth(); x++) {
                for (var y = 0; y < pixels.getHeight(); y++) {
                    // x is saturation (0-100), y is value (100-0)
                    int s = x * 100 / pixels.getWidth();
                    int v = 100 - (y * 100 / pixels.getHeight());
                    var color = ColorMath.fromHsv(hue, s, v) | 0xFF000000;
                    pixels.setPixelABGR(x, y, GuiUtils.blueToRed(color));
                }
            }
            pickerImageSV.upload();
        }
        
        // PICKER_IMAGE_H is static (rainbow), but we generate it once
        // Actually we can generate it once in initTextures
    }

    public void initTextures() {
        if (pickerImageSV != null) pickerImageSV.close();
        if (pickerImageH != null) pickerImageH.close();

        pickerImageSV = new DynamicTexture(() -> EEMod.MOD_ID + "_picker_sv",
                new NativeImage(NativeImage.Format.RGBA, PICKER_TEXTURE_SIZE, PICKER_TEXTURE_SIZE, false));
        pickerImageH = new DynamicTexture(() -> EEMod.MOD_ID + "_picker_h",
                new NativeImage(NativeImage.Format.RGBA, PICKER_HUE_TEXTURE_WIDTH, PICKER_TEXTURE_SIZE, false));

        // Generate Hue texture
        var pixels = Objects.requireNonNull(pickerImageH.getPixels());
        for (var y = 0; y < pixels.getHeight(); y++) {
            int h = y * 360 / pixels.getHeight();
            var color = ColorMath.fromHsv(h, 100, 100) | 0xFF000000;
            for (var x = 0; x < pixels.getWidth(); x++) {
                pixels.setPixelABGR(x, y, GuiUtils.blueToRed(color));
            }
        }
        pickerImageH.upload();

        TextureManager tm = Minecraft.getInstance().getTextureManager();
        // Reset state to force update
        texHue = -1;
        
        tm.register(PICKER_SV_RESOURCE, pickerImageSV);
        tm.register(PICKER_H_RESOURCE, pickerImageH);
    }

    private int oldAlphaLayer;
    private final boolean transparentAsDefault;
    private int color;
    private DragState drag = DragState.NONE;
    private boolean advanced = false;
    private Button advButton, applyColor;
    private EditBox tfr, tfg, tfb, tfh, tfs, tfv, intColor, hexColor;
    private final int defaultColor;
    private int localHue;
    private int localSaturation;
    private int localValue;
    private boolean isUpdating = false;
    private final int originalColor;
    private Consumer<TextColorPreset> textSetter;
    private static final int MAX_GRADIENT_COLORS = 16;
    private static final int GRADIENT_EXTRA_HEIGHT = 34;
    private static final class GradientStop {
        private int color;
        private double position;
        private GradientStop(int color, double position) { this.color = color & 0xFFFFFF; this.position = position; }
    }
    private boolean gradientMode, gradientInitialized;
    private final List<GradientStop> gradientStops = new ArrayList<>();
    private GradientStop selectedStop;
    private Button solidButton, gradientButton;
    private Button addColorButton, removeColorButton, balanceButton;

    /** Only text editors opt in; all existing armor/potion callers remain solid-only. */
    public static GuiColorModifier forText(Screen parent, Consumer<TextColorPreset> setter, int initial) {
        GuiColorModifier picker = new GuiColorModifier(parent, ignored -> {}, initial, initial);
        picker.textSetter = setter;
        picker.gradientStops.add(new GradientStop(initial, 0));
        picker.gradientStops.add(new GradientStop(0xFFFFFF, 1));
        picker.selectedStop = picker.gradientStops.getFirst();
        return picker;
    }

    private TextColorPreset selectedPreset() {
        return gradientMode ? new TextColorPreset(gradientStops.stream()
                .map(s -> new TextColorPreset.Stop(s.color, s.position)).toList()) : TextColorPreset.solid(color);
    }

    private void selectPreset(TextColorPreset preset) {
        gradientMode = textSetter != null && preset.gradient();
        if (gradientMode) {
            gradientStops.clear();
            for (var stop : preset.stops()) gradientStops.add(new GradientStop(stop.color(), stop.position()));
            selectedStop = gradientStops.getFirst();
            gradientInitialized = true;
        }
        updateColor(preset.start());
        rebuildWidgets();
    }

    private void updateModeButtons() {
        if (solidButton == null) return;
        solidButton.active = gradientMode;
        gradientButton.active = !gradientMode;
        updateGradientControls();
    }

    private boolean showAuxiliaryControls() { return !gradientMode || height >= 280; }
    private int gradientWidth(PickerLayout layout) {
        int start = layout.showDyes() ? layout.dyeX() : layout.pickerX();
        int end = layout.showRecent() ? layout.recentX() + RECENT_COLUMNS * layout.recentCellSize() : layout.hueX() + layout.hueWidth();
        return Math.min(width - 16, Math.max(260, end - start));
    }
    private int gradientX(PickerLayout layout) { return (width - gradientWidth(layout)) / 2; }
    private int stopX(PickerLayout layout, GradientStop stop) {
        return gradientX(layout) + 5 + (int) Math.round(stop.position * (gradientWidth(layout) - 11));
    }
    private void updateGradientControls() {
        if (addColorButton == null) return;
        addColorButton.visible = removeColorButton.visible = balanceButton.visible = gradientMode;
        addColorButton.active = gradientStops.size() < MAX_GRADIENT_COLORS;
        removeColorButton.active = gradientStops.size() > 2;
        removeColorButton.setTooltip(Tooltip.create(Component.literal(removeColorButton.active
                ? "Remove the selected color" : "A gradient needs at least two colors.")));
    }
    private void addStop(double position) {
        if (gradientStops.size() >= MAX_GRADIENT_COLORS) return;
        int rgb = selectedPreset().colorAt(position);
        selectedStop = new GradientStop(rgb, position);
        gradientStops.add(selectedStop);
        gradientStops.sort(Comparator.comparingDouble(s -> s.position));
        updateColor(rgb);
        updateGradientControls();
    }
    private void addStop() {
        double start = 0, end = gradientStops.getFirst().position, gap = end;
        for (int i = 1; i < gradientStops.size(); i++) {
            double left = gradientStops.get(i - 1).position, right = gradientStops.get(i).position;
            if (right - left > gap) { start = left; end = right; gap = right - left; }
        }
        if (1 - gradientStops.getLast().position > gap) { start = gradientStops.getLast().position; end = 1; }
        addStop((start + end) / 2);
    }
    private void removeStop() {
        if (gradientStops.size() <= 2) return;
        int index = gradientStops.indexOf(selectedStop);
        gradientStops.remove(selectedStop);
        selectedStop = gradientStops.get(Math.min(index, gradientStops.size() - 1));
        updateColor(selectedStop.color);
        updateGradientControls();
    }
    private void balanceStops() {
        for (int i = 0; i < gradientStops.size(); i++) gradientStops.get(i).position = i / (double) (gradientStops.size() - 1);
    }

    private void drawPreset(GuiGraphicsExtractor g, TextColorPreset preset, int x, int y, int w, int h) {
        for (int i = 0; i < w; i++)
            GuiUtils.drawRect(g, x + i, y, x + i + 1, y + h,
                    0xFF000000 | preset.colorAt(w < 2 ? 0 : i / (double) (w - 1)));
    }

    private PickerLayout createLayout() {
        int marginX = 8;
        int topPadding = 8;
        int bottomPadding = 8;
        int previewGap = 4;
        int afterMainGap = 8;
        int beforeButtonsGap = 8;
        int dyeGap = 4;
        int buttonGap = 5;
        int gradientHeight = gradientMode ? GRADIENT_EXTRA_HEIGHT : 0;
        int auxiliaryHeight = showAuxiliaryControls() ? afterMainGap + ROW_HEIGHT : 0;

        int modeHeight = textSetter == null ? 0 : 26;
        int fixedHeight = modeHeight + gradientHeight + topPadding + bottomPadding + PREVIEW_HEIGHT + previewGap + auxiliaryHeight + beforeButtonsGap
                + BUTTON_HEIGHT;
        int bodyBudget = Math.max(24, height - fixedHeight);

        int dyeCellSize = bodyBudget >= DYE_ROWS * DYE_CELL_SIZE ? DYE_CELL_SIZE : DYE_CELL_SMALL_SIZE;
        int dyeHeight = DYE_ROWS * dyeCellSize;
        boolean showDyes = dyeHeight <= bodyBudget;

        int recentCellSize = GuiUtils.clamp((bodyBudget - RECENT_LABEL_HEIGHT) / RECENT_ROWS, 12, 18);
        int recentHeight = RECENT_LABEL_HEIGHT + RECENT_ROWS * recentCellSize;
        boolean showRecent = recentHeight <= bodyBudget;
        int recentWidth = showRecent ? RECENT_COLUMNS * recentCellSize : 0;
        int currentRecentGap = showRecent ? RECENT_GAP : 0;

        int pickerSize = Math.min(PICKER_TEXTURE_SIZE, bodyBudget);
        int hueWidth = GuiUtils.clamp(pickerSize / 10, PICKER_MIN_HUE_WIDTH, PICKER_HUE_TEXTURE_WIDTH);
        int dyeWidth = showDyes ? DYE_COLUMNS * dyeCellSize : 0;
        int currentDyeGap = showDyes ? dyeGap : 0;
        int pickerByWidth = width - marginX * 2 - dyeWidth - currentDyeGap - PICKER_GAP - hueWidth
                - currentRecentGap - recentWidth;
        pickerSize = Math.min(pickerSize, pickerByWidth);

        if (showDyes && pickerSize < PICKER_MIN_SIZE) {
            showDyes = false;
            dyeWidth = 0;
            currentDyeGap = 0;
            pickerByWidth = width - marginX * 2 - PICKER_GAP - hueWidth - currentRecentGap - recentWidth;
            pickerSize = Math.min(Math.min(PICKER_TEXTURE_SIZE, bodyBudget), pickerByWidth);
        }

        pickerSize = Math.max(24, pickerSize);
        hueWidth = GuiUtils.clamp(pickerSize / 10, PICKER_MIN_HUE_WIDTH, PICKER_HUE_TEXTURE_WIDTH);
        pickerByWidth = width - marginX * 2 - dyeWidth - currentDyeGap - PICKER_GAP - hueWidth
                - currentRecentGap - recentWidth;
        pickerSize = Math.max(24, Math.min(pickerSize, pickerByWidth));
        if (pickerSize < PICKER_MIN_FALLBACK_SIZE && showRecent) {
            showRecent = false;
            recentWidth = 0;
            currentRecentGap = 0;
            pickerByWidth = width - marginX * 2 - dyeWidth - currentDyeGap - PICKER_GAP - hueWidth;
            pickerSize = Math.max(24, Math.min(Math.min(PICKER_TEXTURE_SIZE, bodyBudget), pickerByWidth));
        }
        if (pickerSize < PICKER_MIN_FALLBACK_SIZE && showDyes) {
            showDyes = false;
            dyeWidth = 0;
            currentDyeGap = 0;
            pickerByWidth = width - marginX * 2 - PICKER_GAP - hueWidth - currentRecentGap - recentWidth;
            pickerSize = Math.max(24, Math.min(Math.min(PICKER_TEXTURE_SIZE, bodyBudget), pickerByWidth));
        }

        dyeHeight = showDyes ? DYE_ROWS * dyeCellSize : 0;
        int bodyHeight = Math.max(Math.max(pickerSize, dyeHeight), showRecent ? recentHeight : 0);
        if (bodyHeight > bodyBudget) {
            showDyes = false;
            dyeWidth = 0;
            currentDyeGap = 0;
            dyeHeight = 0;
            bodyHeight = Math.min(Math.max(pickerSize, showRecent ? recentHeight : 0), bodyBudget);
            pickerSize = bodyHeight;
            hueWidth = GuiUtils.clamp(Math.max(1, pickerSize / 10), PICKER_MIN_HUE_WIDTH, PICKER_HUE_TEXTURE_WIDTH);
        }

        int contentHeight = modeHeight + gradientHeight + PREVIEW_HEIGHT + previewGap + bodyHeight + auxiliaryHeight + beforeButtonsGap + BUTTON_HEIGHT;
        int availableHeight = height - topPadding - bottomPadding;
        int contentTop = topPadding + Math.max(0, (availableHeight - contentHeight) / 2);
        int bodyTop = contentTop + modeHeight + PREVIEW_HEIGHT + gradientHeight + previewGap;

        int pickerWidth = advanced ? Math.max(pickerSize, Math.min(184,
                width - marginX * 2 - dyeWidth - currentDyeGap - PICKER_GAP - hueWidth - currentRecentGap - recentWidth)) : pickerSize;
        int contentWidth = dyeWidth + currentDyeGap + pickerWidth + PICKER_GAP + hueWidth
                + currentRecentGap + recentWidth;
        int contentLeft = (width - contentWidth) / 2;
        int pickerX = contentLeft + dyeWidth + currentDyeGap;
        int pickerY = bodyTop + (bodyHeight - pickerSize) / 2;
        int hueX = pickerX + pickerWidth + PICKER_GAP;
        int hueY = pickerY;
        int dyeX = contentLeft;
        int dyeY = bodyTop + (bodyHeight - dyeHeight) / 2;
        int recentX = hueX + hueWidth + currentRecentGap;
        int recentY = bodyTop + (bodyHeight - recentHeight) / 2;

        int randomX = pickerX;
        int randomY = bodyTop + bodyHeight + afterMainGap;
        int randomWidth = 38;
        int deleteWidth = 40;
        int deleteX = hueX + hueWidth - deleteWidth;
        int deleteY = randomY;

        int buttonWidth = GuiUtils.clamp((width - marginX * 2 - buttonGap * 2) / 3, 58, 90);
        int buttonsX = (width - (buttonWidth * 3 + buttonGap * 2)) / 2;
        int buttonsY = bodyTop + bodyHeight + auxiliaryHeight + beforeButtonsGap;

        return new PickerLayout(pickerX, pickerY, pickerSize, hueX, hueY, hueWidth, bodyTop, bodyHeight, contentTop + modeHeight,
                dyeX, dyeY, dyeCellSize, showDyes, recentX, recentY, recentCellSize, showRecent,
                randomX, randomY, randomWidth, deleteX, deleteY, deleteWidth,
                buttonsX, buttonsY, buttonWidth, buttonGap);
    }

    private void applyAdvancedFieldLayout(PickerLayout layout) {
        if (tfr == null || tfg == null || tfb == null || tfh == null || tfs == null || tfv == null || intColor == null || hexColor == null) {
            return;
        }

        int panelLeft = layout.pickerX();
        int panelRight = layout.hueX() + layout.hueWidth();
        int panelWidth = panelRight - panelLeft;
        int fieldWidth = GuiUtils.clamp((panelWidth - 84) / 2, 40, 64);
        int rgbX = panelLeft + 38;
        int hsvX = panelRight - fieldWidth - 6;
        int rowStart = layout.bodyTop() + Math.max(14, (layout.bodyHeight() - 102) / 2);
        int rowGap = 24;

        tfr.setX(rgbX);
        tfg.setX(rgbX);
        tfb.setX(rgbX);
        tfh.setX(hsvX);
        tfs.setX(hsvX);
        tfv.setX(hsvX);

        tfr.setY(rowStart);
        tfg.setY(rowStart + rowGap);
        tfb.setY(rowStart + rowGap * 2);
        tfh.setY(rowStart);
        tfs.setY(rowStart + rowGap);
        tfv.setY(rowStart + rowGap * 2);

        tfr.setWidth(fieldWidth);
        tfg.setWidth(fieldWidth);
        tfb.setWidth(fieldWidth);
        tfh.setWidth(fieldWidth);
        tfs.setWidth(fieldWidth);
        tfv.setWidth(fieldWidth);

        int intHexGap = 8;
        int intHexWidth = Math.max(42, (panelWidth - intHexGap) / 2);
        int intHexY = rowStart + rowGap * 3 + 8;
        intColor.setX(panelLeft);
        intColor.setY(intHexY);
        intColor.setWidth(intHexWidth);
        hexColor.setX(panelRight - intHexWidth);
        hexColor.setY(intHexY);
        hexColor.setWidth(intHexWidth);
    }

    public GuiColorModifier(Screen parent, Consumer<Integer> setter, int color) {
        this(parent, cd -> {
            if (cd.isPresent()) {
                setter.accept(cd.getAsInt());
            }
        }, OptionalInt.of(color), 0xa06540, false);
    }

    public GuiColorModifier(Screen parent, Consumer<Integer> setter, int color, int defaultColor) {
        this(parent, cd -> {
            if (cd.isPresent()) {
                setter.accept(cd.getAsInt());
            }
        }, OptionalInt.of(color), defaultColor, false);
    }

    public GuiColorModifier(Screen parent, Consumer<OptionalInt> setter, OptionalInt color,
                            boolean transparentAsDefault) {
        this(parent, setter, color, color.orElse(0), transparentAsDefault);
    }

    public GuiColorModifier(Screen parent, Consumer<OptionalInt> setter, OptionalInt color, int defaultColor,
                            boolean transparentAsDefault) {
        super(parent, Component.translatable("gui.ee.modifier.meta.setColor"), setter);
        var rgba = color.orElse(defaultColor);
        this.color = rgba & 0xFFFFFF; // remove alpha
        this.oldAlphaLayer = rgba & 0xFF000000;
        if (transparentAsDefault && color.isEmpty())
            this.color |= 0xFF000000;
        this.originalColor = this.color;
        this.defaultColor = defaultColor;
        this.transparentAsDefault = transparentAsDefault;
        
        var hsv = ColorMath.toHsv(this.color);
        localHue = hsv[0];
        localSaturation = hsv[1];
        localValue = hsv[2];
        
        // If color is black/white/gray, hue is undefined (0), but we might want to keep saved hue
        if (localSaturation == 0) {
            localHue = savedHue;
        }
    }

    @Override
    public boolean isModified() {
        return gradientMode || color != originalColor;
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // allow multiple color modifiers
        updatePickerTexture(localHue);
        PickerLayout layout = createLayout();
        applyAdvancedFieldLayout(layout);

        super.extractBackground(graphics, mouseX, mouseY, partialTicks);

        if (!advanced) {
            // SV PICKER
            graphics.blit(RenderPipelines.GUI_TEXTURED, PICKER_SV_RESOURCE,
                    layout.pickerX(), layout.pickerY(),
                    0.0F, 0.0F,
                    layout.pickerSize(), layout.pickerSize(),
                    layout.pickerSize(), layout.pickerSize());

            // SV Index
            int sX = layout.pickerX() + (localSaturation * layout.pickerSize() / 100);
            int vY = layout.pickerY() + layout.pickerSize() - (localValue * layout.pickerSize() / 100);
            
            GuiUtils.drawRect(graphics, sX - 2, vY - 2, sX + 2, vY + 2, 0xff222222);
            GuiUtils.drawRect(graphics, sX - 1, vY - 1, sX + 1, vY + 1, 0xffcccccc);

            // Hue Picker
            graphics.blit(RenderPipelines.GUI_TEXTURED, PICKER_H_RESOURCE,
                    layout.hueX(), layout.hueY(),
                    0.0F, 0.0F,
                    layout.hueWidth(), layout.pickerSize(),
                    layout.hueWidth(), layout.pickerSize());

            // Hue Index
            int hY = layout.hueY() + (localHue * layout.pickerSize() / 360);
            GuiUtils.drawRect(graphics, layout.hueX() - 2, hY - 2, layout.hueX() + layout.hueWidth() + 2, hY + 2, 0xff222222);
            GuiUtils.drawRect(graphics, layout.hueX() - 1, hY - 1, layout.hueX() + layout.hueWidth() + 1, hY + 1, 0xffcccccc);

        } else {
            int panelLeft = layout.pickerX() - 6;
            int panelRight = layout.hueX() + layout.hueWidth() + 6;
            graphics.pose().pushMatrix();
            GuiUtils.drawRect(graphics, panelLeft, layout.bodyTop(), panelRight, layout.bodyTop() + layout.bodyHeight(),
                    0xE0000000);
            graphics.pose().popMatrix();
            graphics.text(font, "RGB", tfr.getX(), tfr.getY() - 14, 0xFFFFFFFF, false);
            graphics.text(font, "HSV", tfh.getX(), tfh.getY() - 14, 0xFFFFFFFF, false);
            GuiUtils.drawRightString(graphics, font, "R: ", tfr, 0xffffffff);
            GuiUtils.drawRightString(graphics, font, "G: ", tfg, 0xffffffff);
            GuiUtils.drawRightString(graphics, font, "B: ", tfb, 0xffffffff);

            GuiUtils.drawRightString(graphics, font, "H: ", tfh, 0xffffffff);
            GuiUtils.drawRightString(graphics, font, "S: ", tfs,
                    0xffffffff);
            GuiUtils.drawRightString(graphics, font, "V: ", tfv,
                    0xffffffff);

            GuiUtils.text(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.intColor") + ":", intColor.getX(),
                    intColor.getY() - 4 - 10, 0xffffffff, 10);
            GuiUtils.text(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.hexColor") + ":", hexColor.getX(),
                    hexColor.getY() - 4 - 10, 0xffffffff, 10);
        }
        
        // Current color preview
        if ((color & 0xFF000000) == 0) {
            int previewWidth = layout.hueX() + layout.hueWidth() - layout.pickerX();
            if (gradientMode) {
                int trackX = gradientX(layout) + 5, trackWidth = gradientWidth(layout) - 10;
                drawPreset(graphics, selectedPreset(), trackX, layout.previewY(), trackWidth, PREVIEW_HEIGHT);
                for (GradientStop stop : gradientStops) if (stop != selectedStop) drawStop(graphics, layout, stop);
                drawStop(graphics, layout, selectedStop);
                String label = "Color " + (gradientStops.indexOf(selectedStop) + 1) + " / " + gradientStops.size()
                        + " · " + Math.round(selectedStop.position * 100) + "%";
                graphics.text(font, label, gradientX(layout), layout.previewY() + 39, 0xFFFFFFFF, false);
            } else drawPreset(graphics, selectedPreset(), layout.pickerX(), layout.previewY(), previewWidth, PREVIEW_HEIGHT);
        }

        if (textSetter == null && Minecraft.getInstance().player != null) {
            ItemStack stack = Minecraft.getInstance().player.getMainHandItem().copy();
            if (ItemUtils.canGlobalColorIt(stack)) {
                ItemUtils.setGlobalColor(stack, color);
                int previewX = layout.pickerX() + (layout.hueX() + layout.hueWidth() - layout.pickerX()) / 2 - 8;
                int previewY = layout.previewY() + (PREVIEW_HEIGHT - 16) / 2;
                GuiUtils.drawItemStack(graphics, stack, previewX, previewY);
            }
        }

        Runnable show = () -> {
        };
        
        // Dye colors
        if (layout.showDyes()) {
            for (var i = 0; i < DyeColor.values().length; ++i) {
                var dyeColor = DyeColor.values()[i];
                var x = layout.dyeX() + (i % DYE_COLUMNS) * layout.dyeCellSize();
                var y = layout.dyeY() + (i / DYE_COLUMNS) * layout.dyeCellSize();

                GuiUtils.drawRect(graphics, x, y, x + layout.dyeCellSize(), y + layout.dyeCellSize(),
                        0xff000000 | dyeColor.getFireworkColor());
                if (GuiUtils.isHover(x, y, layout.dyeCellSize(), layout.dyeCellSize(), mouseX, mouseY)) {
                    show = () -> GuiUtils.drawTextBox(graphics, font, mouseX, mouseY, width, height, getZLevel(),
                            I18n.get("item.minecraft.firework_star." + dyeColor.getName()));
                }
                GuiUtils.drawItemStack(graphics, new ItemStack(dyeItem(dyeColor)),
                        x + (layout.dyeCellSize() - 16) / 2, y + (layout.dyeCellSize() - 16) / 2);
            }
        }

        if (layout.showRecent()) {
            GuiUtils.text(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.recent"),
                    layout.recentX(), layout.recentY(), 0xFFFFFFFF);
            List<TextColorPreset> recentColors = RecentColorHistory.shared().presets(textSetter != null);
            for (int i = 0; i < RecentColorHistory.LIMIT; i++) {
                int x = layout.recentX() + i % RECENT_COLUMNS * layout.recentCellSize();
                int y = layout.recentY() + RECENT_LABEL_HEIGHT + i / RECENT_COLUMNS * layout.recentCellSize();
                boolean hover = GuiUtils.isHover(x, y, layout.recentCellSize(), layout.recentCellSize(), mouseX, mouseY);
                boolean filled = i < recentColors.size();
                GuiUtils.drawRect(graphics, x, y, x + layout.recentCellSize(), y + layout.recentCellSize(),
                        filled && hover ? 0xFFFFFFFF : 0xFF242424);
                drawPreset(graphics, filled ? recentColors.get(i) : TextColorPreset.solid(0x555555),
                        x + 2, y + 2, layout.recentCellSize() - 4, layout.recentCellSize() - 4);
                if (filled && hover) {
                    TextColorPreset preset = recentColors.get(i);
                    show = () -> graphics.setTooltipForNextFrame(font,
                            font.split(Component.literal(preset.label()), Math.min(240, width - 24)), mouseX, mouseY);
                }
            }
        }

        if (gradientMode && GuiUtils.isHover(gradientX(layout), layout.previewY(), gradientWidth(layout), 31, mouseX, mouseY)) {
            show = () -> graphics.setTooltipForNextFrame(font, font.split(Component.literal(
                    "Select a stop to edit its color. Drag it to move it. Click overlapping stops to cycle between them."),
                    Math.min(240, width - 24)), mouseX, mouseY);
        }

        if (showAuxiliaryControls()) {
            // Random color and reset the selected color.
            EETheme.bevel(graphics, layout.randomX(), layout.randomY(), layout.randomWidth(), ROW_HEIGHT,
                    EETheme.controlColor(false, GuiUtils.isHover(layout.randomX(), layout.randomY(), layout.randomWidth(), ROW_HEIGHT, mouseX, mouseY), true), false);
            GuiUtils.drawItemStack(graphics, updatePicker(), layout.randomX() + layout.randomWidth() / 2 - 16 / 2,
                    layout.randomY() + ROW_HEIGHT / 2 - 16 / 2);
            if (GuiUtils.isHover(layout.randomX(), layout.randomY(), layout.randomWidth(), ROW_HEIGHT, mouseX, mouseY)) {
                show = () -> GuiUtils.drawTextBox(graphics, font, mouseX, mouseY, width, height, getZLevel(),
                        I18n.get("gui.ee.modifier.meta.setColor.random"));
            }

            EETheme.bevel(graphics, layout.deleteX(), layout.deleteY(), layout.deleteWidth(), ROW_HEIGHT,
                    EETheme.controlColor(false, GuiUtils.isHover(layout.deleteX(), layout.deleteY(), layout.deleteWidth(), ROW_HEIGHT, mouseX, mouseY), true), false);
            graphics.text(font, "Undo", layout.deleteX() + (layout.deleteWidth() - font.width("Undo")) / 2,
                    layout.deleteY() + (ROW_HEIGHT - font.lineHeight) / 2 + 1, EETheme.TEXT, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

        setZLever(getZLevel() + 75);
        show.run();
        setZLever(getZLevel() - 75);
    }

    private void drawStop(GuiGraphicsExtractor g, PickerLayout layout, GradientStop stop) {
        int x = stopX(layout, stop), y = layout.previewY() + PREVIEW_HEIGHT;
        GuiUtils.drawRect(g, x - 5, y, x + 6, y + 11, stop == selectedStop ? 0xFFFFFFFF : 0xFF242424);
        GuiUtils.drawRect(g, x - 3, y + 2, x + 4, y + 9, 0xFF000000 | stop.color);
    }

    private void complete() {
        if (textSetter != null) {
            TextColorPreset preset = selectedPreset();
            textSetter.accept(preset);
            RecentColorHistory.shared().record(preset);
            return;
        }
        OptionalInt selected = (color & 0xFF000000) != 0
                ? OptionalInt.empty() : OptionalInt.of(color | oldAlphaLayer);
        set(selected);
        if (selected.isPresent()) RecentColorHistory.shared().record(selected.getAsInt());
    }

    @Override
    public void init() {
        String[] invalidDrafts = new String[8];
        EditBox[] oldFields = {tfr,tfg,tfb,tfh,tfs,tfv,intColor,hexColor};
        for (int i=0; i<oldFields.length; i++) if (oldFields[i] != null && EETextField.error(oldFields[i]) != null) invalidDrafts[i] = oldFields[i].getValue();
        initTextures();
        PickerLayout layout = createLayout();

        if (textSetter != null) {
            int modeWidth = layout.hueX() + layout.hueWidth() - layout.pickerX();
            solidButton = addRenderableWidget(new EEButton(layout.pickerX(), layout.previewY() - 26,
                    (modeWidth - 4) / 2, 20, Component.literal("Solid"), b -> {
                gradientMode = false;
                rebuildWidgets();
            }));
            gradientButton = addRenderableWidget(new EEButton(layout.pickerX() + (modeWidth + 4) / 2,
                    layout.previewY() - 26, (modeWidth - 4) / 2, 20, Component.literal("Gradient"), b -> {
                gradientMode = true;
                if (!gradientInitialized) { gradientStops.getFirst().color = color; gradientInitialized = true; }
                updateColor(selectedStop.color);
                rebuildWidgets();
            }));
            int right = gradientX(layout) + gradientWidth(layout), controlsY = layout.previewY() + 34;
            addColorButton = addRenderableWidget(new EEButton(right - 162, controlsY, 52, 20,
                    Component.literal("+ Color"), b -> addStop()));
            addColorButton.setTooltip(Tooltip.create(Component.literal("Add a color stop in the largest gap. Up to 16 colors.")));
            removeColorButton = addRenderableWidget(new EEButton(right - 106, controlsY, 20, 20,
                    Component.literal("×").withColor(0xFF5555), b -> removeStop()));
            balanceButton = addRenderableWidget(new EEButton(right - 82, controlsY, 82, 20,
                    Component.literal("Space evenly"), b -> balanceStops()));
            balanceButton.setTooltip(Tooltip.create(Component.literal("Spread every color evenly, keeping their current order.")));
            updateModeButtons();
        }

        addRenderableWidget(
                new EEButton(layout.buttonsX(), layout.buttonsY(), layout.buttonWidth(), BUTTON_HEIGHT,
                        Component.translatable("gui.ee.cancel"), b -> onCancel()));
        advButton = addRenderableWidget(new EEButton(layout.buttonsX() + layout.buttonWidth() + layout.buttonGap(),
                layout.buttonsY(), layout.buttonWidth(), BUTTON_HEIGHT,
                Component.translatable(advanced ? "gui.ee.modifier.meta.setColor.picker" : "gui.ee.advanced"), b -> {
            advanced ^= true;
            rebuildWidgets();
        }));
        applyColor = addRenderableWidget(
                new EEButton(layout.buttonsX() + 2 * (layout.buttonWidth() + layout.buttonGap()), layout.buttonsY(),
                        layout.buttonWidth(), BUTTON_HEIGHT, Component.translatable("gui.done"), b -> {
                    if (!validFields()) return;
                    complete();
                    getMinecraft().gui.setScreen(parent);
                }));

        // Advanced fields
        tfr = new EETextField(font, 0, 0, 45, 18, Component.literal(""));
        tfg = new EETextField(font, 0, 0, 45, 18, Component.literal(""));
        tfb = new EETextField(font, 0, 0, 45, 18, Component.literal(""));

        tfh = new EETextField(font, 0, 0, 45, 18, Component.literal(""));
        tfs = new EETextField(font, 0, 0, 45, 18, Component.literal(""));
        tfv = new EETextField(font, 0, 0, 45, 18, Component.literal(""));

        intColor = new EETextField(font, 0, 0, 85, 18, Component.literal(""));
        hexColor = new EETextField(font, 0, 0, 85, 18, Component.literal(""));

        tfr.setMaxLength(4);
        tfg.setMaxLength(4);
        tfb.setMaxLength(4);
        tfh.setMaxLength(4);
        tfs.setMaxLength(4);
        tfv.setMaxLength(4);
        tfr.setTooltip(Tooltip.create(Component.literal("Red · 0–255")));
        tfg.setTooltip(Tooltip.create(Component.literal("Green · 0–255")));
        tfb.setTooltip(Tooltip.create(Component.literal("Blue · 0–255")));
        tfh.setTooltip(Tooltip.create(Component.literal("Hue · 0–359°")));
        tfs.setTooltip(Tooltip.create(Component.literal("Saturation · 0–100%")));
        tfv.setTooltip(Tooltip.create(Component.literal("Brightness · 0–100%")));

        tfr.setResponder(s -> channel(tfr, s, 255, "Red", this::updateRed));
        tfg.setResponder(s -> channel(tfg, s, 255, "Green", this::updateGreen));
        tfb.setResponder(s -> channel(tfb, s, 255, "Blue", this::updateBlue));
        tfh.setResponder(s -> channel(tfh, s, 359, "Hue", this::updateHue));
        tfs.setResponder(s -> channel(tfs, s, 100, "Saturation", this::updateSaturation));
        tfv.setResponder(s -> channel(tfv, s, 100, "Brightness", this::updateValue));
        intColor.setResponder(s -> channel(intColor, s, 0xFFFFFF, "Color", this::updateColor));
        hexColor.setResponder(s -> {
            if (isUpdating) return;
            String raw = s.startsWith("#") ? s.substring(1) : s;
            if (raw.matches("[0-9a-fA-F]{6}")) updateColor(Integer.parseInt(raw, 16));
            else EETextField.validate(hexColor, "Enter six hexadecimal digits, such as #62B897.");
            refreshFields();
        });
        addRenderableWidget(tfr);
        addRenderableWidget(tfg);
        addRenderableWidget(tfb);
        addRenderableWidget(tfh);
        addRenderableWidget(tfs);
        addRenderableWidget(tfv);
        addRenderableWidget(intColor);
        addRenderableWidget(hexColor);

        applyAdvancedFieldLayout(layout);
        updateControlsVisibility();
        // Synchronizing fields must retain the automatic-color marker. Only a user's
        // color choice should turn an unset color into an explicit override.
        int draftColor = color;
        updateColor(color);
        color = draftColor;
        EditBox[] newFields = {tfr,tfg,tfb,tfh,tfs,tfv,intColor,hexColor};
        for (int i=0; i<newFields.length; i++) if (invalidDrafts[i] != null) newFields[i].setValue(invalidDrafts[i]);
        super.init();
    }

    private static net.minecraft.world.item.Item dyeItem(DyeColor color) {
        return net.minecraft.world.item.Items.DYE.pick(color);
    }

    private void updateControlsVisibility() {
        tfr.visible = advanced;
        tfg.visible = advanced;
        tfb.visible = advanced;
        tfh.visible = advanced;
        tfs.visible = advanced;
        tfv.visible = advanced;
        intColor.visible = advanced;
        hexColor.visible = advanced;
    }

    @Override
    public void removed() {
        if (pickerImageSV != null) pickerImageSV.close();
        if (pickerImageH != null) pickerImageH.close();
        super.removed();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int mouseButton = event.button();
        PickerLayout layout = createLayout();
        applyAdvancedFieldLayout(layout);

        if (advanced) {
            if (GuiUtils.isRightMouseButton(mouseButton)) {
                if (GuiUtils.isHover(tfr, (int) mouseX, (int) mouseY)) {
                    tfr.setValue("");
                    return true;
                } else if (GuiUtils.isHover(tfg, (int) mouseX, (int) mouseY)) {
                    tfg.setValue("");
                    return true;
                } else if (GuiUtils.isHover(tfb, (int) mouseX, (int) mouseY)) {
                    tfb.setValue("");
                    return true;
                } else if (GuiUtils.isHover(tfh, (int) mouseX, (int) mouseY)) {
                    tfh.setValue("");
                    return true;
                } else if (GuiUtils.isHover(tfv, (int) mouseX, (int) mouseY)) {
                    tfv.setValue("");
                    return true;
                } else if (GuiUtils.isHover(tfs, (int) mouseX, (int) mouseY)) {
                    tfs.setValue("");
                    return true;
                } else if (GuiUtils.isHover(intColor, (int) mouseX, (int) mouseY)) {
                    intColor.setValue("");
                    return true;
                } else if (GuiUtils.isHover(hexColor, (int) mouseX, (int) mouseY)) {
                    hexColor.setValue("#");
                    return true;
                }
            }
        }
        drag = DragState.NONE;
        if (GuiUtils.isLeftMouseButton(mouseButton)) {
            if (gradientMode && GuiUtils.isHover(gradientX(layout), layout.previewY(), gradientWidth(layout),
                    PREVIEW_HEIGHT + 12, (int) mouseX, (int) mouseY)) {
                GradientStop nearest = gradientStops.stream().min(Comparator.comparingDouble(s -> Math.abs(mouseX - stopX(layout, s)))).orElseThrow();
                boolean handle = mouseY >= layout.previewY() + PREVIEW_HEIGHT && Math.abs(mouseX - stopX(layout, nearest)) <= 7;
                if (handle) {
                    var overlapping = gradientStops.stream().filter(s -> Math.abs(stopX(layout, s) - stopX(layout, nearest)) <= 2).toList();
                    int current = overlapping.indexOf(selectedStop);
                    selectedStop = overlapping.get(current < 0 ? 0 : (current + 1) % overlapping.size());
                    drag = DragState.STOP;
                    setDragging(true);
                } else selectedStop = nearest;
                updateColor(selectedStop.color);
                playClick();
                return true;
            }
            if (!advanced && GuiUtils.isHover(layout.pickerX(), layout.pickerY(), layout.pickerSize(), layout.pickerSize(),
                    (int) mouseX, (int) mouseY)) {
                setColor((int) mouseX, (int) mouseY, DragState.SV);
            } else if (!advanced && GuiUtils.isHover(layout.hueX(), layout.hueY(), layout.hueWidth(), layout.pickerSize(),
                    (int) mouseX, (int) mouseY)) {
                setColor((int) mouseX, (int) mouseY, DragState.H);
            } else {
                // Random and Delete buttons
                if (showAuxiliaryControls() && GuiUtils.isHover(layout.deleteX(), layout.deleteY(), layout.deleteWidth(), ROW_HEIGHT, (int) mouseX,
                        (int) mouseY)) {
                    if (transparentAsDefault) {
                        color |= 0xFF000000;
                    } else {
                        oldAlphaLayer = defaultColor & 0xFF000000;
                        updateColor(defaultColor & 0xFFFFFF);
                    }
                    playClick();
                    return true;
                } else if (showAuxiliaryControls() && GuiUtils.isHover(layout.randomX(), layout.randomY(), layout.randomWidth(), ROW_HEIGHT, (int) mouseX,
                        (int) mouseY)) {
                    updateColor(GuiUtils.getRandomColor() & 0xffffff);
                    playClick();
                    return true;
                } else {
                    // Dye colors
                    if (layout.showDyes()) {
                        for (int i = 0; i < DyeColor.values().length; ++i) {
                            int x = layout.dyeX() + (i % DYE_COLUMNS) * layout.dyeCellSize();
                            int y = layout.dyeY() + (i / DYE_COLUMNS) * layout.dyeCellSize();
                            if (GuiUtils.isHover(x, y, layout.dyeCellSize(), layout.dyeCellSize(), (int) mouseX, (int) mouseY)) {
                                updateColor(DyeColor.values()[i].getFireworkColor());
                                playClick();
                                return true;
                            }
                        }
                    }
                    if (layout.showRecent()) {
                        List<TextColorPreset> recentColors = RecentColorHistory.shared().presets(textSetter != null);
                        for (int i = 0; i < recentColors.size(); i++) {
                            int x = layout.recentX() + i % RECENT_COLUMNS * layout.recentCellSize();
                            int y = layout.recentY() + RECENT_LABEL_HEIGHT + i / RECENT_COLUMNS * layout.recentCellSize();
                            if (GuiUtils.isHover(x, y, layout.recentCellSize(), layout.recentCellSize(),
                                    (int) mouseX, (int) mouseY)) {
                                selectPreset(recentColors.get(i));
                                playClick();
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (drag == DragState.STOP) {
            PickerLayout layout = createLayout();
            selectedStop.position = Math.max(0, Math.min(1, (event.x() - gradientX(layout) - 5) / (gradientWidth(layout) - 11)));
            gradientStops.sort(Comparator.comparingDouble(s -> s.position));
            return true;
        }
        setColor((int) event.x(), (int) event.y(), drag);
        return drag != DragState.NONE || super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        drag = DragState.NONE;
        setDragging(false);
        return super.mouseReleased(event);
    }

    private void updateColor(int h, int s, int v) {
        updateColor(h % 360, s, v, ColorMath.fromHsv(h % 360, s, v));
    }

    private void updateColor(int rgba) {
        var hsv = ColorMath.toHsv(rgba);
        updateColor(hsv[0], hsv[1], hsv[2], rgba);
    }

    private void channel(EditBox field, String raw, int maximum, String label, java.util.function.IntConsumer update) {
        if (isUpdating) return;
        try {
            int value = Integer.parseInt(raw);
            if (value < 0 || value > maximum) throw new NumberFormatException();
            update.accept(value);
        } catch (NumberFormatException error) {
            EETextField.validate(field, "Enter a whole " + label.toLowerCase(java.util.Locale.ROOT) + " value from 0 to " + maximum + ".");
        }
        refreshFields();
    }
    private boolean validFields() {
        return java.util.stream.Stream.of(tfr,tfg,tfb,tfh,tfs,tfv,intColor,hexColor)
                .filter(java.util.Objects::nonNull).allMatch(f -> EETextField.error(f) == null);
    }
    private void refreshFields() {
        if (applyColor == null) return;
        String reason = java.util.stream.Stream.of(tfr,tfg,tfb,tfh,tfs,tfv,intColor,hexColor)
                .filter(java.util.Objects::nonNull).map(EETextField::error).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        applyColor.active = reason == null;
        applyColor.setTooltip(reason == null ? null : Tooltip.create(Component.literal(reason)));
    }
    private void updateColor(int h, int s, int v, int rgba) {
        if (isUpdating) return;
        isUpdating = true;
        localHue = h;
        localSaturation = s;
        localValue = v;
        
        // Update saved defaults
        savedHue = h;
        savedSaturation = s;
        savedValue = v;

        tfh.setValue("" + localHue);
        tfs.setValue("" + localSaturation);
        tfv.setValue("" + localValue);
        updatePickerTexture(localHue);

        color = rgba & 0xffffff;
        if (gradientMode && selectedStop != null) selectedStop.color = color;
        tfr.setValue("" + (color >> 16 & 0xFF));
        tfg.setValue("" + (color >> 8 & 0xFF));
        tfb.setValue("" + (color & 0xFF));
        this.intColor.setValue("" + color);
        this.hexColor.setValue(String.format("#%06X", color));
        java.util.stream.Stream.of(tfr,tfg,tfb,tfh,tfs,tfv,intColor,hexColor).filter(java.util.Objects::nonNull).forEach(f -> EETextField.validate(f, null));
        isUpdating = false;
        refreshFields();
    }

    private void setColor(int mouseX, int mouseY, DragState dragState) {
        drag = dragState;
        if (drag == DragState.NONE)
            return;
        PickerLayout layout = createLayout();

        switch (drag) {
            case SV -> {
                // Saturation (x)
                var s = GuiUtils.clamp(mouseX - layout.pickerX(), 0, layout.pickerSize()) * 100 / layout.pickerSize();
                // Value (y) - inverted (top is 100, bottom is 0)
                var v = 100 - (GuiUtils.clamp(mouseY - layout.pickerY(), 0, layout.pickerSize()) * 100 / layout.pickerSize());
                updateColor(localHue, s, v);
            }
            case H -> {
                // Hue (y)
                var h = GuiUtils.clamp(mouseY - layout.hueY(), 0, layout.pickerSize()) * 360 / layout.pickerSize();
                updateColor(h, localSaturation, localValue);
            }
        }
    }

    private void updateRed(int v) {
        updateColor((v & 0xFF) << 16 | ((color >> 8 & 0xFF) & 0xFF) << 8 | ((color & 0xFF) & 0xFF));
    }

    private void updateGreen(int v) {
        updateColor(((color >> 16 & 0xFF) & 0xFF) << 16 | (v & 0xFF) << 8 | ((color & 0xFF) & 0xFF));
    }

    private void updateBlue(int v) {
        updateColor(((color >> 16 & 0xFF) & 0xFF) << 16 | ((color >> 8 & 0xFF) & 0xFF) << 8 | (v & 0xFF));
    }

    private void updateHue(int v) {
        v %= 360;
        if (v < 0)
            v += 360;
        updateColor(v, localSaturation, localValue);
    }

    private void updateSaturation(int v) {
        v = GuiUtils.clamp(v, 0, 100);
        updateColor(localHue, v, localValue);
    }

    private void updateValue(int v) {
        v = GuiUtils.clamp(v, 0, 100);
        updateColor(localHue, localSaturation, v);
    }

    @Override
    protected void generateDev(List<ACTDevInfo> entries, int mouseX, int mouseY) {
        entries.add(devInfo("HEX", "#" + Integer.toHexString((color & 0xFFFFFF) | 0xF000000).substring(1)));
        entries.add(devInfo("HSV", localHue + "/" + localSaturation + "/" + localValue));
        var res = GuiUtils.rgbaFromRGBA(color);
        entries.add(devInfo("RGB", res.red() + "/" + res.green() + "/" + res.blue()));
        super.generateDev(entries, mouseX, mouseY);
    }
}
