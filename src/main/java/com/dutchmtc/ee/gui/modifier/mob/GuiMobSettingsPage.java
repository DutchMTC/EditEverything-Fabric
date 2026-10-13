package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EETextField;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.gui.modifier.GuiModifier;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.GuiUtils;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared compact panel for mob properties and the groups that expose additional properties. */
abstract class GuiMobSettingsPage extends GuiModifier<CompoundTag> {
    protected static final int TEXT = 0xFF303830, MUTED = 0xFF596359;
    protected final MobPropertyEditorState state;
    protected final List<SettingsRow> rows = new ArrayList<>();
    private final boolean extras;
    private final List<SettingsRow> filtered = new ArrayList<>();
    private EditBox search;
    private EEButton clearSearch, apply;
    private String query = "";
    private int left, top, panelWidth, panelHeight, viewTop, viewBottom, scroll, contentHeight;
    private boolean draggingScroll;
    private double scrollGrab;

    protected GuiMobSettingsPage(
            Screen parent,
            Consumer<CompoundTag> setter,
            MobPropertyEditorState state,
            boolean extras) {
        super(parent, Component.literal("Mob settings"), setter);
        this.state = state;
        this.extras = extras;
    }

    protected abstract void buildRows();

    @Override
    public boolean isModified() {
        return !state.tag.equals(state.originalTag);
    }

    @Override
    public void init() {
        clearFocus();
        clearWidgets();
        rows.clear();
        apply = null;
        panelWidth = Math.min(500, width - 16);
        panelHeight = Math.min(350, height - 16);
        buildRows();
        panelHeight =
                Math.min(
                        panelHeight,
                        Math.max(200, 141 + rows.stream().mapToInt(SettingsRow::height).sum()));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        viewTop = top + 95;
        viewBottom = top + panelHeight - 46;
        int tabsWidth = (panelWidth - 28) / 2;
        EEButton properties =
                addRenderableWidget(
                        new EEButton(
                                left + 12,
                                top + 31,
                                tabsWidth,
                                20,
                                Component.literal("Properties"),
                                b ->
                                        mc.gui.setScreen(
                                                new GuiMobFieldsEditor(parent, setter, state))));
        properties.active = extras;
        EEButton groups =
                addRenderableWidget(
                        new EEButton(
                                left + 16 + tabsWidth,
                                top + 31,
                                tabsWidth,
                                20,
                                Component.literal("Extra properties"),
                                b ->
                                        mc.gui.setScreen(
                                                new GuiMobFeatureSetsEditor(
                                                        parent, setter, state))));
        groups.active = !extras;
        groups.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Choose which additional property groups appear in Properties.")));

        search =
                new EETextField(
                        font,
                        left + 17,
                        top + 59,
                        panelWidth - 62,
                        18,
                        Component.literal("Search properties"));
        search.setBordered(false);
        search.setTextShadow(false);
        search.setMaxLength(128);
        search.setHint(
                Component.literal(extras ? "Search extra properties..." : "Search properties...")
                        .withColor(0xAEBBB5));
        search.setValue(query);
        addRenderableWidget(search);
        clearSearch =
                addRenderableWidget(
                        new EEButton(
                                left + panelWidth - 37,
                                top + 59,
                                22,
                                18,
                                Component.literal("×"),
                                b -> {
                                    search.setValue("");
                                    setFocused(search);
                                }));
        clearSearch.setTooltip(Tooltip.create(Component.literal("Clear search")));
        search.setResponder(
                value -> {
                    query = value;
                    scroll = 0;
                    filterRows();
                });
        for (SettingsRow row : rows) if (row.control != null) addWidget(row.control);
        filterRows();
        int footer = top + panelHeight - 27;
        addRenderableWidget(
                new EEButton(
                        left + 12,
                        footer,
                        tabsWidth,
                        20,
                        Component.literal("Cancel"),
                        b -> onCancel()));
        apply =
                addRenderableWidget(
                        new EEButton(
                                left + 16 + tabsWidth,
                                footer,
                                tabsWidth,
                                20,
                                Component.literal("Apply settings"),
                                b -> applySettings()));
        draggingScroll = false;
        refreshValidity();
    }

    @Override
    protected void setInitialFocus() {}

    private void applySettings() {
        if (!valid()) return;
        setter.accept(state.tag.copy());
        mc.gui.setScreen(parent);
    }

    protected void refreshValidity() {
        if (apply != null) apply.active = valid();
    }

    private boolean valid() {
        return rows.stream().allMatch(row -> row.valid.getAsBoolean());
    }

    protected final class SettingsRow {
        final String label, detail, help;
        final AbstractWidget control;
        final int preferredWidth;
        final BooleanSupplier valid;
        final boolean separator;
        int y;

        SettingsRow(
                String label,
                String detail,
                String help,
                AbstractWidget control,
                int preferredWidth,
                BooleanSupplier valid,
                boolean separator) {
            this.label = label;
            this.detail = detail;
            this.help = help;
            this.control = control;
            this.preferredWidth = preferredWidth;
            this.valid = valid;
            this.separator = separator;
            if (control != null && help != null && !help.isBlank())
                control.setTooltip(Tooltip.create(Component.literal(help)));
        }

        int height() {
            return separator ? 8 : 34;
        }

        boolean matches(String value) {
            return !separator
                    && (label + " " + detail + " " + (help == null ? "" : help))
                            .toLowerCase(Locale.ROOT)
                            .contains(value);
        }
    }

    protected SettingsRow row(
            String label, String detail, String help, AbstractWidget control, int controlWidth) {
        return row(label, detail, help, control, controlWidth, () -> true);
    }

    protected SettingsRow row(
            String label,
            String detail,
            String help,
            AbstractWidget control,
            int controlWidth,
            BooleanSupplier valid) {
        SettingsRow row = new SettingsRow(label, detail, help, control, controlWidth, valid, false);
        rows.add(row);
        return row;
    }

    protected void separator() {
        rows.add(new SettingsRow("", "", null, null, 0, () -> true, true));
    }

    protected EEButton toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        return new EEButton(
                0,
                0,
                52,
                20,
                Component.literal(label),
                b -> {
                    setter.accept(!getter.getAsBoolean());
                    b.setMessage(
                            Component.literal(
                                    label + ": " + (getter.getAsBoolean() ? "On" : "Off")));
                }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                boolean on = getter.getAsBoolean();
                setMessage(Component.literal(label + ": " + (on ? "On" : "Off")));
                bevel(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        EETheme.controlColor(on, isHoveredOrFocused(), active));
                String text = on ? "On" : "Off";
                g.text(
                        font,
                        text,
                        getX() + (getWidth() - font.width(text)) / 2,
                        getY() + 6,
                        on ? 0xFFFFFFFF : TEXT,
                        false);
            }
        };
    }

    protected EEButton valueButton(Supplier<String> value, Runnable action) {
        return new EEButton(
                0,
                0,
                156,
                20,
                Component.literal(value.get()),
                b -> {
                    if (b.active) action.run();
                }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                String text = value.get();
                setMessage(Component.literal(text));
                bevel(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        EETheme.controlColor(false, isHoveredOrFocused(), active));
                g.text(
                        font,
                        fit(text, getWidth() - 25),
                        getX() + 6,
                        getY() + 6,
                        active ? TEXT : EETheme.MUTED,
                        false);
                rect(g, getRight() - 18, getY() + 3, 1, 14, 0xFF879180);
                g.text(font, ">", getRight() - 12, getY() + 6, TEXT, false);
                if (isHoveredOrFocused() && font.width(text) > getWidth() - 25)
                    g.setTooltipForNextFrame(
                            font,
                            font.split(Component.literal(text), Math.min(240, width - 24)),
                            mx,
                            my);
            }
        };
    }

    protected EditBox input(String label, String initial) {
        EditBox field = new EETextField(font, 0, 0, 102, 20, Component.literal(label));
        field.setBordered(false);
        field.setTextShadow(false);
        field.setTextColor(0xFFF1F3EF);
        field.setMaxLength(1024);
        field.setValue(initial);
        return field;
    }

    protected static String friendly(String value) {
        String spaced = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return spaced.isEmpty()
                ? spaced
                : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private void filterRows() {
        filtered.clear();
        String term = query.trim().toLowerCase(Locale.ROOT);
        for (SettingsRow row : rows) if (term.isEmpty() || row.matches(term)) filtered.add(row);
        clearSearch.visible = !query.isEmpty();
        contentHeight = filtered.stream().mapToInt(SettingsRow::height).sum();
        layoutRows();
    }

    private void layoutRows() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        for (SettingsRow row : rows) if (row.control != null) row.control.visible = false;
        int y = viewTop - scroll;
        for (SettingsRow row : filtered) {
            row.y = y;
            if (row.control != null) {
                int controlWidth = Math.min(row.preferredWidth, (panelWidth - 45) / 2);
                boolean field = row.control instanceof EditBox;
                row.control.setWidth(controlWidth - (field ? 8 : 0));
                row.control.setX(left + panelWidth - 25 - controlWidth + (field ? 4 : 0));
                row.control.setY(y + 6);
                row.control.visible = y + 32 > viewTop && y < viewBottom;
            }
            y += row.height();
        }
        for (SettingsRow row : rows)
            if (row.control != null && !row.control.visible && row.control.isFocused())
                clearFocus();
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (viewBottom - viewTop));
    }

    private boolean insideView(double x, double y) {
        return x >= left + 12 && x < left + panelWidth - 12 && y >= viewTop && y < viewBottom;
    }

    private void scrollTo(double y) {
        int h = viewBottom - viewTop;
        int thumb = Math.max(16, h * h / Math.max(1, contentHeight));
        scroll = (int) ((y - viewTop - scrollGrab) * maxScroll() / Math.max(1, h - thumb));
        layoutRows();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && maxScroll() > 0
                && event.x() >= left + panelWidth - 20
                && event.x() < left + panelWidth - 11
                && event.y() >= viewTop
                && event.y() < viewBottom) {
            int h = viewBottom - viewTop, thumb = Math.max(16, h * h / contentHeight);
            double thumbTop = viewTop + (h - thumb) * (double) scroll / maxScroll();
            scrollGrab =
                    event.y() >= thumbTop && event.y() < thumbTop + thumb
                            ? event.y() - thumbTop
                            : thumb / 2.0;
            clearFocus();
            draggingScroll = true;
            scrollTo(event.y());
            return true;
        }
        if (insideView(event.x(), event.y())) return super.mouseClicked(event, doubleClick);
        for (SettingsRow row : rows) if (row.control != null) row.control.visible = false;
        boolean handled = super.mouseClicked(event, doubleClick);
        layoutRows();
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScroll && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            scrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (insideView(mx, my)) {
            scroll += (int) Math.round(-vertical * 34);
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDown() && event.key() == InputConstants.KEY_F) {
            setFocused(search);
            search.moveCursorToStart(false);
            search.moveCursorToEnd(true);
            return true;
        }
        if (event.hasControlDown() && event.key() == InputConstants.KEY_S) {
            applySettings();
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEUP
                || event.key() == InputConstants.KEY_PAGEDOWN) {
            scroll +=
                    (event.key() == InputConstants.KEY_PAGEDOWN ? 1 : -1)
                            * (viewBottom - viewTop - 12);
            layoutRows();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        refreshValidity();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        bevel(g, left, top, panelWidth, panelHeight, 0xFFC6C6C6);
        g.text(font, title, left + 12, top + 12, TEXT, false);
        String mob =
                BuiltInRegistries.ENTITY_TYPE
                        .getOptional(Identifier.fromNamespaceAndPath("minecraft", state.entityPath))
                        .map(type -> type.getDescription().getString())
                        .orElse(friendly(state.entityPath));
        mob = fit(mob, panelWidth - 116);
        g.text(font, mob, left + panelWidth - 12 - font.width(mob), top + 12, MUTED, false);
        inputFrame(g, left + 12, top + 58, panelWidth - 24, 20);
        g.text(
                font,
                extras ? "Choose extra groups to show in Properties." : "Property",
                left + 16,
                top + 83,
                MUTED,
                false);
        if (!extras)
            g.text(
                    font,
                    "Value",
                    left + panelWidth - 29 - font.width("Value"),
                    top + 83,
                    MUTED,
                    false);
        g.enableScissor(left + 12, viewTop, left + panelWidth - 22, viewBottom);
        int index = 0;
        for (SettingsRow row : filtered) {
            int stripe = index++;
            if (row.y + row.height() <= viewTop || row.y >= viewBottom) continue;
            if (row.separator) {
                rect(g, left + 16, row.y + 4, panelWidth - 42, 1, 0xFF999F96);
                continue;
            }
            boolean hover = insideView(mx, my) && my >= row.y && my < row.y + 32;
            rect(
                    g,
                    left + 12,
                    row.y,
                    panelWidth - 34,
                    32,
                    hover ? 0xFFCDD3C8 : (stripe % 2 == 0 ? 0xFFBEC3BA : 0xFFB8BEB4));
            int labelWidth = row.control == null ? panelWidth - 50 : row.control.getX() - left - 24;
            boolean showDetail =
                    extras && !row.detail.isEmpty() && !row.detail.equalsIgnoreCase(row.label);
            g.text(
                    font,
                    fit(row.label, labelWidth),
                    left + 19,
                    row.y + (showDetail ? 6 : 12),
                    TEXT,
                    false);
            if (showDetail)
                g.text(font, fit(row.detail, labelWidth), left + 19, row.y + 18, MUTED, false);
            if (row.control != null) {
                if (row.control instanceof EditBox field) {
                    EETextField.validationFrame(
                            field, field.getX() - 4, field.getY(), field.getWidth() + 8, 20);
                    inputFrame(g, field.getX() - 4, field.getY(), field.getWidth() + 8, 20);
                    ((EditBoxAccessor) field).ee$setTextY(field.getY() + 6);
                }
                row.control.extractRenderState(
                        g, insideView(mx, my) ? mx : -1, insideView(mx, my) ? my : -1, delta);
            }
            if (hover && (row.control == null || mx < row.control.getX() - 5)) {
                String help =
                        row.help == null || row.help.isBlank()
                                ? font.width(row.label) > labelWidth ? row.label : null
                                : row.help;
                if (help != null)
                    g.setTooltipForNextFrame(
                            font,
                            font.split(Component.literal(help), Math.min(240, width - 24)),
                            mx,
                            my);
            }
        }
        g.disableScissor();
        if (filtered.isEmpty())
            g.text(font, "No matching properties", left + 16, viewTop + 12, MUTED, false);
        if (maxScroll() > 0) {
            int h = viewBottom - viewTop, thumb = Math.max(16, h * h / contentHeight);
            int barX = left + panelWidth - 17;
            EETheme.scrollbar(
                    g,
                    barX,
                    viewTop,
                    4,
                    h,
                    viewTop + (h - thumb) * scroll / maxScroll(),
                    thumb,
                    mx >= barX - 3 && mx < barX + 7 && my >= viewTop && my < viewBottom);
        }
        rect(g, left + 12, top + panelHeight - 42, panelWidth - 24, 1, 0xFF969E91);
        if (!valid())
            g.text(
                    font,
                    "Enter valid values before applying.",
                    left + 12,
                    top + panelHeight - 37,
                    EETheme.ERROR_LABEL,
                    false);
        ((EditBoxAccessor) search).ee$setTextY(search.getY() + 5);
        super.extractRenderState(g, mx, my, delta);
    }

    protected String fit(String text, int width) {
        return font.width(text) <= width
                ? text
                : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private static void inputFrame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, 0xFF555D53);
        rect(g, x + 1, y + 1, w - 2, h - 2, 0xFF343B39);
        rect(g, x, y + h - 1, w, 1, 0xFFF1F3EF);
        rect(g, x + w - 1, y, 1, h, 0xFFF1F3EF);
    }

    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        EETheme.bevel(g, x, y, w, h, fill, false);
    }

    private static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        GuiUtils.drawRect(g, x, y, x + w, y + h, color);
    }
}
