package com.dutchmtc.ee.mixin;

import com.dutchmtc.ee.gui.modifier.GuiBookEditor;
import com.dutchmtc.ee.utils.ItemUtilsClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Creative Book and Quill use opens the native-paper rich editor; survival remains vanilla. */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {
    @Shadow @Final private ItemStack book;
    @Shadow @Final private InteractionHand hand;

    @Inject(method = "init", at = @At("TAIL"))
    private void ee$editCreativeBook(CallbackInfo ci) {
        if ((Object) this instanceof GuiBookEditor) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isCreative()) return;
        ItemStack original = book.copy();
        int slot =
                hand == InteractionHand.MAIN_HAND
                        ? mc.player.getInventory().getSelectedSlot() + 36
                        : 45;
        mc.gui.setScreen(
                new GuiBookEditor(
                        null,
                        original,
                        edited -> {
                            if (mc.player != null
                                    && mc.player.isCreative()
                                    && ItemStack.matches(
                                            mc.player.inventoryMenu.getSlot(slot).getItem(),
                                            original)) ItemUtilsClient.give(edited, slot);
                        }));
    }
}
