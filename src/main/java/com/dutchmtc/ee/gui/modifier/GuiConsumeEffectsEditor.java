package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EEDragRow;
import com.dutchmtc.ee.gui.components.EEDropdownButton;
import com.dutchmtc.ee.gui.components.EESettingsScreen;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.gui.selector.GuiResourcePicker;
import com.dutchmtc.ee.utils.GuiUtils;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.consume_effects.*;

import java.util.*;
import java.util.function.Consumer;

/** Choose an action, configure it, then add it to the item's consumption draft. */
public final class GuiConsumeEffectsEditor extends EESettingsScreen<List<ConsumeEffect>> {
    private enum Page {
        OVERVIEW,
        ACTIONS,
        EDIT
    }

    private final List<ConsumeEffect> effects, original;
    private Page page = Page.OVERVIEW;
    private EffectForm form;
    private int selectedAction = -1, selectedStatus = -1, linkTarget = -1;
    private int listWidth, listTop, listBottom, listScroll;
    private boolean adding, listDragging;
    private double dragOffset;
    private EEButton primary;
    private EEDropdownButton options;
    private final List<EEButton> removeButtons = new ArrayList<>();
    private final List<EEButton> editButtons = new ArrayList<>();
    private Entry pressedEntry;
    private boolean rowDragging;
    private double pressX, pressY, pointerY, rowDragOffset;
    private int dropBefore = -1;

    private record Entry(int action, int status) {}

    private static final String[] ACTIONS = {
        "Give potion effect",
        "Remove potion effects",
        "Clear potion effects",
        "Random teleport",
        "Play sound"
    };
    private static final String[] DESCRIPTIONS = {
        "Give the player a potion effect.",
        "Remove specific potion effects.",
        "Remove every active potion effect.",
        "Teleport the player to a nearby location.",
        "Play a sound when consumption finishes."
    };

    public GuiConsumeEffectsEditor(
            Screen parent, List<ConsumeEffect> effects, Consumer<List<ConsumeEffect>> setter) {
        super(parent, "Food & drink effects", setter);
        this.effects = new ArrayList<>(effects);
        original = List.copyOf(effects);
    }

    @Override
    public boolean isModified() {
        return !effects.equals(original) || form != null && form.hasDraftChanges();
    }

    @Override
    protected List<ConsumeEffect> result() {
        return List.copyOf(effects);
    }

    @Override
    protected void buildRows() {}

    @Override
    protected String invalidReason() {
        return page == Page.EDIT ? form.error() : null;
    }

    private List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < effects.size(); i++) {
            if (effects.get(i) instanceof ApplyStatusEffectsConsumeEffect e
                    && !e.effects().isEmpty())
                for (int j = 0; j < e.effects().size(); j++) list.add(new Entry(i, j));
            else list.add(new Entry(i, -1));
        }
        return list;
    }

    private void select(Entry entry) {
        selectedAction = entry.action;
        selectedStatus = entry.status;
        adding = false;
        linkTarget = -1;
        form = new EffectForm(this, effects.get(selectedAction), selectedStatus);
        page = Page.EDIT;
        init();
    }

    private void beginAdding() {
        form = null;
        adding = false;
        linkTarget = -1;
        page = Page.ACTIONS;
        init();
    }

    private void startAction(int kind) {
        ConsumeEffect action =
                switch (kind) {
                    case 0 ->
                            new ApplyStatusEffectsConsumeEffect(
                                    new MobEffectInstance(MobEffects.SPEED, 1200));
                    case 1 ->
                            new RemoveStatusEffectsConsumeEffect(
                                    HolderSet.direct(List.<Holder<MobEffect>>of()));
                    case 2 -> ClearAllStatusEffectsConsumeEffect.INSTANCE;
                    case 3 -> new TeleportRandomlyConsumeEffect();
                    case 4 -> new PlaySoundConsumeEffect(SoundEvents.GENERIC_DRINK);
                    default -> throw new IllegalArgumentException("Unknown consume action");
                };
        adding = true;
        selectedAction = selectedStatus = -1;
        form = new EffectForm(this, action, 0);
        form.needsEffect = kind == 0;
        form.needsSound = kind == 4;
        page = Page.EDIT;
        init();
    }

    private void saveAction() {
        if (invalidReason() != null) return;
        ConsumeEffect action = form.result();
        if (adding && linkTarget >= 0) {
            ApplyStatusEffectsConsumeEffect group =
                    (ApplyStatusEffectsConsumeEffect) effects.get(linkTarget);
            List<MobEffectInstance> statuses = new ArrayList<>(group.effects());
            statuses.add(((ApplyStatusEffectsConsumeEffect) action).effects().getFirst());
            effects.set(
                    linkTarget, new ApplyStatusEffectsConsumeEffect(statuses, group.probability()));
            selectedAction = linkTarget;
            selectedStatus = statuses.size() - 1;
        } else if (adding) {
            effects.add(action);
            selectedAction = effects.size() - 1;
            selectedStatus =
                    action instanceof ApplyStatusEffectsConsumeEffect e && !e.effects().isEmpty()
                            ? 0
                            : -1;
        } else effects.set(selectedAction, action);
        form = null;
        page = Page.OVERVIEW;
        init();
        reveal();
    }

    private void back() {
        if (page == Page.EDIT && form.hasDraftChanges()) {
            mc.gui.setScreen(
                    new GuiConfirmation(
                            this,
                            Component.literal("Discard changes to this action?"),
                            () -> {
                                leaveEdit();
                                mc.gui.setScreen(this);
                            },
                            () -> mc.gui.setScreen(this)));
        } else if (page == Page.EDIT) leaveEdit();
        else {
            page = Page.OVERVIEW;
            init();
        }
    }

    private void leaveEdit() {
        form = null;
        page = adding && linkTarget < 0 ? Page.ACTIONS : Page.OVERVIEW;
        adding = false;
        linkTarget = -1;
        init();
    }

    @Override
    public void onCancel() {
        if (page != Page.OVERVIEW) back();
        else super.onCancel();
    }

    private void removeEntry(Entry entry) {
        if (effects.get(entry.action) instanceof ApplyStatusEffectsConsumeEffect group
                && group.effects().size() > 1
                && entry.status >= 0) {
            List<MobEffectInstance> statuses = new ArrayList<>(group.effects());
            statuses.remove(entry.status);
            effects.set(
                    entry.action,
                    new ApplyStatusEffectsConsumeEffect(statuses, group.probability()));
            selectedAction = entry.action;
            selectedStatus = Math.min(entry.status, statuses.size() - 1);
        } else {
            effects.remove(entry.action);
            selectedAction = selectedStatus = -1;
        }
        init();
    }

    private void selectedOption(String option) {
        if (selectedAction < 0) return;
        if (option.equals("Add effect with same chance")) {
            int target = selectedAction;
            startAction(0);
            linkTarget = target;
            form.sharedChance = true;
            ApplyStatusEffectsConsumeEffect group =
                    (ApplyStatusEffectsConsumeEffect) effects.get(target);
            form.effect =
                    new ApplyStatusEffectsConsumeEffect(
                            ((ApplyStatusEffectsConsumeEffect) form.effect).effects(),
                            group.probability());
            init();
        } else {
            int destination = selectedAction + (option.equals("Move earlier") ? -1 : 1);
            Collections.swap(effects, selectedAction, destination);
            selectedAction = destination;
            init();
            reveal();
        }
    }

    private void applyAll() {
        if (page != Page.OVERVIEW) return;
        setter.accept(result());
        mc.gui.setScreen(parent);
    }

    @Override
    public void init() {
        dismissMenu();
        clearFocus();
        clearWidgets();
        removeButtons.clear();
        editButtons.clear();
        cancelRowDrag();
        options = null;
        listDragging = false;
        panelWidth = Math.min(page == Page.ACTIONS ? 440 : 500, width - 16);
        panelHeight =
                Math.min(
                        page == Page.ACTIONS
                                ? 224
                                : page == Page.EDIT ? form.preferredHeight() : 340,
                        height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        listWidth = panelWidth - 24;
        listTop = top + 51;
        listBottom = top + panelHeight - 66;
        viewTop = listTop;
        viewBottom = listBottom;
        int footer = top + panelHeight - 27, half = (panelWidth - 28) / 2;
        if (page == Page.ACTIONS) {
            for (int i = 0; i < ACTIONS.length; i++) {
                int kind = i;
                EEButton choice =
                        new EEButton(
                                left + 12,
                                top + 33 + i * 30,
                                panelWidth - 24,
                                28,
                                Component.literal(ACTIONS[i] + ". " + DESCRIPTIONS[i]),
                                b -> startAction(kind)) {
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
                                ItemStack icon =
                                        new ItemStack(
                                                switch (kind) {
                                                    case 0 -> Items.POTION;
                                                    case 1 -> Items.HONEY_BOTTLE;
                                                    case 2 -> Items.MILK_BUCKET;
                                                    case 3 -> Items.CHORUS_FRUIT;
                                                    default -> Items.NOTE_BLOCK;
                                                });
                                GuiUtils.drawItemStack(g, icon, getX() + 8, getY() + 6);
                                g.text(
                                        font,
                                        fit(ACTIONS[kind], getWidth() - 40),
                                        getX() + 32,
                                        getY() + 4,
                                        EETheme.TEXT,
                                        false);
                                g.text(
                                        font,
                                        fit(DESCRIPTIONS[kind], getWidth() - 40),
                                        getX() + 32,
                                        getY() + 16,
                                        EETheme.MUTED,
                                        false);
                            }
                        };
                addRenderableWidget(choice);
            }
            addRenderableWidget(
                    new EEButton(
                            left + 12,
                            footer,
                            panelWidth - 24,
                            20,
                            Component.literal("Back"),
                            b -> back()));
            primary = null;
        } else if (page == Page.EDIT) {
            form.embed(left, top + 33, panelWidth, panelHeight - 72, width, height);
            addRenderableWidget(
                    new EEButton(
                            left + 12, footer, half, 20, Component.literal("Back"), b -> back()));
            primary =
                    addRenderableWidget(
                            new EEButton(
                                    left + 16 + half,
                                    footer,
                                    half,
                                    20,
                                    Component.literal(adding ? "Add action" : "Save action"),
                                    b -> saveAction()));
        } else {
            listScroll = Math.max(0, Math.min(maxListScroll(), listScroll));
            for (Entry entry : entries()) {
                EEButton edit =
                        new EEButton(0, 0, 32, 18, Component.literal("Edit"), b -> select(entry));
                edit.setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        "Edit " + entryName(entry, effects.get(entry.action)))));
                editButtons.add(addRenderableWidget(edit));
                EEButton remove =
                        new EEButton(
                                0,
                                0,
                                18,
                                18,
                                Component.literal("×").withColor(0xFF5555),
                                b -> removeEntry(entry));
                remove.setTooltip(Tooltip.create(Component.literal("Remove this action")));
                removeButtons.add(addRenderableWidget(remove));
            }
            positionRemoves();
            EEButton add =
                    addRenderableWidget(
                            new EEButton(
                                    left + 12,
                                    listBottom + 7,
                                    half,
                                    20,
                                    Component.literal("Add action"),
                                    b -> beginAdding()));
            List<String> choices = new ArrayList<>();
            if (selectedAction > 0) choices.add("Move earlier");
            if (selectedAction >= 0 && selectedAction < effects.size() - 1)
                choices.add("Move later");
            if (selectedAction >= 0
                    && effects.get(selectedAction) instanceof ApplyStatusEffectsConsumeEffect)
                choices.add("Add effect with same chance");
            options =
                    addRenderableWidget(
                            dropdown("Options...", choices, v -> v, this::selectedOption));
            options.setX(add.getRight() + 4);
            options.setY(add.getY());
            options.setWidth(half);
            options.active = !choices.isEmpty();
            options.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    choices.isEmpty()
                                            ? "Choose an action to view its options."
                                            : "Change the saved action's order or link another"
                                                    + " potion effect.")));
            addRenderableWidget(
                    new EEButton(
                            left + 12,
                            footer,
                            half,
                            20,
                            Component.literal("Cancel"),
                            b -> onCancel()));
            primary =
                    addRenderableWidget(
                            new EEButton(
                                    left + 16 + half,
                                    footer,
                                    half,
                                    20,
                                    Component.literal("Apply"),
                                    b -> applyAll()));
        }
        refreshValidity();
    }

    @Override
    protected void refreshValidity() {
        if (primary != null) {
            String error = invalidReason();
            primary.active = error == null;
            primary.setTooltip(error == null ? null : Tooltip.create(Component.literal(error)));
        }
    }

    private int maxListScroll() {
        return Math.max(0, entries().size() * 42 - (listBottom - listTop));
    }

    private int listThumbHeight() {
        int h = listBottom - listTop;
        return Math.max(18, h * h / Math.max(h, entries().size() * 42));
    }

    private int listThumbY() {
        return listTop
                + (listBottom - listTop - listThumbHeight())
                        * listScroll
                        / Math.max(1, maxListScroll());
    }

    private void positionRemoves() {
        listScroll = Math.max(0, Math.min(maxListScroll(), listScroll));
        for (int i = 0; i < removeButtons.size(); i++) {
            EEButton button = removeButtons.get(i);
            int y = listTop + i * 42 - listScroll + 12;
            button.setX(left + panelWidth - 46);
            button.setY(y);
            button.visible =
                    page == Page.OVERVIEW && !rowDragging && y >= listTop && y + 18 <= listBottom;
            EEButton edit = editButtons.get(i);
            edit.setX(button.getX() - 36);
            edit.setY(y);
            edit.visible = button.visible;
        }
    }

    private void cancelRowDrag() {
        pressedEntry = null;
        rowDragging = false;
        dropBefore = -1;
        setDragging(false);
    }

    private static int entryCount(ConsumeEffect action) {
        return action instanceof ApplyStatusEffectsConsumeEffect e
                ? Math.max(1, e.effects().size())
                : 1;
    }

    private int actionRow(int actionIndex) {
        int rows = 0;
        for (int i = 0; i < actionIndex; i++) rows += entryCount(effects.get(i));
        return rows;
    }

    private void updateDrop() {
        double row = (pointerY - listTop + listScroll) / 42.0;
        int start = 0;
        dropBefore = effects.size();
        for (int i = 0; i < effects.size(); i++) {
            int count = entryCount(effects.get(i));
            if (row < start + count / 2.0) {
                dropBefore = i;
                break;
            }
            start += count;
        }
    }

    private void finishRowDrag(MouseButtonEvent event) {
        if (rowDragging
                && event.x() >= left + 12
                && event.x() < left + panelWidth - 12
                && event.y() >= listTop - 16
                && event.y() < listBottom + 16) {
            pointerY = event.y();
            updateDrop();
            int source = pressedEntry.action;
            int destination = dropBefore > source ? dropBefore - 1 : dropBefore;
            if (source != destination) {
                ConsumeEffect action = effects.remove(source);
                effects.add(destination, action);
                selectedAction = destination;
                selectedStatus = pressedEntry.status;
                init();
                reveal();
                return;
            }
        }
        cancelRowDrag();
        positionRemoves();
    }

    private void reveal() {
        List<Entry> list = entries();
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).action == selectedAction && list.get(i).status == selectedStatus) {
                int y = i * 42;
                if (y < listScroll) listScroll = y;
                if (y + 42 > listScroll + listBottom - listTop)
                    listScroll = y + 42 - (listBottom - listTop);
            }
        positionRemoves();
    }

    private boolean insideList(double x, double y) {
        return page == Page.OVERVIEW
                && x >= left + 12
                && x < left + panelWidth - 12
                && y >= listTop
                && y < listBottom;
    }

    private void dragList(double y) {
        int travel = listBottom - listTop - listThumbHeight();
        if (travel > 0)
            listScroll = (int) Math.round((y - dragOffset - listTop) * maxListScroll() / travel);
        positionRemoves();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (hasOpenMenu()) return super.mouseClicked(e, twice);
        if (page == Page.EDIT && form.hasOpenMenu()) return form.mouseClicked(e, twice);
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && insideList(e.x(), e.y())) {
            if (maxListScroll() > 0 && e.x() >= left + panelWidth - 22) {
                clearFocus();
                listDragging = true;
                dragOffset =
                        e.y() >= listThumbY() && e.y() < listThumbY() + listThumbHeight()
                                ? e.y() - listThumbY()
                                : listThumbHeight() / 2.0;
                dragList(e.y());
                return true;
            }
            for (EEButton remove : removeButtons)
                if (remove.visible
                        && e.x() >= remove.getX()
                        && e.x() < remove.getRight()
                        && e.y() >= remove.getY()
                        && e.y() < remove.getBottom()) return super.mouseClicked(e, twice);
            for (EEButton edit : editButtons)
                if (edit.visible
                        && e.x() >= edit.getX()
                        && e.x() < edit.getRight()
                        && e.y() >= edit.getY()
                        && e.y() < edit.getBottom()) return super.mouseClicked(e, twice);
            int index = ((int) e.y() - listTop + listScroll) / 42;
            if (index < entries().size()) {
                Entry entry = entries().get(index);
                selectedAction = entry.action;
                selectedStatus = entry.status;
                init();
                pressedEntry = entry;
                pressX = e.x();
                pressY = pointerY = e.y();
                rowDragOffset = e.y() - (listTop + index * 42 - listScroll);
                setDragging(true);
            }
            return true;
        }
        if (page == Page.EDIT && e.y() >= top + 33 && e.y() < top + panelHeight - 39) {
            clearFocus();
            return form.mouseClicked(e, twice);
        }
        if (form != null) form.setFocused(null);
        return super.mouseClicked(e, twice);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (pressedEntry != null) {
            pointerY = e.y();
            if (!rowDragging && Math.hypot(e.x() - pressX, e.y() - pressY) >= 3) rowDragging = true;
            if (rowDragging) {
                updateDrop();
                positionRemoves();
            }
            return true;
        }
        if (listDragging) {
            dragList(e.y());
            return true;
        }
        return page == Page.EDIT && form.mouseDragged(e, dx, dy) || super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (pressedEntry != null && e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            finishRowDrag(e);
            return true;
        }
        listDragging = false;
        if (page == Page.EDIT) form.mouseReleased(e);
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (hasOpenMenu()) return super.mouseScrolled(x, y, h, v);
        if (page == Page.EDIT) return form.mouseScrolled(x, y, h, v);
        if (insideList(x, y)) {
            listScroll = Math.max(0, Math.min(maxListScroll(), listScroll - (int) (v * 42)));
            positionRemoves();
            if (rowDragging) updateDrop();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (hasOpenMenu()) return super.keyPressed(e);
        if (page == Page.EDIT && form.hasOpenMenu()) return form.keyPressed(e);
        if (e.key() == InputConstants.KEY_ESCAPE) {
            if (pressedEntry != null) {
                cancelRowDrag();
                positionRemoves();
                return true;
            }
            onCancel();
            return true;
        }
        if (e.hasControlDown() && e.key() == InputConstants.KEY_S) {
            if (page == Page.EDIT) saveAction();
            else applyAll();
            return true;
        }
        if (page == Page.EDIT) return form.keyPressed(e) || super.keyPressed(e);
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return page == Page.EDIT && form.charTyped(e) || super.charTyped(e);
    }

    @Override
    public void tick() {
        if (page == Page.EDIT) form.tick();
        super.tick();
        if (rowDragging) {
            int delta = pointerY < listTop + 18 ? -4 : pointerY > listBottom - 18 ? 4 : 0;
            listScroll = Math.max(0, Math.min(maxListScroll(), listScroll + delta));
            updateDrop();
        }
        positionRemoves();
    }

    private String fit(String value, int w) {
        return font.width(value) <= w
                ? value
                : font.plainSubstrByWidth(value, Math.max(0, w - font.width("..."))) + "...";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        String heading =
                page == Page.ACTIONS
                        ? "What happens when consumed?"
                        : page == Page.EDIT ? actionTitle(form.result()) : title.getString();
        g.text(font, fit(heading, panelWidth - 24), left + 12, top + 14, EETheme.TEXT, false);
        if (page == Page.OVERVIEW) {
            g.text(
                    font,
                    "When this item is eaten or drunk",
                    left + 12,
                    top + 33,
                    EETheme.MUTED,
                    false);
            EETheme.bevel(
                    g, left + 12, listTop, listWidth, listBottom - listTop, EETheme.INPUT, true);
            g.enableScissor(left + 13, listTop + 1, left + panelWidth - 22, listBottom - 1);
            renderEntries(g, mx, my);
            if (rowDragging) renderRowDrag(g);
            g.disableScissor();
            if (maxListScroll() > 0)
                EETheme.scrollbar(
                        g,
                        left + panelWidth - 19,
                        listTop + 1,
                        4,
                        listBottom - listTop - 2,
                        listThumbY(),
                        listThumbHeight(),
                        listDragging);
        } else if (page == Page.EDIT) form.extractRenderState(g, mx, my, delta);
        EETheme.rect(g, left + 12, top + panelHeight - 34, panelWidth - 24, 1, 0xFF969E91);
        renderWidgetsAndMenu(g, mx, my, delta);
    }

    private void renderEntries(GuiGraphicsExtractor g, int mx, int my) {
        List<Entry> list = entries();
        for (int i = listScroll / 42;
                i < list.size() && listTop + i * 42 - listScroll < listBottom;
                i++) {
            Entry entry = list.get(i);
            ConsumeEffect action = effects.get(entry.action);
            int y = listTop + i * 42 - listScroll;
            boolean hover =
                    !rowDragging && !hasOpenMenu() && insideList(mx, my) && my >= y && my < y + 42;
            boolean selected = entry.action == selectedAction && entry.status == selectedStatus;
            if (selected || hover)
                EETheme.rect(
                        g, left + 13, y, listWidth - 12, 42, selected ? EETheme.THUMB : 0xFF48534F);
            EEDragRow.grip(
                    g, left + 20, y, 42,
                    selected ? EETheme.MUTED : 0xFFB6C2BB);
            drawIcon(g, entry, action, left + 31, y + 13);
            String name =
                    entry.status >= 0
                            ? "Give " + entryName(entry, action)
                            : entryName(entry, action);
            g.text(
                    font,
                    fit(name, listWidth - 118),
                    left + 54,
                    y + 8,
                    selected ? EETheme.TEXT : EETheme.LIGHT,
                    false);
            g.text(
                    font,
                    fit(entrySummary(entry, action), listWidth - 118),
                    left + 54,
                    y + 23,
                    selected ? EETheme.TEXT : 0xFFB6C2BB,
                    false);
            if (hover && mx < left + panelWidth - 84)
                g.setTooltipForNextFrame(
                        font,
                        Component.literal(
                                entryCount(action) > 1
                                        ? "Drag to reorder. Linked effects move together."
                                        : "Drag to reorder."),
                        mx,
                        my);
        }
        if (list.isEmpty()) {
            g.text(font, "No actions added", left + 24, listTop + 18, EETheme.LIGHT, false);
            g.text(
                    font,
                    "Choose Add action to get started.",
                    left + 24,
                    listTop + 35,
                    0xFFB6C2BB,
                    false);
        }
    }

    private void renderRowDrag(GuiGraphicsExtractor g) {
        int boundary = listTop + actionRow(dropBefore) * 42 - listScroll;
        ConsumeEffect action = effects.get(pressedEntry.action);
        int y =
                EEDragRow.floatingTop(pointerY, rowDragOffset, listTop, listBottom, 40);
        EEDragRow.floatingFrame(g, left + 18, y, listWidth - 26, 40);
        drawIcon(g, pressedEntry, action, left + 26, y + 12);
        String name =
                pressedEntry.status >= 0
                        ? "Give " + entryName(pressedEntry, action)
                        : entryName(pressedEntry, action);
        g.text(font, fit(name, listWidth - 70), left + 50, y + 7, EETheme.TEXT, false);
        String summary =
                entryCount(action) > 1
                        ? "Linked effects move together"
                        : entrySummary(pressedEntry, action);
        g.text(font, fit(summary, listWidth - 70), left + 50, y + 22, EETheme.TEXT, false);
        EEDragRow.dropMarker(
                g, left + 14, boundary, listWidth - 14, listTop, listBottom);
    }

    private static String actionTitle(ConsumeEffect action) {
        return switch (action) {
            case ApplyStatusEffectsConsumeEffect e -> "Give potion effect";
            case RemoveStatusEffectsConsumeEffect e -> "Remove potion effects";
            case ClearAllStatusEffectsConsumeEffect e -> "Clear potion effects";
            case TeleportRandomlyConsumeEffect e -> "Random teleport";
            case PlaySoundConsumeEffect e -> "Play sound";
            default -> "Consume action";
        };
    }

    private static String label(ConsumeEffect action) {
        return actionTitle(action);
    }

    private static String statusName(MobEffectInstance e) {
        return effectName(e.getEffect())
                + (e.getAmplifier() == 0 ? "" : " " + (e.getAmplifier() + 1));
    }

    private String entryName(Entry entry, ConsumeEffect action) {
        if (entry.status >= 0)
            return statusName(
                    ((ApplyStatusEffectsConsumeEffect) action).effects().get(entry.status));
        if (action instanceof RemoveStatusEffectsConsumeEffect e && e.effects().size() == 1)
            return "Remove " + effectName(e.effects().stream().findFirst().orElseThrow());
        return label(action);
    }

    private String entrySummary(Entry entry, ConsumeEffect action) {
        if (entry.status >= 0) {
            ApplyStatusEffectsConsumeEffect group = (ApplyStatusEffectsConsumeEffect) action;
            MobEffectInstance status = group.effects().get(entry.status);
            return (status.isInfiniteDuration()
                            ? "Infinite"
                            : format(status.getDuration() / 20.0) + " s")
                    + " · "
                    + percent(group.probability())
                    + "%"
                    + (group.effects().size() > 1 ? " together" : " chance");
        }
        return switch (action) {
            case RemoveStatusEffectsConsumeEffect e ->
                    e.effects().stream()
                            .map(GuiConsumeEffectsEditor::effectName)
                            .collect(java.util.stream.Collectors.joining(", "));
            case ClearAllStatusEffectsConsumeEffect e -> "Like drinking milk";
            case TeleportRandomlyConsumeEffect e -> format(e.diameter()) + " blocks";
            case PlaySoundConsumeEffect e -> GuiItemSettingsEditor.soundName(e.sound());
            default -> "Preserved unchanged";
        };
    }

    private void drawIcon(GuiGraphicsExtractor g, Entry entry, ConsumeEffect action, int x, int y) {
        Holder<MobEffect> status = null;
        if (entry.status >= 0)
            status =
                    ((ApplyStatusEffectsConsumeEffect) action)
                            .effects()
                            .get(entry.status)
                            .getEffect();
        else if (action instanceof RemoveStatusEffectsConsumeEffect e && e.effects().size() > 0)
            status = e.effects().stream().findFirst().orElseThrow();
        if (status != null) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(status.value());
            g.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    Identifier.fromNamespaceAndPath(
                            id.getNamespace(), "mob_effect/" + id.getPath()),
                    x,
                    y,
                    16,
                    16);
        } else {
            ItemStack icon =
                    new ItemStack(
                            action instanceof TeleportRandomlyConsumeEffect
                                    ? Items.CHORUS_FRUIT
                                    : action instanceof PlaySoundConsumeEffect
                                            ? Items.NOTE_BLOCK
                                            : Items.MILK_BUCKET);
            GuiUtils.drawItemStack(g, icon, x, y);
        }
    }

    private static final class EffectForm extends PotionEffectForm<ConsumeEffect> {
        private ConsumeEffect effect;
        private final ConsumeEffect original;
        private int selectedEffect;
        private boolean needsEffect, needsSound, choiceMade, sharedChance;

        EffectForm(Screen parent, ConsumeEffect effect, int selected) {
            super(
                    parent,
                    effect instanceof ApplyStatusEffectsConsumeEffect
                            ? "Potion effect"
                            : label(effect),
                    ignored -> {},
                    effect instanceof ApplyStatusEffectsConsumeEffect e && !e.effects().isEmpty()
                            ? e.effects().get(Math.max(0, selected))
                            : new MobEffectInstance(MobEffects.SPEED, 1200));
            this.effect = effect;
            selectedEffect = Math.max(0, selected);
            original = effect;
        }

        private String error() {
            return invalidReason();
        }

        private boolean hasDraftChanges() {
            return ((GuiConsumeEffectsEditor) parent).adding && choiceMade || isModified();
        }

        private int preferredHeight() {
            int count =
                    switch (effect) {
                        case ApplyStatusEffectsConsumeEffect e ->
                                e.effects().isEmpty() ? 1 : infinite ? 7 : 8;
                        case RemoveStatusEffectsConsumeEffect e -> 1 + e.effects().size();
                        case TeleportRandomlyConsumeEffect e -> 2;
                        default -> 1;
                    };
            return Math.max(144, count * 30 + 82);
        }

        @Override
        protected String invalidReason() {
            if (needsEffect) return "Choose a potion effect first.";
            if (needsSound) return "Choose a sound first.";
            if (effect instanceof RemoveStatusEffectsConsumeEffect e && e.effects().size() == 0)
                return "Choose at least one potion effect to remove.";
            return super.invalidReason();
        }

        @Override
        protected void refreshValidity() {
            super.refreshValidity();
            ((GuiConsumeEffectsEditor) parent).refreshValidity();
        }

        @Override
        public boolean isModified() {
            return !result().equals(original) || !errors.isEmpty();
        }

        @Override
        protected ConsumeEffect result() {
            if (effect instanceof ApplyStatusEffectsConsumeEffect e && !e.effects().isEmpty()) {
                List<MobEffectInstance> list = new ArrayList<>(e.effects());
                list.set(selectedEffect, statusResult());
                return new ApplyStatusEffectsConsumeEffect(list, e.probability());
            }
            return effect;
        }

        @Override
        protected boolean relevant(String key) {
            return !key.equals("duration") || !infinite;
        }

        @Override
        protected void buildRows() {
            switch (effect) {
                case ApplyStatusEffectsConsumeEffect e -> {
                    if (!e.effects().isEmpty()) primaryStatusRows();
                    if (sharedChance)
                        row(
                                "Chance",
                                "This effect runs together with the selected group.",
                                readOnly(percent(e.probability()) + "% together"),
                                130);
                    else
                        row(
                                "Chance (%)",
                                "Chance that this group of effects is applied. 100 always applies"
                                        + " it.",
                                number(
                                        "chance",
                                        percent(e.probability()),
                                        0,
                                        100,
                                        false,
                                        v -> {
                                            ApplyStatusEffectsConsumeEffect current =
                                                    (ApplyStatusEffectsConsumeEffect) effect;
                                            effect =
                                                    new ApplyStatusEffectsConsumeEffect(
                                                            current.effects(), (float) (v / 100));
                                        }),
                                90);
                    if (!e.effects().isEmpty()) displayStatusRows();
                }
                case RemoveStatusEffectsConsumeEffect e -> {
                    row(
                            "Potion effects",
                            "Choose a potion effect to remove after consuming.",
                            action(
                                    e.effects().size() == 0
                                            ? "Choose effects..."
                                            : "Add another...",
                                    () -> pickEffect(parent, null, this::addRemovedEffect)),
                            160);
                    for (Holder<MobEffect> removed : e.effects().stream().toList()) {
                        var remove =
                                action(
                                        "×",
                                        () -> {
                                            RemoveStatusEffectsConsumeEffect current =
                                                    (RemoveStatusEffectsConsumeEffect) effect;
                                            List<Holder<MobEffect>> list =
                                                    new ArrayList<>(
                                                            current.effects().stream().toList());
                                            list.remove(removed);
                                            effect =
                                                    new RemoveStatusEffectsConsumeEffect(
                                                            HolderSet.direct(list));
                                            rebuild();
                                        });
                        remove.setMessage(Component.literal("×").withColor(0xFF5555));
                        row(
                                effectName(removed),
                                "Remove this entry from the list of effects to clear.",
                                remove,
                                20);
                    }
                }
                case ClearAllStatusEffectsConsumeEffect e ->
                        row(
                                "Clear all effects",
                                "Removes all active potion effects when consumed, like drinking"
                                        + " milk.",
                                readOnly("All potion effects"),
                                150);
                case TeleportRandomlyConsumeEffect e -> {
                    row(
                            "Distance (blocks)",
                            "Diameter of the random teleport area, like chorus fruit.",
                            number(
                                    "distance",
                                    format(e.diameter()),
                                    0,
                                    Float.MAX_VALUE,
                                    false,
                                    v -> {
                                        TeleportRandomlyConsumeEffect c =
                                                (TeleportRandomlyConsumeEffect) effect;
                                        effect =
                                                new TeleportRandomlyConsumeEffect(
                                                        (float) v, c.directionalParticles());
                                    }),
                            90);
                    row(
                            "Particle trail",
                            "Show particles pointing in the direction of teleportation.",
                            toggle(
                                    () ->
                                            ((TeleportRandomlyConsumeEffect) effect)
                                                    .directionalParticles(),
                                    v -> {
                                        TeleportRandomlyConsumeEffect c =
                                                (TeleportRandomlyConsumeEffect) effect;
                                        effect = new TeleportRandomlyConsumeEffect(c.diameter(), v);
                                    }),
                            52);
                }
                case PlaySoundConsumeEffect e ->
                        row(
                                "Sound",
                                "Play a sound when consumption finishes.",
                                action(
                                        needsSound
                                                ? "Choose sound..."
                                                : GuiItemSettingsEditor.soundName(e.sound()),
                                        () -> {
                                            Set<Identifier> sounds =
                                                    new HashSet<>(
                                                            BuiltInRegistries.SOUND_EVENT.keySet());
                                            sounds.addAll(
                                                    mc.getSoundManager().getAvailableSounds());
                                            mc.gui.setScreen(
                                                    new GuiResourcePicker(
                                                            parent,
                                                            "Play sound",
                                                            sounds,
                                                            needsSound
                                                                    ? null
                                                                    : ((PlaySoundConsumeEffect)
                                                                                    effect)
                                                                            .sound()
                                                                            .value()
                                                                            .location(),
                                                            id -> {
                                                                needsSound = false;
                                                                choiceMade = true;
                                                                effect =
                                                                        new PlaySoundConsumeEffect(
                                                                                GuiItemSettingsEditor
                                                                                        .sound(id));
                                                            },
                                                            ItemStack.EMPTY,
                                                            true,
                                                            false));
                                        }),
                                180);
                default ->
                        row(
                                "Effect",
                                "This modded action is preserved as-is. Use Data components to edit"
                                        + " its custom fields.",
                                readOnly("Unchanged"),
                                140);
            }
        }

        private void addRemovedEffect(Holder<MobEffect> removed) {
            RemoveStatusEffectsConsumeEffect current = (RemoveStatusEffectsConsumeEffect) effect;
            List<Holder<MobEffect>> list = new ArrayList<>(current.effects().stream().toList());
            if (!list.contains(removed)) {
                choiceMade = true;
                list.add(removed);
                effect = new RemoveStatusEffectsConsumeEffect(HolderSet.direct(list));
            }
        }
    }

    private static List<Holder<MobEffect>> effectChoices() {
        return BuiltInRegistries.MOB_EFFECT.stream()
                .map(BuiltInRegistries.MOB_EFFECT::wrapAsHolder)
                .sorted(Comparator.comparing(e -> e.value().getDisplayName().getString()))
                .toList();
    }

    private static String effectName(Holder<MobEffect> effect) {
        return effect.value().getDisplayName().getString();
    }

    private static void pickEffect(
            Screen parent, Holder<MobEffect> current, Consumer<Holder<MobEffect>> setter) {
        Map<Identifier, String> choices = new LinkedHashMap<>();
        for (Holder<MobEffect> effect : effectChoices())
            choices.put(BuiltInRegistries.MOB_EFFECT.getKey(effect.value()), effectName(effect));
        net.minecraft.client.Minecraft.getInstance()
                .gui
                .setScreen(
                        new GuiResourcePicker(
                                parent,
                                "Choose effect",
                                choices,
                                current == null
                                        ? null
                                        : BuiltInRegistries.MOB_EFFECT.getKey(current.value()),
                                id ->
                                        setter.accept(
                                                BuiltInRegistries.MOB_EFFECT.wrapAsHolder(
                                                        BuiltInRegistries.MOB_EFFECT
                                                                .getOptional(id)
                                                                .orElseThrow())),
                                "effect"));
    }

    private abstract static class PotionEffectForm<T> extends EESettingsScreen<T> {
        protected Holder<MobEffect> type;
        protected int level, ticks;
        protected boolean infinite, ambient, particles, icon;
        private MobEffectInstance original;
        private MobEffectInstance cached;

        PotionEffectForm(Screen parent, String title, Consumer<T> setter, MobEffectInstance e) {
            super(parent, title, setter);
            loadStatus(e);
        }

        protected final void loadStatus(MobEffectInstance e) {
            original = e;
            cached = e;
            type = e.getEffect();
            level = e.getAmplifier() + 1;
            ticks = e.getDuration();
            infinite = e.isInfiniteDuration();
            ambient = e.isAmbient();
            particles = e.isVisible();
            icon = e.showIcon();
        }

        protected final MobEffectInstance statusResult() {
            int duration = infinite ? -1 : ticks;
            if (type.equals(original.getEffect())
                    && duration == original.getDuration()
                    && level == original.getAmplifier() + 1
                    && ambient == original.isAmbient()
                    && particles == original.isVisible()
                    && icon == original.showIcon()) return original;
            if (type.equals(cached.getEffect())
                    && duration == cached.getDuration()
                    && level == cached.getAmplifier() + 1
                    && ambient == cached.isAmbient()
                    && particles == cached.isVisible()
                    && icon == cached.showIcon()) return cached;
            // Preserve hidden fallback effects and other native fields when changing visible
            // settings.
            var ops =
                    mc.level
                            .registryAccess()
                            .createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var tag =
                    (net.minecraft.nbt.CompoundTag)
                            MobEffectInstance.CODEC.encodeStart(ops, original).getOrThrow();
            tag.putString("id", BuiltInRegistries.MOB_EFFECT.getKey(type.value()).toString());
            tag.putInt("duration", duration);
            tag.putInt("amplifier", level - 1);
            tag.putBoolean("ambient", ambient);
            tag.putBoolean("show_particles", particles);
            tag.putBoolean("show_icon", icon);
            cached = MobEffectInstance.CODEC.parse(ops, tag).getOrThrow();
            return cached;
        }

        @Override
        protected boolean relevant(String key) {
            return !key.equals("duration") || !infinite;
        }

        protected final void primaryStatusRows() {
            EffectForm editor = (EffectForm) this;
            row(
                    "Effect",
                    "Potion effect to apply.",
                    action(
                            editor.needsEffect ? "Choose effect..." : effectName(type),
                            () ->
                                    pickEffect(
                                            parent,
                                            editor.needsEffect ? null : type,
                                            v -> {
                                                type = v;
                                                editor.needsEffect = false;
                                                editor.choiceMade = true;
                                            })),
                    180);
            row(
                    "Level",
                    "Level 1 is the standard effect. Levels range from 1 to 256.",
                    number("level", "" + level, 1, 256, true, v -> level = (int) v),
                    90);
            if (!infinite)
                row(
                        "Duration (seconds)",
                        "Effect duration. 20 game ticks equals one second.",
                        number(
                                "duration",
                                format(ticks / 20.0),
                                0,
                                Integer.MAX_VALUE / 20.0,
                                false,
                                v -> ticks = (int) Math.round(v * 20)),
                        90);
        }

        protected final void displayStatusRows() {
            row(
                    "Infinite duration",
                    "Lasts until explicitly removed.",
                    toggle(
                            () -> infinite,
                            v -> {
                                infinite = v;
                                if (!v && ticks < 0) ticks = 1200;
                            }),
                    52);
            row(
                    "Particles",
                    "Show potion particles around the player.",
                    toggle(() -> particles, v -> particles = v),
                    52);
            row(
                    "HUD icon",
                    "Show this potion effect's icon on the screen.",
                    toggle(() -> icon, v -> icon = v),
                    52);
            row(
                    "Ambient",
                    "Use the subtle particles associated with beacon effects.",
                    toggle(() -> ambient, v -> ambient = v),
                    52);
        }
    }
}
