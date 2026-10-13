package com.dutchmtc.ee.mixin;

import com.dutchmtc.ee.utils.MobStackSpawning;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityType.class)
public class SpawnEggPassengersMixin {
    @Inject(method = "updateCustomEntityTag", at = @At("TAIL"))
    private static void ee$loadRiders(
            Level level,
            LivingEntity placer,
            Entity entity,
            TypedEntityData<EntityType<?>> data,
            CallbackInfo ci) {
        if (entity != null && data.type() == entity.getType())
            MobStackSpawning.attach(entity, data.copyTagWithoutId(), placer);
    }
}
