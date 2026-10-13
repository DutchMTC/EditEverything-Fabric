package com.dutchmtc.ee.utils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Prediction;

public final class ServerItemOps {
    private ServerItemOps() {
    }

    public static void setMainHand(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.containerMenu.broadcastChanges();
    }

    public static boolean give(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        boolean added = player.getInventory().add(stack);
        if (!added) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
        player.containerMenu.broadcastChanges();
        return added;
    }
}

