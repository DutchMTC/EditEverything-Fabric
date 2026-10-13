package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.utils.*;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

import java.util.*;
import java.util.function.Consumer;

/** Potion appearance above the same inline effects workbench used for entities. */
public class GuiPotionModifier extends GuiEffectsEditor<ItemStack> {
    final PotionEditorData data;
    private ItemStack preview;
    private final Map<Item, float[]> previewCenters = new HashMap<>();

    public GuiPotionModifier(Screen parent, ItemStack source, Consumer<ItemStack> setter) {
        super(
                parent,
                Component.literal("Potion editor"),
                setter,
                new PotionEditorData(source, Minecraft.getInstance().level.registryAccess()));
        data = (PotionEditorData) super.data;
    }

    @Override
    public boolean isModified() {
        return data.changed();
    }

    @Override
    protected ItemStack result() {
        return data.result();
    }

    @Override
    protected int headerHeight() {
        return 80;
    }

    @Override
    protected void buildHeader() {
        for (int i = 0; i < PotionEditorData.FORMS.size(); i++) {
            Item item = PotionEditorData.FORMS.get(i);
            EEButton type =
                    addRenderableWidget(
                            new EEButton(
                                    left + 76 + i * 32,
                                    top + 33,
                                    28,
                                    28,
                                    Component.literal(formName(item)),
                                    b -> {
                                        data.item = item;
                                        init();
                                    }) {
                                @Override
                                protected void extractContents(
                                        GuiGraphicsExtractor g, int mx, int my, float delta) {
                                    boolean chosen = data.item == item;
                                    EETheme.bevel(
                                            g,
                                            getX(),
                                            getY(),
                                            getWidth(),
                                            getHeight(),
                                            chosen
                                                    ? EETheme.CONTROL
                                                    : isHoveredOrFocused()
                                                            ? EETheme.MUTED
                                                            : EETheme.INPUT,
                                            true);
                                    if (chosen)
                                        EETheme.rect(
                                                g,
                                                getX() + 1,
                                                getBottom() - 3,
                                                getWidth() - 2,
                                                2,
                                                EETheme.ON);
                                    ItemStack icon = new ItemStack(item);
                                    icon.set(DataComponents.POTION_CONTENTS, data.contents());
                                    GuiUtils.drawItemStack(g, icon, getX() + 6, getY() + 6);
                                }
                            });
            type.active = PotionEditorData.supports(data.source);
            type.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    type.active
                                            ? formName(item)
                                                    + (data.item == item ? " (selected)" : "")
                                            : "Item type can only be changed for potions and tipped"
                                                    + " arrows.")));
        }
        addRenderableWidget(
                        new EEButton(
                                left + panelWidth - 48,
                                top + 33,
                                36,
                                24,
                                Component.literal("Potion color"),
                                b ->
                                        mc.gui.setScreen(
                                                new GuiColorModifier(
                                                        this,
                                                        v -> data.color = v,
                                                        data.color,
                                                        preview.get(DataComponents.POTION_CONTENTS)
                                                                .getColor(),
                                                        true))) {
                            @Override
                            protected void extractContents(
                                    GuiGraphicsExtractor g, int mx, int my, float delta) {
                                EETheme.bevel(
                                        g,
                                        getX(),
                                        getY(),
                                        getWidth(),
                                        getHeight(),
                                        isHoveredOrFocused()
                                                ? EETheme.CONTROL_HOVER
                                                : EETheme.CONTROL,
                                        false);
                                EETheme.rect(
                                        g,
                                        getX() + 3,
                                        getY() + 3,
                                        getWidth() - 6,
                                        getHeight() - 6,
                                        0xFF000000
                                                | preview.get(DataComponents.POTION_CONTENTS)
                                                        .getColor());
                            }
                        })
                .setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        "Change the potion color, or reset to the effects' natural"
                                                + " color.")));
    }

    @Override
    protected void renderHeader(GuiGraphicsExtractor g, int mx, int my) {
        float[] center = previewCenter(data.item);
        g.pose().pushMatrix();
        g.pose().translate(left + 39 - center[0] * 2, top + 49 - center[1] * 2);
        g.pose().scale(2, 2);
        GuiUtils.drawItemStack(g, preview, 0, 0);
        g.pose().popMatrix();
        g.text(font, formName(data.item), left + 76, top + 67, EETheme.LIGHT, false);
        g.text(font, "Color", left + panelWidth - 46, top + 15, EETheme.CONTROL_HOVER, false);
    }

    @Override
    protected void renderPreviewTooltip(GuiGraphicsExtractor g, int mx, int my) {
        if (previewHovered(mx, my)) GuiUtils.setTooltipForNextFrame(g, font, preview, mx, my);
    }

    @Override
    protected void refreshPreview() {
        preview = new ItemStack(data.item);
        preview.set(DataComponents.POTION_CONTENTS, data.contents());
    }

    private static String formName(Item item) {
        return item == Items.POTION
                ? "Drinkable"
                : item == Items.SPLASH_POTION
                        ? "Splash"
                        : item == Items.LINGERING_POTION
                                ? "Lingering"
                                : item == Items.TIPPED_ARROW
                                        ? "Tipped arrow"
                                        : item.getName(new ItemStack(item)).getString();
    }

    private boolean previewHovered(int mx, int my) {
        return !choosing() && mx >= left + 15 && mx < left + 63 && my >= top + 29 && my < top + 72;
    }

    private float[] previewCenter(Item item) {
        if (!PotionEditorData.FORMS.contains(item)) return new float[] {8, 8};
        return previewCenters.computeIfAbsent(
                item,
                key -> {
                    String path = BuiltInRegistries.ITEM.getKey(key).getPath();
                    List<String> textures =
                            key == Items.TIPPED_ARROW
                                    ? List.of("tipped_arrow_base", "tipped_arrow_head")
                                    : List.of(path, "potion_overlay");
                    float minX = 16, minY = 16, maxX = 0, maxY = 0;
                    boolean found = false;
                    for (String texture : textures) {
                        var resource =
                                mc.getResourceManager()
                                        .getResource(
                                                Identifier.withDefaultNamespace(
                                                        "textures/item/" + texture + ".png"));
                        if (resource.isEmpty()) continue;
                        try (var stream = resource.get().open();
                                var image = NativeImage.read(stream)) {
                            for (int y = 0; y < image.getHeight(); y++)
                                for (int x = 0; x < image.getWidth(); x++)
                                    if ((image.getPixel(x, y) >>> 24) != 0) {
                                        found = true;
                                        minX = Math.min(minX, x * 16F / image.getWidth());
                                        minY = Math.min(minY, y * 16F / image.getHeight());
                                        maxX = Math.max(maxX, (x + 1) * 16F / image.getWidth());
                                        maxY = Math.max(maxY, (y + 1) * 16F / image.getHeight());
                                    }
                        } catch (java.io.IOException ignored) {
                            return new float[] {8, 8};
                        }
                    }
                    return found
                            ? new float[] {(minX + maxX) / 2, (minY + maxY) / 2}
                            : new float[] {8, 8};
                });
    }
}
