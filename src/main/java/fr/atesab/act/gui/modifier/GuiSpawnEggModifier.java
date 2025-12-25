package fr.atesab.act.gui.modifier;

import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.gui.modifier.nbt.GuiNBTModifier;
import fr.atesab.act.gui.selector.GuiButtonListSelector;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import fr.atesab.act.utils.Tuple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuiSpawnEggModifier extends GuiModifier<ItemStack> {
    private ItemStack currentItemStack;
    
    private enum Tab {
        GENERAL("General"),
        FLAGS("Flags"),
        DATA("Data");
        
        final String label;
        Tab(String label) { this.label = label; }
    }
    
    private Tab currentTab = Tab.GENERAL;

    public GuiSpawnEggModifier(Screen parent, Consumer<ItemStack> setter, ItemStack currentItemStack) {
        super(parent, Component.literal("Set entity"), setter);
        this.currentItemStack = currentItemStack;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(graphics, mouseX, mouseY, partialTicks);
        
        // Draw Item Stack (Fixed at top)
        if (currentItemStack != null) {
            GuiUtils.drawItemStack(graphics, currentItemStack, width / 2 - 10, 20);
            if (GuiUtils.isHover(width / 2 - 10, 20, 20, 20, mouseX, mouseY))
                graphics.renderTooltip(font, currentItemStack, mouseX, mouseY);
        }
        
        // Draw title
        GuiUtils.drawCenterString(graphics, font, getStringTitle(), width / 2, 5, 0xFFFFFFFF);
    }

    @Override
    public void init() {
        clearWidgets();
        super.init();
        
        int centerX = width / 2;
        
        // Add Tab Buttons
        int tabWidth = 60;
        int totalTabWidth = Tab.values().length * tabWidth;
        int startX = (width - totalTabWidth) / 2;
        int y = 45; 
        
        for (int i = 0; i < Tab.values().length; i++) {
            Tab t = Tab.values()[i];
            ACTButton btn = new ACTButton(startX + i * tabWidth, y, tabWidth, 20, Component.literal(t.label), b -> {
                currentTab = t;
                init(); // Re-init to refresh widgets
            });
            btn.active = (t != currentTab);
            addRenderableWidget(btn);
        }
        
        int contentY = y + 25;
        int spacing = 24;
        
        switch (currentTab) {
            case GENERAL -> initGeneral(centerX, contentY, spacing);
            case FLAGS -> initFlags(centerX, contentY, spacing);
            case DATA -> initData(centerX, contentY, spacing);
        }
        
        // Done/Cancel at bottom
        addRenderableWidget(new ACTButton(centerX - 100, height - 25, 100, 20,
                Component.translatable("gui.done"), b -> {
            set(currentItemStack);
            getMinecraft().setScreen(parent);
        }));
        addRenderableWidget(new ACTButton(centerX + 1, height - 25, 99, 20,
                Component.translatable("gui.act.cancel"), b -> getMinecraft().setScreen(parent)));
    }
    
    private void initGeneral(int centerX, int currentY, int spacing) {
        // Entity Type Selector
        addRenderableWidget(new ACTButton(centerX - 100, currentY, 200, 20,
                Component.literal("Set entity"), b -> {
            List<Tuple<String, SpawnEggItem>> eggs = new ArrayList<>();
            SpawnEggItem.eggs()
                    .forEach(egg -> eggs.add(new Tuple<>(egg.getDescription().getString(), egg)));
            getMinecraft().setScreen(new GuiButtonListSelector<>(GuiSpawnEggModifier.this,
                    Component.literal("Set entity"), eggs, egg -> {
                ItemStack newStack = new ItemStack(egg);
                CustomData oldData = currentItemStack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
                CompoundTag newTag = oldData.copyTag();
                
                ResourceLocation newId = getEntityRegistry().getKey(egg.getType(ItemStack.EMPTY));
                newTag.putString("id", newId.toString());
                
                if (!newTag.isEmpty()) {
                    newStack.set(DataComponents.ENTITY_DATA, CustomData.of(newTag));
                }
                
                if (currentItemStack.has(DataComponents.CUSTOM_NAME)) {
                    newStack.set(DataComponents.CUSTOM_NAME, currentItemStack.get(DataComponents.CUSTOM_NAME));
                }
                
                currentItemStack = newStack;
                return null;
            }));
        }));
        currentY += spacing;

        // Name
        EditBox nameBox = new EditBox(font, centerX - 100, currentY, 200, 20, Component.literal("Name"));
        nameBox.setMaxLength(256);
        String currentName = "";
        if (getEntityTag().contains("CustomName", 8)) {
            try {
                currentName = Component.Serializer.fromJson(getEntityTag().getString("CustomName"), getRegistryAccess()).getString().replaceAll("" + ChatUtils.MODIFIER, "&");
            } catch (Exception e) {
                // ignore
            }
        }
        nameBox.setValue(currentName);
        nameBox.setResponder(val -> updateEntityTag(tag -> {
            if (val.isEmpty()) {
                tag.remove("CustomName");
            } else {
                tag.putString("CustomName", Component.Serializer.toJson(Component.literal(val.replaceAll("&", String.valueOf(ChatUtils.MODIFIER))), getRegistryAccess()));
            }
        }));
        addRenderableWidget(nameBox);
        currentY += spacing;

        // CustomNameVisible
        addRenderableWidget(new GuiBooleanButton(centerX - 100, currentY, 200, 20,
                Component.literal("Show Name"),
                val -> updateEntityTag(tag -> tag.putBoolean("CustomNameVisible", val)),
                () -> getEntityTag().getBoolean("CustomNameVisible")
        ));
        currentY += spacing;

        // Specific Properties
        EntityType<?> type = ((SpawnEggItem) currentItemStack.getItem()).getType(currentItemStack);
        ResourceLocation id = getEntityRegistry().getKey(type);
        String idStr = id.toString();

        if (idStr.contains("zombie") || idStr.contains("piglin") || idStr.contains("hoglin") || idStr.contains("zoglin")) {
             addRenderableWidget(new GuiBooleanButton(centerX - 100, currentY, 200, 20,
                    Component.literal("Is Baby"), 
                    val -> updateEntityTag(tag -> tag.putBoolean("IsBaby", val)),
                    () -> getEntityTag().getBoolean("IsBaby")
            ));
            currentY += spacing;
        }
        
        if (idStr.contains("slime") || idStr.contains("magma_cube") || idStr.contains("phantom")) {
            addIntegerInput(centerX, currentY, "Size", "Size");
            currentY += spacing;
        }
        
        if (idStr.contains("creeper")) {
            addRenderableWidget(new GuiBooleanButton(centerX - 100, currentY, 200, 20,
                    Component.literal("Powered"), 
                    val -> updateEntityTag(tag -> tag.putBoolean("powered", val)),
                    () -> getEntityTag().getBoolean("powered")
            ));
            currentY += spacing;
            
            addIntegerInput(centerX, currentY, "Explosion Radius", "ExplosionRadius");
            currentY += spacing;
            addIntegerInput(centerX, currentY, "Fuse", "Fuse");
            currentY += spacing;
        }
        
        if (idStr.contains("cow") || idStr.contains("sheep") || idStr.contains("chicken") || idStr.contains("pig") || 
            idStr.contains("rabbit") || idStr.contains("wolf") || idStr.contains("cat") || idStr.contains("horse") ||
            idStr.contains("donkey") || idStr.contains("mule") || idStr.contains("llama") || idStr.contains("panda") ||
            idStr.contains("fox") || idStr.contains("turtle") || idStr.contains("villager")) {
             addIntegerInput(centerX, currentY, "Age", "Age");
             currentY += spacing;
        }
    }
    
    private void initFlags(int centerX, int currentY, int spacing) {
        String[] flags = {
            "CanPickUpLoot", "FallFlying", "Glowing", "HasVisualFire", 
            "Invulnerable", "LeftHanded", "NoAI", "NoGravity", 
            "OnGround", "PersistenceRequired", "Silent"
        };
        for (int i = 0; i < flags.length; i++) {
            String flag = flags[i];
            int col = i % 2;
            int row = i / 2;
            int x = col == 0 ? centerX - 100 : centerX + 2;
            int y = currentY + row * 22;
            
            addRenderableWidget(new GuiBooleanButton(x, y, 98, 20,
                    Component.literal(flag), 
                    val -> updateEntityTag(tag -> tag.putBoolean(flag, val)),
                    () -> getEntityTag().getBoolean(flag)
            ));
        }
    }
    
    private void initData(int centerX, int currentY, int spacing) {
        // Equipment
        addRenderableWidget(new ACTButton(centerX - 100, currentY, 200, 20,
                Component.literal("Edit Equipment"), b -> {
            CompoundTag tag = getEntityTag();
            ItemUtils.ContainerData data = getEquipmentData(tag);
            List<Component> slotNames = List.of(
                Component.literal("Main Hand"),
                Component.literal("Off Hand"),
                Component.literal("Head"),
                Component.literal("Chest"),
                Component.literal("Legs"),
                Component.literal("Feet"),
                Component.literal("Body"),
                Component.literal("Saddle")
            );
            getMinecraft().setScreen(new GuiContainerModifier(this, Component.literal("Equipment"), newData -> {
                updateEntityTag(t -> setEquipmentData(t, newData));
            }, data, slotNames));
        }));
        currentY += spacing;

        // Attributes
        addRenderableWidget(new ACTButton(centerX - 100, currentY, 200, 20,
                Component.literal("Edit Attributes"), b -> {
            CompoundTag tag = getEntityTag();
            List<CompoundTag> attributes = new ArrayList<>();
            if (tag.contains("attributes", 9)) {
                ListTag list = tag.getList("attributes", 10);
                for (int i = 0; i < list.size(); i++) {
                    attributes.add(list.getCompound(i));
                }
            } else if (tag.contains("Attributes", 9)) {
                ListTag list = tag.getList("Attributes", 10);
                for (int i = 0; i < list.size(); i++) {
                    attributes.add(list.getCompound(i));
                }
            }
            getMinecraft().setScreen(new GuiEntityAttributeModifier(this, attributes, newAttrs -> {
                updateEntityTag(t -> {
                    ListTag list = new ListTag();
                    newAttrs.forEach(list::add);
                    t.put("attributes", list);
                    t.remove("Attributes"); // Remove legacy key
                });
            }));
        }));
        currentY += spacing;

        // Active Effects
        addRenderableWidget(new ACTButton(centerX - 100, currentY, 200, 20,
                Component.literal("Edit Active Effects"), b -> {
            CompoundTag tag = getEntityTag();
            List<CompoundTag> effects = new ArrayList<>();
            if (tag.contains("active_effects", 9)) {
                ListTag list = tag.getList("active_effects", 10);
                for (int i = 0; i < list.size(); i++) {
                    effects.add(list.getCompound(i));
                }
            } else if (tag.contains("ActiveEffects", 9)) {
                ListTag list = tag.getList("ActiveEffects", 10);
                for (int i = 0; i < list.size(); i++) {
                    effects.add(list.getCompound(i));
                }
            }
            getMinecraft().setScreen(new GuiActiveEffectsModifier(this, effects, newEffects -> {
                updateEntityTag(t -> {
                    ListTag list = new ListTag();
                    newEffects.forEach(list::add);
                    t.put("active_effects", list);
                    t.remove("ActiveEffects"); // Remove legacy key
                });
            }));
        }));
        currentY += spacing;
        
        // Edit Raw NBT
        addRenderableWidget(new ACTButton(centerX - 100, currentY, 200, 20,
                Component.translatable("gui.act.modifier.tag.editor"), b -> {
            CompoundTag tag = getEntityTag();
            getMinecraft().setScreen(new GuiNBTModifier(GuiSpawnEggModifier.this, newTag -> {
                currentItemStack.set(DataComponents.ENTITY_DATA, CustomData.of(newTag));
            }, tag));
        }));
    }

    private void addIntegerInput(int centerX, int y, String label, String nbtKey) {
        EditBox editBox = new EditBox(font, centerX + 2, y, 95, 20, Component.literal(label));
        editBox.setValue(String.valueOf(getEntityTag().getInt(nbtKey)));
        editBox.setResponder(val -> {
            try {
                int i = Integer.parseInt(val);
                updateEntityTag(tag -> tag.putInt(nbtKey, i));
                editBox.setTextColor(0xE0E0E0);
            } catch (NumberFormatException e) {
                editBox.setTextColor(0xFF0000);
            }
        });
        
        addRenderableWidget(editBox);
        
        ACTButton labelBtn = new ACTButton(centerX - 100, y, 100, 20, Component.literal(label), b -> {});
        labelBtn.active = false;
        addRenderableWidget(labelBtn);
    }
    
    private CompoundTag getEntityTag() {
        CustomData customData = currentItemStack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
        return customData.copyTag();
    }

    private void updateEntityTag(Consumer<CompoundTag> updater) {
        CustomData customData = currentItemStack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        updater.accept(tag);
        
        if (!tag.contains("id")) {
            EntityType<?> type = ((SpawnEggItem) currentItemStack.getItem()).getType(ItemStack.EMPTY);
            ResourceLocation id = getEntityRegistry().getKey(type);
            tag.putString("id", id.toString());
        }
        
        if (tag.size() == 1 && tag.contains("id")) {
            currentItemStack.remove(DataComponents.ENTITY_DATA);
        } else {
            currentItemStack.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
        }
    }
    
    private ItemUtils.ContainerData getEquipmentData(CompoundTag tag) {
        NonNullList<ItemStack> stacks = NonNullList.withSize(8, ItemStack.EMPTY);
        net.minecraft.core.HolderLookup.Provider registryAccess = getRegistryAccess();
        
        if (tag.contains("hand_items", 9)) {
            ListTag list = tag.getList("hand_items", 10);
            for (int i = 0; i < list.size() && i < 2; i++) {
                stacks.set(i, ItemStack.parseOptional(registryAccess, list.getCompound(i)));
            }
        } else if (tag.contains("HandItems", 9)) {
            ListTag list = tag.getList("HandItems", 10);
            for (int i = 0; i < list.size() && i < 2; i++) {
                stacks.set(i, ItemStack.parseOptional(registryAccess, list.getCompound(i)));
            }
        }
        
        if (tag.contains("armor_items", 9)) {
            ListTag list = tag.getList("armor_items", 10);
            if (list.size() > 0) stacks.set(5, ItemStack.parseOptional(registryAccess, list.getCompound(0)));
            if (list.size() > 1) stacks.set(4, ItemStack.parseOptional(registryAccess, list.getCompound(1)));
            if (list.size() > 2) stacks.set(3, ItemStack.parseOptional(registryAccess, list.getCompound(2)));
            if (list.size() > 3) stacks.set(2, ItemStack.parseOptional(registryAccess, list.getCompound(3)));
        } else if (tag.contains("ArmorItems", 9)) {
            ListTag list = tag.getList("ArmorItems", 10);
            if (list.size() > 0) stacks.set(5, ItemStack.parseOptional(registryAccess, list.getCompound(0)));
            if (list.size() > 1) stacks.set(4, ItemStack.parseOptional(registryAccess, list.getCompound(1)));
            if (list.size() > 2) stacks.set(3, ItemStack.parseOptional(registryAccess, list.getCompound(2)));
            if (list.size() > 3) stacks.set(2, ItemStack.parseOptional(registryAccess, list.getCompound(3)));
        }
        
        // Body Armor (Slot 6)
        if (tag.contains("body_armor_item", 10)) {
            stacks.set(6, ItemStack.parseOptional(registryAccess, tag.getCompound("body_armor_item")));
        } else if (tag.contains("ArmorItem", 10)) { // Horse (Legacy)
            stacks.set(6, ItemStack.parseOptional(registryAccess, tag.getCompound("ArmorItem")));
        } else if (tag.contains("BodyArmorItem", 10)) { // Wolf (Legacy)
            stacks.set(6, ItemStack.parseOptional(registryAccess, tag.getCompound("BodyArmorItem")));
        } else if (tag.contains("DecorItem", 10)) { // Llama (Legacy)
            stacks.set(6, ItemStack.parseOptional(registryAccess, tag.getCompound("DecorItem")));
        }
        
        // Saddle (Slot 7)
        if (tag.contains("saddle", 10)) {
            stacks.set(7, ItemStack.parseOptional(registryAccess, tag.getCompound("saddle")));
        } else if (tag.contains("SaddleItem", 10)) {
            stacks.set(7, ItemStack.parseOptional(registryAccess, tag.getCompound("SaddleItem")));
        } else if (tag.getBoolean("Saddle")) {
            stacks.set(7, new ItemStack(net.minecraft.world.item.Items.SADDLE));
        }
        
        return new ItemUtils.ContainerData(new ItemUtils.ContainerSize(2, 4), stacks);
    }

    private void setEquipmentData(CompoundTag tag, ItemUtils.ContainerData data) {
        ListTag handItems = new ListTag();
        handItems.add(getTagOrEmpty(data.stacks().get(0)));
        handItems.add(getTagOrEmpty(data.stacks().get(1)));
        tag.put("HandItems", handItems);
        tag.remove("hand_items");

        ListTag armorItems = new ListTag();
        armorItems.add(getTagOrEmpty(data.stacks().get(5)));
        armorItems.add(getTagOrEmpty(data.stacks().get(4)));
        armorItems.add(getTagOrEmpty(data.stacks().get(3)));
        armorItems.add(getTagOrEmpty(data.stacks().get(2)));
        tag.put("ArmorItems", armorItems);
        tag.remove("armor_items");
        
        EntityType<?> type = ((SpawnEggItem) currentItemStack.getItem()).getType(currentItemStack);
        ResourceLocation id = getEntityRegistry().getKey(type);
        String path = id.getPath();

        // Body Armor (Slot 6)
        ItemStack bodyStack = data.stacks().get(6);
        CompoundTag bodyTag = getTagOrEmpty(bodyStack);
        
        if (path.equals("wolf")) {
            if (!bodyStack.isEmpty()) tag.put("BodyArmorItem", bodyTag);
            else tag.remove("BodyArmorItem");
        } else if (path.contains("llama")) {
            if (!bodyStack.isEmpty()) tag.put("DecorItem", bodyTag);
            else tag.remove("DecorItem");
        } else if (path.equals("horse")) {
            if (!bodyStack.isEmpty()) tag.put("ArmorItem", bodyTag);
            else tag.remove("ArmorItem");
        }
        
        tag.remove("body_armor_item");
        
        // Saddle (Slot 7)
        ItemStack saddleStack = data.stacks().get(7);
        if (path.equals("pig") || path.equals("strider")) {
            tag.putBoolean("Saddle", !saddleStack.isEmpty());
            tag.remove("SaddleItem");
        } else {
            if (!saddleStack.isEmpty()) {
                tag.put("SaddleItem", getTagOrEmpty(saddleStack));
            } else {
                tag.remove("SaddleItem");
            }
            tag.remove("Saddle");
        }
        tag.remove("saddle");
    }
    
    private CompoundTag getTagOrEmpty(ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        return (CompoundTag) stack.save(getRegistryAccess());
    }

    private net.minecraft.core.HolderLookup.Provider getRegistryAccess() {
        return mc.level != null ? mc.level.registryAccess() : VanillaRegistries.createLookup();
    }

    private Registry<EntityType<?>> getEntityRegistry() {
        if (mc.level != null) {
            return mc.level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        }
        return BuiltInRegistries.ENTITY_TYPE;
    }
}
