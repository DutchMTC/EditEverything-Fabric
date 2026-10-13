package com.dutchmtc.ee.gui.modifier;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

public record SignBlockPreviewState(BlockModelRenderState model,
                                    float rotation, int x0, int y0, int x1, int y1,
                                    float scale, ScreenRectangle scissorArea,
                                    ScreenRectangle bounds) implements PictureInPictureRenderState {
    public SignBlockPreviewState(BlockModelRenderState model, float rotation,
                                 int x0, int y0, int x1, int y1, float scale,
                                 ScreenRectangle scissorArea) {
        this(model, rotation, x0, y0, x1, y1, scale, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}
