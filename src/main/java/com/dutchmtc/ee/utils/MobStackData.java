package com.dutchmtc.ee.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;

import java.util.ArrayList;
import java.util.List;

/** A top-to-bottom editable chain; imported additional riders stay attached to their own mob. */
public final class MobStackData {
    public static final int MAX_MOBS = 64;

    public static final class Mob {
        private String id;
        private CompoundTag tag;

        private Mob(String id, CompoundTag tag) {
            this.id = id;
            this.tag = tag.copy();
        }

        public String id() {
            return id;
        }

        public EntityType<?> type() {
            Identifier key = Identifier.tryParse(id);
            return key == null
                    ? null
                    : BuiltInRegistries.ENTITY_TYPE.get(key).map(h -> h.value()).orElse(null);
        }

        public String name() {
            return type() == null ? id : type().getDescription().getString();
        }

        public CompoundTag tag() {
            return tag.copy();
        }

        public void update(ItemStack edited) {
            var data = edited.get(DataComponents.ENTITY_DATA);
            if (data != null) {
                id = BuiltInRegistries.ENTITY_TYPE.getKey(data.type()).toString();
                tag = data.copyTagWithoutId();
            }
        }

        public ItemStack egg() {
            EntityType<?> type = type();
            if (type == null) return ItemStack.EMPTY;
            ItemStack egg =
                    SpawnEggItem.byId(type)
                            .map(h -> new ItemStack(h.value()))
                            .orElseGet(
                                    () ->
                                            new ItemStack(
                                                    net.minecraft.world.item.Items.PIG_SPAWN_EGG));
            egg.set(DataComponents.ENTITY_DATA, TypedEntityData.of(type, tag));
            return egg;
        }
    }

    private final ItemStack original;
    private final List<Mob> mobs = new ArrayList<>();

    public MobStackData(ItemStack item) {
        original = item.copy();
        var data = item.get(DataComponents.ENTITY_DATA);
        EntityType<?> type = data != null ? data.type() : SpawnEggItem.getType(item);
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTagWithoutId();
        for (int depth = 0; depth < MAX_MOBS; depth++) {
            ListTag riders = tag.getList("Passengers").orElseGet(ListTag::new).copy();
            CompoundTag next = riders.isEmpty() ? null : riders.getCompound(0).orElse(null);
            if (next != null && depth + 1 < MAX_MOBS) {
                riders.remove(0);
                if (riders.isEmpty()) tag.remove("Passengers");
                else tag.put("Passengers", riders);
            }
            mobs.addFirst(new Mob(id, tag));
            if (next == null || depth + 1 == MAX_MOBS) break;
            id = next.getString("id").orElse("");
            tag = next.copy();
            tag.remove("id");
        }
    }

    public List<Mob> mobs() {
        return List.copyOf(mobs);
    }

    public void add(EntityType<?> type) {
        mobs.addFirst(
                new Mob(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), new CompoundTag()));
    }

    public String duplicateReason(Mob mob) {
        if (mob == null || !mobs.contains(mob)) return "Select a mob to duplicate.";
        if (mob.type() == null || !mob.type().canSummon() || !mob.type().canSerialize())
            return "Choose a valid entity type before duplicating this mob.";
        return count() + count(mob.tag, 0) > MAX_MOBS
                ? "Duplicating this mob would exceed the "
                        + MAX_MOBS
                        + " mob limit, including additional riders."
                : null;
    }

    /**
     * Copy only this mob and its attached side riders, placing it immediately above the original.
     */
    public Mob duplicate(Mob mob) {
        if (duplicateReason(mob) != null) return null;
        Mob copy = new Mob(mob.id, mob.tag);
        mobs.add(mobs.indexOf(mob), copy);
        return copy;
    }

    public void remove(Mob mob) {
        if (mobs.size() > 1) mobs.remove(mob);
    }

    public void move(Mob mob, int index) {
        if (!mobs.remove(mob)) return;
        mobs.add(Math.clamp(index, 0, mobs.size()), mob);
    }

    public ItemStack result() {
        CompoundTag upper = null;
        for (Mob mob : mobs) {
            CompoundTag tag = mob.tag();
            if (upper != null) {
                ListTag riders = tag.getList("Passengers").orElseGet(ListTag::new).copy();
                riders.addFirst(upper);
                tag.put("Passengers", riders);
            }
            tag.putString("id", mob.id());
            upper = tag;
        }
        Mob base = mobs.getLast();
        ItemStack result = original.copy();
        var before = original.get(DataComponents.ENTITY_DATA);
        EntityType<?> originalType =
                before == null ? SpawnEggItem.getType(original) : before.type();
        if (base.type() == null) return result;
        if (base.type() != originalType)
            result =
                    SpawnEggItem.byId(base.type())
                            .map(h -> original.transmuteCopy(h.value()))
                            .orElse(result);
        upper.remove("id");
        result.set(DataComponents.ENTITY_DATA, TypedEntityData.of(base.type(), upper));
        return result;
    }

    public boolean modified() {
        return !ItemStack.matches(original, result());
    }

    public int count() {
        int total = 0;
        for (Mob mob : mobs) {
            total += count(mob.tag, 0);
            if (total > MAX_MOBS) break;
        }
        return total;
    }

    public String validationReason() {
        for (Mob mob : mobs)
            if (mob.type() == null || !mob.type().canSummon())
                return "Choose a valid entity type for " + mob.name() + ".";
        for (Mob mob : mobs)
            if (!mob.type().canSerialize()) return mob.name() + " cannot be saved as a riding mob.";
        int total = count();
        return total > MAX_MOBS
                ? "A stack can contain at most " + MAX_MOBS + " mobs, including additional riders."
                : null;
    }

    private static int count(CompoundTag tag, int depth) {
        if (depth >= MAX_MOBS) return MAX_MOBS + 1;
        int total = 1;
        for (var rider : tag.getList("Passengers").orElseGet(ListTag::new)) {
            if (rider instanceof CompoundTag child) total += count(child, depth + 1);
            if (total > MAX_MOBS) break;
        }
        return total;
    }
}
