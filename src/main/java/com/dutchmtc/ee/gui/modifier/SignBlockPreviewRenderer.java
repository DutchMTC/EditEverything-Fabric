package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;

/** Draws the placed block model into a GUI preview. */
public final class SignBlockPreviewRenderer extends PictureInPictureRenderer<SignBlockPreviewState> {
    @Override public Class<SignBlockPreviewState> getRenderStateClass() { return SignBlockPreviewState.class; }
    @Override protected String getTextureLabel() { return "sign_block_preview"; }
    @Override protected float getTranslateY(int height, int guiScale) { return height / 2f; }

    @Override
    protected void renderToTexture(SignBlockPreviewState state, PoseStack pose, SubmitNodeCollector nodes) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        pose.scale(1f, -1f, 1f);
        pose.rotate(new Quaternionf().rotateY(state.rotation()));
        pose.translate(-0.5f, -0.5f, -0.5f);
        state.model().submit(pose, nodes, 15728880, OverlayTexture.NO_OVERLAY, 0);
    }
}
