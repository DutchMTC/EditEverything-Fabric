package com.dutchmtc.ee.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Native Minecraft assets for the inline effect controls. */
public enum EEEffectIcon {
    INFINITE,
    PARTICLES,
    STATUS_ICON,
    AMBIENT;

    private static final ItemStack CLOCK = new ItemStack(Items.CLOCK);
    private static final ItemStack BEACON = new ItemStack(Items.BEACON);
    private static final Identifier PARTICLE =
            Identifier.withDefaultNamespace("textures/particle/effect_5.png");

    public void render(
            GuiGraphicsExtractor g, int x, int y, int width, int height, boolean enabled) {
        int size = this == STATUS_ICON ? 18 : 16;
        int scale = Math.min(width / size, height / size);
        if (scale < 1) return;
        g.pose().pushMatrix();
        try {
            g.pose().translate(x + (width - size * scale) / 2F, y + (height - size * scale) / 2F);
            g.pose().scale(scale, scale);
            switch (this) {
                case INFINITE -> g.fakeItem(CLOCK, 0, 0);
                case AMBIENT -> g.fakeItem(BEACON, 0, 0);
                case PARTICLES ->
                        g.blit(
                                RenderPipelines.GUI_TEXTURED,
                                PARTICLE,
                                0,
                                0,
                                0,
                                0,
                                16,
                                16,
                                8,
                                8,
                                8,
                                8,
                                enabled ? 0xFFB178E4 : EETheme.MUTED);
                case STATUS_ICON -> renderHud(g, enabled);
            }
        } finally {
            g.pose().popMatrix();
        }
    }

    /** Retain the compact HUD frame; the caller overlays the native effect sprite. */
    private static void renderHud(GuiGraphicsExtractor g, boolean enabled) {
        int light = enabled ? 0xFFC5CCBA : EETheme.MUTED;
        int dark = enabled ? 0xFF666B60 : EETheme.MUTED;
        int corner = enabled ? 0xFF31352F : EETheme.MUTED;
        EETheme.rect(g, 0, 0, 18, 18, enabled ? 0xFF525B4C : EETheme.MUTED);
        EETheme.rect(g, 0, 0, 17, 1, light);
        EETheme.rect(g, 0, 0, 1, 17, light);
        EETheme.rect(g, 1, 17, 17, 1, dark);
        EETheme.rect(g, 17, 1, 1, 17, dark);
        EETheme.rect(g, 17, 0, 1, 1, corner);
        EETheme.rect(g, 0, 17, 1, 1, corner);
    }
}
