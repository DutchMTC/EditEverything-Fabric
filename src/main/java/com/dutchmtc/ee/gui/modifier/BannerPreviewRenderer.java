package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;

import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/** Full native banner/shield models, independent of the inventory icon's size and orientation. */
public final class BannerPreviewRenderer extends PictureInPictureRenderer<BannerPreviewState> {
    private final GpuBuffer[] lighting = new GpuBuffer[2];

    @Override
    public void prepare(
            BannerPreviewState state,
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

    private void setupLighting(boolean shield) {
        int index = shield ? 1 : 0;
        if (lighting[index] == null) {
            // Camera-facing lights illuminate the visible cloth without changing its dye colors.
            // Keep this buffer private: other GUI previews retain Minecraft's usual lighting.
            try (var stack = MemoryStack.stackPush()) {
                float front = shield ? 1 : -1;
                var data =
                        Std140Builder.onStack(stack, Lighting.UBO_SIZE)
                                .putVec3(new Vector3f(.2f, -.3f, front).normalize())
                                .putVec3(new Vector3f(-.2f, -.3f, front).normalize())
                                .get();
                lighting[index] =
                        RenderSystem.getDevice()
                                .createBuffer(
                                        () -> "Banner preview lighting",
                                        GpuBuffer.USAGE_UNIFORM,
                                        data);
            }
        }
        RenderSystem.setShaderLights(lighting[index].slice());
    }

    @Override
    public void close() {
        super.close();
        for (int i = 0; i < lighting.length; i++) {
            if (lighting[i] != null) {
                lighting[i].close();
                lighting[i] = null;
            }
        }
    }

    @Override
    public Class<BannerPreviewState> getRenderStateClass() {
        return BannerPreviewState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "banner_editor_preview";
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2f;
    }

    @Override
    protected void renderToTexture(
            BannerPreviewState state, PoseStack pose, SubmitNodeCollector nodes) {
        setupLighting(state.shield());
        // Shield geometry needs its native depth orientation in the GUI projection.
        pose.scale(1, -1, state.shield() ? -1 : 1);
        pose.rotate(
                new Quaternionf()
                        .rotateX(state.pitch() * .017453292f)
                        .rotateY(-state.yaw() * .017453292f));
        pose.translate(-state.center().x, -state.center().y, -state.center().z);
        state.model().submit(pose, nodes, 15728880, OverlayTexture.NO_OVERLAY, 0);
    }
}
