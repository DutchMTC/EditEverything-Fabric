package com.dutchmtc.ee.utils;

import com.dutchmtc.ee.gui.modifier.StyledTextDocument;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

import java.util.HashMap;
import java.util.Objects;

/** Isolated command draft; block state and executor data use their native item components. */
public final class CommandBlockEditorData {
    public static final int COMMAND_LIMIT = 32500;
    private static final String[] EXECUTOR_KEYS = {
        "Command",
        "SuccessCount",
        "CustomName",
        "TrackOutput",
        "LastOutput",
        "UpdateLastExecution",
        "LastExecution"
    };
    private final ItemStack source;
    private final HolderLookup.Provider registries;
    private final CompoundTag originalTag;
    private final String initialCommand, initialFacing;
    private final Component initialName;
    private final Component originalName;
    private final boolean initialAuto, initialConditional, initialTrack;
    public final StyledTextDocument name;
    public Item type;
    public String command, facing;
    public boolean alwaysActive, conditional, trackOutput;

    public CommandBlockEditorData(ItemStack source) {
        this(source, VanillaRegistries.createWorldLookup());
    }

    public CommandBlockEditorData(ItemStack source, HolderLookup.Provider registries) {
        this.registries = registries;
        this.source = source.copy();
        type = source.getItem();
        originalTag = tag(source, type == Items.COMMAND_BLOCK_MINECART);
        command = initialCommand = originalTag.getStringOr("Command", "");
        alwaysActive =
                initialAuto =
                        originalTag.getBooleanOr(
                                "auto",
                                type == Items.CHAIN_COMMAND_BLOCK
                                        && !source.has(DataComponents.BLOCK_ENTITY_DATA));
        trackOutput = initialTrack = originalTag.getBooleanOr("TrackOutput", true);
        var state = source.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        conditional = initialConditional = "true".equals(state.properties().get("conditional"));
        facing = initialFacing = state.properties().getOrDefault("facing", "");
        Component loaded = source.get(DataComponents.CUSTOM_NAME);
        if ((loaded == null || type == Items.COMMAND_BLOCK_MINECART)
                && originalTag.get("CustomName") != null)
            loaded =
                    ComponentSerialization.CODEC
                            .parse(
                                    registries.createSerializationContext(NbtOps.INSTANCE),
                                    originalTag.get("CustomName"))
                            .result()
                            .orElse(loaded);
        name = new StyledTextDocument(loaded);
        originalName = loaded == null ? Component.empty() : loaded.copy();
        initialName = name.component();
    }

    public boolean minecart() {
        return type == Items.COMMAND_BLOCK_MINECART;
    }

    public boolean modified() {
        return type != source.getItem()
                || !command.equals(initialCommand)
                || !name.component().equals(initialName)
                || alwaysActive != initialAuto
                || conditional != initialConditional
                || trackOutput != initialTrack
                || !facing.equals(initialFacing);
    }

    public ItemStack result() {
        boolean repairCartName =
                minecart() && !name.text().isEmpty() && !originalTag.contains("CustomName");
        // A plain block inherits placement defaults (including world feedback rules).
        // Applying must explicitly save the settings displayed by the editor.
        boolean initializeBlock = !minecart() && !source.has(DataComponents.BLOCK_ENTITY_DATA);
        if (!modified() && !repairCartName && !initializeBlock) return source.copy();
        ItemStack result = ItemUtils.setItem(type, source);
        int amount = Math.clamp(source.getCount(), 1, 99);
        if (result.has(DataComponents.MAX_DAMAGE)) {
            amount = 1;
            if (result.getMaxStackSize() > 1) result.set(DataComponents.MAX_STACK_SIZE, 1);
        } else if (amount > result.getMaxStackSize())
            result.set(DataComponents.MAX_STACK_SIZE, amount);
        result.setCount(amount);
        boolean conversion = minecart() != (source.getItem() == Items.COMMAND_BLOCK_MINECART);
        CompoundTag tag = conversion ? tag(source, minecart()) : originalTag.copy();
        if (conversion)
            for (String key : EXECUTOR_KEYS)
                if (originalTag.get(key) != null) tag.put(key, originalTag.get(key).copy());
        // Commands are literal command text, never legacy text/color codes.
        tag.putString("Command", command);
        if (!command.equals(initialCommand)) tag.putInt("SuccessCount", 0);
        tag.putBoolean("TrackOutput", trackOutput);
        if (!trackOutput) tag.remove("LastOutput");
        boolean nameChanged = !Objects.equals(initialName, name.component());
        if (nameChanged || conversion || repairCartName) {
            if (name.text().isEmpty()) {
                tag.remove("CustomName");
                if (nameChanged) result.remove(DataComponents.CUSTOM_NAME);
            } else {
                Component value = nameChanged ? name.component() : originalName;
                tag.put(
                        "CustomName",
                        ComponentSerialization.CODEC
                                .encodeStart(
                                        registries.createSerializationContext(NbtOps.INSTANCE),
                                        value)
                                .getOrThrow());
                if (nameChanged) result.set(DataComponents.CUSTOM_NAME, value);
            }
        }
        if (minecart()) {
            tag.remove("auto");
            result.set(
                    DataComponents.ENTITY_DATA,
                    TypedEntityData.of(EntityTypes.COMMAND_BLOCK_MINECART, tag));
            if (conversion) {
                result.remove(DataComponents.BLOCK_ENTITY_DATA);
                result.remove(DataComponents.BLOCK_STATE);
            }
        } else {
            tag.putBoolean("auto", alwaysActive);
            result.set(
                    DataComponents.BLOCK_ENTITY_DATA,
                    TypedEntityData.of(BlockEntityTypes.COMMAND_BLOCK, tag));
            if (conversion) result.remove(DataComponents.ENTITY_DATA);
            var properties =
                    new HashMap<>(
                            source.getOrDefault(
                                            DataComponents.BLOCK_STATE,
                                            BlockItemStateProperties.EMPTY)
                                    .properties());
            if (conditional != initialConditional || conversion)
                properties.put("conditional", Boolean.toString(conditional));
            if (!facing.equals(initialFacing) || conversion) {
                if (facing.isEmpty()) properties.remove("facing");
                else properties.put("facing", facing);
            }
            if (properties.isEmpty()) result.remove(DataComponents.BLOCK_STATE);
            else result.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(properties));
        }
        return result;
    }

    private static CompoundTag tag(ItemStack stack, boolean cart) {
        if (cart) {
            var data = stack.get(DataComponents.ENTITY_DATA);
            return data == null ? new CompoundTag() : data.copyTagWithoutId();
        }
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data == null ? new CompoundTag() : data.copyTagWithoutId();
    }
}
