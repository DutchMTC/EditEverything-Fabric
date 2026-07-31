package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTModifier;
import com.dutchmtc.ee.gui.modifier.mob.GuiMobFieldsEditor;
import com.dutchmtc.ee.gui.modifier.mob.GuiVillagerTradesEditor;
import com.dutchmtc.ee.gui.modifier.mob.MobPropertyEditorState;
import com.dutchmtc.ee.mobdata.MobDataRepository;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class GuiSpawnEggModifier extends GuiModifier<ItemStack> {
    private ItemStack currentItemStack;
    private final ItemStack originalItemStack;

    private static final float DEFAULT_EQUIPMENT_DROP_CHANCE = 0.085F;
    private static final String[] EQUIPMENT_DROP_CHANCE_KEYS = {
            "mainhand",
            "offhand",
            "head",
            "chest",
            "legs",
            "feet",
            "body",
            "saddle"
    };
    
    private enum Tab {
        GENERAL("General"),
        FLAGS("Flags"),
        DATA("Data"),
        TRADES("Trades");
        
        final String label;
        Tab(String label) { this.label = label; }
    }
    
    private Tab currentTab = Tab.GENERAL;

    public GuiSpawnEggModifier(Screen parent, Consumer<ItemStack> setter, ItemStack currentItemStack) {
        super(parent, Component.literal("Set entity"), setter);
        this.originalItemStack = currentItemStack;
        this.currentItemStack = currentItemStack.copy();
    }

    @Override
    public boolean isModified() {
        return !ItemStack.matches(currentItemStack, originalItemStack);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        
        // Draw Item Stack (Fixed at top)
        if (currentItemStack != null) {
            GuiUtils.drawItemStack(graphics, currentItemStack, width / 2 - 10, 20);
            if (GuiUtils.isHover(width / 2 - 10, 20, 20, 20, mouseX, mouseY))
                GuiUtils.setTooltipForNextFrame(graphics, font, currentItemStack, mouseX, mouseY);
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
        List<Tab> tabs = getVisibleTabs();
        if (!tabs.contains(currentTab)) {
            currentTab = Tab.GENERAL;
        }

        int totalTabWidth = tabs.size() * tabWidth;
        int startX = (width - totalTabWidth) / 2;
        int y = 45; 
        
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            EEButton btn = new EEButton(startX + i * tabWidth, y, tabWidth, 20, Component.literal(t.label), b -> {
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
            case TRADES -> initTrades(centerX, contentY, spacing);
        }
        
        // Done/Cancel at bottom
        addRenderableWidget(new EEButton(centerX - 100, height - 25, 100, 20,
                Component.translatable("gui.ee.cancel"), b -> onCancel()));
        addRenderableWidget(new EEButton(centerX + 1, height - 25, 99, 20,
                Component.translatable("gui.done"), b -> {
            set(currentItemStack);
            getMinecraft().gui.setScreen(parent);
        }));
    }
    
    private void initGeneral(int centerX, int currentY, int spacing) {
        // Spawn Egg Item Selector
        addRenderableWidget(new EEButton(centerX - 100, currentY, 200, 20,
                Component.literal("Set Spawn Egg Item"), b -> {
            List<Tuple<String, SpawnEggItem>> eggs = new ArrayList<>();
            net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .stream()
                    .filter(item -> item instanceof SpawnEggItem)
                    .map(item -> (SpawnEggItem) item)
                    .forEach(egg -> eggs.add(new Tuple<>(egg.getName(new ItemStack(egg)).getString(), egg)));
            getMinecraft().gui.setScreen(new GuiButtonListSelector<>(GuiSpawnEggModifier.this,
                    Component.literal("Set Spawn Egg Item"), eggs, egg -> {
                ItemStack newStack = new ItemStack(egg);
                TypedEntityData<EntityType<?>> oldData = ItemUtils.getComponent(currentItemStack, DataComponents.ENTITY_DATA);
                CompoundTag newTag = oldData != null ? oldData.copyTagWithoutId() : new CompoundTag();
                EntityType<?> newType = getEggEntityType(newStack);
                ItemUtils.setComponent(newStack, DataComponents.ENTITY_DATA, TypedEntityData.of(newType, newTag));
                
                if (ItemUtils.getComponent(currentItemStack, DataComponents.CUSTOM_NAME) != null) {
                    ItemUtils.setComponent(newStack, DataComponents.CUSTOM_NAME, ItemUtils.getComponent(currentItemStack, DataComponents.CUSTOM_NAME));
                }
                
                currentItemStack = newStack;
                return null;
            }));
        }));
        currentY += spacing;

        // Entity Type Selector
        addRenderableWidget(new EEButton(centerX - 100, currentY, 200, 20,
                Component.literal("Set Entity Type"), b -> {
            List<Tuple<String, EntityType<?>>> entities = new ArrayList<>();
            BuiltInRegistries.ENTITY_TYPE.stream()
                    .filter(EntityType::canSummon)
                    .sorted(Comparator.comparing(t -> t.getDescription().getString()))
                    .forEach(type -> entities.add(new Tuple<>(type.getDescription().getString(), type)));
            
            getMinecraft().gui.setScreen(new GuiButtonListSelector<>(GuiSpawnEggModifier.this,
                    Component.literal("Set Entity Type"), entities, type -> {
                TypedEntityData<EntityType<?>> oldData = ItemUtils.getComponent(currentItemStack, DataComponents.ENTITY_DATA);
                CompoundTag newTag = oldData != null ? oldData.copyTagWithoutId() : new CompoundTag();
                ItemUtils.setComponent(currentItemStack, DataComponents.ENTITY_DATA, TypedEntityData.of(type, newTag));
                return null;
            }));
        }));
        currentY += spacing;

        EntityType<?> type = getCurrentEntityType(currentItemStack);
        Identifier id = getEntityRegistry().getKey(type);
        String idStr = id.toString();

        // Mob Fields / Feature Sets (data-driven)
        addRenderableWidget(new EEButton(centerX - 100, currentY, 200, 20,
                Component.literal("Edit Mob Properties"), b -> {
            EntityType<?> entityType = getCurrentEntityType(currentItemStack);
            Identifier entityId = getEntityRegistry().getKey(entityType);
            String entityPath = entityId.getPath();

            CompoundTag tag = getEntityTag();

            // Default enabled feature sets from mob_fields.json (if present)
            java.util.Set<String> featureSets = new java.util.LinkedHashSet<>();
            JsonObject entity = MobDataRepository.getEntity(entityPath);
            if (entity != null && entity.has("feature_sets") && entity.get("feature_sets").isJsonArray()) {
                JsonArray arr = entity.getAsJsonArray("feature_sets");
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i).isJsonPrimitive()) {
                        featureSets.add(arr.get(i).getAsString());
                    }
                }
            }

            MobPropertyEditorState state = MobPropertyEditorState.createDefault(entityPath, tag, featureSets);
            getMinecraft().gui.setScreen(new GuiMobFieldsEditor(GuiSpawnEggModifier.this, this::setEntityTag, state));
        }));
        currentY += spacing;

        // Name
        addNameInput(centerX, currentY);
        currentY += spacing;

        // CustomNameVisible
        addRenderableWidget(new GuiBooleanButton(centerX - 100, currentY, 200, 20,
                Component.literal("Show Name"),
                val -> updateEntityTag(tag -> {
                    ItemUtils.putBoolean(tag, "CustomNameVisible", val);
                    ItemUtils.remove(tag, "custom_name_visible");
                }),
                () -> ItemUtils.getBoolean(getEntityTag(), "CustomNameVisible") || ItemUtils.getBoolean(getEntityTag(), "custom_name_visible")
        ));
        currentY += spacing;
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
                    val -> updateEntityTag(tag -> ItemUtils.putBoolean(tag, flag, val)),
                    () -> ItemUtils.getBoolean(getEntityTag(), flag)
            ));
        }
    }
    
    private void initData(int centerX, int currentY, int spacing) {
        // Equipment
        addRenderableWidget(new EEButton(centerX - 100, currentY, 200, 20,
                Component.literal("Edit Equipment"), b -> {
            CompoundTag tag = getEntityTag();
            ItemUtils.ContainerData equipment = getEquipmentData(tag);
            float[] dropChances = getEquipmentDropChances(tag);
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
            getMinecraft().gui.setScreen(new GuiSpawnEggEquipmentModifier(this, Component.literal("Equipment"), newData -> {
                updateEntityTag(t -> {
                    setEquipmentData(t, newData.equipment());
                    setEquipmentDropChances(t, newData.dropChances());
                });
            }, new GuiSpawnEggEquipmentModifier.EquipmentData(equipment, dropChances), slotNames));
        }));
        currentY += spacing;

        // Attributes
        addRenderableWidget(new EEButton(centerX - 100, currentY, 200, 20,
                Component.literal("Edit Attributes"), b -> {
            CompoundTag tag = getEntityTag();
            List<CompoundTag> attributes = new ArrayList<>();
            if (ItemUtils.hasTag(tag, "attributes", 9)) {
                ListTag list = ItemUtils.getList(tag, "attributes", 10);
                for (int i = 0; i < list.size(); i++) {
                    attributes.add(ItemUtils.getCompound(list, i));
                }
            } else if (ItemUtils.hasTag(tag, "Attributes", 9)) {
                ListTag list = ItemUtils.getList(tag, "Attributes", 10);
                for (int i = 0; i < list.size(); i++) {
                    attributes.add(ItemUtils.getCompound(list, i));
                }
            }
            getMinecraft().gui.setScreen(new GuiEntityAttributeModifier(this, attributes, newAttrs -> {
                updateEntityTag(t -> {
                    ListTag list = new ListTag();
                    newAttrs.forEach(list::add);
                    t.put("attributes", list);
                    ItemUtils.remove(t, "Attributes"); // Remove legacy key
                });
            }));
        }));
        currentY += spacing;

        // Active Effects
        addRenderableWidget(new EEButton(centerX - 100, currentY, 200, 20,
                Component.literal("Edit Active Effects"), b -> {
            CompoundTag tag = getEntityTag();
            List<CompoundTag> effects = new ArrayList<>();
            if (ItemUtils.hasTag(tag, "active_effects", 9)) {
                ListTag list = ItemUtils.getList(tag, "active_effects", 10);
                for (int i = 0; i < list.size(); i++) {
                    effects.add(ItemUtils.getCompound(list, i));
                }
            } else if (ItemUtils.hasTag(tag, "ActiveEffects", 9)) {
                ListTag list = ItemUtils.getList(tag, "ActiveEffects", 10);
                for (int i = 0; i < list.size(); i++) {
                    effects.add(ItemUtils.getCompound(list, i));
                }
            }
            getMinecraft().gui.setScreen(new GuiActiveEffectsModifier(this, effects, newEffects -> {
                updateEntityTag(t -> {
                    ListTag list = new ListTag();
                    newEffects.forEach(list::add);
                    t.put("active_effects", list);
                    ItemUtils.remove(t, "ActiveEffects"); // Remove legacy key
                });
            }));
        }));
        currentY += spacing;
        
        // Edit Raw NBT
        addRenderableWidget(new EEButton(centerX - 100, currentY, 200, 20,
                Component.translatable("gui.ee.modifier.tag.editor"), b -> {
            CompoundTag tag = getEntityTag();
            getMinecraft().gui.setScreen(new GuiNBTModifier(GuiSpawnEggModifier.this, newTag -> {
                setEntityTag(newTag);
            }, tag));
        }));
    }

    private void initTrades(int centerX, int currentY, int spacing) {
        if (!supportsTradesTab()) {
            EEButton info = new EEButton(centerX - 100, currentY, 200, 20,
                    Component.literal("Trades are only available for villager-type entities."), b -> {});
            info.active = false;
            addRenderableWidget(info);
            return;
        }

        int tradeCount = getTradeRecipes(getEntityTag()).size();
        EEButton count = new EEButton(centerX - 100, currentY, 200, 20,
                Component.literal("Trades: " + tradeCount), b -> {});
        count.active = false;
        addRenderableWidget(count);
        currentY += spacing;

        addRenderableWidget(new EEButton(centerX - 100, currentY, 200, 20,
                Component.literal("Edit Trades"), b -> {
            List<CompoundTag> recipes = getTradeRecipes(getEntityTag());
            // Defer opening to the next tick to avoid the screen being overwritten by other UI transitions
            // (e.g. chat closing), which can look like the GUI "opens for 1 frame then closes".
            var mc = getMinecraft();
            mc.execute(() -> mc.gui.setScreen(new GuiVillagerTradesEditor(this, recipes, newRecipes -> {
                updateEntityTag(tag -> {
                    setTradeRecipes(tag, newRecipes);
                    ensureVillagerDataForTrading(tag);
                });
            })));
        }));
        currentY += spacing;

        addRenderableWidget(new EEButton(centerX - 100, currentY, 200, 20,
                Component.literal("Edit Offers NBT (raw)"), b -> {
            CompoundTag tag = getEntityTag();
            CompoundTag offers = tag.getCompound("Offers").orElse(new CompoundTag());
            getMinecraft().gui.setScreen(new GuiNBTModifier(Component.literal("Offers"), this, this::setOffersTag, offers));
        }));
    }

    private void setOffersTag(CompoundTag offersTag) {
        updateEntityTag(tag -> {
            if (offersTag == null || offersTag.isEmpty()) {
                ItemUtils.remove(tag, "Offers");
            } else {
                tag.put("Offers", offersTag);
                ensureVillagerDataForTrading(tag);
            }
        });
    }

    private void ensureVillagerDataForTrading(CompoundTag entityTag) {
        if (entityTag == null) {
            return;
        }

        EntityType<?> type = getCurrentEntityType(currentItemStack);
        Identifier id = getEntityRegistry().getKey(type);
        if (id == null) {
            return;
        }

        // Villager trading can instantly close if VillagerData is missing or the profession is "none".
        // Only apply sane defaults for vanilla villager variants; don't clobber custom entities.
        String path = id.getPath();
        if (!"villager".equals(path) && !"zombie_villager".equals(path)) {
            return;
        }

        CompoundTag data = entityTag.getCompound("VillagerData").orElse(new CompoundTag());
        boolean changed = false;

        String profession = data.getString("profession").orElse("");
        if (profession.isEmpty() || "minecraft:none".equals(profession) || "none".equals(profession)) {
            data.putString("profession", "minecraft:farmer");
            changed = true;
        }

        String villagerType = data.getString("type").orElse("");
        if (villagerType.isEmpty()) {
            data.putString("type", "minecraft:plains");
            changed = true;
        }

        if (!ItemUtils.hasTag(data, "level", Tag.TAG_INT) && !ItemUtils.hasTag(data, "level", Tag.TAG_BYTE)) {
            data.putInt("level", 2);
            changed = true;
        }

        if (changed) {
            entityTag.put("VillagerData", data);
        }
    }

    private static List<CompoundTag> getTradeRecipes(CompoundTag entityTag) {
        List<CompoundTag> out = new ArrayList<>();
        if (!ItemUtils.hasTag(entityTag, "Offers", Tag.TAG_COMPOUND)) return out;
        CompoundTag offers = entityTag.getCompound("Offers").orElse(new CompoundTag());
        if (!ItemUtils.hasTag(offers, "Recipes", Tag.TAG_LIST)) return out;
        ListTag recipes = offers.getList("Recipes").orElse(new ListTag());
        for (int i = 0; i < recipes.size(); i++) {
            Tag t = recipes.get(i);
            if (t instanceof CompoundTag ct) {
                out.add(ct.copy());
            }
        }
        return out;
    }

    private static void setTradeRecipes(CompoundTag entityTag, List<CompoundTag> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            if (ItemUtils.hasTag(entityTag, "Offers", Tag.TAG_COMPOUND)) {
                CompoundTag offers = entityTag.getCompound("Offers").orElse(new CompoundTag());
                ItemUtils.remove(offers, "Recipes");
                if (offers.isEmpty()) {
                    ItemUtils.remove(entityTag, "Offers");
                } else {
                    entityTag.put("Offers", offers);
                }
            }
            return;
        }

        CompoundTag offers = entityTag.getCompound("Offers").orElse(new CompoundTag());
        ListTag list = new ListTag();
        for (CompoundTag recipe : recipes) {
            list.add(recipe);
        }
        offers.put("Recipes", list);
        entityTag.put("Offers", offers);
    }

    private List<Tab> getVisibleTabs() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(Tab.GENERAL);
        tabs.add(Tab.FLAGS);
        tabs.add(Tab.DATA);
        if (supportsTradesTab()) {
            tabs.add(Tab.TRADES);
        }
        return tabs;
    }

    private boolean supportsTradesTab() {
        EntityType<?> type = getCurrentEntityType(currentItemStack);
        Identifier id = getEntityRegistry().getKey(type);
        if (id == null) return false;
        String path = id.getPath();
        return "villager".equals(path) || "zombie_villager".equals(path) || "wandering_trader".equals(path);
    }

    private void addIntegerInput(int centerX, int y, String label, String nbtKey) {
        EditBox editBox = new EditBox(font, centerX + 2, y, 95, 20, Component.literal(label));
        editBox.setValue(String.valueOf(ItemUtils.getInt(getEntityTag(), nbtKey)));
        editBox.setResponder(val -> {
            try {
                int i = Integer.parseInt(val);
                updateEntityTag(tag -> ItemUtils.putInt(tag, nbtKey, i));
                editBox.setTextColor(0xE0E0E0);
            } catch (NumberFormatException e) {
                editBox.setTextColor(0xFF0000);
            }
        });
        
        addRenderableWidget(editBox);
        
        EEButton labelBtn = new EEButton(centerX - 100, y, 100, 20, Component.literal(label), b -> {});
        labelBtn.active = false;
        addRenderableWidget(labelBtn);
    }
    
    private CompoundTag getEntityTag() {
        TypedEntityData<EntityType<?>> entityData = ItemUtils.getComponent(currentItemStack, DataComponents.ENTITY_DATA);
        return entityData != null ? entityData.copyTagWithoutId() : new CompoundTag();
    }

    private void updateEntityTag(Consumer<CompoundTag> updater) {
        TypedEntityData<EntityType<?>> entityData = ItemUtils.getComponent(currentItemStack, DataComponents.ENTITY_DATA);
        EntityType<?> type = entityData != null ? entityData.type() : getEggEntityType(currentItemStack);
        CompoundTag tag = entityData != null ? entityData.copyTagWithoutId() : new CompoundTag();
        updater.accept(tag);
        
        if (tag.isEmpty()) {
            ItemUtils.setComponent(currentItemStack, DataComponents.ENTITY_DATA, null);
        } else {
            ItemUtils.setComponent(currentItemStack, DataComponents.ENTITY_DATA, TypedEntityData.of(type, tag));
        }
    }

    private void setEntityTag(CompoundTag newTag) {
        TypedEntityData<EntityType<?>> entityData = ItemUtils.getComponent(currentItemStack, DataComponents.ENTITY_DATA);
        EntityType<?> type = entityData != null ? entityData.type() : getEggEntityType(currentItemStack);
        if (newTag == null || newTag.isEmpty()) {
            ItemUtils.setComponent(currentItemStack, DataComponents.ENTITY_DATA, null);
        } else {
            ItemUtils.setComponent(currentItemStack, DataComponents.ENTITY_DATA, TypedEntityData.of(type, newTag));
        }
    }

    private static Component parseComponent(String json) {
        try {
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .result()
                    .orElse(Component.empty());
        } catch (Exception e) {
            return Component.empty();
        }
    }

    private static Component parseComponentSnbt(String snbt) {
        try {
            CompoundTag wrapper = TagParser.parseCompoundFully("{v:" + snbt + "}");
            Tag tag;
            if (snbt.startsWith("[")) {
                tag = wrapper.getList("v").orElse(new ListTag());
            } else {
                tag = wrapper.getCompound("v").orElse(new CompoundTag());
            }
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, tag)
                    .result()
                    .orElse(Component.empty());
        } catch (Exception e) {
            return Component.empty();
        }
    }

    private static Tag toComponentNbt(Component component) {
        return ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component).result().orElse(null);
    }

    private static String toComponentJson(Component component) {
        return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component)
                .result()
                .map(Object::toString)
                .orElse("{\"text\":\"\"}");
    }

    private static Component readEntityCustomName(CompoundTag entityTag) {
        if (ItemUtils.hasTag(entityTag, "CustomName", Tag.TAG_COMPOUND)) {
            CompoundTag raw = entityTag.getCompound("CustomName").orElse(new CompoundTag());
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, raw).result().orElse(Component.empty());
        }
        if (ItemUtils.hasTag(entityTag, "CustomName", Tag.TAG_LIST)) {
            ListTag raw = entityTag.getList("CustomName").orElse(new ListTag());
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, raw).result().orElse(Component.empty());
        }
        if (ItemUtils.hasTag(entityTag, "custom_name", Tag.TAG_STRING)) {
            String raw = ItemUtils.getString(entityTag, "custom_name");
            Component parsed = parseComponent(raw);
            if (!parsed.getString().isEmpty()) {
                return parsed;
            }
            return parseComponentSnbt(raw);
        }
        if (ItemUtils.hasTag(entityTag, "custom_name", Tag.TAG_COMPOUND)) {
            CompoundTag raw = entityTag.getCompound("custom_name").orElse(new CompoundTag());
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, raw).result().orElse(Component.empty());
        }
        if (ItemUtils.hasTag(entityTag, "custom_name", Tag.TAG_LIST)) {
            ListTag raw = entityTag.getList("custom_name").orElse(new ListTag());
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, raw).result().orElse(Component.empty());
        }
        if (ItemUtils.hasTag(entityTag, "CustomName", Tag.TAG_STRING)) {
            String raw = ItemUtils.getString(entityTag, "CustomName");
            // 1.21.5+ stores text components directly in NBT; legacy may still be JSON/SNBT-in-a-string
            Component parsed = parseComponent(raw);
            if (!parsed.getString().isEmpty()) return parsed;
            parsed = parseComponentSnbt(raw);
            if (!parsed.getString().isEmpty()) return parsed;
            // If it's a plain string, it's already a valid (string) text component.
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, StringTag.valueOf(raw)).result().orElse(Component.literal(raw));
        }
        return Component.empty();
    }

    private static EntityType<?> getEggEntityType(ItemStack stack) {
        if (stack.getItem() instanceof SpawnEggItem egg) {
            ItemStack baseStack = new ItemStack(egg);
            try {
                return egg.getType(baseStack);
            } catch (Throwable t) {
                return egg.getType(stack);
            }
        }
        return EntityTypes.PIG;
    }

    private static EntityType<?> getCurrentEntityType(ItemStack stack) {
        TypedEntityData<EntityType<?>> entityData = ItemUtils.getComponent(stack, DataComponents.ENTITY_DATA);
        return entityData != null ? entityData.type() : getEggEntityType(stack);
    }

    private static Component parseLegacyFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        String formatted = ChatUtils.translateColorCodes(input);
        StringBuilder buffer = new StringBuilder();
        List<net.minecraft.ChatFormatting> formats = new ArrayList<>();
        Integer rgbColor = null;
        MutableComponent out = Component.literal("");

        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (c == ChatUtils.MODIFIER && i + 1 < formatted.length()) {
                char code = Character.toLowerCase(formatted.charAt(i + 1));

                // §x§R§R§G§G§B§B
                if (code == 'x' && i + 13 < formatted.length()) {
                    int rgb = tryParseSectionHexColor(formatted, i);
                    if (rgb >= 0) {
                        if (buffer.length() > 0) {
                            MutableComponent part = Component.literal(buffer.toString());
                            if (!formats.isEmpty()) {
                                part = part.withStyle(formats.toArray(new net.minecraft.ChatFormatting[0]));
                            }
                            if (rgbColor != null) {
                                int cRgb = rgbColor;
                                part = part.withStyle(s -> s.withColor(cRgb));
                            }
                            out.append(part);
                            buffer.setLength(0);
                        }

                        formats.clear();
                        rgbColor = rgb;
                        i += 13;
                        continue;
                    }
                }

                net.minecraft.ChatFormatting fmt = legacyCodeToFormatting(code);
                if (fmt != null) {
                    if (buffer.length() > 0) {
                        MutableComponent part = Component.literal(buffer.toString());
                        if (!formats.isEmpty()) {
                            part = part.withStyle(formats.toArray(new net.minecraft.ChatFormatting[0]));
                        }
                        if (rgbColor != null) {
                            int cRgb = rgbColor;
                            part = part.withStyle(s -> s.withColor(cRgb));
                        }
                        out.append(part);
                        buffer.setLength(0);
                    }

                    if (code == 'r') {
                        formats.clear();
                        rgbColor = null;
                    } else if (com.dutchmtc.ee.utils.ChatUtils.isColor(fmt)) {
                        formats.clear();
                        formats.add(fmt);
                        rgbColor = null;
                    } else if (!formats.contains(fmt)) {
                        formats.add(fmt);
                    }

                    i++; // skip code
                    continue;
                }
            }
            buffer.append(c);
        }

        if (buffer.length() > 0) {
            MutableComponent part = Component.literal(buffer.toString());
            if (!formats.isEmpty()) {
                part = part.withStyle(formats.toArray(new net.minecraft.ChatFormatting[0]));
            }
            if (rgbColor != null) {
                int cRgb = rgbColor;
                part = part.withStyle(s -> s.withColor(cRgb));
            }
            out.append(part);
        }

        return out;
    }

    private static int tryParseSectionHexColor(String s, int sectionIndex) {
        // §x§R§R§G§G§B§B -> 14 chars total, starting at §
        if (sectionIndex + 13 >= s.length()) return -1;
        if (s.charAt(sectionIndex) != ChatUtils.MODIFIER) return -1;
        if (Character.toLowerCase(s.charAt(sectionIndex + 1)) != 'x') return -1;

        int rgb = 0;
        for (int j = 0; j < 6; j++) {
            int sepIndex = sectionIndex + 2 + j * 2;
            int digitIndex = sectionIndex + 3 + j * 2;
            if (s.charAt(sepIndex) != ChatUtils.MODIFIER) return -1;
            int d = Character.digit(s.charAt(digitIndex), 16);
            if (d < 0) return -1;
            rgb = (rgb << 4) | d;
        }
        return rgb;
    }

    private static net.minecraft.ChatFormatting legacyCodeToFormatting(char code) {
        for (net.minecraft.ChatFormatting f : net.minecraft.ChatFormatting.values()) {
            String s = f.toString();
            if (s.length() >= 2 && Character.toLowerCase(s.charAt(1)) == Character.toLowerCase(code)) {
                return f;
            }
        }
        return null;
    }
     
    private ItemUtils.ContainerData getEquipmentData(CompoundTag tag) {
        NonNullList<ItemStack> stacks = NonNullList.withSize(8, ItemStack.EMPTY);
        net.minecraft.core.HolderLookup.Provider registryAccess = getRegistryAccess();

        // 1.21.5+ equipment format (preferred in 1.21.11)
        if (ItemUtils.hasTag(tag, "equipment", Tag.TAG_COMPOUND)) {
            CompoundTag equipment = ItemUtils.getCompound(tag, "equipment");
            stacks.set(0, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "mainhand")));
            stacks.set(1, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "offhand")));
            stacks.set(2, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "head")));
            stacks.set(3, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "chest")));
            stacks.set(4, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "legs")));
            stacks.set(5, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "feet")));
            stacks.set(6, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "body")));
            stacks.set(7, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "saddle")));
        }
        
        // Legacy fallbacks (only fill slots not already present)
        if (stacks.get(0).isEmpty() && stacks.get(1).isEmpty() && ItemUtils.hasTag(tag, "hand_items", 9)) {
            ListTag list = ItemUtils.getList(tag, "hand_items", 10);
            for (int i = 0; i < list.size() && i < 2; i++) {
                stacks.set(i, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, i)));
            }
        } else if (stacks.get(0).isEmpty() && stacks.get(1).isEmpty() && ItemUtils.hasTag(tag, "HandItems", 9)) {
            ListTag list = ItemUtils.getList(tag, "HandItems", 10);
            for (int i = 0; i < list.size() && i < 2; i++) {
                stacks.set(i, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, i)));
            }
        }
        
        if (stacks.get(2).isEmpty() && stacks.get(3).isEmpty() && stacks.get(4).isEmpty() && stacks.get(5).isEmpty() && ItemUtils.hasTag(tag, "armor_items", 9)) {
            ListTag list = ItemUtils.getList(tag, "armor_items", 10);
            if (list.size() > 0) stacks.set(5, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 0)));
            if (list.size() > 1) stacks.set(4, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 1)));
            if (list.size() > 2) stacks.set(3, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 2)));
            if (list.size() > 3) stacks.set(2, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 3)));
        } else if (stacks.get(2).isEmpty() && stacks.get(3).isEmpty() && stacks.get(4).isEmpty() && stacks.get(5).isEmpty() && ItemUtils.hasTag(tag, "ArmorItems", 9)) {
            ListTag list = ItemUtils.getList(tag, "ArmorItems", 10);
            if (list.size() > 0) stacks.set(5, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 0)));
            if (list.size() > 1) stacks.set(4, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 1)));
            if (list.size() > 2) stacks.set(3, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 2)));
            if (list.size() > 3) stacks.set(2, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 3)));
        }
        
        // Body Armor (Slot 6)
        if (stacks.get(6).isEmpty() && ItemUtils.hasTag(tag, "body_armor_item", 10)) {
            stacks.set(6, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "body_armor_item")));
        } else if (stacks.get(6).isEmpty() && ItemUtils.hasTag(tag, "ArmorItem", 10)) { // Horse (Legacy)
            stacks.set(6, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "ArmorItem")));
        } else if (stacks.get(6).isEmpty() && ItemUtils.hasTag(tag, "BodyArmorItem", 10)) { // Wolf (Legacy)
            stacks.set(6, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "BodyArmorItem")));
        } else if (stacks.get(6).isEmpty() && ItemUtils.hasTag(tag, "DecorItem", 10)) { // Llama (Legacy)
            stacks.set(6, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "DecorItem")));
        }
        
        // Saddle (Slot 7)
        if (stacks.get(7).isEmpty() && ItemUtils.hasTag(tag, "saddle", 10)) {
            stacks.set(7, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "saddle")));
        } else if (stacks.get(7).isEmpty() && ItemUtils.hasTag(tag, "SaddleItem", 10)) {
            stacks.set(7, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "SaddleItem")));
        } else if (stacks.get(7).isEmpty() && ItemUtils.getBoolean(tag, "Saddle")) {
            stacks.set(7, new ItemStack(net.minecraft.world.item.Items.SADDLE));
        }
        
        return new ItemUtils.ContainerData(new ItemUtils.ContainerSize(2, 4), stacks);
    }

    private float[] getEquipmentDropChances(CompoundTag tag) {
        float[] chances = new float[8];
        Arrays.fill(chances, DEFAULT_EQUIPMENT_DROP_CHANCE);

        // 1.21.5+ format: drop_chances compound keyed by slot
        if (ItemUtils.hasTag(tag, "drop_chances", Tag.TAG_COMPOUND)) {
            CompoundTag dropChances = ItemUtils.getCompound(tag, "drop_chances");
            for (int i = 0; i < chances.length && i < EQUIPMENT_DROP_CHANCE_KEYS.length; i++) {
                chances[i] = readChance(dropChances, EQUIPMENT_DROP_CHANCE_KEYS[i], DEFAULT_EQUIPMENT_DROP_CHANCE);
            }
            return chances;
        }

        // Legacy lists (if present)
        if (ItemUtils.hasTag(tag, "HandDropChances", Tag.TAG_LIST)) {
            ListTag list = ItemUtils.getList(tag, "HandDropChances", Tag.TAG_FLOAT);
            if (!list.isEmpty()) {
                chances[0] = readChance(list, 0, DEFAULT_EQUIPMENT_DROP_CHANCE);
                chances[1] = readChance(list, 1, DEFAULT_EQUIPMENT_DROP_CHANCE);
            }
        } else if (ItemUtils.hasTag(tag, "hand_drop_chances", Tag.TAG_LIST)) {
            ListTag list = ItemUtils.getList(tag, "hand_drop_chances", Tag.TAG_FLOAT);
            if (!list.isEmpty()) {
                chances[0] = readChance(list, 0, DEFAULT_EQUIPMENT_DROP_CHANCE);
                chances[1] = readChance(list, 1, DEFAULT_EQUIPMENT_DROP_CHANCE);
            }
        }

        if (ItemUtils.hasTag(tag, "ArmorDropChances", Tag.TAG_LIST)) {
            ListTag list = ItemUtils.getList(tag, "ArmorDropChances", Tag.TAG_FLOAT);
            if (!list.isEmpty()) {
                chances[5] = readChance(list, 0, DEFAULT_EQUIPMENT_DROP_CHANCE); // feet
                chances[4] = readChance(list, 1, DEFAULT_EQUIPMENT_DROP_CHANCE); // legs
                chances[3] = readChance(list, 2, DEFAULT_EQUIPMENT_DROP_CHANCE); // chest
                chances[2] = readChance(list, 3, DEFAULT_EQUIPMENT_DROP_CHANCE); // head
            }
        } else if (ItemUtils.hasTag(tag, "armor_drop_chances", Tag.TAG_LIST)) {
            ListTag list = ItemUtils.getList(tag, "armor_drop_chances", Tag.TAG_FLOAT);
            if (!list.isEmpty()) {
                chances[5] = readChance(list, 0, DEFAULT_EQUIPMENT_DROP_CHANCE); // feet
                chances[4] = readChance(list, 1, DEFAULT_EQUIPMENT_DROP_CHANCE); // legs
                chances[3] = readChance(list, 2, DEFAULT_EQUIPMENT_DROP_CHANCE); // chest
                chances[2] = readChance(list, 3, DEFAULT_EQUIPMENT_DROP_CHANCE); // head
            }
        }

        if (ItemUtils.hasTag(tag, "body_armor_drop_chance", Tag.TAG_FLOAT) || ItemUtils.hasTag(tag, "body_armor_drop_chance", Tag.TAG_DOUBLE)) {
            chances[6] = readChance(tag, "body_armor_drop_chance", DEFAULT_EQUIPMENT_DROP_CHANCE);
        } else if (ItemUtils.hasTag(tag, "BodyArmorDropChance", Tag.TAG_FLOAT) || ItemUtils.hasTag(tag, "BodyArmorDropChance", Tag.TAG_DOUBLE)) {
            chances[6] = readChance(tag, "BodyArmorDropChance", DEFAULT_EQUIPMENT_DROP_CHANCE);
        }

        if (ItemUtils.hasTag(tag, "saddle_drop_chance", Tag.TAG_FLOAT) || ItemUtils.hasTag(tag, "saddle_drop_chance", Tag.TAG_DOUBLE)) {
            chances[7] = readChance(tag, "saddle_drop_chance", DEFAULT_EQUIPMENT_DROP_CHANCE);
        } else if (ItemUtils.hasTag(tag, "SaddleDropChance", Tag.TAG_FLOAT) || ItemUtils.hasTag(tag, "SaddleDropChance", Tag.TAG_DOUBLE)) {
            chances[7] = readChance(tag, "SaddleDropChance", DEFAULT_EQUIPMENT_DROP_CHANCE);
        }

        return chances;
    }

    private void setEquipmentDropChances(CompoundTag tag, float[] chances) {
        if (chances == null || chances.length < 8) {
            return;
        }

        CompoundTag dropChances = new CompoundTag();
        for (int i = 0; i < 8 && i < EQUIPMENT_DROP_CHANCE_KEYS.length; i++) {
            float chance = chances[i];
            if (Float.isNaN(chance) || Float.isInfinite(chance)) {
                chance = DEFAULT_EQUIPMENT_DROP_CHANCE;
            }
            if (Math.abs(chance - DEFAULT_EQUIPMENT_DROP_CHANCE) > 1.0e-6f) {
                ItemUtils.putFloat(dropChances, EQUIPMENT_DROP_CHANCE_KEYS[i], chance);
            }
        }

        if (dropChances.isEmpty()) {
            ItemUtils.remove(tag, "drop_chances");
        } else {
            tag.put("drop_chances", dropChances);
        }

        // Remove legacy keys
        ItemUtils.remove(tag, "HandDropChances");
        ItemUtils.remove(tag, "hand_drop_chances");
        ItemUtils.remove(tag, "ArmorDropChances");
        ItemUtils.remove(tag, "armor_drop_chances");
        ItemUtils.remove(tag, "body_armor_drop_chance");
        ItemUtils.remove(tag, "BodyArmorDropChance");
        ItemUtils.remove(tag, "saddle_drop_chance");
        ItemUtils.remove(tag, "SaddleDropChance");
    }

    private static float readChance(CompoundTag tag, String key, float def) {
        if (ItemUtils.hasTag(tag, key, Tag.TAG_FLOAT)) {
            return ItemUtils.getFloat(tag, key);
        }
        if (ItemUtils.hasTag(tag, key, Tag.TAG_DOUBLE)) {
            return (float) ItemUtils.getDouble(tag, key);
        }
        if (ItemUtils.hasTag(tag, key, Tag.TAG_INT)) {
            return (float) ItemUtils.getInt(tag, key);
        }
        return def;
    }

    private static float readChance(ListTag list, int index, float def) {
        if (index < 0 || index >= list.size()) {
            return def;
        }
        Tag t = list.get(index);
        if (t == null) {
            return def;
        }
        if (t instanceof net.minecraft.nbt.FloatTag ft) {
            return ft.floatValue();
        }
        if (t instanceof net.minecraft.nbt.DoubleTag dt) {
            return (float) dt.doubleValue();
        }
        if (t instanceof net.minecraft.nbt.IntTag it) {
            return (float) it.intValue();
        }
        if (t instanceof net.minecraft.nbt.ShortTag st) {
            return (float) st.shortValue();
        }
        if (t instanceof net.minecraft.nbt.ByteTag bt) {
            return (float) bt.byteValue();
        }
        return def;
    }

    private void setEquipmentData(CompoundTag tag, ItemUtils.ContainerData data) {
        // 1.21.5+ format: store equipment in the dedicated compound. (1.21.11 uses this)
        CompoundTag equipment = new CompoundTag();
        putEquipmentSlot(equipment, "mainhand", data.stacks().get(0));
        putEquipmentSlot(equipment, "offhand", data.stacks().get(1));
        putEquipmentSlot(equipment, "head", data.stacks().get(2));
        putEquipmentSlot(equipment, "chest", data.stacks().get(3));
        putEquipmentSlot(equipment, "legs", data.stacks().get(4));
        putEquipmentSlot(equipment, "feet", data.stacks().get(5));
        putEquipmentSlot(equipment, "body", data.stacks().get(6));
        putEquipmentSlot(equipment, "saddle", data.stacks().get(7));
        if (equipment.isEmpty()) {
            ItemUtils.remove(tag, "equipment");
        } else {
            tag.put("equipment", equipment);
        }

        // Remove legacy lists to avoid confusing/ignored data on 1.21.11
        ItemUtils.remove(tag, "hand_items");
        ItemUtils.remove(tag, "HandItems");
        ItemUtils.remove(tag, "armor_items");
        ItemUtils.remove(tag, "ArmorItems");
        
        EntityType<?> type = getCurrentEntityType(currentItemStack);
        Identifier id = getEntityRegistry().getKey(type);
        String path = id.getPath();

        // Body Armor (Slot 6)
        ItemStack bodyStack = data.stacks().get(6);
        CompoundTag bodyTag = getTagOrEmpty(bodyStack);
        if (!bodyStack.isEmpty()) tag.put("body_armor_item", bodyTag);
        else ItemUtils.remove(tag, "body_armor_item");
        
        if (path.equals("wolf")) {
            if (!bodyStack.isEmpty()) tag.put("BodyArmorItem", bodyTag);
            else ItemUtils.remove(tag, "BodyArmorItem");
        } else if (path.contains("llama")) {
            if (!bodyStack.isEmpty()) tag.put("DecorItem", bodyTag);
            else ItemUtils.remove(tag, "DecorItem");
        } else if (path.equals("horse")) {
            if (!bodyStack.isEmpty()) tag.put("ArmorItem", bodyTag);
            else ItemUtils.remove(tag, "ArmorItem");
        }
        
        // Saddle (Slot 7)
        ItemStack saddleStack = data.stacks().get(7);
        if (path.equals("pig") || path.equals("strider")) {
            ItemUtils.putBoolean(tag, "Saddle", !saddleStack.isEmpty());
            ItemUtils.remove(tag, "saddle");
            ItemUtils.remove(tag, "SaddleItem");
        } else {
            if (!saddleStack.isEmpty()) {
                tag.put("saddle", getTagOrEmpty(saddleStack));
            } else {
                ItemUtils.remove(tag, "saddle");
            }
            if (!saddleStack.isEmpty()) {
                tag.put("SaddleItem", getTagOrEmpty(saddleStack));
            } else {
                ItemUtils.remove(tag, "SaddleItem");
            }
            ItemUtils.remove(tag, "Saddle");
        }
    }

    private void putEquipmentSlot(CompoundTag equipment, String slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        equipment.put(slot, getTagOrEmpty(stack));
    }
    
    private CompoundTag getTagOrEmpty(ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        return (CompoundTag) ItemUtils.saveStack(stack, getRegistryAccess());
    }

    private net.minecraft.core.HolderLookup.Provider getRegistryAccess() {
        return mc.level != null ? mc.level.registryAccess() : VanillaRegistries.createLookup();
    }

    private Registry<EntityType<?>> getEntityRegistry() {
        if (mc.level != null) {
            return mc.level.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE);
        }
        return BuiltInRegistries.ENTITY_TYPE;
    }

    private void addNameInput(int centerX, int y) {
        EditBox nameBox = new EditBox(font, centerX - 100, y, 200, 20, Component.literal("Name"));
        nameBox.setMaxLength(256);
        String currentName = "";
        Component currentNameComponent = readEntityCustomName(getEntityTag());
        if (!currentNameComponent.getString().isEmpty()) {
            currentName = ChatUtils.componentToLegacyCodes(currentNameComponent);
        }
        nameBox.setValue(currentName);
        nameBox.setResponder(val -> updateEntityTag(tag -> {
            if (val.isEmpty()) {
                ItemUtils.remove(tag, "custom_name");
                ItemUtils.remove(tag, "CustomName");
            } else {
                Component component = parseLegacyFormatting(val);
                Tag nbt = toComponentNbt(component);
                if (nbt != null) {
                    tag.put("CustomName", nbt);
                } else {
                    tag.put("CustomName", StringTag.valueOf(val));
                }
                ItemUtils.remove(tag, "custom_name");
            }
        }));
        addRenderableWidget(nameBox);
    }

}
