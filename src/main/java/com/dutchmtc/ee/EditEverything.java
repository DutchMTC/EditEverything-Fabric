package com.dutchmtc.ee;

import com.dutchmtc.ee.mixin.CreativeModeTabsAccessor;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/** A creative tab to add items */
public class EditEverything {

    public static final ResourceKey<CreativeModeTab> ACT_TAB_KEY =
            ResourceKey.create(
                    Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "act_tab"));
    public static final ResourceKey<CreativeModeTab> OPERATOR_TAB_KEY =
            ResourceKey.create(
                    Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "operator_items"));

    private final Collection<ItemStack> subItems = new ArrayList<>();
    private final Collection<ItemStack> extraItems = new ArrayList<>();
    private CreativeModeTab tab;
    private CreativeModeTab operatorTab;

    public void buildSubItems() {
        CreativeModeTabsAccessor.ee$setCachedParameters(null);
    }

    /** Called after category tabs finish, before the search tab indexes their contents. */
    public void rebuildExtraItems(CreativeModeTab.ItemDisplayParameters params) {
        if (operatorTab == null) return;
        Set<Item> ordinaryItems = new HashSet<>();
        for (CreativeModeTab other : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (other == tab
                    || other == operatorTab
                    || other.getType() != CreativeModeTab.Type.CATEGORY) continue;
            if (Identifier.withDefaultNamespace("op_blocks")
                    .equals(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(other))) continue;
            other.getDisplayItems().forEach(stack -> ordinaryItems.add(stack.getItem()));
        }
        extraItems.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()
                    && item.isEnabled(params.enabledFeatures())
                    && !ordinaryItems.contains(item)) extraItems.add(stack);
        }
        operatorTab.buildContents(params);
    }

    /**
     * add a block to this tab
     *
     * @param sub the item
     */
    public void addSubitem(ItemLike sub) {
        addSubitem(new ItemStack(sub, 1));
    }

    /**
     * add a stack to this tab
     *
     * @param sub the stack
     */
    public void addSubitem(ItemStack sub) {
        if (!sub.isEmpty()) subItems.add(sub.copyWithCount(1));
    }

    @SuppressWarnings("unchecked")
    public ItemStack makeIcon() {
        return ItemUtils.buildStack(
                Blocks.STRUCTURE_BLOCK, 1, null, null, new Tuple[] {new Tuple<>(null, 1)});
    }

    private void accept(
            CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output output) {
        Set<ItemStack> items = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (String code : EEMod.getCustomItems()) {
            if (code == null || code.isEmpty()) {
                continue;
            }
            try {
                ItemStack stack =
                        ItemUtils.getFromGiveCode(
                                com.dutchmtc.ee.utils.ChatUtils.translateColorCodes(code),
                                params != null ? params.holders() : null);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                stack.setCount(1);
                items.add(stack);
            } catch (Throwable t) {
                // Never fail tab population (and creative search indexing) due to a single bad
                // entry.
                EEMod.LOGGER.warn("Skipping invalid custom item entry in ACT tab: {}", code, t);
            }
        }
        output.acceptAll(items);
    }

    private void acceptOperatorItems(
            CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output output) {
        Set<ItemStack> items = ItemStackLinkedSet.createTypeAndComponentsSet();
        extraItems.forEach(stack -> items.add(stack.copyWithCount(1)));
        subItems.forEach(stack -> items.add(stack.copyWithCount(1)));
        output.acceptAll(items);
    }

    public void register() {
        tab =
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.ee.saved"))
                        .icon(() -> new ItemStack(Items.CHEST))
                        .displayItems(this::accept)
                        .build();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ACT_TAB_KEY, tab);
        operatorTab =
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.ee.operator"))
                        .icon(this::makeIcon)
                        .displayItems(this::acceptOperatorItems)
                        .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, OPERATOR_TAB_KEY, operatorTab);
    }

    public void refresh() {
        // Rebuild with the active world's registry and feature flags on the next refresh.
        buildSubItems();
    }

    public CreativeModeTab getTab() {
        return Optional.ofNullable(tab)
                .orElseThrow(() -> new RuntimeException("tab wasn't built yet!"));
    }

    public boolean isTabRegistered() {
        return tab != null;
    }

    public CreativeModeTab getOperatorTab() {
        return Optional.ofNullable(operatorTab)
                .orElseThrow(() -> new IllegalStateException("Operator tab wasn't built yet!"));
    }
}
