package fr.atesab.act.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.UUID;

import com.google.common.collect.Multimap;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atesab.act.ACTMod;
import fr.atesab.act.internalcommand.InternalCommandModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.component.Unbreakable;
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

import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;

@InternalCommandModule(name = "item")
public class ItemUtils {
    
    // Helper for NBT migration
    public static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        return getTag(stack);
    }
    
    public static CompoundTag getOrCreateTagElement(ItemStack stack, String key) {
        CompoundTag tag = getTag(stack);
        if (!tag.contains(key, 10)) {
            tag.put(key, new CompoundTag());
            setTag(stack, tag);
        }
        return tag.getCompound(key);
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
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
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

        public ExplosionInformation() {
            this(CommandUtils.getRandomElement(FireworkExplosion.Shape.values()), RANDOM.nextBoolean(), RANDOM.nextBoolean(),
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

        public ExplosionInformation(FireworkExplosion.Shape type, boolean trail, boolean flicker, int[] colors, int[] fadeColors) {
            this.type = type;
            this.trail = trail;
            this.flicker = flicker;
            this.colors = colors;
            this.fadeColors = fadeColors;
        }

        public ExplosionInformation(CompoundTag explosion) {
            this(explosion.getByte("Type"), explosion.getBoolean("Trail"), explosion.getBoolean("Flicker"),
                    explosion.getIntArray("Colors"), explosion.getIntArray("FadeColors"));
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
            tag.putByte("Type", (byte) type.getId());
            if (trail) {
                tag.putBoolean("Trail", true);
            }
            if (flicker) {
                tag.putBoolean("Flicker", true);
            }
            if (colors.length != 0) {
                tag.putIntArray("Colors", colors);
            }
            if (fadeColors.length != 0) {
                tag.putIntArray("FadeColors", fadeColors);
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
            ARMOR = new AttributeModifierBuilder(Attributes.ARMOR.value(), new AttributeModifier(ResourceLocation.withDefaultNamespace("armor"), 0, AttributeModifier.Operation.ADD_VALUE));
            ARMOR_TOUGHNESS = new AttributeModifierBuilder(Attributes.ARMOR_TOUGHNESS.value(), new AttributeModifier(ResourceLocation.withDefaultNamespace("armor_toughness"), 0, AttributeModifier.Operation.ADD_VALUE));
            KNOCKBACK_RESISTANCE = new AttributeModifierBuilder(Attributes.KNOCKBACK_RESISTANCE.value(), new AttributeModifier(ResourceLocation.withDefaultNamespace("knockback_resistance"), 0, AttributeModifier.Operation.ADD_VALUE));
            TOOL_ATTACK_DAMAGE = new AttributeModifierBuilder(Attributes.ATTACK_DAMAGE.value(), new AttributeModifier(ResourceLocation.withDefaultNamespace("attack_damage"), 0, AttributeModifier.Operation.ADD_VALUE));
            TOOL_ATTACK_SPEED = new AttributeModifierBuilder(Attributes.ATTACK_SPEED.value(), new AttributeModifier(ResourceLocation.withDefaultNamespace("attack_speed"), 0, AttributeModifier.Operation.ADD_VALUE));
            SWORD_ATTACK_DAMAGE = new AttributeModifierBuilder(Attributes.ATTACK_DAMAGE.value(), new AttributeModifier(ResourceLocation.withDefaultNamespace("attack_damage"), 0, AttributeModifier.Operation.ADD_VALUE));
            SWORD_ATTACK_SPEED = new AttributeModifierBuilder(Attributes.ATTACK_SPEED.value(), new AttributeModifier(ResourceLocation.withDefaultNamespace("attack_speed"), 0, AttributeModifier.Operation.ADD_VALUE));
        }

        private final AttributeModifier clone;
        private final Attribute attribute;

        private AttributeModifierBuilder(Attribute attribute, AttributeModifier modifier) {
            this.clone = modifier;
            this.attribute = attribute;
            BUILDERS_INTERNAL.add(this);
        }

        public ResourceLocation getId() {
            return clone.id(); // getId -> id
        }

        public String getName() {
            return clone.id().toString(); // getName -> id (ResourceLocation)
        }

        public AttributeModifier build(double val, AttributeModifier.Operation op) {
            return new AttributeModifier(clone.id(), val, op);
        }

        public String getDescriptionId() {
            return attribute.getDescriptionId();
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

    private static final Random RANDOM = ACTMod.RANDOM;
    private static final Map<String, Tuple<Long, CompoundTag>> SKIN_CACHE = new HashMap<>();
    private static final Map<String, Tuple<Long, String>> UUID_CACHE = new HashMap<>();

    private static final Character[] RANDOM_CHAR = {'X', 'Y', 'M', 'Z'};

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
            is.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        if (lore != null) {
            setLore(is, lore);
        }
        if (enchantments != null) {
            setEnchantments(Arrays.asList(enchantments), is, is.getItem().equals(Items.ENCHANTED_BOOK));
        }
        return is;
    }

    public static boolean canGive(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack it = mc.player.getInventory().getItem(i);
            if (it.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static List<AttributeData> getAttributes(ItemStack stack) {
        List<AttributeData> l = new ArrayList<>();
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
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
        DyedItemColor color = stack.get(DataComponents.DYED_COLOR);
        return color != null ? color.rgb() : 10511680;
    }

    public static String getCustomTag(ItemStack stack, String key, String defaultValue) {
        CompoundTag tag = getTag(stack);
        if (tag == null) {
            return defaultValue;
        }
        return tag.contains(key) ? tag.getString(key) : defaultValue;
    }

    public static List<Tuple<Enchantment, Integer>> getEnchantments(ItemStack stack) {
        return getEnchantments(stack, false);
    }

    public static List<Tuple<Enchantment, Integer>> getEnchantments(ItemStack stack, boolean book) {
        List<Tuple<Enchantment, Integer>> list = new ArrayList<>();
        ItemEnchantments enchantments = stack.getOrDefault(book ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
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
        net.minecraft.core.HolderLookup.Provider registryAccess = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.registryAccess() : VanillaRegistries.createLookup();
        return new ItemReader(registryAccess).readItem(code);
    }

    public static String getGiveCode(ItemStack itemStack) {
        return getGiveCode(itemStack, true);
    }

    public static String getGiveCode(ItemStack itemStack, boolean showCount) {
        net.minecraft.core.HolderLookup.Provider registryAccess = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.registryAccess() : VanillaRegistries.createLookup();
        
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        StringBuilder builder = new StringBuilder(itemId.toString());
        
        Tag tag = itemStack.save(registryAccess);
        if (tag instanceof CompoundTag ct && ct.contains("components", 10)) {
            CompoundTag components = ct.getCompound("components");
            if (!components.isEmpty()) {
                builder.append("[");
                boolean first = true;
                List<String> keys = new ArrayList<>(components.getAllKeys());
                Collections.sort(keys);
                
                for (String key : keys) {
                    if (!first) builder.append(",");
                    first = false;
                    builder.append(key).append("=");
                    builder.append(components.get(key).toString());
                }
                builder.append("]");
            }
        } else {
            // Fallback for legacy or if components are missing but custom_data exists in old format (unlikely)
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
        String uuidStr = getUUIDByNames(name).stream().findFirst().orElseThrow().b;
        CompoundTag skinTag = getSkinInformationFromUUID(uuidStr);
        
        UUID uuid = UUID.fromString(addHyphen(uuidStr));
        GameProfile profile = new GameProfile(uuid, name);
        
        if (skinTag.contains("Properties", 10)) {
            CompoundTag props = skinTag.getCompound("Properties");
            if (props.contains("textures", 9)) {
                ListTag textures = props.getList("textures", 10);
                for (int i = 0; i < textures.size(); i++) {
                    CompoundTag tex = textures.getCompound(i);
                    String value = tex.getString("Value");
                    String signature = tex.contains("Signature") ? tex.getString("Signature") : null;
                    profile.getProperties().put("textures", new Property("textures", value, signature));
                }
            }
        }
        
        is.set(DataComponents.PROFILE, new ResolvableProfile(profile));
        return is;
    }

    public static ItemStack getHead(ItemStack is, String uuid, String url, String name) {
        UUID id = UUID.fromString(addHyphen(uuid));
        GameProfile profile = new GameProfile(id, name);
        String value = Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes());
        profile.getProperties().put("textures", new Property("textures", value));
        is.set(DataComponents.PROFILE, new ResolvableProfile(profile));
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
        getUUIDByNames(names).forEach(tuple -> {
            try {
                ItemStack stack = new ItemStack(Items.PLAYER_HEAD, 1);
                String uuidStr = tuple.b;
                CompoundTag skinTag = getSkinInformationFromUUID(uuidStr);
                
                UUID uuid = UUID.fromString(addHyphen(uuidStr));
                GameProfile profile = new GameProfile(uuid, tuple.a);
                
                if (skinTag.contains("Properties", 10)) {
                    CompoundTag props = skinTag.getCompound("Properties");
                    if (props.contains("textures", 9)) {
                        ListTag textures = props.getList("textures", 10);
                        for (int i = 0; i < textures.size(); i++) {
                            CompoundTag tex = textures.getCompound(i);
                            String value = tex.getString("Value");
                            String signature = tex.contains("Signature") ? tex.getString("Signature") : null;
                            profile.getProperties().put("textures", new Property("textures", value, signature));
                        }
                    }
                }
                stack.set(DataComponents.PROFILE, new ResolvableProfile(profile));
                stacks.add(stack);
            } catch (Exception ignore) {
            }
        });
        return stacks;
    }

    public static String[] getLore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return new String[0];
        }
        return lore.lines().stream()
                .map(Component::getString)
                .toArray(String[]::new);
    }

    public static CompoundTag getOrCreateSubCompound(CompoundTag compound, String key) {
        if (compound.contains(key, 10)) {
            return compound.getCompound(key);
        }
        CompoundTag nbttagcompound = new CompoundTag();
        compound.put(key, nbttagcompound);
        return nbttagcompound;
    }

    public static PotionInformation getPotionInformation(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return new PotionInformation(OptionalInt.empty(), Potions.WATER.value(), new ArrayList<>());
        
        OptionalInt color = contents.customColor().map(OptionalInt::of).orElse(OptionalInt.empty());
        Potion potion = contents.potion().map(Holder::value).orElse(Potions.WATER.value());
        List<MobEffectInstance> effects = new ArrayList<>(contents.customEffects());
        
        return new PotionInformation(color, potion, effects);
    }

    public static CompoundTag getFireworkExplosionTag(ItemStack stack) {
        FireworkExplosion exp = stack.get(DataComponents.FIREWORK_EXPLOSION);
        if (exp != null) {
            return new ExplosionInformation(exp.shape(), exp.hasTrail(), exp.hasTwinkle(), exp.colors().toIntArray(), exp.fadeColors().toIntArray()).getTag();
        }
        return new CompoundTag();
    }

    public static void setFireworkExplosionFromTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.FIREWORK_EXPLOSION, new ExplosionInformation(tag).toFireworkExplosion());
    }

    public static CompoundTag getFireworksTag(ItemStack stack) {
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        CompoundTag tag = new CompoundTag();
        if (fireworks != null) {
            tag.putByte("Flight", (byte) fireworks.flightDuration());
            ListTag explosions = new ListTag();
            for (FireworkExplosion exp : fireworks.explosions()) {
                explosions.add(new ExplosionInformation(exp.shape(), exp.hasTrail(), exp.hasTwinkle(), exp.colors().toIntArray(), exp.fadeColors().toIntArray()).getTag());
            }
            tag.put("Explosions", explosions);
        }
        return tag;
    }

    public static void setFireworksFromTag(ItemStack stack, CompoundTag tag) {
        int flight = tag.getByte("Flight");
        ListTag explosionsTag = tag.getList("Explosions", 10);
        List<FireworkExplosion> explosions = new ArrayList<>();
        for (int i = 0; i < explosionsTag.size(); i++) {
            explosions.add(new ExplosionInformation(explosionsTag.getCompound(i)).toFireworkExplosion());
        }
        stack.set(DataComponents.FIREWORKS, new Fireworks(flight, explosions));
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
        fw.set(DataComponents.CUSTOM_NAME, Component.translatable(list.get(RANDOM.nextInt(list.size())).getDescriptionId())
                .withStyle(ChatFormatting.values()[RANDOM.nextInt(16)])
                .append(" " + CommandUtils.getRandomElement(RANDOM_CHAR) + RANDOM.nextInt(1000)));
        setLore(fw, new String[]{ChatFormatting.YELLOW + "" + ChatFormatting.ITALIC + I18n.get("cmd.act.rfw")});
        return fw;
    }

    public static CompoundTag getSkinInformationFromUUID(String uuid) throws IOException, CommandSyntaxException {
        if (SKIN_CACHE.containsKey(uuid) && SKIN_CACHE.get(uuid).a + 60000 > System.currentTimeMillis()) {
            return SKIN_CACHE.get(uuid).b.copy();
        }
        CompoundTag requestCompound = TagParser.parseTag(
                sendRequest("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.replaceAll("-", ""),
                        null, null, null));
        CompoundTag newTag = new CompoundTag();
        if (requestCompound.contains("properties", 9)) {
            ListTag properties = requestCompound.getList("properties", 10);
            ListTag textures = new ListTag();
            properties.forEach(base -> {
                CompoundTag tex = (CompoundTag) base;
                CompoundTag newTex = new CompoundTag();
                if (tex.contains("value", 8)) {
                    newTex.putString("Value", tex.getString("value"));
                }
                textures.add(newTex);
            });
            newTag.put("Properties", new CompoundTag());
            newTag.putString("Id", addHyphen(uuid.replaceAll("-", "")));
            newTag.getCompound("Properties").put("textures", textures);
        }
        SKIN_CACHE.put(uuid, new Tuple<>(System.currentTimeMillis(), newTag.copy()));
        return newTag;
    }

    public static List<Tuple<String, String>> getUUIDByNames(String... names)
            throws IOException, CommandSyntaxException {
        List<Tuple<String, String>> list = new ArrayList<>();
        String query = Arrays.stream(names).map(n -> {
                    if (UUID_CACHE.containsKey(n) && UUID_CACHE.get(n).a + 60000 > System.currentTimeMillis()) {
                        list.add(new Tuple<>(n, UUID_CACHE.get(n).b));
                        return null;
                    }
                    return '"' + n + '"';
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));
        if (!query.isEmpty()) {
            CompoundTag tag = TagParser.parseTag("{data:" + sendRequest("https://api.mojang.com/profiles/minecraft",
                    "POST", "application/json", "[" + query + "]") + "}");
            if (tag.contains("data", 9)) {
                tag.getList("data", 10).forEach(base -> {
                    CompoundTag data = (CompoundTag) base;
                    if (data.contains("id", 8) && data.contains("name", 8)) {
                        String name = data.getString("name");
                        String id = data.getString("id");
                        list.add(new Tuple<>(name, id));
                        UUID_CACHE.put(name, new Tuple<>(System.currentTimeMillis(), id));
                    }
                });
            }
        }
        return list;
    }

    public static void give(ItemStack stack) {
        give(Minecraft.getInstance(), stack);
    }

    public static void give(ItemStack stack, int slot) {
        give(Minecraft.getInstance(), stack, slot);
    }

    public static void give(List<ItemStack> stacks) {
        give(Minecraft.getInstance(), stacks);
    }

    @Deprecated
    public static void give(Minecraft mc, ItemStack stack) {
        if (mc.player != null && mc.player.isCreative()) {
            if (stack != null) {
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().items.get(i).isEmpty()) {
                        give(mc, stack, 36 + i);
                        ChatUtils.itemStack(stack);
                        return;
                    }
                }
            }
            ChatUtils.error(I18n.get("gui.act.give.fail"));
        } else {
            ChatUtils.error(I18n.get("gui.act.nocreative"));
        }
    }

    @Deprecated
    public static void give(Minecraft mc, ItemStack stack, int slot) {
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        if (mc.player.isCreative()) {
            mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(slot, stack));
            mc.player.inventoryMenu.getSlot(slot).set(stack);
        } else {
            ChatUtils.error(I18n.get("gui.act.nocreative"));
        }
    }

    @Deprecated
    public static void give(Minecraft mc, List<ItemStack> stacks) {
        if (mc.player != null && mc.player.isCreative()) {
            int i = 0, j = 0;
            ItemStack is;
            stacks:
            for (; j < stacks.size(); j++) {
                is = stacks.get(j);
                for (; i < 9; i++) {
                    if (mc.player.getInventory().items.get(i).isEmpty()) {
                        give(mc, is, 36 + i);
                        ChatUtils.itemStack(is);
                        i++;
                        continue stacks;
                    }
                }
                ChatUtils.error(I18n.get("gui.act.give.fail"));
                return;
            }
        } else {
            ChatUtils.error(I18n.get("gui.act.nocreative"));
        }
    }

    public static boolean isUnbreakable(ItemStack stack) {
        return stack.has(DataComponents.UNBREAKABLE);
    }

    private static String sendRequest(String url, String method, String contentType, String content)
            throws IOException {
        Proxy proxy = Minecraft.getInstance().getProxy();
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
            EquipmentSlotGroup group = data.getSlot() == null ? EquipmentSlotGroup.ANY : EquipmentSlotGroup.bySlot(data.getSlot());
            var holder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(data.getAttribute());
            builder.add(holder, data.getModifier(), group);
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        return stack;
    }

    public static final int LEATHER_ARMOR_BASE_COLOR = 10511680;

    public static ItemStack setColor(ItemStack stack, int color) {
        if (color == LEATHER_ARMOR_BASE_COLOR) {
            stack.remove(DataComponents.DYED_COLOR);
        } else {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, true));
        }
        return stack;
    }

    public static boolean isPotionItem(ItemStack stack) {
        return stack.getItem().equals(Items.POTION) || stack.getItem().equals(Items.SPLASH_POTION)
                || stack.getItem().equals(Items.LINGERING_POTION) || stack.getItem().equals(Items.TIPPED_ARROW);
    }

    public static boolean isLeatherArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem ai && ai.getMaterial() == ArmorMaterials.LEATHER;
    }

    public static boolean canGlobalColorIt(ItemStack stack) {
        return isPotionItem(stack) || isLeatherArmor(stack);
    }

    public static ItemStack removeColor(ItemStack stack) {
        if (isPotionItem(stack)) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null && contents.customColor().isPresent()) {
                 stack.set(DataComponents.POTION_CONTENTS, new PotionContents(contents.potion(), Optional.empty(), contents.customEffects()));
            }
        } else if (isLeatherArmor(stack)) {
            stack.remove(DataComponents.DYED_COLOR);
        }
        return stack;
    }

    public static ItemStack setGlobalColor(ItemStack stack, int color) {
        if (isPotionItem(stack)) {
            PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(contents.potion(), Optional.of(color), contents.customEffects()));
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
            DyedItemColor color = stack.get(DataComponents.DYED_COLOR);
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
        RegistryAccess registryAccess = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.registryAccess() : null;
        if (registryAccess == null) return stack;

        Registry<Enchantment> registry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);

        for (Tuple<Enchantment, Integer> t : enchantments) {
            var holder = registry.wrapAsHolder(t.a);
            mutable.set(holder, t.b);
        }
        stack.set(book ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    public static int getLightLevel(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        if (tag == null) {
            return 15;
        }

        CompoundTag blockStateTag = tag.getCompound("BlockStateTag");

        if (!blockStateTag.contains("level")) {
            return 15;
        }

        return blockStateTag.getInt("level");
    }

    public static void setLightLevel(ItemStack stack, int level) {
        CompoundTag tag = getOrCreateTag(stack);
        CompoundTag blockStateTag = tag.getCompound("BlockStateTag");
        blockStateTag.putInt("level", level);
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
            stack.remove(DataComponents.LORE);
            return stack;
        }
        List<Component> components = Arrays.stream(lore)
                .map(Component::literal)
                .collect(Collectors.toList());
        stack.set(DataComponents.LORE, new ItemLore(components));
        return stack;
    }

    public static ItemStack setPotionInformation(ItemStack stack, PotionInformation info) {
        Optional<Holder<Potion>> potion = Optional.of(BuiltInRegistries.POTION.wrapAsHolder(info.getMain()));
        Optional<Integer> color = info.getCustomColor().isPresent() ? Optional.of(info.getCustomColor().getAsInt()) : Optional.empty();
        PotionContents contents = new PotionContents(potion, color, info.getCustomEffects());
        stack.set(DataComponents.POTION_CONTENTS, contents);
        return stack;
    }

    public static ItemStack setUnbreakable(ItemStack stack, boolean value) {
        if (value) {
            stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
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

        if (!tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag blockTag = tag.getCompound("BlockEntityTag");

        if (!blockTag.contains("Items", Tag.TAG_LIST)) {
            return;
        }

        ListTag items = blockTag.getList("Items", Tag.TAG_COMPOUND);
        RegistryAccess registryAccess = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.registryAccess() : RegistryAccess.EMPTY;

        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            byte slot = item.getByte("Slot");
            if (!data.size().isIn(slot)) {
                continue;
            }

            data.stacks.set(slot, ItemStack.parse(registryAccess, item).orElse(ItemStack.EMPTY));
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

        if (!tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return air();
        }

        CompoundTag blockTag = tag.getCompound("BlockEntityTag");

        if (!blockTag.contains("RecordItem", Tag.TAG_COMPOUND)) {
            return air();
        }

        RegistryAccess registryAccess = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.registryAccess() : RegistryAccess.EMPTY;
        return ItemStack.parse(registryAccess, blockTag.getCompound("RecordItem")).orElse(ItemStack.EMPTY);
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
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
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
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks));
        } else if (b == Blocks.JUKEBOX) {
            ItemStack cd = stacks.get(0);
            if (cd.getItem() == Items.AIR) {
                return stack;
            }
            CompoundTag blockTag = getOrCreateTagElement(stack, "BlockEntityTag");
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("id", getRegistry(cd.getItem()).toString());
            itemTag.putByte("Count", (byte) cd.getCount());
            CompoundTag it = getTag(cd);
            if (it != null) {
                itemTag.put("tag", it);
            }

            blockTag.put("RecordItem", itemTag);
            setTag(stack, getTag(stack));
        }
        return stack;
    }

    public static <T> ResourceLocation getRegistry(Registry<T> registry, T obj) {
        return registry.getKey(obj);
    }

    public static ResourceLocation getRegistry(ItemLike obj) {
        if (obj instanceof Item item) {
            return getRegistry(BuiltInRegistries.ITEM, item);
        } else if (obj instanceof Block block) {
            return getRegistry(BuiltInRegistries.BLOCK, block);
        }
        throw new IllegalArgumentException("bad itemlike type: " + obj.getClass());
    }

    public static ResourceLocation getRegistry(ItemStack obj) {
        return getRegistry(obj.getItem());
    }
}
