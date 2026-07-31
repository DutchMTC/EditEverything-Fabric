package com.dutchmtc.ee.network;

import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.utils.ArmorStandEditorUtils;
import com.dutchmtc.ee.utils.PermissionCompat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class EENetworking {
    private EENetworking() {
    }

    public static void initCommon() {
        PayloadTypeRegistry.clientboundPlay().register(OpenGiverPayload.TYPE, OpenGiverPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenMenuPayload.TYPE, OpenMenuPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenEditorPayload.TYPE, OpenEditorPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenColorPickerPayload.TYPE, OpenColorPickerPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(InstantClickPayload.TYPE, InstantClickPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(InstantPlacePayload.TYPE, InstantPlacePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenArmorStandEditorPayload.TYPE, OpenArmorStandEditorPayload.STREAM_CODEC);

        PayloadTypeRegistry.serverboundPlay().register(ApplyArmorStandEditsPayload.TYPE, ApplyArmorStandEditsPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ApplyArmorStandEditsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> applyArmorStandEdits(payload, context.player()));
        });
    }

    public static void sendOpenGiver(ServerPlayer player, String giveCode) {
        ServerPlayNetworking.send(player, new OpenGiverPayload(giveCode == null ? "" : giveCode));
    }

    public static void sendOpenMenu(ServerPlayer player, String options) {
        ServerPlayNetworking.send(player, new OpenMenuPayload(options == null ? "" : options));
    }

    public static void sendOpenEditor(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenEditorPayload());
    }

    public static void sendOpenColorPicker(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenColorPickerPayload());
    }

    public static void sendOpenArmorStandEditor(ServerPlayer player, int entityId) {
        ServerPlayNetworking.send(player, new OpenArmorStandEditorPayload(entityId));
    }

    public static void sendToggleInstantClick(ServerPlayer player) {
        ServerPlayNetworking.send(player, new InstantClickPayload((byte) 0, false));
    }

    public static void sendSetInstantClick(ServerPlayer player, boolean value) {
        ServerPlayNetworking.send(player, new InstantClickPayload((byte) 1, value));
    }

    public static void sendToggleInstantPlace(ServerPlayer player) {
        ServerPlayNetworking.send(player, new InstantPlacePayload((byte) 0, false));
    }

    public static void sendSetInstantPlace(ServerPlayer player, boolean value) {
        ServerPlayNetworking.send(player, new InstantPlacePayload((byte) 1, value));
    }

    public record OpenGiverPayload(String giveCode) implements CustomPacketPayload {
        public static final Type<OpenGiverPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "open_giver"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenGiverPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.stringUtf8(32767), OpenGiverPayload::giveCode, OpenGiverPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenMenuPayload(String options) implements CustomPacketPayload {
        public static final Type<OpenMenuPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "open_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenuPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.stringUtf8(32767), OpenMenuPayload::options, OpenMenuPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenEditorPayload() implements CustomPacketPayload {
        public static final Type<OpenEditorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "open_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenEditorPayload> STREAM_CODEC = StreamCodec.unit(new OpenEditorPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenColorPickerPayload() implements CustomPacketPayload {
        public static final Type<OpenColorPickerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "open_color_picker"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenColorPickerPayload> STREAM_CODEC = StreamCodec.unit(new OpenColorPickerPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenArmorStandEditorPayload(int entityId) implements CustomPacketPayload {
        public static final Type<OpenArmorStandEditorPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "open_armor_stand_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenArmorStandEditorPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, OpenArmorStandEditorPayload::entityId, OpenArmorStandEditorPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ApplyArmorStandEditsPayload(int entityId, CompoundTag state) implements CustomPacketPayload {
        public static final Type<ApplyArmorStandEditsPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "apply_armor_stand_edits"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApplyArmorStandEditsPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, ApplyArmorStandEditsPayload::entityId,
                        ByteBufCodecs.COMPOUND_TAG, ApplyArmorStandEditsPayload::state,
                        ApplyArmorStandEditsPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record InstantClickPayload(byte mode, boolean value) implements CustomPacketPayload {
        public static final Type<InstantClickPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "instant_click"));
        public static final StreamCodec<RegistryFriendlyByteBuf, InstantClickPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE, InstantClickPayload::mode, ByteBufCodecs.BOOL, InstantClickPayload::value, InstantClickPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record InstantPlacePayload(byte mode, boolean value) implements CustomPacketPayload {
        public static final Type<InstantPlacePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "instant_place"));
        public static final StreamCodec<RegistryFriendlyByteBuf, InstantPlacePayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE, InstantPlacePayload::mode, ByteBufCodecs.BOOL, InstantPlacePayload::value, InstantPlacePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void applyArmorStandEdits(ApplyArmorStandEditsPayload payload, ServerPlayer player) {
        if (player == null || player.level() == null) {
            return;
        }
        if (!PermissionCompat.hasGamemasterPermissions(player.createCommandSourceStack())) {
            return;
        }

        Entity entity = player.level().getEntity(payload.entityId());
        if (!(entity instanceof ArmorStand stand)) {
            return;
        }

        CompoundTag state = payload.state();
        if (state == null) {
            return;
        }

        ArmorStandEditorUtils.applyState(stand, state, player.level().registryAccess());
    }
}
