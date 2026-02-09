package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.ColorMath;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;

public class GuiColorModifier extends GuiModifier<OptionalInt> {

    private enum DragState {
        SV, H, NONE
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
    private Button advButton;
    private EditBox tfr, tfg, tfb, tfh, tfs, tfv, intColor, hexColor;
    private final int defaultColor;
    private int localHue;
    private int localSaturation;
    private int localValue;
    private boolean isUpdating = false;
    private final int originalColor;

    private PickerLayout createLayout() {
        int marginX = 8;
        int topPadding = 8;
        int bottomPadding = 8;
        int previewGap = 4;
        int afterMainGap = 8;
        int beforeButtonsGap = 8;
        int dyeGap = 4;
        int buttonGap = 5;

        int fixedHeight = topPadding + bottomPadding + PREVIEW_HEIGHT + previewGap + afterMainGap + ROW_HEIGHT + beforeButtonsGap
                + BUTTON_HEIGHT;
        int bodyBudget = Math.max(24, height - fixedHeight);

        int dyeCellSize = bodyBudget >= DYE_ROWS * DYE_CELL_SIZE ? DYE_CELL_SIZE : DYE_CELL_SMALL_SIZE;
        int dyeHeight = DYE_ROWS * dyeCellSize;
        boolean showDyes = dyeHeight <= bodyBudget;

        int pickerSize = Math.min(PICKER_TEXTURE_SIZE, bodyBudget);
        int hueWidth = GuiUtils.clamp(pickerSize / 10, PICKER_MIN_HUE_WIDTH, PICKER_HUE_TEXTURE_WIDTH);
        int dyeWidth = showDyes ? DYE_COLUMNS * dyeCellSize : 0;
        int currentDyeGap = showDyes ? dyeGap : 0;
        int pickerByWidth = width - marginX * 2 - dyeWidth - currentDyeGap - PICKER_GAP - hueWidth;
        pickerSize = Math.min(pickerSize, pickerByWidth);

        if (showDyes && pickerSize < PICKER_MIN_SIZE) {
            showDyes = false;
            dyeWidth = 0;
            currentDyeGap = 0;
            pickerByWidth = width - marginX * 2 - PICKER_GAP - hueWidth;
            pickerSize = Math.min(Math.min(PICKER_TEXTURE_SIZE, bodyBudget), pickerByWidth);
        }

        pickerSize = Math.max(24, pickerSize);
        hueWidth = GuiUtils.clamp(pickerSize / 10, PICKER_MIN_HUE_WIDTH, PICKER_HUE_TEXTURE_WIDTH);
        pickerByWidth = width - marginX * 2 - dyeWidth - currentDyeGap - PICKER_GAP - hueWidth;
        pickerSize = Math.max(24, Math.min(pickerSize, pickerByWidth));
        if (pickerSize < PICKER_MIN_FALLBACK_SIZE && showDyes) {
            showDyes = false;
            dyeWidth = 0;
            currentDyeGap = 0;
            pickerByWidth = width - marginX * 2 - PICKER_GAP - hueWidth;
            pickerSize = Math.max(24, Math.min(Math.min(PICKER_TEXTURE_SIZE, bodyBudget), pickerByWidth));
        }

        dyeHeight = showDyes ? DYE_ROWS * dyeCellSize : 0;
        int bodyHeight = Math.max(pickerSize, dyeHeight);
        if (bodyHeight > bodyBudget) {
            showDyes = false;
            dyeWidth = 0;
            currentDyeGap = 0;
            dyeHeight = 0;
            bodyHeight = Math.min(pickerSize, bodyBudget);
            pickerSize = bodyHeight;
            hueWidth = GuiUtils.clamp(Math.max(1, pickerSize / 10), PICKER_MIN_HUE_WIDTH, PICKER_HUE_TEXTURE_WIDTH);
        }

        int contentHeight = PREVIEW_HEIGHT + previewGap + bodyHeight + afterMainGap + ROW_HEIGHT + beforeButtonsGap + BUTTON_HEIGHT;
        int availableHeight = height - topPadding - bottomPadding;
        int contentTop = topPadding + Math.max(0, (availableHeight - contentHeight) / 2);
        int bodyTop = contentTop + PREVIEW_HEIGHT + previewGap;

        int contentWidth = dyeWidth + currentDyeGap + pickerSize + PICKER_GAP + hueWidth;
        int contentLeft = (width - contentWidth) / 2;
        int pickerX = contentLeft + dyeWidth + currentDyeGap;
        int pickerY = bodyTop + (bodyHeight - pickerSize) / 2;
        int hueX = pickerX + pickerSize + PICKER_GAP;
        int hueY = pickerY;
        int dyeX = contentLeft;
        int dyeY = bodyTop + (bodyHeight - dyeHeight) / 2;

        int randomX = pickerX;
        int randomY = bodyTop + bodyHeight + afterMainGap;
        int randomWidth = 38;
        int deleteWidth = 40;
        int deleteX = hueX + hueWidth - deleteWidth;
        int deleteY = randomY;

        int buttonWidth = GuiUtils.clamp((width - marginX * 2 - buttonGap * 2) / 3, 58, 90);
        int buttonsX = (width - (buttonWidth * 3 + buttonGap * 2)) / 2;
        int buttonsY = randomY + ROW_HEIGHT + beforeButtonsGap;

        return new PickerLayout(pickerX, pickerY, pickerSize, hueX, hueY, hueWidth, bodyTop, bodyHeight, contentTop,
                dyeX, dyeY, dyeCellSize, showDyes, randomX, randomY, randomWidth, deleteX, deleteY, deleteWidth,
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
        int rowStart = layout.bodyTop() + Math.max(4, (layout.bodyHeight() - 112) / 2);
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
        return color != originalColor;
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // allow multiple color modifiers
        updatePickerTexture(localHue);
        PickerLayout layout = createLayout();
        applyAdvancedFieldLayout(layout);

        super.renderBackground(graphics, mouseX, mouseY, partialTicks);

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
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.red") + ": ", tfr, 0xffffffff);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.green") + ": ", tfg, 0xffffffff);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.blue") + ": ", tfb, 0xffffffff);

            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.hue") + ": ", tfh, 0xffffffff);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.saturation") + ": ", tfs,
                    0xffffffff);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.value") + ": ", tfv,
                    0xffffffff);

            GuiUtils.drawString(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.intColor") + ":", intColor.getX(),
                    intColor.getY() - 4 - 10, 0xffffffff, 10);
            GuiUtils.drawString(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.hexColor") + ":", hexColor.getX(),
                    hexColor.getY() - 4 - 10, 0xffffffff, 10);
        }
        
        // Current color preview
        if ((color & 0xFF000000) == 0)
            GuiUtils.drawRect(graphics, layout.pickerX(), layout.previewY(), layout.hueX() + layout.hueWidth(), layout.previewY() + PREVIEW_HEIGHT,
                    color | 0xff000000);

        if (Minecraft.getInstance().player != null) {
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
                GuiUtils.drawItemStack(graphics, new ItemStack(DyeItem.byColor(dyeColor)),
                        x + (layout.dyeCellSize() - 16) / 2, y + (layout.dyeCellSize() - 16) / 2);
            }
        }

        // random
        GuiUtils.drawHoverableRect(graphics, layout.randomX(), layout.randomY(), layout.randomX() + layout.randomWidth(),
                layout.randomY() + ROW_HEIGHT,
                0xFF444444, GuiUtils.getTimeColor(RANDOM_PICKER_FREQUENCY, 50, 15), mouseX, mouseY);
        GuiUtils.drawItemStack(graphics, updatePicker(), layout.randomX() + layout.randomWidth() / 2 - 16 / 2,
                layout.randomY() + ROW_HEIGHT / 2 - 16 / 2);
        if (GuiUtils.isHover(layout.randomX(), layout.randomY(), layout.randomWidth(), ROW_HEIGHT, mouseX, mouseY)) {
            show = () -> GuiUtils.drawTextBox(graphics, font, mouseX, mouseY, width, height, getZLevel(),
                    I18n.get("gui.ee.modifier.meta.setColor.random"));
        }

        // delete
        GuiUtils.drawHoverableRect(graphics, layout.deleteX(), layout.deleteY(),
                layout.deleteX() + layout.deleteWidth(), layout.deleteY() + ROW_HEIGHT,
                0xFFDD4444, 0xFFFF4444, mouseX, mouseY);
        GuiUtils.drawCenterString(graphics, font, "Undo", layout.deleteX() + layout.deleteWidth() / 2, layout.deleteY(),
                0xFFFFFFFF, ROW_HEIGHT);

        super.render(graphics, mouseX, mouseY, partialTicks);

        setZLever(getZLevel() + 75);
        show.run();
        setZLever(getZLevel() - 75);
    }

    private void complete() {
        set((color & 0xFF000000) != 0 ? OptionalInt.empty() : OptionalInt.of(color | oldAlphaLayer));
    }

    @Override
    public void init() {
        initTextures();
        PickerLayout layout = createLayout();

        addRenderableWidget(
                new EEButton(layout.buttonsX(), layout.buttonsY(), layout.buttonWidth(), BUTTON_HEIGHT,
                        Component.translatable("gui.ee.cancel"), b -> onCancel()));
        advButton = addRenderableWidget(new EEButton(layout.buttonsX() + layout.buttonWidth() + layout.buttonGap(),
                layout.buttonsY(), layout.buttonWidth(), BUTTON_HEIGHT,
                Component.translatable("gui.ee.advanced"), b -> {
            advanced ^= true;
            advButton.setMessage(Component.translatable(
                    advanced ? "gui.ee.modifier.meta.setColor.picker" : "gui.ee.advanced"));
            updateControlsVisibility();
        }));
        addRenderableWidget(
                new EEButton(layout.buttonsX() + 2 * (layout.buttonWidth() + layout.buttonGap()), layout.buttonsY(),
                        layout.buttonWidth(), BUTTON_HEIGHT, Component.translatable("gui.done"), b -> {
                    complete();
                    getMinecraft().setScreen(parent);
                }));

        // Advanced fields
        tfr = new EditBox(font, 0, 0, 45, 18, Component.literal(""));
        tfg = new EditBox(font, 0, 0, 45, 18, Component.literal(""));
        tfb = new EditBox(font, 0, 0, 45, 18, Component.literal(""));

        tfh = new EditBox(font, 0, 0, 45, 18, Component.literal(""));
        tfs = new EditBox(font, 0, 0, 45, 18, Component.literal(""));
        tfv = new EditBox(font, 0, 0, 45, 18, Component.literal(""));

        intColor = new EditBox(font, 0, 0, 85, 18, Component.literal(""));
        hexColor = new EditBox(font, 0, 0, 85, 18, Component.literal(""));

        tfr.setMaxLength(4);
        tfg.setMaxLength(4);
        tfb.setMaxLength(4);
        tfh.setMaxLength(4);
        tfs.setMaxLength(4);
        tfv.setMaxLength(4);

        tfr.setResponder(s -> {
            try {
                updateRed(s.isEmpty() ? 0 : Integer.parseInt(s));
            } catch (NumberFormatException e) {
            }
        });
        tfg.setResponder(s -> {
            try {
                updateGreen(s.isEmpty() ? 0 : Integer.parseInt(s));
            } catch (NumberFormatException e) {
            }
        });
        tfb.setResponder(s -> {
            try {
                updateBlue(s.isEmpty() ? 0 : Integer.parseInt(s));
            } catch (NumberFormatException e) {
            }
        });
        tfh.setResponder(s -> {
            try {
                updateHue(s.isEmpty() ? 0 : Integer.parseInt(s));
            } catch (NumberFormatException e) {
            }
        });
        tfs.setResponder(s -> {
            try {
                updateSaturation(s.isEmpty() ? 0 : Integer.parseInt(s));
            } catch (NumberFormatException e) {
            }
        });
        tfv.setResponder(s -> {
            try {
                updateValue(s.isEmpty() ? 0 : Integer.parseInt(s));
            } catch (NumberFormatException e) {
            }
        });
        hexColor.setResponder(s -> {
            try {
                String s1 = s.substring(1);
                updateColor(s1.isEmpty() ? 0 : Integer.parseInt(s1, 16));
            } catch (NumberFormatException e) {
            }
        });
        intColor.setResponder(s -> {
            try {
                updateColor(s.isEmpty() ? 0 : Integer.parseInt(s));
            } catch (NumberFormatException e) {
            }
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
        updateColor(color); // sync picker color
        super.init();
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
            if (mouseButton == 1) {
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
        if (mouseButton == 0) {
            if (!advanced && GuiUtils.isHover(layout.pickerX(), layout.pickerY(), layout.pickerSize(), layout.pickerSize(),
                    (int) mouseX, (int) mouseY)) {
                setColor((int) mouseX, (int) mouseY, DragState.SV);
            } else if (!advanced && GuiUtils.isHover(layout.hueX(), layout.hueY(), layout.hueWidth(), layout.pickerSize(),
                    (int) mouseX, (int) mouseY)) {
                setColor((int) mouseX, (int) mouseY, DragState.H);
            } else {
                // Random and Delete buttons
                if (GuiUtils.isHover(layout.deleteX(), layout.deleteY(), layout.deleteWidth(), ROW_HEIGHT, (int) mouseX,
                        (int) mouseY)) {
                    if (transparentAsDefault) {
                        color |= 0xFF000000;
                    } else {
                        oldAlphaLayer = defaultColor & 0xFF000000;
                        updateColor(defaultColor & 0xFFFFFF);
                    }
                    playClick();
                    return true;
                } else if (GuiUtils.isHover(layout.randomX(), layout.randomY(), layout.randomWidth(), ROW_HEIGHT, (int) mouseX,
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
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        setColor((int) event.x(), (int) event.y(), drag);
        return super.mouseDragged(event, dx, dy);
    }

    private void updateColor(int h, int s, int v) {
        updateColor(h % 360, s, v, ColorMath.fromHsv(h % 360, s, v));
    }

    private void updateColor(int rgba) {
        var hsv = ColorMath.toHsv(rgba);
        updateColor(hsv[0], hsv[1], hsv[2], rgba);
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
        tfr.setValue("" + (color >> 16 & 0xFF));
        tfg.setValue("" + (color >> 8 & 0xFF));
        tfb.setValue("" + (color & 0xFF));
        this.intColor.setValue("" + color);
        this.hexColor.setValue("#" + Integer.toHexString(color));
        isUpdating = false;
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
