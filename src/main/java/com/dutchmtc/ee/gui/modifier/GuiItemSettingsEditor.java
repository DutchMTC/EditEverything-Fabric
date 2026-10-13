package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTModifier;
import com.dutchmtc.ee.gui.selector.*;
import com.dutchmtc.ee.utils.ItemUtils;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.*;
import net.minecraft.util.Unit;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;

import java.util.*;
import java.util.function.*;

/** Friendly controls for common item components. Only Apply commits this draft. */
public final class GuiItemSettingsEditor extends EESettingsScreen<ItemStack> {
    private ItemStack draft;
    private final ItemStack original;
    private Consumable consumeDraft;
    private FoodProperties foodDraft;
    private static final List<DataComponentType<?>> GENERAL =
            List.of(
                    DataComponents.MAX_STACK_SIZE,
                    DataComponents.MAX_DAMAGE,
                    DataComponents.DAMAGE,
                    DataComponents.UNBREAKABLE,
                    DataComponents.REPAIR_COST);
    private static final List<DataComponentType<?>> APPEARANCE =
            List.of(
                    DataComponents.ITEM_MODEL,
                    DataComponents.RARITY,
                    DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
                    DataComponents.TOOLTIP_STYLE);
    private static final List<DataComponentType<?>> USE =
            List.of(
                    DataComponents.BREAK_SOUND,
                    DataComponents.ATTACK_ANIMATION,
                    DataComponents.INTERACT_ANIMATION,
                    DataComponents.USE_EFFECTS,
                    DataComponents.USE_COOLDOWN);
    private static final List<DataComponentType<?>> FOOD =
            List.of(DataComponents.USE_REMAINDER, DataComponents.CONSUMABLE, DataComponents.FOOD);

    public GuiItemSettingsEditor(Screen parent, ItemStack stack, Consumer<ItemStack> setter) {
        super(parent, "Item settings", setter, "General", "Appearance", "Food", "Use", "Advanced");
        original = stack.copy();
        draft = stack.copy();
        consumeDraft = stack.getOrDefault(DataComponents.CONSUMABLE, Consumable.builder().build());
        foodDraft = stack.getOrDefault(DataComponents.FOOD, new FoodProperties(4, 2.4f, false));
    }

    @Override
    public boolean isModified() {
        return !ItemStack.matches(draft, original) || !errors.isEmpty();
    }

    @Override
    protected ItemStack preview() {
        return draft;
    }

    @Override
    protected ItemStack result() {
        return draft.copy();
    }

    @Override
    protected boolean canRestoreDefaults() {
        return tab != 4;
    }

    @Override
    protected String invalidReason() {
        String error = super.invalidReason();
        if (error != null) return error;
        if (draft.has(DataComponents.MAX_DAMAGE) && draft.getMaxStackSize() != 1)
            return "Items with durability must have a stack size of 1.";
        if (draft.getCount() > draft.getMaxStackSize())
            return "Stack size must be at least the current amount ("
                    + draft.getCount()
                    + "). Change the amount in Type first.";
        if (draft.getOrDefault(DataComponents.DAMAGE, 0) > draft.getMaxDamage())
            return "Damage cannot exceed maximum durability.";
        return ItemStack.validateStrict(draft).error().map(e -> e.message()).orElse(null);
    }

    @Override
    protected boolean relevant(String key) {
        return (!key.startsWith("consume.") || draft.has(DataComponents.CONSUMABLE))
                && (!key.startsWith("food.") || draft.has(DataComponents.FOOD));
    }

    @Override
    protected void buildRows() {
        switch (tab) {
            case 0 -> general();
            case 1 -> appearance();
            case 2 -> foodAndDrink();
            case 3 -> use();
            default -> advanced();
        }
    }

    private void general() {
        row(
                "Stack size",
                "Maximum items in one stack, from 1 to 99. Durability items must stay at 1.",
                number(
                        "general.stack",
                        "" + draft.getMaxStackSize(),
                        1,
                        Item.ABSOLUTE_MAX_STACK_SIZE,
                        true,
                        v -> draft.set(DataComponents.MAX_STACK_SIZE, (int) v)),
                90);
        row(
                "Max durability",
                "Total durability. Use 0 for no durability. Requires a stack size and amount of 1.",
                number(
                        "general.durability",
                        "" + draft.getMaxDamage(),
                        0,
                        Integer.MAX_VALUE,
                        true,
                        v -> {
                            if (v == 0) {
                                draft.remove(DataComponents.MAX_DAMAGE);
                                draft.remove(DataComponents.DAMAGE);
                                setFieldValue("general.damage", "0");
                                errors.remove("general.damage");
                            } else {
                                draft.set(DataComponents.MAX_DAMAGE, (int) v);
                                if (!draft.has(DataComponents.DAMAGE))
                                    draft.set(DataComponents.DAMAGE, 0);
                                try {
                                    int damage =
                                            Integer.parseInt(
                                                    values.getOrDefault("general.damage", "0"));
                                    if (damage >= 0) {
                                        draft.set(DataComponents.DAMAGE, damage);
                                        errors.remove("general.damage");
                                    }
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }),
                90);
        row(
                "Damage",
                "Durability already used. It cannot exceed maximum durability.",
                number(
                        "general.damage",
                        "" + draft.getDamageValue(),
                        0,
                        Integer.MAX_VALUE,
                        true,
                        v -> {
                            if (draft.has(DataComponents.MAX_DAMAGE))
                                draft.set(DataComponents.DAMAGE, (int) v);
                            else if (v > 0)
                                errors.put(
                                        "general.damage",
                                        "Add maximum durability before setting damage.");
                        }),
                90);
        row(
                "Unbreakable",
                "Stops the item from losing durability.",
                toggle(
                        () -> draft.has(DataComponents.UNBREAKABLE),
                        v -> {
                            if (v) draft.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                            else draft.remove(DataComponents.UNBREAKABLE);
                        }),
                52);
        row(
                "Repair cost",
                "Prior anvil work penalty. 0 removes the prior-work penalty.",
                number(
                        "general.repair",
                        "" + draft.getOrDefault(DataComponents.REPAIR_COST, 0),
                        0,
                        Integer.MAX_VALUE,
                        true,
                        v -> draft.set(DataComponents.REPAIR_COST, (int) v)),
                90);
        row(
                "Can Break",
                "Choose blocks this item can break in Adventure mode.",
                action(
                        "Choose blocks...",
                        () ->
                                mc.gui.setScreen(
                                        new GuiAdventureBlockModifier(
                                                this,
                                                draft,
                                                GuiAdventureBlockModifier.Kind.CAN_BREAK))),
                144);
        row(
                "Can Place",
                "Choose blocks this item can be placed against in Adventure mode.",
                action(
                        "Choose blocks...",
                        () ->
                                mc.gui.setScreen(
                                        new GuiAdventureBlockModifier(
                                                this,
                                                draft,
                                                GuiAdventureBlockModifier.Kind.CAN_PLACE))),
                144);
    }

    private void appearance() {
        row(
                "Item model",
                "Choose an appearance from loaded resource-pack models. Material and behavior stay"
                        + " the same.",
                action(modelName(draft.get(DataComponents.ITEM_MODEL)), this::chooseModel),
                180);
        Rarity rarity = draft.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        row(
                "Rarity",
                "Changes the item's default name color and rarity.",
                dropdown(
                        friendly(rarity.getSerializedName()),
                        List.of(Rarity.values()),
                        r -> friendly(r.getSerializedName()),
                        r -> draft.set(DataComponents.RARITY, r)),
                140);
        Boolean glint = draft.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        row(
                "Enchantment glow",
                "Default follows enchantments. On forces the shimmer; Off hides it.",
                dropdown(
                                glint == null ? "Default" : glint ? "On" : "Off",
                                List.of("Default", "On", "Off"),
                                Function.identity(),
                                v -> {
                                    if (v.equals("Default"))
                                        draft.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
                                    else
                                        draft.set(
                                                DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
                                                v.equals("On"));
                                })
                        .setOnState(Boolean.TRUE.equals(glint)),
                140);
        row(
                "Tooltip style",
                "Custom tooltip frame supplied by a resource pack. Leave blank for the normal"
                        + " frame.",
                text(
                        "appearance.tooltip",
                        Objects.toString(draft.get(DataComponents.TOOLTIP_STYLE), ""),
                        v -> {
                            if (v.isBlank()) {
                                errors.remove("appearance.tooltip");
                                draft.remove(DataComponents.TOOLTIP_STYLE);
                            } else {
                                Identifier id = Identifier.tryParse(v);
                                if (id == null)
                                    errors.put(
                                            "appearance.tooltip",
                                            "Enter a valid tooltip style ID, such as pack:style.");
                                else {
                                    errors.remove("appearance.tooltip");
                                    draft.set(DataComponents.TOOLTIP_STYLE, id);
                                }
                            }
                        }),
                180);
    }

    private void use() {
        row(
                "Break sound",
                "Sound when this item runs out of durability. None makes it silent.",
                action(
                        soundName(draft.get(DataComponents.BREAK_SOUND)),
                        () ->
                                chooseSound(
                                        "Break sound",
                                        draft.get(DataComponents.BREAK_SOUND),
                                        s -> draft.set(DataComponents.BREAK_SOUND, s),
                                        true)),
                180);
        swing("Attack animation", DataComponents.ATTACK_ANIMATION, "attack");
        swing("Interact animation", DataComponents.INTERACT_ANIMATION, "interact");
        UseEffects effects = draft.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT);
        row(
                "Sprint while using",
                "Allow sprinting while eating or holding use.",
                toggle(
                        () ->
                                draft.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT)
                                        .canSprint(),
                        v -> {
                            UseEffects e =
                                    draft.getOrDefault(
                                            DataComponents.USE_EFFECTS, UseEffects.DEFAULT);
                            draft.set(
                                    DataComponents.USE_EFFECTS,
                                    new UseEffects(v, e.interactVibrations(), e.speedMultiplier()));
                        }),
                52);
        row(
                "Use vibrations",
                "Emit vibrations while using, detectable by sculk sensors.",
                toggle(
                        () ->
                                draft.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT)
                                        .interactVibrations(),
                        v -> {
                            UseEffects e =
                                    draft.getOrDefault(
                                            DataComponents.USE_EFFECTS, UseEffects.DEFAULT);
                            draft.set(
                                    DataComponents.USE_EFFECTS,
                                    new UseEffects(e.canSprint(), v, e.speedMultiplier()));
                        }),
                52);
        row(
                "Use speed (%)",
                "Movement speed while using. 100 is normal speed; 0 prevents movement.",
                number(
                        "use.speed",
                        percent(effects.speedMultiplier()),
                        0,
                        100,
                        false,
                        v -> {
                            UseEffects e =
                                    draft.getOrDefault(
                                            DataComponents.USE_EFFECTS, UseEffects.DEFAULT);
                            draft.set(
                                    DataComponents.USE_EFFECTS,
                                    new UseEffects(
                                            e.canSprint(),
                                            e.interactVibrations(),
                                            (float) (v / 100)));
                        }),
                90);
        UseCooldown cooldown = draft.get(DataComponents.USE_COOLDOWN);
        row(
                "Cooldown (seconds)",
                "Wait after use. 0 disables cooldown.",
                number(
                        "use.cooldown",
                        format(cooldown == null ? 0 : cooldown.seconds()),
                        0,
                        Integer.MAX_VALUE / 20.0,
                        false,
                        v -> {
                            if (v > 0 && (float) v == 0) {
                                errors.put(
                                        "use.cooldown",
                                        "Cooldown is too small. Enter 0 to disable it.");
                                return;
                            }
                            if (v == 0) draft.remove(DataComponents.USE_COOLDOWN);
                            else
                                draft.set(
                                        DataComponents.USE_COOLDOWN,
                                        new UseCooldown(
                                                (float) v, Optional.ofNullable(cooldownGroup())));
                        }),
                90);
        row(
                "Cooldown group",
                "Optional group ID. Items in the same group share a cooldown.",
                text(
                        "use.group",
                        cooldown == null
                                ? ""
                                : cooldown.cooldownGroup().map(Object::toString).orElse(""),
                        v -> {
                            Identifier id = v.isBlank() ? null : Identifier.tryParse(v);
                            if (!v.isBlank() && id == null)
                                errors.put(
                                        "use.group",
                                        "Enter a valid cooldown group ID or leave it blank.");
                            else {
                                errors.remove("use.group");
                                UseCooldown c = draft.get(DataComponents.USE_COOLDOWN);
                                if (c != null)
                                    draft.set(
                                            DataComponents.USE_COOLDOWN,
                                            new UseCooldown(c.seconds(), Optional.ofNullable(id)));
                            }
                        }),
                180);
    }

    private void foodAndDrink() {
        Consumable current = draft.get(DataComponents.CONSUMABLE);
        List<String> modes = new ArrayList<>(List.of("Off", "Eat", "Drink"));
        for (ItemUseAnimation animation : ItemUseAnimation.values()) {
            String name = animationName(animation);
            if (!modes.contains(name)) modes.add(name);
        }
        row(
                "Consume item",
                "Choose how this item is consumed. Off keeps the settings while you edit. Other"
                    + " animations only change the pose; they do not add bow or shield behavior.",
                dropdown(
                        current == null ? "Off" : animationName(current.animation()),
                        modes,
                        v -> v,
                        this::setConsumptionMode),
                150);
        var editEffects =
                action(
                        "Edit effects...",
                        () ->
                                mc.gui.setScreen(
                                        new GuiConsumeEffectsEditor(
                                                this,
                                                draft.get(DataComponents.CONSUMABLE)
                                                        .onConsumeEffects(),
                                                list -> {
                                                    Consumable c =
                                                            draft.get(DataComponents.CONSUMABLE);
                                                    consumeDraft =
                                                            new Consumable(
                                                                    c.consumeSeconds(),
                                                                    c.animation(),
                                                                    c.sound(),
                                                                    c.hasConsumeParticles(),
                                                                    list);
                                                    draft.set(
                                                            DataComponents.CONSUMABLE,
                                                            consumeDraft);
                                                })));
        editEffects.active = current != null;
        row(
                "Effects",
                current == null
                        ? "Choose Eat, Drink or another consumption animation first."
                        : "What happens after consuming: potion effects, removing effects,"
                                + " teleporting or playing a sound.",
                editEffects,
                150);
        row(
                "Restore hunger",
                "Make this item restore hunger. Turning this on also enables eating if consumption"
                        + " is off.",
                toggle(
                        () -> draft.has(DataComponents.FOOD),
                        v -> {
                            if (v) {
                                if (!draft.has(DataComponents.CONSUMABLE))
                                    setConsumptionMode("Eat");
                                draft.set(DataComponents.FOOD, foodDraft);
                            } else {
                                foodDraft = draft.get(DataComponents.FOOD);
                                draft.remove(DataComponents.FOOD);
                            }
                        }),
                52);
        if (draft.has(DataComponents.FOOD)) {
            FoodProperties food = draft.get(DataComponents.FOOD);
            row(
                    "Hunger points",
                    "Hunger restored. Two points fill one hunger icon.",
                    number(
                            "food.nutrition",
                            "" + food.nutrition(),
                            0,
                            Integer.MAX_VALUE,
                            true,
                            v -> food((int) v, null, null)),
                    90);
            row(
                    "Saturation",
                    "Extra nourishment that delays hunger loss after eating.",
                    number(
                            "food.saturation",
                            format(food.saturation()),
                            0,
                            Float.MAX_VALUE,
                            false,
                            v -> food(null, (float) v, null)),
                    90);
            row(
                    "Eat when full",
                    "Allow consuming even at full hunger.",
                    toggle(
                            () -> draft.get(DataComponents.FOOD).canAlwaysEat(),
                            v -> food(null, null, v)),
                    52);
        }
        if (current != null) consumption();
    }

    private static String animationName(ItemUseAnimation animation) {
        return animation == ItemUseAnimation.NONE
                ? "No animation"
                : friendly(animation.getSerializedName());
    }

    private void setConsumptionMode(String mode) {
        if (mode.equals("Off")) {
            Consumable current = draft.get(DataComponents.CONSUMABLE);
            if (current != null) consumeDraft = current;
            draft.remove(DataComponents.CONSUMABLE);
            return;
        }
        ItemUseAnimation animation =
                Arrays.stream(ItemUseAnimation.values())
                        .filter(a -> animationName(a).equals(mode))
                        .findFirst()
                        .orElseThrow();
        Consumable current = draft.get(DataComponents.CONSUMABLE);
        Consumable base = current == null ? consumeDraft : current;
        // Change the ordinary eating/drinking sound with its animation; retain a custom sound.
        Holder<SoundEvent> sound = base.sound();
        Identifier id = sound.value().location();
        if (id.equals(Identifier.withDefaultNamespace("entity.generic.eat"))
                || id.equals(Identifier.withDefaultNamespace("entity.generic.drink"))) {
            if (animation == ItemUseAnimation.EAT)
                sound = sound(Identifier.withDefaultNamespace("entity.generic.eat"));
            if (animation == ItemUseAnimation.DRINK) sound = SoundEvents.GENERIC_DRINK;
        }
        consumeDraft =
                new Consumable(
                        base.consumeSeconds(),
                        animation,
                        sound,
                        base.hasConsumeParticles(),
                        base.onConsumeEffects());
        draft.set(DataComponents.CONSUMABLE, consumeDraft);
    }

    private Identifier cooldownGroup() {
        String value = values.getOrDefault("use.group", "");
        return value.isBlank()
                ? draft.getOrDefault(DataComponents.USE_COOLDOWN, new UseCooldown(1))
                        .cooldownGroup()
                        .orElse(null)
                : Identifier.tryParse(value);
    }

    private void swing(String name, DataComponentType<SwingAnimation> type, String key) {
        SwingAnimation animation = draft.getOrDefault(type, SwingAnimation.DEFAULT);
        row(
                name,
                "Hand motion. Whack swings, Stab thrusts, None hides it.",
                dropdown(
                        friendly(animation.type().getSerializedName()),
                        List.of(SwingAnimationType.values()),
                        v -> friendly(v.getSerializedName()),
                        v -> {
                            SwingAnimation a = draft.getOrDefault(type, SwingAnimation.DEFAULT);
                            draft.set(type, new SwingAnimation(v, a.duration()));
                        }),
                140);
        row(
                name.replace("animation", "length (ticks)"),
                "Animation length. 20 ticks equals one second.",
                number(
                        "use." + key,
                        "" + animation.duration(),
                        0,
                        Integer.MAX_VALUE,
                        true,
                        v -> {
                            SwingAnimation a = draft.getOrDefault(type, SwingAnimation.DEFAULT);
                            draft.set(type, new SwingAnimation(a.type(), (int) v));
                        }),
                90);
    }

    private void consumption() {
        Consumable consume = draft.get(DataComponents.CONSUMABLE);
        row(
                "Consume time (seconds)",
                "Hold-use time before consumption. 0 consumes instantly.",
                number(
                        "consume.time",
                        format(consume.consumeSeconds()),
                        0,
                        Integer.MAX_VALUE / 20.0,
                        false,
                        v -> consume((float) v, null, null, null)),
                90);
        row(
                "Consume sound",
                "Sound during consumption.",
                action(
                        soundName(consume.sound()),
                        () ->
                                chooseSound(
                                        "Consume sound",
                                        consume.sound(),
                                        s -> consume(null, null, s, null),
                                        false)),
                180);
        row(
                "Consume particles",
                "Show item particles while consuming.",
                toggle(
                        () -> draft.get(DataComponents.CONSUMABLE).hasConsumeParticles(),
                        v -> consume(null, null, null, v)),
                52);
        row(
                "Returned item",
                "Item left after consuming, such as an empty bottle. Choose Air for none.",
                action(
                        remainderName(),
                        () ->
                                mc.gui.setScreen(
                                        new GuiTypeListSelector(
                                                this,
                                                Component.literal("Returned item"),
                                                stack -> {
                                                    draft.set(
                                                            DataComponents.USE_REMAINDER,
                                                            stack.isEmpty()
                                                                    ? null
                                                                    : new UseRemainder(
                                                                            ItemStackTemplate
                                                                                    .fromNonEmptyStack(
                                                                                            stack)));
                                                    return this;
                                                }))),
                180);
    }

    private void consume(
            Float time, ItemUseAnimation animation, Holder<SoundEvent> sound, Boolean particles) {
        Consumable c = draft.get(DataComponents.CONSUMABLE);
        consumeDraft =
                new Consumable(
                        time == null ? c.consumeSeconds() : time,
                        animation == null ? c.animation() : animation,
                        sound == null ? c.sound() : sound,
                        particles == null ? c.hasConsumeParticles() : particles,
                        c.onConsumeEffects());
        draft.set(DataComponents.CONSUMABLE, consumeDraft);
    }

    private void food(Integer nutrition, Float saturation, Boolean always) {
        FoodProperties f = draft.get(DataComponents.FOOD);
        foodDraft =
                new FoodProperties(
                        nutrition == null ? f.nutrition() : nutrition,
                        saturation == null ? f.saturation() : saturation,
                        always == null ? f.canAlwaysEat() : always);
        draft.set(DataComponents.FOOD, foodDraft);
    }

    private void advanced() {
        row(
                "Data components",
                "Advanced editor for components without a dedicated interface.",
                action(
                        "Open editor...",
                        () -> {
                            GuiDataComponentModifier screen =
                                    new GuiDataComponentModifier(this, draft.copy());
                            screen.setSetter(
                                    stack -> {
                                        draft = stack.copy();
                                        values.clear();
                                        errors.clear();
                                    });
                            mc.gui.setScreen(screen);
                        }),
                150);
        row(
                "Custom data",
                "Advanced custom data for commands and other mods.",
                action(
                        "Open editor...",
                        () ->
                                mc.gui.setScreen(
                                        new GuiNBTModifier(
                                                this,
                                                tag -> ItemUtils.setTag(draft, tag),
                                                ItemUtils.getTag(draft) == null
                                                        ? new net.minecraft.nbt.CompoundTag()
                                                        : ItemUtils.getTag(draft).copy()))),
                150);
    }

    private String remainderName() {
        UseRemainder r = draft.get(DataComponents.USE_REMAINDER);
        return r == null ? "None" : r.convertInto().create().getHoverName().getString();
    }

    private void chooseModel() {
        Set<Identifier> models = new HashSet<>();
        mc.getResourceManager()
                .listResources("items", id -> id.getPath().endsWith(".json"))
                .keySet()
                .forEach(
                        id ->
                                models.add(
                                        Identifier.fromNamespaceAndPath(
                                                id.getNamespace(),
                                                id.getPath()
                                                        .substring(6, id.getPath().length() - 5))));
        Identifier current = draft.get(DataComponents.ITEM_MODEL);
        if (current != null) models.add(current);
        mc.gui.setScreen(
                new GuiResourcePicker(
                        this,
                        "Item model",
                        models,
                        current,
                        id -> draft.set(DataComponents.ITEM_MODEL, id),
                        draft,
                        false,
                        false));
    }

    public void chooseSound(
            String title,
            Holder<SoundEvent> current,
            Consumer<Holder<SoundEvent>> setter,
            boolean none) {
        Set<Identifier> sounds = new HashSet<>(BuiltInRegistries.SOUND_EVENT.keySet());
        sounds.addAll(mc.getSoundManager().getAvailableSounds());
        Identifier id = current == null ? null : current.value().location();
        if (id != null) sounds.add(id);
        mc.gui.setScreen(
                new GuiResourcePicker(
                        this,
                        title,
                        sounds,
                        id,
                        v -> setter.accept(sound(v)),
                        ItemStack.EMPTY,
                        true,
                        none));
    }

    public static Holder<SoundEvent> sound(Identifier id) {
        if (id == null) return null;
        return BuiltInRegistries.SOUND_EVENT
                .getOptional(id)
                .map(BuiltInRegistries.SOUND_EVENT::wrapAsHolder)
                .orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(id)));
    }

    private static String modelName(Identifier id) {
        return id == null ? "Default" : friendly(id.getPath());
    }

    public static String soundName(Holder<SoundEvent> sound) {
        return sound == null ? "None" : friendly(sound.value().location().getPath());
    }

    @Override
    protected void restoreDefaults() {
        ItemStack defaults = new ItemStack(draft.getItem());
        for (DataComponentType<?> type :
                tab == 0 ? GENERAL : tab == 1 ? APPEARANCE : tab == 2 ? FOOD : USE)
            restore(type, defaults);
        if (tab == 0 && draft.getCount() > draft.getMaxStackSize())
            draft.set(DataComponents.MAX_STACK_SIZE, draft.getCount());
        String prefix =
                tab == 0 ? "general." : tab == 1 ? "appearance." : tab == 2 ? "food." : "use.";
        values.keySet()
                .removeIf(
                        k ->
                                k.startsWith(prefix)
                                        || tab == 2
                                                && (k.startsWith("consume.")
                                                        || k.startsWith("food.")));
        errors.keySet()
                .removeIf(
                        k ->
                                k.startsWith(prefix)
                                        || tab == 2
                                                && (k.startsWith("consume.")
                                                        || k.startsWith("food.")));
        if (tab == 2) {
            consumeDraft =
                    draft.getOrDefault(DataComponents.CONSUMABLE, Consumable.builder().build());
            foodDraft = draft.getOrDefault(DataComponents.FOOD, new FoodProperties(4, 2.4f, false));
        }
    }

    private <V> void restore(DataComponentType<V> type, ItemStack defaults) {
        draft.set(type, defaults.get(type));
    }

    public static boolean supports(DataComponentType<?> type) {
        return GENERAL.contains(type)
                || APPEARANCE.contains(type)
                || FOOD.contains(type)
                || USE.contains(type);
    }

    public GuiItemSettingsEditor selectSection(DataComponentType<?> type) {
        tab =
                GENERAL.contains(type)
                        ? 0
                        : APPEARANCE.contains(type) ? 1 : FOOD.contains(type) ? 2 : 3;
        return this;
    }
}
