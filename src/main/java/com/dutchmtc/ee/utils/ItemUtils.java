package com.dutchmtc.ee.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.internalcommand.InternalCommandModule;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.core.Holder;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.ItemContainerContents;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@InternalCommandModule(name = "item")
public class ItemUtils {

    public static boolean hasTag(CompoundTag tag, String key, int type) {
        return switch (type) {
            case Tag.TAG_BYTE -> tag.getByte(key).isPresent();
            case Tag.TAG_SHORT -> tag.getShort(key).isPresent();
            case Tag.TAG_INT -> tag.getInt(key).isPresent();
            case Tag.TAG_LONG -> tag.getLong(key).isPresent();
            case Tag.TAG_FLOAT -> tag.getFloat(key).isPresent();
            case Tag.TAG_DOUBLE -> tag.getDouble(key).isPresent();
            case Tag.TAG_BYTE_ARRAY -> tag.getByteArray(key).isPresent();
            case Tag.TAG_STRING -> tag.getString(key).isPresent();
            case Tag.TAG_LIST -> tag.getList(key).isPresent();
            case Tag.TAG_COMPOUND -> tag.getCompound(key).isPresent();
            case Tag.TAG_INT_ARRAY -> tag.getIntArray(key).isPresent();
            case Tag.TAG_LONG_ARRAY -> tag.getLongArray(key).isPresent();
            default -> tag.contains(key);
        };
    }

    public static CompoundTag getCompound(CompoundTag tag, String key) {
        return tag.getCompound(key).orElse(new CompoundTag());
    }

    public static CompoundTag getCompound(ListTag tag, int index) {
        if (index >= 0 && index < tag.size()) {
            Tag t = tag.get(index);
            return t instanceof CompoundTag ct ? ct : new CompoundTag();
        }
        return new CompoundTag();
    }

    public static ListTag getList(CompoundTag tag, String key, int type) {
        ListTag list = tag.getList(key).orElse(new ListTag());
        if (list.isEmpty()) {
            return list;
        }
        return list.get(0).getId() == type ? list : new ListTag();
    }

    public static String getString(CompoundTag tag, String key) {
        return tag.getString(key).orElse("");
    }

    public static int getInt(CompoundTag tag, String key) {
        return tag.getInt(key).orElse(0);
    }

    public static byte getByte(CompoundTag tag, String key) {
        return tag.getByte(key).orElse((byte) 0);
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        return tag.getBoolean(key).orElse(getByte(tag, key) != 0);
    }

    public static int[] getIntArray(CompoundTag tag, String key) {
        return tag.getIntArray(key).orElseGet(() -> new int[0]);
    }

    public static double getDouble(CompoundTag tag, String key) {
        return tag.getDouble(key).orElse(0.0D);
    }

    public static float getFloat(CompoundTag tag, String key) {
        return tag.getFloat(key).orElse(0.0F);
    }

    public static long getLong(CompoundTag tag, String key) {
        return tag.getLong(key).orElse(0L);
    }

    public static short getShort(CompoundTag tag, String key) {
        return tag.getShort(key).orElse((short) 0);
    }

    public static byte[] getByteArray(CompoundTag tag, String key) {
        return tag.getByteArray(key).orElseGet(() -> new byte[0]);
    }

    public static long[] getLongArray(CompoundTag tag, String key) {
        return tag.getLongArray(key).orElseGet(() -> new long[0]);
    }

    public static <T> T getComponent(ItemStack stack, DataComponentType<? extends T> type) {
        try {
            return stack.getComponents().get(type);
        } catch (Throwable t) {
            for (TypedDataComponent<?> typed : stack.getComponents()) {
                if (typed.type() == type) {
                    return (T) typed.value();
                }
            }
            return null;
        }
    }

    public static void putString(CompoundTag tag, String key, String value) {
        tag.putString(key, value);
    }

    public static void putInt(CompoundTag tag, String key, int value) {
        tag.putInt(key, value);
    }

    public static void putByte(CompoundTag tag, String key, byte value) {
        tag.putByte(key, value);
    }

    public static void putBoolean(CompoundTag tag, String key, boolean value) {
        tag.putBoolean(key, value);
    }

    public static void putDouble(CompoundTag tag, String key, double value) {
        tag.putDouble(key, value);
    }

    public static void putFloat(CompoundTag tag, String key, float value) {
        tag.putFloat(key, value);
    }

    public static void putLong(CompoundTag tag, String key, long value) {
        tag.putLong(key, value);
    }

    public static void putShort(CompoundTag tag, String key, short value) {
        tag.putShort(key, value);
    }

    public static void putIntArray(CompoundTag tag, String key, int[] value) {
        tag.put(key, new IntArrayTag(value));
    }

    public static void putByteArray(CompoundTag tag, String key, byte[] value) {
        tag.put(key, new ByteArrayTag(value));
    }

    public static void putLongArray(CompoundTag tag, String key, long[] value) {
        tag.put(key, new LongArrayTag(value));
    }

    public static void remove(CompoundTag tag, String key) {
        tag.remove(key);
    }

    public static Tag saveStack(ItemStack stack, net.minecraft.core.HolderLookup.Provider registryAccess) {
        return ItemStack.CODEC.encodeStart(registryAccess.createSerializationContext(NbtOps.INSTANCE), stack)
                .getOrThrow(IllegalStateException::new);
    }

    public static ItemStack parseStack(net.minecraft.core.HolderLookup.Provider registryAccess, CompoundTag tag) {
        return ItemStack.CODEC.parse(registryAccess.createSerializationContext(NbtOps.INSTANCE), tag)
                .result()
                .orElse(ItemStack.EMPTY);
    }

    public static <T> void setComponent(ItemStack stack, DataComponentType<? super T> type, @Nullable T value) {
        try {
            stack.set(type, value);
        } catch (Throwable t) {
            stack.applyComponents(DataComponentPatch.builder().set(type, value).build());
        }
    }

    /**
     * Encode a data component value to NBT using the component's own persistence codec.
     * <p>
     * This is the foundation for a generic "edit any component" GUI: encode to a {@link Tag},
     * edit the tag, then decode back to the component's value type.
     */
    public static <T> DataResult<Tag> encodeComponentToNbt(net.minecraft.core.HolderLookup.Provider registryAccess,
                                                           DataComponentType<T> type, T value) {
        Codec<T> codec = type.codec();
        return codec.encodeStart(registryAccess.createSerializationContext(NbtOps.INSTANCE), value);
    }

    /**
     * Decode a data component value from NBT using the component's own persistence codec.
     */
    public static <T> DataResult<T> decodeComponentFromNbt(net.minecraft.core.HolderLookup.Provider registryAccess,
                                                           DataComponentType<T> type, Tag tag) {
        Codec<T> codec = type.codec();
        return codec.parse(registryAccess.createSerializationContext(NbtOps.INSTANCE), tag);
    }

    // Helper for NBT migration
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = getComponent(stack, DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        setComponent(stack, DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        return getTag(stack);
    }

    public static CompoundTag getOrCreateTagElement(ItemStack stack, String key) {
        CompoundTag tag = getTag(stack);
        if (!hasTag(tag, key, 10)) {
            tag.put(key, new CompoundTag());
            setTag(stack, tag);
        }
        return getCompound(tag, key);
    }

    public static class AttributeData {
        private EquipmentSlot slot;
        private AttributeModifier modifier;
        private Attribute attribute;

        public AttributeData(EquipmentSlot slot, AttributeModifier modifier, Attribute attribute) {
            this.slot = slot;
            this.modifier = modifier;
            this.attribute = attribute;
        }

        public void setSlot(EquipmentSlot slot) {
            this.slot = slot;
        }

        public void setModifier(AttributeModifier modifier) {
            this.modifier = modifier;
        }

        public void setAttribute(Attribute attribute) {
            this.attribute = attribute;
        }

        public Attribute getAttribute() {
            return attribute;
        }

        public AttributeModifier getModifier() {
            return modifier;
        }

        public EquipmentSlot getSlot() {
            return slot;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            AttributeData that = (AttributeData) o;
            return Objects.equals(slot, that.slot) &&
                    Objects.equals(modifier, that.modifier) &&
                    Objects.equals(attribute, that.attribute);
        }

        @Override
        public int hashCode() {
            return Objects.hash(slot, modifier, attribute);
        }
    }

    public static final class ExplosionInformation implements Cloneable {
        private int[] colors;
        private int[] fadeColors;
        private boolean trail, flicker;
        private FireworkExplosion.Shape type;

        private static FireworkExplosion.Shape randomShape() {
            FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
            return shapes[RANDOM.nextInt(shapes.length)];
        }

        public ExplosionInformation() {
            this(randomShape(), RANDOM.nextBoolean(),
                    RANDOM.nextBoolean(),
                    new int[RANDOM.nextInt(6) + 1], new int[RANDOM.nextInt(7)]);
            colors = new int[RANDOM.nextInt(6 - ((trail ? 1 : 0) + (flicker ? 1 : 0))) + 1];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = DyeColor.values()[RANDOM.nextInt(DyeColor.values().length)].getFireworkColor();
            }
            for (int i = 0; i < fadeColors.length; i++) {
                fadeColors[i] = DyeColor.values()[RANDOM.nextInt(DyeColor.values().length)].getFireworkColor();
            }

        }

        public ExplosionInformation(int type, boolean trail, boolean flicker, int[] colors, int[] fadeColors) {
            this(FireworkExplosion.Shape.byId(type), trail, flicker, colors, fadeColors);
        }

        public ExplosionInformation(FireworkExplosion.Shape type, boolean trail, boolean flicker, int[] colors,
                int[] fadeColors) {
            this.type = type;
            this.trail = trail;
            this.flicker = flicker;
            this.colors = colors;
            this.fadeColors = fadeColors;
        }

        public ExplosionInformation(CompoundTag explosion) {
            this(getByte(explosion, "Type"), getBoolean(explosion, "Trail"), getBoolean(explosion, "Flicker"),
                    getIntArray(explosion, "Colors"), getIntArray(explosion, "FadeColors"));
        }

        @Override
        public ExplosionInformation clone() {
            try {
                return (ExplosionInformation) super.clone();
            } catch (CloneNotSupportedException e) {
                return new ExplosionInformation(type, trail, flicker, colors, fadeColors);
            }
        }

        public ExplosionInformation colors(int[] colors) {
            this.colors = colors;
            return this;
        }

        public ExplosionInformation fadeColors(int[] fadeColors) {
            this.fadeColors = fadeColors;
            return this;
        }

        public ExplosionInformation flicker(boolean flicker) {
            this.flicker = flicker;
            return this;
        }

        public int[] getColors() {
            return colors;
        }

        public int[] getFadeColors() {
            return fadeColors;
        }

        public CompoundTag getTag() {
            CompoundTag tag = new CompoundTag();
            putByte(tag, "Type", (byte) type.getId());
            if (trail) {
                putBoolean(tag, "Trail", true);
            }
            if (flicker) {
                putBoolean(tag, "Flicker", true);
            }
            if (colors.length != 0) {
                putIntArray(tag, "Colors", colors);
            }
            if (fadeColors.length != 0) {
                putIntArray(tag, "FadeColors", fadeColors);
            }
            return tag;
        }

        public FireworkExplosion.Shape getType() {
            return type;
        }

        public boolean isFlicker() {
            return flicker;
        }

        public boolean isTrail() {
            return trail;
        }

        public ExplosionInformation trail(boolean trail) {
            this.trail = trail;
            return this;
        }

        public ExplosionInformation type(FireworkExplosion.Shape type) {
            this.type = type;
            return this;
        }

        public FireworkExplosion toFireworkExplosion() {
            return new FireworkExplosion(type, new IntArrayList(colors), new IntArrayList(fadeColors), trail, flicker);
        }
    }

    public static final class PotionInformation {
        private OptionalInt customColor;
        private List<MobEffectInstance> customEffects;
        private Potion main;

        public PotionInformation(OptionalInt customColor, Potion main, List<MobEffectInstance> customEffects) {
            this.customColor = customColor;
            this.main = main;
            this.customEffects = customEffects;
        }

        public OptionalInt getCustomColor() {
            return customColor;
        }

        public List<MobEffectInstance> getCustomEffects() {
            return customEffects;
        }

        public Potion getMain() {
            return main;
        }

        public PotionInformation customColor(OptionalInt customColor) {
            this.customColor = customColor;
            return this;
        }

        public PotionInformation customEffects(List<MobEffectInstance> customEffects) {
            this.customEffects = customEffects;
            return this;
        }

        public PotionInformation main(Potion main) {
            this.main = main;
            return this;
        }

    }

    public static class AttributeModifierBuilder {
        private static final List<AttributeModifierBuilder> BUILDERS_INTERNAL = new ArrayList<>();
        public static final List<AttributeModifierBuilder> BUILDERS = Collections.unmodifiableList(BUILDERS_INTERNAL);
        public static final AttributeModifierBuilder ARMOR;
        public static final AttributeModifierBuilder ARMOR_TOUGHNESS;
        public static final AttributeModifierBuilder KNOCKBACK_RESISTANCE;
        public static final AttributeModifierBuilder TOOL_ATTACK_DAMAGE;
        public static final AttributeModifierBuilder TOOL_ATTACK_SPEED;
        public static final AttributeModifierBuilder SWORD_ATTACK_DAMAGE;
        public static final AttributeModifierBuilder SWORD_ATTACK_SPEED;

        static {
            ARMOR = new AttributeModifierBuilder(Attributes.ARMOR.value(), new AttributeModifier(
                    Identifier.withDefaultNamespace("armor"), 0, AttributeModifier.Operation.ADD_VALUE));
            ARMOR_TOUGHNESS = new AttributeModifierBuilder(Attributes.ARMOR_TOUGHNESS.value(), new AttributeModifier(
                    Identifier.withDefaultNamespace("armor_toughness"), 0, AttributeModifier.Operation.ADD_VALUE));
            KNOCKBACK_RESISTANCE = new AttributeModifierBuilder(Attributes.KNOCKBACK_RESISTANCE.value(),
                    new AttributeModifier(Identifier.withDefaultNamespace("knockback_resistance"), 0,
                            AttributeModifier.Operation.ADD_VALUE));
            TOOL_ATTACK_DAMAGE = new AttributeModifierBuilder(Attributes.ATTACK_DAMAGE.value(), new AttributeModifier(
                    Identifier.withDefaultNamespace("attack_damage"), 0, AttributeModifier.Operation.ADD_VALUE));
            TOOL_ATTACK_SPEED = new AttributeModifierBuilder(Attributes.ATTACK_SPEED.value(), new AttributeModifier(
                    Identifier.withDefaultNamespace("attack_speed"), 0, AttributeModifier.Operation.ADD_VALUE));
            SWORD_ATTACK_DAMAGE = new AttributeModifierBuilder(Attributes.ATTACK_DAMAGE.value(), new AttributeModifier(
                    Identifier.withDefaultNamespace("attack_damage"), 0, AttributeModifier.Operation.ADD_VALUE));
            SWORD_ATTACK_SPEED = new AttributeModifierBuilder(Attributes.ATTACK_SPEED.value(), new AttributeModifier(
                    Identifier.withDefaultNamespace("attack_speed"), 0, AttributeModifier.Operation.ADD_VALUE));
        }

        private final AttributeModifier clone;
        private final Attribute attribute;

        private AttributeModifierBuilder(Attribute attribute, AttributeModifier modifier) {
            this.clone = modifier;
            this.attribute = attribute;
            BUILDERS_INTERNAL.add(this);
        }

        public Identifier getId() {
            return clone.id(); // getId -> id
        }

        public String getName() {
            return clone.id().toString(); // getName -> id (ResourceLocation)
        }

        public AttributeModifier build(double val, AttributeModifier.Operation op) {
            return new AttributeModifier(clone.id(), val, op);
        }

        public String getDescriptionId() {
            Identifier id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
            return "attribute." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        }

        public AttributeData buildData(EquipmentSlot slot, double val, AttributeModifier.Operation op) {
            return new AttributeData(slot, build(val, op), attribute);
        }
    }

    public static final String NBT_CHILD_DISPLAY = "display";
    public static final String NBT_CHILD_ENCHANTMENTS = "Enchantments";
    public static final String NBT_CHILD_BOOK_ENCHANTMENTS = "StoredEnchantments";
    public static final String NBT_CHILD_EXPLOSIONS = "Explosions";
    public static final String NBT_CHILD_FIREWORKS = "Fireworks";
    public static final String NBT_CHILD_ATTRIBUTE_MODIFIER = "AttributeModifiers";

    private static final Random RANDOM = EEMod.RANDOM;
    private static final Map<String, Tuple<Long, CompoundTag>> SKIN_CACHE = new HashMap<>();
    private static final Map<String, Tuple<Long, String>> UUID_CACHE = new HashMap<>();

    private static final Character[] RANDOM_CHAR = { 'X', 'Y', 'M', 'Z' };

    private static String addHyphen(String uuid) {
        if (uuid.length() < 20) {
            return uuid;
        }
        return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-"
                + uuid.substring(16, 20) + "-" + uuid.substring(20);
    }

    public static ItemStack buildStack(Block block, int count, @Nullable String name, @Nullable String[] lore,
            @Nullable Tuple<Enchantment, Integer>[] enchantments) {
        return buildStack(block.asItem(), count, name, lore, enchantments);
    }

    public static ItemStack buildStack(Item item, int count, @Nullable String name, @Nullable String[] lore,
            @Nullable Tuple<Enchantment, Integer>[] enchantments) {
        ItemStack is = new ItemStack(item, count);
        if (name != null) {
            setComponent(is, DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        if (lore != null) {
            setLore(is, lore);
        }
        if (enchantments != null) {
            setEnchantments(Arrays.asList(enchantments), is, is.getItem().equals(Items.ENCHANTED_BOOK));
        }
        return is;
    }

    public static List<AttributeData> getAttributes(ItemStack stack) {
        List<AttributeData> l = new ArrayList<>();
        ItemAttributeModifiers modifiers = getComponent(stack, DataComponents.ATTRIBUTE_MODIFIERS);
        modifiers = modifiers != null ? modifiers : ItemAttributeModifiers.EMPTY;
        for (var entry : modifiers.modifiers()) {
            EquipmentSlot slot = null;
            for (EquipmentSlot s : EquipmentSlot.values()) {
                if (EquipmentSlotGroup.bySlot(s).equals(entry.slot())) {
                    slot = s;
                    break;
                }
            }
            l.add(new AttributeData(slot, entry.modifier(), entry.attribute().value()));
        }
        return l;
    }

    public static int getColor(ItemStack stack) {
        DyedItemColor color = getComponent(stack, DataComponents.DYED_COLOR);
        return color != null ? color.rgb() : 10511680;
    }

    public static String getCustomTag(ItemStack stack, String key, String defaultValue) {
        CompoundTag tag = getTag(stack);
        if (tag == null) {
            return defaultValue;
        }
        return hasTag(tag, key, 8) ? getString(tag, key) : defaultValue;
    }

    public static List<Tuple<Enchantment, Integer>> getEnchantments(ItemStack stack) {
        return getEnchantments(stack, false);
    }

    public static List<Tuple<Enchantment, Integer>> getEnchantments(ItemStack stack, boolean book) {
        List<Tuple<Enchantment, Integer>> list = new ArrayList<>();
        ItemEnchantments enchantments = getComponent(stack,
                book ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS);
        enchantments = enchantments != null ? enchantments : ItemEnchantments.EMPTY;
        for (var entry : enchantments.entrySet()) {
            list.add(new Tuple<>(entry.getKey().value(), entry.getIntValue()));
        }
        return list;
    }

    public static ExplosionInformation getExplosionInformation(@Nullable CompoundTag explosion) {
        return explosion == null ? new ExplosionInformation() : new ExplosionInformation(explosion);
    }

    public static ItemStack getFromGiveCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        net.minecraft.core.HolderLookup.Provider registryAccess = VanillaRegistries.createLookup();
        return new ItemReader(registryAccess).readItem(code);
    }

    public static String getGiveCode(ItemStack itemStack) {
        return getGiveCode(itemStack, true);
    }

    public static String getGiveCode(ItemStack itemStack, boolean showCount) {
        if (itemStack.isEmpty()) {
            return "";
        }
        net.minecraft.core.HolderLookup.Provider registryAccess = VanillaRegistries.createLookup();

        Identifier itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        StringBuilder builder = new StringBuilder(itemId.toString());

        Tag tag = saveStack(itemStack, registryAccess);
        if (tag instanceof CompoundTag ct && ct.getCompound("components").isPresent()) {
            CompoundTag components = ct.getCompound("components").orElseThrow();
            if (!components.isEmpty()) {
                builder.append("[");
                boolean first = true;
                java.util.Map<String, Tag> entries = new java.util.HashMap<>();
                components.forEach(entries::put);

                List<String> keys = new ArrayList<>(components.keySet());
                Collections.sort(keys);

                for (String key : keys) {
                    if (!first)
                        builder.append(",");
                    first = false;
                    builder.append(key).append("=");
                    Tag v = entries.get(key);
                    if (v != null) {
                        builder.append(v);
                    }
                }
                builder.append("]");
            }
        } else {
            // Fallback for legacy or if components are missing but custom_data exists in
            // old format (unlikely)
            boolean noTag = getTag(itemStack) != null && !getTag(itemStack).isEmpty();
            if (noTag) {
                builder.append(getTag(itemStack).toString());
            }
        }

        if (showCount && itemStack.getCount() != 1) {
            builder.append(" ").append(itemStack.getCount());
        }

        return builder.toString();
    }

    public static ItemStack getHead(ItemStack is, String name)
            throws IOException, CommandSyntaxException, NoSuchElementException {
        // Modern versions can resolve the skin from the profile name alone (same as:
        // /give @s minecraft:player_head[profile=<name>])
        setComponent(is, DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
        return is;
    }

    public static ItemStack getHead(ItemStack is, String uuid, String url, String name) {
        UUID id = null;
        if (uuid != null && !uuid.isEmpty()) {
            try {
                id = UUID.fromString(addHyphen(uuid));
            } catch (IllegalArgumentException e) {
                // Be tolerant: some callers may provide non-UUID strings (e.g. legacy UI
                // inputs).
                id = UUID.nameUUIDFromBytes(("ACT:uuid:" + uuid).getBytes(StandardCharsets.UTF_8));
            }
        }
        String value = Base64.getEncoder()
                .encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes());
        // authlib PropertyMap is immutable, so build properties before constructing it
        com.google.common.collect.Multimap<String, Property> props = com.google.common.collect.HashMultimap.create();
        props.put("textures", new Property("textures", value));
        GameProfile profile = new GameProfile(id, name, new com.mojang.authlib.properties.PropertyMap(props));
        setComponent(is, DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return is;
    }

    public static ItemStack getHeadFromUrl(ItemStack is, String url, @Nullable String name) {
        String value = Base64.getEncoder()
                .encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes());
        com.google.common.collect.Multimap<String, Property> props = com.google.common.collect.HashMultimap.create();
        props.put("textures", new Property("textures", value));
        // Minecraft requires a non-null profile id for resolved profiles; generate a
        // stable id from the URL.
        UUID id = UUID.nameUUIDFromBytes(("ACT:url:" + url).getBytes(StandardCharsets.UTF_8));
        if (name == null) {
            name = "";
        }
        GameProfile profile = new GameProfile(id, name, new com.mojang.authlib.properties.PropertyMap(props));
        setComponent(is, DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return is;
    }

    public static ItemStack getHead(String name) throws IOException, CommandSyntaxException, NoSuchElementException {
        return getHead(new ItemStack(Items.PLAYER_HEAD, 1), name);
    }

    public static ItemStack getHead(String uuid, String url, String name) {
        return getHead(new ItemStack(Items.PLAYER_HEAD, 1), uuid, url, name);
    }

    public static List<ItemStack> getHeads(Collection<Player> players)
            throws IOException, CommandSyntaxException, NoSuchElementException {
        return getHeads(players.stream().map(Player::getScoreboardName).toArray(String[]::new));
    }

    public static List<ItemStack> getHeads(String... names)
            throws IOException, CommandSyntaxException, NoSuchElementException {
        List<ItemStack> stacks = new ArrayList<>();
        for (String name : names) {
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD, 1);
            setComponent(stack, DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
            stacks.add(stack);
        }
        return stacks;
    }

    public static String[] getLore(ItemStack stack) {
        ItemLore lore = getComponent(stack, DataComponents.LORE);
        if (lore == null) {
            return new String[0];
        }
        return lore.lines().stream()
                .map(Component::getString)
                .toArray(String[]::new);
    }

    public static CompoundTag getOrCreateSubCompound(CompoundTag compound, String key) {
        if (hasTag(compound, key, 10)) {
            return getCompound(compound, key);
        }
        CompoundTag nbttagcompound = new CompoundTag();
        compound.put(key, nbttagcompound);
        return nbttagcompound;
    }

    public static PotionInformation getPotionInformation(ItemStack stack) {
        PotionContents contents = getComponent(stack, DataComponents.POTION_CONTENTS);
        if (contents == null)
            return new PotionInformation(OptionalInt.empty(), Potions.WATER.value(), new ArrayList<>());

        OptionalInt color = contents.customColor().map(OptionalInt::of).orElse(OptionalInt.empty());
        Potion potion = contents.potion().map(Holder::value).orElse(Potions.WATER.value());
        List<MobEffectInstance> effects = new ArrayList<>(contents.customEffects());

        return new PotionInformation(color, potion, effects);
    }

    public static CompoundTag getFireworkExplosionTag(ItemStack stack) {
        FireworkExplosion exp = getComponent(stack, DataComponents.FIREWORK_EXPLOSION);
        if (exp != null) {
            return new ExplosionInformation(exp.shape(), exp.hasTrail(), exp.hasTwinkle(), exp.colors().toIntArray(),
                    exp.fadeColors().toIntArray()).getTag();
        }
        return new CompoundTag();
    }

    public static void setFireworkExplosionFromTag(ItemStack stack, CompoundTag tag) {
        setComponent(stack, DataComponents.FIREWORK_EXPLOSION, new ExplosionInformation(tag).toFireworkExplosion());
    }

    public static CompoundTag getFireworksTag(ItemStack stack) {
        Fireworks fireworks = getComponent(stack, DataComponents.FIREWORKS);
        CompoundTag tag = new CompoundTag();
        if (fireworks != null) {
            putByte(tag, "Flight", (byte) fireworks.flightDuration());
            ListTag explosions = new ListTag();
            for (FireworkExplosion exp : fireworks.explosions()) {
                explosions.add(new ExplosionInformation(exp.shape(), exp.hasTrail(), exp.hasTwinkle(),
                        exp.colors().toIntArray(), exp.fadeColors().toIntArray()).getTag());
            }
            tag.put("Explosions", explosions);
        }
        return tag;
    }

    public static void setFireworksFromTag(ItemStack stack, CompoundTag tag) {
        int flight = getByte(tag, "Flight");
        ListTag explosionsTag = getList(tag, "Explosions", 10);
        List<FireworkExplosion> explosions = new ArrayList<>();
        for (int i = 0; i < explosionsTag.size(); i++) {
            explosions.add(new ExplosionInformation(getCompound(explosionsTag, i)).toFireworkExplosion());
        }
        setComponent(stack, DataComponents.FIREWORKS, new Fireworks(flight, explosions));
    }

    public static ItemStack getRandomFireworks() {
        CompoundTag fwt = new CompoundTag();
        int flight = RANDOM.nextInt(3);
        fwt.putInt("Flight", flight + 1);
        ListTag explosions = new ListTag();
        int exp = RANDOM.nextInt(7 - flight) + 1;
        for (int i = 0; i < exp; i++) {
            explosions.add(getExplosionInformation(null).getTag());
        }
        fwt.put(NBT_CHILD_EXPLOSIONS, explosions);
        ItemStack fw = new ItemStack(Items.FIREWORK_ROCKET);
        setFireworksFromTag(fw, fwt);
        List<EntityType<?>> list = BuiltInRegistries.ENTITY_TYPE.stream().filter(EntityType::canSummon).toList();
        EntityType<?> entityType = list.get(RANDOM.nextInt(list.size()));
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        String desc = "entity." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        setComponent(fw, DataComponents.CUSTOM_NAME, Component.translatable(desc)
                .withStyle(ChatFormatting.values()[RANDOM.nextInt(16)])
                .append(Component.literal(" " + RANDOM_CHAR[RANDOM.nextInt(RANDOM_CHAR.length)] + RANDOM.nextInt(1000))));
        setLoreComponents(fw, List.of(Component.translatable("cmd.ee.rfw")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)));
        return fw;
    }

    public static CompoundTag getSkinInformationFromUUID(String uuid) throws IOException, CommandSyntaxException {
        if (SKIN_CACHE.containsKey(uuid) && SKIN_CACHE.get(uuid).a + 60000 > System.currentTimeMillis()) {
            return SKIN_CACHE.get(uuid).b.copy();
        }
        CompoundTag requestCompound = TagParser.parseCompoundFully(
                sendRequest("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.replaceAll("-", ""),
                        null, null, null));
        CompoundTag newTag = new CompoundTag();
        if (hasTag(requestCompound, "properties", 9)) {
            ListTag properties = getList(requestCompound, "properties", 10);
            ListTag textures = new ListTag();
            properties.forEach(base -> {
                CompoundTag tex = (CompoundTag) base;
                CompoundTag newTex = new CompoundTag();
                if (hasTag(tex, "value", 8)) {
                    newTex.putString("Value", getString(tex, "value"));
                }
                textures.add(newTex);
            });
            newTag.put("Properties", new CompoundTag());
            putString(newTag, "Id", addHyphen(uuid.replaceAll("-", "")));
            getCompound(newTag, "Properties").put("textures", textures);
        }
        SKIN_CACHE.put(uuid, new Tuple<>(System.currentTimeMillis(), newTag.copy()));
        return newTag;
    }

    public static List<Tuple<String, String>> getUUIDByNames(String... names)
            throws IOException, CommandSyntaxException {
        List<Tuple<String, String>> list = new ArrayList<>();
        List<String> toQuery = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String n : names) {
            if (UUID_CACHE.containsKey(n) && UUID_CACHE.get(n).a + 60000 > now) {
                list.add(new Tuple<>(n, UUID_CACHE.get(n).b));
            } else {
                toQuery.add(n);
            }
        }

        String query = toQuery.stream()
                .map(n -> '"' + n + '"')
                .collect(Collectors.joining(","));
        if (!query.isEmpty()) {
            CompoundTag tag = TagParser
                    .parseCompoundFully("{data:" + sendRequest("https://api.mojang.com/profiles/minecraft",
                            "POST", "application/json", "[" + query + "]") + "}");
            if (hasTag(tag, "data", 9)) {
                getList(tag, "data", 10).forEach(base -> {
                    CompoundTag data = (CompoundTag) base;
                    if (hasTag(data, "id", 8) && hasTag(data, "name", 8)) {
                        String name = getString(data, "name");
                        String id = getString(data, "id");
                        list.add(new Tuple<>(name, id));
                        UUID_CACHE.put(name, new Tuple<>(System.currentTimeMillis(), id));
                    }
                });
            }
        }
        return list;
    }


    public static boolean isUnbreakable(ItemStack stack) {
        return stack.has(DataComponents.UNBREAKABLE);
    }

    private static String sendRequest(String url, String method, String contentType, String content)
            throws IOException {
        Proxy proxy = Proxy.NO_PROXY;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(proxy);
        if (method != null) {
            connection.setRequestMethod(method);
        }
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        }
        connection.setRequestProperty("Content-Language", "en-US");
        connection.setDoOutput(true);
        if (content != null) {
            connection.setRequestProperty("Content-Length", "" + content.getBytes().length);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
                output.writeBytes(content);
                output.flush();
            }
        }
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                buffer.append("\n").append(line);
            }
            return buffer.length() > 0 ? buffer.substring(1) : "";
        }
    }

    public static ItemStack setAttributes(List<AttributeData> attributes, ItemStack stack) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (AttributeData data : attributes) {
            EquipmentSlotGroup group = data.getSlot() == null ? EquipmentSlotGroup.ANY
                    : EquipmentSlotGroup.bySlot(data.getSlot());
            var holder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(data.getAttribute());
            builder.add(holder, data.getModifier(), group);
        }
        setComponent(stack, DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        return stack;
    }

    public static final int LEATHER_ARMOR_BASE_COLOR = 10511680;

    public static ItemStack setColor(ItemStack stack, int color) {
        if (color == LEATHER_ARMOR_BASE_COLOR) {
            stack.remove(DataComponents.DYED_COLOR);
        } else {
            setComponent(stack, DataComponents.DYED_COLOR, new DyedItemColor(color));
        }
        return stack;
    }

    public static boolean isPotionItem(ItemStack stack) {
        return stack.getItem().equals(Items.POTION) || stack.getItem().equals(Items.SPLASH_POTION)
                || stack.getItem().equals(Items.LINGERING_POTION) || stack.getItem().equals(Items.TIPPED_ARROW);
    }

    public static boolean isLeatherArmor(ItemStack stack) {
        return stack.is(net.minecraft.tags.ItemTags.DYEABLE);
    }

    public static boolean canGlobalColorIt(ItemStack stack) {
        return isPotionItem(stack) || isLeatherArmor(stack);
    }

    public static ItemStack removeColor(ItemStack stack) {
        if (isPotionItem(stack)) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null && contents.customColor().isPresent()) {
                setComponent(stack, DataComponents.POTION_CONTENTS, new PotionContents(contents.potion(),
                        Optional.empty(), contents.customEffects(), contents.customName()));
            }
        } else if (isLeatherArmor(stack)) {
            stack.remove(DataComponents.DYED_COLOR);
        }
        return stack;
    }

    public static ItemStack setGlobalColor(ItemStack stack, int color) {
        if (isPotionItem(stack)) {
            PotionContents contents = getComponent(stack, DataComponents.POTION_CONTENTS);
            contents = contents != null ? contents : PotionContents.EMPTY;
            setComponent(stack, DataComponents.POTION_CONTENTS, new PotionContents(contents.potion(),
                    Optional.of(color), contents.customEffects(), contents.customName()));
        } else if (isLeatherArmor(stack)) {
            setColor(stack, color);
        }

        return stack;
    }

    public static OptionalInt getDefaultGlobalColor(ItemStack stack) {
        if (isLeatherArmor(stack)) {
            return OptionalInt.of(LEATHER_ARMOR_BASE_COLOR);
        }
        return OptionalInt.empty();
    }

    public static OptionalInt getGlobalColor(ItemStack stack) {
        if (isPotionItem(stack)) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null && contents.customColor().isPresent()) {
                return OptionalInt.of(contents.customColor().get());
            }
        } else if (isLeatherArmor(stack)) {
            DyedItemColor color = getComponent(stack, DataComponents.DYED_COLOR);
            if (color != null) {
                return OptionalInt.of(color.rgb());
            }
        }

        return OptionalInt.empty();
    }

    public static ItemStack setCustomTag(ItemStack stack, String key, String value) {
        CompoundTag tag = getTag(stack);
        if (tag == null) {
            tag = new CompoundTag();
        }
        tag.putString(key, value);
        setTag(stack, tag);
        return stack;
    }

    public static ItemStack setEnchantments(List<Tuple<Enchantment, Integer>> enchantments, ItemStack stack) {
        return setEnchantments(enchantments, stack, false);
    }

    public static ItemStack setEnchantments(List<Tuple<Enchantment, Integer>> enchantments, ItemStack stack,
            boolean book) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Registry<Enchantment> registry = (Registry) BuiltInRegistries.REGISTRY.getValue(Registries.ENCHANTMENT.identifier());
        if (registry == null) {
            return stack;
        }
        for (Tuple<Enchantment, Integer> t : enchantments) {
            var holder = registry.wrapAsHolder(t.a);
            mutable.set(holder, t.b);
        }
        setComponent(stack, book ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS,
                mutable.toImmutable());
        return stack;
    }

    public static int getLightLevel(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        if (tag == null) {
            return 15;
        }

        CompoundTag blockStateTag = getCompound(tag, "BlockStateTag");

        if (!blockStateTag.contains("level")) {
            return 15;
        }

        return getInt(blockStateTag, "level");
    }

    public static void setLightLevel(ItemStack stack, int level) {
        CompoundTag tag = getOrCreateTag(stack);
        CompoundTag blockStateTag = getCompound(tag, "BlockStateTag");
        putInt(blockStateTag, "level", level);
        tag.put("BlockStateTag", blockStateTag);
        setTag(stack, tag);
    }

    public static ItemStack setItem(Item item, ItemStack stack) {
        ItemStack is = new ItemStack(item, stack.getCount());
        is.applyComponents(stack.getComponentsPatch());
        return is;
    }

    public static ItemStack setLore(ItemStack stack, String[] lore) {
        if (lore == null || lore.length == 0) {
            return setLoreComponents(stack, List.of());
        }
        List<Component> components = Arrays.stream(lore)
                .map(Component::literal)
                .collect(Collectors.toList());
        return setLoreComponents(stack, components);
    }

    public static ItemStack setLoreComponents(ItemStack stack, List<Component> lore) {
        if (lore == null || lore.isEmpty()) {
            stack.remove(DataComponents.LORE);
            return stack;
        }
        setComponent(stack, DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    public static ItemStack setPotionInformation(ItemStack stack, PotionInformation info) {
        Optional<Holder<Potion>> potion = Optional.of(BuiltInRegistries.POTION.wrapAsHolder(info.getMain()));
        Optional<Integer> color = info.getCustomColor().isPresent() ? Optional.of(info.getCustomColor().getAsInt())
                : Optional.empty();
        PotionContents contents = new PotionContents(potion, color, info.getCustomEffects(), Optional.empty());
        setComponent(stack, DataComponents.POTION_CONTENTS, contents);
        return stack;
    }

    public static ItemStack setUnbreakable(ItemStack stack, boolean value) {
        if (value) {
            setComponent(stack, DataComponents.UNBREAKABLE, Unit.INSTANCE);
        } else {
            stack.remove(DataComponents.UNBREAKABLE);
        }
        return stack;
    }

    public record ContainerSize(int sizeX, int sizeY) {
        public int indexOf(int x, int y) {
            return y * sizeX() + x;
        }

        public boolean isIn(int slot) {
            return slot >= 0 && slot < sizeX() * sizeY();
        }
    }

    public record ContainerData(ContainerSize size, NonNullList<ItemStack> stacks) {
        public ContainerData copy() {
            NonNullList<ItemStack> stackCopy = NonNullList.withSize(stacks.size(), air());
            for (int i = 0; i < stacks.size(); i++) {
                stackCopy.set(i, stacks.get(i).copy());
            }
            return new ContainerData(size(), stackCopy);
        }
    }

    public static boolean isContainer(ItemStack stack) {
        return getContainerSize(stack) != null;
    }

    public static ContainerSize getContainerSize(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) {
            return null;
        }

        Block b = bi.getBlock();
        if (b instanceof ShulkerBoxBlock || b == Blocks.BARREL || b == Blocks.TRAPPED_CHEST || b == Blocks.CHEST) {
            return new ContainerSize(9, 3);
        }
        if (b == Blocks.DISPENSER || b == Blocks.DROPPER) {
            return new ContainerSize(3, 3);
        }
        if (b instanceof AbstractFurnaceBlock) {
            return new ContainerSize(1, 2);
        }
        if (b == Blocks.JUKEBOX) {
            return new ContainerSize(1, 1);
        }
        if (b == Blocks.HOPPER) {
            return new ContainerSize(5, 1);
        }
        return null;
    }

    public static void loadTagItems(ItemStack stack, ContainerData data) {
        CompoundTag tag = getTag(stack);
        if (tag == null) {
            return;
        }

        if (!hasTag(tag, "BlockEntityTag", Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag blockTag = getCompound(tag, "BlockEntityTag");

        if (!hasTag(blockTag, "Items", Tag.TAG_LIST)) {
            return;
        }

        ListTag items = getList(blockTag, "Items", Tag.TAG_COMPOUND);
        net.minecraft.core.HolderLookup.Provider registryAccess = VanillaRegistries.createLookup();

        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = getCompound(items, i);
            byte slot = getByte(item, "Slot");
            if (!data.size().isIn(slot)) {
                continue;
            }

            data.stacks.set(slot, parseStack(registryAccess, item));
        }
    }

    public static ItemStack air() {
        return new ItemStack(Items.AIR);
    }

    public static ItemStack loadCD(ItemStack jukebox) {
        CompoundTag tag = getTag(jukebox);
        if (tag == null) {
            return air();
        }

        if (!hasTag(tag, "BlockEntityTag", Tag.TAG_COMPOUND)) {
            return air();
        }

        CompoundTag blockTag = getCompound(tag, "BlockEntityTag");

        if (!hasTag(blockTag, "RecordItem", Tag.TAG_COMPOUND)) {
            return air();
        }

        net.minecraft.core.HolderLookup.Provider registryAccess = VanillaRegistries.createLookup();
        return parseStack(registryAccess, getCompound(blockTag, "RecordItem"));
    }

    public static ContainerData fetchContainerData(ItemStack stack) {
        ContainerSize size = getContainerSize(stack);

        if (!(size != null && stack.getItem() instanceof BlockItem bi)) {
            return null;
        }

        NonNullList<ItemStack> stacks = NonNullList.withSize(size.sizeX() * size.sizeY(), air());

        Block b = bi.getBlock();
        ContainerData data = new ContainerData(size, stacks);
        if (b instanceof ShulkerBoxBlock || b == Blocks.BARREL || b == Blocks.TRAPPED_CHEST || b == Blocks.CHEST
                || b == Blocks.DISPENSER || b == Blocks.DROPPER || b instanceof AbstractFurnaceBlock
                || b == Blocks.HOPPER) {
            ItemContainerContents contents = getComponent(stack, DataComponents.CONTAINER);
            if (contents != null) {
                contents.copyInto(stacks);
            }
        } else if (b == Blocks.JUKEBOX) {
            data.stacks.set(0, loadCD(stack));
        } else {
            return null;
        }

        return data;
    }

    public static ItemStack setContainerData(ItemStack stack, ContainerData data) {
        return setContainerData(stack, data.stacks());
    }

    public static ItemStack setContainerData(ItemStack stack, NonNullList<ItemStack> stacks) {
        if (!(stack.getItem() instanceof BlockItem bi)) {
            return stack;
        }

        Block b = bi.getBlock();
        if (b instanceof ShulkerBoxBlock || b == Blocks.BARREL || b == Blocks.TRAPPED_CHEST || b == Blocks.CHEST
                || b == Blocks.DISPENSER || b == Blocks.DROPPER || b == Blocks.HOPPER
                || b instanceof AbstractFurnaceBlock) {
            setComponent(stack, DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks));
        } else if (b == Blocks.JUKEBOX) {
            ItemStack cd = stacks.get(0);
            if (cd.getItem() == Items.AIR) {
                return stack;
            }
            CompoundTag blockTag = getOrCreateTagElement(stack, "BlockEntityTag");
            CompoundTag itemTag = new CompoundTag();
            putString(itemTag, "id", getRegistry(cd.getItem()).toString());
            putByte(itemTag, "Count", (byte) cd.getCount());
            CompoundTag it = getTag(cd);
            if (it != null) {
                itemTag.put("tag", it);
            }

            blockTag.put("RecordItem", itemTag);
            setTag(stack, getTag(stack));
        }
        return stack;
    }

    public static <T> Identifier getRegistry(Registry<T> registry, T obj) {
        return registry.getKey(obj);
    }

    public static Identifier getRegistry(ItemLike obj) {
        if (obj instanceof Item item) {
            return getRegistry(BuiltInRegistries.ITEM, item);
        } else if (obj instanceof Block block) {
            return getRegistry(BuiltInRegistries.BLOCK, block);
        }
        throw new IllegalArgumentException("bad itemlike type: " + obj.getClass());
    }

    public static Identifier getRegistry(ItemStack obj) {
        return getRegistry(obj.getItem());
    }
}
