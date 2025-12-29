package fr.atesab.act.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class ItemUtilsClient {
    private ItemUtilsClient() {
    }

    public static boolean canGive(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack it = mc.player.getInventory().getItem(i);
            if (it.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static void give(ItemStack stack) {
        give(Minecraft.getInstance(), stack);
    }

    public static void give(ItemStack stack, int slot) {
        give(Minecraft.getInstance(), stack, slot);
    }

    public static void give(List<ItemStack> stacks) {
        give(Minecraft.getInstance(), stacks);
    }

    @Deprecated
    public static void give(Minecraft mc, ItemStack stack) {
        if (mc.player != null && mc.player.isCreative()) {
            if (stack != null) {
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getItem(i).isEmpty()) {
                        give(mc, stack, 36 + i);
                        ChatUtils.itemStack(stack);
                        return;
                    }
                }
            }
            ChatUtils.error(I18n.get("gui.act.give.fail"));
        } else {
            ChatUtils.error(I18n.get("gui.act.nocreative"));
        }
    }

    @Deprecated
    public static void give(Minecraft mc, ItemStack stack, int slot) {
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        if (mc.player.isCreative()) {
            mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(slot, stack));
            mc.player.inventoryMenu.getSlot(slot).set(stack);
        } else {
            ChatUtils.error(I18n.get("gui.act.nocreative"));
        }
    }

    @Deprecated
    public static void give(Minecraft mc, List<ItemStack> stacks) {
        if (mc.player != null && mc.player.isCreative()) {
            int i = 0, j = 0;
            ItemStack is;
            stacks:
            for (; j < stacks.size(); j++) {
                is = stacks.get(j);
                for (; i < 9; i++) {
                    if (mc.player.getInventory().getItem(i).isEmpty()) {
                        give(mc, is, 36 + i);
                        ChatUtils.itemStack(is);
                        i++;
                        continue stacks;
                    }
                }
                ChatUtils.error(I18n.get("gui.act.give.fail"));
                return;
            }
        } else {
            ChatUtils.error(I18n.get("gui.act.nocreative"));
        }
    }
}
