package fr.atesab.act.gui;

import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ReflectionUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class GuiACT extends Screen {
    public record ACTDevInfo(String title, String[] elements) {
    }

    protected static ACTDevInfo devInfo(String title, String... elements) {
        return new ACTDevInfo(title, elements);
    }

    private static boolean devMode = false;

    public static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    protected static boolean isDevModeEnabled() {
        return devMode;
    }

    protected Screen parent;
    protected Minecraft mc;

    public GuiACT(Screen parent, Component name) {
        super(name);
        this.parent = parent;
        mc = Minecraft.getInstance();
    }

    /**
     * @return the formatted title
     */
    public String getStringTitle() {
        return super.title.getString();
    }

    public Screen getParent() {
        return parent;
    }

    public void setParent(Screen parent) {
        this.parent = parent;
    }

    public float getZLevel() {
        return 0; // getBlitOffset() is gone, usually 0 or handled by pose stack
    }

    public void setZLever(float zLevel) {
        // super.setBlitOffset((int) zLevel); // Gone
    }

    public Minecraft getMinecraft() {
        return mc;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_K && event.hasControlDown() && event.hasShiftDown()) {
            devMode ^= true; // toggle dev mode
        }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (devMode) {
            var entries = new ArrayList<ACTDevInfo>();
            entries.add(devInfo(ChatFormatting.BOLD + "ACT Dev")); // header
            entries.add(devInfo("Menu", ReflectionUtils.superClassTo(this.getClass(), GuiACT.class).stream()
                    .map(Class::getSimpleName).toArray(String[]::new))); // menu name
            generateDev(entries, mouseX, mouseY); // fetch from the menu
            entries.add(devInfo("Mouse", "LX/LY: " + (mouseX + "/" + mouseY),
                    "CX/CY: " + (mouseX - width / 2) + "/" + (mouseY - height / 2),
                    "RX/RY: " + (mouseX - width) + "/" + (mouseY - height)));
            // render
            var lines = entries.stream().mapToInt(dev -> 1 + dev.elements().length).sum();
            var w = entries.stream().mapToInt(dev -> Math.max(font.width(dev.title()),
                    Arrays.stream(dev.elements()).mapToInt(font::width).max().orElse(0))).max().getAsInt();
            var x = width - w - 4;
            var y = 4;
            GuiUtils.drawGradientRect(graphics, x - 4, 0, width, y + lines * (font.lineHeight + 2) + 4, 0x44000000,
                    0x44000000);
            for (var dev : entries) {
                GuiUtils.drawCenterString(graphics, font, dev.title(), x + w / 2, y, Objects.requireNonNull(ChatFormatting.RED.getColor()));
                y += font.lineHeight + 2;
                for (var element : dev.elements()) {
                    GuiUtils.drawString(graphics, font, element, x, y, 0xFFFFFFFF, font.lineHeight);
                    y += font.lineHeight + 2;
                }
            }
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    /**
     * generate the dev info list
     *
     * @param entries the entries added to the dev mode
     * @param mouseX  the mouseX
     * @param mouseY  the mouseY
     */
    protected void generateDev(List<ACTDevInfo> entries, int mouseX, int mouseY) {
        // fill by implementation
    }
}
