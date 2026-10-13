package com.dutchmtc.ee.mixin;

import com.dutchmtc.ee.gui.GuiSignEditWarning;
import com.dutchmtc.ee.utils.ItemUtilsClient;
import com.dutchmtc.ee.utils.SignItemData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractSignEditScreen.class)
public abstract class SignEditScreenMixin {
    @Shadow @Final protected SignBlockEntity sign;
    @Shadow @Final private SignTextSlot slot;
    @Shadow @Final private String[] messages;
    @Unique private SignText ee$front, ee$back;
    @Unique private boolean ee$rich;

    @Inject(method = "<init>(Lnet/minecraft/world/level/block/entity/SignBlockEntity;Lnet/minecraft/world/level/block/entity/SignTextSlot;ZLnet/minecraft/network/chat/Component;)V", at = @At("RETURN"))
    private void ee$captureOriginal(SignBlockEntity sign, SignTextSlot slot, boolean filtered, Component title, CallbackInfo ci) {
        ee$front = sign.getText(SignTextSlot.FRONT);
        ee$back = sign.getText(SignTextSlot.BACK);
        ee$rich = SignItemData.hasRichFormatting(sign.getText(slot));
    }

    @Inject(method = "onDone", at = @At("HEAD"), cancellable = true)
    private void ee$confirmLossySave(CallbackInfo ci) {
        if (!ee$rich) return;
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || sign.isRemoved()
                || sign.playerIsTooFarAwayToEdit(mc.player.getUUID())) {
            mc.gui.setScreen(null);
            return;
        }
        boolean creative = mc.player.isCreative();
        boolean canGetItem = creative && ItemUtilsClient.canGive(mc);
        mc.gui.setScreen(new GuiSignEditWarning(() -> mc.gui.setScreen(null), () -> {
            if (mc.getConnection() != null)
                mc.getConnection().send(new ServerboundSignUpdatePacket(sign.getBlockPos(), List.of(messages), slot));
            mc.gui.setScreen(null);
        }, () -> {
            if (mc.level == null || mc.player == null || !mc.player.isCreative() || !ItemUtilsClient.canGive(mc)) return;
            ItemStack item = sign.getBlockState().getCloneItemStack(mc.level, sign.getBlockPos(), false);
            SignItemData.write(item, ee$front, ee$back, sign.isWaxed(), mc.level.registryAccess());
            ItemUtilsClient.give(item);
            mc.gui.setScreen(null);
        }, creative, canGetItem));
    }

    /** Opening the confirmation must not itself send the destructive vanilla update. */
    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void ee$keepOriginalUntilConfirmed(CallbackInfo ci) {
        if (!ee$rich) return;
        ci.cancel();
        Minecraft.getInstance().textInputManager().stopTextInput(this);
        sign.setText(ee$front, SignTextSlot.FRONT);
        sign.setText(ee$back, SignTextSlot.BACK);
    }
}
