package com.dutchmtc.ee.gui.components;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.modifier.GuiModifier;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.GuiUtils;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.*;

/** Compact, scrolling settings forms with shared inputs, toggles and dropdowns. */
public abstract class EESettingsScreen<T> extends GuiModifier<T> {
    protected final List<Row> rows = new ArrayList<>();
    protected final Map<String, String> values = new HashMap<>(), errors = new LinkedHashMap<>();
    protected int left, top, panelWidth, panelHeight, viewTop, viewBottom, scroll;
    private final String[] tabs;
    protected int tab;
    private EEButton apply;
    private boolean dragging;
    private double dragOffset;
    private AbstractWidget menuAnchor;
    private List<String> menuLabels;
    private IntConsumer menuChoice;
    private int menuOffset, menuFocus;
    private final EEDropdownScrollbar menuScrollbar = new EEDropdownScrollbar();
    private int[] embeddedBounds;

    /** Reuse the same form inside an owning screen, without a second header or Apply/Cancel. */
    public final void embed(int x, int y, int w, int h, int screenWidth, int screenHeight) {
        embeddedBounds = new int[] {x, y, w, h};
        init(screenWidth, screenHeight);
    }

    public final boolean hasOpenMenu() {
        return menuAnchor != null;
    }

    protected final void dismissMenu() {
        menuAnchor = null;
        menuScrollbar.reset();
    }

    protected EESettingsScreen(Screen parent, String title, Consumer<T> setter, String... tabs) {
        super(parent, Component.literal(title), setter);
        this.tabs = tabs;
    }

    protected abstract void buildRows();

    protected abstract T result();

    /** Focused forms can stay compact without changing the shared control sizing rules. */
    protected int preferredWidth() {
        return 500;
    }

    protected String applyLabel() {
        return "Apply";
    }

    protected String invalidReason() {
        return errors.entrySet().stream()
                .filter(e -> relevant(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    protected boolean relevant(String key) {
        return true;
    }

    protected ItemStack preview() {
        return ItemStack.EMPTY;
    }

    protected void restoreDefaults() {}

    protected boolean canRestoreDefaults() {
        return false;
    }

    @Override
    public void init() {
        clearFocus();
        clearWidgets();
        rows.clear();
        menuAnchor = null;
        apply = null;
        dragging = false;
        panelWidth =
                embeddedBounds == null ? Math.min(preferredWidth(), width - 16) : embeddedBounds[2];
        buildRows();
        if (embeddedBounds != null) {
            left = embeddedBounds[0];
            top = embeddedBounds[1];
            panelHeight = embeddedBounds[3];
            viewTop = top + 4;
            viewBottom = top + panelHeight - 4;
            for (Row row : rows) {
                addWidget(row.control);
                if (row.remove != null) addWidget(row.remove);
            }
            layout();
            refreshValidity();
            return;
        }
        panelHeight =
                Math.min(
                        Math.min(360, height - 16),
                        Math.max(144, contentHeight() + (tabs.length == 0 ? 82 : 108)));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        viewTop = top + (tabs.length == 0 ? 37 : 63);
        viewBottom = top + panelHeight - 39;
        if (tabs.length > 0) {
            int available = panelWidth - 24 - (tabs.length - 1) * 4;
            int needed = Arrays.stream(tabs).mapToInt(t -> font.width(t) + 14).sum();
            int extra = Math.max(0, available - needed) / tabs.length;
            int x = left + 12;
            for (int i = 0; i < tabs.length; i++) {
                int w =
                        needed <= available
                                ? font.width(tabs[i]) + 14 + extra
                                : available / tabs.length;
                int index = i;
                EEButton button =
                        addRenderableWidget(
                                new EEButton(
                                        x,
                                        top + 31,
                                        w,
                                        20,
                                        Component.literal(tabs[i]),
                                        b -> {
                                            tab = index;
                                            scroll = 0;
                                            rebuild();
                                        }));
                button.active = i != tab;
                x += w + 4;
            }
        }
        for (Row row : rows) {
            addWidget(row.control);
            if (row.remove != null) addWidget(row.remove);
        }
        layout();
        int restoreWidth = canRestoreDefaults() ? 48 : 0;
        int w = (panelWidth - 28 - restoreWidth) / 2;
        if (canRestoreDefaults()) {
            EEButton reset =
                    addRenderableWidget(
                            new EEButton(
                                    left + 12,
                                    top + panelHeight - 27,
                                    44,
                                    20,
                                    Component.literal("Reset"),
                                    b ->
                                            mc.gui.setScreen(
                                                    new GuiConfirmation(
                                                            this,
                                                            Component.literal(
                                                                    "Restore "
                                                                            + (tabs.length == 0
                                                                                    ? "these"
                                                                                          + " settings"
                                                                                    : tabs[tab]
                                                                                            .toLowerCase(
                                                                                                    Locale
                                                                                                            .ROOT))
                                                                            + " to defaults?"),
                                                            Component.literal("Restore defaults"),
                                                            () -> {
                                                                restoreDefaults();
                                                                mc.gui.setScreen(this);
                                                            },
                                                            () -> mc.gui.setScreen(this)))));
            reset.setTooltip(
                    Tooltip.create(Component.literal("Restore defaults for this section.")));
        }
        addRenderableWidget(
                new EEButton(
                        left + 12 + restoreWidth,
                        top + panelHeight - 27,
                        w,
                        20,
                        Component.literal("Cancel"),
                        b -> onCancel()));
        apply =
                addRenderableWidget(
                        new EEButton(
                                left + 16 + restoreWidth + w,
                                top + panelHeight - 27,
                                w,
                                20,
                                Component.literal(applyLabel()),
                                b -> applyChanges()));
        refreshValidity();
    }

    protected final void rebuild() {
        init();
    }

    @Override
    protected void setInitialFocus() {}

    @Override
    public void onClose() {
        onCancel();
    }

    private void applyChanges() {
        if (invalidReason() != null) return;
        setter.accept(result());
        mc.gui.setScreen(parent);
    }

    protected void refreshValidity() {
        if (apply != null) {
            String error = invalidReason();
            apply.active = error == null;
            apply.setTooltip(error == null ? null : Tooltip.create(Component.literal(error)));
        }
    }

    protected final class Row {
        final String label, help;
        final AbstractWidget control;
        final int preferredWidth;
        final EEButton remove;
        int y;

        Row(
                String label,
                String help,
                AbstractWidget control,
                int preferredWidth,
                Runnable remove) {
            this.label = label;
            this.help = help;
            this.control = control;
            this.preferredWidth = preferredWidth;
            control.setTooltip(Tooltip.create(Component.literal(help)));
            this.remove =
                    remove == null
                            ? null
                            : new EEButton(
                                    0,
                                    0,
                                    20,
                                    20,
                                    Component.literal("×").withColor(0xFF5555),
                                    b -> remove.run());
            if (this.remove != null)
                this.remove.setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        "Remove " + label.toLowerCase(Locale.ROOT) + ".")));
        }
    }

    protected final void row(String label, String help, AbstractWidget control, int width) {
        row(label, help, control, width, null);
    }

    protected final void row(
            String label, String help, AbstractWidget control, int width, Runnable remove) {
        rows.add(new Row(label, help, control, width, remove));
    }

    protected final EEButton action(String label, Runnable run) {
        return new EEButton(0, 0, 140, 20, Component.literal(label), b -> run.run());
    }

    protected final EEButton readOnly(String value) {
        EEButton display =
                new EEButton(0, 0, 140, 20, Component.literal(value), b -> {}) {
                    @Override
                    protected void extractContents(
                            GuiGraphicsExtractor g, int mx, int my, float delta) {
                        g.text(font, getMessage(), getX() + 6, getY() + 6, EETheme.MUTED, false);
                    }
                };
        display.active = false;
        return display;
    }

    protected final EEButton toggle(BooleanSupplier get, Consumer<Boolean> set) {
        return new EEButton(
                0,
                0,
                52,
                20,
                Component.empty(),
                b -> {
                    set.accept(!get.getAsBoolean());
                    rebuild();
                }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                boolean on = get.getAsBoolean();
                setMessage(Component.literal(on ? "On" : "Off"));
                EETheme.bevel(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        EETheme.controlColor(on, isHoveredOrFocused(), active),
                        false);
                g.text(
                        font,
                        getMessage(),
                        getX() + (getWidth() - font.width(getMessage())) / 2,
                        getY() + 6,
                        on ? EETheme.LIGHT : EETheme.TEXT,
                        false);
            }
        };
    }

    protected final EditBox text(String key, String initial, Consumer<String> change) {
        EditBox field = new EETextField(font, 0, 0, 100, 18, Component.literal(key));
        field.setBordered(false);
        field.setTextShadow(false);
        field.setTextColor(EETheme.LIGHT);
        field.setMaxLength(256);
        field.setValue(values.computeIfAbsent(key, k -> initial));
        EETextField.validate(field, errors.get(key));
        field.setResponder(
                value -> {
                    values.put(key, value);
                    change.accept(value);
                    EETextField.validate(field, errors.get(key));
                    refreshValidity();
                });
        return field;
    }

    protected final void setFieldValue(String key, String value) {
        values.put(key, value);
        for (var child : children())
            if (child instanceof EditBox field && field.getMessage().getString().equals(key))
                field.setValue(value);
    }

    protected final EditBox number(
            String key,
            String initial,
            double min,
            double max,
            boolean integer,
            DoubleConsumer change) {
        EditBox field =
                text(
                        key,
                        initial,
                        value -> {
                            try {
                                double number = Double.parseDouble(value);
                                if (!Double.isFinite(number)
                                        || number < min
                                        || number > max
                                        || integer && number != Math.rint(number))
                                    throw new NumberFormatException();
                                errors.remove(key);
                                change.accept(number);
                            } catch (NumberFormatException error) {
                                errors.put(
                                        key,
                                        "Enter "
                                                + (integer ? "a whole number" : "a number")
                                                + " from "
                                                + format(min)
                                                + " to "
                                                + format(max)
                                                + ".");
                            }
                        });
        try {
            double value = Double.parseDouble(field.getValue());
            if (!Double.isFinite(value)
                    || value < min
                    || value > max
                    || integer && value != Math.rint(value)) throw new NumberFormatException();
            errors.remove(key);
        } catch (NumberFormatException ex) {
            errors.put(
                    key,
                    "Enter "
                            + (integer ? "a whole number" : "a number")
                            + " from "
                            + format(min)
                            + " to "
                            + format(max)
                            + ".");
        }
        EETextField.validate(field, errors.get(key));
        return field;
    }

    protected final <V> EEDropdownButton dropdown(
            String label, List<V> options, Function<V, String> names, Consumer<V> choose) {
        return new EEDropdownButton(
                0,
                0,
                140,
                20,
                Component.literal(label),
                b -> {
                    clearFocus();
                    menuAnchor = b;
                    menuLabels = options.stream().map(names).toList();
                    menuOffset = menuFocus = 0;
                    menuScrollbar.reset();
                    menuChoice =
                            index -> {
                                choose.accept(options.get(index));
                                rebuild();
                            };
                });
    }

    protected static String friendly(String value) {
        String text = value.replace('_', ' ').replace('.', ' ').replace('/', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    protected static String format(double value) {
        return value == Math.rint(value) && value > Long.MIN_VALUE && value < Long.MAX_VALUE
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    protected static String format(float value) {
        return value == Math.rint(value) ? format((double) value) : Float.toString(value);
    }

    protected static String percent(float value) {
        return format(Math.round(value * 10000000.0) / 100000.0);
    }

    private int contentHeight() {
        return rows.size() * 30;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (viewBottom - viewTop));
    }

    private int thumbHeight() {
        int h = viewBottom - viewTop;
        return Math.max(18, h * h / Math.max(h, contentHeight()));
    }

    private int thumbY() {
        return viewTop + (viewBottom - viewTop - thumbHeight()) * scroll / Math.max(1, maxScroll());
    }

    private void layout() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.y = viewTop + i * 30 - scroll;
            int w =
                    Math.min(
                            row.preferredWidth,
                            (panelWidth - 40) / 2 - (row.remove == null ? 0 : 24));
            row.control.setWidth(w);
            row.control.setX(left + panelWidth - 26 - w - (row.remove == null ? 0 : 24));
            row.control.setY(row.y + 5);
            row.control.visible = row.y + 25 > viewTop && row.y + 5 < viewBottom;
            if (!row.control.visible && row.control.isFocused()) clearFocus();
            if (row.remove != null) {
                row.remove.setX(left + panelWidth - 46);
                row.remove.setY(row.y + 5);
                row.remove.visible = row.control.visible;
            }
        }
    }

    private boolean insideView(double x, double y) {
        return x >= left + 12 && x < left + panelWidth - 12 && y >= viewTop && y < viewBottom;
    }

    private int menuRows() {
        int available = viewBottom - top - (embeddedBounds == null ? 31 : 0) - 4;
        return Math.min(menuLabels.size(), Math.max(1, Math.min(6, available / 20)));
    }

    private int menuX() {
        return Math.max(
                left + 12, Math.min(menuAnchor.getX(), left + panelWidth - menuWidth() - 12));
    }

    private int menuWidth() {
        return Math.min(panelWidth - 24, Math.max(140, menuAnchor.getWidth()));
    }

    private int menuY() {
        return Math.max(
                top + (embeddedBounds == null ? 31 : 0),
                Math.min(menuAnchor.getBottom() + 2, viewBottom - menuRows() * 20 - 4));
    }

    private void layoutMenuScrollbar() {
        menuScrollbar.layout(
                menuX() + menuWidth() - EEDropdownScrollbar.WIDTH,
                menuY() + 2,
                menuRows() * 20,
                menuRows(),
                menuLabels.size(),
                menuOffset,
                1);
    }

    private void scrollMenu(int offset) {
        menuOffset = offset;
        menuFocus = Math.clamp(menuFocus, menuOffset, menuOffset + menuRows() - 1);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menuAnchor != null) {
            layoutMenuScrollbar();
            if (menuScrollbar.mouseClicked(event, this::scrollMenu)) return true;
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && event.x() >= menuX()
                    && event.x() < menuX() + menuWidth()
                    && event.y() >= menuY() + 2
                    && event.y() < menuY() + 2 + menuRows() * 20) {
                menuChoice.accept(menuOffset + (int) (event.y() - menuY() - 2) / 20);
            } else menuAnchor = null;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && maxScroll() > 0
                && event.x() >= left + panelWidth - 21
                && event.x() < left + panelWidth - 10
                && event.y() >= viewTop
                && event.y() < viewBottom) {
            clearFocus();
            dragging = true;
            dragOffset =
                    event.y() >= thumbY() && event.y() < thumbY() + thumbHeight()
                            ? event.y() - thumbY()
                            : thumbHeight() / 2.0;
            dragScroll(event.y());
            return true;
        }
        if (!insideView(event.x(), event.y()))
            for (Row row : rows) {
                row.control.visible = false;
                if (row.remove != null) row.remove.visible = false;
            }
        boolean handled = super.mouseClicked(event, doubleClick);
        layout();
        return handled;
    }

    private void dragScroll(double y) {
        int travel = viewBottom - viewTop - thumbHeight();
        if (travel > 0)
            scroll = (int) Math.round((y - dragOffset - viewTop) / travel * maxScroll());
        layout();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (menuAnchor != null) {
            layoutMenuScrollbar();
            if (menuScrollbar.mouseDragged(event, this::scrollMenu)) return true;
        }
        if (dragging) {
            dragScroll(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (menuScrollbar.mouseReleased()) return true;
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (menuAnchor != null) {
            scrollMenu(
                    Math.max(
                            0,
                            Math.min(
                                    menuLabels.size() - menuRows(),
                                    menuOffset - (int) Math.signum(vertical))));
            return true;
        }
        if (insideView(x, y)) {
            scroll -= (int) (vertical * 30);
            layout();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (menuAnchor != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) menuAnchor = null;
            else if (event.key() == InputConstants.KEY_RETURN) menuChoice.accept(menuFocus);
            else if (event.key() == InputConstants.KEY_UP
                    || event.key() == InputConstants.KEY_DOWN) {
                menuFocus =
                        Math.max(
                                0,
                                Math.min(
                                        menuLabels.size() - 1,
                                        menuFocus
                                                + (event.key() == InputConstants.KEY_UP ? -1 : 1)));
                menuOffset = Math.max(Math.min(menuOffset, menuFocus), menuFocus - menuRows() + 1);
            }
            return true;
        }
        if (event.hasControlDown() && event.key() == InputConstants.KEY_S) {
            applyChanges();
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
        if (embeddedBounds == null) {
            GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
            EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
            ItemStack item = preview();
            int indent = item.isEmpty() ? 0 : 24;
            if (!item.isEmpty()) GuiUtils.drawItemStack(g, item, left + 12, top + 9);
            g.text(font, title, left + 12 + indent, top + 13, EETheme.TEXT, false);
        }
        g.enableScissor(left + 12, viewTop, left + panelWidth - 22, viewBottom);
        for (Row row : rows) {
            if (!row.control.visible) continue;
            boolean hover =
                    menuAnchor == null && insideView(mx, my) && my >= row.y && my < row.y + 30;
            if (hover) EETheme.rect(g, left + 12, row.y, panelWidth - 34, 30, 0xFFCdd3C8);
            int labelWidth = row.control.getX() - left - 23;
            g.text(font, fit(row.label, labelWidth), left + 16, row.y + 11, EETheme.TEXT, false);
            if (row.control instanceof EditBox field) {
                EETextField.validationFrame(
                        field, field.getX() - 4, field.getY(), field.getWidth() + 8, 18);
                EETheme.bevel(
                        g,
                        field.getX() - 4,
                        field.getY(),
                        field.getWidth() + 8,
                        18,
                        EETheme.INPUT,
                        true);
                ((EditBoxAccessor) field).ee$setTextY(field.getY() + 5);
            }
            row.control.extractRenderState(
                    g,
                    menuAnchor == null && insideView(mx, my) ? mx : -1,
                    menuAnchor == null && insideView(mx, my) ? my : -1,
                    delta);
            if (row.remove != null) row.remove.extractRenderState(g, mx, my, delta);
            if (hover && mx < row.control.getX() - 5)
                g.setTooltipForNextFrame(
                        font,
                        font.split(
                                Component.literal(row.label + ": " + row.help),
                                Math.min(240, width - 24)),
                        mx,
                        my);
        }
        g.disableScissor();
        if (rows.isEmpty())
            g.text(font, "No effects yet", left + 16, viewTop + 11, EETheme.MUTED, false);
        if (maxScroll() > 0)
            EETheme.scrollbar(
                    g,
                    left + panelWidth - 17,
                    viewTop,
                    4,
                    viewBottom - viewTop,
                    thumbY(),
                    thumbHeight(),
                    dragging);
        if (embeddedBounds == null)
            EETheme.rect(g, left + 12, viewBottom + 6, panelWidth - 24, 1, 0xFF969E91);
        renderWidgetsAndMenu(g, mx, my, delta);
    }

    protected final void renderWidgetsAndMenu(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(
                g, menuAnchor == null ? mx : -1, menuAnchor == null ? my : -1, delta);
        if (menuAnchor != null) {
            layoutMenuScrollbar();
            EETheme.bevel(
                    g,
                    menuX() - 1,
                    menuY(),
                    menuWidth() + 2,
                    menuRows() * 20 + 4,
                    EETheme.OPTION,
                    false);
            for (int i = 0; i < menuRows(); i++)
                EEDropdownButton.renderOption(
                        g,
                        font,
                        Component.literal(menuLabels.get(menuOffset + i)),
                        ItemStack.EMPTY,
                        menuX(),
                        menuY() + 2 + i * 20,
                        menuWidth() - (menuScrollbar.needed() ? EEDropdownScrollbar.WIDTH : 0),
                        20,
                        false,
                        menuOffset + i == menuFocus
                                || mx >= menuX()
                                        && mx < menuX() + menuWidth()
                                        && my >= menuY() + 2 + i * 20
                                        && my < menuY() + 22 + i * 20);
            menuScrollbar.render(g, mx, my);
        }
    }

    private String fit(String text, int width) {
        return font.width(text) <= width
                ? text
                : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }
}
