package com.dutchmtc.ee.utils;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Helpers for rendering entities inside a fixed UI box.
 */
public final class GuiEntityPreviewUtils {
    private static final AtomicInteger NEXT_PREVIEW_ENTITY_ID = new AtomicInteger(1_000_000_000);

    private GuiEntityPreviewUtils() {
    }

    public static void renderLivingEntityInBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int scale, float yOffset,
                                              float yawOffsetDeg, float pitchDeg, LivingEntity entity) {
        if (graphics == null || entity == null) {
            return;
        }

        EntityRenderState state = extractRenderState(entity);

        // Some states (notably ArmorStand markers) can report 0x0 bounds, which breaks centering.
        float stableWidth = state.boundingBoxWidth;
        float stableHeight = state.boundingBoxHeight;
        if (stableWidth <= 1.0e-4f || stableHeight <= 1.0e-4f) {
            EntityType<?> type = entity.getType();
            var dims = type.getDimensions();
            stableWidth = dims.width();
            stableHeight = dims.height();
            state.boundingBoxWidth = stableWidth;
            state.boundingBoxHeight = stableHeight;
        }

        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0f + yawOffsetDeg;
            living.yRot = yawOffsetDeg;
            living.xRot = living.pose != Pose.FALL_FLYING ? pitchDeg : 0.0f;

            // Normalize bounding box dimensions to world scale (matches vanilla InventoryScreen behavior)
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1.0f;
        }

        Quaternionf base = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf pitchRot = new Quaternionf().rotateX(pitchDeg * 0.017453292f);
        base.mul(pitchRot);

        Vector3f translate = new Vector3f(0.0f, state.boundingBoxHeight / 2.0f + yOffset, 0.0f);
        graphics.entity(state, (float) scale, translate, base, pitchRot, x, y, x + width, y + height);
    }

    private static EntityRenderState extractRenderState(LivingEntity entity) {
        ensurePreviewEntityId(entity);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        @SuppressWarnings("rawtypes")
        EntityRenderer renderer = dispatcher.getRenderer(entity);
        @SuppressWarnings("unchecked")
        EntityRenderState state = ((EntityRenderer<LivingEntity, EntityRenderState>) renderer).createRenderState(entity, 1.0f);

        state.lightCoords = 15728880;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        return state;
    }

    private static void ensurePreviewEntityId(LivingEntity entity) {
        try {
            entity.getId();
        } catch (IllegalStateException ignored) {
            // 26.2 render-state extraction reads the entity id for item model state.
            // GUI preview entities are never spawned into a level, so assign a local id first.
            entity.setId(NEXT_PREVIEW_ENTITY_ID.getAndIncrement());
        }
    }
}
