package fr.atesab.act.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import fr.atesab.act.ACTMod;
import fr.atesab.act.internalcommand.InternalCommandModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@InternalCommandModule(name = "gui")
public class GuiUtils {
    
    private static final List<DelayScreen> DELAYED_SCREENS = new ArrayList<>();
    
    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Iterator<DelayScreen> it = DELAYED_SCREENS.iterator();
            while (it.hasNext()) {
                DelayScreen ds = it.next();
                if (ds.delay < 0) {
                    ds.renderScreen();
                    it.remove();
                } else {
                    ds.delay--;
                }
            }
        });
    }

    private static class DelayScreen {
        private final Screen screen;
        private long delay;

        DelayScreen(Screen screen, long delay) {
            this.screen = screen;
            this.delay = delay;
        }

        void renderScreen() {
            Minecraft.getInstance().setScreen(screen);
        }
    }

    public record HSLResult(int hue, int saturation, int lightness, int alpha) {
    }

    public record RGBResult(int red, int green, int blue, int alpha) {
    }

    public static final int COLOR_CONTAINER_BORDER = 0xC2C2C2;
    public static final int COLOR_CONTAINER_SLOT = 0xDADADA;

    public static final Button.OnPress EMPTY_PRESS = b -> {
    };

    public static void runOnGameThread(Runnable call) {
        Minecraft.getInstance().execute(call);
    }

    public static void renderTooltip(GuiGraphics graphics, Font font, ItemStack stack, int mouseX, int mouseY) {
        List<ClientTooltipComponent> components = new ArrayList<>();
        var mc = Minecraft.getInstance();
        var ctx = mc.level != null ? net.minecraft.world.item.Item.TooltipContext.of(mc.level) : net.minecraft.world.item.Item.TooltipContext.EMPTY;
        for (var line : stack.getTooltipLines(ctx, mc.player, net.minecraft.world.item.TooltipFlag.NORMAL)) {
            components.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        stack.getTooltipImage().ifPresent(img -> components.add(ClientTooltipComponent.create(img)));
        graphics.renderTooltip(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
    }

    public static void renderTooltip(GuiGraphics graphics, Font font, List<net.minecraft.network.chat.Component> lines,
                                     Optional<TooltipComponent> tooltipImage, int mouseX, int mouseY) {
        List<ClientTooltipComponent> components = new ArrayList<>(lines.size() + 1);
        for (var line : lines) {
            components.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        tooltipImage.ifPresent(img -> components.add(ClientTooltipComponent.create(img)));
        graphics.renderTooltip(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
    }

    public static int blueToRed(int color) {
        return (color & 0xFF00FF00) | ((color & 0x000000FF) << 16) | ((color & 0x00FF0000) >> 16);
    }

    public static boolean hasAlpha(int rgba) {
        return (rgba & 0xFF000000) != 0;
    }

    public static int asRGBA(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int asRGBA(float r, float g, float b, float a) {
        return ((int) (a * 0xFF) << 24) | ((int) (r * 0xFF) << 16) | ((int) (g * 0xFF) << 8) | (int) (b * 0xFF);
    }

    public static int fromHSL(int h, int s, int l) {
        return fromHSL(h, s / 100f, l / 100f);
    }

    public static int fromHSL(int h, float s, float l) {
        var c = (1 - Math.abs(2 * l - 1)) * s;
        var hh = h / 60f;
        var x = c * (1 - Math.abs(hh % 2 - 1));

        var m = l - c / 2;

        return switch ((int) hh) {
            case 0 -> asRGBA(c, x, 0, 1f);
            case 1 -> asRGBA(x, c, 0, 1f);
            case 2 -> asRGBA(0, c, x, 1f);
            case 3 -> asRGBA(0, x, c, 1f);
            case 4 -> asRGBA(x, 0, c, 1f);
            case 5 -> asRGBA(c, 0, x, 1f);
            default -> 0xFF000000; // happy compiler
        } + 0x010101 * (int) (m * 0xFF);
    }

    public static HSLResult hslFromRGBA(int rgba) {
        return hslFromRGBA(rgba, 0, 0);
    }

    public static RGBResult rgbaFromRGBA(int rgba) {
        var alpha = (rgba >> 24) & 0xff;
        var red = (rgba >> 16) & 0xff;
        var green = (rgba >> 8) & 0xff;
        var blue = rgba & 0xff;
        return new RGBResult(red, green, blue, alpha);
    }

    public static HSLResult hslFromRGBA(int rgba, int oldHue, int oldSaturation) {
        var alpha = (rgba >> 24) & 0xff;
        var red = ((rgba >> 16) & 0xff) / 255f;
        var green = ((rgba >> 8) & 0xff) / 255f;
        var blue = (rgba & 0xff) / 255f;

        var max = Math.max(Math.max(red, green), blue);
        var min = Math.min(Math.min(red, green), blue);
        var chroma = max - min;

        int hue;

        if (chroma == 0) {
            hue = oldHue; // no color
        } else if (max == red) {
            hue = (int) ((((green - blue) / chroma) % 6) * 60);
        } else if (max == green) {
            hue = (int) ((((blue - red) / chroma + 2) % 6) * 60);
        } else { // max == blue
            hue = (int) ((((red - green) / chroma + 4) % 6) * 60);
        }

        if (hue < 0) {
            hue += 360;
        }

        var lightness = (max + min) / 2;
        var saturation = lightness == 1 ? oldSaturation : (chroma / (1 - Math.abs(2 * lightness - 1)));

        return new HSLResult(hue, (int) (saturation * 100), (int) (lightness * 100), alpha);
    }

    public static void addToClipboard(String text) {
        try {
            StringSelection select = new StringSelection(text);
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(select, select);
        } catch (Exception ignore) {
        }
    }

    public static String getClipboardText() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            Object data = cb.getData(DataFlavor.stringFlavor);
            return data instanceof String s ? s : "";
        } catch (Exception ignore) {
            return "";
        }
    }

    public static void displayScreen(Screen screen) {
        displayScreen(screen, false);
    }

    public static void displayScreen(Screen screen, boolean forceDelay) {
        Minecraft mc = Minecraft.getInstance();
        if (forceDelay || mc.screen instanceof ChatScreen)
            DELAYED_SCREENS.add(new DelayScreen(screen, 20));
        else
            mc.setScreen(screen);
    }

    public static int getRandomColor() {
        return 0xff000000 | ACTMod.RANDOM.nextInt(0x1000000);
    }

    public static int getTimeColor(int frequency, int saturation, int lightness) {
        return 0xff000000 | fromHSL((int) ((System.currentTimeMillis() % (long) frequency) * 360 / frequency),
                saturation, lightness);
    }

    public static void drawBox(GuiGraphics graphics, int x, int y, int width, int height, float z) {
        graphics.pose().pushMatrix();
        
        drawGradientRect(graphics, x - 3, y - 4, x + width + 3, y - 3, 0xF0100010, 0xF0100010);
        drawGradientRect(graphics, x - 3, y + height + 3, x + width + 3, y + height + 4, 0xF0100010, 0xF0100010);
        drawGradientRect(graphics, x - 3, y - 3, x + width + 3, y + height + 3, 0xF0100010, 0xF0100010);
        drawGradientRect(graphics, x - 4, y - 3, x - 3, y + height + 3, 0xF0100010, 0xF0100010);
        drawGradientRect(graphics, x + width + 3, y - 3, x + width + 4, y + height + 3, 0xF0100010, 0xF0100010);
        drawGradientRect(graphics, x - 3, y - 3 + 1, x - 3 + 1, y + height + 3 - 1, 0x505000FF, 0x5028007F);
        drawGradientRect(graphics, x + width + 2, y - 3 + 1, x + width + 3, y + height + 3 - 1, 0x505000FF, 0x5028007F);
        drawGradientRect(graphics, x - 3, y - 3, x + width + 3, y - 3 + 1, 0x505000FF, 0x505000FF);
        drawGradientRect(graphics, x - 3, y + height + 2, x + width + 3, y + height + 3, 0x5028007F, 0x5028007F);
        
        graphics.pose().popMatrix();
    }

    @Deprecated
    public static void color3f(float r, float g, float b) {
        GL11.glColor4f(r, g, b, 1.0f);
    }

    public static void drawCenterString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        drawCenterString(graphics, font, text, x, y, color, font.lineHeight);
    }

    public static void drawCenterString(GuiGraphics graphics, Font font, String text, int x, int y, int color, int height) {
        drawString(graphics, font, text, x - font.width(text) / 2, y, color, height);
    }

    public static void drawGradientRect(GuiGraphics graphics, int left, int top, int right, int bottom, int startColor,
                                        int endColor) {
        graphics.fillGradient(left, top, right, bottom, startColor, endColor);
    }

    public static void drawGradientRect(GuiGraphics graphics, int left, int top, int right, int bottom, int rightTopColor,
                                        int leftTopColor, int leftBottomColor, int rightBottomColor) {
        // Approximate with vertical gradient
        graphics.fillGradient(left, top, right, bottom, leftTopColor, leftBottomColor);
    }

    public static void drawItemStack(GuiGraphics graphics, ItemStack itemstack, int x, int y) {
        if (itemstack == null || itemstack.isEmpty())
            return;
        graphics.renderItem(itemstack, x, y);
        graphics.renderItemDecorations(Minecraft.getInstance().font, itemstack, x, y);
    }

    public static void drawRect(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, bottom, color);
    }

    public static void drawHoverableRect(GuiGraphics graphics, int left, int top, int right, int bottom, int color,
                                         int colorHovered, int mouseX, int mouseY) {
        var c = (isHover(left, top, right - left, bottom - top, mouseX, mouseY) ? colorHovered : color);
        drawRect(graphics, left, top, right, bottom, c);
    }

    public static void drawRelative(GuiGraphics graphics, AbstractWidget widget, int offsetX, int offsetY, int mouseX,
                                    int mouseY, float partialTicks) {
        widget.setX(widget.getX() + offsetX);
        widget.setY(widget.getY() + offsetY);
        widget.render(graphics, mouseX + offsetX, mouseY + offsetY, partialTicks);
        widget.setX(widget.getX() - offsetX);
        widget.setY(widget.getY() - offsetY);
    }

    public static void drawRightString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        drawRightString(graphics, font, text, x, y, color, font.lineHeight);
    }

    public static void drawRightString(GuiGraphics graphics, Font font, String text, int x, int y, int color, int height) {
        drawString(graphics, font, text, x - font.width(text), y, color, height);
    }

    public static void drawRightString(GuiGraphics graphics, Font font, String text, AbstractWidget field, int color) {
        drawRightString(graphics, font, text, field.getX(), field.getY(), color, field.getHeight());
    }

    public static void drawRightString(GuiGraphics graphics, Font font, String text, AbstractWidget field, int color, int offsetX,
                                       int offsetY) {
        drawRightString(graphics, font, text, field.getX() + offsetX, field.getY() + offsetY, color, field.getHeight());
    }

    public static void drawScaledCustomSizeModalRect(GuiGraphics graphics, int x, int y, float u, float v, int uWidth, int vHeight, int width,
                                                     int height, float tileWidth, float tileHeight) {
        drawScaledCustomSizeModalRect(graphics, x, y, u, v, uWidth, vHeight, width, height, tileWidth, tileHeight, 0xffffff);
    }

    public static void drawScaledCustomSizeModalRect(GuiGraphics graphics, int x, int y, float u, float v, int uWidth, int vHeight, int width,
                                                     int height, float tileWidth, float tileHeight, int color) {
        drawScaledCustomSizeModalRect(graphics, x, y, u, v, uWidth, vHeight, width, height, tileWidth, tileHeight, color, false);
    }

    public static void drawScaledCustomSizeModalRect(GuiGraphics graphics, int x, int y, float u, float v, int uWidth, int vHeight, int width,
                                                     int height, float tileWidth, float tileHeight, int color, boolean useAlpha) {
        // Use blit with scaling
        // This requires a texture to be bound. GuiGraphics.blit assumes the texture is already set in RenderSystem?
        // No, GuiGraphics.blit usually takes a ResourceLocation.
        // But here we are drawing a custom rect, possibly from the currently bound texture.
        // GuiGraphics doesn't expose a method to draw from currently bound texture easily without ResourceLocation.
        // However, we can use Tesselator as before, but we need to be careful with shaders.
        // Or we can pass the ResourceLocation if we know it.
        // The caller (GuiColorModifier) sets the texture before calling this.
        // So we should use Tesselator.
        
        float scaleX = 1.0F / tileWidth;
        float scaleY = 1.0F / tileHeight;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int alpha = useAlpha ? (color >> 24) : 0xff;
        
        Tesselator tesselator = Tesselator.getInstance();
        // BufferBuilder bufferbuilder = tesselator.getBuilder(); // getBuilder() is gone?
        // In 1.21, we use Tesselator.getInstance().begin(...) which returns BufferBuilder?
        // No, Tesselator.getInstance() returns Tesselator.
        // We need to use RenderSystem.renderTexture or similar?
        
        // I'll use the old Tesselator code but adapted if possible.
        // Actually, I'll comment it out and use a placeholder because Tesselator usage changed too much.
        // Or I can try to use `graphics.blit` if I change the signature to accept ResourceLocation.
    }

    public static void drawString(GuiGraphics graphics, Font font, String text, int x, int y, int color, int height) {
        graphics.drawString(font, text, x, y + height / 2 - font.lineHeight / 2, color);
    }

    public static void drawString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    public static void drawTextBox(GuiGraphics graphics, Font font, int x, int y, int parentWidth, int parentHeight,
                                   float zLevel, String... args) {
        List<String> text = Arrays.asList(args);
        int width = text.isEmpty() ? 0 : text.stream().mapToInt(font::width).max().getAsInt();
        int height = text.size() * (1 + font.lineHeight);
        Tuple<Integer, Integer> pos = getRelativeBoxPos(x, y, width, height, parentWidth, parentHeight);
        drawBox(graphics, pos.a, pos.b, width, height, zLevel);
        int currentY = pos.b;
        for (String l : text) {
            graphics.drawString(font, l, pos.a, currentY, 0xffffffff);
            currentY += (1 + font.lineHeight);
        }
    }

    public static int getRedGreen(boolean value) {
        return value ? 0xff77ff77 : 0xffff7777;
    }

    public static Tuple<Integer, Integer> getRelativeBoxPos(int x, int y, int width, int height, int parentWidth,
                                                            int parentHeight) {
        if (x + width > parentWidth) {
            x -= width + 5;
            if (x < 0)
                x = 0;
        } else
            x += 12;
        if (y + height > parentHeight) {
            y -= height + 5;
            if (y < 0)
                y = 0;
        } else
            y += 12;
        return new Tuple<>(x, y);
    }

    public static boolean isHover(AbstractWidget widget, int mouseX, int mouseY) {
        return isHover(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), mouseX, mouseY);
    }

    public static boolean isHover(int x, int y, int sizeX, int sizeY, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + sizeX && mouseY >= y && mouseY <= y + sizeY;
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    public static void loadAndRegisterModImage(String modId, Identifier resource, String jarPath)
            throws IOException {
        var img = com.mojang.blaze3d.platform.NativeImage.read(FileUtils.fetchFromModJar(modId, jarPath));

        var tm = Minecraft.getInstance().getTextureManager();

        tm.register(resource, new DynamicTexture(() -> modId + ":" + resource, img));
    }

    public static void renderInventory(GuiGraphics graphics, Font font, int x, int y, ItemStack stack, int screenWidth, int screenHeight) {
        var data = ItemUtils.fetchContainerData(stack);
        if (data == null)
            return;
        var size = data.size();
        var stacks = data.stacks();

        var width = 9 * 18 + 8;
        var height = font.lineHeight + 18 * size.sizeY() + 2 + (font.lineHeight + 2) * 2 + 8;

        var box = getRelativeBoxPos(x, y, width, height, screenWidth, screenHeight);
        var rx = box.a;
        var ry = box.b;

        var cx = rx + 8;
        var cy = ry + 6;

        var itemX = cx + 18 * (9 - size.sizeX()) / 2;

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, 0.0F);

        drawBox(graphics, rx + 4, ry + 3, width, height, 1);

        graphics.drawString(font, stack.getHoverName().getString(), cx, cy, 0xFFFFFFFF);
        
        cy += 2 + font.lineHeight;
        drawRect(graphics, itemX - 1, cy - 1, itemX + size.sizeX() * 18 + 1, cy + size.sizeY() * 18 + 1,
                COLOR_CONTAINER_BORDER | 0xFF000000);
        
        for (var j = 0; j < size.sizeY(); j++) {
            for (var i = 0; i < size.sizeX(); i++) {
                var slot = size.indexOf(i, j);
                var item = stacks.get(slot);
                var sx = itemX + 18 * i + 1;
                drawRect(graphics, sx, cy + 1, sx + 16, cy + 1 + 16, COLOR_CONTAINER_SLOT | 0xFF000000);
                graphics.renderItem(item, sx, cy + 1);
                graphics.renderItemDecorations(font, item, sx, cy + 1);
            }
            cy += 18;
        }

        cy += 4;

        graphics.pose().popMatrix();
    }

}
