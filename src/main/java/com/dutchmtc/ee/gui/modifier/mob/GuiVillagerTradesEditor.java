package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.DynamicItemStackButtonWidget;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiBooleanButton;
import com.dutchmtc.ee.gui.modifier.GuiModifier;
import com.dutchmtc.ee.gui.selector.GuiTypeListSelector;
import com.dutchmtc.ee.mobdata.VillagerTradesRepository;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Clean, single-screen villager trades editor for Offers.Recipes.
 * Left: trade list. Right: selected trade editor with full labels (no abbreviations).
 */
public class GuiVillagerTradesEditor extends GuiModifier<List<CompoundTag>> {
    private static final int MARGIN = 10;
    private static final int PADDING = 10;
    private static final int HEADER_H = 36;

    private static final int ROW_H = 22;

    private final List<CompoundTag> originalRecipes;
    private final List<CompoundTag> recipes;

    private int selectedIndex = 0;
    private int scrollOffset = 0;

    // Editor widgets
    private DynamicItemStackButtonWidget buyAIcon;
    private DynamicItemStackButtonWidget buyBIcon;
    private DynamicItemStackButtonWidget sellIcon;

    private EditBox buyACount;
    private EditBox buyBCount;
    private EditBox sellCount;

    private EditBox maxUses;
    private EditBox priceMultiplier;
    private EditBox demand;
    private EditBox specialPrice;

    private boolean rewardExp;
    private GuiBooleanButton rewardExpToggle;

    private GuiBooleanButton hasBuyBToggle;

    private int editorTextLeft;
    private int countBoxX;
    private int editorScroll = 0;
    private int editorContentTop = 0;
    private int editorContentBottom = 0;
    private int editorViewTop = 0;
    private int editorViewBottom = 0;
    private final List<AbstractWidget> editorWidgets = new ArrayList<>();
    private final List<Integer> editorWidgetBaseY = new ArrayList<>();

    public GuiVillagerTradesEditor(Screen parent, List<CompoundTag> recipes, Consumer<List<CompoundTag>> setter) {
        super(parent, Component.literal("Trades"), setter);
        this.originalRecipes = normalize(recipes);
        this.recipes = normalize(recipes);
    }

    @Override
    public boolean isModified() {
        if (recipes.size() != originalRecipes.size()) return true;
        for (int i = 0; i < recipes.size(); i++) {
            if (!recipes.get(i).equals(originalRecipes.get(i))) return true;
        }
        return false;
    }

    @Override
    protected void init() {
        clearWidgets();
        super.init();

        if (selectedIndex < 0) selectedIndex = 0;
        if (selectedIndex >= recipes.size()) selectedIndex = Math.max(0, recipes.size() - 1);

        int left = panelLeft();
        int top = panelTop();
        int bottom = panelBottom();

        // Bottom buttons
        addRenderableWidget(new EEButton(width / 2 - 96, bottom - 25, 94, 20,
                Component.translatable("gui.ee.cancel"), b -> onCancel()));
        addRenderableWidget(new EEButton(width / 2 + 2, bottom - 25, 94, 20,
                Component.translatable("gui.done"), b -> {
                    applyEditorToSelected();
                    set(get());
                    mc.gui.setScreen(parent);
                }));

        // List actions
        addRenderableWidget(new EEButton(left + PADDING, top + HEADER_H, 62, 18,
                Component.literal("Add"), b -> {
                    applyEditorToSelected();
                    recipes.add(defaultRecipe());
                    selectedIndex = recipes.size() - 1;
                    ensureSelectedVisible();
                    syncEditorFromSelected();
                }));

        addRenderableWidget(new EEButton(left + PADDING + 64, top + HEADER_H, 70, 18,
                Component.literal("Remove"), b -> {
                    if (recipes.isEmpty()) return;
                    recipes.remove(selectedIndex);
                    selectedIndex = Math.min(selectedIndex, Math.max(0, recipes.size() - 1));
                    ensureSelectedVisible();
                    syncEditorFromSelected();
                }));

        addRenderableWidget(new EEButton(left + PADDING + 136, top + HEADER_H, 62, 18,
                Component.literal("Copy"), b -> {
                    if (recipes.isEmpty()) return;
                    applyEditorToSelected();
                    recipes.add(selectedIndex + 1, recipes.get(selectedIndex).copy());
                    selectedIndex = selectedIndex + 1;
                    ensureSelectedVisible();
                    syncEditorFromSelected();
                }));

        // Right editor panel widgets
        int editorLeft = editorLeft();
        int editorTop = top + HEADER_H;
        initEditorWidgets(editorLeft, editorTop);

        syncEditorFromSelected();
        updateEditorWidgetLayout();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);

        int left = panelLeft();
        int top = panelTop();
        int right = panelRight();
        int bottom = panelBottom();

        // Outer panel
        GuiUtils.drawRect(graphics, left, top, right, bottom, GuiUtils.COLOR_CONTAINER_BORDER | 0xFF000000);
        GuiUtils.drawCenterString(graphics, font, "Villager Trades", width / 2, top + 6, 0xFFFFFFFF);

        // Vertical split
        int listLeft = left + PADDING;
        int listTop = top + HEADER_H + 22;
        int listRight = listLeft + listWidth();
        int listBottom = bottom - 36;

        GuiUtils.drawRect(graphics, listLeft - 2, listTop - 2, listRight + 2, listBottom + 2,
                GuiUtils.COLOR_CONTAINER_SLOT | 0xFF000000);

        GuiUtils.text(graphics, font, "Trades", listLeft, listTop - 12, 0xFFFFFFFF, font.lineHeight);

        renderTradeList(graphics, listLeft, listTop, listRight, listBottom, mouseX, mouseY);

        // Editor heading
        int editorLeft = editorLeft();
        GuiUtils.text(graphics, font, "Selected Trade", editorLeft, top + HEADER_H + 2, 0xFFFFFFFF, font.lineHeight);

        // Item row labels (text-only; avoids "disabled widget" hover cursor)
        if (buyAIcon != null && buyAIcon.visible) {
            GuiUtils.text(graphics, font, "Buy (A)", editorTextLeft, buyAIcon.getY() + 5, 0xFFFFFFFF, font.lineHeight);
        }
        if (hasBuyBToggle != null) {
            // label is inside the toggle widget; no extra label
        }
        if (buyBIcon != null && buyBIcon.visible) {
            GuiUtils.text(graphics, font, "Second cost item", editorTextLeft, buyBIcon.getY() + 5, 0xFFFFFFFF, font.lineHeight);
        }
        if (sellIcon != null && sellIcon.visible) {
            GuiUtils.text(graphics, font, "Sell", editorTextLeft, sellIcon.getY() + 5, 0xFFFFFFFF, font.lineHeight);
        }

        if (maxUses != null && maxUses.visible) {
            GuiUtils.text(graphics, font, "Max Uses", editorLeft, maxUses.getY() + 5, 0xFFFFFFFF, font.lineHeight);
        }
        if (priceMultiplier != null && priceMultiplier.visible) {
            GuiUtils.text(graphics, font, "Price Multiplier", editorLeft, priceMultiplier.getY() + 5, 0xFFFFFFFF, font.lineHeight);
        }
        if (demand != null && demand.visible) {
            GuiUtils.text(graphics, font, "Demand", editorLeft, demand.getY() + 5, 0xFFFFFFFF, font.lineHeight);
        }
        if (specialPrice != null && specialPrice.visible) {
            GuiUtils.text(graphics, font, "Special Price", editorLeft, specialPrice.getY() + 5, 0xFFFFFFFF, font.lineHeight);
        }

        renderEditorScrollHint(graphics, editorLeft, top, bottom);

        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;

        int left = panelLeft();
        int top = panelTop();
        int bottom = panelBottom();
        int listLeft = left + PADDING;
        int listTop = top + HEADER_H + 22;
        int listRight = listLeft + listWidth();
        int listBottom = bottom - 36;

        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        if (GuiUtils.isHover(listLeft, listTop, listRight - listLeft, listBottom - listTop, mouseX, mouseY)) {
            int row = (mouseY - listTop) / ROW_H;
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < recipes.size()) {
                applyEditorToSelected();
                clearEditorFocus();
                selectedIndex = idx;
                syncEditorFromSelected();
                return true;
            }
        }

        // Consume click so it doesn't interact with the world (which can close the screen).
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = panelLeft();
        int top = panelTop();
        int bottom = panelBottom();
        int listLeft = left + PADDING;
        int listTop = top + HEADER_H + 22;
        int listRight = listLeft + listWidth();
        int listBottom = bottom - 36;

        if (GuiUtils.isHover(listLeft, listTop, listRight - listLeft, listBottom - listTop, (int) mouseX, (int) mouseY)) {
            int visible = visibleRows(listTop, listBottom);
            int maxOffset = Math.max(0, recipes.size() - visible);
            if (verticalAmount < 0) scrollOffset = Math.min(maxOffset, scrollOffset + 1);
            else if (verticalAmount > 0) scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }

        int editorLeft = editorLeft();
        int editorRight = panelRight() - PADDING;
        if (GuiUtils.isHover(editorLeft, editorViewTop, editorRight - editorLeft, editorViewBottom - editorViewTop,
                (int) mouseX, (int) mouseY)) {
            int maxScroll = editorMaxScroll();
            if (maxScroll <= 0) {
                return true;
            }
            if (verticalAmount < 0) {
                editorScroll = Math.min(maxScroll, editorScroll + 16);
            } else if (verticalAmount > 0) {
                editorScroll = Math.max(0, editorScroll - 16);
            }
            updateEditorWidgetLayout();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void renderTradeList(GuiGraphicsExtractor graphics, int listLeft, int listTop, int listRight, int listBottom, int mouseX, int mouseY) {
        int visible = visibleRows(listTop, listBottom);
        int maxOffset = Math.max(0, recipes.size() - visible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

        if (recipes.isEmpty()) {
            GuiUtils.text(graphics, font, "(no trades)", listLeft + 6, listTop + 6, 0xFFAAAAAA, font.lineHeight);
            return;
        }

        for (int row = 0; row < visible; row++) {
            int idx = scrollOffset + row;
            if (idx >= recipes.size()) break;

            int y = listTop + row * ROW_H;
            boolean selected = idx == selectedIndex;
            int bg = selected ? 0x5533AAFF : 0x22000000;
            GuiUtils.drawRect(graphics, listLeft, y, listRight, y + ROW_H - 1, bg);

            CompoundTag recipe = recipes.get(idx);
            ItemStack buy = toDisplayStack(recipe.getCompound("buy").orElse(new CompoundTag()));
            ItemStack sell = toDisplayStack(recipe.getCompound("sell").orElse(new CompoundTag()));

            int iconY = y + 2;
            int buyX = listLeft + 4;
            int sellX = listLeft + 24;
            if (!buy.isEmpty()) {
                graphics.item(buy, buyX, iconY);
            }
            if (!sell.isEmpty()) {
                graphics.item(sell, sellX, iconY);
            }

            String label = "Trade " + (idx + 1) + ": " + itemLabel(recipe.getCompound("buy").orElse(new CompoundTag()))
                    + " \u2192 " + itemLabel(recipe.getCompound("sell").orElse(new CompoundTag()));
            GuiUtils.text(graphics, font, trimToWidth(label, listRight - (listLeft + 44) - 6),
                    listLeft + 44, y + 7, 0xFFFFFFFF, font.lineHeight);
        }

        // Scroll hint
        if (recipes.size() > visible) {
            GuiUtils.text(graphics, font, (scrollOffset + 1) + "-" + Math.min(recipes.size(), scrollOffset + visible) + " / " + recipes.size(),
                    listLeft + 6, listBottom + 4, 0xFF7F7F7F, font.lineHeight);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    private void initEditorWidgets(int editorLeft, int editorTop) {
        editorWidgets.clear();
        editorWidgetBaseY.clear();

        int x = editorLeft;
        int y = editorTop + 22;
        int editorRight = panelRight() - PADDING;
        int wValue = Math.max(90, editorRight - (x + 120));
        editorTextLeft = x + 24;
        countBoxX = editorRight - 60;

        // Items
        buyAIcon = new DynamicItemStackButtonWidget(x, y + 1, ItemStack.EMPTY, b -> pickItemFor("buy"));
        addRenderableWidget(buyAIcon);
        registerEditorWidget(buyAIcon);
        buyACount = new EditBox(font, countBoxX, y, 60, 18, Component.literal("Buy A Count"));
        addRenderableWidget(buyACount);
        registerEditorWidget(buyACount);
        y += 24;

        hasBuyBToggle = new GuiBooleanButton(x, y, Math.min(260, editorRight - x), 18,
                Component.literal("Second cost item (optional)"),
                val -> {
                    setHasBuyB(val);
                    syncEditorFromSelected();
                },
                () -> getHasBuyB());
        hasBuyBToggle.setTooltip(Tooltip.create(Component.literal("Enable a second buy item for this trade (optional).")));
        addRenderableWidget(hasBuyBToggle);
        registerEditorWidget(hasBuyBToggle);
        y += 24;

        buyBIcon = new DynamicItemStackButtonWidget(x, y + 1, ItemStack.EMPTY, b -> pickItemFor("buyB"));
        addRenderableWidget(buyBIcon);
        registerEditorWidget(buyBIcon);
        buyBCount = new EditBox(font, countBoxX, y, 60, 18, Component.literal("Buy B Count"));
        addRenderableWidget(buyBCount);
        registerEditorWidget(buyBCount);
        y += 24;

        sellIcon = new DynamicItemStackButtonWidget(x, y + 1, ItemStack.EMPTY, b -> pickItemFor("sell"));
        addRenderableWidget(sellIcon);
        registerEditorWidget(sellIcon);
        sellCount = new EditBox(font, countBoxX, y, 60, 18, Component.literal("Sell Count"));
        addRenderableWidget(sellCount);
        registerEditorWidget(sellCount);
        y += 28;

        // Trade fields
        maxUses = new EditBox(font, x + 112, y, wValue, 18, Component.literal("Max Uses"));
        addRenderableWidget(maxUses);
        registerEditorWidget(maxUses);
        y += 22;

        rewardExp = true;
        rewardExpToggle = new GuiBooleanButton(x, y, 220, 18,
                Component.literal("Reward Experience"),
                val -> rewardExp = val,
                () -> rewardExp);
        rewardExpToggle.setTooltip(Tooltip.create(Component.literal(tooltipFor("rewardExp") != null ? tooltipFor("rewardExp") : "")));
        addRenderableWidget(rewardExpToggle);
        registerEditorWidget(rewardExpToggle);
        y += 22;

        priceMultiplier = new EditBox(font, x + 112, y, wValue, 18, Component.literal("Price Multiplier"));
        addRenderableWidget(priceMultiplier);
        registerEditorWidget(priceMultiplier);
        y += 22;

        demand = new EditBox(font, x + 112, y, wValue, 18, Component.literal("Demand"));
        addRenderableWidget(demand);
        registerEditorWidget(demand);
        y += 22;

        specialPrice = new EditBox(font, x + 112, y, wValue, 18, Component.literal("Special Price"));
        addRenderableWidget(specialPrice);
        registerEditorWidget(specialPrice);

        editorContentTop = editorTop + 22;
        editorContentBottom = y + 18;
        editorViewTop = panelTop() + HEADER_H + 22;
        editorViewBottom = panelBottom() - 36;
    }

    private int panelLeft() { return MARGIN; }
    private int panelTop() { return MARGIN; }
    private int panelRight() { return width - MARGIN; }
    private int panelBottom() { return height - MARGIN; }

    private int listWidth() {
        // Responsive list width: roughly 1/3 screen, clamped.
        int available = panelRight() - panelLeft() - (PADDING * 3);
        int preferred = available / 3;
        return Math.max(220, Math.min(360, preferred));
    }

    private int editorLeft() {
        return panelLeft() + PADDING + listWidth() + PADDING;
    }

    private void registerEditorWidget(AbstractWidget widget) {
        editorWidgets.add(widget);
        editorWidgetBaseY.add(widget.getY());
    }

    private boolean canShowEditorWidget(AbstractWidget widget) {
        if (widget == buyBIcon || widget == buyBCount) {
            return getHasBuyB();
        }
        return true;
    }

    private int editorMaxScroll() {
        return Math.max(0, editorContentBottom - editorViewBottom);
    }

    private void updateEditorWidgetLayout() {
        editorViewTop = panelTop() + HEADER_H + 22;
        editorViewBottom = panelBottom() - 36;

        int contentBottom = editorContentTop;
        for (int i = 0; i < editorWidgets.size(); i++) {
            AbstractWidget widget = editorWidgets.get(i);
            if (!canShowEditorWidget(widget)) {
                continue;
            }
            int baseY = editorWidgetBaseY.get(i);
            contentBottom = Math.max(contentBottom, baseY + widget.getHeight());
        }
        editorContentBottom = contentBottom;

        int maxScroll = editorMaxScroll();
        editorScroll = Math.max(0, Math.min(editorScroll, maxScroll));

        for (int i = 0; i < editorWidgets.size(); i++) {
            AbstractWidget widget = editorWidgets.get(i);
            int baseY = editorWidgetBaseY.get(i);
            int y = baseY - editorScroll;
            widget.setY(y);
            boolean enabledByState = canShowEditorWidget(widget);
            boolean inViewport = y + widget.getHeight() > editorViewTop && y < editorViewBottom;
            widget.visible = enabledByState && inViewport;
            if (!widget.visible && widget instanceof EditBox box) {
                box.setFocused(false);
            }
        }
    }

    private void renderEditorScrollHint(GuiGraphicsExtractor graphics, int editorLeft, int top, int bottom) {
        int maxScroll = editorMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int editorRight = panelRight() - PADDING;
        String txt = "Scroll";
        GuiUtils.text(graphics, font, txt, editorRight - font.width(txt), bottom - 32, 0xFF7F7F7F, font.lineHeight);
        int barX = editorRight - 2;
        int barTop = top + HEADER_H + 24;
        int barBottom = bottom - 40;
        int barH = Math.max(24, barBottom - barTop);
        GuiUtils.drawRect(graphics, barX, barTop, barX + 1, barBottom, 0xFF3A3A3A);
        int thumbH = Math.max(10, barH * Math.max(1, editorViewBottom - editorViewTop)
                / Math.max(1, editorContentBottom - editorContentTop));
        int thumbY = barTop + (barH - thumbH) * editorScroll / Math.max(1, maxScroll);
        GuiUtils.drawRect(graphics, barX - 1, thumbY, barX + 2, thumbY + thumbH, 0xFFCFCFCF);
    }

    private int visibleRows(int listTop, int listBottom) {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    private void ensureSelectedVisible() {
        int left = panelLeft();
        int top = panelTop();
        int listTop = top + HEADER_H + 22;
        int listBottom = panelBottom() - 36;
        int visible = visibleRows(listTop, listBottom);
        int maxOffset = Math.max(0, recipes.size() - visible);

        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + visible) {
            scrollOffset = selectedIndex - visible + 1;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));
    }

    private void syncEditorFromSelected() {
        if (recipes.isEmpty()) {
            selectedIndex = 0;
            setEditorEnabled(false);
            updateEditorWidgetLayout();
            return;
        }
        setEditorEnabled(true);
        CompoundTag r = recipes.get(selectedIndex);

        buyAIcon.setStack(toDisplayStack(r.getCompound("buy").orElse(new CompoundTag())));
        buyBIcon.setStack(toDisplayStack(r.getCompound("buyB").orElse(new CompoundTag())));
        sellIcon.setStack(toDisplayStack(r.getCompound("sell").orElse(new CompoundTag())));

        buyACount.setValue(String.valueOf(readCount(r.getCompound("buy").orElse(new CompoundTag()))));
        buyBCount.setValue(String.valueOf(readCount(r.getCompound("buyB").orElse(new CompoundTag()))));
        sellCount.setValue(String.valueOf(readCount(r.getCompound("sell").orElse(new CompoundTag()))));

        maxUses.setValue(String.valueOf(r.getInt("maxUses").orElse(12)));
        priceMultiplier.setValue(String.valueOf(r.getFloat("priceMultiplier").orElse(0.0F)));
        demand.setValue(String.valueOf(r.getInt("demand").orElse(0)));
        specialPrice.setValue(String.valueOf(r.getInt("specialPrice").orElse(0)));

        rewardExp = r.getBoolean("rewardExp").orElse(true);
        if (rewardExpToggle != null) {
            rewardExpToggle.refreshDisplay();
        }
        setHasBuyB(r.getCompound("buyB").isPresent());
        if (hasBuyBToggle != null) {
            hasBuyBToggle.refreshDisplay();
        }
        updateEditorWidgetLayout();
    }

    private void clearEditorFocus() {
        if (buyACount != null) buyACount.setFocused(false);
        if (buyBCount != null) buyBCount.setFocused(false);
        if (sellCount != null) sellCount.setFocused(false);
        if (maxUses != null) maxUses.setFocused(false);
        if (priceMultiplier != null) priceMultiplier.setFocused(false);
        if (demand != null) demand.setFocused(false);
        if (specialPrice != null) specialPrice.setFocused(false);
    }

    private void setEditorEnabled(boolean enabled) {
        if (buyAIcon != null) buyAIcon.active = enabled;
        if (buyBIcon != null) buyBIcon.active = enabled && getHasBuyB();
        if (sellIcon != null) sellIcon.active = enabled;

        setEditable(buyACount, enabled);
        setEditable(buyBCount, enabled && getHasBuyB());
        setEditable(sellCount, enabled);

        setEditable(maxUses, enabled);
        setEditable(priceMultiplier, enabled);
        setEditable(demand, enabled);
        setEditable(specialPrice, enabled);
    }

    private static void setEditable(@Nullable EditBox box, boolean editable) {
        if (box == null) return;
        box.setEditable(editable);
        box.setTextColor(editable ? 0xFFFFFFFF : 0xFFA0A0A0);
        if (!editable) {
            box.setFocused(false);
        }
    }

    private boolean getHasBuyB() {
        if (recipes.isEmpty()) return false;
        return recipes.get(selectedIndex).getCompound("buyB").isPresent();
    }

    private void setHasBuyB(boolean enabled) {
        if (recipes.isEmpty()) return;
        CompoundTag r = recipes.get(selectedIndex);
        if (enabled) {
            if (!r.getCompound("buyB").isPresent()) {
                r.put("buyB", defaultCost("minecraft:emerald", 1));
            }
        } else {
            ItemUtils.remove(r, "buyB");
        }
        if (buyBIcon != null) {
            buyBIcon.active = enabled;
        }
        if (buyBCount != null) {
            setEditable(buyBCount, enabled);
        }
        updateEditorWidgetLayout();
    }

    private void pickItemFor(String key) {
        if (recipes.isEmpty()) return;
        applyEditorToSelected();
        getMinecraft().gui.setScreen(new GuiTypeListSelector(this, Component.literal("Select item"), is -> {
            setItemFromStack(key, is);
            syncEditorFromSelected();
            return null;
        }));
    }

    private void applyEditorToSelected() {
        if (recipes.isEmpty()) return;

        CompoundTag r = recipes.get(selectedIndex);

        writeCount(r, "buy", buyACount);
        writeCount(r, "sell", sellCount);
        if (getHasBuyB()) {
            writeCount(r, "buyB", buyBCount);
        }

        Integer uses = parseInt(maxUses, 1, 999999);
        if (uses != null) r.putInt("maxUses", uses);

        r.putBoolean("rewardExp", rewardExp);

        Float mult = parseFloat(priceMultiplier);
        if (mult != null) r.putFloat("priceMultiplier", mult);

        Integer dem = parseInt(demand, -999999, 999999);
        if (dem != null) r.putInt("demand", dem);

        Integer sp = parseInt(specialPrice, -999999, 999999);
        if (sp != null) r.putInt("specialPrice", sp);
    }

    private void writeCount(CompoundTag recipe, String key, EditBox box) {
        CompoundTag item = recipe.getCompound(key).orElse(new CompoundTag());
        Integer count = parseInt(box, 1, 127);
        if (count == null) return;
        ItemUtils.putInt(item, "count", count);
        recipe.put(key, item);
    }

    private @Nullable Integer parseInt(EditBox box, int min, int max) {
        try {
            String v = box.getValue().trim();
            if (v.isEmpty() || "-".equals(v)) {
                box.setTextColor(0xFFFF4444);
                return null;
            }
            int i = Integer.parseInt(v);
            if (i < min || i > max) {
                box.setTextColor(0xFFFF4444);
                return null;
            }
            box.setTextColor(0xFFFFFFFF);
            return i;
        } catch (Exception e) {
            box.setTextColor(0xFFFF4444);
            return null;
        }
    }

    private @Nullable Float parseFloat(EditBox box) {
        try {
            String v = box.getValue().trim();
            if (v.isEmpty()) {
                box.setTextColor(0xFFFF4444);
                return null;
            }
            float f = Float.parseFloat(v);
            box.setTextColor(0xFFFFFFFF);
            return f;
        } catch (Exception e) {
            box.setTextColor(0xFFFF4444);
            return null;
        }
    }

    private List<CompoundTag> get() {
        List<CompoundTag> out = new ArrayList<>();
        for (CompoundTag t : recipes) out.add(t.copy());
        return out;
    }

    private static List<CompoundTag> normalize(List<CompoundTag> in) {
        List<CompoundTag> out = new ArrayList<>();
        if (in == null) return out;
        HolderLookup.Provider registryAccess = getRegistryAccess();
        for (CompoundTag raw : in) {
            CompoundTag recipe = raw == null ? new CompoundTag() : raw.copy();

            // Vanilla merchant recipe format:
            // - buy / buyB are ItemCost (fields like item/count/components predicate)
            // - sell is ItemStack (id/count/components)
            normalizeCostField(recipe, "buy", registryAccess);
            normalizeCostField(recipe, "buyB", registryAccess);
            normalizeSellField(recipe, "sell", registryAccess);

            // Ensure required entries exist.
            if (!recipe.getCompound("buy").isPresent()) {
                recipe.put("buy", defaultCost("minecraft:emerald", 1));
            }
            if (!recipe.getCompound("sell").isPresent()) {
                recipe.put("sell", defaultStack("minecraft:bread", 1));
            }

            out.add(recipe);
        }
        return out;
    }

    private static void normalizeCostField(CompoundTag recipe, String key, HolderLookup.Provider registryAccess) {
        if (recipe == null) return;
        CompoundTag value = recipe.getCompound(key).orElse(null);
        if (value == null) return;

        // Already an ItemCost
        if (ItemUtils.hasTag(value, "id", Tag.TAG_STRING)) {
            return;
        }

        // Legacy format: buy/buyB used to be ItemStack tags (id/count/components). Convert to ItemCost.
        if (ItemUtils.hasTag(value, "id", Tag.TAG_STRING)) {
            ItemStack stack = ItemUtils.parseStack(registryAccess, value);
            if (stack.isEmpty()) {
                return;
            }
            Tag costTag = saveItemCost(stack, registryAccess);
            if (costTag instanceof CompoundTag ct && !ct.isEmpty()) {
                recipe.put(key, ct);
            }
        }
    }

    private static void normalizeSellField(CompoundTag recipe, String key, HolderLookup.Provider registryAccess) {
        if (recipe == null) return;
        CompoundTag value = recipe.getCompound(key).orElse(null);
        if (value == null) return;

        // Already an ItemStack
        if (ItemUtils.hasTag(value, "id", Tag.TAG_STRING)) {
            return;
        }

        // If someone wrote ItemCost here, convert to ItemStack.
        if (ItemUtils.hasTag(value, "item", Tag.TAG_STRING)) {
            try {
                ItemCost cost = ItemCost.CODEC.parse(registryAccess.createSerializationContext(NbtOps.INSTANCE), value)
                        .result()
                        .orElse(null);
                if (cost == null) {
                    return;
                }
                Tag stackTag = ItemUtils.saveStack(cost.itemStack(), registryAccess);
                if (stackTag instanceof CompoundTag ct && !ct.isEmpty()) {
                    recipe.put(key, ct);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static CompoundTag defaultRecipe() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("maxUses", 12);
        tag.putBoolean("rewardExp", true);
        tag.putFloat("priceMultiplier", 0.0F);
        tag.putInt("demand", 0);
        tag.putInt("specialPrice", 0);

        tag.put("buy", defaultCost("minecraft:emerald", 1));
        tag.put("sell", defaultStack("minecraft:bread", 1));
        return tag;
    }

    private static CompoundTag defaultCost(String itemId, int count) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) {
            return new CompoundTag();
        }
        var holder = BuiltInRegistries.ITEM.get(id);
        if (holder.isEmpty()) {
            return new CompoundTag();
        }
        ItemCost cost = new ItemCost(holder.get(), clampCount(count), DataComponentExactPredicate.EMPTY);
        Tag tag = ItemCost.CODEC.encodeStart(getRegistryAccess().createSerializationContext(NbtOps.INSTANCE), cost)
                .result()
                .orElseGet(CompoundTag::new);
        return tag instanceof CompoundTag ct ? ct : new CompoundTag();
    }

    private static CompoundTag defaultStack(String itemId, int count) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) {
            return new CompoundTag();
        }
        var holder = BuiltInRegistries.ITEM.get(id);
        if (holder.isEmpty()) {
            return new CompoundTag();
        }
        ItemStack stack = new ItemStack(holder.get().value(), clampCount(count));
        Tag tag = ItemUtils.saveStack(stack, getRegistryAccess());
        return tag instanceof CompoundTag ct ? ct : new CompoundTag();
    }

    private static int clampCount(int count) {
        return Math.max(1, Math.min(127, count));
    }

    private static HolderLookup.Provider getRegistryAccess() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null ? mc.level.registryAccess() : VanillaRegistries.createLookup();
    }

    private static Tag saveItemCost(ItemStack stack, HolderLookup.Provider registryAccess) {
        try {
            ItemCost cost = new ItemCost(stack.getItem().builtInRegistryHolder(), stack.getCount(),
                    DataComponentExactPredicate.allOf(stack.getComponents()));
            return ItemCost.CODEC.encodeStart(registryAccess.createSerializationContext(NbtOps.INSTANCE), cost)
                    .result()
                    .orElseGet(CompoundTag::new);
        } catch (Throwable ignored) {
            return new CompoundTag();
        }
    }

    private void setItemFromStack(String key, ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == Items.AIR) return;
        CompoundTag r = recipes.get(selectedIndex);
        HolderLookup.Provider registryAccess = getRegistryAccess();
        if ("buy".equals(key) || "buyB".equals(key)) {
            Tag costTag = saveItemCost(stack, registryAccess);
            r.put(key, costTag);
        } else {
            Tag itemTag = ItemUtils.saveStack(stack, registryAccess);
            r.put(key, itemTag);
        }
    }

    private static int readCount(CompoundTag item) {
        if (item == null) return 1;
        if (ItemUtils.hasTag(item, "count", Tag.TAG_BYTE)) return item.getByte("count").orElse((byte) 1);
        if (ItemUtils.hasTag(item, "count", Tag.TAG_INT)) return item.getInt("count").orElse(1);
        if (ItemUtils.hasTag(item, "Count", Tag.TAG_BYTE)) return item.getByte("Count").orElse((byte) 1);
        if (ItemUtils.hasTag(item, "Count", Tag.TAG_INT)) return item.getInt("Count").orElse(1);
        return 1;
    }

    private static ItemStack toDisplayStack(CompoundTag item) {
        if (item == null) return ItemStack.EMPTY;

        // Prefer ItemCost decode (buy/buyB), then fall back to ItemStack (sell/legacy).
        try {
            ItemCost cost = ItemCost.CODEC.parse(getRegistryAccess().createSerializationContext(NbtOps.INSTANCE), item)
                    .result()
                    .orElse(null);
            if (cost != null) {
                return cost.itemStack();
            }
        } catch (Throwable ignored) {
        }

        // ItemStack (sell, or legacy buy/buyB)
        try {
            ItemStack decoded = ItemUtils.parseStack(getRegistryAccess(), item);
            if (!decoded.isEmpty()) {
                return decoded;
            }
        } catch (Throwable ignored) {
        }

        // Fallback: old minimal format (id/count)
        String idStr = item.getString("id").orElse("");
        Identifier id = Identifier.tryParse(idStr);
        if (id == null) return ItemStack.EMPTY;
        var holder = BuiltInRegistries.ITEM.get(id);
        if (holder.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(holder.get().value());
        int c = readCount(item);
        out.setCount(Math.max(1, Math.min(out.getMaxStackSize(), c)));
        return out;
    }

    private static String itemLabel(CompoundTag item) {
        if (item == null) return "?";
        try {
            ItemStack stack = toDisplayStack(item);
            return stack.isEmpty() ? "?" : stack.getHoverName().getString();
        } catch (Throwable t) {
            return "?";
        }
    }

    private String trimToWidth(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        // Hard-trim (no shortened labels requested), but keep list readable by limiting drawn width.
        // This does not alter data, only the visual list line.
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String next = b.toString() + s.charAt(i);
            if (font.width(next) > maxWidth) {
                return b.toString();
            }
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    private static @Nullable String tooltipFor(String key) {
        JsonObject field = VillagerTradesRepository.getOfferEntryFieldSchema(key);
        if (field == null) return null;
        if (!field.has("help") || !field.get("help").isJsonPrimitive()) return null;
        return field.get("help").getAsString();
    }
}
