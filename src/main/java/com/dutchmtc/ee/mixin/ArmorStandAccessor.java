package com.dutchmtc.ee.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ArmorStand.class)
public interface ArmorStandAccessor {
    @Accessor("disabledSlots")
    int ee$getDisabledSlots();

    @Accessor("disabledSlots")
    void ee$setDisabledSlots(int disabledSlots);

    @Invoker("setSmall")
    void ee$invokeSetSmall(boolean small);

    @Invoker("setMarker")
    void ee$invokeSetMarker(boolean marker);
}

