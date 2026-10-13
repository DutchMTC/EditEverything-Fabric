package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.BannerEditorData;
import com.dutchmtc.ee.utils.GuiUtils;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.*;

import org.joml.Vector3f;

import java.util.*;
import java.util.function.Consumer;

/** A native-model preview with foreground-first layers and an embedded pattern catalog. */
public final class GuiBannerEditor extends GuiModifier<ItemStack> {
    private static final int ROW = 28, CELL_W = 32, CELL_H = 44;
    private final BannerEditorData draft;
    private BannerEditorData.Layer selected, pressed, editing;
    private final List<Holder<BannerPattern>> patterns = new ArrayList<>(),
            filtered = new ArrayList<>();
    private final List<EEButton> colors = new ArrayList<>(), removes = new ArrayList<>();
    private final EEDropdownScrollbar scrollbar = new EEDropdownScrollbar(),
            catalogScrollbar = new EEDropdownScrollbar();
    private final ItemStackRenderState model = new ItemStackRenderState();
    private BannerFlagModel flag;
    private boolean previewDirty = true,
            catalog,
            rowDragging,
            cameraDragging,
            palette,
            basePalette,
            optionsOpen;
    private int left, top, panelWidth, panelHeight, previewX, previewY, previewWidth, previewHeight;
    private int listX,
            listWidth,
            listTop,
            listBottom,
            bodyBottom,
            scroll,
            catalogScroll,
            gridTop,
            gridBottom,
            drop;
    private int optionFocus = -1;
    private double pressX, pressY, pointerY, grab;
    private float yaw = 180, pitch, zoom = 1;
    private String query = "";
    private EETextField search;
    private EEButton add, change, apply, back, base;
    private EEDropdownButton options;
    private AbstractWidget paletteAnchor;
    private BannerEditorData.Layer paletteLayer;

    public GuiBannerEditor(Screen parent, ItemStack source, Consumer<ItemStack> setter) {
        super(
                parent,
                Component.literal(
                        source.getItem() instanceof ShieldItem ? "Shield editor" : "Banner editor"),
                setter);
        draft = new BannerEditorData(source);
        yaw = draft.shield() ? 0 : 180;
        selected = draft.layers().isEmpty() ? null : draft.layers().getLast();
        var registry =
                mc.level != null ? mc.level.registryAccess() : mc.getConnection().registryAccess();
        registry.lookupOrThrow(Registries.BANNER_PATTERN)
                .listElements()
                .filter(h -> !h.is(BannerPatterns.BASE))
                .forEach(patterns::add);
        patterns.sort(Comparator.comparing(GuiBannerEditor::patternName));
        refilter();
    }

    @Override
    public boolean isModified() {
        return draft.changed();
    }

    @Override
    public void onClose() {
        onCancel();
    }

    @Override
    protected void setInitialFocus() {}

    @Override
    protected void repositionElements() {
        init();
    }

    @Override
    public void init() {
        previewDirty = true;
        clearWidgets();
        clearFocus();
        colors.clear();
        removes.clear();
        palette = optionsOpen = rowDragging = cameraDragging = false;
        pressed = null;
        panelWidth = Math.min(420, width - 16);
        panelHeight = Math.min(304, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        previewX = left + 12;
        previewY = top + 38;
        previewWidth = Math.max(82, Math.min(124, (panelWidth - 24) * 28 / 100));
        bodyBottom = top + panelHeight - 39;
        previewHeight = bodyBottom - previewY - 37;
        listX = previewX + previewWidth + 8;
        listWidth = left + panelWidth - 12 - listX;
        listTop = top + 62;
        listBottom = bodyBottom - 25;
        int footerY = top + panelHeight - 27, half = (panelWidth - 28) / 2;
        back =
                addRenderableWidget(
                        new EEButton(
                                left + 12,
                                footerY,
                                half,
                                20,
                                Component.literal(catalog ? "Back" : "Cancel"),
                                b -> {
                                    if (catalog) closeCatalog();
                                    else onCancel();
                                }));
        apply =
                addRenderableWidget(
                        new EEButton(
                                left + 16 + half,
                                footerY,
                                half,
                                20,
                                Component.literal("Apply"),
                                b -> {
                                    if (catalog || !apply.active) return;
                                    set(draft.result());
                                    mc.gui.setScreen(parent);
                                }));
        options =
                addRenderableWidget(
                        new EEDropdownButton(
                                left + panelWidth - 86,
                                top + 10,
                                74,
                                20,
                                Component.literal("Options"),
                                b -> {
                                    palette = false;
                                    optionsOpen = !optionsOpen;
                                    optionFocus = -1;
                                    clearFocus();
                                }));
        options.active = !catalog;
        options.setTooltip(
                catalog
                        ? Tooltip.create(Component.literal("Return to your layers to use Options."))
                        : null);
        base =
                addRenderableWidget(
                        new EEButton(
                                previewX,
                                bodyBottom - 20,
                                previewWidth,
                                20,
                                Component.literal("Base color"),
                                b -> openPalette(base, null)) {
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
                                DyeColor color = draft.displayedBase();
                                if (color == null)
                                    g.item(new ItemStack(Items.SHIELD), getX() + 4, getY() + 2);
                                else {
                                    EETheme.rect(g, getX() + 5, getY() + 5, 12, 10, EETheme.INPUT);
                                    EETheme.rect(
                                            g,
                                            getX() + 6,
                                            getY() + 6,
                                            10,
                                            8,
                                            0xFF000000 | color.getTextureDiffuseColor());
                                }
                                g.text(
                                        font,
                                        fit(
                                                color == null ? "Wood" : dyeName(color),
                                                getWidth() - 33),
                                        getX() + 22,
                                        getY() + 6,
                                        EETheme.TEXT,
                                        false);
                                for (int row = 0; row < 3; row++)
                                    EETheme.rect(
                                            g,
                                            getRight() - 10 + row,
                                            getY() + 8 + row,
                                            5 - row * 2,
                                            1,
                                            EETheme.TEXT);
                            }
                        });
        base.setTooltip(
                Tooltip.create(
                        Component.literal("Change the base color using Minecraft's dye colors.")));
        if (catalog) {
            search =
                    addRenderableWidget(
                            new EETextField(
                                    font,
                                    listX + 6,
                                    top + 57,
                                    listWidth - 12,
                                    18,
                                    Component.literal("Search patterns")));
            search.setBordered(false);
            search.setTextShadow(false);
            search.setTextColor(EETheme.LIGHT);
            search.setMaxLength(256);
            search.setValue(query);
            search.setHint(Component.literal("Search patterns...").withColor(0xAEBBB5));
            search.setResponder(
                    value -> {
                        query = value;
                        refilter();
                        catalogScroll = 0;
                        position();
                    });
            gridTop = top + 82;
            gridBottom = bodyBottom;
            if (flag == null)
                flag =
                        new BannerFlagModel(
                                mc.getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
        } else {
            add =
                    addRenderableWidget(
                            new EEButton(
                                    listX + listWidth - 74,
                                    top + 34,
                                    74,
                                    20,
                                    Component.literal("Add layer"),
                                    b -> openCatalog(null)));
            change =
                    addRenderableWidget(
                            new EEButton(
                                    listX,
                                    bodyBottom - 20,
                                    listWidth,
                                    20,
                                    Component.literal("Change pattern"),
                                    b -> {
                                        if (selected != null) openCatalog(selected);
                                    }));
            for (var layer : visualLayers()) {
                EEButton color =
                        addWidget(
                                new EEButton(
                                        0,
                                        0,
                                        18,
                                        18,
                                        Component.literal("Color"),
                                        b -> {
                                            selected = layer;
                                            refresh();
                                            openPalette(b, layer);
                                        }) {
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
                                                g, getX() + 3, getY() + 3, 12, 12, EETheme.INPUT);
                                        EETheme.rect(
                                                g,
                                                getX() + 4,
                                                getY() + 4,
                                                10,
                                                10,
                                                0xFF000000 | layer.color.getTextureDiffuseColor());
                                    }
                                });
                color.setTooltip(
                        Tooltip.create(Component.literal("Layer color: " + dyeName(layer.color))));
                colors.add(color);
                EEButton remove =
                        addWidget(
                                new EEButton(
                                        0, 0, 18, 18, Component.literal("×"), b -> remove(layer)));
                remove.setTooltip(
                        Tooltip.create(
                                Component.literal("Remove " + patternName(layer.pattern) + ".")));
                removes.add(remove);
            }
        }
        scrollbar.reset();
        catalogScrollbar.reset();
        position();
        refresh();
    }

    private void changed() {
        previewDirty = true;
        init();
    }

    private List<BannerEditorData.Layer> visualLayers() {
        return draft.layers().reversed();
    }

    private int maxScroll() {
        return Math.max(0, draft.layers().size() * ROW - (listBottom - listTop));
    }

    private int gutter() {
        return maxScroll() > 0 ? EEDropdownScrollbar.WIDTH : 0;
    }

    private int rowRight() {
        return listX + listWidth - 4 - gutter();
    }

    private void position() {
        scroll = Math.clamp(scroll, 0, maxScroll());
        for (int i = 0; i < colors.size(); i++) {
            int y = listTop + i * ROW - scroll + (ROW - 18) / 2;
            boolean visible = y >= listTop && y + 18 <= listBottom && !rowDragging && !catalog;
            colors.get(i).setPosition(rowRight() - 42, y);
            removes.get(i).setPosition(rowRight() - 20, y);
            colors.get(i).visible = removes.get(i).visible = visible;
        }
        scrollbar.layout(
                listX + listWidth - EEDropdownScrollbar.WIDTH,
                listTop,
                listBottom - listTop,
                listBottom - listTop,
                draft.layers().size() * ROW,
                scroll,
                ROW);
        if (catalog) {
            catalogScroll = Math.clamp(catalogScroll, 0, Math.max(0, totalGridRows() - gridRows()));
            catalogScrollbar.layout(
                    listX + listWidth - EEDropdownScrollbar.WIDTH,
                    gridTop,
                    gridBottom - gridTop,
                    gridRows(),
                    totalGridRows(),
                    catalogScroll,
                    1);
        }
    }

    private void refresh() {
        apply.active = !catalog;
        apply.setTooltip(
                catalog
                        ? Tooltip.create(
                                Component.literal(
                                        "Choose a pattern or return to your layers first."))
                        : null);
        if (!catalog) {
            add.active = draft.canAdd();
            add.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    add.active
                                            ? "Choose a pattern to add in front of your existing"
                                                    + " layers."
                                            : "Minecraft displays up to 16 pattern layers. Remove a"
                                                    + " layer before adding another.")));
            change.active = selected != null;
            change.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    selected == null
                                            ? "Select a layer first."
                                            : "Choose a different pattern for the selected layer."
                                                    + " Its color stays the same.")));
        }
    }

    private void ensureSelectedVisible() {
        if (selected == null) return;
        int index = visualLayers().indexOf(selected);
        if (index < 0) return;
        if (index * ROW < scroll) scroll = index * ROW;
        else if ((index + 1) * ROW > scroll + listBottom - listTop)
            scroll = (index + 1) * ROW - (listBottom - listTop);
        position();
    }

    private void remove(BannerEditorData.Layer layer) {
        int index = visualLayers().indexOf(layer);
        draft.remove(layer);
        if (selected == layer)
            selected =
                    draft.layers().isEmpty()
                            ? null
                            : visualLayers().get(Math.min(index, draft.layers().size() - 1));
        changed();
    }

    private void openCatalog(BannerEditorData.Layer layer) {
        if (layer == null && !draft.canAdd()) return;
        editing = layer;
        catalog = true;
        query = "";
        catalogScroll = 0;
        refilter();
        init();
        if (layer != null) {
            int index = filtered.indexOf(layer.pattern);
            if (index >= 0) catalogScroll = index / gridCols();
            position();
        }
    }

    private void closeCatalog() {
        catalog = false;
        editing = null;
        init();
        ensureSelectedVisible();
    }

    private void choosePattern(Holder<BannerPattern> pattern) {
        if (!catalog || pattern == null) return;
        if (editing == null) selected = draft.add(pattern);
        else {
            editing.pattern = pattern;
            selected = editing;
        }
        catalog = false;
        editing = null;
        changed();
        ensureSelectedVisible();
    }

    private void refilter() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        filtered.clear();
        for (var pattern : patterns)
            if (q.isEmpty()
                    || patternName(pattern).toLowerCase(Locale.ROOT).contains(q)
                    || patternId(pattern).toLowerCase(Locale.ROOT).contains(q))
                filtered.add(pattern);
    }

    private int gridCols() {
        return Math.max(1, (listWidth - EEDropdownScrollbar.WIDTH) / CELL_W);
    }

    private int gridRows() {
        return Math.max(1, (gridBottom - gridTop) / CELL_H);
    }

    private int totalGridRows() {
        return (filtered.size() + gridCols() - 1) / gridCols();
    }

    private int gridX() {
        return listX + (listWidth - EEDropdownScrollbar.WIDTH - gridCols() * CELL_W) / 2;
    }

    private int gridIndex(double x, double y) {
        if (x < gridX()
                || x >= gridX() + gridCols() * CELL_W
                || y < gridTop
                || y >= gridTop + gridRows() * CELL_H) return -1;
        int index =
                (catalogScroll + (int) (y - gridTop) / CELL_H) * gridCols()
                        + (int) (x - gridX()) / CELL_W;
        return index < filtered.size() ? index : -1;
    }

    private void openPalette(AbstractWidget anchor, BannerEditorData.Layer layer) {
        optionsOpen = false;
        palette = true;
        paletteAnchor = anchor;
        paletteLayer = layer;
        basePalette = layer == null;
        clearFocus();
    }

    private int paletteWidth() {
        return 100;
    }

    private int paletteHeight() {
        return 108 + (basePalette && draft.shield() ? 24 : 0);
    }

    private int paletteX() {
        return Math.clamp(paletteAnchor.getX(), left + 12, left + panelWidth - 12 - paletteWidth());
    }

    private int paletteY() {
        return Math.max(
                top + 34, Math.min(paletteAnchor.getBottom() + 2, bodyBottom - paletteHeight()));
    }

    private void chooseColor(DyeColor color) {
        if (basePalette) {
            if (!draft.setBase(color)) return;
        } else if (paletteLayer != null) paletteLayer.color = color;
        palette = false;
        changed();
    }

    private String[] optionLabels() {
        return new String[] {
            "Duplicate layer", "Move forward", "Move backward", "Reset view", "Clear patterns"
        };
    }

    private String optionReason(int i) {
        int index = selected == null ? -1 : draft.layers().indexOf(selected);
        return switch (i) {
            case 0 ->
                    selected == null
                            ? "Select a layer first."
                            : !draft.canAdd()
                                    ? "Minecraft displays up to 16 pattern layers."
                                    : null;
            case 1 ->
                    index < 0
                            ? "Select a layer first."
                            : index == draft.layers().size() - 1
                                    ? "This layer is already at the front."
                                    : null;
            case 2 ->
                    index < 0
                            ? "Select a layer first."
                            : index == 0 ? "This layer is already at the back." : null;
            case 4 -> draft.layers().isEmpty() ? "There are no patterns to clear." : null;
            default -> null;
        };
    }

    private void chooseOption(int i) {
        if (optionReason(i) != null) return;
        optionsOpen = false;
        switch (i) {
            case 0 -> {
                selected = draft.duplicate(selected);
                changed();
                ensureSelectedVisible();
            }
            case 1, 2 -> {
                draft.move(selected, draft.layers().indexOf(selected) + (i == 1 ? 1 : -1));
                changed();
                ensureSelectedVisible();
            }
            case 3 -> {
                yaw = draft.shield() ? 0 : 180;
                pitch = 0;
                zoom = 1;
            }
            case 4 ->
                    mc.gui.setScreen(
                            new GuiConfirmation(
                                    this,
                                    Component.literal("Remove all pattern layers?"),
                                    Component.literal("Clear patterns"),
                                    () -> {
                                        draft.clear();
                                        selected = null;
                                        scroll = 0;
                                        previewDirty = true;
                                        mc.gui.setScreen(this);
                                    },
                                    () -> mc.gui.setScreen(this)));
        }
    }

    private int optionsX() {
        return left + panelWidth - 12 - 146;
    }

    private int optionsY() {
        return top + 34;
    }

    private boolean insidePreview(double x, double y) {
        return x >= previewX
                && x < previewX + previewWidth
                && y >= previewY
                && y < previewY + previewHeight;
    }

    private boolean insideList(double x, double y) {
        return x >= listX && x < rowRight() && y >= listTop && y < listBottom;
    }

    private int rowIndex(double y) {
        return (int) (y - listTop + scroll) / ROW;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (palette) {
            if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return true;
            int x = paletteX(), y = paletteY(), mx = (int) e.x(), my = (int) e.y();
            if (GuiUtils.isHover(x + 6, y + 18, 88, 88, mx, my)) {
                int col = (mx - x - 6) / 22, row = (my - y - 18) / 22;
                if ((mx - x - 6) % 22 < 20 && (my - y - 18) % 22 < 20)
                    chooseColor(DyeColor.values()[row * 4 + col]);
            } else if (basePalette
                    && draft.shield()
                    && GuiUtils.isHover(x + 6, y + 108, 88, 20, mx, my)) chooseColor(null);
            else palette = false;
            return true;
        }
        if (optionsOpen) {
            if (e.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && GuiUtils.isHover(optionsX(), optionsY(), 146, 100, (int) e.x(), (int) e.y()))
                chooseOption((int) (e.y() - optionsY()) / 20);
            else optionsOpen = false;
            return true;
        }
        if (catalog) {
            if (catalogScrollbar.mouseClicked(
                    e,
                    v -> {
                        catalogScroll = v;
                        position();
                    })) return true;
            if (e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                int index = gridIndex(e.x(), e.y());
                if (index >= 0) {
                    playClick();
                    choosePattern(filtered.get(index));
                    return true;
                }
            }
        } else {
            if (scrollbar.mouseClicked(
                    e,
                    v -> {
                        scroll = v;
                        position();
                    })) return true;
            if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && insideList(e.x(), e.y())) {
                for (var button : colors)
                    if (button.visible && button.isMouseOver(e.x(), e.y()))
                        return super.mouseClicked(e, twice);
                for (var button : removes)
                    if (button.visible && button.isMouseOver(e.x(), e.y()))
                        return super.mouseClicked(e, twice);
                int index = rowIndex(e.y());
                if (index >= draft.layers().size()) return true;
                selected = pressed = visualLayers().get(index);
                refresh();
                clearFocus();
                pressX = e.x();
                pressY = pointerY = e.y();
                grab = e.y() - (listTop + index * ROW - scroll);
                setDragging(true);
                return true;
            }
        }
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && insidePreview(e.x(), e.y())) {
            cameraDragging = true;
            setDragging(true);
            clearFocus();
            return true;
        }
        return super.mouseClicked(e, twice);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (palette || optionsOpen) return true;
        if (catalog
                && catalogScrollbar.mouseDragged(
                        e,
                        v -> {
                            catalogScroll = v;
                            position();
                        })) return true;
        if (!catalog
                && scrollbar.mouseDragged(
                        e,
                        v -> {
                            scroll = v;
                            position();
                        })) return true;
        if (cameraDragging) {
            yaw -= (float) dx * 2;
            pitch = Math.clamp(pitch - (float) dy, -35, 35);
            return true;
        }
        if (pressed != null) {
            pointerY = e.y();
            rowDragging |= Math.hypot(e.x() - pressX, e.y() - pressY) >= 3;
            if (rowDragging) {
                updateDrop();
                position();
            }
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    private void updateDrop() {
        drop =
                Math.clamp(
                        (int) Math.round((pointerY - grab + ROW / 2.0 - listTop + scroll) / ROW),
                        0,
                        draft.layers().size());
    }

    private void cancelDrag() {
        pressed = null;
        rowDragging = cameraDragging = false;
        setDragging(false);
        position();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (scrollbar.mouseReleased() || catalogScrollbar.mouseReleased()) {
            setDragging(false);
            return true;
        }
        if (cameraDragging) {
            cameraDragging = false;
            setDragging(false);
            return true;
        }
        if (pressed != null && e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (rowDragging) {
                int before = visualLayers().indexOf(pressed),
                        target =
                                Math.clamp(
                                        drop - (before < drop ? 1 : 0),
                                        0,
                                        draft.layers().size() - 1);
                draft.move(pressed, draft.layers().size() - 1 - target);
                previewDirty = true;
            }
            cancelDrag();
            init();
            ensureSelectedVisible();
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (palette || optionsOpen) return true;
        if (insidePreview(x, y)) {
            zoom = Math.clamp(zoom + (float) vertical * .1f, .6f, 1.8f);
            return true;
        }
        if (catalog && x >= listX && x < listX + listWidth && y >= gridTop && y < gridBottom) {
            catalogScroll -= (int) Math.signum(vertical);
            position();
            return true;
        }
        if (!catalog && x >= listX && x < listX + listWidth && y >= listTop && y < listBottom) {
            scroll -= (int) (vertical * ROW);
            position();
            if (rowDragging) updateDrop();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.isEscape()) {
            if (palette || optionsOpen) {
                palette = optionsOpen = false;
                return true;
            }
            if (pressed != null || cameraDragging) {
                cancelDrag();
                return true;
            }
            if (catalog) {
                closeCatalog();
                return true;
            }
        }
        if (optionsOpen) {
            if (e.isUp() || e.isDown()) {
                optionFocus =
                        optionFocus < 0
                                ? (e.isUp() ? 4 : 0)
                                : Math.clamp(optionFocus + (e.isUp() ? -1 : 1), 0, 4);
            } else if ((e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_SPACE)
                    && optionFocus >= 0) chooseOption(optionFocus);
            return true;
        }
        if (palette) return true;
        if (!catalog && selected != null && (e.isUp() || e.isDown())) {
            int index = visualLayers().indexOf(selected),
                    next = Math.clamp(index + (e.isUp() ? -1 : 1), 0, draft.layers().size() - 1);
            if (e.hasAltDown()) {
                draft.move(selected, draft.layers().size() - 1 - next);
                changed();
            } else selected = visualLayers().get(next);
            ensureSelectedVisible();
            refresh();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void tick() {
        if (rowDragging) {
            if (pointerY < listTop + 14) scroll -= 4;
            if (pointerY > listBottom - 14) scroll += 4;
            position();
            updateDrop();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        g.item(draft.result(), left + 12, top + 12);
        g.text(
                font,
                draft.shield() ? "Shield editor" : "Banner editor",
                left + 34,
                top + 14,
                EETheme.TEXT,
                false);
        EETheme.bevel(g, previewX, previewY, previewWidth, previewHeight, EETheme.INPUT, true);
        renderPreview(g);
        g.text(font, "Base color", previewX, bodyBottom - 33, EETheme.TEXT, false);
        int hoverX = palette || optionsOpen ? -1 : mx, hoverY = palette || optionsOpen ? -1 : my;
        if (catalog) renderCatalog(g, hoverX, hoverY);
        else renderLayers(g, hoverX, hoverY, delta);
        super.extractRenderState(g, hoverX, hoverY, delta);
        if (palette) renderPalette(g, mx, my);
        if (optionsOpen) renderOptions(g, mx, my);
    }

    private void renderPreview(GuiGraphicsExtractor g) {
        if (previewDirty) {
            mc.getItemModelResolver()
                    .updateForTopItem(
                            model, draft.result(), ItemDisplayContext.NONE, mc.level, null, 0);
            previewDirty = false;
        }
        if (model.isEmpty()) return;
        var bounds = model.getModelBoundingBox();
        var center = bounds.getCenter();
        float scale =
                (float)
                                Math.min(
                                        (previewWidth - 16) / Math.max(.01, bounds.getXsize()),
                                        (previewHeight - 12) / Math.max(.01, bounds.getYsize()))
                        * .90f
                        * zoom;
        g.guiRenderState.addPicturesInPictureState(
                new BannerPreviewState(
                        model,
                        new Vector3f((float) center.x, (float) center.y, (float) center.z),
                        draft.shield(),
                        yaw,
                        pitch,
                        previewX + 2,
                        previewY + 2,
                        previewX + previewWidth - 2,
                        previewY + previewHeight - 2,
                        scale,
                        g.scissorStack.peek()));
    }

    private void renderLayers(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.text(font, "Layers", listX, top + 40, EETheme.TEXT, false);
        EETheme.bevel(
                g,
                listX - 1,
                listTop - 1,
                listWidth + 2,
                listBottom - listTop + 2,
                EETheme.INPUT,
                true);
        g.enableScissor(listX, listTop, listX + listWidth, listBottom);
        var layers = visualLayers();
        if (layers.isEmpty())
            g.text(font, "No patterns", listX + 8, listTop + 12, EETheme.LIGHT, false);
        for (int i = 0; i < layers.size(); i++) {
            int y = listTop + i * ROW - scroll;
            if (y + ROW <= listTop || y >= listBottom) continue;
            var layer = layers.get(i);
            boolean hover = insideList(mx, my) && rowIndex(my) == i && !rowDragging;
            if (rowDragging && layer == pressed) continue;
            EETheme.rect(
                    g,
                    listX + 2,
                    y + 1,
                    rowRight() - listX - 2,
                    ROW - 2,
                    layer == selected
                            ? EETheme.CONTROL
                            : hover ? EETheme.CONTROL_HOVER : EETheme.PANEL);
            EEDragRow.grip(g, listX + 6, y, ROW, EETheme.MUTED);
            g.item(patternIcon(layer.pattern, layer.color), listX + 15, y + 6);
            g.text(
                    font,
                    fit(patternName(layer.pattern), rowRight() - listX - 80),
                    listX + 35,
                    y + 10,
                    EETheme.TEXT,
                    false);
            if (hover && mx < rowRight() - 44) {
                var help = new ArrayList<Component>();
                help.add(Component.literal(patternName(layer.pattern)));
                help.add(Component.literal(dyeName(layer.color)));
                if (draft.layers().indexOf(layer) >= BannerEditorData.VISIBLE_LAYER_LIMIT)
                    help.add(
                            Component.literal(
                                            "This imported layer is kept, but Minecraft only"
                                                    + " displays the first 16. Move it behind the"
                                                    + " others to make it visible.")
                                    .withColor(EETheme.ERROR));
                else help.add(Component.literal("Drawn over the layers below."));
                g.setTooltipForNextFrame(
                        font,
                        help.stream()
                                .flatMap(
                                        line ->
                                                font
                                                        .split(line, Math.min(230, width - 24))
                                                        .stream())
                                .toList(),
                        (screenWidth,
                                screenHeight,
                                cursorX,
                                cursorY,
                                tooltipWidth,
                                tooltipHeight) ->
                                new org.joml.Vector2i(
                                        Math.clamp(
                                                cursorX + 12,
                                                4,
                                                Math.max(4, width - tooltipWidth - 4)),
                                        Math.clamp(
                                                cursorY - 12,
                                                4,
                                                Math.max(4, height - tooltipHeight - 4))),
                        mx,
                        my,
                        false);
            }
            colors.get(i).extractRenderState(g, mx, my, delta);
            removes.get(i).extractRenderState(g, mx, my, delta);
        }
        if (rowDragging) {
            int y = EEDragRow.floatingTop(pointerY, grab, listTop, listBottom, ROW - 2);
            EEDragRow.floatingFrame(g, listX + 4, y, rowRight() - listX - 8, ROW - 2);
            EEDragRow.grip(g, listX + 9, y, ROW - 2, EETheme.MUTED);
            g.item(patternIcon(pressed.pattern, pressed.color), listX + 18, y + 5);
            g.text(
                    font,
                    fit(patternName(pressed.pattern), rowRight() - listX - 48),
                    listX + 38,
                    y + 9,
                    EETheme.TEXT,
                    false);
            EEDragRow.dropMarker(
                    g,
                    listX + 2,
                    listTop + drop * ROW - scroll,
                    rowRight() - listX - 2,
                    listTop,
                    listBottom);
        }
        g.disableScissor();
        scrollbar.render(g, mx, my);
    }

    private void renderCatalog(GuiGraphicsExtractor g, int mx, int my) {
        g.text(
                font,
                editing == null ? "Choose pattern" : "Change pattern",
                listX,
                top + 40,
                EETheme.TEXT,
                false);
        EETheme.bevel(g, listX, top + 56, listWidth, 20, EETheme.INPUT, true);
        ((EditBoxAccessor) search)
                .ee$setTextY(search.getY() + (search.getHeight() - font.lineHeight) / 2);
        EETextField.validationFrame(search, listX, top + 56, listWidth, 20);
        EETheme.bevel(
                g,
                listX - 1,
                gridTop - 1,
                listWidth + 2,
                gridBottom - gridTop + 2,
                EETheme.INPUT,
                true);
        int first = catalogScroll * gridCols(),
                last = Math.min(filtered.size(), first + gridRows() * gridCols());
        int hover = gridIndex(mx, my);
        for (int i = first; i < last; i++) {
            int index = i - first,
                    x = gridX() + index % gridCols() * CELL_W,
                    y = gridTop + index / gridCols() * CELL_H;
            var pattern = filtered.get(i);
            boolean current = editing != null && editing.pattern.equals(pattern);
            if (i == hover || current)
                EETheme.rect(
                        g,
                        x + 1,
                        y + 1,
                        CELL_W - 2,
                        CELL_H - 2,
                        i == hover ? EETheme.CONTROL_HOVER : EETheme.CONTROL);
            g.bannerPattern(
                    flag,
                    DyeColor.WHITE,
                    new BannerPatternLayers(
                            List.of(new BannerPatternLayers.Layer(pattern, DyeColor.BLACK))),
                    x + 2,
                    y + 2,
                    x + CELL_W - 2,
                    y + CELL_H - 2);
        }
        if (filtered.isEmpty())
            g.text(font, "No matching patterns", listX + 6, gridTop + 12, EETheme.LIGHT, false);
        if (hover >= 0)
            g.setTooltipForNextFrame(
                    font,
                    font.split(
                            Component.literal(patternName(filtered.get(hover))),
                            Math.min(230, width - 24)),
                    mx,
                    my);
        catalogScrollbar.render(g, mx, my);
    }

    private void renderPalette(GuiGraphicsExtractor g, int mx, int my) {
        int x = paletteX(), y = paletteY();
        EETheme.bevel(g, x, y, paletteWidth(), paletteHeight(), EETheme.PANEL, false);
        g.text(font, basePalette ? "Base color" : "Layer color", x + 6, y + 6, EETheme.TEXT, false);
        DyeColor selectedColor = basePalette ? draft.displayedBase() : paletteLayer.color;
        for (int i = 0; i < 16; i++) {
            DyeColor color = DyeColor.values()[i];
            int cx = x + 6 + i % 4 * 22, cy = y + 18 + i / 4 * 22;
            boolean hover = GuiUtils.isHover(cx, cy, 20, 20, mx, my);
            EETheme.bevel(
                    g,
                    cx,
                    cy,
                    20,
                    20,
                    color == selectedColor
                            ? EETheme.ON
                            : hover ? EETheme.CONTROL_HOVER : EETheme.CONTROL,
                    false);
            EETheme.rect(g, cx + 3, cy + 3, 14, 14, 0xFF000000 | color.getTextureDiffuseColor());
            if (hover)
                g.setTooltipForNextFrame(
                        font,
                        font.split(Component.literal(dyeName(color)), Math.min(230, width - 24)),
                        mx,
                        my);
        }
        if (basePalette && draft.shield()) {
            int cy = y + 108;
            boolean enabled = draft.layers().isEmpty(),
                    hover = GuiUtils.isHover(x + 6, cy, 88, 20, mx, my);
            EETheme.bevel(
                    g,
                    x + 6,
                    cy,
                    88,
                    20,
                    hover && enabled ? EETheme.CONTROL_HOVER : EETheme.CONTROL,
                    false);
            g.item(new ItemStack(Items.SHIELD), x + 10, cy + 2);
            g.text(font, "Wood", x + 30, cy + 6, enabled ? EETheme.TEXT : EETheme.MUTED, false);
            if (hover)
                g.setTooltipForNextFrame(
                        font,
                        font.split(
                                Component.literal(
                                        enabled
                                                ? "Restore the shield's original wooden surface."
                                                : "Remove all pattern layers before restoring the"
                                                        + " wooden surface."),
                                Math.min(230, width - 24)),
                        mx,
                        my);
        }
    }

    private void renderOptions(GuiGraphicsExtractor g, int mx, int my) {
        int x = optionsX(), y = optionsY();
        EETheme.bevel(g, x - 1, y - 1, 148, 102, EETheme.OPTION, false);
        for (int i = 0; i < 5; i++) {
            int rowY = y + i * 20;
            boolean hover = GuiUtils.isHover(x, rowY, 146, 20, mx, my) || i == optionFocus;
            String reason = optionReason(i);
            EEDropdownButton.renderOption(
                    g,
                    font,
                    Component.literal(optionLabels()[i]),
                    ItemStack.EMPTY,
                    x,
                    rowY,
                    146,
                    20,
                    false,
                    hover);
            if (reason != null)
                g.text(font, optionLabels()[i], x + 8, rowY + 6, EETheme.MUTED, false);
            if (hover) {
                String help =
                        reason == null
                                ? switch (i) {
                                    case 0 ->
                                            "Copy the selected pattern and color into a layer in"
                                                    + " front of it.";
                                    case 1 -> "Draw the selected layer over the next layer.";
                                    case 2 -> "Draw the selected layer behind the previous layer.";
                                    case 3 -> "Restore the preview's rotation and zoom.";
                                    default ->
                                            "Remove all pattern layers. The base color stays the"
                                                    + " same.";
                                }
                                : reason;
                g.setTooltipForNextFrame(
                        font,
                        font.split(Component.literal(help), Math.min(230, width - 24)),
                        mx,
                        my);
            }
        }
    }

    private String fit(String text, int maxWidth) {
        return font.width(text) <= maxWidth
                ? text
                : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static String dyeName(DyeColor color) {
        return I18n.get("color.minecraft." + color.getName());
    }

    private static String patternId(Holder<BannerPattern> pattern) {
        return pattern.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(pattern.value().assetId().toString());
    }

    private static String patternName(Holder<BannerPattern> pattern) {
        String key = pattern.value().translationKey(),
                suffix =
                        key.startsWith("block.minecraft.banner.")
                                ? key.substring("block.minecraft.banner.".length())
                                : pattern.value().assetId().getPath();
        String itemKey = "item.minecraft." + suffix + "_banner_pattern.desc",
                itemName = I18n.get(itemKey);
        if (!itemName.equals(itemKey)) return itemName;
        String whiteKey = key + ".white", white = I18n.get(whiteKey);
        if (!white.equals(whiteKey))
            return white.replace(dyeName(DyeColor.WHITE) + " ", "")
                    .replace(" " + dyeName(DyeColor.WHITE), "")
                    .trim();
        StringBuilder name = new StringBuilder();
        for (String word : suffix.split("_")) {
            if (!name.isEmpty()) name.append(' ');
            if (!word.isEmpty())
                name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    private static ItemStack patternIcon(Holder<BannerPattern> pattern, DyeColor color) {
        ItemStack icon = new ItemStack(Items.BANNER.white());
        icon.set(
                DataComponents.BANNER_PATTERNS,
                new BannerPatternLayers(List.of(new BannerPatternLayers.Layer(pattern, color))));
        return icon;
    }
}
