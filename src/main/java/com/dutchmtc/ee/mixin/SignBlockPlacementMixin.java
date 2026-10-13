package com.dutchmtc.ee.mixin;

import com.dutchmtc.ee.utils.SignItemData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A prewritten sign is ready to place; vanilla's plain-text editor would erase per-letter styling on close. */
@Mixin(SignBlock.class)
public class SignBlockPlacementMixin {
    @Inject(method = "setPlacedBy", at = @At("HEAD"), cancellable = true)
    private void ee$keepPresetSignText(Level level, BlockPos pos, BlockState state,
                                       LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        var legacy = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (legacy != null && (legacy.contains("front_text") || legacy.contains("back_text"))) {
            // 26.3 applies default item components after NBT; restore legacy text when no explicit component overrides it.
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                sign.setText(SignItemData.read(stack, SignTextSlot.FRONT, level.registryAccess()), SignTextSlot.FRONT);
                sign.setText(SignItemData.read(stack, SignTextSlot.BACK, level.registryAccess()), SignTextSlot.BACK);
                sign.setWaxed(SignItemData.waxed(stack));
            }
            ci.cancel();
            return;
        }
        if (hasText(stack.get(DataComponents.SIGN_TEXT_FRONT))
                || hasText(stack.get(DataComponents.SIGN_TEXT_BACK))) {
            ci.cancel();
            return;
        }
    }

    private static boolean hasText(SignText text) {
        return text != null && text.getMessages(false).stream()
                .anyMatch(line -> !line.getString().isEmpty());
    }
}
