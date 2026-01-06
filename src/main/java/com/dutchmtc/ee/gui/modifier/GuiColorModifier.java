package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.gui.components.EEButton;
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
        HL, S, NONE
    }

    private static final int PICKER_SIZE_Y = 200;
    private static final int PICKER_S_SIZE_X = 20;
    private static final int PICKER_HL_SIZE_X = 200;
    private static final Identifier PICKER_S_RESOURCE = Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "picker_hl");
    private static final Identifier PICKER_HL_RESOURCE = Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "picker_s");
    
    private DynamicTexture pickerImageS;
    private DynamicTexture pickerImageHL;

    private static final ItemStack RANDOM_PICKER = new ItemStack(Items.POTION);
    private static final int RANDOM_PICKER_FREQUENCY = 3600;

    private static ItemStack updatePicker() {
        ItemUtils.setGlobalColor(RANDOM_PICKER, GuiUtils.getTimeColor(RANDOM_PICKER_FREQUENCY, 100, 50));
        return RANDOM_PICKER;
    }

    // Saved state for defaults
    private static int savedHue;
    private static int savedSaturation;
    private static int savedLightness;

    // Texture state
    private int texHue = -1;
    private int texSaturation = -1;
    private int texLightness = -1;

    private void updatePickerTexture(int hue, int saturation, int lightness) {
        if (pickerImageS == null || pickerImageHL == null) {
            initTextures();
        }
        // regen PICKER_IMAGE_S
        if (!(hue == texHue && lightness == texLightness)) {
            texHue = hue;
            texLightness = lightness;

            var pixels = Objects.requireNonNull(pickerImageS.getPixels());

            for (var y = 0; y < pixels.getHeight(); y++) { // saturation
                var color = GuiUtils.fromHSL(hue, y * 100 / pixels.getHeight(), lightness);
                for (var x = 0; x < pixels.getWidth(); x++)
                    pixels.setPixelABGR(x, y, GuiUtils.blueToRed(color));
            }

            pickerImageS.upload();
        }

        // regen PICKER_IMAGE_HL
        if (saturation != texSaturation) {
            texSaturation = saturation;

            var pixels = Objects.requireNonNull(pickerImageHL.getPixels());

            for (var x = 0; x < pixels.getWidth(); x++) // hue
                for (var y = 0; y < pixels.getHeight(); y++) // lightness
                    pixels.setPixelABGR(x, y, GuiUtils.blueToRed(
                            GuiUtils.fromHSL(x * 360 / pixels.getWidth(), saturation, y * 100 / pixels.getHeight())));

            pickerImageHL.upload();
        }

    }

    public void initTextures() {
        if (pickerImageS != null) pickerImageS.close();
        if (pickerImageHL != null) pickerImageHL.close();

        pickerImageS = new DynamicTexture(() -> EEMod.MOD_ID + "_picker_s",
                new NativeImage(NativeImage.Format.RGBA, PICKER_S_SIZE_X, PICKER_SIZE_Y, false));
        pickerImageHL = new DynamicTexture(() -> EEMod.MOD_ID + "_picker_hl",
                new NativeImage(NativeImage.Format.RGBA, PICKER_HL_SIZE_X, PICKER_SIZE_Y, false));

        TextureManager tm = Minecraft.getInstance().getTextureManager();
        // Reset state to force update
        texHue = -1;
        texSaturation = -1;
        texLightness = -1;
        updatePickerTexture(localHue, localSaturation, localLightness);
        
        tm.register(PICKER_S_RESOURCE, pickerImageS);
        tm.register(PICKER_HL_RESOURCE, pickerImageHL);
    }

    private int oldAlphaLayer;
    private final boolean transparentAsDefault;
    private int color;
    private DragState drag = DragState.NONE;
    private boolean advanced = false;
    private Button advButton;
    private EditBox tfr, tfg, tfb, tfh, tfs, tfl, intColor, hexColor;
    private final int defaultColor;
    private int localHue;
    private int localSaturation;
    private int localLightness;
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
        var hsl = GuiUtils.hslFromRGBA(rgba, savedHue, savedSaturation);
        var fullblack = (rgba & 0xFFFFFF) == 0;
        localHue = hsl.hue();
        localSaturation = fullblack ? 100 : hsl.saturation();
        localLightness = hsl.lightness();
    }

    @Override
    public boolean isModified() {
        return color != originalColor;
    }

    @Override
    public void tick() {
        // Removed tick calls
        super.tick();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // allow multiple color modifiers
        updatePickerTexture(localHue, localSaturation, localLightness);

        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);

        if (!advanced) {
            // S PICKER
            graphics.blit(RenderPipelines.GUI_TEXTURED, PICKER_S_RESOURCE,
                    width / 2 + 180, height / 2 - 76,
                    0.0F, 0.0F,
                    PICKER_S_SIZE_X, PICKER_SIZE_Y,
                    PICKER_S_SIZE_X, PICKER_SIZE_Y);


            // - S Index
            var saturationDelta = texSaturation * 76 * 2 / 100;
            GuiUtils.drawRect(graphics, width / 2 + 178, height / 2 - 76 + saturationDelta - 2, width / 2 + 178 + 22,
                    height / 2 - 76 + saturationDelta + 2, 0xff222222);
            GuiUtils.drawRect(graphics, width / 2 + 180, height / 2 - 76 + saturationDelta - 1, width / 2 + 180 + 20,
                    height / 2 - 76 + saturationDelta + 1, 0xff999999);

            // HL Picker
            graphics.blit(RenderPipelines.GUI_TEXTURED, PICKER_HL_RESOURCE,
                    width / 2 - 158, height / 2 - 76,
                    0.0F, 0.0F,
                    158 + 176, 76 * 2,
                    PICKER_HL_SIZE_X, PICKER_SIZE_Y);

            // - HL Index
            var hueDelta = texHue * (158 + 176) / 360;
            var lightnessDelta = texLightness * (76 * 2) / 100;
            GuiUtils.drawRect(graphics, width / 2 - 158 + hueDelta - 5, height / 2 - 76 + lightnessDelta - 2,
                    width / 2 - 158 + hueDelta - 5 + 10, height / 2 - 76 + lightnessDelta - 2 + 4, 0xff222222);
            GuiUtils.drawRect(graphics, width / 2 - 158 + hueDelta - 2, height / 2 - 76 + lightnessDelta - 5,
                    width / 2 - 158 + hueDelta - 2 + 4, height / 2 - 76 + lightnessDelta - 5 + 10, 0xff222222);

            GuiUtils.drawRect(graphics, width / 2 - 158 + hueDelta - 4, height / 2 - 76 + lightnessDelta - 1,
                    width / 2 - 158 + hueDelta - 4 + 8, height / 2 - 76 + lightnessDelta - 1 + 2, 0xff999999);
            GuiUtils.drawRect(graphics, width / 2 - 158 + hueDelta - 1, height / 2 - 76 + lightnessDelta - 4,
                    width / 2 - 158 + hueDelta - 1 + 2, height / 2 - 76 + lightnessDelta - 4 + 8, 0xff999999);
        } else {
            GuiUtils.drawRect(graphics, width / 2 - 158, height / 2 - 76, width / 2 + 200, height / 2 + 76,
                    0x88000000);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.red") + ": ", tfr, 0xffffffff);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.green") + ": ", tfg, 0xffffffff);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.blue") + ": ", tfb, 0xffffffff);

            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.hue") + ": ", tfh, 0xffffffff);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.lightness") + ": ", tfl,
                    0xffffffff);
            GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.saturation") + ": ", tfs,
                    0xffffffff);

            GuiUtils.drawString(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.intColor") + ":", intColor.getX(),
                    intColor.getY() - 4 - 10, 0xffffffff, 10);
            GuiUtils.drawString(graphics, font, I18n.get("gui.ee.modifier.meta.setColor.hexColor") + ":", hexColor.getX(),
                    hexColor.getY() - 4 - 10, 0xffffffff, 10);
        }
        if ((color & 0xFF000000) == 0)
            GuiUtils.drawRect(graphics, width / 2 - 158, height / 2 - 100, width / 2 + 176, height / 2 - 80,
                    color | 0xff000000);

        Runnable show = () -> {
        };
        for (var i = 0; i < DyeColor.values().length; ++i) {
            var color = DyeColor.values()[i];
            var x = width / 2 - 200 + (i % 2) * 19;
            var y = height / 2 - 76 + (i / 2) * 19;
            GuiUtils.drawRect(graphics, x, y, x + 19, y + 19, 0xff000000 | color.getFireworkColor());
            if (GuiUtils.isHover(x, y, 19, 19, mouseX, mouseY)) {
                show = () -> GuiUtils.drawTextBox(graphics, font, mouseX, mouseY, width, height, getZLevel(),
                        I18n.get("item.minecraft.firework_star." + color.getName()));
            }
            GuiUtils.drawItemStack(graphics, new ItemStack(DyeItem.byColor(color)), x + (19 - 16) / 2,
                    y + (19 - 16) / 2);
        }

        // random
        GuiUtils.drawHoverableRect(graphics, width / 2 - 200, height / 2 - 100, width / 2 - 162, height / 2 - 80,
                0xFF444444, GuiUtils.getTimeColor(RANDOM_PICKER_FREQUENCY, 50, 15), mouseX, mouseY);
        GuiUtils.drawItemStack(graphics, updatePicker(), width / 2 - 200 + 38 / 2 - 16 / 2,
                height / 2 - 100 + 20 / 2 - 16 / 2);
        if (GuiUtils.isHover(width / 2 - 200, height / 2 - 100, 38, 20, mouseX, mouseY)) {
            show = () -> GuiUtils.drawTextBox(graphics, font, mouseX, mouseY, width, height, getZLevel(),
                    I18n.get("gui.ee.modifier.meta.setColor.random"));
        }

        // delete
        GuiUtils.drawHoverableRect(graphics, width / 2 + 180, height / 2 - 100, width / 2 + 200, height / 2 - 80,
                0xFFDD4444, 0xFFFF4444, mouseX, mouseY);
        GuiUtils.drawCenterString(graphics, font, "x", width / 2 + 190, height / 2 - 100, 0xFFFFFFFF, 20);

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

        addRenderableWidget(
                new EEButton(width / 2 - 200, height / 2 + 80, 130, 20, Component.translatable("gui.done"), b -> {
                    complete();
                    getMinecraft().setScreen(parent);
                }));
        advButton = addRenderableWidget(new EEButton(width / 2 - 66, height / 2 + 80, 132, 20,
                Component.translatable("gui.ee.advanced"), b -> {
            advanced ^= true;
            advButton.setMessage(Component.translatable(
                    advanced ? "gui.ee.modifier.meta.setColor.picker" : "gui.ee.advanced"));
        }));
        addRenderableWidget(
                new EEButton(width / 2 + 70, height / 2 + 80, 130, 20, Component.translatable("gui.ee.cancel"), b -> onCancel()));

        var advWidth = 158 + 200;
        var midAdv = width / 2 + (-158 + 200) / 2;
        tfr = new EditBox(font, midAdv - 56, height / 2 - 54, 56, 18, Component.literal(""));
        tfg = new EditBox(font, midAdv - 56, height / 2 - 26, 56, 18, Component.literal(""));
        tfb = new EditBox(font, midAdv - 56, height / 2 + 2, 56, 18, Component.literal(""));

        var rightAdv = width / 2 + 200;
        tfh = new EditBox(font, rightAdv - 56, height / 2 - 54, 56, 18, Component.literal(""));
        tfl = new EditBox(font, rightAdv - 56, height / 2 - 26, 56, 18, Component.literal(""));
        tfs = new EditBox(font, rightAdv - 56, height / 2 + 2, 56, 18, Component.literal(""));

        var intHexWidth = (advWidth - 4 - 4) / 2;
        intColor = new EditBox(font, midAdv - intHexWidth, height / 2 + 40, intHexWidth, 18, Component.literal(""));
        hexColor = new EditBox(font, midAdv + 4, height / 2 + 40, intHexWidth, 18, Component.literal(""));

        tfr.setMaxLength(4);
        tfg.setMaxLength(4);
        tfb.setMaxLength(4);
        tfh.setMaxLength(4);
        tfl.setMaxLength(4);
        tfs.setMaxLength(4);

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
        tfl.setResponder(s -> {
            try {
                updateLightness(s.isEmpty() ? 0 : Integer.parseInt(s));
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
        addRenderableWidget(tfl);
        addRenderableWidget(tfs);
        addRenderableWidget(intColor);
        addRenderableWidget(hexColor);

        updateColor(color); // sync picker color
        super.init();
    }

    @Override
    public void removed() {
        if (pickerImageS != null) pickerImageS.close();
        if (pickerImageHL != null) pickerImageHL.close();
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
                } else if (GuiUtils.isHover(tfl, (int) mouseX, (int) mouseY)) {
                    tfl.setValue("");
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
            if (!advanced && GuiUtils.isHover(width / 2 - 158, height / 2 - 76, 158 + 176, 76 * 2, (int) mouseX,
                    (int) mouseY)) {
                setColor((int) mouseX, (int) mouseY, DragState.HL);
            } else if (!advanced
                    && GuiUtils.isHover(width / 2 + 180, height / 2 - 76, 20, 76 * 2, (int) mouseX, (int) mouseY)) {
                setColor((int) mouseX, (int) mouseY, DragState.S);
            } else if (GuiUtils.isHover(width / 2 + 180, height / 2 - 100, 20, 20, (int) mouseX, (int) mouseY)) {
                if (transparentAsDefault) {
                    color |= 0xFF000000;
                } else {
                    oldAlphaLayer = defaultColor & 0xFF000000;
                    updateColor(defaultColor & 0xFFFFFF);
                }
                playClick();
                return true;
            } else if (GuiUtils.isHover(width / 2 - 200, height / 2 - 100, 38, 20, (int) mouseX, (int) mouseY)) {
                updateColor(GuiUtils.getRandomColor() & 0xffffff);
                playClick();
                return true;
            } else
                for (int i = 0; i < DyeColor.values().length; ++i)
                    if (GuiUtils.isHover(width / 2 - 200 + (i % 2) * 19, height / 2 - 76 + (i / 2) * 19, 19, 19,
                            (int) mouseX, (int) mouseY)) {
                        updateColor(DyeColor.values()[i].getFireworkColor());
                        playClick();
                        return true;
                    }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        setColor((int) event.x(), (int) event.y(), drag);
        return super.mouseDragged(event, dx, dy);
    }

    private void updateColor(int h, int s, int l) {
        updateColor(h % 360, s, l, GuiUtils.fromHSL(h % 360, s, l));
    }

    private void updateColor(int rgba) {
        var hsl = GuiUtils.hslFromRGBA(rgba, localHue, localSaturation);
        updateColor(hsl.hue(), hsl.saturation(), hsl.lightness(), rgba);
    }

    private void updateColor(int h, int s, int l, int rgba) {
        if (isUpdating) return;
        isUpdating = true;
        localHue = h;
        localSaturation = s;
        localLightness = l;
        
        // Update saved defaults
        savedHue = h;
        savedSaturation = s;
        savedLightness = l;

        tfh.setValue("" + localHue);
        tfs.setValue("" + localSaturation);
        tfl.setValue("" + localLightness);
        updatePickerTexture(localHue, localSaturation, localLightness);

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

        switch (drag) {
            case HL -> {
                // hue
                var hue = GuiUtils.clamp(mouseX - (width / 2 - 158), 0, 158 + 176) * 360 / (158 + 176 + 1);
                // lightness
                var lightness = GuiUtils.clamp(mouseY - (height / 2 - 76), 0, 76 * 2) * 100 / (76 * 2);
                updateColor(hue, texSaturation, lightness);
            }
            case S -> {
                var saturation = GuiUtils.clamp(mouseY - (height / 2 - 76), 0, 76 * 2) * 100 / (76 * 2);
                updateColor(texHue, saturation, texLightness);
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
        updateColor(v, texSaturation, texLightness);
    }

    private void updateSaturation(int v) {
        v = GuiUtils.clamp(v, 0, 100);
        updateColor(texHue, v, texLightness);
    }

    private void updateLightness(int v) {
        v = GuiUtils.clamp(v, 0, 100);
        updateColor(texHue, texSaturation, v);
    }

    @Override
    protected void generateDev(List<ACTDevInfo> entries, int mouseX, int mouseY) {
        entries.add(devInfo("HEX", "#" + Integer.toHexString((color & 0xFFFFFF) | 0xF000000).substring(1)));
        entries.add(devInfo("HSL", texHue + "/" + texSaturation + "/" + texLightness));
        var res = GuiUtils.rgbaFromRGBA(color);
        entries.add(devInfo("RGB", res.red() + "/" + res.green() + "/" + res.blue()));
        super.generateDev(entries, mouseX, mouseY);
    }
}
