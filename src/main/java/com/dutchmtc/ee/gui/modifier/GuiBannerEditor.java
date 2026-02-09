package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.ItemStackButtonWidget;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.selector.GuiBannerPatternSelector;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class GuiBannerEditor extends GuiModifier<ItemStack> {
    private static final int MARGIN = 10;
    private static final int PADDING = 10;

    private static final int MAX_PATTERNS = 6;

    private static final int ROW_H = 20;
    private static final int LIST_W = 240;

    private static final int PREVIEW_SCALE = 4;
    private static final int PREVIEW_SIZE = 16 * PREVIEW_SCALE;

    private static final int LIST_HEADER_H = 18;

    private final ItemStack originalStack;
    private ItemStack stack;

    private final List<BannerPatternLayers.Layer> layers = new ArrayList<>();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    public GuiBannerEditor(Screen parent, ItemStack stack, Consumer<ItemStack> setter) {
        super(parent, Component.translatable("gui.ee.banner.title"), setter);
        this.originalStack = stack == null ? ItemStack.EMPTY : stack.copy();
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
        loadFromStack();
    }

    @Override
    public boolean isModified() {
        return !ItemStack.matches(stack, originalStack);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // do nothing
    }

    @Override
    protected void init() {
        clearWidgets();
        super.init();

        clampSelection();

        int left = MARGIN;
        int top = MARGIN;
        int right = width - MARGIN;
        int bottom = height - MARGIN;

        int listX = left + PADDING;
        int listY = top + 46;
        int panelsBottom = bottom - 56;
        int listH = Math.max(0, panelsBottom - listY);
        int listFooterH = layers.size() >= MAX_PATTERNS ? (font.lineHeight + 6) : 0;

        int editorX = listX + LIST_W + PADDING;
        int editorW = right - PADDING - editorX;
        int editorY = listY;
        int editorH = listH;
        int controlsW = Math.max(60, editorW - (PREVIEW_SIZE + 18));
        int dyeCols = Math.max(4, Math.min(8, controlsW / 20));
        int editorPadX = 6;
        int editorHeaderY = editorY + 4;
        int editorLineY = editorHeaderY + font.lineHeight + 2;
        int baseBtnY = editorLineY + font.lineHeight + 4;
        int patternBtnY = baseBtnY + 24;
        int dyeStartY = patternBtnY + 34;

        // Bottom buttons
        addRenderableWidget(new EEButton(width / 2 - 96, bottom - 25, 94, 20,
                Component.translatable("gui.ee.cancel"), b -> onCancel()));
        addRenderableWidget(new EEButton(width / 2 + 2, bottom - 25, 94, 20,
                Component.translatable("gui.done"), b -> {
                    set(stack);
                    mc.setScreen(parent);
                }));

        // List actions
        EEButton add = new EEButton(listX, top + 22, 56, 18, Component.literal("Add"), b -> {
            if (layers.size() >= MAX_PATTERNS) return;
            layers.add(defaultLayer());
            selectedIndex = layers.size() - 1;
            int footerH = layers.size() >= MAX_PATTERNS ? (font.lineHeight + 6) : 0;
            ensureSelectedVisible(listH - LIST_HEADER_H - footerH);
            applyLayersToStack();
            init();
        });
        add.active = layers.size() < MAX_PATTERNS;
        addRenderableWidget(add);

        EEButton remove = new EEButton(listX + 58, top + 22, 72, 18, Component.literal("Remove"), b -> {
            if (layers.isEmpty()) return;
            layers.remove(selectedIndex);
            selectedIndex = Math.min(selectedIndex, Math.max(0, layers.size() - 1));
            int footerH = layers.size() >= MAX_PATTERNS ? (font.lineHeight + 6) : 0;
            ensureSelectedVisible(listH - LIST_HEADER_H - footerH);
            applyLayersToStack();
            init();
        });
        remove.active = !layers.isEmpty();
        addRenderableWidget(remove);

        EEButton up = new EEButton(listX + 132, top + 22, 44, 18, Component.literal("Up"), b -> {
            if (selectedIndex <= 0 || selectedIndex >= layers.size()) return;
            BannerPatternLayers.Layer a = layers.get(selectedIndex - 1);
            layers.set(selectedIndex - 1, layers.get(selectedIndex));
            layers.set(selectedIndex, a);
            selectedIndex--;
            int footerH = layers.size() >= MAX_PATTERNS ? (font.lineHeight + 6) : 0;
            ensureSelectedVisible(listH - LIST_HEADER_H - footerH);
            applyLayersToStack();
            init();
        });
        up.active = selectedIndex > 0 && selectedIndex < layers.size();
        addRenderableWidget(up);

        EEButton down = new EEButton(listX + 178, top + 22, 54, 18, Component.literal("Down"), b -> {
            if (layers.isEmpty() || selectedIndex < 0 || selectedIndex >= layers.size() - 1) return;
            BannerPatternLayers.Layer a = layers.get(selectedIndex + 1);
            layers.set(selectedIndex + 1, layers.get(selectedIndex));
            layers.set(selectedIndex, a);
            selectedIndex++;
            int footerH = layers.size() >= MAX_PATTERNS ? (font.lineHeight + 6) : 0;
            ensureSelectedVisible(listH - LIST_HEADER_H - footerH);
            applyLayersToStack();
            init();
        });
        down.active = !layers.isEmpty() && selectedIndex >= 0 && selectedIndex < layers.size() - 1;
        addRenderableWidget(down);

        EEButton dup = new EEButton(listX, bottom - 48, 86, 18, Component.literal("Duplicate"), b -> {
            if (layers.size() >= MAX_PATTERNS) return;
            if (layers.isEmpty() || selectedIndex < 0 || selectedIndex >= layers.size()) return;
            layers.add(selectedIndex + 1, layers.get(selectedIndex));
            selectedIndex++;
            int footerH = layers.size() >= MAX_PATTERNS ? (font.lineHeight + 6) : 0;
            ensureSelectedVisible(listH - LIST_HEADER_H - footerH);
            applyLayersToStack();
            init();
        });
        dup.active = !layers.isEmpty() && layers.size() < MAX_PATTERNS;
        addRenderableWidget(dup);

        EEButton clear = new EEButton(listX + 88, bottom - 48, 64, 18, Component.literal("Clear"), b -> {
            layers.clear();
            selectedIndex = 0;
            scrollOffset = 0;
            applyLayersToStack();
            init();
        });
        clear.active = !layers.isEmpty();
        addRenderableWidget(clear);

        // Base color selector (kept left of the preview so text never overlaps)
        int controlBtnW = Math.min(controlsW, editorW);
        addRenderableWidget(new EEButton(editorX + editorPadX, baseBtnY, controlBtnW, 18,
                Component.literal(trimToWidth(baseColorLabel(), Math.max(0, controlBtnW - 8))), b -> {
                    applyLayersToStack();
                    openBaseColorSelector();
                }));

        // Selected layer editor
        EEButton patternBtn = new EEButton(editorX + editorPadX, patternBtnY, controlBtnW, 20,
                Component.literal(trimToWidth(patternLabel(), Math.max(0, controlBtnW - 8))), b -> openPatternSelector());
        patternBtn.active = !layers.isEmpty() && selectedIndex >= 0 && selectedIndex < layers.size();
        addRenderableWidget(patternBtn);

        // Dye color quick picker (16 dyes)
        int dyeStartX = editorX + editorPadX;
        for (int i = 0; i < DyeColor.values().length; i++) {
            DyeColor c = DyeColor.values()[i];
            int dx = dyeStartX + (i % dyeCols) * 20;
            int dy = dyeStartY + (i / dyeCols) * 20;
            addRenderableWidget(new ItemStackButtonWidget(dx, dy, new ItemStack(dyeItem(c)), b -> {
                if (!hasSelection()) return;
                BannerPatternLayers.Layer old = layers.get(selectedIndex);
                layers.set(selectedIndex, new BannerPatternLayers.Layer(old.pattern(), c));
                applyLayersToStack();
            }));
        }

        // Add preview tooltip hint not as widget; drawn in render.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);

        int left = MARGIN;
        int top = MARGIN;
        int right = width - MARGIN;
        int bottom = height - MARGIN;

        int listX = left + PADDING;
        int listY = top + 46;
        int panelsBottom = bottom - 56;
        int listH = Math.max(0, panelsBottom - listY);
        int listFooterH = layers.size() >= MAX_PATTERNS ? (font.lineHeight + 6) : 0;

        int editorX = listX + LIST_W + PADDING;
        int editorW = right - PADDING - editorX;
        int editorY = listY;
        int editorH = listH;
        int controlsW = Math.max(60, editorW - (PREVIEW_SIZE + 18));
        int dyeCols = Math.max(4, Math.min(8, controlsW / 20));
        int editorPadX = 6;
        int editorHeaderY = editorY + 4;
        int editorLineY = editorHeaderY + font.lineHeight + 2;
        int baseBtnY = editorLineY + font.lineHeight + 4;
        int patternBtnY = baseBtnY + 24;
        int dyeStartY = patternBtnY + 34;
        int dyeLabelY = dyeStartY - font.lineHeight - 2;

        // Outer panel
        GuiUtils.drawRect(graphics, left, top, right, bottom, GuiUtils.COLOR_CONTAINER_BORDER | 0xFF000000);
        GuiUtils.drawCenterString(graphics, font, getStringTitle(), width / 2, top + 8, 0xFF7F7F7F);

        // Panels
        GuiUtils.drawRect(graphics, listX - 2, listY - 2, listX + LIST_W + 2, listY + listH + 2,
                GuiUtils.COLOR_CONTAINER_BORDER | 0xFF000000);
        GuiUtils.drawRect(graphics, editorX - 2, editorY - 2, editorX + editorW + 2, editorY + editorH + 2,
                GuiUtils.COLOR_CONTAINER_BORDER | 0xFF000000);

        // List header (inside panel so it doesn't overlap action buttons)
        GuiUtils.drawString(graphics, font, "Layers", listX + 4, listY + 4, 0xFFB0B0B0, font.lineHeight);
        String limitText = I18n.get("gui.ee.banner.pattern_limit", layers.size(), MAX_PATTERNS);
        int limitColor = layers.size() >= MAX_PATTERNS ? 0xFFFF7777 : 0xFF7F7F7F;
        GuiUtils.drawString(graphics, font, limitText, listX + LIST_W - 4 - font.width(limitText), listY + 4,
                limitColor, font.lineHeight);

        // Rows
        int listContentY = listY + LIST_HEADER_H;
        int listContentH = Math.max(0, listH - LIST_HEADER_H - listFooterH);
        int visible = Math.max(1, listContentH / ROW_H);
        int maxScroll = Math.max(0, layers.size() - visible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (layers.isEmpty()) {
            GuiUtils.drawString(graphics, font, "(no patterns)", listX + 6, listContentY + 4, 0xFF7F7F7F, font.lineHeight);
        } else {
            for (int row = 0; row < visible; row++) {
                int idx = scrollOffset + row;
                if (idx >= layers.size()) break;
                int y = listContentY + row * ROW_H;
                boolean selected = idx == selectedIndex;

                int bg = selected ? 0xFF2A2A2A : 0xFF1A1A1A;
                int fg = selected ? 0xFFECECEC : 0xFFBFBFBF;

                GuiUtils.drawRect(graphics, listX, y, listX + LIST_W, y + ROW_H - 1, bg);

                BannerPatternLayers.Layer layer = layers.get(idx);
                ItemStack icon = patternIcon(layer.pattern(), layer.color());
                int iconX = listX + 4;
                int iconY = y + 1;
                GuiUtils.drawItemStack(graphics, icon, iconX, iconY);

                String label = (idx + 1) + ". " + layerLabel(layer);
                int labelX = listX + 4 + 20;
                label = trimToWidth(label, LIST_W - (labelX - listX) - 6);
                GuiUtils.drawString(graphics, font, label, labelX, y + 5, fg, font.lineHeight);
            }
        }

        // Limit warning under all layers (footer)
        if (listFooterH > 0) {
            int footerY = listY + listH - listFooterH;
            GuiUtils.drawRect(graphics, listX, footerY, listX + LIST_W, footerY + listFooterH, 0xFF151515);
            String warn = I18n.get("gui.ee.banner.pattern_limit.reached");
            GuiUtils.drawString(graphics, font, warn, listX + 4, footerY + (listFooterH - font.lineHeight) / 2,
                    0xFFFF7777, font.lineHeight);
        }

        // Editor header (inside panel)
        GuiUtils.drawString(graphics, font, "Editor", editorX + 4, editorHeaderY, 0xFFB0B0B0, font.lineHeight);

        if (hasSelection()) {
            BannerPatternLayers.Layer layer = layers.get(selectedIndex);
            String shown = layerLabel(layer);
            shown = trimToWidth(shown, Math.max(0, controlsW));
            GuiUtils.drawString(graphics, font, shown, editorX + editorPadX, editorLineY, 0xFFE0E0E0, font.lineHeight);

            // Pattern icon for the selected layer (left of "Color:")
            ItemStack icon = patternIcon(layer.pattern(), layer.color());
            int iconX = editorX + editorPadX;
            int iconY = dyeLabelY - 2;
            GuiUtils.drawItemStack(graphics, icon, iconX, iconY);
            GuiUtils.drawString(graphics, font, "Color:", iconX + 20, dyeLabelY, 0xFFB0B0B0, font.lineHeight);
        } else {
            GuiUtils.drawString(graphics, font, "Add a layer to edit", editorX + editorPadX, editorLineY, 0xFF7F7F7F, font.lineHeight);
        }

        // Preview
        int previewX = editorX + editorW - PREVIEW_SIZE - 8;
        int previewY = editorY + 18;
        GuiUtils.drawRect(graphics, previewX - 4, previewY - 4, previewX + PREVIEW_SIZE + 4, previewY + PREVIEW_SIZE + 4,
                0xFF0F0F0F);
        GuiUtils.drawRect(graphics, previewX - 5, previewY - 5, previewX + PREVIEW_SIZE + 5, previewY + PREVIEW_SIZE + 5,
                GuiUtils.COLOR_CONTAINER_BORDER | 0xFF000000);

        if (!stack.isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(previewX, previewY);
            graphics.pose().scale(PREVIEW_SCALE, PREVIEW_SCALE);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popMatrix();
        }

        // Dye highlight / tooltip
        if (hasSelection()) {
            DyeColor selectedColor = layers.get(selectedIndex).color();
            int dyeStartX = editorX + editorPadX;
            for (int i = 0; i < DyeColor.values().length; i++) {
                DyeColor c = DyeColor.values()[i];
                if (c != selectedColor) continue;
                int dx = dyeStartX + (i % dyeCols) * 20;
                int dy = dyeStartY + (i / dyeCols) * 20;
                GuiUtils.drawRect(graphics, dx - 1, dy - 1, dx + 19, dy + 19, 0x99FFFFFF);
                break;
            }
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int left = MARGIN;
        int top = MARGIN;
        int bottom = height - MARGIN;

        int listX = left + PADDING;
        int listY = top + 46;
        int panelsBottom = bottom - 56;
        int listH = Math.max(0, panelsBottom - listY);
        int listContentY = listY + LIST_HEADER_H;
        int listFooterH = layers.size() >= MAX_PATTERNS ? (font.lineHeight + 6) : 0;
        int listContentH = Math.max(0, listH - LIST_HEADER_H - listFooterH);
        int visible = Math.max(1, listContentH / ROW_H);

        if (GuiUtils.isHover(listX, listContentY, LIST_W, listContentH, (int) mouseX, (int) mouseY)) {
            int row = ((int) mouseY - listContentY) / ROW_H;
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < layers.size()) {
                selectedIndex = idx;
                playClick();
                init();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = MARGIN;
        int top = MARGIN;
        int bottom = height - MARGIN;

        int listX = left + PADDING;
        int listY = top + 46;
        int panelsBottom = bottom - 56;
        int listH = Math.max(0, panelsBottom - listY);
        int listContentY = listY + LIST_HEADER_H;
        int listFooterH = layers.size() >= MAX_PATTERNS ? (font.lineHeight + 6) : 0;
        int listContentH = Math.max(0, listH - LIST_HEADER_H - listFooterH);
        int visible = Math.max(1, listContentH / ROW_H);

        if (GuiUtils.isHover(listX, listContentY, LIST_W, listContentH, (int) mouseX, (int) mouseY)) {
            int maxScroll = Math.max(0, layers.size() - visible);
            if (verticalAmount > 0) scrollOffset = Math.max(0, scrollOffset - 1);
            if (verticalAmount < 0) scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void loadFromStack() {
        layers.clear();
        if (stack == null || stack.isEmpty()) return;
        BannerPatternLayers current = ItemUtils.getComponent(stack, DataComponents.BANNER_PATTERNS);
        if (current == null) return;
        layers.addAll(current.layers());
    }

    private void applyLayersToStack() {
        if (stack == null || stack.isEmpty()) return;
        if (layers.isEmpty()) {
            stack.remove(DataComponents.BANNER_PATTERNS);
            return;
        }
        ItemUtils.setComponent(stack, DataComponents.BANNER_PATTERNS, new BannerPatternLayers(List.copyOf(layers)));
    }

    private void clampSelection() {
        if (selectedIndex < 0) selectedIndex = 0;
        if (selectedIndex >= layers.size()) selectedIndex = Math.max(0, layers.size() - 1);
        if (scrollOffset < 0) scrollOffset = 0;
    }

    private boolean hasSelection() {
        return selectedIndex >= 0 && selectedIndex < layers.size();
    }

    private void ensureSelectedVisible(int listH) {
        int visible = Math.max(1, listH / ROW_H);
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
        if (selectedIndex >= scrollOffset + visible) scrollOffset = selectedIndex - visible + 1;
        scrollOffset = Math.max(0, scrollOffset);
    }

    private String baseColorLabel() {
        DyeColor base = getBaseColor();
        if (stack.getItem() instanceof ShieldItem) {
            if (base == null) return "Base: " + I18n.get("gui.ee.none");
            return "Base: " + dyeName(base);
        }
        if (base == null) return "Base: ?";
        return "Base: " + dyeName(base);
    }

    private DyeColor getBaseColor() {
        if (stack.getItem() instanceof BannerItem bi) {
            return bi.getColor();
        }
        return ItemUtils.getComponent(stack, DataComponents.BASE_COLOR);
    }

    private void openBaseColorSelector() {
        List<Tuple<String, DyeColor>> entries = new ArrayList<>();
        boolean allowNone = stack.getItem() instanceof ShieldItem;
        if (allowNone) {
            entries.add(new Tuple<>(I18n.get("gui.ee.none"), null));
        }
        for (DyeColor c : DyeColor.values()) {
            entries.add(new Tuple<>(dyeName(c), c));
        }
        mc.setScreen(new GuiButtonListSelector<>(this, Component.literal("Base Color"), entries, color -> {
            applyLayersToStack();
            setBaseColor(color);
            return null;
        }));
    }

    private void setBaseColor(DyeColor color) {
        if (stack.isEmpty()) return;
        if (stack.getItem() instanceof BannerItem) {
            if (color == null) return;
            stack = ItemUtils.setItem(bannerItem(color), stack);
            return;
        }
        if (stack.getItem() instanceof ShieldItem) {
            if (color == null) {
                stack.remove(DataComponents.BASE_COLOR);
            } else {
                ItemUtils.setComponent(stack, DataComponents.BASE_COLOR, color);
            }
        }
    }

    private void openPatternSelector() {
        if (!hasSelection()) return;

        Registry<BannerPattern> registry = bannerPatternRegistry();
        Holder<BannerPattern> current = layers.get(selectedIndex).pattern();
        mc.setScreen(new GuiBannerPatternSelector(this, Component.literal("Pattern"), registry, current, holder -> {
            if (holder == null) return;
            BannerPatternLayers.Layer old = layers.get(selectedIndex);
            layers.set(selectedIndex, new BannerPatternLayers.Layer(holder, old.color()));
            applyLayersToStack();
        }));
    }

    private BannerPatternLayers.Layer defaultLayer() {
        Registry<BannerPattern> registry = bannerPatternRegistry();
        BannerPattern picked = registry.getOptional(BannerPatterns.STRIPE_CENTER)
                .orElseGet(() -> registry.getAny()
                        .filter(h -> !h.is(BannerPatterns.BASE))
                        .map(Holder::value)
                        .orElse(null));
        if (picked == null) {
            picked = new BannerPattern(Identifier.fromNamespaceAndPath("minecraft", "stripe_center"),
                    "block.minecraft.banner.stripe_center");
            return new BannerPatternLayers.Layer(Holder.direct(picked), DyeColor.WHITE);
        }
        Holder<BannerPattern> holder = registry.wrapAsHolder(picked);
        return new BannerPatternLayers.Layer(holder, DyeColor.WHITE);
    }

    private Registry<BannerPattern> bannerPatternRegistry() {
        if (mc.level != null) return mc.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        if (mc.getConnection() != null) return mc.getConnection().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        throw new IllegalStateException("No registry access for banner patterns");
    }

    private String patternLabel() {
        if (!hasSelection()) return "Pattern: (none)";
        Holder<BannerPattern> h = layers.get(selectedIndex).pattern();
        return "Pattern: " + patternName(h);
    }

    private static String layerLabel(BannerPatternLayers.Layer layer) {
        if (layer == null) return "?";
        return patternDisplayName(layer.pattern(), layer.color());
    }

    private static String patternDisplayName(Holder<BannerPattern> pattern, DyeColor color) {
        if (pattern == null || color == null) return "?";
        try {
            String baseKey = pattern.value().translationKey(); // e.g. "block.minecraft.banner.flower"
            String keyWithColor = baseKey + "." + color.getName(); // e.g. "...flower.black"
            String localized = I18n.get(keyWithColor);
            if (localized != null && !localized.isBlank() && !Objects.equals(localized, keyWithColor)) {
                return localized;
            }

            // Fallback for special patterns: item desc key, e.g. "item.minecraft.flower_banner_pattern.desc"
            String suffix = baseKey.startsWith("block.minecraft.banner.") ? baseKey.substring("block.minecraft.banner.".length()) : null;
            if (suffix != null && !suffix.isBlank()) {
                String descKey = "item.minecraft." + suffix + "_banner_pattern.desc";
                String desc = I18n.get(descKey);
                if (desc != null && !desc.isBlank() && !Objects.equals(desc, descKey)) {
                    // Include color name so layers are distinguishable in the list
                    return dyeName(color) + " " + desc;
                }
            }

            // Last resort: show key suffix
            return suffix != null ? (dyeName(color) + " " + suffix) : keyWithColor;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static ItemStack patternIcon(Holder<BannerPattern> pattern, DyeColor color) {
        ItemStack stack = new ItemStack(Items.WHITE_BANNER);
        if (pattern == null || color == null) return stack;
        ItemUtils.setComponent(stack, DataComponents.BANNER_PATTERNS, new BannerPatternLayers(List.of(
                new BannerPatternLayers.Layer(pattern, color)
        )));
        stack.set(DataComponents.CUSTOM_NAME, null);
        return stack;
    }

    private static String dyeName(DyeColor color) {
        if (color == null) return I18n.get("gui.ee.none");
        return I18n.get("color.minecraft." + color.getName());
    }

    private static String patternName(Holder<BannerPattern> pattern) {
        if (pattern == null) return "?";
        try {
            // Prefer the banner-pattern item description (e.g. "Flower Charge") when it exists.
            String baseKey = pattern.value().translationKey(); // e.g. "block.minecraft.banner.flower"
            String suffix = baseKey.startsWith("block.minecraft.banner.")
                    ? baseKey.substring("block.minecraft.banner.".length())
                    : null;

            if (suffix != null && !suffix.isBlank()) {
                String descKey = "item.minecraft." + suffix + "_banner_pattern.desc";
                String desc = I18n.get(descKey);
                if (desc != null && !desc.isBlank() && !Objects.equals(desc, descKey)) {
                    return desc;
                }
            }

            // Fallback: use the white variant of the block key (exists for all patterns) and strip the color prefix
            // when possible so the editor shows just the pattern name.
            String whiteKey = baseKey + ".white";
            String white = I18n.get(whiteKey);
            if (white != null && !white.isBlank() && !Objects.equals(white, whiteKey)) {
                String whiteColorName = I18n.get("color.minecraft.white");
                if (whiteColorName != null && !whiteColorName.isBlank()) {
                    String prefix = whiteColorName + " ";
                    if (white.startsWith(prefix)) {
                        String stripped = white.substring(prefix.length()).trim();
                        if (!stripped.isEmpty()) {
                            return stripped;
                        }
                    }
                }
                return white;
            }

            // Last resort: any translation for the base key (or the raw key)
            String name = I18n.get(baseKey);
            if (name != null && !name.isBlank() && !Objects.equals(name, baseKey)) {
                return name;
            }
            return suffix != null && !suffix.isBlank() ? suffix : baseKey;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static Item dyeItem(DyeColor color) {
        return switch (Objects.requireNonNull(color)) {
            case WHITE -> Items.WHITE_DYE;
            case ORANGE -> Items.ORANGE_DYE;
            case MAGENTA -> Items.MAGENTA_DYE;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_DYE;
            case YELLOW -> Items.YELLOW_DYE;
            case LIME -> Items.LIME_DYE;
            case PINK -> Items.PINK_DYE;
            case GRAY -> Items.GRAY_DYE;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_DYE;
            case CYAN -> Items.CYAN_DYE;
            case PURPLE -> Items.PURPLE_DYE;
            case BLUE -> Items.BLUE_DYE;
            case BROWN -> Items.BROWN_DYE;
            case GREEN -> Items.GREEN_DYE;
            case RED -> Items.RED_DYE;
            case BLACK -> Items.BLACK_DYE;
        };
    }

    private static Item bannerItem(DyeColor color) {
        return switch (Objects.requireNonNull(color)) {
            case WHITE -> Items.WHITE_BANNER;
            case ORANGE -> Items.ORANGE_BANNER;
            case MAGENTA -> Items.MAGENTA_BANNER;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_BANNER;
            case YELLOW -> Items.YELLOW_BANNER;
            case LIME -> Items.LIME_BANNER;
            case PINK -> Items.PINK_BANNER;
            case GRAY -> Items.GRAY_BANNER;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_BANNER;
            case CYAN -> Items.CYAN_BANNER;
            case PURPLE -> Items.PURPLE_BANNER;
            case BLUE -> Items.BLUE_BANNER;
            case BROWN -> Items.BROWN_BANNER;
            case GREEN -> Items.GREEN_BANNER;
            case RED -> Items.RED_BANNER;
            case BLACK -> Items.BLACK_BANNER;
        };
    }

    private String trimToWidth(String s, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(s) <= maxWidth) return s;
        int dots = font.width("...");
        return font.plainSubstrByWidth(s, Math.max(0, maxWidth - dots)) + "...";
    }
}
