package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import org.joml.Quaternionf;

public final class MobStackPreviewRenderer extends PictureInPictureRenderer<MobStackPreviewState> {
    @Override
    public Class<MobStackPreviewState> getRenderStateClass() {
        return MobStackPreviewState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "mob_stack_preview";
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2f;
    }

    @Override
    protected void renderToTexture(
            MobStackPreviewState state, PoseStack pose, SubmitNodeCollector nodes) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        var pitch = new Quaternionf().rotateX(state.pitch() * .017453292f);
        pose.rotate(
                new Quaternionf()
                        .rotateZ(state.displayView() ? 0 : (float) Math.PI)
                        .rotateX(state.displayView() ? (float) Math.PI : 0)
                        .mul(pitch)
                        .rotateY(-state.yaw() * .017453292f));
        pose.translate(0, -state.centerY(), 0);
        var camera = new CameraRenderState();
        camera.orientation = new Quaternionf(pitch).conjugate().rotateY((float) Math.PI);
        for (var model : state.models()) {
            pose.pushPose();
            pose.translate(model.position().x, model.position().y, model.position().z);
            mc.getEntityRenderDispatcher().submit(model.state(), camera, 0, 0, 0, pose, nodes);
            pose.popPose();
        }
    }
}
