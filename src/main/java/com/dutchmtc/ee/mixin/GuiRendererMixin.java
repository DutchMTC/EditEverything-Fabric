package com.dutchmtc.ee.mixin;

import com.dutchmtc.ee.gui.modifier.SignBlockPreviewRenderer;
import com.dutchmtc.ee.gui.modifier.SignBlockPreviewState;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableMap$Builder;buildOrThrow()Lcom/google/common/collect/ImmutableMap;"))
    private com.google.common.collect.ImmutableMap<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> ee$registerSignPreview(
            com.google.common.collect.ImmutableMap.Builder<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> builder) {
        return builder.put(SignBlockPreviewState.class, new SignBlockPreviewRenderer())
                .put(com.dutchmtc.ee.gui.modifier.BannerPreviewState.class,
                        new com.dutchmtc.ee.gui.modifier.BannerPreviewRenderer())
                .put(com.dutchmtc.ee.gui.modifier.MobStackPreviewState.class,
                        new com.dutchmtc.ee.gui.modifier.MobStackPreviewRenderer())
                .put(com.dutchmtc.ee.gui.components.NativeItemPreviewState.class,
                        new com.dutchmtc.ee.gui.components.NativeItemPreviewRenderer()).buildOrThrow();
    }
}
