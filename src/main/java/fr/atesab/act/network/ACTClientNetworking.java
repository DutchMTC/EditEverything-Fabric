package fr.atesab.act.network;

import fr.atesab.act.ACTMod;
import fr.atesab.act.ACTModClient;
import fr.atesab.act.utils.ItemUtils;
import fr.atesab.act.utils.ItemUtilsClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

@Environment(EnvType.CLIENT)
public final class ACTClientNetworking {
    private ACTClientNetworking() {
    }

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(ACTNetworking.OpenGiverPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    Class<?> cls = Class.forName("fr.atesab.act.gui.GuiGiver");
                    var ctor = cls.getConstructor(Screen.class, String.class);
                    Object screen = ctor.newInstance(null, payload.giveCode());
                    Minecraft.getInstance().setScreen((Screen) screen);
                } catch (Throwable t) {
                    ACTMod.LOGGER.error("Failed to open giver GUI from server packet", t);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ACTNetworking.OpenMenuPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    Class<?> cls = Class.forName("fr.atesab.act.gui.GuiMenu");
                    var ctor = cls.getConstructor(Screen.class);
                    Object screen = ctor.newInstance((Screen) null);
                    Minecraft.getInstance().setScreen((Screen) screen);
                } catch (Throwable t) {
                    ACTMod.LOGGER.error("Failed to open menu GUI from server packet", t);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ACTNetworking.OpenEditorPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    ACTModClient.openGiver();
                } catch (Throwable t) {
                    ACTMod.LOGGER.error("Failed to open editor GUI from server packet", t);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ACTNetworking.OpenColorPickerPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    Class<?> cls = Class.forName("fr.atesab.act.gui.modifier.GuiColorModifier");
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
                    ACTMod.LOGGER.error("Failed to open color GUI from server packet", t);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ACTNetworking.InstantClickPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.mode() == 0) {
                    ACTMod.setInstantMineEnabled(!ACTMod.isInstantMineEnabled());
                } else {
                    ACTMod.setInstantMineEnabled(payload.value());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ACTNetworking.InstantPlacePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.mode() == 0) {
                    ACTMod.setInstantPlaceEnabled(!ACTMod.isInstantPlaceEnabled());
                } else {
                    ACTMod.setInstantPlaceEnabled(payload.value());
                }
            });
        });
    }
}
