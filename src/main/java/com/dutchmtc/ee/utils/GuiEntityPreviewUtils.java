package com.dutchmtc.ee.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicInteger;

/** Helpers for rendering entities inside a fixed UI box. */
public final class GuiEntityPreviewUtils {
    private static final AtomicInteger NEXT_PREVIEW_ENTITY_ID = new AtomicInteger(1_000_000_000);

    private GuiEntityPreviewUtils() {}

    public static Entity prepareStackPreview(Entity entity) {
        ensurePreviewEntityId(entity);
        entity.setPos(0, 0, 0);
        entity.setCustomNameVisible(false);
        return entity;
    }

    public static void loadPreviewData(Entity entity, net.minecraft.nbt.CompoundTag data) {
        net.minecraft.world.item.component.TypedEntityData.of(entity.getType(), data)
                .loadInto(entity);
        restoreDisplayText(entity, data);
    }

    public static void restoreDisplayText(Entity entity, net.minecraft.nbt.CompoundTag data) {
        var level = Minecraft.getInstance().level;
        if (level != null
                && entity instanceof net.minecraft.world.entity.Display.TextDisplay text
                && data.get("text") != null) {
            net.minecraft.network.chat.ComponentSerialization.CODEC
                    .parse(
                            level.registryAccess()
                                    .createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                            data.get("text"))
                    .result()
                    .ifPresent(
                            value ->
                                    ((com.dutchmtc.ee.mixin.TextDisplayAccessor) text)
                                            .ee$setText(value));
        }
        var tags = data.getList("Passengers").orElse(new net.minecraft.nbt.ListTag());
        for (int i = 0; i < Math.min(tags.size(), entity.getPassengers().size()); i++)
            if (tags.get(i) instanceof net.minecraft.nbt.CompoundTag child)
                restoreDisplayText(entity.getPassengers().get(i), child);
    }

    /** Detached client entities do not receive vanilla's riding-position packets or ticks. */
    public static void positionStackPreview(Entity root) {
        for (Entity rider : root.getPassengers()) {
            var anchor =
                    root.getPassengerRidingPosition(rider)
                            .subtract(rider.getVehicleAttachmentPoint(root));
            rider.setPos(anchor);
            rider.setOldPosAndRot();
            positionStackPreview(rider);
        }
    }

    /** Native riding offsets and models, rendered in one shared camera and clipped preview box. */
    public static void renderStackInBox(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            float zoom,
            float yaw,
            float pitch,
            Entity root) {
        var entities = root.getSelfAndPassengers().toList();
        double minY = entities.stream().mapToDouble(e -> e.getY()).min().orElse(0);
        double maxY =
                entities.stream()
                        .mapToDouble(e -> e.getY() + Math.max(.3, e.getBbHeight()))
                        .max()
                        .orElse(1);
        double maxWidth =
                entities.stream()
                        .mapToDouble(
                                e ->
                                        Math.max(.3, e.getBbWidth())
                                                + Math.abs(e.getX() - root.getX()) * 2)
                        .max()
                        .orElse(1);
        float scale =
                (float)
                        Math.max(
                                1,
                                Math.min(
                                                (width - 12) / (maxWidth * 1.4),
                                                (height - 12) / ((maxY - minY) * 1.15))
                                        * zoom);
        var models =
                new java.util.ArrayList<com.dutchmtc.ee.gui.modifier.MobStackPreviewState.Model>();
        for (Entity entity : entities) {
            EntityRenderState state = extractRenderState(entity);
            if (state instanceof LivingEntityRenderState living) {
                living.bodyRot = 180;
                living.yRot = 0;
                living.xRot = 0;
            }
            Vector3f offset =
                    new Vector3f(
                            (float) (entity.getX() - root.getX()),
                            (float) entity.getY(),
                            (float) (entity.getZ() - root.getZ()));
            models.add(new com.dutchmtc.ee.gui.modifier.MobStackPreviewState.Model(state, offset));
        }
        graphics.guiRenderState.addPicturesInPictureState(
                new com.dutchmtc.ee.gui.modifier.MobStackPreviewState(
                        models,
                        (float) ((minY + maxY) / 2),
                        yaw,
                        pitch,
                        x,
                        y,
                        x + width,
                        y + height,
                        scale,
                        graphics.scissorStack.peek()));
    }

    public static void renderLivingEntityInBox(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int scale,
            float yOffset,
            float yawOffsetDeg,
            float pitchDeg,
            LivingEntity entity) {
        renderEntityInBox(
                graphics,
                x,
                y,
                width,
                height,
                scale,
                yOffset,
                yawOffsetDeg,
                pitchDeg,
                entity,
                true);
    }

    public static void renderEntityInBox(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int scale,
            float yOffset,
            float yawOffsetDeg,
            float pitchDeg,
            Entity entity) {
        if (entity instanceof net.minecraft.world.entity.Display display) {
            renderDisplayInBox(graphics, x, y, width, height, 1, yawOffsetDeg, pitchDeg, display);
            return;
        }
        renderEntityInBox(
                graphics,
                x,
                y,
                width,
                height,
                scale,
                yOffset,
                yawOffsetDeg,
                pitchDeg,
                entity,
                false);
    }

    /** Displays have no physical hitbox: fit and center their transformed rendered content. */
    public static void renderDisplayInBox(
            GuiGraphicsExtractor g,
            int x,
            int y,
            int width,
            int height,
            float zoom,
            float yaw,
            float pitch,
            net.minecraft.world.entity.Display entity) {
        if (entity.renderState() == null) entity.tick();
        var state = entity.renderState();
        if (state == null) return;
        float minX = -.5f, minY = -.5f, minZ = -.5f, maxX = .5f, maxY = .5f, maxZ = .5f;
        if (entity instanceof net.minecraft.world.entity.Display.BlockDisplay) {
            minX = minY = minZ = 0;
            maxX = maxY = maxZ = 1;
        } else if (entity instanceof net.minecraft.world.entity.Display.TextDisplay text
                && text.textRenderState() != null) {
            var lines =
                    Minecraft.getInstance()
                            .font
                            .split(
                                    text.textRenderState().text(),
                                    Math.max(1, text.textRenderState().lineWidth()));
            int pixels =
                    lines.stream().mapToInt(Minecraft.getInstance().font::width).max().orElse(1);
            minX = -(pixels + 4) / 80f;
            maxX = -minX;
            minY = -.025f;
            maxY = (lines.size() * 10 + 2) / 40f;
            minZ = -.01f;
            maxZ = .01f;
        }
        var transform = state.transformation().get(1).getMatrix();
        Vector3f low = new Vector3f(Float.POSITIVE_INFINITY),
                high = new Vector3f(Float.NEGATIVE_INFINITY);
        for (int i = 0; i < 8; i++) {
            var point =
                    transform.transformPosition(
                            new Vector3f(
                                    (i & 1) == 0 ? minX : maxX,
                                    (i & 2) == 0 ? minY : maxY,
                                    (i & 4) == 0 ? minZ : maxZ));
            low.min(point);
            high.max(point);
        }
        Vector3f center = new Vector3f(low).add(high).mul(.5f), size = new Vector3f(high).sub(low);
        float diameter = Math.max(.1f, Math.max(size.x, Math.max(size.y, size.z)));
        float scale =
                Math.max(.000001f, Math.min(width - 12, height - 12) / (diameter * 1.35f) * zoom);
        var renderState = extractRenderState(entity);
        if (renderState
                instanceof
                net.minecraft.client.renderer.entity.state.DisplayEntityRenderState display) {
            display.cameraXRot = pitch;
            display.cameraYRot = -yaw;
        }
        var model =
                new com.dutchmtc.ee.gui.modifier.MobStackPreviewState.Model(
                        renderState, center.negate());
        g.guiRenderState.addPicturesInPictureState(
                new com.dutchmtc.ee.gui.modifier.MobStackPreviewState(
                        java.util.List.of(model),
                        0,
                        yaw,
                        pitch,
                        x,
                        y,
                        x + width,
                        y + height,
                        scale,
                        g.scissorStack.peek(),
                        true));
    }

    private static void renderEntityInBox(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int scale,
            float yOffset,
            float yawOffsetDeg,
            float pitchDeg,
            Entity entity,
            boolean turnHeadWithYaw) {
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
            // Free rotation keeps the head aligned with the body, including a full 360-degree turn.
            // Preserve the original head-following behavior for existing armor stand editors.
            living.yRot = turnHeadWithYaw ? yawOffsetDeg : 0;
            living.xRot = living.pose != Pose.FALL_FLYING ? pitchDeg : 0.0f;

            // Normalize bounding box dimensions to world scale (matches vanilla InventoryScreen
            // behavior)
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1.0f;
        }

        Quaternionf base = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf pitchRot = new Quaternionf().rotateX(pitchDeg * 0.017453292f);
        base.mul(pitchRot);
        if (!(state instanceof LivingEntityRenderState)) base.rotateY(yawOffsetDeg * 0.017453292f);

        Vector3f translate = new Vector3f(0.0f, state.boundingBoxHeight / 2.0f + yOffset, 0.0f);
        graphics.entity(
                state, (float) scale, translate, base, pitchRot, x, y, x + width, y + height);
    }

    private static EntityRenderState extractRenderState(Entity entity) {
        ensurePreviewEntityId(entity);
        if (entity instanceof Display display && display.renderState() == null) display.tick();

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        @SuppressWarnings("rawtypes")
        EntityRenderer renderer = dispatcher.getRenderer(entity);
        @SuppressWarnings("unchecked")
        EntityRenderState state =
                ((EntityRenderer<Entity, EntityRenderState>) renderer)
                        .createRenderState(entity, 1.0f);

        state.lightCoords = 15728880;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        return state;
    }

    private static void ensurePreviewEntityId(Entity entity) {
        try {
            entity.getId();
        } catch (IllegalStateException ignored) {
            // 26.2 render-state extraction reads the entity id for item model state.
            // GUI preview entities are never spawned into a level, so assign a local id first.
            entity.setId(NEXT_PREVIEW_ENTITY_ID.getAndIncrement());
        }
    }
}
