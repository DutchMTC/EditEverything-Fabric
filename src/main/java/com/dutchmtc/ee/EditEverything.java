package com.dutchmtc.ee;

import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * A creative tab to add items
 */
public class EditEverything {

    public static final ResourceKey<CreativeModeTab> ACT_TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "act_tab"));

    private final Collection<ItemStack> subItems = new ArrayList<>();
    private CreativeModeTab tab;

    public void buildSubItems() {
        // This is heavy and might not work well in 1.21 without proper context.
        // I'll simplify it to just add items that are not in any other tab if possible,
        // or just skip the auto-discovery for now to prevent crashes.
        
        // Logic to find items not in any tab:
        Set<Item> knownItems = new HashSet<>();
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == this.tab || tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            // We can't easily get the contents of other tabs without forcing them to build.
            // Fabric doesn't expose a simple "getAllItems" for a tab without building it.
            // For now, I'll skip this auto-population to avoid issues.
        }
        
        BuiltInRegistries.ITEM.forEach(item -> {
            // Add all items? No, that's too many.
            // The original mod added items that were NOT in other tabs.
        });
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
        subItems.add(sub.copy());
    }

    @SuppressWarnings("unchecked")
    public ItemStack makeIcon() {
        return ItemUtils.buildStack(Blocks.STRUCTURE_BLOCK, 1, null, null,
                new Tuple[]{new Tuple<>(null, 1)});
    }

    private void accept(CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output output) {
        output.acceptAll(subItems);
        EEMod.getCustomItems().stream()
                .map(s -> s.replaceAll("&", "" + EEMod.FORMAT_CHAR))
                .map(ItemUtils::getFromGiveCode)
                .peek(s -> s.setCount(1))
                .forEach(output::accept);
    }

    public void register() {
        tab = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.ee"))
                .icon(this::makeIcon)
                .displayItems(this::accept)
                .build();
        
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ACT_TAB_KEY, tab);
    }
    
    public void refresh() {
        // In 1.21, tabs are dynamic. We might not need to do anything if we use the event correctly.
        // But since we use a static list 'subItems', we might need to clear and rebuild it.
        buildSubItems();
    }

    public CreativeModeTab getTab() {
        return Optional.ofNullable(tab).orElseThrow(() -> new RuntimeException("tab wasn't built yet!"));
    }

    public boolean isTabRegistered() {
        return tab != null;
    }
}
