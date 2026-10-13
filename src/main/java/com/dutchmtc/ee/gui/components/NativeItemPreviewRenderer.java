package com.dutchmtc.ee.gui.components;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class NativeItemPreviewRenderer
        extends PictureInPictureRenderer<NativeItemPreviewState> {
    @Override
    public Class<NativeItemPreviewState> getRenderStateClass() {
        return NativeItemPreviewState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "ee_native_item_preview";
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2f;
    }

    @Override
    public void prepare(
            NativeItemPreviewState state,
            GuiRenderState gui,
            FeatureRenderDispatcher features,
            int guiScale) {
        var previousLights = RenderSystem.getShaderLights();
        try {
            super.prepare(state, gui, features, guiScale);
        } finally {
            RenderSystem.setShaderLights(previousLights);
        }
    }

    @Override
    protected void renderToTexture(
            NativeItemPreviewState state, PoseStack pose, SubmitNodeCollector nodes) {
        Minecraft.getInstance()
                .gameRenderer
                .lighting()
                .setupFor(
                        state.model().usesBlockLight()
                                ? Lighting.Entry.ITEMS_3D
                                : Lighting.Entry.ITEMS_FLAT);
        // The PIP projection looks along the opposite depth axis to Minecraft's item atlas.
        // Match the native OversizedItemRenderer to keep the GUI model facing the camera.
        pose.scale(1, -1, -1);
        pose.translate(-state.center().x, -state.center().y, -state.center().z);
        state.model().submit(pose, nodes, 15728880, OverlayTexture.NO_OVERLAY, 0);
    }
}
