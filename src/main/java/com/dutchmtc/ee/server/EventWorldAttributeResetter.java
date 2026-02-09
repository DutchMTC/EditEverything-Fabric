package com.dutchmtc.ee.server;

import com.dutchmtc.ee.EEMod;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.List;

public final class EventWorldAttributeResetter {
    private static boolean initialized = false;

    private EventWorldAttributeResetter() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(EventWorldAttributeResetter::onPlayerChangedWorld);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            if (player == null) return;
            onPlayerDisconnect(player);
        });
    }

    private static void onPlayerChangedWorld(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
        if (!EEMod.doesResetPlayerAttributesOnEventWorldExit()) return;

        var eventWorldId = EEMod.getEventWorldId().orElse(null);
        if (eventWorldId == null) return;

        boolean leavingEventWorld = origin.dimension().identifier().equals(eventWorldId)
                && !destination.dimension().identifier().equals(eventWorldId);
        if (!leavingEventWorld) return;

        resetPlayerAttributes(player);
    }

    private static void onPlayerDisconnect(ServerPlayer player) {
        if (!EEMod.doesResetPlayerAttributesOnEventWorldExit()) return;

        var eventWorldId = EEMod.getEventWorldId().orElse(null);
        if (eventWorldId == null) return;

        if (!player.level().dimension().identifier().equals(eventWorldId)) return;

        resetPlayerAttributes(player);
    }

    private static void resetPlayerAttributes(ServerPlayer player) {
        var attributeMap = player.getAttributes();

        for (AttributeInstance instance : List.copyOf(attributeMap.getSyncableAttributes())) {
            for (var modifier : List.copyOf(instance.getPermanentModifiers())) {
                instance.removeModifier(modifier);
            }
            attributeMap.resetBaseValue(instance.getAttribute());
        }

        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }
}
