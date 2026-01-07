package com.dutchmtc.ee.gui;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.GuiBooleanButton;
import com.dutchmtc.ee.gui.modifier.GuiItemStackModifier;
import com.dutchmtc.ee.utils.ArmorStandEditorUtils;
import com.dutchmtc.ee.utils.ArmorStandItemUtils;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiEntityPreviewUtils;
import com.dutchmtc.ee.utils.GuiUtils;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueOutput;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class GuiArmorStandItemEditor extends Screen {
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
        HEAD("gui.ee.armorstand.part.head"),
        BODY("gui.ee.armorstand.part.body"),
        LEFT_ARM("gui.ee.armorstand.part.left_arm"),
        RIGHT_ARM("gui.ee.armorstand.part.right_arm"),
        LEFT_LEG("gui.ee.armorstand.part.left_leg"),
        RIGHT_LEG("gui.ee.armorstand.part.right_leg");

        final String labelKey;

        PosePart(String labelKey) {
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
    private final Consumer<ItemStack> setter;
    private ItemStack currentItemStack;

    private Tab tab = Tab.POSE;
    private PosePart posePart = PosePart.HEAD;

    private ArmorStand preview;
    private CompoundTag originalTag;
    private CompoundTag currentTag;

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

    public GuiArmorStandItemEditor(Screen parent, ItemStack armorStandStack, Consumer<ItemStack> setter) {
        super(Component.translatable("gui.ee.armorstand.title"));
        this.parent = parent;
        this.setter = setter;
        this.currentItemStack = armorStandStack != null ? armorStandStack : ItemStack.EMPTY;
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
        if (preview == null) {
            preview = new ArmorStand(minecraft.level, 0, 0, 0);
        }

        if (originalTag == null) {
            originalTag = ArmorStandItemUtils.getArmorStandEntityTag(currentItemStack);
        }
        if (currentTag == null) {
            currentTag = originalTag.copy();
        }

        loadTagIntoPreview(currentTag);

        resetPreviewView();
        draggingPreview = false;

        rebuildUi();
    }

    private void resetPreviewView() {
        previewYaw = DEFAULT_PREVIEW_YAW;
        previewPitch = DEFAULT_PREVIEW_PITCH;
    }

    private boolean isModified() {
        return originalTag != null && currentTag != null && !currentTag.equals(originalTag);
    }

    private void onCancel() {
        if (minecraft == null) return;
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

        int bottomY = panelTop + PANEL_H - PAD - BTN_H;
        addRenderableWidget(new EEButton(panelLeft + PAD, bottomY, 90, BTN_H, Component.translatable("gui.ee.armorstand.reset"),
                b -> {
                    currentTag = originalTag.copy();
                    loadTagIntoPreview(currentTag);
                    rebuildUi();
                }));

        addRenderableWidget(new EEButton(panelLeft + PAD + 94, bottomY, 110, BTN_H, Component.translatable("gui.ee.armorstand.reset_view"),
                b -> resetPreviewView()));

        addRenderableWidget(new EEButton(panelLeft + PANEL_W - PAD - 186, bottomY, 90, BTN_H,
                Component.translatable("gui.cancel"), b -> onCancel()));

        addRenderableWidget(new EEButton(panelLeft + PANEL_W - PAD - 92, bottomY, 92, BTN_H,
                Component.translatable("gui.done").withStyle(ChatFormatting.GREEN), b -> {
                    syncTagFromPreview();
                    ArmorStandItemUtils.setArmorStandEntityTag(currentItemStack, ArmorStandItemUtils.sanitizeArmorStandEntityTag(currentTag));
                    setter.accept(currentItemStack);
                    minecraft.setScreen(parent);
                }));
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

        float[] xyz = getPosePart(posePart);
        addRenderableWidget(new DegreeSlider(x, y, w, BTN_H, Component.translatable("gui.ee.armorstand.axis.x"),
                -180.0f, 180.0f, xyz[0], v -> setPoseAxis(posePart, 0, v)));
        y += BTN_H + 4;
        addRenderableWidget(new DegreeSlider(x, y, w, BTN_H, Component.translatable("gui.ee.armorstand.axis.y"),
                -180.0f, 180.0f, xyz[1], v -> setPoseAxis(posePart, 1, v)));
        y += BTN_H + 4;
        addRenderableWidget(new DegreeSlider(x, y, w, BTN_H, Component.translatable("gui.ee.armorstand.axis.z"),
                -180.0f, 180.0f, xyz[2], v -> setPoseAxis(posePart, 2, v)));
        y += BTN_H + 8;

        addRenderableWidget(new EEButton(x, y, w, BTN_H, Component.translatable("gui.ee.armorstand.reset_part"),
                b -> {
                    resetPosePart(posePart);
                    syncTagFromPreview();
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

        // Armor column
        addRenderableWidget(new ItemSlotButton(leftColX, y0, Component.translatable("gui.ee.armorstand.slot.head"),
                () -> preview.getItemBySlot(EquipmentSlot.HEAD),
                is -> preview.setItemSlot(EquipmentSlot.HEAD, is)));
        y0 += rowH;

        addRenderableWidget(new ItemSlotButton(leftColX, y0, Component.translatable("gui.ee.armorstand.slot.chest"),
                () -> preview.getItemBySlot(EquipmentSlot.CHEST),
                is -> preview.setItemSlot(EquipmentSlot.CHEST, is)));
        y0 += rowH;

        addRenderableWidget(new ItemSlotButton(leftColX, y0, Component.translatable("gui.ee.armorstand.slot.legs"),
                () -> preview.getItemBySlot(EquipmentSlot.LEGS),
                is -> preview.setItemSlot(EquipmentSlot.LEGS, is)));
        y0 += rowH;

        addRenderableWidget(new ItemSlotButton(leftColX, y0, Component.translatable("gui.ee.armorstand.slot.feet"),
                () -> preview.getItemBySlot(EquipmentSlot.FEET),
                is -> preview.setItemSlot(EquipmentSlot.FEET, is)));

        // Hand column (aligned to chest/legs rows)
        int handsY = y + rowH;
        addRenderableWidget(new ItemSlotButton(rightColX, handsY, Component.translatable("gui.ee.armorstand.slot.mainhand"),
                () -> preview.getItemBySlot(EquipmentSlot.MAINHAND),
                is -> preview.setItemSlot(EquipmentSlot.MAINHAND, is)));

        handsY += rowH;
        addRenderableWidget(new ItemSlotButton(rightColX, handsY, Component.translatable("gui.ee.armorstand.slot.offhand"),
                () -> preview.getItemBySlot(EquipmentSlot.OFFHAND),
                is -> preview.setItemSlot(EquipmentSlot.OFFHAND, is)));

        int hintY = y + rowH * 4 + 6;
        return hintY + font.lineHeight + 4;
    }

    private int buildOptionsTab(int x, int y, int w) {
        int halfW = (w - 2) / 2;

        addRenderableWidget(new GuiBooleanButton(x, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.invisible"),
                v -> {
                    preview.setInvisible(v);
                    syncTagFromPreview();
                }, () -> preview.isInvisible()));
        addRenderableWidget(new GuiBooleanButton(x + halfW + 2, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.glowing"),
                v -> {
                    preview.setGlowingTag(v);
                    syncTagFromPreview();
                }, () -> preview.hasGlowingTag()));
        y += BTN_H + 4;

        addRenderableWidget(new GuiBooleanButton(x, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.no_gravity"),
                v -> {
                    preview.setNoGravity(v);
                    syncTagFromPreview();
                }, () -> preview.isNoGravity()));
        addRenderableWidget(new GuiBooleanButton(x + halfW + 2, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.small"),
                v -> {
                    ArmorStandEditorUtils.setSmall(preview, v);
                    syncTagFromPreview();
                }, () -> preview.isSmall()));
        y += BTN_H + 4;

        addRenderableWidget(new GuiBooleanButton(x, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.show_arms"),
                v -> {
                    preview.setShowArms(v);
                    syncTagFromPreview();
                }, () -> preview.showArms()));
        addRenderableWidget(new GuiBooleanButton(x + halfW + 2, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.no_base_plate"),
                v -> {
                    preview.setNoBasePlate(v);
                    syncTagFromPreview();
                }, () -> !preview.showBasePlate()));
        y += BTN_H + 4;

        addRenderableWidget(new GuiBooleanButton(x, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.marker"),
                v -> {
                    ArmorStandEditorUtils.setMarker(preview, v);
                    syncTagFromPreview();
                }, () -> preview.isMarker()));
        addRenderableWidget(new GuiBooleanButton(x + halfW + 2, y, halfW, BTN_H, Component.translatable("gui.ee.armorstand.flag.invulnerable"),
                v -> {
                    preview.setInvulnerable(v);
                    syncTagFromPreview();
                }, () -> preview.isInvulnerable()));
        y += BTN_H + 8;

        y += font.lineHeight + 2;

        nameField = new EditBox(font, x, y, w, BTN_H, Component.translatable("gui.ee.armorstand.name"));
        nameField.setHint(Component.translatable("gui.ee.armorstand.name.hint").withStyle(ChatFormatting.DARK_GRAY));
        nameField.setMaxLength(256);
        nameField.setValue(Optional.ofNullable(preview.getCustomName()).map(c -> c.getString()).orElse("").replace(ChatUtils.MODIFIER, '&'));
        nameField.setResponder(v -> {
            if (v == null || v.isBlank()) {
                preview.setCustomName(null);
            } else {
                preview.setCustomName(Component.literal(v.replace('&', ChatUtils.MODIFIER)));
            }
            syncTagFromPreview();
        });
        addRenderableWidget(nameField);
        y += BTN_H + 4;

        addRenderableWidget(new GuiBooleanButton(x, y, w, BTN_H, Component.translatable("gui.ee.armorstand.name_visible"),
                v -> {
                    preview.setCustomNameVisible(v);
                    syncTagFromPreview();
        }, () -> preview.isCustomNameVisible()));
        y += BTN_H + 8;

        return y + 4;
    }

    private int buildLocksTab(int x, int y, int w) {
        int box = Checkbox.getBoxSize(font);
        int colW = Math.max(18, box) + 6;
        int gridX = x + 74;

        // Header icons (rendered in render())
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

        int current = ArmorStandEditorUtils.getDisabledSlots(preview);
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
                        int v2 = ArmorStandEditorUtils.getDisabledSlots(preview);
                        int m2 = 1 << slot.getFilterBit(offset);
                        int next = value ? (v2 | m2) : (v2 & ~m2);
                        ArmorStandEditorUtils.setDisabledSlots(preview, next);
                        syncTagFromPreview();
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

    private void loadTagIntoPreview(CompoundTag tag) {
        if (minecraft == null || minecraft.level == null) return;
        preview = new ArmorStand(minecraft.level, 0, 0, 0);
        CompoundTag safe = ArmorStandItemUtils.sanitizeArmorStandEntityTag(tag);
        TypedEntityData.of(net.minecraft.world.entity.EntityType.ARMOR_STAND, safe).loadInto(preview);
    }

    private void syncTagFromPreview() {
        if (minecraft == null || minecraft.level == null) return;
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, minecraft.level.registryAccess());
        preview.saveWithoutId(out);
        currentTag = ArmorStandItemUtils.sanitizeArmorStandEntityTag(out.buildResult());
    }

    private float[] getPosePart(PosePart part) {
        var r = switch (part) {
            case HEAD -> preview.getHeadPose();
            case BODY -> preview.getBodyPose();
            case LEFT_ARM -> preview.getLeftArmPose();
            case RIGHT_ARM -> preview.getRightArmPose();
            case LEFT_LEG -> preview.getLeftLegPose();
            case RIGHT_LEG -> preview.getRightLegPose();
        };
        return new float[]{r.x(), r.y(), r.z()};
    }

    private void setPoseAxis(PosePart part, int axis, float degrees) {
        float[] xyz = getPosePart(part);
        xyz[axis] = degrees;
        var rot = new net.minecraft.core.Rotations(xyz[0], xyz[1], xyz[2]);
        switch (part) {
            case HEAD -> preview.setHeadPose(rot);
            case BODY -> preview.setBodyPose(rot);
            case LEFT_ARM -> preview.setLeftArmPose(rot);
            case RIGHT_ARM -> preview.setRightArmPose(rot);
            case LEFT_LEG -> preview.setLeftLegPose(rot);
            case RIGHT_LEG -> preview.setRightLegPose(rot);
        }
        syncTagFromPreview();
    }

    private void resetPosePart(PosePart part) {
        switch (part) {
            case HEAD -> preview.setHeadPose(ArmorStand.DEFAULT_HEAD_POSE);
            case BODY -> preview.setBodyPose(ArmorStand.DEFAULT_BODY_POSE);
            case LEFT_ARM -> preview.setLeftArmPose(ArmorStand.DEFAULT_LEFT_ARM_POSE);
            case RIGHT_ARM -> preview.setRightArmPose(ArmorStand.DEFAULT_RIGHT_ARM_POSE);
            case LEFT_LEG -> preview.setLeftLegPose(ArmorStand.DEFAULT_LEFT_LEG_POSE);
            case RIGHT_LEG -> preview.setRightLegPose(ArmorStand.DEFAULT_RIGHT_LEG_POSE);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);

        GuiUtils.drawBox(graphics, panelLeft, panelTop, PANEL_W, PANEL_H, 0);

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
            // visible backdrop
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
        private final Consumer<Float> setter;

        private DegreeSlider(int x, int y, int width, int height, Component label, float min, float max, float initial,
                Consumer<Float> setter) {
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
        private final Consumer<ItemStack> setter;

        private ItemSlotButton(int x, int y, Component tooltip, java.util.function.Supplier<ItemStack> getter,
                Consumer<ItemStack> setter) {
            super(x, y, 18, 18, tooltip);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void onPress(InputWithModifiers event) {
            // no-op
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!this.active || !this.visible) return false;
            if (!GuiUtils.isHover(getX(), getY(), getWidth(), getHeight(), (int) event.x(), (int) event.y())) return false;

            playDownSound(Objects.requireNonNull(minecraft).getSoundManager());

            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                setter.accept(ItemStack.EMPTY);
                syncTagFromPreview();
                rebuildUi();
                return true;
            }

            ItemStack current = getter.get();
            Objects.requireNonNull(minecraft).setScreen(new GuiItemStackModifier(GuiArmorStandItemEditor.this, current.copy(), newItem -> {
                ItemStack sanitized = newItem == null ? ItemStack.EMPTY : newItem;
                if (!sanitized.isEmpty() && sanitized.getCount() <= 0) {
                    sanitized.setCount(1);
                }
                setter.accept(sanitized);
                syncTagFromPreview();
                rebuildUi();
            }));
            return true;
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ItemStack stack = getter.get();
            if (stack == null) stack = ItemStack.EMPTY;

            // Slot background (visible even when empty)
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
                            List.of(getMessage()), Optional.empty(), mouseX, mouseY);
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
