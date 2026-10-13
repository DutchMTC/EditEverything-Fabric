package com.dutchmtc.ee.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("highlightPos") int ee$getHighlightPos();
    @Accessor("highlightPos") void ee$setHighlightPos(int position);
    @Accessor("textX") int ee$getTextX();
    @Accessor("textY") int ee$getTextY();
    @Accessor("textY") void ee$setTextY(int y);
    @Accessor("displayPos") int ee$getDisplayPos();
    @Accessor("displayPos") void ee$setDisplayPos(int position);
}
