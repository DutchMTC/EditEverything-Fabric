package com.dutchmtc.ee.utils;

import com.dutchmtc.ee.utils.ItemUtils.ContainerData;
import com.dutchmtc.ee.utils.ItemUtils.ContainerSize;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;

import java.util.ArrayList;
import java.util.List;

/** Native storage adapters shared by the container editor and item tooltip preview. */
public final class ContainerItemData {
    private ContainerItemData() {}

    private static HolderLookup.Provider registries() {
        var level = Minecraft.getInstance().level;
        return level == null ? VanillaRegistries.createWorldLookup() : level.registryAccess();
    }

    public static boolean isBundle(ItemStack stack) {
        return stack.getItem() instanceof BundleItem || stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    private static EntityType<?> vehicleType(ItemStack stack) {
        if (stack.getItem() == Items.CHEST_MINECART) return EntityTypes.CHEST_MINECART;
        if (stack.getItem() == Items.HOPPER_MINECART) return EntityTypes.HOPPER_MINECART;
        var data = stack.get(DataComponents.ENTITY_DATA);
        if (data != null && ContainerEntity.class.isAssignableFrom(data.type().getBaseClass()))
            return data.type();
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.getPath().endsWith("_chest_boat") || id.getPath().endsWith("_chest_raft"))
            return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        return null;
    }

    private static ContainerSize shape(int count) {
        return new ContainerSize(Math.min(9, count), (count + 8) / 9);
    }

    private static int blockSlots(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof EntityBlock factory) {
            var entity = factory.newBlockEntity(BlockPos.ZERO, item.getBlock().defaultBlockState());
            if (entity instanceof Container container
                    && entity.collectComponents().has(DataComponents.CONTAINER))
                return container.getContainerSize();
        }
        return 0;
    }

    public static ContainerSize size(ItemStack stack) {
        if (isBundle(stack)) {
            int imported =
                    stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).size();
            // 1/99 is the smallest possible unit weight; keep spare slots for mixed stacks.
            return shape(Math.max(108, imported + 1));
        }
        var type = vehicleType(stack);
        if (type != null)
            return type == EntityTypes.HOPPER_MINECART
                    ? new ContainerSize(5, 1)
                    : new ContainerSize(9, 3);
        if (stack.getItem() instanceof BlockItem item) {
            var block = item.getBlock();
            if (block == Blocks.JUKEBOX || block == Blocks.LECTERN) return new ContainerSize(1, 1);
            if (block instanceof CampfireBlock) return new ContainerSize(4, 1);
            if (block instanceof EntityBlock entityBlock) {
                var entity = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                if (entity instanceof Container container
                        && entity.collectComponents().has(DataComponents.CONTAINER)) {
                    int count = container.getContainerSize();
                    if (count == 9 && (block == Blocks.DISPENSER || block == Blocks.DROPPER))
                        return new ContainerSize(3, 3);
                    if (count == 6) return new ContainerSize(3, 2);
                    if (count > 0) return shape(count);
                }
            }
        }
        var contents = stack.get(DataComponents.CONTAINER);
        return contents == null ? null : shape(Math.max(27, contents.size()));
    }

    public static ContainerData read(ItemStack stack) {
        var size = size(stack);
        if (size == null) return null;
        int count = size.sizeX() * size.sizeY();
        int blockCount = blockSlots(stack);
        if (!isBundle(stack) && blockCount > 0) count = blockCount;
        else if (!isBundle(stack)
                && vehicleType(stack) == null
                && singleField(stack) == null
                && !(stack.getItem() instanceof BlockItem)
                && stack.has(DataComponents.CONTAINER))
            count = Math.max(27, stack.get(DataComponents.CONTAINER).size());
        var slots = NonNullList.withSize(count, ItemStack.EMPTY);
        if (isBundle(stack)) {
            var copies =
                    stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
                            .itemCopies()
                            .toList();
            for (int i = 0; i < copies.size(); i++) slots.set(i, copies.get(i));
        } else if (vehicleType(stack) != null) {
            var data = stack.get(DataComponents.ENTITY_DATA);
            if (data != null) readSlottedItems(data.copyTagWithoutId(), slots);
        } else if (singleField(stack) != null) {
            String field = singleField(stack);
            var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (data != null) slots.set(0, readSingle(data.copyTagWithoutId(), field));
            else {
                var legacy = ItemUtils.getTag(stack);
                if (legacy != null)
                    legacy.getCompound("BlockEntityTag")
                            .ifPresent(tag -> slots.set(0, readSingle(tag, field)));
            }
        } else {
            var contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) contents.copyInto(slots);
            else {
                var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                if (data != null) readSlottedItems(data.copyTagWithoutId(), slots);
                else ItemUtils.loadTagItems(stack, new ContainerData(size, slots));
            }
        }
        return new ContainerData(size, slots, isBundle(stack));
    }

    private static String singleField(ItemStack stack) {
        return stack.is(Items.JUKEBOX) ? "RecordItem" : stack.is(Items.LECTERN) ? "Book" : null;
    }

    private static ItemStack readSingle(CompoundTag tag, String field) {
        return tag.getCompound(field)
                .map(item -> ItemUtils.parseStack(registries(), item))
                .orElse(ItemStack.EMPTY);
    }

    private static void readSlottedItems(CompoundTag tag, NonNullList<ItemStack> slots) {
        tag.read(
                        "Items",
                        ItemStackWithSlot.CODEC.listOf(),
                        registries().createSerializationContext(NbtOps.INSTANCE))
                .ifPresent(
                        items -> {
                            for (var item : items)
                                if (item.slot() >= 0 && item.slot() < slots.size())
                                    slots.set(item.slot(), item.stack().copy());
                        });
    }

    public static ItemStack write(ItemStack stack, List<ItemStack> slots) {
        if (isBundle(stack)) {
            stack.set(DataComponents.BUNDLE_CONTENTS, bundle(slots));
        } else if (vehicleType(stack) != null) {
            var existing = stack.get(DataComponents.ENTITY_DATA);
            var tag = existing == null ? new CompoundTag() : existing.copyTagWithoutId();
            var items = new ArrayList<ItemStackWithSlot>();
            for (int i = 0; i < slots.size(); i++)
                if (!slots.get(i).isEmpty())
                    items.add(new ItemStackWithSlot(i, slots.get(i).copy()));
            tag.put(
                    "Items",
                    ItemStackWithSlot.CODEC
                            .listOf()
                            .encodeStart(
                                    registries().createSerializationContext(NbtOps.INSTANCE), items)
                            .getOrThrow());
            tag.remove("LootTable");
            tag.remove("LootTableSeed");
            stack.set(
                    DataComponents.ENTITY_DATA,
                    TypedEntityData.of(
                            existing == null ? vehicleType(stack) : existing.type(), tag));
        } else if (singleField(stack) != null
                && stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof EntityBlock entityBlock) {
            String field = singleField(stack);
            var existing = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            var legacy = ItemUtils.getTag(stack);
            var tag =
                    existing != null
                            ? existing.copyTagWithoutId()
                            : legacy == null
                                    ? new CompoundTag()
                                    : legacy.getCompound("BlockEntityTag")
                                            .map(CompoundTag::copy)
                                            .orElseGet(CompoundTag::new);
            var value = slots.getFirst();
            if (value.isEmpty()) tag.remove(field);
            else tag.put(field, ItemUtils.saveStack(value, registries()));
            if (stack.is(Items.LECTERN)) tag.putInt("Page", 0);
            var entity =
                    entityBlock.newBlockEntity(BlockPos.ZERO, item.getBlock().defaultBlockState());
            stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(entity.getType(), tag));
            var state =
                    stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
            stack.set(
                    DataComponents.BLOCK_STATE,
                    stack.is(Items.LECTERN)
                            ? state.with(LecternBlock.HAS_BOOK, !value.isEmpty())
                            : state.with(JukeboxBlock.HAS_RECORD, !value.isEmpty()));
            if (legacy != null) {
                legacy.getCompound("BlockEntityTag").ifPresent(block -> block.remove(field));
                ItemUtils.setTag(stack, legacy);
            }
        } else if (size(stack) != null) {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
            // Explicit contents replace a loot-table placeholder.
            stack.remove(DataComponents.CONTAINER_LOOT);
            var existing = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (existing != null
                    && (existing.contains("LootTable") || existing.contains("Items"))) {
                var tag = existing.copyTagWithoutId();
                tag.remove("Items");
                tag.remove("LootTable");
                tag.remove("LootTableSeed");
                stack.set(
                        DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(existing.type(), tag));
            }
        }
        return stack;
    }

    public static BundleContents bundle(List<ItemStack> slots) {
        return new BundleContents(
                slots.stream()
                        .filter(s -> !s.isEmpty())
                        .map(ItemStackTemplate::fromNonEmptyStack)
                        .toList());
    }
}
