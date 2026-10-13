package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EETheme;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiModifier;
import com.dutchmtc.ee.gui.selector.GuiTypeListSelector;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import com.dutchmtc.ee.gui.components.EETextField;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Familiar merchant exchange with a separate, independently scrolling options panel. */
public class GuiVillagerTradesEditor extends GuiModifier<List<CompoundTag>> {
    private static final int PANEL = 0xFFC6C6C6, TEXT = 0xFF303030, MUTED = 0xFF555555;
    private static final int BORDER = 0xFF555555, ERROR = EETheme.ERROR_LABEL;
    private static final int ROW_H = 24;
    private static final Identifier TRADE_ARROW = Identifier.withDefaultNamespace("container/villager/trade_arrow");
    private static final Identifier MERCHANT_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/villager.png");
    private final List<CompoundTag> originalRecipes, recipes;
    // Draft text outlives widgets, including incomplete input and nested item pickers.
    private final Map<String, String> draft = new LinkedHashMap<>(), loaded = new LinkedHashMap<>();
    private final Map<String, EditBox> fields = new LinkedHashMap<>();
    private final List<PlacedWidget> editorWidgets = new ArrayList<>();
    private final List<Consumer<GuiGraphicsExtractor>> decorations = new ArrayList<>();
    private final List<TradeButton> rowButtons = new ArrayList<>();
    private final List<DeletedTrade> deleted = new ArrayList<>();
    private final List<Integer> matches = new ArrayList<>();
    private TradeButton previousPage, nextPage;
    private int selectedIndex, listOffset, editorScroll, contentHeight;
    private int left, top, right, bottom, listX, listW, editorX, editorW, viewTop, viewBottom;
    private boolean compact, advanced, draggingOptions;
    private TradeEditorLayout layout;
    private String notice = "";
    private TradeButton saveButton, fixButton;
    private record PlacedWidget(AbstractWidget widget, int y) {}
    private record DeletedTrade(CompoundTag recipe, int position,
                                Map<String, String> draft, Map<String, String> loaded) {}

    public GuiVillagerTradesEditor(Screen parent, List<CompoundTag> recipes, Consumer<List<CompoundTag>> setter) {
        super(parent, Component.literal("Villager trades"), setter);
        this.originalRecipes = normalize(recipes);
        this.recipes = normalize(recipes);
        loadDraft();
    }

    @Override
    public boolean isModified() { return !recipes.equals(originalRecipes) || !draft.equals(loaded); }

    @Override
    protected void init() {
        clearFocus();
        clearWidgets();
        fields.clear(); editorWidgets.clear(); decorations.clear(); rowButtons.clear();
        super.init();
        layout = TradeEditorLayout.of(width, height);
        left = layout.left(); top = layout.top(); right = layout.right(); bottom = layout.bottom();
        compact = layout.compact();
        listX = layout.listX(); listW = layout.listWidth();
        editorX = layout.optionsX(); editorW = layout.optionsWidth();
        viewTop = top + 34; viewBottom = bottom - 51;
        contentHeight = 0;

        button(right - 176, bottom - 27, 72, 20, "Cancel", this::onCancel);
        saveButton = button(right - 98, bottom - 27, 88, 20, "Save trades", this::save);
        fixButton = button(right - 78, bottom - 46, 68, 16, "Fix value", () -> commitDraft());
        TradeButton undo = button(left + 10, bottom - 27, 76, 20, "Undo delete", this::undoDelete);
        undo.visible = !deleted.isEmpty();
        undo.setTooltip(Tooltip.create(Component.literal("Restore the last deleted trade, including its unsaved edits.")));

        refreshMatches();
        initLibrary();
        if (!recipes.isEmpty()) {
            initExchange();
            initOptions();
        }
        updateValidation();
        layoutEditor();
    }

    private void initLibrary() {
        for (int i = 0; i < visibleRows(); i++) {
            final int row = i;
            TradeButton entry = new TradeButton(listX, layout.listTop() + i * ROW_H, listW - 6, 22,
                    "", () -> selectRow(row)) {
                @Override
                protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    if (listOffset + row >= matches.size()) return;
                    int index = matches.get(listOffset + row);
                    CompoundTag recipe = recipes.get(index);
                    boolean selected = index == selectedIndex;
                    bevel(g, getX(), getY(), getWidth(), getHeight(),
                            selected ? 0xFFB6C99A : isHoveredOrFocused() ? 0xFFE5E5E5 : 0xFFAAAAAA, false);
                    if (selected || isFocused()) {
                        rect(g, getX(), getY(), 2, getHeight(), selected ? 0xFF48642D : 0xFFFFFFFF);
                    }
                    int x = getX() + (getWidth() - 94) / 2;
                    drawOfferItem(g, recipe, "buy", index, x + 2, getY() + 3);
                    drawOfferItem(g, recipe, "buyB", index, x + 28, getY() + 3);
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, TRADE_ARROW, x + 54, getY() + 7, 10, 9);
                    drawOfferItem(g, recipe, "sell", index, x + 76, getY() + 3);
                }
            };
            rowButtons.add(addRenderableWidget(entry));
        }
        int navY = layout.listTop() - 18;
        previousPage = button(listX + listW - 40, navY, 18, 16, "<", () -> scrollList(-visibleRows()));
        nextPage = button(listX + listW - 20, navY, 18, 16, ">", () -> scrollList(visibleRows()));
        previousPage.setTooltip(Tooltip.create(Component.literal("Previous offers")));
        nextPage.setTooltip(Tooltip.create(Component.literal("Next offers")));
        int actionY = bottom - 64;
        int addW = compact ? (listW - 4) / 3 : listW;
        button(listX, actionY, addW, 20, compact ? "Add" : "+ Add trade", this::addTrade);
        int actionsX = compact ? listX + addW + 2 : layout.tradeX() + (layout.tradeWidth() - 132) / 2;
        int actionW = compact ? addW : 64;
        button(actionsX, actionY, actionW, 20, compact ? "Copy" : "Duplicate", this::duplicateTrade).active = !recipes.isEmpty();
        button(actionsX + actionW + 2, actionY, actionW, 20, "Delete", this::deleteTrade).active = !recipes.isEmpty();
        refreshRows();
    }

    private void drawOfferItem(GuiGraphicsExtractor g, CompoundTag recipe, String key, int index, int x, int y) {
        ItemStack stack = toDisplayStack(compound(recipe, key));
        if (stack.isEmpty()) return;
        String amount = index == selectedIndex ? draft.get(key) : String.valueOf(readCount(compound(recipe, key)));
        g.item(stack, x, y);
        g.itemDecorations(font, stack, x, y, "1".equals(amount) ? "" : amount);
    }

    private void initExchange() {
        int x = layout.slotsX(), y = layout.slotsY();
        itemSlot("buy", x, y);
        itemSlot("buyB", x + 34, y);
        itemSlot("sell", x + 100, y);
        quantity("buy", x - 2, y + 34);
        if (hasSecondCost()) {
            quantity("buyB", x + 32, y + 34);
            TradeButton remove = button(x + 64, y + 34, 18, 18, "X", () -> {
                recipes.get(selectedIndex).remove("buyB");
                draft.remove("buyB");
                loaded.remove("buyB");
                notice = "Second payment removed. Save to apply.";
                init();
            });
            remove.setMessage(Component.literal("X").withStyle(ChatFormatting.RED));
            remove.setTooltip(Tooltip.create(Component.literal("Remove "
                    + itemLabel(compound(recipes.get(selectedIndex), "buyB"))
                    + " from this trade's payment.")));
        }
        quantity("sell", x + 98, y + 34);
    }

    private void itemSlot(String key, int x, int y) {
        ItemStack item = toDisplayStack(compound(recipes.get(selectedIndex), key));
        boolean optional = key.equals("buyB") && !hasSecondCost();
        TradeButton picker = new TradeButton(x, y, 26, 26,
                optional ? "Add a second payment item" : "Change " + item.getHoverName().getString(), () -> pickItemFor(key)) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                bevel(g, getX(), getY(), 26, 26, 0xFF8B8B8B, true);
                if (isHoveredOrFocused()) rect(g, getX() + 1, getY() + 1, 24, 24, 0x55FFFFFF);
                if (optional) text(g, "+", getX() + 10, getY() + 9, 0xFFE5E5E5);
                else g.item(item, getX() + 5, getY() + 5);
            }
        };
        String role = key.equals("sell") ? "You receive" : key.equals("buyB") ? "You also pay" : "You pay";
        picker.setTooltip(Tooltip.create(Component.literal(optional
                ? "Optional second payment\nClick to add another item the player must pay."
                : role + ": " + item.getHoverName().getString() + "\nClick to change this item.")));
        addRenderableWidget(picker);
    }

    private void quantity(String key, int x, int y) {
        EditBox box = makeField(key, x, y, 30);
        box.setTooltip(Tooltip.create(Component.literal(fieldName(key) + "\nType an amount from 1 to 127.")));
        addRenderableWidget(box);
    }

    private void initOptions() {
        option("maxUses", "Trades per restock", "How many times the trade can be used before it sells out.", 0);
        decorations.add(g -> {
            if (errorFor("uses") == null && errorFor("maxUses") == null) {
                int remaining = Math.max(0, Integer.parseInt(draft.get("maxUses").trim())
                        - Integer.parseInt(draft.get("uses").trim()));
                text(g, remaining == 0 ? "Sold out" : remaining + " uses remaining",
                        editorX, viewTop + 36 - editorScroll, remaining == 0 ? ERROR : TEXT);
            }
        });
        place(new TradeButton(editorX, 0, editorW - 8, 20, "Restock now", () -> {
            draft.put("uses", "0"); notice = "Trade restocked. Save to apply."; init();
        }), 49);
        boolean reward = recipes.get(selectedIndex).getBoolean("rewardExp").orElse(true);
        TradeButton xp = new TradeButton(editorX, 0, editorW - 8, 20,
                reward ? "Give player XP: On" : "Give player XP: Off", () -> {
                    recipes.get(selectedIndex).putBoolean("rewardExp", !reward); init();
                });
        xp.setTooltip(Tooltip.create(Component.literal("Drop experience orbs for the player when this trade is used.")));
        place(xp, 73);
        int nextY = 99;
        place(new TradeButton(editorX, 0, editorW - 8, 20, advanced ? "- Fewer options" : "+ More options", () -> {
            advanced = !advanced; init();
        }), nextY);
        nextY += 30;
        if (advanced) {
            option("uses", "Times already used", "Set to 0 to restock. At the restock limit, the offer is sold out.", nextY);
            option("xp", "Villager XP per trade", "Experience the villager gains from each completed trade.", nextY + 44);
            option("priceMultiplier", "Demand multiplier", "0 disables demand-based price changes. Vanilla trades often use 0.05.", nextY + 88);
            option("demand", "Demand", "Positive demand can raise the price of the first payment item.", nextY + 132);
            option("specialPrice", "Price adjustment", "Added to the first payment. Negative values give a discount.", nextY + 176);
            place(new TradeButton(editorX, 0, editorW - 8, 20, "Reset pricing", () -> {
                draft.put("priceMultiplier", "0.0"); draft.put("demand", "0"); draft.put("specialPrice", "0"); init();
            }), nextY + 220);
            nextY += 248;
        }
        contentHeight = nextY;
    }

    private void option(String key, String title, String help, int y) {
        decorations.add(g -> text(g, fit(title, editorW - 8), editorX, viewTop + y - editorScroll, TEXT));
        EditBox box = makeField(key, editorX + 1, 0, editorW - 10);
        box.setTooltip(Tooltip.create(Component.literal(help + "\n" + ruleHint(key))));
        place(box, y + 13);
    }

    private EditBox makeField(String key, int x, int y, int w) {
        EditBox box = new EETextField(font, x, y, w, 18, Component.literal(fieldName(key)));
        box.setMaxLength(32); box.setValue(draft.getOrDefault(key, "1"));
        fields.put(key, box);
        box.setResponder(value -> { draft.put(key, value); notice = ""; updateValidation(); });
        return box;
    }

    private void place(AbstractWidget widget, int y) {
        addWidget(widget); editorWidgets.add(new PlacedWidget(widget, y));
    }
    private void layoutEditor() {
        editorScroll = Math.max(0, Math.min(editorScroll, maxEditorScroll()));
        for (PlacedWidget placed : editorWidgets) {
            int y = viewTop + placed.y - editorScroll;
            placed.widget.setY(y);
            placed.widget.visible = y < viewBottom && y + placed.widget.getHeight() > viewTop;
            if (!placed.widget.visible && placed.widget.isFocused()) {
                placed.widget.setFocused(false); setFocused(null);
            }
        }
    }
    private int maxEditorScroll() { return Math.max(0, contentHeight - (viewBottom - viewTop)); }

    private void updateValidation() {
        for (var field : fields.entrySet())
            EETextField.validate(field.getValue(), errorFor(field.getKey()));
        if (saveButton != null) {
            saveButton.active = firstError() == null;
            saveButton.setTooltip(Tooltip.create(Component.literal(firstError() == null
                    ? "Apply all trade changes (Ctrl+S)." : firstError() + "\nFix this value before saving.")));
        }
        if (fixButton != null) fixButton.visible = firstError() != null;
    }
    private String errorFor(String key) { return TradeInputRules.error(key, draft.get(key)); }
    private String firstError() {
        for (String key : draft.keySet())
            if (errorFor(key) != null) return fieldName(key) + ": " + errorFor(key);
        return null;
    }
    private boolean commitDraft() {
        String error = firstError();
        if (error != null) {
            notice = error;
            String key = draft.keySet().stream().filter(k -> errorFor(k) != null).findFirst().orElse("buy");
            if (!key.equals("buy") && !key.equals("buyB") && !key.equals("sell") && !key.equals("maxUses"))
                advanced = true;
            init();
            EditBox field = fields.get(key);
            if (field != null) {
                editorWidgets.stream().filter(p -> p.widget == field).findFirst().ifPresent(p -> {
                    editorScroll = p.y - 14; layoutEditor();
                });
                setFocused(field); field.setFocused(true);
            }
            return false;
        }
        if (recipes.isEmpty()) return true;
        CompoundTag recipe = recipes.get(selectedIndex);
        for (var entry : draft.entrySet()) {
            String key = entry.getKey(), value = entry.getValue().trim();
            // Keep missing fields and all untouched NBT exactly as loaded.
            if (entry.getValue().equals(loaded.get(key))) continue;
            if (key.equals("buy") || key.equals("buyB") || key.equals("sell")) {
                CompoundTag item = compound(recipe, key).copy();
                item.putInt("count", Integer.parseInt(value)); recipe.put(key, item);
            } else if (key.equals("priceMultiplier")) recipe.putFloat(key, Float.parseFloat(value));
            else recipe.putInt(key, Integer.parseInt(value));
        }
        loaded.clear(); loaded.putAll(draft);
        return true;
    }
    private void loadDraft() {
        draft.clear(); loaded.clear();
        if (recipes.isEmpty()) return;
        selectedIndex = Math.max(0, Math.min(selectedIndex, recipes.size() - 1));
        CompoundTag r = recipes.get(selectedIndex);
        draft.put("buy", String.valueOf(readCount(compound(r, "buy"))));
        draft.put("sell", String.valueOf(readCount(compound(r, "sell"))));
        if (hasSecondCost()) draft.put("buyB", String.valueOf(readCount(compound(r, "buyB"))));
        draft.put("maxUses", String.valueOf(r.getInt("maxUses").orElse(12)));
        draft.put("uses", String.valueOf(r.getInt("uses").orElse(0)));
        draft.put("xp", String.valueOf(r.getInt("xp").orElse(1)));
        draft.put("priceMultiplier", String.valueOf(r.getFloat("priceMultiplier").orElse(0.0F)));
        draft.put("demand", String.valueOf(r.getInt("demand").orElse(0)));
        draft.put("specialPrice", String.valueOf(r.getInt("specialPrice").orElse(0)));
        loaded.putAll(draft);
    }
    private void save() {
        if (commitDraft()) { set(copyRecipes()); mc.gui.setScreen(parent); }
    }
    private void addTrade() {
        if (!commitDraft()) return;
        recipes.add(defaultRecipe()); selectedIndex = recipes.size() - 1;
        editorScroll = 0;
        loadDraft(); revealSelected(); notice = "New trade added. Choose the items to exchange."; init();
    }
    private void duplicateTrade() {
        if (!commitDraft()) return;
        recipes.add(selectedIndex + 1, recipes.get(selectedIndex).copy()); selectedIndex++;
        loadDraft(); revealSelected(); notice = "Trade duplicated."; init();
    }
    private void deleteTrade() {
        // An incomplete draft may still be deleted; Undo must restore its exact text as well as NBT.
        if (firstError() == null) commitDraft();
        deleted.add(new DeletedTrade(recipes.remove(selectedIndex), selectedIndex,
                new LinkedHashMap<>(draft), new LinkedHashMap<>(loaded)));
        selectedIndex = Math.min(selectedIndex, Math.max(0, recipes.size() - 1));
        loadDraft(); editorScroll = 0; notice = "Trade deleted. Use Undo delete to restore it."; init();
    }
    private void undoDelete() {
        if (deleted.isEmpty() || !commitDraft()) return;
        DeletedTrade restored = deleted.remove(deleted.size() - 1);
        selectedIndex = Math.min(restored.position, recipes.size());
        recipes.add(selectedIndex, restored.recipe);
        draft.clear(); draft.putAll(restored.draft);
        loaded.clear(); loaded.putAll(restored.loaded);
        revealSelected(); notice = "Trade restored."; init();
    }
    private void selectRow(int row) {
        if (listOffset + row >= matches.size()) return;
        int next = matches.get(listOffset + row);
        if (next != selectedIndex && !commitDraft()) return;
        if (next != selectedIndex) { selectedIndex = next; loadDraft(); editorScroll = 0; }
        notice = ""; init();
    }
    private void pickItemFor(String key) {
        mc.gui.setScreen(new GuiTypeListSelector(this, Component.literal(
                key.equals("sell") ? "Choose what the player receives" : "Choose what the player pays"), stack -> {
            // Keep the stored quantity; any pending quantity edit still lives in the draft.
            int count = readCount(compound(recipes.get(selectedIndex), key));
            if (stack.isEmpty()) return null;
            setItemFromStack(key, stack.copyWithCount(count));
            if (key.equals("buyB") && !draft.containsKey("buyB")) {
                draft.put("buyB", "1"); loaded.put("buyB", "1");
            }
            return null;
        }).setAmountEnabled(false));
    }
    private boolean hasSecondCost() {
        return !recipes.isEmpty() && recipes.get(selectedIndex).getCompound("buyB").isPresent();
    }
    private List<CompoundTag> copyRecipes() {
        List<CompoundTag> result = new ArrayList<>();
        for (CompoundTag recipe : recipes) result.add(recipe.copy());
        return result;
    }
    private static CompoundTag compound(CompoundTag recipe, String key) {
        return recipe.getCompound(key).orElseGet(CompoundTag::new);
    }
    private void refreshMatches() {
        matches.clear();
        for (int i = 0; i < recipes.size(); i++) matches.add(i);
    }
    private void refreshRows() {
        listOffset = Math.max(0, Math.min(listOffset, Math.max(0, matches.size() - visibleRows())));
        for (int row = 0; row < rowButtons.size(); row++) {
            TradeButton button = rowButtons.get(row);
            button.visible = listOffset + row < matches.size();
            if (button.visible) {
                int index = matches.get(listOffset + row);
                CompoundTag recipe = recipes.get(index);
                String summary = "Trade " + (index + 1) + ". Player pays " + paymentSummary(recipe)
                        + ". Receives " + readCount(compound(recipe, "sell")) + " " + itemLabel(compound(recipe, "sell"));
                button.setMessage(Component.literal(summary));
                button.setTooltip(Tooltip.create(Component.literal(summary)));
            }
        }
        if (previousPage != null) previousPage.active = listOffset > 0;
        if (nextPage != null) nextPage.active = listOffset + visibleRows() < matches.size();
    }
    private void revealSelected() { listOffset = Math.max(0, selectedIndex - visibleRows() + 1); }
    private int visibleRows() { return layout.visibleRows(); }
    private void scrollList(int amount) { listOffset += amount; refreshRows(); }
    private String paymentSummary(CompoundTag recipe) {
        CompoundTag a = compound(recipe, "buy");
        String result = readCount(a) + " " + itemLabel(a);
        if (recipe.getCompound("buyB").isPresent()) {
            CompoundTag b = compound(recipe, "buyB");
            result += " + " + readCount(b) + " " + itemLabel(b);
        }
        return result;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        bevel(g, left, top, right - left, bottom - top, PANEL, false);
        // A separate right-hand extension keeps the exchange visually independent of its settings.
        rect(g, editorX - 8, top + 8, 1, bottom - top - 56, 0xFF777777);
        rect(g, editorX - 7, top + 8, 1, bottom - top - 56, 0xFFFFFFFF);
        text(g, "Villager trades", left + 10, top + 12, TEXT);
        text(g, "Trade options", editorX, top + 12, TEXT);
        text(g, "Offers", listX, layout.listTop() - 14, TEXT);
        if (recipes.isEmpty()) {
            text(g, "No trades yet.", listX + 3, layout.listTop() + 8, MUTED);
            if (!compact) text(g, "Click + Add trade to begin.", layout.tradeX(), top + 70, TEXT);
            text(g, "Add a trade first.", editorX, viewTop + 8, MUTED);
        } else {
            renderExchange(g);
            g.enableScissor(editorX, viewTop, editorX + editorW, viewBottom);
            for (var decoration : decorations) decoration.accept(g);
            boolean pointerInside = inside(mx, my, editorX, viewTop, editorW, viewBottom - viewTop);
            for (PlacedWidget placed : editorWidgets)
                placed.widget.extractRenderState(g, pointerInside ? mx : -1, pointerInside ? my : -1, delta);
            g.disableScissor();
            scrollbar(g, editorX + editorW - 3, viewTop, viewBottom - viewTop,
                    contentHeight, viewBottom - viewTop, editorScroll);
        }
        scrollbar(g, listX + listW - 3, layout.listTop(), visibleRows() * ROW_H - 2,
                matches.size(), visibleRows(), listOffset);
        String error = firstError();
        String status = error != null ? error : !notice.isEmpty() ? notice : isModified()
                ? "Unsaved changes" : "Changes apply when you save.";
        text(g, fit(status, right - left - (error != null ? 100 : 20)), left + 10, bottom - 39,
                error != null ? ERROR : MUTED);
        super.extractRenderState(g, mx, my, delta);
    }

    private void renderExchange(GuiGraphicsExtractor g) {
        int x = layout.slotsX(), y = layout.slotsY();
        text(g, "You pay", x, y - 13, TEXT);
        text(g, "You get", Math.min(x + 98, layout.tradeX() + layout.tradeWidth() - font.width("You get")),
                y - 13, TEXT);
        // The large arrow is baked into the vanilla merchant background. The small offer-list
        // sprite has the same color as this panel and would disappear here.
        g.blit(RenderPipelines.GUI_TEXTURED, MERCHANT_TEXTURE, x + 72, y + 4,
                185.0F, 36.0F, 24, 18, 512, 256);
        if (!hasSecondCost()) text(g, "optional", x + 30, y + 40, MUTED);
        text(g, fit("Click items to change", layout.tradeWidth()), layout.tradeX() + 2, y + 63, MUTED);
        if (!compact) {
            text(g, "Trade " + (selectedIndex + 1) + " of " + recipes.size(), layout.tradeX() + 2, top + 30, MUTED);
            if (bottom - top >= 290) {
                CompoundTag r = recipes.get(selectedIndex);
                text(g, "You pay:", layout.tradeX() + 8, y + 93, MUTED);
                text(g, fit(draft.get("buy") + " " + itemLabel(compound(r, "buy")), layout.tradeWidth() - 16),
                        layout.tradeX() + 8, y + 107, TEXT);
                int next = y + 124;
                if (hasSecondCost()) {
                    text(g, fit("+ " + draft.get("buyB") + " " + itemLabel(compound(r, "buyB")), layout.tradeWidth() - 16),
                            layout.tradeX() + 8, next, TEXT);
                    next += 17;
                }
                text(g, "You get:", layout.tradeX() + 8, next, MUTED);
                text(g, fit(draft.get("sell") + " " + itemLabel(compound(r, "sell")), layout.tradeWidth() - 16),
                        layout.tradeX() + 8, next + 14, TEXT);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (maxEditorScroll() > 0 && inside(event.x(), event.y(), editorX + editorW - 6, viewTop, 8, viewBottom - viewTop)) {
            draggingOptions = true;
            scrollOptionsTo(event.y());
            return true;
        }
        if (inside(event.x(), event.y(), editorX, viewTop, editorW, viewBottom - viewTop)) {
            super.mouseClicked(event, doubleClick);
        } else {
            // A partially clipped option must never intercept an exchange or footer click.
            for (PlacedWidget placed : editorWidgets) placed.widget.visible = false;
            super.mouseClicked(event, doubleClick);
            layoutEditor();
        }
        return true;
    }
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingOptions) { scrollOptionsTo(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingOptions) { draggingOptions = false; return true; }
        return super.mouseReleased(event);
    }
    private void scrollOptionsTo(double y) {
        int height = viewBottom - viewTop;
        int thumb = Math.min(height, Math.max(16, height * height / Math.max(1, contentHeight)));
        editorScroll = (int) ((y - viewTop - thumb / 2.0) * maxEditorScroll() / Math.max(1, height - thumb));
        layoutEditor();
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (inside(mx, my, listX, layout.listTop(), listW, layout.listBottom() - layout.listTop())) {
            scrollList(vertical < 0 ? 1 : vertical > 0 ? -1 : 0); return true;
        }
        if (inside(mx, my, editorX, viewTop, editorW, viewBottom - viewTop)) {
            editorScroll += vertical < 0 ? 26 : vertical > 0 ? -26 : 0;
            layoutEditor(); return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDown() && event.key() == 83) { save(); return true; }
        if (event.key() == 266 || event.key() == 267) {
            int direction = event.key() == 267 ? 1 : -1;
            if (rowButtons.contains(getFocused()) || getFocused() == previousPage || getFocused() == nextPage)
                scrollList(direction * visibleRows());
            else { editorScroll += direction * Math.max(26, viewBottom - viewTop - 22); layoutEditor(); }
            return true;
        }
        return super.keyPressed(event);
    }
    @Override
    public void onClose() { onCancel(); }

    private TradeButton button(int x, int y, int w, int h, String label, Runnable action) {
        return addRenderableWidget(new TradeButton(x, y, w, h, label, action));
    }
    private class TradeButton extends EEButton {
        TradeButton(int x, int y, int w, int h, String label, Runnable action) {
            super(x, y, w, h, Component.literal(label), b -> action.run());
        }
    }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int color) {
        g.text(font, value, x, y, color, false);
    }
    private static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) GuiUtils.drawRect(g, x, y, x + w, y + h, color);
    }
    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, boolean inset) {
        EETheme.bevel(g, x, y, w, h, fill, inset);
    }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int total, int shown, int offset) {
        if (total <= shown || h <= 0) return;
        rect(g, x, y, 2, h, BORDER);
        int thumb = Math.min(h, Math.max(16, h * shown / total));
        rect(g, x, y + (h - thumb) * offset / Math.max(1, total - shown), 2, thumb, 0xFFEEEEEE);
    }
    private String fit(String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
    }
    private static String fieldName(String key) {
        return switch (key) {
            case "buy" -> "Payment quantity";
            case "buyB" -> "Second payment quantity";
            case "sell" -> "Received quantity";
            case "maxUses" -> "Trades per restock";
            case "uses" -> "Trades already used";
            case "xp" -> "Villager experience";
            case "priceMultiplier" -> "Demand multiplier";
            case "demand" -> "Demand";
            default -> "Price adjustment";
        };
    }
    private static String ruleHint(String key) {
        return switch (key) {
            case "buy", "buyB", "sell" -> "Enter a whole number from 1 to 127.";
            case "maxUses" -> "Enter a positive whole number.";
            case "uses", "xp" -> "Enter a whole number of 0 or more.";
            case "priceMultiplier" -> "Enter a finite number of 0 or more, for example 0.05.";
            default -> "Enter a whole number. Negative values are allowed.";
        };
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

    private static HolderLookup.Provider fallbackRegistry;

    private static HolderLookup.Provider getRegistryAccess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) return mc.level.registryAccess();
        if (fallbackRegistry == null) fallbackRegistry = VanillaRegistries.createWorldLookup();
        return fallbackRegistry;
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

}
