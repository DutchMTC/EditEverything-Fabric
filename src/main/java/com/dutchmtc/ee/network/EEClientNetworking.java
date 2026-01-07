package com.dutchmtc.ee.network;

import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.EEModClient;
import com.dutchmtc.ee.gui.GuiArmorStandEditor;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ItemUtilsClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

@Environment(EnvType.CLIENT)
public final class EEClientNetworking {
    private EEClientNetworking() {
    }

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(EENetworking.OpenGiverPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    Class<?> cls = Class.forName("com.dutchmtc.ee.gui.GuiGiver");
                    var ctor = cls.getConstructor(Screen.class, String.class);
                    Object screen = ctor.newInstance(null, payload.giveCode());
                    Minecraft.getInstance().setScreen((Screen) screen);
                } catch (Throwable t) {
                    EEMod.LOGGER.error("Failed to open giver GUI from server packet", t);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EENetworking.OpenMenuPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    Class<?> cls = Class.forName("com.dutchmtc.ee.gui.GuiMenu");
                    var ctor = cls.getConstructor(Screen.class);
                    Object screen = ctor.newInstance((Screen) null);
                    Minecraft.getInstance().setScreen((Screen) screen);
                } catch (Throwable t) {
                    EEMod.LOGGER.error("Failed to open menu GUI from server packet", t);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EENetworking.OpenEditorPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    EEModClient.openGiver();
                } catch (Throwable t) {
                    EEMod.LOGGER.error("Failed to open editor GUI from server packet", t);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EENetworking.OpenColorPickerPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    Class<?> cls = Class.forName("com.dutchmtc.ee.gui.modifier.GuiColorModifier");
                    var ctor = cls.getConstructor(Screen.class, java.util.function.IntConsumer.class);
                    Object screen = ctor.newInstance(null, (java.util.function.IntConsumer) (newColor) -> {
                        var mc = Minecraft.getInstance();
                        if (mc.player == null) {
                            return;
                        }
                        var is = mc.player.getMainHandItem();
                        int slot = 36 + mc.player.getInventory().getSelectedSlot();
                        ItemUtilsClient.give(ItemUtils.setGlobalColor(is, newColor), slot);
                    });
                    Minecraft.getInstance().setScreen((Screen) screen);
                } catch (Throwable t) {
                    EEMod.LOGGER.error("Failed to open color GUI from server packet", t);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EENetworking.InstantClickPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.mode() == 0) {
                    EEMod.setInstantMineEnabled(!EEMod.isInstantMineEnabled());
                } else {
                    EEMod.setInstantMineEnabled(payload.value());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EENetworking.InstantPlacePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.mode() == 0) {
                    EEMod.setInstantPlaceEnabled(!EEMod.isInstantPlaceEnabled());
                } else {
                    EEMod.setInstantPlaceEnabled(payload.value());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EENetworking.OpenArmorStandEditorPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                var mc = Minecraft.getInstance();
                Screen parent = mc.screen;
                // This screen is currently only opened via the /ee armorstand command.
                mc.setScreen(new GuiArmorStandEditor(parent, payload.entityId(), true));
            });
        });
    }

    public static void sendApplyArmorStandEdits(int entityId, CompoundTag state) {
        if (state == null) {
            state = new CompoundTag();
        }
        ClientPlayNetworking.send(new EENetworking.ApplyArmorStandEditsPayload(entityId, state));
    }
}
