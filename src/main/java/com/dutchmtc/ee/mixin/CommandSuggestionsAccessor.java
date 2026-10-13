package com.dutchmtc.ee.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native completion bounds let editors suppress only controls underneath the popup. */
@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsAccessor {
    @Accessor("suggestions")
    CommandSuggestions.SuggestionsList ee$getSuggestions();

    @Mixin(CommandSuggestions.SuggestionsList.class)
    interface ListBounds {
        @Accessor("rect")
        Rect2i ee$getRect();
    }
}
