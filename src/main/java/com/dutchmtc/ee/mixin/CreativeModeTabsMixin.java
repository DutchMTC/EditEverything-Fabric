package com.dutchmtc.ee.mixin;

import com.dutchmtc.ee.EEMod;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeTabs.class)
public class CreativeModeTabsMixin {
    @Inject(
            method = "buildAllTabContents",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/item/CreativeModeTabs;streamAllTabs()Ljava/util/stream/Stream;",
                            ordinal = 1))
    private static void ee$populateExtraItems(
            CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        EEMod.ADVANCED_CREATIVE_TAB.rebuildExtraItems(parameters);
    }
}
