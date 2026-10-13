package com.dutchmtc.ee.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.joml.Vector3f;

/** Native item geometry rendered at the preview's resolution, bypassing the 16px icon atlas. */
public record NativeItemPreviewState(
        ItemStackRenderState model,
        Vector3f center,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds)
        implements PictureInPictureRenderState {
    public static void draw(
            GuiGraphicsExtractor g, ItemStack item, int x, int y, int width, int height) {
        var mc = Minecraft.getInstance();
        var model = new ItemStackRenderState();
        mc.getItemModelResolver()
                .updateForTopItem(model, item, ItemDisplayContext.GUI, mc.level, null, 0);
        if (model.isEmpty()) return;
        var bounds = model.getModelBoundingBox();
        var center = bounds.getCenter();
        float scale =
                (float)
                        Math.min(
                                (width - 6) / Math.max(.01, bounds.getXsize()),
                                (height - 6) / Math.max(.01, bounds.getYsize()));
        var clip = g.scissorStack.peek();
        g.guiRenderState.addPicturesInPictureState(
                new NativeItemPreviewState(
                        model,
                        new Vector3f((float) center.x, (float) center.y, (float) center.z),
                        x,
                        y,
                        x + width,
                        y + height,
                        scale,
                        clip,
                        PictureInPictureRenderState.getBounds(x, y, x + width, y + height, clip)));
    }
}
