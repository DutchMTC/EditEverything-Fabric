package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.modifier.GuiBooleanButton;
import com.dutchmtc.ee.gui.modifier.GuiItemStackModifier;
import com.dutchmtc.ee.network.EEClientNetworking;
import com.dutchmtc.ee.utils.ArmorStandEditorUtils;
import com.dutchmtc.ee.utils.ArmorStandItemUtils;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiEntityPreviewUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtilsClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueOutput;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

@Environment(EnvType.CLIENT)
public class GuiArmorStandEditor extends Screen {
    private enum Tab {
        POSE("gui.ee.armorstand.tab.pose"),
        EQUIPMENT("gui.ee.armorstand.tab.equipment"),
        LOCKS("gui.ee.armorstand.tab.locks"),
        OPTIONS("gui.ee.armorstand.tab.options");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private enum PosePart {
        HEAD(ArmorStandEditorUtils.POSE_HEAD, "gui.ee.armorstand.part.head"),
        BODY(ArmorStandEditorUtils.POSE_BODY, "gui.ee.armorstand.part.body"),
        LEFT_ARM(ArmorStandEditorUtils.POSE_LEFT_ARM, "gui.ee.armorstand.part.left_arm"),
        RIGHT_ARM(ArmorStandEditorUtils.POSE_RIGHT_ARM, "gui.ee.armorstand.part.right_arm"),
        LEFT_LEG(ArmorStandEditorUtils.POSE_LEFT_LEG, "gui.ee.armorstand.part.left_leg"),
        RIGHT_LEG(ArmorStandEditorUtils.POSE_RIGHT_LEG, "gui.ee.armorstand.part.right_leg");

        final String nbtKey;
        final String labelKey;

        PosePart(String nbtKey, String labelKey) {
            this.nbtKey = nbtKey;
            this.labelKey = labelKey;
        }
    }

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 280;
    private static final int PAD = 12;

    private static final int PREVIEW_W = 190;
    private static final int PREVIEW_H = 220;

    private static final int TAB_H = 20;
    private static final int BTN_H = 20;
    private static final float DEFAULT_PREVIEW_YAW = 0.0f;
    private static final float DEFAULT_PREVIEW_PITCH = 16.0f;

    private final Screen parent;
    private final int targetEntityId;
    private final boolean showGetAsItemButton;

    private Tab tab = Tab.POSE;
    private PosePart posePart = PosePart.HEAD;

    private ArmorStand preview;
    private CompoundTag originalState;
    private CompoundTag currentState;

    private int panelLeft;
    private int panelTop;
    private int previewLeft;
    private int previewTop;

    private boolean draggingPreview;
    private float previewYaw;
    private float previewPitch;

    private EditBox nameField;
    private final int[] lockHeaderX = new int[6];
    private int lockHeaderY;

    public GuiArmorStandEditor(Screen parent, int entityId) {
        this(parent, entityId, false);
    }

    public GuiArmorStandEditor(Screen parent, int entityId, boolean showGetAsItemButton) {
        super(Component.translatable("gui.ee.armorstand.title"));
        this.parent = parent;
        this.targetEntityId = entityId;
        this.showGetAsItemButton = showGetAsItemButton;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    protected void init() {
        panelLeft = width / 2 - PANEL_W / 2;
        panelTop = height / 2 - PANEL_H / 2;
        previewLeft = panelLeft + PAD;
        previewTop = panelTop + TAB_H + 4;

        if (minecraft == null || minecraft.level == null) {
            minecraft.setScreen(parent);
            return;
        }

        HolderLookup.Provider registryAccess = minecraft.level.registryAccess();
        Entity target = minecraft.level.getEntity(targetEntityId);
        if (!(target instanceof ArmorStand armorStand)) {
            minecraft.setScreen(parent);
            return;
        }

        if (preview == null) {
            preview = new ArmorStand(minecraft.level, armorStand.getX(), armorStand.getY(), armorStand.getZ());
        }

        if (originalState == null) {
            originalState = ArmorStandEditorUtils.writeState(armorStand, registryAccess);
        }
        if (currentState == null) {
            currentState = originalState.copy();
        }
        ArmorStandEditorUtils.applyState(preview, currentState, registryAccess);

        resetPreviewView();
        draggingPreview = false;

        rebuildUi();
    }

    private void resetPreviewView() {
        previewYaw = DEFAULT_PREVIEW_YAW;
        previewPitch = DEFAULT_PREVIEW_PITCH;
    }

    private boolean isModified() {
        return originalState != null && currentState != null && !currentState.equals(originalState);
    }

    private void onCancel() {
        if (minecraft == null) {
            return;
        }
        if (isModified()) {
            minecraft.setScreen(new GuiConfirmation(this,
                    Component.translatable("gui.ee.discard_changes_question"),
                    () -> minecraft.setScreen(parent),
                    () -> minecraft.setScreen(this)));
        } else {
            minecraft.setScreen(parent);
        }
    }

    private void rebuildUi() {
        clearWidgets();

        int controlsLeft = previewLeft + PREVIEW_W + PAD;
        int controlsTop = panelTop;
        int controlsW = panelLeft + PANEL_W - PAD - controlsLeft;

        // Tabs
        int tabCount = Tab.values().length;
        int tabW = (controlsW - (tabCount - 1) * 2) / tabCount;
        int tabX = controlsLeft;

        var poseTab = new EEButton(tabX, controlsTop, tabW, TAB_H, Component.translatable(Tab.POSE.key), b -> {
            tab = Tab.POSE;
            rebuildUi();
        });
        poseTab.active = tab != Tab.POSE;
        addRenderableWidget(poseTab);

        tabX += tabW + 2;
        var equipTab = new EEButton(tabX, controlsTop, tabW, TAB_H,
                Component.translatable(Tab.EQUIPMENT.key), b -> {
            tab = Tab.EQUIPMENT;
            rebuildUi();
        });
        equipTab.active = tab != Tab.EQUIPMENT;
        addRenderableWidget(equipTab);

        tabX += tabW + 2;
        var locksTab = new EEButton(tabX, controlsTop, tabW, TAB_H,
                Component.translatable(Tab.LOCKS.key), b -> {
            tab = Tab.LOCKS;
            rebuildUi();
        });
        locksTab.active = tab != Tab.LOCKS;
        addRenderableWidget(locksTab);

        tabX += tabW + 2;
        var optionsTab = new EEButton(tabX, controlsTop, tabW, TAB_H,
                Component.translatable(Tab.OPTIONS.key), b -> {
            tab = Tab.OPTIONS;
            rebuildUi();
        });
        optionsTab.active = tab != Tab.OPTIONS;
        addRenderableWidget(optionsTab);

        int y = controlsTop + TAB_H + 8;

        switch (tab) {
            case POSE -> y = buildPoseTab(controlsLeft, y, controlsW);
            case EQUIPMENT -> y = buildEquipmentTab(controlsLeft, y, controlsW);
            case LOCKS -> y = buildLocksTab(controlsLeft, y, controlsW);
            case OPTIONS -> y = buildOptionsTab(controlsLeft, y, controlsW);
        }

        // Bottom actions
        int bottomY = panelTop + PANEL_H - PAD - BTN_H;
        int rightEdge = panelLeft + PANEL_W - PAD;

        addRenderableWidget(new EEButton(panelLeft + PAD, bottomY, 90, BTN_H, Component.translatable("gui.ee.armorstand.reset"),
                b -> {
                    if (minecraft == null || minecraft.level == null) return;
                    currentState = originalState.copy();
                    ArmorStandEditorUtils.applyState(preview, currentState, minecraft.level.registryAccess());
                    rebuildUi();
                }));

        addRenderableWidget(new EEButton(panelLeft + PAD + 94, bottomY, 110, BTN_H, Component.translatable("gui.ee.armorstand.reset_view"),
                b -> resetPreviewView()));

        addRenderableWidget(new EEButton(panelLeft + PANEL_W - PAD - 186, bottomY, 90, BTN_H,
                Component.translatable("gui.cancel"), b -> onCancel()));

        if (showGetAsItemButton) {
            int btnW = 120;
            addRenderableWidget(new EEButton(rightEdge - btnW, bottomY - BTN_H - 4, btnW, BTN_H,
                    Component.translatable("gui.ee.armorstand.get_as_item"),
                    b -> giveTargetArmorStandAsItem()));
        }

        addRenderableWidget(new EEButton(panelLeft + PANEL_W - PAD - 92, bottomY, 92, BTN_H,
                Component.translatable("gui.ee.armorstand.apply").withStyle(ChatFormatting.GREEN), b -> {
                    EEClientNetworking.sendApplyArmorStandEdits(targetEntityId, currentState);
                    minecraft.setScreen(parent);
                }));
    }

    private void giveTargetArmorStandAsItem() {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        Entity target = minecraft.level.getEntity(targetEntityId);
        if (!(target instanceof ArmorStand armorStand)) {
            ChatUtils.error(I18n.get("cmd.ee.armorstand.no_target"));
            return;
        }

        ItemStack item = new ItemStack(Items.ARMOR_STAND);
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, minecraft.level.registryAccess());
        armorStand.saveWithoutId(out);
        ArmorStandItemUtils.setArmorStandEntityTag(item, out.buildResult());

        ItemUtilsClient.give(item);
    }

    private int buildPoseTab(int x, int y, int w) {
        int partW = (w - 4) / 3;
        PosePart[] parts = PosePart.values();
        for (int i = 0; i < parts.length; i++) {
            PosePart p = parts[i];
            int bx = x + (i % 3) * (partW + 2);
            int by = y + (i / 3) * (BTN_H + 2);
            var btn = new EEButton(bx, by, partW, BTN_H, Component.translatable(p.labelKey), b -> {
                posePart = p;
                rebuildUi();
            });
            btn.active = posePart != p;
            addRenderableWidget(btn);
        }
        y += (BTN_H + 2) * 2 + 8;

        // Sliders for X/Y/Z of selected part
        CompoundTag pose = currentState.getCompoundOrEmpty(ArmorStandEditorUtils.KEY_POSE);
        float[] xyz = readPose(pose, posePart.nbtKey);

        int sliderW = w;
        addRenderableWidget(new DegreeSlider(x, y, sliderW, BTN_H, Component.translatable("gui.ee.armorstand.axis.x"),
                -180.0f, 180.0f, xyz[0], v -> setPoseAxis(posePart.nbtKey, 0, v)));
        y += BTN_H + 4;
        addRenderableWidget(new DegreeSlider(x, y, sliderW, BTN_H, Component.translatable("gui.ee.armorstand.axis.y"),
                -180.0f, 180.0f, xyz[1], v -> setPoseAxis(posePart.nbtKey, 1, v)));
        y += BTN_H + 4;
        addRenderableWidget(new DegreeSlider(x, y, sliderW, BTN_H, Component.translatable("gui.ee.armorstand.axis.z"),
                -180.0f, 180.0f, xyz[2], v -> setPoseAxis(posePart.nbtKey, 2, v)));
        y += BTN_H + 8;

        addRenderableWidget(new EEButton(x, y, w, BTN_H, Component.translatable("gui.ee.armorstand.reset_part"),
                b -> {
                    resetPosePart(posePart);
                    rebuildUi();
                }));
        return y + BTN_H + 4;
    }

    private int buildEquipmentTab(int x, int y, int w) {
        final int slot = 18;
        final int rowH = 22;

        int leftColX = x + 18;
        int rightColX = x + (w / 2) + 8;

        int y0 = y;

        addRenderableWidget(new ItemSlotButton(leftColX, y0, Component.translatable("gui.ee.armorstand.slot.head"),
                () -> readEquip(ArmorStandEditorUtils.EQUIP_HEAD),
                is -> writeEquip(ArmorStandEditorUtils.EQUIP_HEAD, is)));
        y0 += rowH;

        addRenderableWidget(new ItemSlotButton(leftColX, y0, Component.translatable("gui.ee.armorstand.slot.chest"),
                () -> readEquip(ArmorStandEditorUtils.EQUIP_CHEST),
                is -> writeEquip(ArmorStandEditorUtils.EQUIP_CHEST, is)));
        y0 += rowH;

        addRenderableWidget(new ItemSlotButton(leftColX, y0, Component.translatable("gui.ee.armorstand.slot.legs"),
                () -> readEquip(ArmorStandEditorUtils.EQUIP_LEGS),
                is -> writeEquip(ArmorStandEditorUtils.EQUIP_LEGS, is)));
        y0 += rowH;

        addRenderableWidget(new ItemSlotButton(leftColX, y0, Component.translatable("gui.ee.armorstand.slot.feet"),
                () -> readEquip(ArmorStandEditorUtils.EQUIP_FEET),
                is -> writeEquip(ArmorStandEditorUtils.EQUIP_FEET, is)));

        int handsY = y + rowH;
        addRenderableWidget(new ItemSlotButton(rightColX, handsY, Component.translatable("gui.ee.armorstand.slot.mainhand"),
                () -> readEquip(ArmorStandEditorUtils.EQUIP_MAINHAND),
                is -> writeEquip(ArmorStandEditorUtils.EQUIP_MAINHAND, is)));

        handsY += rowH;
        addRenderableWidget(new ItemSlotButton(rightColX, handsY, Component.translatable("gui.ee.armorstand.slot.offhand"),
                () -> readEquip(ArmorStandEditorUtils.EQUIP_OFFHAND),
                is -> writeEquip(ArmorStandEditorUtils.EQUIP_OFFHAND, is)));

        int hintY = y + rowH * 4 + 6;
        return hintY + font.lineHeight + 4;
    }

    private int buildOptionsTab(int x, int y, int w) {
        int halfW = (w - 2) / 2;

        addRenderableWidget(new GuiBooleanButton(x, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.invisible"),
                v -> setFlag(ArmorStandEditorUtils.KEY_INVISIBLE, v),
                () -> currentState.getBooleanOr(ArmorStandEditorUtils.KEY_INVISIBLE, false)));
        addRenderableWidget(new GuiBooleanButton(x + halfW + 2, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.glowing"),
                v -> setFlag(ArmorStandEditorUtils.KEY_GLOWING, v),
                () -> currentState.getBooleanOr(ArmorStandEditorUtils.KEY_GLOWING, false)));
        y += BTN_H + 4;

        addRenderableWidget(new GuiBooleanButton(x, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.no_gravity"),
                v -> setFlag(ArmorStandEditorUtils.KEY_NO_GRAVITY, v),
                () -> currentState.getBooleanOr(ArmorStandEditorUtils.KEY_NO_GRAVITY, false)));
        addRenderableWidget(new GuiBooleanButton(x + halfW + 2, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.small"),
                v -> setFlag(ArmorStandEditorUtils.KEY_SMALL, v),
                () -> currentState.getBooleanOr(ArmorStandEditorUtils.KEY_SMALL, false)));
        y += BTN_H + 4;

        addRenderableWidget(new GuiBooleanButton(x, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.show_arms"),
                v -> setFlag(ArmorStandEditorUtils.KEY_SHOW_ARMS, v),
                () -> currentState.getBooleanOr(ArmorStandEditorUtils.KEY_SHOW_ARMS, false)));
        addRenderableWidget(new GuiBooleanButton(x + halfW + 2, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.no_base_plate"),
                v -> setFlag(ArmorStandEditorUtils.KEY_NO_BASE_PLATE, v),
                () -> currentState.getBooleanOr(ArmorStandEditorUtils.KEY_NO_BASE_PLATE, false)));
        y += BTN_H + 4;

        addRenderableWidget(new GuiBooleanButton(x, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.marker"),
                v -> setFlag(ArmorStandEditorUtils.KEY_MARKER, v),
                () -> currentState.getBooleanOr(ArmorStandEditorUtils.KEY_MARKER, false)));
        addRenderableWidget(new GuiBooleanButton(x + halfW + 2, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.invulnerable"),
                v -> setFlag(ArmorStandEditorUtils.KEY_INVULNERABLE, v),
                () -> currentState.getBooleanOr(ArmorStandEditorUtils.KEY_INVULNERABLE, false)));
        y += BTN_H + 8;

        y += font.lineHeight + 2;

        nameField = new EditBox(font, x, y, w, BTN_H, Component.translatable("gui.ee.armorstand.name"));
        nameField.setHint(Component.translatable("gui.ee.armorstand.name.hint").withStyle(ChatFormatting.DARK_GRAY));
        nameField.setMaxLength(256);
        nameField.setValue(currentState.getStringOr(ArmorStandEditorUtils.KEY_NAME, "").replace(ChatUtils.MODIFIER, '&'));
        nameField.setResponder(v -> {
            currentState.putString(ArmorStandEditorUtils.KEY_NAME, v == null ? "" : v);
            if (minecraft != null && minecraft.level != null) {
                ArmorStandEditorUtils.applyState(preview, currentState, minecraft.level.registryAccess());
            }
        });
        addRenderableWidget(nameField);
        y += BTN_H + 4;

        addRenderableWidget(new GuiBooleanButton(x, y, w, BTN_H, Component.translatable("gui.ee.armorstand.name_visible"),
                v -> setFlag(ArmorStandEditorUtils.KEY_NAME_VISIBLE, v),
                () -> currentState.getBooleanOr(ArmorStandEditorUtils.KEY_NAME_VISIBLE, false)));
        y += BTN_H + 4;

        return y;
    }

    private int buildLocksTab(int x, int y, int w) {
        int box = Checkbox.getBoxSize(font);
        int colW = Math.max(18, box) + 6;
        int gridX = x + 74;

        lockHeaderY = y;
        EquipmentSlot[] slots = lockSlots();
        for (int i = 0; i < slots.length; i++) {
            lockHeaderX[i] = gridX + i * colW;
        }

        int rowY = y + 20;
        addLockRow(gridX, rowY, slots, 0);
        rowY += 20;
        addLockRow(gridX, rowY, slots, 8);
        rowY += 20;
        addLockRow(gridX, rowY, slots, 16);
        rowY += 22;

        return rowY + font.lineHeight + 4;
    }

    private void addLockRow(int gridX, int y, EquipmentSlot[] slots, int offset) {
        int box = Checkbox.getBoxSize(font);
        int colW = Math.max(18, box) + 6;

        int current = currentState.getIntOr(ArmorStandEditorUtils.KEY_DISABLED_SLOTS, 0);
        for (int i = 0; i < slots.length; i++) {
            EquipmentSlot slot = slots[i];
            int mask = 1 << slot.getFilterBit(offset);
            boolean selected = (current & mask) != 0;

            boolean lockAll = (current & (1 << slot.getFilterBit(0))) != 0;
            boolean enabled = offset == 0 || !lockAll;

            Checkbox cb = Checkbox.builder(Component.empty(), font)
                    .pos(gridX + i * colW, y)
                    .selected(selected)
                    .onValueChange((c, value) -> {
                        int v2 = currentState.getIntOr(ArmorStandEditorUtils.KEY_DISABLED_SLOTS, 0);
                        int m2 = 1 << slot.getFilterBit(offset);
                        int next = value ? (v2 | m2) : (v2 & ~m2);
                        currentState.putInt(ArmorStandEditorUtils.KEY_DISABLED_SLOTS, next);
                        if (minecraft != null && minecraft.level != null) {
                            ArmorStandEditorUtils.applyState(preview, currentState, minecraft.level.registryAccess());
                        }
                        rebuildUi();
                    })
                    .build();
            cb.active = enabled;
            addRenderableWidget(cb);
        }
    }

    private static EquipmentSlot[] lockSlots() {
        return new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };
    }

    private static ItemStack[] lockIcons() {
        return new ItemStack[]{
                new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.SHIELD),
                new ItemStack(Items.LEATHER_HELMET),
                new ItemStack(Items.LEATHER_CHESTPLATE),
                new ItemStack(Items.LEATHER_LEGGINGS),
                new ItemStack(Items.LEATHER_BOOTS)
        };
    }

    private float[] readPose(CompoundTag pose, String key) {
        if (pose == null) {
            return new float[]{0, 0, 0};
        }
        ListTag list = pose.getList(key).orElse(null);
        if (list == null || list.size() < 3) {
            return new float[]{0, 0, 0};
        }
        return new float[]{list.getFloatOr(0, 0.0f), list.getFloatOr(1, 0.0f), list.getFloatOr(2, 0.0f)};
    }

    private void setPoseAxis(String poseKey, int axis, float degrees) {
        CompoundTag pose = currentState.getCompoundOrEmpty(ArmorStandEditorUtils.KEY_POSE);
        ListTag list = pose.getList(poseKey).orElse(new ListTag());
        while (list.size() < 3) {
            list.add(FloatTag.valueOf(0.0f));
        }
        list.set(axis, FloatTag.valueOf(degrees));
        pose.put(poseKey, list);
        currentState.put(ArmorStandEditorUtils.KEY_POSE, pose);

        if (minecraft != null && minecraft.level != null) {
            ArmorStandEditorUtils.applyState(preview, currentState, minecraft.level.registryAccess());
        }
    }

    private void resetPosePart(PosePart part) {
        if (minecraft == null || minecraft.level == null) return;
        Entity target = minecraft.level.getEntity(targetEntityId);
        if (!(target instanceof ArmorStand armorStand)) return;

        HolderLookup.Provider registryAccess = minecraft.level.registryAccess();
        CompoundTag fresh = ArmorStandEditorUtils.writeState(armorStand, registryAccess);
        CompoundTag pose = currentState.getCompoundOrEmpty(ArmorStandEditorUtils.KEY_POSE);
        CompoundTag freshPose = fresh.getCompoundOrEmpty(ArmorStandEditorUtils.KEY_POSE);
        freshPose.getList(part.nbtKey).ifPresent(list -> pose.put(part.nbtKey, list.copy()));
        currentState.put(ArmorStandEditorUtils.KEY_POSE, pose);
        ArmorStandEditorUtils.applyState(preview, currentState, registryAccess);
    }

    private void setFlag(String key, boolean value) {
        if (key.equals(ArmorStandEditorUtils.KEY_DISABLED_SLOTS)) {
            return;
        }
        if (key.equals(ArmorStandEditorUtils.KEY_NAME)) {
            return;
        }
        currentState.putBoolean(key, value);
        if (minecraft != null && minecraft.level != null) {
            ArmorStandEditorUtils.applyState(preview, currentState, minecraft.level.registryAccess());
        }
    }

    private ItemStack readEquip(String key) {
        if (minecraft == null || minecraft.level == null) return ItemStack.EMPTY;
        CompoundTag equip = currentState.getCompoundOrEmpty(ArmorStandEditorUtils.KEY_EQUIPMENT);
        return equip.getCompound(key)
                .map(ct -> com.dutchmtc.ee.utils.ItemUtils.parseStack(minecraft.level.registryAccess(), ct))
                .orElse(ItemStack.EMPTY);
    }

    private void writeEquip(String key, ItemStack stack) {
        if (minecraft == null || minecraft.level == null) return;
        CompoundTag equip = currentState.getCompoundOrEmpty(ArmorStandEditorUtils.KEY_EQUIPMENT);
        if (stack == null || stack.isEmpty()) {
            equip.remove(key);
        } else {
            Tag saved = com.dutchmtc.ee.utils.ItemUtils.saveStack(stack, minecraft.level.registryAccess());
            if (saved instanceof CompoundTag ct) {
                equip.put(key, ct);
            }
        }
        currentState.put(ArmorStandEditorUtils.KEY_EQUIPMENT, equip);
        ArmorStandEditorUtils.applyState(preview, currentState, minecraft.level.registryAccess());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);

        // Panel background
        GuiUtils.drawBox(graphics, panelLeft, panelTop, PANEL_W, PANEL_H, 0);

        // Preview frame
        GuiUtils.drawRect(graphics, previewLeft - 1, previewTop - 1, previewLeft + PREVIEW_W + 1, previewTop + PREVIEW_H + 1, 0xFF202020);
        GuiUtils.drawRect(graphics, previewLeft, previewTop, previewLeft + PREVIEW_W, previewTop + PREVIEW_H, 0xFF0F0F0F);

        if (preview != null) {
            GuiEntityPreviewUtils.renderLivingEntityInBox(
                    graphics,
                    previewLeft,
                    previewTop,
                    PREVIEW_W,
                    PREVIEW_H,
                    60,
                    0.0f,
                    previewYaw,
                    previewPitch,
                    preview
            );
        }

        if (tab == Tab.LOCKS) {
            renderLockHeaderIcons(graphics);
        }

        renderTabText(graphics);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderTabText(GuiGraphics graphics) {
        int controlsLeft = previewLeft + PREVIEW_W + PAD;
        int controlsTop = panelTop;
        int controlsW = panelLeft + PANEL_W - PAD - controlsLeft;
        int x = controlsLeft;
        int y = controlsTop + TAB_H + 8;

        switch (tab) {
            case EQUIPMENT -> {
                final int slot = 18;
                final int rowH = 22;
                final int labelGap = 6;

                int leftColX = x + 18;
                int rightColX = x + (controlsW / 2) + 8;
                int labelYOffset = (slot - font.lineHeight) / 2;

                int y0 = y;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.slot.head").withStyle(ChatFormatting.GRAY),
                        leftColX + slot + labelGap, y0 + labelYOffset, 0xFFFFFFFF);
                y0 += rowH;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.slot.chest").withStyle(ChatFormatting.GRAY),
                        leftColX + slot + labelGap, y0 + labelYOffset, 0xFFFFFFFF);
                y0 += rowH;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.slot.legs").withStyle(ChatFormatting.GRAY),
                        leftColX + slot + labelGap, y0 + labelYOffset, 0xFFFFFFFF);
                y0 += rowH;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.slot.feet").withStyle(ChatFormatting.GRAY),
                        leftColX + slot + labelGap, y0 + labelYOffset, 0xFFFFFFFF);

                int handsY = y + rowH;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.slot.mainhand").withStyle(ChatFormatting.GRAY),
                        rightColX + slot + labelGap, handsY + labelYOffset, 0xFFFFFFFF);
                handsY += rowH;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.slot.offhand").withStyle(ChatFormatting.GRAY),
                        rightColX + slot + labelGap, handsY + labelYOffset, 0xFFFFFFFF);

                int hintY = y + rowH * 4 + 6;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.equipment.hint").withStyle(ChatFormatting.DARK_GRAY),
                        x, hintY, 0xFFFFFFFF);
            }
            case LOCKS -> {
                int rowY = y + 20;
                int rowLabelY = rowY + (BTN_H - font.lineHeight) / 2;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.locks.remove").withStyle(ChatFormatting.GRAY),
                        x, rowLabelY, 0xFFFFFFFF);

                rowY += 20;
                rowLabelY = rowY + (BTN_H - font.lineHeight) / 2;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.locks.replace").withStyle(ChatFormatting.GRAY),
                        x, rowLabelY, 0xFFFFFFFF);

                rowY += 20;
                rowLabelY = rowY + (BTN_H - font.lineHeight) / 2;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.locks.place").withStyle(ChatFormatting.GRAY),
                        x, rowLabelY, 0xFFFFFFFF);

                rowY += 22;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.locks.hint").withStyle(ChatFormatting.DARK_GRAY),
                        x, rowY, 0xFFFFFFFF);
            }
            case OPTIONS -> {
                int nameLabelY = y + 3 * (BTN_H + 4) + BTN_H + 8;
                graphics.drawString(font, Component.translatable("gui.ee.armorstand.name").withStyle(ChatFormatting.GRAY),
                        x, nameLabelY, 0xFFFFFFFF);
            }
            default -> {
            }
        }
    }

    private void renderLockHeaderIcons(GuiGraphics graphics) {
        ItemStack[] icons = lockIcons();
        for (int i = 0; i < icons.length; i++) {
            int x = lockHeaderX[i];
            GuiUtils.drawRect(graphics, x - 1, lockHeaderY - 1, x + 18, lockHeaderY + 18, 0xFF5A5A5A);
            GuiUtils.drawRect(graphics, x, lockHeaderY, x + 17, lockHeaderY + 17, 0xFF202020);
            GuiUtils.drawItemStack(graphics, icons[i], x + 1, lockHeaderY + 1);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (GuiUtils.isHover(previewLeft, previewTop, PREVIEW_W, PREVIEW_H, (int) event.x(), (int) event.y())
                && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingPreview = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingPreview && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            previewYaw = Mth.wrapDegrees(previewYaw - (float) dx * 1.6f);
            previewPitch = Mth.clamp(previewPitch - (float) dy * 0.9f, -35.0f, 35.0f);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingPreview = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onCancel();
            return true;
        }
        return super.keyPressed(event);
    }

    private static final class DegreeSlider extends AbstractSliderButton {
        private final Component label;
        private final float min;
        private final float max;
        private final java.util.function.Consumer<Float> setter;

        private DegreeSlider(int x, int y, int width, int height, Component label, float min, float max, float initial,
                java.util.function.Consumer<Float> setter) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            setValueFromDegrees(initial);
            updateMessage();
        }

        private void setValueFromDegrees(float degrees) {
            float clamped = Mth.clamp(degrees, min, max);
            this.value = (clamped - min) / (max - min);
        }

        private float getDegrees() {
            return (float) (min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            float deg = getDegrees();
            setMessage(label.copy()
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.1f°", deg)).withStyle(ChatFormatting.GOLD)));
        }

        @Override
        protected void applyValue() {
            float deg = getDegrees();
            setter.accept(deg);
            updateMessage();
        }
    }

    private final class ItemSlotButton extends AbstractButton {
        private final java.util.function.Supplier<ItemStack> getter;
        private final java.util.function.Consumer<ItemStack> setter;

        private ItemSlotButton(int x, int y, Component tooltip, java.util.function.Supplier<ItemStack> getter,
                java.util.function.Consumer<ItemStack> setter) {
            super(x, y, 18, 18, tooltip);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void onPress(InputWithModifiers event) {
            // no-op; use mouseClicked for button selection
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!this.active || !this.visible) {
                return false;
            }
            if (!GuiUtils.isHover(getX(), getY(), getWidth(), getHeight(), (int) event.x(), (int) event.y())) {
                return false;
            }
            playDownSound(Objects.requireNonNull(minecraft).getSoundManager());

            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                setter.accept(ItemStack.EMPTY);
                rebuildUi();
                return true;
            }

            ItemStack current = getter.get();
            Objects.requireNonNull(minecraft).setScreen(new GuiItemStackModifier(GuiArmorStandEditor.this, current.copy(), newItem -> {
                ItemStack sanitized = newItem == null ? ItemStack.EMPTY : newItem;
                if (!sanitized.isEmpty() && sanitized.getCount() <= 0) {
                    sanitized.setCount(1);
                }
                setter.accept(sanitized);
                rebuildUi();
            }));
            return true;
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ItemStack stack = getter.get();
            if (stack == null) {
                stack = ItemStack.EMPTY;
            }
            GuiUtils.drawRect(graphics, getX(), getY(), getX() + 18, getY() + 18, 0xFF5A5A5A);
            GuiUtils.drawRect(graphics, getX() + 1, getY() + 1, getX() + 17, getY() + 17, 0xFF202020);
            if (!stack.isEmpty()) {
                GuiUtils.drawItemStack(graphics, stack, getX() + 1, getY() + 1);
            }
            if (isHoveredOrFocused()) {
                GuiUtils.drawRect(graphics, getX(), getY(), getX() + 18, getY() + 18, 0x55FFFFFF);
                if (!stack.isEmpty()) {
                    GuiUtils.renderTooltip(graphics, Objects.requireNonNull(minecraft).font, stack, mouseX, mouseY);
                } else {
                    GuiUtils.renderTooltip(graphics, Objects.requireNonNull(minecraft).font,
                            java.util.List.of(getMessage()), java.util.Optional.empty(), mouseX, mouseY);
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
