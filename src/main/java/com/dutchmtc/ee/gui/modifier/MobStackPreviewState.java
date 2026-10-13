package com.dutchmtc.ee.gui.modifier;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import org.joml.Vector3f;

import java.util.List;

/** One scene keeps all native models in the same depth buffer and camera. */
public record MobStackPreviewState(
        List<Model> models,
        float centerY,
        float yaw,
        float pitch,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        boolean displayView,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds)
        implements PictureInPictureRenderState {
    public record Model(EntityRenderState state, Vector3f position) {}

    public MobStackPreviewState(
            List<Model> models,
            float centerY,
            float yaw,
            float pitch,
            int x0,
            int y0,
            int x1,
            int y1,
            float scale,
            ScreenRectangle scissorArea) {
        this(
                List.copyOf(models),
                centerY,
                yaw,
                pitch,
                x0,
                y0,
                x1,
                y1,
                scale,
                false,
                scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }

    public MobStackPreviewState(
            List<Model> models,
            float centerY,
            float yaw,
            float pitch,
            int x0,
            int y0,
            int x1,
            int y1,
            float scale,
            ScreenRectangle scissorArea,
            boolean displayView) {
        this(
                List.copyOf(models),
                centerY,
                yaw,
                pitch,
                x0,
                y0,
                x1,
                y1,
                scale,
                displayView,
                scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}
