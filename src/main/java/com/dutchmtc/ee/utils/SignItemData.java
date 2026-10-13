package com.dutchmtc.ee.utils;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.HangingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.Optional;

/** Store both current components and the pre-26.3 sign NBT used by older servers. */
public final class SignItemData {
    private SignItemData() {}

    public static SignText read(ItemStack stack, SignTextSlot side, HolderLookup.Provider registries) {
        var type = side == SignTextSlot.FRONT ? DataComponents.SIGN_TEXT_FRONT : DataComponents.SIGN_TEXT_BACK;
        SignText component = stack.get(type);
        SignText explicit = stack.getComponentsPatch().split().added().get(type);
        if (explicit != null) return explicit;
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return component == null ? SignText.EMPTY : component;
        var tag = data.copyTagWithoutId().get(side == SignTextSlot.FRONT ? "front_text" : "back_text");
        return tag == null ? (component == null ? SignText.EMPTY : component)
                : ItemUtils.decodeComponentFromNbt(registries, DataComponents.SIGN_TEXT_FRONT, tag)
                    .result().orElse(component == null ? SignText.EMPTY : component);
    }

    public static boolean waxed(ItemStack stack) {
        if (stack.has(DataComponents.WAXED)) return true;
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data != null && data.copyTagWithoutId().getBooleanOr("is_waxed", false);
    }

    public static void write(ItemStack stack, SignText front, SignText back, boolean waxed, HolderLookup.Provider registries) {
        stack.set(DataComponents.SIGN_TEXT_FRONT, front);
        stack.set(DataComponents.SIGN_TEXT_BACK, back);
        stack.set(DataComponents.WAXED, waxed ? Unit.INSTANCE : null);
        var existing = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTagWithoutId();
        tag.put("front_text", ItemUtils.encodeComponentToNbt(registries, DataComponents.SIGN_TEXT_FRONT, front).getOrThrow());
        tag.put("back_text", ItemUtils.encodeComponentToNbt(registries, DataComponents.SIGN_TEXT_BACK, back).getOrThrow());
        tag.putBoolean("is_waxed", waxed);
        // Since 26.3, root click events are gated by this block-entity flag. Vanilla enforces
        // operator placement permissions for this NBT; older servers simply ignore the field.
        if (front.getMessages(false).stream().anyMatch(c -> SignClickActions.executable(c.getStyle().getClickEvent()))
                || back.getMessages(false).stream().anyMatch(c -> SignClickActions.executable(c.getStyle().getClickEvent())))
            tag.putBoolean("allow_op_features", true);
        boolean hanging = stack.getItem() instanceof BlockItem item && item.getBlock() instanceof HangingSignBlock;
        stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(
                hanging ? BlockEntityTypes.HANGING_SIGN : BlockEntityTypes.SIGN, tag));
    }

    public static boolean hasRichFormatting(SignText text) {
        return text.getMessages(false).stream().anyMatch(line -> line.getStyle().getClickEvent() != null || line.visit((style, value) ->
                !value.isEmpty() && !style.equals(Style.EMPTY) ? Optional.of(true) : Optional.empty(), Style.EMPTY).orElse(false));
    }
}
