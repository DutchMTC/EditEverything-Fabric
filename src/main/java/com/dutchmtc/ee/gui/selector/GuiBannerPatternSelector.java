package com.dutchmtc.ee.gui.selector;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiModifier;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Visual banner-pattern selector (grid of icons) instead of a text-only list.
 */
public class GuiBannerPatternSelector extends GuiModifier<Holder<BannerPattern>> {
    private static final int MARGIN = 10;
    private static final int HEADER_H = 42;
    private static final int FOOTER_H = 34;

    private static final int ICON_SCALE = 2;
    private static final int ICON_PX = 16 * ICON_SCALE;
    private static final int CELL = ICON_PX + 8; // padding + border

    private final Holder<BannerPattern> current;
    private final Registry<BannerPattern> registry;
    private final Consumer<Holder<BannerPattern>> onSelect;

    private EditBox search;
    private String lastQuery = "";
    private int scrollRow = 0;

    private final List<Holder<BannerPattern>> all = new ArrayList<>();
    private final List<Holder<BannerPattern>> filtered = new ArrayList<>();

    public GuiBannerPatternSelector(Screen parent, Component title, Registry<BannerPattern> registry,
                                    Holder<BannerPattern> current, Consumer<Holder<BannerPattern>> onSelect) {
        super(parent, title, h -> {
        });
        this.registry = registry;
        this.current = current;
        this.onSelect = onSelect;

        registry.stream()
                .map(registry::wrapAsHolder)
                .filter(h -> !h.is(BannerPatterns.BASE))
                .forEach(all::add);
        all.sort(Comparator.comparing(h -> patternName(h).toLowerCase(Locale.ROOT)));
        filtered.addAll(all);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // do nothing
    }

    @Override
    protected void init() {
        clearWidgets();
        super.init();

        int bottom = height - MARGIN;

        search = new EditBox(font, MARGIN + 70, MARGIN + 18, Math.max(120, width - (MARGIN * 2 + 80)), 16,
                Component.literal(""));
        search.setMaxLength(256);
        search.setValue(lastQuery);
        addRenderableWidget(search);

        addRenderableWidget(new EEButton(width / 2 - 60, bottom - 24, 120, 20,
                Component.translatable("gui.ee.cancel"), b -> mc.gui.setScreen(parent)));
    }

    @Override
    public void tick() {
        super.tick();
        if (search == null) return;
        String q = search.getValue();
        if (!Objects.equals(q, lastQuery)) {
            lastQuery = q;
            refilter();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);

        int left = MARGIN;
        int top = MARGIN;
        int right = width - MARGIN;
        int bottom = height - MARGIN;

        GuiUtils.drawRect(graphics, left, top, right, bottom, GuiUtils.COLOR_CONTAINER_BORDER | 0xFF000000);
        GuiUtils.drawCenterString(graphics, font, getStringTitle(), width / 2, top + 6, 0xFFFFFFFF);
        GuiUtils.drawRightString(graphics, font, I18n.get("gui.ee.search") + " : ", search.getX(), search.getY(),
                0xFFFFA000, search.getHeight());

        int gridTop = top + HEADER_H;
        int gridBottom = bottom - FOOTER_H;

        int gridW = right - left;
        int gridH = Math.max(0, gridBottom - gridTop);
        int cols = Math.max(1, gridW / CELL);
        int rowsVisible = Math.max(1, gridH / CELL);

        int totalRows = (filtered.size() + cols - 1) / cols;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows - rowsVisible)));

        int startIndex = scrollRow * cols;
        int endIndex = Math.min(filtered.size(), startIndex + rowsVisible * cols);

        Holder<BannerPattern> hover = null;
        for (int idx = startIndex; idx < endIndex; idx++) {
            int rel = idx - startIndex;
            int c = rel % cols;
            int r = rel / cols;
            int x = left + c * CELL;
            int y = gridTop + r * CELL;

            Holder<BannerPattern> pattern = filtered.get(idx);
            boolean isCurrent = current != null && current.is(pattern);

            int bg = isCurrent ? 0xFF2A2A2A : 0xFF151515;
            GuiUtils.drawRect(graphics, x, y, x + CELL - 2, y + CELL - 2, bg);

            ItemStack icon = iconFor(pattern);
            int iconX = x + (CELL - ICON_PX) / 2;
            int iconY = y + (CELL - ICON_PX) / 2;
            graphics.pose().pushMatrix();
            graphics.pose().translate(iconX, iconY);
            graphics.pose().scale(ICON_SCALE, ICON_SCALE);
            graphics.item(icon, 0, 0);
            graphics.pose().popMatrix();

            boolean hovered = GuiUtils.isHover(x, y, CELL - 2, CELL - 2, mouseX, mouseY);
            if (hovered) {
                GuiUtils.drawRect(graphics, x, y, x + CELL - 2, y + CELL - 2, 0x55FFFFFF);
                hover = pattern;
            } else if (isCurrent) {
                GuiUtils.drawRect(graphics, x, y, x + CELL - 2, y + CELL - 2, 0x33FFFFFF);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (hover != null) {
            List<Component> lines = List.of(
                    Component.literal(patternName(hover)),
                    Component.literal(patternId(hover)).withStyle(net.minecraft.ChatFormatting.GRAY));
            GuiUtils.setTooltipForNextFrame(graphics, font, lines, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;

        if (event.button() != 0) return false;

        int left = MARGIN;
        int top = MARGIN;
        int right = width - MARGIN;
        int bottom = height - MARGIN;

        int gridTop = top + HEADER_H;
        int gridBottom = bottom - FOOTER_H;

        int gridW = right - left;
        int gridH = Math.max(0, gridBottom - gridTop);
        int cols = Math.max(1, gridW / CELL);
        int rowsVisible = Math.max(1, gridH / CELL);

        int totalRows = (filtered.size() + cols - 1) / cols;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows - rowsVisible)));

        int startIndex = scrollRow * cols;
        int endIndex = Math.min(filtered.size(), startIndex + rowsVisible * cols);

        int mx = (int) event.x();
        int my = (int) event.y();

        if (!GuiUtils.isHover(left, gridTop, gridW, gridH, mx, my)) return false;

        int c = (mx - left) / CELL;
        int r = (my - gridTop) / CELL;
        int idx = startIndex + r * cols + c;
        if (idx < startIndex || idx >= endIndex || idx < 0 || idx >= filtered.size()) return false;

        Holder<BannerPattern> picked = filtered.get(idx);
        playClick();
        if (onSelect != null) onSelect.accept(picked);
        mc.gui.setScreen(parent);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = MARGIN;
        int top = MARGIN;
        int right = width - MARGIN;
        int bottom = height - MARGIN;

        int gridTop = top + HEADER_H;
        int gridBottom = bottom - FOOTER_H;
        int gridW = right - left;
        int gridH = Math.max(0, gridBottom - gridTop);

        if (!GuiUtils.isHover(left, gridTop, gridW, gridH, (int) mouseX, (int) mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int cols = Math.max(1, gridW / CELL);
        int rowsVisible = Math.max(1, gridH / CELL);
        int totalRows = (filtered.size() + cols - 1) / cols;
        int maxScroll = Math.max(0, totalRows - rowsVisible);

        if (verticalAmount > 0) scrollRow = Math.max(0, scrollRow - 1);
        if (verticalAmount < 0) scrollRow = Math.min(maxScroll, scrollRow + 1);
        return true;
    }

    private void refilter() {
        String q = lastQuery == null ? "" : lastQuery.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        if (q.isEmpty()) {
            filtered.addAll(all);
        } else {
            for (Holder<BannerPattern> h : all) {
                String name = patternName(h).toLowerCase(Locale.ROOT);
                String id = patternId(h).toLowerCase(Locale.ROOT);
                if (name.contains(q) || id.contains(q)) {
                    filtered.add(h);
                }
            }
        }
        scrollRow = 0;
    }

    private ItemStack iconFor(Holder<BannerPattern> pattern) {
        ItemStack stack = new ItemStack(Items.BANNER.white());
        ItemUtils.setComponent(stack, DataComponents.BANNER_PATTERNS, new BannerPatternLayers(List.of(
                new BannerPatternLayers.Layer(pattern, DyeColor.BLACK)
        )));
        stack.set(DataComponents.CUSTOM_NAME, null);
        return stack;
    }

    private static String patternName(Holder<BannerPattern> pattern) {
        if (pattern == null) return "?";
        try {
            // Prefer the banner-pattern item description (e.g. "Flower Charge") when it exists.
            String baseKey = pattern.value().translationKey(); // e.g. "block.minecraft.banner.flower"
            String suffix = baseKey.startsWith("block.minecraft.banner.") ? baseKey.substring("block.minecraft.banner.".length()) : null;
            if (suffix != null && !suffix.isBlank()) {
                String descKey = "item.minecraft." + suffix + "_banner_pattern.desc";
                String desc = I18n.get(descKey);
                if (desc != null && !desc.isBlank() && !Objects.equals(desc, descKey)) {
                    return desc;
                }

                // Fallback: use the white variant of the block key (exists for all patterns)
                String whiteKey = baseKey + ".white";
                String white = I18n.get(whiteKey);
                if (white != null && !white.isBlank() && !Objects.equals(white, whiteKey)) {
                    return white;
                }
            }

            return baseKey;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String patternId(Holder<BannerPattern> pattern) {
        if (pattern == null) return "?";
        try {
            Identifier id = pattern.unwrapKey().map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
            return id != null ? id.toString() : pattern.value().assetId().toString();
        } catch (Throwable t) {
            return "?";
        }
    }
}
