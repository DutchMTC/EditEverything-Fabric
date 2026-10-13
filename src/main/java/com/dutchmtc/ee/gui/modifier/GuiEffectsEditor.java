package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.*;
import com.dutchmtc.ee.utils.EffectListData.Entry;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

import java.util.*;
import java.util.function.*;

/** Shared inline effect rows, native catalog and draft-safe interaction for items and entities. */
abstract class GuiEffectsEditor<T> extends GuiModifier<T> {
    protected final EffectListData data;
    private final List<Controls> controls = new ArrayList<>();
    protected int left, top, panelWidth, panelHeight;
    private int listTop, listBottom, listRight, scroll;
    private static final int ROW = 26, CARD_HEIGHT = 48;
    private static final int TOGGLE_SIZE = 22, TOGGLE_STEP = 24;
    private int effectRow;
    private final EEDropdownScrollbar scrollbar = new EEDropdownScrollbar();
    private EEButton apply;
    private Popup popup;
    private boolean choosingEffect;
    private Entry changing;
    private List<Choice> catalog = List.of(), filtered = List.of();
    private String query = "";
    private EditBox search;
    private int catalogScroll, keyboardRow = -1;
    private final EEDropdownScrollbar catalogBar = new EEDropdownScrollbar();
    private Entry selected;

    private record Choice(String name, Identifier id) {}

    private record Controls(
            Entry entry,
            EEButton name,
            EditBox level,
            EditBox seconds,
            List<EEButton> toggles,
            EEButton remove) {
        List<AbstractWidget> widgets() {
            List<AbstractWidget> result = new ArrayList<>(List.of(name, level, seconds, remove));
            result.addAll(toggles);
            return result;
        }
    }

    protected GuiEffectsEditor(
            Screen parent, Component title, Consumer<T> setter, EffectListData data) {
        super(parent, title, setter);
        this.data = data;
    }

    protected abstract T result();

    protected int headerHeight() {
        return 32;
    }

    protected boolean showStatusIconToggle() {
        return true;
    }

    protected void buildHeader() {}

    protected void renderHeader(GuiGraphicsExtractor g, int mx, int my) {}

    protected void refreshPreview() {}

    protected void renderPreviewTooltip(GuiGraphicsExtractor g, int mx, int my) {}

    @Override
    protected void setInitialFocus() {}

    @Override
    protected void rebuildWidgets() {
        init();
    }

    @Override
    public void onClose() {
        if (choosing()) finishChoosing();
        else onCancel();
    }

    protected final boolean choosing() {
        return choosingEffect;
    }

    @Override
    public void init() {
        Entry focusEntry = null;
        boolean focusLevel = false;
        for (Controls c : controls)
            if (c.level.isFocused() || c.seconds.isFocused()) {
                focusEntry = c.entry;
                focusLevel = c.level.isFocused();
            }
        clearFocus();
        clearWidgets();
        controls.clear();
        popup = null;
        apply = null;
        scrollbar.reset();
        catalogBar.reset();
        panelWidth = Math.min(320, width - 16);
        effectRow = CARD_HEIGHT + 4;
        panelHeight =
                Math.min(
                        height - 16,
                        choosing()
                                ? 310
                                : Math.min(
                                        340,
                                        headerHeight()
                                                + 64
                                                + Math.max(1, data.effects.size()) * effectRow));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        listTop = top + (choosing() ? 63 : headerHeight() + 26);
        listBottom = top + panelHeight - 38;
        listRight = left + panelWidth - 12;
        if (choosing()) {
            search =
                    addRenderableWidget(
                            field(left + 18, top + 37, panelWidth - 36, "Search", query));
            search.setHint(Component.literal("Search effects...").withColor(0xAEBBB5));
            search.setResponder(
                    v -> {
                        query = v;
                        catalogScroll = 0;
                        keyboardRow = -1;
                        filter();
                    });
            addRenderableWidget(
                    action(
                            left + 12,
                            top + panelHeight - 28,
                            panelWidth - 24,
                            20,
                            "Back",
                            b -> finishChoosing()));
            filter();
            return;
        }
        refreshPreview();
        buildHeader();
        addRenderableWidget(
                action(
                        left + panelWidth - 108,
                        top + headerHeight() + 4,
                        96,
                        20,
                        "Add effect",
                        b -> beginChoosing(null)));
        for (Entry entry : data.effects) {
            EEButton name =
                    addWidget(
                            new EEButton(
                                    0,
                                    0,
                                    100,
                                    20,
                                    Component.literal(entry.effect.name()),
                                    b -> beginChoosing(entry)) {
                                @Override
                                protected void extractContents(
                                        GuiGraphicsExtractor g, int mx, int my, float delta) {
                                    EETheme.bevel(g, getX(), getY(), 20, 20, EETheme.INPUT, true);
                                    drawEffectIcon(g, entry.effect, getX() + 2, getY() + 2);
                                    g.text(
                                            font,
                                            fit(entry.effect.name(), getWidth() - 28),
                                            getX() + 22,
                                            getY() + 6,
                                            EETheme.TEXT,
                                            false);
                                    int color = EETheme.MUTED;
                                    for (int r = 0; r < 3; r++)
                                        EETheme.rect(
                                                g,
                                                getRight() - 7 + r,
                                                getY() + 9 + r,
                                                5 - r * 2,
                                                1,
                                                color);
                                }
                            });
            name.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    entry.effect.name()
                                            + "\n"
                                            + "Choose a different effect. Right-click to"
                                            + " duplicate.")));
            EditBox level = addWidget(field(0, 0, 20, "Level", entry.level));
            level.setMaxLength(12);
            level.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "Level 1 is the standard effect. Creative levels can reach"
                                            + " 256.")));
            level.setResponder(
                    v -> {
                        data.setLevel(entry, v);
                        selected = entry;
                        refreshValidity();
                        refreshPreview();
                    });
            EditBox seconds = addWidget(field(0, 0, 42, "Seconds", entry.seconds));
            seconds.setMaxLength(32);
            seconds.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "Duration in seconds. Decimals are allowed. The clock toggles"
                                            + " infinite duration.")));
            seconds.setResponder(
                    v -> {
                        data.setSeconds(entry, v);
                        selected = entry;
                        refreshValidity();
                        refreshPreview();
                    });
            List<EEButton> toggles = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                if (i == 2 && !showStatusIconToggle()) continue;
                toggles.add(addWidget(effectToggle(entry, i)));
            }
            EEButton remove =
                    addWidget(
                            new EEButton(
                                    0,
                                    0,
                                    20,
                                    20,
                                    Component.literal("×"),
                                    b -> {
                                        data.remove(entry);
                                        if (selected == entry) selected = null;
                                        init();
                                    }));
            remove.setTooltip(
                    Tooltip.create(Component.literal("Remove " + entry.effect.name() + ".")));
            controls.add(new Controls(entry, name, level, seconds, toggles, remove));
        }
        int w = (panelWidth - 28) / 2, footer = top + panelHeight - 28;
        addRenderableWidget(action(left + 12, footer, w, 20, "Cancel", b -> onCancel()));
        apply =
                addRenderableWidget(
                        action(
                                left + 16 + w,
                                footer,
                                w,
                                20,
                                "Apply",
                                b -> {
                                    if (data.error() != null) return;
                                    setter.accept(result());
                                    mc.gui.setScreen(parent);
                                }));
        layout();
        refreshValidity();
        if (focusEntry != null)
            for (Controls c : controls)
                if (c.entry == focusEntry) {
                    scroll =
                            Math.clamp(
                                    scroll,
                                    Math.max(
                                            0,
                                            (controls.indexOf(c) + 1) * effectRow
                                                    - (listBottom - listTop)),
                                    Math.min(maxScroll(), controls.indexOf(c) * effectRow));
                    layout();
                    setFocused(focusLevel ? c.level : c.seconds);
                }
    }

    protected EEButton action(int x, int y, int w, int h, String label, Button.OnPress press) {
        return new EEButton(x, y, w, h, Component.literal(label), press) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                EETheme.bevel(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        EETheme.controlColor(false, isHoveredOrFocused(), active),
                        false);
                g.text(
                        font,
                        getMessage(),
                        getX() + (getWidth() - font.width(getMessage())) / 2,
                        getY() + (getHeight() - font.lineHeight) / 2 + 1,
                        active ? EETheme.TEXT : EETheme.MUTED,
                        false);
            }
        };
    }

    private void refreshValidity() {
        controls.forEach(
                c -> {
                    for (EditBox field : List.of(c.level, c.seconds))
                        if (font.width(field.getValue()) < field.getWidth())
                            ((EditBoxAccessor) field).ee$setDisplayPos(0);
                    EETextField.validate(c.level, c.entry.levelError());
                    EETextField.validate(c.seconds, c.entry.durationError());
                });
        if (apply != null) {
            String reason = data.error();
            apply.active = reason == null;
            apply.setTooltip(reason == null ? null : Tooltip.create(Component.literal(reason)));
        }
    }

    private static boolean toggleState(Entry entry, int option) {
        return switch (option) {
            case 0 -> entry.effect.ticks == -1;
            case 1 -> entry.effect.particles;
            case 2 -> entry.effect.icon;
            default -> entry.effect.ambient;
        };
    }

    private EEButton effectToggle(Entry entry, int option) {
        String name =
                List.of("Infinite duration", "Particles", "Status icon", "Ambient particles")
                        .get(option);
        String explanation =
                List.of(
                                "The effect never expires.",
                                "Show particles around the entity.",
                                "Show the effect icon in the HUD.",
                                "Use the subtle particles associated with beacon effects.")
                        .get(option);
        EEButton button =
                new EEButton(
                        0,
                        0,
                        TOGGLE_SIZE,
                        TOGGLE_SIZE,
                        Component.literal(name),
                        b -> {
                            selected = entry;
                            if (entry.fromPotion) data.materialize();
                            switch (option) {
                                case 0 -> data.setInfinite(entry, entry.effect.ticks != -1);
                                case 1 -> entry.effect.particles = !entry.effect.particles;
                                case 2 -> entry.effect.icon = !entry.effect.icon;
                                case 3 -> entry.effect.ambient = !entry.effect.ambient;
                            }
                            init();
                        }) {
                    @Override
                    protected void extractContents(
                            GuiGraphicsExtractor g, int mx, int my, float delta) {
                        boolean on = toggleState(entry, option);
                        EETheme.rect(
                                g,
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                EETheme.controlColor(on, isHoveredOrFocused(), active));
                        EETheme.rect(g, getX(), getBottom() - 1, getWidth(), 1, EETheme.TRACK);
                        if (isFocused())
                            EETheme.bevel(
                                    g,
                                    getX(),
                                    getY(),
                                    getWidth(),
                                    getHeight(),
                                    EETheme.controlColor(on, true, active),
                                    false);
                        EEEffectIcon.values()[option].render(
                                g, getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4, active);
                        if (option == 2)
                            drawEffectIcon(
                                    g,
                                    entry.effect,
                                    getX() + (getWidth() - 16) / 2,
                                    getY() + (getHeight() - 16) / 2);
                    }
                };
        button.setTooltip(
                Tooltip.create(
                        Component.literal(
                                name
                                        + ": "
                                        + (toggleState(entry, option) ? "On" : "Off")
                                        + "\n"
                                        + explanation)));
        return button;
    }

    private EditBox field(int x, int y, int w, String label, String value) {
        EditBox field = new EETextField(font, x, y, w, 18, Component.literal(label));
        field.setBordered(false);
        field.setTextShadow(false);
        field.setTextColor(EETheme.LIGHT);
        field.setMaxLength(256);
        field.setValue(value);
        return field;
    }

    private void beginChoosing(Entry entry) {
        clearFocus();
        choosingEffect = true;
        changing = entry;
        query = "";
        catalogScroll = 0;
        keyboardRow = -1;
        List<Choice> choices = new ArrayList<>();
        BuiltInRegistries.MOB_EFFECT.forEach(
                effect ->
                        choices.add(
                                new Choice(
                                        effect.getDisplayName().getString(),
                                        BuiltInRegistries.MOB_EFFECT.getKey(effect))));
        choices.sort(Comparator.comparing(Choice::name, String.CASE_INSENSITIVE_ORDER));
        catalog = List.copyOf(choices);
        init();
        setFocused(search);
    }

    private void filter() {
        String q = query.strip().toLowerCase(Locale.ROOT);
        filtered =
                catalog.stream()
                        .filter(c -> (c.name + " " + c.id).toLowerCase(Locale.ROOT).contains(q))
                        .toList();
        catalogScroll =
                Math.clamp(
                        catalogScroll,
                        0,
                        Math.max(0, filtered.size() * ROW - (listBottom - listTop)));
        layoutCatalog();
    }

    private void choose(int index) {
        if (index < 0 || index >= filtered.size()) return;
        Choice choice = filtered.get(index);
        if (changing == null) {
            Entry entry =
                    new Entry(
                            EffectEditorData.create(
                                    BuiltInRegistries.MOB_EFFECT.wrapAsHolder(
                                            BuiltInRegistries.MOB_EFFECT
                                                    .getOptional(choice.id)
                                                    .orElseThrow())),
                            false);
            data.effects.add(entry);
            selected = entry;
            scroll = Integer.MAX_VALUE;
        } else {
            if (!changing.effect.id.equals(choice.id.toString())) {
                if (changing.fromPotion) data.materialize();
                changing.effect.id = choice.id.toString();
            }
            selected = changing;
        }
        finishChoosing();
        playClick();
    }

    private void finishChoosing() {
        choosingEffect = false;
        changing = null;
        init();
    }

    private int maxScroll() {
        return Math.max(0, data.effects.size() * effectRow - (listBottom - listTop));
    }

    private boolean listOverflows() {
        return choosing() ? filtered.size() * ROW > listBottom - listTop : maxScroll() > 0;
    }

    /** Selection includes the row's padding, but never paints over the scrollbar or bevel. */
    private int rowRight() {
        return listRight - (listOverflows() ? 14 : 1);
    }

    private int contentRight() {
        return listRight - 6 - (listOverflows() ? 14 : 0);
    }

    private void layout() {
        scroll = Math.clamp(scroll, 0, maxScroll());
        int cardRight = rowRight();
        for (int i = 0; i < controls.size(); i++) {
            Controls c = controls.get(i);
            int y = listTop + i * effectRow - scroll;
            c.name.setX(left + 20);
            c.name.setY(y + 3);
            c.remove.setX(cardRight - 26);
            c.remove.setY(y + 3);
            c.name.setWidth(c.remove.getX() - 6 - c.name.getX());
            c.level.setX(left + 53);
            c.level.setY(y + 26);
            c.seconds.setX(left + 113);
            c.seconds.setY(y + 26);
            int toggleX = cardRight - 8 - (TOGGLE_SIZE + (c.toggles.size() - 1) * TOGGLE_STEP);
            for (int t = 0; t < c.toggles.size(); t++) {
                c.toggles.get(t).setX(toggleX + t * TOGGLE_STEP);
                c.toggles.get(t).setY(y + 24);
            }
            for (AbstractWidget widget : c.widgets())
                widget.visible = widget.getBottom() > listTop && widget.getY() < listBottom;
            c.seconds.visible &= c.entry.effect.ticks != -1;
            c.seconds.active = c.entry.effect.ticks != -1;
        }
        scrollbar.layout(
                listRight - 14,
                listTop,
                listBottom - listTop,
                listBottom - listTop,
                data.effects.size() * effectRow,
                scroll,
                effectRow);
    }

    private void layoutCatalog() {
        catalogBar.layout(
                listRight - 14,
                listTop,
                listBottom - listTop,
                listBottom - listTop,
                filtered.size() * ROW,
                catalogScroll,
                ROW);
    }

    private void effectOptions(AbstractWidget anchor, Entry entry) {
        clearFocus();
        selected = entry;
        popup =
                new Popup(
                        anchor,
                        List.of("Duplicate effect"),
                        List.of(ItemStack.EMPTY),
                        i -> {
                            if (entry.error() != null) return;
                            data.effects.add(new Entry(entry.effect.copy(), false));
                            scroll = Integer.MAX_VALUE;
                            init();
                        });
        popup.entry = entry;
    }

    private final class Popup {
        final List<String> labels;
        final List<ItemStack> icons;
        final IntConsumer choose;
        final int x, y, w, shown;
        int offset, focus;
        final EEDropdownScrollbar bar = new EEDropdownScrollbar();
        Entry entry;

        Popup(
                AbstractWidget anchor,
                List<String> labels,
                List<ItemStack> icons,
                IntConsumer choose) {
            this.labels = labels;
            this.icons = icons;
            this.choose = choose;
            w = Math.min(panelWidth - 24, Math.max(196, anchor.getWidth()));
            shown = Math.min(labels.size(), Math.max(1, (listBottom - top - 34) / 20));
            x = Math.clamp(anchor.getX(), left + 12, left + panelWidth - 12 - w);
            y = Math.clamp(anchor.getBottom() + 2, top + 32, listBottom - shown * 20 - 4);
        }

        void layoutBar() {
            bar.layout(
                    x + w - EEDropdownScrollbar.WIDTH,
                    y + 2,
                    shown * 20,
                    shown,
                    labels.size(),
                    offset,
                    1);
        }

        String reason(int i) {
            return entry != null ? entry.error() : null;
        }

        void pick(int i) {
            if (reason(i) != null) return;
            popup = null;
            choose.accept(i);
        }

        boolean click(MouseButtonEvent event) {
            layoutBar();
            if (bar.mouseClicked(event, n -> offset = n)) return true;
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && event.x() >= x
                    && event.x() < x + w
                    && event.y() >= y + 2
                    && event.y() < y + 2 + shown * 20)
                pick(offset + (int) (event.y() - y - 2) / 20);
            else popup = null;
            return true;
        }

        void render(GuiGraphicsExtractor g, int mx, int my) {
            layoutBar();
            EETheme.bevel(g, x - 1, y, w + 2, shown * 20 + 4, EETheme.OPTION, false);
            for (int row = 0; row < shown; row++) {
                int i = offset + row, ry = y + 2 + row * 20;
                boolean hover =
                        mx >= x && mx < x + w - (bar.needed() ? 12 : 0) && my >= ry && my < ry + 20;
                String reason = reason(i);
                String label = labels.get(i);
                int cw = w - (bar.needed() ? 12 : 0);
                EEDropdownButton.renderOption(
                        g,
                        font,
                        Component.literal(label),
                        icons.get(i),
                        x,
                        ry,
                        cw,
                        20,
                        false,
                        hover);
                if (reason != null)
                    g.text(font, fit(label, cw - 16), x + 8, ry + 6, EETheme.BLOCKED, false);
                if (hover)
                    g.setTooltipForNextFrame(
                            font, Component.literal(reason != null ? reason : label), mx, my);
            }
            bar.render(g, mx, my);
        }
    }

    private boolean inside(double x, double y) {
        return x >= left + 13 && x < listRight - 1 && y >= listTop && y < listBottom;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (popup != null) return popup.click(event);
        if (choosing()) {
            layoutCatalog();
            if (catalogBar.mouseClicked(event, n -> catalogScroll = n)) return true;
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && inside(event.x(), event.y())
                    && event.x() < rowRight()) {
                choose(((int) event.y() - listTop + catalogScroll) / ROW);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (scrollbar.mouseClicked(
                event,
                n -> {
                    scroll = n;
                    layout();
                })) {
            clearFocus();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT
                && inside(event.x(), event.y())
                && event.x() < rowRight()) {
            int index = ((int) event.y() - listTop + scroll) / effectRow;
            int withinCard = ((int) event.y() - listTop + scroll) % effectRow;
            if (index < controls.size() && withinCard < CARD_HEIGHT)
                effectOptions(controls.get(index).name, controls.get(index).entry);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && inside(event.x(), event.y())
                && event.x() < rowRight()) {
            int offset = (int) event.y() - listTop + scroll;
            int index = offset / effectRow;
            if (index < controls.size() && offset % effectRow < CARD_HEIGHT)
                selected = controls.get(index).entry;
        }
        if (!inside(event.x(), event.y()))
            controls.forEach(
                    c -> {
                        c.widgets().forEach(widget -> widget.visible = false);
                    });
        boolean handled = super.mouseClicked(event, doubleClick);
        layout();
        if (getFocused() != null && !children().contains(getFocused())) setFocused(null);
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (popup != null) {
            popup.layoutBar();
            popup.bar.mouseDragged(event, n -> popup.offset = n);
            return true;
        }
        if (choosing())
            return catalogBar.mouseDragged(event, n -> catalogScroll = n)
                    || super.mouseDragged(event, dx, dy);
        return scrollbar.mouseDragged(
                        event,
                        n -> {
                            scroll = n;
                            layout();
                        })
                || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (popup != null) {
            popup.bar.mouseReleased();
            return true;
        }
        return (choosing() ? catalogBar : scrollbar).mouseReleased() || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (popup != null) {
            popup.offset =
                    Math.clamp(
                            popup.offset - (int) Math.signum(vertical),
                            0,
                            popup.labels.size() - popup.shown);
            return true;
        }
        if (inside(x, y)) {
            if (choosing()) {
                catalogScroll =
                        Math.clamp(
                                catalogScroll - (int) Math.round(vertical * ROW * 2),
                                0,
                                Math.max(0, filtered.size() * ROW - (listBottom - listTop)));
                layoutCatalog();
            } else {
                scroll =
                        Math.clamp(
                                scroll - (int) Math.round(vertical * effectRow * 2),
                                0,
                                maxScroll());
                layout();
            }
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (popup != null) {
            if (key == InputConstants.KEY_ESCAPE) popup = null;
            else if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
                popup.focus =
                        Math.clamp(
                                popup.focus + (key == InputConstants.KEY_UP ? -1 : 1),
                                0,
                                popup.labels.size() - 1);
                popup.offset =
                        Math.clamp(
                                popup.offset,
                                Math.max(0, popup.focus - popup.shown + 1),
                                Math.min(popup.focus, popup.labels.size() - popup.shown));
            } else if (key == InputConstants.KEY_RETURN) popup.pick(popup.focus);
            return true;
        }
        if (choosing()) {
            if (key == InputConstants.KEY_ESCAPE) {
                finishChoosing();
                return true;
            }
            if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
                keyboardRow =
                        Math.clamp(
                                keyboardRow + (key == InputConstants.KEY_UP ? -1 : 1),
                                0,
                                Math.max(0, filtered.size() - 1));
                catalogScroll =
                        Math.clamp(
                                catalogScroll,
                                Math.max(0, (keyboardRow + 1) * ROW - (listBottom - listTop)),
                                Math.min(
                                        keyboardRow * ROW,
                                        Math.max(
                                                0,
                                                filtered.size() * ROW - (listBottom - listTop))));
                layoutCatalog();
                return true;
            }
            if (key == InputConstants.KEY_RETURN) {
                choose(keyboardRow);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return popup != null || super.charTyped(event);
    }

    private String fit(String text, int w) {
        return font.width(text) <= w
                ? text
                : font.plainSubstrByWidth(text, Math.max(0, w - font.width("..."))) + "...";
    }

    private void inputSurface(GuiGraphicsExtractor g, EditBox field) {
        EETextField.validationFrame(
                field, field.getX() - 4, field.getY(), field.getWidth() + 8, 18);
        EETheme.rect(g, field.getX() - 4, field.getY(), field.getWidth() + 8, 18, EETheme.INPUT);
        EETheme.rect(
                g,
                field.getX() - 4,
                field.getBottom() - 1,
                field.getWidth() + 8,
                1,
                field.isFocused() ? EETheme.LIGHT : EETheme.TRACK);
        ((EditBoxAccessor) field).ee$setTextY(field.getY() + 5);
    }

    private void drawEffectIcon(GuiGraphicsExtractor g, EffectEditorData effect, int x, int y) {
        if (effect.effect() == null) return;
        Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.effect().value());
        g.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                Identifier.fromNamespaceAndPath(id.getNamespace(), "mob_effect/" + id.getPath()),
                x,
                y,
                16,
                16);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0x90101010, 0xB0101010);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        if (!choosing()) {
            EETheme.rect(g, left + 1, top + 1, panelWidth - 2, headerHeight() - 2, EETheme.INPUT);
            EETheme.rect(g, left + 1, top + headerHeight() - 1, panelWidth - 2, 1, EETheme.TRACK);
        }
        g.text(
                font,
                choosingEffect
                        ? (changing == null ? "Add effect" : "Change effect")
                        : title.getString(),
                left + 12,
                top + 15,
                choosing() ? EETheme.TEXT : EETheme.LIGHT,
                false);
        if (choosing()) {
            inputSurface(g, search);
            renderCatalog(g, popup == null ? mx : -1, popup == null ? my : -1);
        } else {
            renderHeader(g, mx, my);
            g.text(font, "Effects", left + 12, top + headerHeight() + 10, EETheme.TEXT, false);
            g.enableScissor(left + 12, listTop, listRight, listBottom);
            for (int i = 0; i < controls.size(); i++) {
                Controls c = controls.get(i);
                int y = listTop + i * effectRow - scroll;
                if (y + CARD_HEIGHT <= listTop || y >= listBottom) continue;
                boolean hover =
                        popup == null
                                && inside(mx, my)
                                && mx < rowRight()
                                && my >= y
                                && my < y + CARD_HEIGHT;
                EETheme.rect(
                        g,
                        left + 12,
                        y,
                        rowRight() - left - 12,
                        CARD_HEIGHT,
                        hover
                                ? EETheme.OPTION_HOVER
                                : selected == c.entry ? EETheme.OPTION : EETheme.PANEL);
                EETheme.rect(
                        g,
                        left + 12,
                        y + CARD_HEIGHT - 1,
                        rowRight() - left - 12,
                        1,
                        EETheme.CONTROL);
                g.text(font, "Level", left + 20, c.level.getY() + 5, EETheme.MUTED, false);
                g.text(font, "Time", left + 85, c.seconds.getY() + 5, EETheme.MUTED, false);
                inputSurface(g, c.level);
                if (c.entry.effect.ticks == -1) {
                    EETheme.rect(g, left + 109, c.seconds.getY(), 50, 18, EETheme.INPUT);
                    g.text(
                            font,
                            "Infinite",
                            left + 113,
                            c.seconds.getY() + 5,
                            EETheme.LIGHT,
                            false);
                } else {
                    inputSurface(g, c.seconds);
                    g.text(font, "s", left + 163, c.seconds.getY() + 5, EETheme.MUTED, false);
                }
                for (AbstractWidget widget : c.widgets())
                    widget.extractRenderState(
                            g,
                            popup == null && inside(mx, my) ? mx : -1,
                            popup == null && inside(mx, my) ? my : -1,
                            delta);
            }
            g.disableScissor();
            scrollbar.render(g, mx, my);
            if (data.effects.isEmpty()) {
                g.text(font, "No effects", left + 20, listTop + 17, EETheme.MUTED, false);
            }
        }
        EETheme.rect(g, left + 12, listBottom + 6, panelWidth - 24, 1, 0xFF969E91);
        super.extractRenderState(g, popup == null ? mx : -1, popup == null ? my : -1, delta);
        if (!choosing() && popup == null) renderPreviewTooltip(g, mx, my);
        if (popup != null) popup.render(g, mx, my);
    }

    private void renderCatalog(GuiGraphicsExtractor g, int mx, int my) {
        EETheme.bevel(
                g,
                left + 12,
                listTop - 1,
                listRight - left - 12,
                listBottom - listTop + 2,
                EETheme.INPUT,
                true);
        g.enableScissor(left + 13, listTop, listRight - 1, listBottom);
        for (int i = 0; i < filtered.size(); i++) {
            Choice choice = filtered.get(i);
            int y = listTop + i * ROW - catalogScroll;
            if (y + ROW <= listTop || y >= listBottom) continue;
            boolean hover = inside(mx, my) && mx < rowRight() && my >= y && my < y + ROW;
            boolean selectedChoice =
                    changing != null && changing.effect.id.equals(choice.id.toString());
            boolean highlight = hover || keyboardRow == i || selectedChoice;
            if (highlight)
                EETheme.rect(g, left + 13, y, rowRight() - left - 13, ROW, EETheme.OPTION_HOVER);
            drawEffectIcon(
                    g,
                    EffectEditorData.create(
                            BuiltInRegistries.MOB_EFFECT.wrapAsHolder(
                                    BuiltInRegistries.MOB_EFFECT
                                            .getOptional(choice.id)
                                            .orElseThrow())),
                    left + 20,
                    y + 6);
            g.text(
                    font,
                    fit(choice.name, contentRight() - left - 46),
                    left + 42,
                    y + 10,
                    highlight ? EETheme.TEXT : EETheme.LIGHT,
                    false);
            if (hover) g.setTooltipForNextFrame(font, Component.literal(choice.name), mx, my);
        }
        g.disableScissor();
        catalogBar.render(g, mx, my);
        if (filtered.isEmpty())
            g.text(font, "No matching effects", left + 20, listTop + 13, 0xFFB8C7BD, false);
    }
}
