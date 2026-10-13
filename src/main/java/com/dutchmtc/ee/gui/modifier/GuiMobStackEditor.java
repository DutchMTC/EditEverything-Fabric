package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.EEDragRow;
import com.dutchmtc.ee.gui.components.EEDropdownScrollbar;
import com.dutchmtc.ee.gui.components.EETheme;
import com.dutchmtc.ee.gui.selector.GuiResourcePicker;
import com.dutchmtc.ee.utils.GuiEntityPreviewUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.MobStackData;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/** Direct top-to-bottom stack editing, with nested entity editors sharing only this draft. */
public final class GuiMobStackEditor extends GuiModifier<ItemStack> {
    private static final int ROW = 28;
    private final MobStackData draft;
    private final List<EEButton> edits = new ArrayList<>(), removes = new ArrayList<>();
    private final EEDropdownScrollbar scrollbar = new EEDropdownScrollbar();
    private MobStackData.Mob selected, pressed;
    private int left,
            top,
            panelWidth,
            panelHeight,
            listX,
            listWidth,
            listTop,
            listBottom,
            scroll,
            drop;
    private boolean rowDragging, cameraDragging, previewDirty = true;
    private double pressX, pressY, pointerY, rowDragOffset;
    private Entity preview;
    private net.minecraft.world.level.Level previewLevel;
    private float yaw = -25, pitch = -8, zoom = 1;
    private EEButton add, duplicate, apply;

    public GuiMobStackEditor(Screen parent, ItemStack item, Consumer<ItemStack> setter) {
        super(parent, Component.literal("Mob stack"), setter);
        draft = new MobStackData(item);
        selected = draft.mobs().getLast();
    }

    @Override
    public boolean isModified() {
        return draft.modified();
    }

    @Override
    public void init() {
        clearWidgets();
        clearFocus();
        edits.clear();
        removes.clear();
        panelWidth = Math.min(440, width - 16);
        panelHeight =
                Math.min(Math.min(310, height - 16), Math.max(210, 90 + draft.mobs().size() * ROW));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        listX = left + (panelWidth >= 380 ? 124 : 100);
        listWidth = left + panelWidth - 12 - listX;
        listTop = top + 50;
        listBottom = top + panelHeight - 40;
        add =
                addRenderableWidget(
                        new EEButton(
                                left + panelWidth - 92,
                                top + 10,
                                80,
                                20,
                                Component.literal("Add mob"),
                                b -> chooseMob()));
        duplicate =
                addRenderableWidget(
                        new EEButton(
                                left + panelWidth - 162,
                                top + 10,
                                66,
                                20,
                                Component.literal("Duplicate"),
                                b -> duplicateSelected()));
        int footer = top + panelHeight - 28, w = (panelWidth - 28) / 2;
        addRenderableWidget(
                new EEButton(
                        left + 12, footer, w, 20, Component.literal("Cancel"), b -> onCancel()));
        apply =
                addRenderableWidget(
                        new EEButton(
                                left + 16 + w,
                                footer,
                                w,
                                20,
                                Component.literal("Apply"),
                                b -> {
                                    if (draft.validationReason() == null) {
                                        set(draft.result());
                                        mc.gui.setScreen(parent);
                                    }
                                }));
        for (var mob : draft.mobs()) {
            EEButton edit = new EEButton(0, 0, 36, 18, Component.literal("Edit"), b -> edit(mob));
            edit.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "Edit this mob's type, name, behavior, equipment and"
                                            + " effects.")));
            edits.add(addWidget(edit));
            EEButton remove = new EEButton(0, 0, 18, 18, Component.literal("×"), b -> remove(mob));
            remove.active = draft.mobs().size() > 1;
            remove.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    remove.active
                                            ? "Remove this mob. The mobs above and below join"
                                                    + " together."
                                            : "Keep at least one mob in the stack.")));
            removes.add(addWidget(remove));
        }
        pressed = null;
        rowDragging = cameraDragging = false;
        scrollbar.reset();
        position();
        refreshButtons();
    }

    private int gutter() {
        return maxScroll() > 0 ? EEDropdownScrollbar.WIDTH : 0;
    }

    private int rowRight() {
        return listX + listWidth - 4 - gutter();
    }

    private int maxScroll() {
        return Math.max(0, draft.mobs().size() * ROW - (listBottom - listTop));
    }

    private void position() {
        scroll = Math.clamp(scroll, 0, maxScroll());
        for (int i = 0; i < edits.size(); i++) {
            int y = listTop + i * ROW - scroll + (ROW - 18) / 2;
            boolean visible = y >= listTop && y + 18 <= listBottom && !rowDragging;
            EEButton edit = edits.get(i), remove = removes.get(i);
            edit.setPosition(rowRight() - 60, y);
            remove.setPosition(rowRight() - 20, y);
            edit.visible = remove.visible = visible;
        }
        scrollbar.layout(
                listX + listWidth - EEDropdownScrollbar.WIDTH,
                listTop,
                listBottom - listTop,
                listBottom - listTop,
                draft.mobs().size() * ROW,
                scroll,
                ROW);
    }

    private void refreshButtons() {
        String reason = draft.validationReason();
        apply.active = reason == null;
        apply.setTooltip(
                Tooltip.create(
                        Component.literal(
                                reason == null
                                        ? "Save this stack to the entity draft. Spawning riders"
                                              + " requires EditEverything on the server (also works"
                                              + " in singleplayer)."
                                        : reason)));
        add.active = draft.count() < MobStackData.MAX_MOBS;
        add.setTooltip(
                Tooltip.create(
                        Component.literal(
                                add.active
                                        ? "Add a mob on top of the stack."
                                        : "A stack can contain at most "
                                                + MobStackData.MAX_MOBS
                                                + " mobs.")));
        String duplicateReason = draft.duplicateReason(selected);
        duplicate.active = duplicateReason == null;
        duplicate.setTooltip(
                Tooltip.create(
                        Component.literal(
                                duplicateReason == null
                                        ? "Duplicate the selected mob and its settings, placing the"
                                              + " copy above it."
                                        : duplicateReason)));
    }

    private void duplicateSelected() {
        var copy = draft.duplicate(selected);
        if (copy == null) return;
        selected = copy;
        previewDirty = true;
        init();
        int y = draft.mobs().indexOf(copy) * ROW;
        if (y < scroll) scroll = y;
        else if (y + ROW > scroll + listBottom - listTop) scroll = y + ROW - (listBottom - listTop);
        position();
    }

    private void chooseMob() {
        if (draft.count() >= MobStackData.MAX_MOBS) return;
        var choices = new LinkedHashMap<net.minecraft.resources.Identifier, String>();
        BuiltInRegistries.ENTITY_TYPE.forEach(
                type -> {
                    if (type.canSummon()
                            && (SpawnEggItem.byId(type).isPresent()
                                    || type == EntityTypes.ARMOR_STAND))
                        choices.put(
                                BuiltInRegistries.ENTITY_TYPE.getKey(type),
                                type.getDescription().getString());
                });
        mc.gui.setScreen(
                new GuiResourcePicker(
                        this,
                        "Add mob on top",
                        choices,
                        null,
                        id -> {
                            if (draft.count() >= MobStackData.MAX_MOBS) return;
                            draft.add(BuiltInRegistries.ENTITY_TYPE.get(id).orElseThrow().value());
                            selected = draft.mobs().getFirst();
                            scroll = 0;
                            previewDirty = true;
                        },
                        "mob"));
    }

    private void edit(MobStackData.Mob mob) {
        selected = mob;
        ItemStack egg = mob.egg();
        if (egg.isEmpty()) {
            var choices = new LinkedHashMap<net.minecraft.resources.Identifier, String>();
            BuiltInRegistries.ENTITY_TYPE.forEach(
                    type -> {
                        if (type.canSummon()
                                && (SpawnEggItem.byId(type).isPresent()
                                        || type == EntityTypes.ARMOR_STAND))
                            choices.put(
                                    BuiltInRegistries.ENTITY_TYPE.getKey(type),
                                    type.getDescription().getString());
                    });
            mc.gui.setScreen(
                    new GuiResourcePicker(
                            this,
                            "Choose mob type",
                            choices,
                            null,
                            id -> {
                                var type =
                                        BuiltInRegistries.ENTITY_TYPE.get(id).orElseThrow().value();
                                ItemStack replacement =
                                        new ItemStack(net.minecraft.world.item.Items.PIG_SPAWN_EGG);
                                replacement.set(
                                        net.minecraft.core.component.DataComponents.ENTITY_DATA,
                                        net.minecraft.world.item.component.TypedEntityData.of(
                                                type, mob.tag()));
                                mob.update(replacement);
                                previewDirty = true;
                            },
                            "mob"));
            return;
        }
        mc.gui.setScreen(
                new GuiSpawnEggModifier(
                        this,
                        edited -> {
                            mob.update(edited);
                            previewDirty = true;
                        },
                        egg,
                        true));
    }

    private void remove(MobStackData.Mob mob) {
        if (draft.mobs().size() <= 1) return;
        Runnable remove =
                () -> {
                    draft.remove(mob);
                    selected = draft.mobs().getLast();
                    previewDirty = true;
                    mc.gui.setScreen(this);
                };
        if (!mob.tag().getList("Passengers").orElseGet(net.minecraft.nbt.ListTag::new).isEmpty())
            mc.gui.setScreen(
                    new GuiConfirmation(
                            this,
                            Component.literal("Remove this mob and its additional riders?"),
                            Component.literal("Remove mob"),
                            remove,
                            () -> mc.gui.setScreen(this)));
        else remove.run();
    }

    private boolean insideList(double x, double y) {
        return x >= listX && x < rowRight() && y >= listTop && y < listBottom;
    }

    private boolean insidePreview(double x, double y) {
        return x >= left + 12 && x < listX - 8 && y >= listTop && y < listBottom;
    }

    private int index(double y) {
        return Math.clamp(((int) y - listTop + scroll) / ROW, 0, draft.mobs().size() - 1);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (scrollbar.mouseClicked(
                e,
                v -> {
                    scroll = v;
                    position();
                })) return true;
        if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT
                && insidePreview(e.x(), e.y())) {
            cameraDragging = true;
            setDragging(true);
            return true;
        }
        if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT
                && insideList(e.x(), e.y())) {
            for (EEButton button : edits)
                if (button.visible && button.isMouseOver(e.x(), e.y()))
                    return super.mouseClicked(e, twice);
            for (EEButton button : removes)
                if (button.visible && button.isMouseOver(e.x(), e.y()))
                    return super.mouseClicked(e, twice);
            if (e.y() - listTop + scroll >= draft.mobs().size() * ROW) return true;
            selected = pressed = draft.mobs().get(index(e.y()));
            refreshButtons();
            pressX = e.x();
            pressY = pointerY = e.y();
            rowDragOffset = e.y() - (listTop + draft.mobs().indexOf(pressed) * ROW - scroll);
            setDragging(true);
            clearFocus();
            return true;
        }
        return super.mouseClicked(e, twice);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (scrollbar.mouseDragged(
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
                        (int)
                                Math.round(
                                        (pointerY - rowDragOffset + ROW / 2.0 - listTop + scroll)
                                                / ROW),
                        0,
                        draft.mobs().size());
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
    public boolean mouseReleased(MouseButtonEvent e) {
        if (scrollbar.mouseReleased()) return true;
        if (cameraDragging) {
            cameraDragging = false;
            return true;
        }
        if (pressed != null
                && e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            if (rowDragging) {
                int before = draft.mobs().indexOf(pressed);
                draft.move(pressed, drop - (before < drop ? 1 : 0));
                previewDirty = true;
            }
            pressed = null;
            rowDragging = false;
            init();
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (insidePreview(x, y)) {
            zoom = Math.clamp(zoom + (float) vertical * .1f, .5f, 2);
            return true;
        }
        if (x >= listX && x < listX + listWidth && y >= listTop && y < listBottom) {
            scroll -= (int) (vertical * ROW);
            position();
            if (rowDragging) updateDrop();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.isEscape() && pressed != null) {
            pressed = null;
            rowDragging = false;
            setDragging(false);
            position();
            return true;
        }
        if (e.hasAltDown() && (e.isUp() || e.isDown())) {
            int index = draft.mobs().indexOf(selected);
            if (index >= 0) {
                draft.move(selected, index + (e.isUp() ? -1 : 1));
                previewDirty = true;
                init();
            }
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        onCancel();
    }

    private void refreshPreview() {
        if (!previewDirty && previewLevel == mc.level) return;
        previewDirty = false;
        previewLevel = mc.level;
        preview = null;
        if (mc.level == null || draft.validationReason() != null) return;
        try {
            var data = draft.result().get(net.minecraft.core.component.DataComponents.ENTITY_DATA);
            var tag = data.copyTagWithoutId();
            tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(data.type()).toString());
            preview =
                    EntityType.loadEntityRecursive(
                            tag,
                            mc.level,
                            new net.minecraft.world.entity.EntitySpawnRequest(
                                    EntitySpawnReason.LOAD, true),
                            GuiEntityPreviewUtils::prepareStackPreview);
            if (preview != null) {
                GuiEntityPreviewUtils.restoreDisplayText(preview, tag);
                GuiEntityPreviewUtils.positionStackPreview(preview);
            }
        } catch (Exception ex) {
            com.dutchmtc.ee.EEMod.LOGGER.debug("Cannot preview mob stack", ex);
        }
    }

    private String fit(String text, int width) {
        return font.width(text) <= width
                ? text
                : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiUtils.drawGradientRect(
                g, left, top, left + panelWidth, top + panelHeight, EETheme.PANEL, EETheme.PANEL);
        EETheme.bevel(g, left, top, panelWidth, panelHeight, EETheme.PANEL, false);
        g.text(font, title, left + 12, top + 16, EETheme.TEXT, false);
        g.text(font, "Top to bottom", listX, top + 36, EETheme.MUTED, false);
        int px = left + 12, pw = listX - 8 - px, ph = listBottom - listTop;
        GuiUtils.drawGradientRect(g, px, listTop, px + pw, listBottom, 0xFF3B4544, 0xFF252C2B);
        refreshPreview();
        if (preview != null) {
            try {
                GuiEntityPreviewUtils.renderStackInBox(
                        g, px + 2, listTop + 2, pw - 4, ph - 4, zoom, yaw, pitch, preview);
            } catch (Exception ex) {
                preview = null;
                com.dutchmtc.ee.EEMod.LOGGER.debug("Cannot render mob stack", ex);
            }
        }
        if (preview == null)
            g.text(font, fit("No preview", pw - 8), px + 4, listTop + ph / 2, EETheme.LIGHT, false);
        EETheme.bevel(
                g, listX, listTop - 1, listWidth, listBottom - listTop + 2, EETheme.INPUT, true);
        g.enableScissor(listX + 1, listTop, listX + listWidth - gutter(), listBottom);
        var mobs = draft.mobs();
        for (int i = 0; i < mobs.size(); i++) {
            int y = listTop + i * ROW - scroll;
            if (y + ROW <= listTop || y >= listBottom) continue;
            var mob = mobs.get(i);
            boolean hover = !rowDragging && insideList(mx, my) && index(my) == i;
            EETheme.rect(
                    g,
                    listX + 2,
                    y + 1,
                    rowRight() - listX - 2,
                    ROW - 2,
                    mob == selected
                            ? EETheme.CONTROL
                            : hover ? EETheme.OPTION_HOVER : EETheme.PANEL);
            EEDragRow.grip(g, listX + 7, y, ROW, EETheme.MUTED);
            g.text(
                    font,
                    fit(mob.name(), rowRight() - listX - 84),
                    listX + 19,
                    y + (ROW - font.lineHeight) / 2 + 1,
                    EETheme.TEXT,
                    false);
            if (hover && mx < rowRight() - 64) {
                List<Component> help = new ArrayList<>();
                help.add(Component.literal(mob.name()));
                help.add(
                        Component.literal(
                                i == mobs.size() - 1
                                        ? "Base mob: stands on the ground."
                                        : "Rides " + mobs.get(i + 1).name() + "."));
                if (!mob.tag()
                        .getList("Passengers")
                        .orElseGet(net.minecraft.nbt.ListTag::new)
                        .isEmpty())
                    help.add(
                            Component.literal(
                                    "Additional imported riders stay attached to this mob."));
                var lines =
                        help.stream()
                                .flatMap(
                                        line ->
                                                font
                                                        .split(line, Math.min(240, width - 24))
                                                        .stream())
                                .toList();
                g.setTooltipForNextFrame(font, lines, mx, my);
            }
            edits.get(i).extractRenderState(g, mx, my, delta);
            removes.get(i).extractRenderState(g, mx, my, delta);
        }
        if (rowDragging) {
            int y = EEDragRow.floatingTop(pointerY, rowDragOffset, listTop, listBottom, ROW - 2);
            EEDragRow.floatingFrame(g, listX + 6, y, rowRight() - listX - 10, ROW - 2);
            EEDragRow.grip(g, listX + 11, y, ROW - 2, EETheme.MUTED);
            g.text(
                    font,
                    fit(pressed.name(), rowRight() - listX - 38),
                    listX + 23,
                    y + (ROW - 2 - font.lineHeight) / 2 + 1,
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
        super.extractRenderState(g, mx, my, delta);
    }
}
