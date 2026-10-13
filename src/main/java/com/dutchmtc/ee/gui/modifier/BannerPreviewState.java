package com.dutchmtc.ee.gui.modifier;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import org.joml.Vector3f;

public record BannerPreviewState(
        ItemStackRenderState model,
        Vector3f center,
        boolean shield,
        float yaw,
        float pitch,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds)
        implements PictureInPictureRenderState {
    public BannerPreviewState(
            ItemStackRenderState model,
            Vector3f center,
            boolean shield,
            float yaw,
            float pitch,
            int x0,
            int y0,
            int x1,
            int y1,
            float scale,
            ScreenRectangle scissorArea) {
        this(
                model,
                center,
                shield,
                yaw,
                pitch,
                x0,
                y0,
                x1,
                y1,
                scale,
                scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}
