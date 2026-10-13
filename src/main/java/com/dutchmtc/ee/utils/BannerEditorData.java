package com.dutchmtc.ee.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Native-order layers (background first), with stable identities for selection and dragging. */
public final class BannerEditorData {
    public static final int VISIBLE_LAYER_LIMIT = 16;

    public static final class Layer {
        public Holder<BannerPattern> pattern;
        public DyeColor color;

        private Layer(Holder<BannerPattern> pattern, DyeColor color) {
            this.pattern = pattern;
            this.color = color;
        }

        public BannerPatternLayers.Layer nativeLayer() {
            return new BannerPatternLayers.Layer(pattern, color);
        }
    }

    private final ItemStack source;
    private final DyeColor initialBase;
    private final List<Layer> layers = new ArrayList<>();
    private DyeColor base;

    public BannerEditorData(ItemStack source) {
        this.source = source.copy();
        base =
                initialBase =
                        source.getItem() instanceof BannerItem banner
                                ? banner.getColor()
                                : source.get(DataComponents.BASE_COLOR);
        for (var layer :
                source.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
                        .layers()) layers.add(new Layer(layer.pattern(), layer.color()));
    }

    public List<Layer> layers() {
        return List.copyOf(layers);
    }

    public boolean shield() {
        return source.getItem() instanceof ShieldItem;
    }

    public DyeColor base() {
        return base;
    }

    public DyeColor displayedBase() {
        return base == null && !layers.isEmpty() ? DyeColor.WHITE : base;
    }

    public boolean canAdd() {
        return layers.size() < VISIBLE_LAYER_LIMIT;
    }

    public boolean setBase(DyeColor color) {
        if (color == null && (!shield() || !layers.isEmpty())) return false;
        base = color;
        return true;
    }

    public Layer add(Holder<BannerPattern> pattern) {
        if (!canAdd() || pattern == null || pattern.is(BannerPatterns.BASE)) return null;
        int rgb =
                displayedBase() == null
                        ? DyeColor.WHITE.getTextureDiffuseColor()
                        : displayedBase().getTextureDiffuseColor();
        double luminance =
                ((rgb >> 16 & 255) * .2126 + (rgb >> 8 & 255) * .7152 + (rgb & 255) * .0722) / 255;
        Layer layer = new Layer(pattern, luminance > .45 ? DyeColor.BLACK : DyeColor.WHITE);
        layers.add(layer);
        return layer;
    }

    public Layer duplicate(Layer layer) {
        int index = layers.indexOf(layer);
        if (index < 0 || !canAdd()) return null;
        Layer copy = new Layer(layer.pattern, layer.color);
        layers.add(index + 1, copy);
        return copy;
    }

    public void remove(Layer layer) {
        layers.remove(layer);
    }

    public void clear() {
        layers.clear();
    }

    public void move(Layer layer, int index) {
        int from = layers.indexOf(layer);
        if (from < 0) return;
        layers.remove(from);
        layers.add(Math.clamp(index, 0, layers.size()), layer);
    }

    public BannerPatternLayers patterns() {
        return new BannerPatternLayers(layers.stream().map(Layer::nativeLayer).toList());
    }

    public boolean changed() {
        return !Objects.equals(base, initialBase)
                || !patterns()
                        .equals(
                                source.getOrDefault(
                                        DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
    }

    public ItemStack result() {
        if (!changed()) return source.copy();
        ItemStack result =
                source.getItem() instanceof BannerItem && base != initialBase
                        ? ItemUtils.setItem(Items.BANNER.pick(base), source)
                        : source.copy();
        if (shield()) {
            if (base == null) result.remove(DataComponents.BASE_COLOR);
            else result.set(DataComponents.BASE_COLOR, base);
        }
        if (layers.isEmpty()) result.remove(DataComponents.BANNER_PATTERNS);
        else result.set(DataComponents.BANNER_PATTERNS, patterns());
        return result;
    }
}
