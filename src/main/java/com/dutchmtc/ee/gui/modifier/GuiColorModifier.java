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

    private static final int PICKER_SIZE = 200;
    private static final int PICKER_HUE_WIDTH = 20;
    private static final int PICKER_GAP = 4;
    private static final Identifier PICKER_SV_RESOURCE = Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "picker_sv");
    private static final Identifier PICKER_H_RESOURCE = Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "picker_h");
    
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
                new NativeImage(NativeImage.Format.RGBA, PICKER_SIZE, PICKER_SIZE, false));
        pickerImageH = new DynamicTexture(() -> EEMod.MOD_ID + "_picker_h",
                new NativeImage(NativeImage.Format.RGBA, PICKER_HUE_WIDTH, PICKER_SIZE, false));

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

        super.renderBackground(graphics, mouseX, mouseY, partialTicks);

        int centerX = width / 2;
        int centerY = height / 2;
        int totalWidth = PICKER_SIZE + PICKER_GAP + PICKER_HUE_WIDTH;
        int pickerX = centerX - totalWidth / 2;
        int pickerY = centerY - 100;
        int hueX = pickerX + PICKER_SIZE + PICKER_GAP;
        int hueY = pickerY;

        if (!advanced) {
            // SV PICKER
            graphics.blit(RenderPipelines.GUI_TEXTURED, PICKER_SV_RESOURCE,
                    pickerX, pickerY,
                    0.0F, 0.0F,
                    PICKER_SIZE, PICKER_SIZE,
                    PICKER_SIZE, PICKER_SIZE);

            // SV Index
            int sX = pickerX + (localSaturation * PICKER_SIZE / 100);
            int vY = pickerY + PICKER_SIZE - (localValue * PICKER_SIZE / 100);
            
            GuiUtils.drawRect(graphics, sX - 2, vY - 2, sX + 2, vY + 2, 0xff222222);
            GuiUtils.drawRect(graphics, sX - 1, vY - 1, sX + 1, vY + 1, 0xffcccccc);

            // Hue Picker
            graphics.blit(RenderPipelines.GUI_TEXTURED, PICKER_H_RESOURCE,
                    hueX, hueY,
                    0.0F, 0.0F,
                    PICKER_HUE_WIDTH, PICKER_SIZE,
                    PICKER_HUE_WIDTH, PICKER_SIZE);

            // Hue Index
            int hY = hueY + (localHue * PICKER_SIZE / 360);
            GuiUtils.drawRect(graphics, hueX - 2, hY - 2, hueX + PICKER_HUE_WIDTH + 2, hY + 2, 0xff222222);
            GuiUtils.drawRect(graphics, hueX - 1, hY - 1, hueX + PICKER_HUE_WIDTH + 1, hY + 1, 0xffcccccc);

        } else {
            graphics.pose().pushMatrix();
            GuiUtils.drawRect(graphics, centerX - 100, pickerY, centerX + 130, pickerY + PICKER_SIZE,
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
            GuiUtils.drawRect(graphics, pickerX, pickerY - 24, hueX + PICKER_HUE_WIDTH, pickerY - 4,
                    color | 0xff000000);

        if (Minecraft.getInstance().player != null) {
            ItemStack stack = Minecraft.getInstance().player.getMainHandItem().copy();
            if (ItemUtils.canGlobalColorIt(stack)) {
                ItemUtils.setGlobalColor(stack, color);
                int previewX = pickerX + (hueX + PICKER_HUE_WIDTH - pickerX) / 2 - 8;
                int previewY = pickerY - 24 + (20 - 16) / 2;
                GuiUtils.drawItemStack(graphics, stack, previewX, previewY);
            }
        }

        Runnable show = () -> {
        };
        
        // Dye colors
        for (var i = 0; i < DyeColor.values().length; ++i) {
            var color = DyeColor.values()[i];
            var x = pickerX - 40 + (i % 2) * 19;
            var y = pickerY + (i / 2) * 19;
            
            GuiUtils.drawRect(graphics, x, y, x + 19, y + 19, 0xff000000 | color.getFireworkColor());
            if (GuiUtils.isHover(x, y, 19, 19, mouseX, mouseY)) {
                show = () -> GuiUtils.drawTextBox(graphics, font, mouseX, mouseY, width, height, getZLevel(),
                        I18n.get("item.minecraft.firework_star." + color.getName()));
            }
            GuiUtils.drawItemStack(graphics, new ItemStack(DyeItem.byColor(color)), x + (19 - 16) / 2,
                    y + (19 - 16) / 2);
        }

        // random
        int randX = pickerX;
        int randY = pickerY + PICKER_SIZE + 10;
        GuiUtils.drawHoverableRect(graphics, randX, randY, randX + 38, randY + 20,
                0xFF444444, GuiUtils.getTimeColor(RANDOM_PICKER_FREQUENCY, 50, 15), mouseX, mouseY);
        GuiUtils.drawItemStack(graphics, updatePicker(), randX + 38 / 2 - 16 / 2,
                randY + 20 / 2 - 16 / 2);
        if (GuiUtils.isHover(randX, randY, 38, 20, mouseX, mouseY)) {
            show = () -> GuiUtils.drawTextBox(graphics, font, mouseX, mouseY, width, height, getZLevel(),
                    I18n.get("gui.ee.modifier.meta.setColor.random"));
        }

        // delete
        int delWidth = 40;
        int delX = hueX + PICKER_HUE_WIDTH - delWidth;
        int delY = randY;
        GuiUtils.drawHoverableRect(graphics, delX, delY, delX + delWidth, delY + 20,
                0xFFDD4444, 0xFFFF4444, mouseX, mouseY);
        GuiUtils.drawCenterString(graphics, font, "Undo", delX + delWidth / 2, delY, 0xFFFFFFFF, 20);

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

        int centerX = width / 2;
        int centerY = height / 2;
        int pickerY = centerY - 100;
        int btnY = pickerY + PICKER_SIZE + 40;
        
        int btnWidth = 80;
        int btnGap = 5;
        int totalBtnWidth = 3 * btnWidth + 2 * btnGap;
        int btnStartX = centerX - totalBtnWidth / 2;

        addRenderableWidget(
                new EEButton(btnStartX, btnY, btnWidth, 20, Component.translatable("gui.done"), b -> {
                    complete();
                    getMinecraft().setScreen(parent);
                }));
        advButton = addRenderableWidget(new EEButton(btnStartX + btnWidth + btnGap, btnY, btnWidth, 20,
                Component.translatable("gui.ee.advanced"), b -> {
            advanced ^= true;
            advButton.setMessage(Component.translatable(
                    advanced ? "gui.ee.modifier.meta.setColor.picker" : "gui.ee.advanced"));
            updateControlsVisibility();
        }));
        addRenderableWidget(
                new EEButton(btnStartX + 2 * (btnWidth + btnGap), btnY, btnWidth, 20, Component.translatable("gui.ee.cancel"), b -> onCancel()));

        // Advanced fields
        int rgbBoxX = centerX - 40;
        int hsvBoxX = centerX + 75;
        int boxWidth = 45;
        
        tfr = new EditBox(font, rgbBoxX, centerY - 54, boxWidth, 18, Component.literal(""));
        tfg = new EditBox(font, rgbBoxX, centerY - 26, boxWidth, 18, Component.literal(""));
        tfb = new EditBox(font, rgbBoxX, centerY + 2, boxWidth, 18, Component.literal(""));

        tfh = new EditBox(font, hsvBoxX, centerY - 54, boxWidth, 18, Component.literal(""));
        tfs = new EditBox(font, hsvBoxX, centerY - 26, boxWidth, 18, Component.literal(""));
        tfv = new EditBox(font, hsvBoxX, centerY + 2, boxWidth, 18, Component.literal(""));

        int intHexWidth = 85;
        intColor = new EditBox(font, centerX - 90, centerY + 40, intHexWidth, 18, Component.literal(""));
        hexColor = new EditBox(font, centerX + 5, centerY + 40, intHexWidth, 18, Component.literal(""));

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
        
        int centerX = width / 2;
        int centerY = height / 2;
        int totalWidth = PICKER_SIZE + PICKER_GAP + PICKER_HUE_WIDTH;
        int pickerX = centerX - totalWidth / 2;
        int pickerY = centerY - 100;
        int hueX = pickerX + PICKER_SIZE + PICKER_GAP;
        int hueY = pickerY;

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
            if (!advanced && GuiUtils.isHover(pickerX, pickerY, PICKER_SIZE, PICKER_SIZE, (int) mouseX, (int) mouseY)) {
                setColor((int) mouseX, (int) mouseY, DragState.SV);
            } else if (!advanced && GuiUtils.isHover(hueX, hueY, PICKER_HUE_WIDTH, PICKER_SIZE, (int) mouseX, (int) mouseY)) {
                setColor((int) mouseX, (int) mouseY, DragState.H);
            } else {
                // Random and Delete buttons
                int randX = pickerX;
                int randY = pickerY + PICKER_SIZE + 10;
                int delWidth = 40;
                int delX = hueX + PICKER_HUE_WIDTH - delWidth;
                int delY = randY;

                if (GuiUtils.isHover(delX, delY, delWidth, 20, (int) mouseX, (int) mouseY)) {
                    if (transparentAsDefault) {
                        color |= 0xFF000000;
                    } else {
                        oldAlphaLayer = defaultColor & 0xFF000000;
                        updateColor(defaultColor & 0xFFFFFF);
                    }
                    playClick();
                    return true;
                } else if (GuiUtils.isHover(randX, randY, 38, 20, (int) mouseX, (int) mouseY)) {
                    updateColor(GuiUtils.getRandomColor() & 0xffffff);
                    playClick();
                    return true;
                } else {
                    // Dye colors
                    for (int i = 0; i < DyeColor.values().length; ++i) {
                        int x = pickerX - 40 + (i % 2) * 19;
                        int y = pickerY + (i / 2) * 19;
                        if (GuiUtils.isHover(x, y, 19, 19, (int) mouseX, (int) mouseY)) {
                            updateColor(DyeColor.values()[i].getFireworkColor());
                            playClick();
                            return true;
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

        int centerX = width / 2;
        int centerY = height / 2;
        int totalWidth = PICKER_SIZE + PICKER_GAP + PICKER_HUE_WIDTH;
        int pickerX = centerX - totalWidth / 2;
        int pickerY = centerY - 100;
        int hueX = pickerX + PICKER_SIZE + PICKER_GAP;
        int hueY = pickerY;

        switch (drag) {
            case SV -> {
                // Saturation (x)
                var s = GuiUtils.clamp(mouseX - pickerX, 0, PICKER_SIZE) * 100 / PICKER_SIZE;
                // Value (y) - inverted (top is 100, bottom is 0)
                var v = 100 - (GuiUtils.clamp(mouseY - pickerY, 0, PICKER_SIZE) * 100 / PICKER_SIZE);
                updateColor(localHue, s, v);
            }
            case H -> {
                // Hue (y)
                var h = GuiUtils.clamp(mouseY - hueY, 0, PICKER_SIZE) * 360 / PICKER_SIZE;
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
