package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EEDropdownButton;
import com.dutchmtc.ee.gui.components.EEDropdownScrollbar;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.GuiUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import com.dutchmtc.ee.gui.components.EETextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** One focused form beside a searchable list; narrow screens use the same form on its own. */
abstract class GuiAttributesEditor<T, R> extends GuiModifier<T> {
    protected final List<R> rows = new ArrayList<>();
    protected int selected = -1;
    protected int left, top, panelWidth, panelHeight, detailX, detailWidth;
    protected EditBox valueField;
    protected EEButton apply;
    private final List<Attribute> catalog = new ArrayList<>();
    private final List<Integer> filtered = new ArrayList<>();
    private boolean compact, detail, choosing, adding, dragging;
    private String query = "", savedQuery = "";
    private int viewTop, viewBottom, listWidth, scroll, savedScroll, keyboardRow = -1;
    private double dragOffset;
    private EditBox search;
    private EEButton duplicateButton;
    private Popup popup;

    protected GuiAttributesEditor(Screen parent, Consumer<T> setter) {
        super(parent, Component.literal("Attributes"), setter);
        BuiltInRegistries.ATTRIBUTE.forEach(catalog::add);
        catalog.sort(Comparator.comparing(a -> Component.translatable(a.getDescriptionId()).getString(), String.CASE_INSENSITIVE_ORDER));
    }
    protected abstract Attribute attribute(R row);
    protected abstract T get();
    protected String cannotChoose(Attribute attribute, boolean adding) { return null; }
    protected String name(R row) { return attribute(row) == null ? "Unknown attribute" : attributeName(attribute(row)); }
    protected abstract String summary(R row);
    protected abstract R create(Attribute attribute);
    protected abstract void changeAttribute(R row, Attribute attribute);
    protected abstract void buildDetails(R row, int x, int y, int width);
    protected abstract String invalidReason(R row);
    protected abstract void renderDetails(GuiGraphicsExtractor g, R row, int x, int y, int width);
    protected boolean supportsDuplicate() { return false; }
    protected void duplicateSelected() {}
    protected static String attributeName(Attribute attribute) { return Component.translatable(attribute.getDescriptionId()).getString(); }
    protected R current() { return selected >= 0 && selected < rows.size() ? rows.get(selected) : null; }
    protected int formY() { return top + 38; }
    protected void refreshApply() {
        if (apply == null) return;
        String error = rows.stream().map(this::invalidReason).filter(s -> s != null).findFirst().orElse(null);
        apply.active = error == null;
        apply.setTooltip(error == null ? null : Tooltip.create(Component.literal(error)));
        if (valueField != null && current() != null) EETextField.validate(valueField, invalidReason(current()));
        if (duplicateButton != null) {
            String reason = current() == null ? "Select an attribute to duplicate." : invalidReason(current());
            duplicateButton.active = reason == null;
            duplicateButton.setTooltip(Tooltip.create(Component.literal(reason == null
                    ? "Add an independent copy of the selected attribute." : reason)));
        }
    }
    @Override protected void setInitialFocus() {}
    @Override public void onClose() { onCancel(); }
    @Override public void init() {
        clearFocus(); clearWidgets(); popup = null; dragging = false; valueField = null; apply = null; duplicateButton = null;
        panelWidth = Math.min(560, width - 16); panelHeight = Math.min(300, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        compact = panelWidth < 440;
        boolean form = !choosing && current() != null && (!compact || detail);
        listWidth = choosing || compact ? panelWidth - 24 : 190;
        viewTop = top + 66; viewBottom = top + panelHeight - (choosing ? 40 : 60);
        boolean hasBack = choosing || compact && detail;
        if (hasBack) {
            addRenderableWidget(new EEButton(left + 12, top + 10, 60, 20, Component.literal("Back"), b -> {
                if (choosing) finishChoosing(); else { detail = false; init(); }
            }));
        }
        if (!choosing && listVisible()) {
            int actionWidth = listWidth - 10;
            int duplicateWidth = Math.min(100, (actionWidth - 4) / 2);
            int addWidth = supportsDuplicate() ? actionWidth - duplicateWidth - 4 : actionWidth;
            int actionY = top + panelHeight - 54;
            addRenderableWidget(new EEButton(left + 12, actionY, addWidth, 20,
                    Component.literal("Add attribute"), b -> chooseAttribute(true)));
            if (supportsDuplicate()) addDuplicateButton(left + 16 + addWidth, actionY, duplicateWidth, 20);
        }
        if (!form || !compact) {
            search = field(left + 18, top + 41, listWidth - 22, 18, "Search attributes...", query);
            search.setResponder(s -> { query = s; scroll = 0; keyboardRow = -1; filter(); });
        } else search = null;
        detailX = compact ? left + 12 : left + 212;
        detailWidth = compact ? panelWidth - 24 : panelWidth - 224;
        if (form) {
            R row = current();
            int y = formY();
            if (compact) {
                if (supportsDuplicate()) addDuplicateButton(detailX + detailWidth - 104, y - 6, 80, 18);
                addRenderableWidget(new EEButton(detailX + detailWidth - 18, y - 6, 18, 18,
                        Component.literal("×").withColor(0xFF5555), b -> removeSelected()) {
                    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                        renderRemove(g, getX(), getY(), isHoveredOrFocused());
                    }
                }).setTooltip(Tooltip.create(Component.literal("Remove this attribute")));
            }
            addRenderableWidget(new EEDropdownButton(detailX, y + (compact ? 24 : 13), detailWidth, 20,
                    Component.literal(name(row)), b -> chooseAttribute(false)))
                    .setTooltip(Tooltip.create(Component.literal("Choose the property this entry changes.")));
            buildDetails(row, detailX, detailsY(), detailWidth);
        }
        int footerWidth = (panelWidth - 28) / 2;
        addRenderableWidget(new EEButton(left + 12, top + panelHeight - 28, footerWidth, 20,
                Component.translatable("gui.ee.cancel"), b -> onCancel()));
        apply = addRenderableWidget(new EEButton(left + 16 + footerWidth, top + panelHeight - 28, footerWidth, 20,
                Component.literal("Apply"), b -> { if (b.active) { set(get()); mc.gui.setScreen(parent); } }));
        filter(); refreshApply();
    }
    private int detailsY() { return formY() + (compact ? 11 : 0); }
    private void addDuplicateButton(int x, int y, int w, int h) {
        duplicateButton = addRenderableWidget(new EEButton(x, y, w, h, Component.literal("Duplicate"), b -> {
            if (current() == null || invalidReason(current()) != null) return;
            duplicateSelected(); detail = true; init(); reveal();
        }));
    }
    protected EditBox field(int x, int y, int w, int h, String hint, String value) {
        EditBox box = new EETextField(font, x, y, w, h, Component.literal(hint));
        box.setMaxLength(128); box.setBordered(false); box.setTextShadow(false);
        box.setTextColor(EETheme.LIGHT); box.setHint(Component.literal(hint).withColor(0xAEBBB5));
        box.setValue(value); return addRenderableWidget(box);
    }
    protected void label(GuiGraphicsExtractor g, String text, int x, int y) { g.text(font, text, x, y, EETheme.TEXT, false); }
    protected void inputSurface(GuiGraphicsExtractor g, EditBox box, boolean valid) {
        EETextField.validationFrame(box, box.getX() - 6, box.getY() - 1, box.getWidth() + 12, box.getHeight() + 2);
        EETheme.bevel(g, box.getX() - 6, box.getY() - 1, box.getWidth() + 12, box.getHeight() + 2, EETheme.INPUT, true);
        ((EditBoxAccessor) box).ee$setTextY(box.getY() + 5);
        EETextField.validate(box, valid ? null : invalidReason(current()));
    }
    protected void openOptions(EEDropdownButton anchor, List<String> labels, int active, IntConsumer action) {
        clearFocus(); popup = new Popup(anchor, labels, active, action);
    }
    private void chooseAttribute(boolean add) {
        if (!add && current() == null) return;
        savedQuery = query; savedScroll = scroll; query = ""; scroll = 0; keyboardRow = -1; choosing = true; adding = add; init();
        setFocused(search);
    }
    private void finishChoosing() { choosing = false; keyboardRow = -1; query = savedQuery; scroll = savedScroll; init(); }
    private void pick(int index) {
        if (index < 0 || index >= filtered.size()) return;
        if (choosing) {
            Attribute attribute = catalog.get(filtered.get(index));
            if (cannotChoose(attribute, adding) != null) return;
            if (adding) { rows.add(create(attribute)); selected = rows.size() - 1; }
            else changeAttribute(current(), attribute);
            detail = true; finishChoosing();
        } else { selected = filtered.get(index); detail = true; init(); }
        playClick();
    }
    private void removeSelected() {
        if (current() == null) return;
        rows.remove(selected); selected = Math.min(selected, rows.size() - 1); detail = false; init();
    }
    private void filter() {
        filtered.clear(); String[] tokens = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
        int size = choosing ? catalog.size() : rows.size();
        for (int i = 0; i < size; i++) {
            Attribute attr = choosing ? catalog.get(i) : attribute(rows.get(i));
            String text = (choosing ? attributeName(attr) : name(rows.get(i)) + " " + summary(rows.get(i)))
                    + (attr == null ? "" : " " + BuiltInRegistries.ATTRIBUTE.getKey(attr));
            String lower = text.toLowerCase(Locale.ROOT); boolean match = true;
            for (String token : tokens) if (!lower.contains(token)) { match = false; break; }
            if (match) filtered.add(i);
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }
    private int rowHeight() { return choosing ? 24 : 32; }
    private boolean listVisible() { return choosing || !compact || !detail || current() == null; }
    private int listRight() { return left + 12 + listWidth - 10; }
    private int maxScroll() { return Math.max(0, filtered.size() * rowHeight() - (viewBottom - viewTop)); }
    private int thumbHeight() { int h = viewBottom - viewTop; return Math.max(16, h * h / Math.max(h, filtered.size() * rowHeight())); }
    private int thumbY() { return viewTop + (viewBottom - viewTop - thumbHeight()) * scroll / Math.max(1, maxScroll()); }
    private int at(double x, double y) {
        if (!listVisible() || x < left + 12 || x >= listRight() || y < viewTop || y >= viewBottom) return -1;
        int index = ((int) y - viewTop + scroll) / rowHeight(); return index < filtered.size() ? index : -1;
    }
    private int removeY(int index) { return viewTop + index * rowHeight() - scroll + 7; }
    private boolean removalVisible(int index) {
        return !choosing && filtered.get(index) == selected && removeY(index) >= viewTop && removeY(index) + 18 <= viewBottom;
    }
    private boolean overRemove(int index, double x, double y) {
        return removalVisible(index) && x >= listRight() - 23 && x < listRight() - 5
                && y >= removeY(index) && y < removeY(index) + 18;
    }
    private void renderRemove(GuiGraphicsExtractor g, int x, int y, boolean hover) {
        EETheme.removeButton(g, x, y, 18, 18, hover, true);
    }
    private void dragTo(double y) {
        int travel = viewBottom - viewTop - thumbHeight();
        if (travel > 0) scroll = (int) Math.round(Math.clamp((y - dragOffset - viewTop) / travel, 0, 1) * maxScroll());
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (popup != null) { popup.click(event); return true; }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && listVisible()) {
            int bar = listRight() + 4;
            if (maxScroll() > 0 && event.x() >= bar - 3 && event.x() < bar + 7 && event.y() >= viewTop && event.y() < viewBottom) {
                clearFocus(); dragging = true;
                dragOffset = event.y() >= thumbY() && event.y() < thumbY() + thumbHeight() ? event.y() - thumbY() : thumbHeight() / 2.0;
                dragTo(event.y()); return true;
            }
            int index = at(event.x(), event.y());
            if (index >= 0) {
                if (overRemove(index, event.x(), event.y())) { clearFocus(); removeSelected(); playClick(); }
                else pick(index);
                return true;
            }
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        // A button may rebuild the form during its press; don't keep focus on the removed widget.
        if (getFocused() != null && !children().contains(getFocused())) setFocused(choosing ? search : null);
        return handled;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (popup != null) { popup.drag(event); return true; }
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) { dragTo(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (popup != null) { popup.scrollbar.mouseReleased(); return true; }
        if (dragging) { dragging = false; return true; } return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (popup != null) { popup.scrollTo(Math.clamp(popup.offset - (int) Math.round(v * 40), 0, popup.max())); return true; }
        if (listVisible() && x >= left + 12 && x < left + 12 + listWidth && y >= viewTop && y < viewBottom) {
            scroll = Math.clamp(scroll - (int) Math.round(v * rowHeight() * 2), 0, maxScroll()); return true;
        }
        return super.mouseScrolled(x, y, h, v);
    }
    @Override public boolean charTyped(CharacterEvent event) { return popup != null || super.charTyped(event); }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (popup != null) {
            if (key == InputConstants.KEY_ESCAPE) popup = null;
            else if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
                popup.focus = Math.clamp(popup.focus + (key == InputConstants.KEY_UP ? -1 : 1), 0, popup.labels.size() - 1);
                popup.offset = Math.clamp(popup.offset, Math.max(0, (popup.focus + 1) * 20 - popup.h), Math.min(popup.max(), popup.focus * 20));
            } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) popup.select(popup.focus);
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE && choosing) { finishChoosing(); return true; }
        if (key == InputConstants.KEY_ESCAPE && compact && detail) { detail = false; init(); return true; }
        if (search != null && event.hasControlDown() && key == InputConstants.KEY_F) { setFocused(search); return true; }
        if (listVisible() && (getFocused() == null || search != null && search.isFocused() && key == InputConstants.KEY_DOWN)) {
            if (!choosing && key == InputConstants.KEY_DELETE && current() != null && filtered.contains(selected)) {
                removeSelected(); playClick(); return true;
            }
            int index = choosing ? keyboardRow : filtered.indexOf(selected);
            int next = switch (key) {
                case InputConstants.KEY_DOWN -> index + 1;
                case InputConstants.KEY_UP -> index - 1;
                case InputConstants.KEY_HOME -> 0;
                case InputConstants.KEY_END -> filtered.size() - 1;
                default -> -2;
            };
            if (next != -2 && !filtered.isEmpty()) {
                if (choosing) { clearFocus(); keyboardRow = Math.clamp(next, 0, filtered.size() - 1); }
                else pick(Math.clamp(next, 0, filtered.size() - 1));
                reveal(); return true;
            }
            if (choosing && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) { pick(keyboardRow); return true; }
        }
        return super.keyPressed(event);
    }
    private void reveal() {
        int index = choosing ? keyboardRow : filtered.indexOf(selected); if (index < 0) return;
        scroll = Math.clamp(scroll, Math.max(0, (index + 1) * rowHeight() - (viewBottom - viewTop)), Math.min(maxScroll(), index * rowHeight()));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0x90101010, 0xB0101010);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        label(g, choosing ? "Choose attribute" : "Attributes", left + (choosing || compact && detail ? 80 : 12), top + 16);
        if (search != null) inputSurface(g, search, true);
        int hovered = popup == null ? at(mx, my) : -1;
        if (listVisible()) {
            EETheme.bevel(g, left + 12, viewTop - 1, listWidth - 10, viewBottom - viewTop + 2, EETheme.INPUT, true);
            g.enableScissor(left + 13, viewTop, listRight() - 1, viewBottom);
            for (int i = scroll / rowHeight(); i < filtered.size(); i++) {
                int y = viewTop + i * rowHeight() - scroll; if (y >= viewBottom) break;
                boolean picked = !choosing && filtered.get(i) == selected;
                EETheme.rect(g, left + 13, y, listWidth - 12, rowHeight(), picked ? 0xFF515F55 : hovered == i || choosing && keyboardRow == i ? 0xFF48544C : EETheme.INPUT);
                if (picked) EETheme.rect(g, left + 13, y + 4, 2, rowHeight() - 8, 0xFF76B660);
                String name = choosing ? attributeName(catalog.get(filtered.get(i))) : name(rows.get(filtered.get(i)));
                boolean disabled = choosing && cannotChoose(catalog.get(filtered.get(i)), adding) != null;
                int textWidth = listWidth - (picked ? 50 : 28);
                g.text(font, fit(name, textWidth), left + 21, y + (choosing ? 8 : 5), disabled ? 0xFFC98F8F : EETheme.LIGHT, false);
                if (!choosing) g.text(font, fit(summary(rows.get(filtered.get(i))), textWidth), left + 21, y + 18, 0xFFAEBBB5, false);
                if (removalVisible(i)) renderRemove(g, listRight() - 23, removeY(i), hovered == i && overRemove(i, mx, my));
            }
            g.disableScissor();
            if (filtered.isEmpty()) {
                g.text(font, query.isBlank() ? "No attributes added." : "No matches.", left + 22, viewTop + 12, EETheme.LIGHT, false);
                if (query.isBlank()) g.text(font, "Use Add attribute to begin.", left + 22, viewTop + 28, 0xFFAEBBB5, false);
            }
            if (maxScroll() > 0) EETheme.scrollbar(g, listRight() + 4, viewTop, 4, viewBottom - viewTop, thumbY(), thumbHeight(), dragging);
        }
        if (!choosing && current() != null && (!compact || detail)) {
            label(g, "Attribute", detailX, formY());
            renderDetails(g, current(), detailX, detailsY(), detailWidth);
        } else if (!choosing && !compact) {
            label(g, "Select an attribute to edit.", detailX, top + 80);
        }
        super.extractRenderState(g, popup == null ? mx : -1, popup == null ? my : -1, delta);
        if (hovered >= 0) {
            String text = choosing ? attributeName(catalog.get(filtered.get(hovered))) : name(rows.get(filtered.get(hovered))) + "\n" + summary(rows.get(filtered.get(hovered)));
            String reason = choosing ? cannotChoose(catalog.get(filtered.get(hovered)), adding) : null;
            if (overRemove(hovered, mx, my)) text = "Remove " + name(rows.get(selected));
            g.setTooltipForNextFrame(font, font.split(Component.literal(reason == null ? text : reason), 220), mx, my);
        }
        if (popup != null) popup.render(g, mx, my);
    }
    protected String fit(String text, int space) { return font.width(text) <= space ? text : font.plainSubstrByWidth(text, Math.max(0, space - font.width("..."))) + "..."; }

    /** Small bounded options list, using the same option rendering as the other editors. */
    private final class Popup {
        final List<String> labels;
        final IntConsumer action;
        final int x, y, w, h, active;
        int focus, offset;
        final EEDropdownScrollbar scrollbar = new EEDropdownScrollbar();
        Popup(EEDropdownButton anchor, List<String> labels, int active, IntConsumer action) {
            this.labels = labels; this.action = action; this.active = active; focus = active;
            w = anchor.getWidth(); x = anchor.getX(); h = Math.min(labels.size() * 20, Math.min(160, Math.max(20, (panelHeight - 70) / 20 * 20)));
            y = Math.clamp(anchor.getBottom() + 3, top + 31, top + panelHeight - 35 - h);
            offset = Math.clamp((active + 1) * 20 - h, 0, max());
        }
        int max() { return Math.max(0, labels.size() * 20 - h); }
        void layoutScrollbar() { scrollbar.layout(x + w - EEDropdownScrollbar.WIDTH, y, h, h, labels.size() * 20, offset, 20); }
        void scrollTo(int value) {
            offset = value;
            focus = Math.clamp(focus, offset / 20, Math.min(labels.size() - 1, (offset + h - 1) / 20));
        }
        void select(int index) { IntConsumer callback = action; popup = null; callback.accept(index); playClick(); }
        void click(MouseButtonEvent e) {
            if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) { popup = null; return; }
            if (e.x() < x || e.x() >= x + w || e.y() < y || e.y() >= y + h) { popup = null; return; }
            layoutScrollbar();
            if (scrollbar.mouseClicked(e, this::scrollTo)) return;
            select(Math.min(labels.size() - 1, ((int) e.y() - y + offset) / 20));
        }
        void drag(MouseButtonEvent e) {
            layoutScrollbar(); scrollbar.mouseDragged(e, this::scrollTo);
        }
        void render(GuiGraphicsExtractor g, int mx, int my) {
            layoutScrollbar();
            EETheme.bevel(g, x - 1, y - 1, w + 2, h + 2, EETheme.PANEL, false);
            g.enableScissor(x, y, x + w, y + h);
            for (int i = offset / 20; i < labels.size(); i++) {
                int yy = y + i * 20 - offset; if (yy >= y + h) break;
                EEDropdownButton.renderOption(g, font, Component.literal(labels.get(i)), ItemStack.EMPTY,
                        x, yy, w - (scrollbar.needed() ? EEDropdownScrollbar.WIDTH : 0), 20, i == active, i == focus || mx >= x && mx < x + w - (scrollbar.needed() ? EEDropdownScrollbar.WIDTH : 0) && my >= yy && my < yy + 20);
            }
            g.disableScissor();
            scrollbar.render(g, mx, my);
        }
    }
}
