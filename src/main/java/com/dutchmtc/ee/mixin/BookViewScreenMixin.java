package com.dutchmtc.ee.mixin;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiBookEditor;
import com.dutchmtc.ee.utils.ItemUtilsClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep normal reading and add creative editing only for the exact held written book. */
@Mixin(BookViewScreen.class)
public abstract class BookViewScreenMixin extends Screen {
    @Shadow private BookViewScreen.BookAccess bookAccess;

    protected BookViewScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ee$addBookEditor(CallbackInfo ci) {
        if (((Object) this).getClass() != BookViewScreen.class
                || minecraft.player == null
                || !minecraft.player.isCreative()) return;
        int slot = -1;
        ItemStack main = minecraft.player.getMainHandItem(),
                off = minecraft.player.getOffhandItem();
        if (main.is(Items.WRITTEN_BOOK)
                && BookViewScreen.BookAccess.fromItem(main).equals(bookAccess))
            slot = minecraft.player.getInventory().getSelectedSlot() + 36;
        else if (off.is(Items.WRITTEN_BOOK)
                && BookViewScreen.BookAccess.fromItem(off).equals(bookAccess)) slot = 45;
        if (slot < 0) return;
        int target = slot;
        ItemStack original = minecraft.player.inventoryMenu.getSlot(slot).getItem().copy();
        EEButton edit =
                addRenderableWidget(
                        new EEButton(
                                (width - 200) / 2,
                                220,
                                200,
                                20,
                                Component.literal("Edit book"),
                                b ->
                                        minecraft.gui.setScreen(
                                                new GuiBookEditor(
                                                        null,
                                                        original,
                                                        edited -> {
                                                            Minecraft mc = Minecraft.getInstance();
                                                            if (mc.player != null
                                                                    && mc.player.isCreative()
                                                                    && ItemStack.matches(
                                                                            mc.player
                                                                                    .inventoryMenu
                                                                                    .getSlot(target)
                                                                                    .getItem(),
                                                                            original))
                                                                ItemUtilsClient.give(
                                                                        edited, target);
                                                        }))));
        edit.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Edit pages, author, signing and text actions. Changes are saved"
                                    + " only with Apply.")));
    }
}
