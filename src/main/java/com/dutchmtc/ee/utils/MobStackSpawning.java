package com.dutchmtc.ee.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Spawn-egg passenger support. Runs before vanilla adds the root and its riders to the level. */
public final class MobStackSpawning {
    private MobStackSpawning() {}

    public static void attach(Entity root, CompoundTag data, LivingEntity placer) {
        if (!(root.level() instanceof ServerLevel level)) return;
        if (!root.getPassengers().isEmpty()) return;
        boolean operator =
                placer instanceof Player player
                        && level.getServer().getPlayerList().isOp(player.nameAndId());
        if (root.getType().onlyOpCanSetNbt() && !operator) return;
        int[] budget = {MobStackData.MAX_MOBS - 1};
        for (var value : data.getList("Passengers").orElseGet(ListTag::new)) {
            if (!(value instanceof CompoundTag tag)) continue;
            CompoundTag copy = prepare(tag, operator, budget, 0);
            if (copy == null) continue;
            Entity rider =
                    EntityType.loadEntityRecursive(
                            copy,
                            level,
                            new EntitySpawnRequest(EntitySpawnReason.LOAD, true),
                            entity -> {
                                entity.setUUID(UUID.randomUUID());
                                entity.setPos(root.position());
                                return entity;
                            });
            if (rider != null && rider.startRiding(root, true, false)) position(root);
        }
    }

    private static CompoundTag prepare(
            CompoundTag source, boolean operator, int[] budget, int depth) {
        if (depth >= MobStackData.MAX_MOBS || budget[0]-- <= 0) return null;
        Identifier id = Identifier.tryParse(source.getString("id").orElse(""));
        EntityType<?> type =
                id == null
                        ? null
                        : BuiltInRegistries.ENTITY_TYPE.get(id).map(h -> h.value()).orElse(null);
        if (type == null || !type.canSummon() || type.onlyOpCanSetNbt() && !operator) return null;
        CompoundTag tag = source.copy();
        for (String key : java.util.List.of("UUID", "Pos", "Motion", "Rotation", "Dimension"))
            tag.remove(key);
        ListTag riders = new ListTag();
        for (var value : source.getList("Passengers").orElseGet(ListTag::new)) {
            if (value instanceof CompoundTag child) {
                var copy = prepare(child, operator, budget, depth + 1);
                if (copy != null) riders.add(copy);
            }
        }
        if (riders.isEmpty()) tag.remove("Passengers");
        else tag.put("Passengers", riders);
        return tag;
    }

    public static void position(Entity root) {
        for (Entity rider : root.getPassengers()) {
            root.positionRider(rider);
            position(rider);
        }
    }
}
