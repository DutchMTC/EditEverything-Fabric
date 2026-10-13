package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EETheme;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.StyledTextField;
import com.dutchmtc.ee.gui.GuiArmorStandItemEditor;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTModifier;
import com.dutchmtc.ee.gui.modifier.mob.GuiMobFieldsEditor;
import com.dutchmtc.ee.gui.modifier.mob.GuiVillagerTradesEditor;
import com.dutchmtc.ee.gui.modifier.mob.MobPropertyEditorState;
import com.dutchmtc.ee.mobdata.MobDataRepository;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.GuiEntityPreviewUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class GuiSpawnEggModifier extends GuiModifier<ItemStack> {
    private ItemStack currentItemStack;
    private final ItemStack originalItemStack;
    private final boolean stackMember;

    private static final float DEFAULT_EQUIPMENT_DROP_CHANCE = 0.085F;
    private static final String[] EQUIPMENT_DROP_CHANCE_KEYS = {
            "mainhand",
            "offhand",
            "head",
            "chest",
            "legs",
            "feet",
            "body",
            "saddle"
    };
    

    private static final int TEXT = 0xFF303030, MUTED = 0xFF555555;
    private enum Page {
        GENERAL("General"), BEHAVIOR("Behavior"), EQUIPMENT("Equipment"), ADVANCED("Advanced");
        final String label;
        Page(String label) { this.label = label; }
    }
    private Page page = Page.GENERAL;
    private EntityEditorLayout layout;
    private int scroll, contentHeight;
    private boolean draggingScroll;
    private StyledTextField nameBox;
    private TextSelectionToolbar nameToolbar;
    private StyledTextDocument nameDocument;
    private Component nameSnapshot;
    private EEButton saveButton;
    private Entity previewEntity;
    private net.minecraft.world.level.Level previewLevel;
    private boolean previewDirty = true, draggingPreview;
    private float previewYaw = -25, previewPitch = -8, previewZoom = 1;
    private String previewMessage = "";
    private final List<PlacedWidget> contentWidgets = new ArrayList<>();
    private final List<Consumer<GuiGraphicsExtractor>> decorations = new ArrayList<>();
    private record PlacedWidget(AbstractWidget widget, int y) {}

    public GuiSpawnEggModifier(Screen parent, Consumer<ItemStack> setter, ItemStack currentItemStack) {
        this(parent, setter, currentItemStack, false);
    }

    GuiSpawnEggModifier(Screen parent, Consumer<ItemStack> setter, ItemStack currentItemStack, boolean stackMember) {
        super(parent, Component.literal("Entity editor"), setter);
        this.stackMember = stackMember;
        this.originalItemStack = currentItemStack.copy();
        this.currentItemStack = currentItemStack.copy();
    }

    @Override
    public boolean isModified() { return !ItemStack.matches(currentItemStack, originalItemStack); }

    @Override
    public void init() {
        clearFocus();
        clearWidgets();
        contentWidgets.clear();
        decorations.clear();
        nameBox = null;
        nameToolbar = null;
        super.init();
        layout = EntityEditorLayout.of(width, height);
        contentHeight = 0;
        for (Page entry : Page.values()) {
            EEButton button = new EEButton(layout.navigationX(entry.ordinal()), layout.navigationY(),
                    layout.navigationWidth(), 20, Component.literal(entry.label), b -> {
                page = entry;
                scroll = 0;
                init();
            });
            button.active = entry != page;
            addRenderableWidget(button);
        }
        switch (page) {
            case GENERAL -> initGeneral();
            case BEHAVIOR -> initBehavior();
            case EQUIPMENT -> initEquipment();
            case ADVANCED -> initAdvanced();
        }
        addRenderableWidget(new EEButton(layout.right() - 178, layout.bottom() - 27, 72, 20,
                Component.literal("Cancel"), b -> onCancel()));
        EEButton save = saveButton = addRenderableWidget(new EEButton(layout.right() - 100, layout.bottom() - 27, 90, 20,
                Component.literal(stackMember ? "Save mob" : "Save entity"), b -> save()));
        save.setTooltip(Tooltip.create(Component.literal(stackMember ? "Save changes to this mob in the stack draft. Ctrl+S" : "Apply all changes to this spawn egg. Ctrl+S")));
        EEButton resetView = addRenderableWidget(new EEButton(layout.previewX(), layout.viewBottom() - 18,
                layout.previewWidth(), 18, Component.literal("Reset view"), b -> {
            previewYaw = -25; previewPitch = -8; previewZoom = 1;
        }));
        resetView.setTooltip(Tooltip.create(Component.literal("Drag the model to rotate it. Scroll over it to zoom.\nReset the preview camera.")));
        draggingPreview = false;
        draggingScroll = false;
        layoutContent();
    }

    private void initGeneral() {
        label("Entity type", 0);
        EEButton type = actionButton(entityName() + "  >", 11, this::chooseEntityType);
        type.setTooltip(Tooltip.create(Component.literal("Choose what this egg spawns.")));
        label("Custom name (optional)", 38);
        addNameInput(49);
        toggle("Show name", "CustomNameVisible", "Always display the custom name, even when you are not looking at the entity.",
                76, layout.contentX(), usableWidth(),
                () -> ItemUtils.getBoolean(getEntityTag(), "CustomNameVisible")
                        || ItemUtils.getBoolean(getEntityTag(), "custom_name_visible"),
                value -> updateEntityTag(tag -> {
                    ItemUtils.putBoolean(tag, "CustomNameVisible", value);
                    ItemUtils.remove(tag, "custom_name_visible");
                }));
        int y = action(entityName() + " settings...", mobSettingsHint(), 108, this::openMobSettings);
        if (getCurrentEntityType(currentItemStack) == EntityTypes.ARMOR_STAND)
            y = action("Armor stand editor...", "Edit pose, equipment, locks and appearance.", y, this::openArmorStandEditor);
        if (supportsTrades())
            y = action("Trades... (" + getTradeRecipes(getEntityTag()).size() + ")",
                    "Choose what players pay and receive.", y, this::openTrades);
        if (!stackMember) action("Mob stack...", "Add mobs riding on top of this entity, edit each mob and change their order.", y,
                () -> mc.gui.setScreen(new GuiMobStackEditor(this, currentItemStack, edited -> {
                    currentItemStack = edited.copy(); nameDocument = null; previewDirty = true;
                })));
    }

    private void openArmorStandEditor() {
        getMinecraft().gui.setScreen(new GuiArmorStandItemEditor(this, currentItemStack.copy(), edited -> {
            currentItemStack = edited.copy();
            nameDocument = null;
            previewDirty = true;
        }));
    }

    private String mobSettingsHint() {
        String id = getEntityRegistry().getKey(getCurrentEntityType(currentItemStack)).getPath();
        return switch (id) {
            case "villager", "zombie_villager" -> "Profession, level, appearance and more.";
            case "cat", "wolf", "horse", "parrot" -> "Variant, owner and other mob settings.";
            default -> "Settings specific to this entity.";
        };
    }

    private void initBehavior() {
        boolean columns = usableWidth() >= 340;
        int columnWidth = columns ? (usableWidth() - 10) / 2 : usableWidth();
        int y = section("Behavior flags", 0);
        String[][] flags = {
                {"AI enabled", "Let the mob move, think and act.", "NoAI", "true"},
                {"Gravity", "Let the entity fall normally.", "NoGravity", "true"},
                {"Invincible", "Protect the entity from most damage.", "Invulnerable", "false"},
                {"Keep from despawning", "Keep the mob when players move away.", "PersistenceRequired", "false"},
                {"Pick up items", "Allow the mob to collect nearby items.", "CanPickUpLoot", "false"},
                {"Silent", "Mute the entity's sounds.", "Silent", "false"},
                {"Glowing outline", "Show an outline through blocks.", "Glowing", "false"},
                {"Visual fire", "Show flames without setting the entity on fire.", "HasVisualFire", "false"},
                {"Left-handed", "Hold the main-hand item on the left.", "LeftHanded", "false"},
                {"Gliding", "Start in the elytra flight state. Minecraft may update this after spawning.", "FallFlying", "false"},
                {"On ground", "Start marked as touching the ground. Minecraft may update this after spawning.", "OnGround", "false"}
        };
        for (int i = 0; i < flags.length; i++) {
            String[] flag = flags[i];
            int col = columns ? i % 2 : 0, row = columns ? i / 2 : i;
            flag(flag[0], flag[1], flag[2], Boolean.parseBoolean(flag[3]), y + row * 28,
                    layout.contentX() + col * (columnWidth + 10), columnWidth);
        }
    }

    private void initEquipment() {
        int y = section("Equipment & attributes", 0);
        y = action("Equipment...", "Held items, armor, saddle and drop chances.", y, this::openEquipment);
        y = action("Attributes...", "Base stats such as health, speed and damage.", y, this::openAttributes);
        action("Effects...", "Potion effects, strength and duration.", y, this::openEffects);
    }

    private void initAdvanced() {
        label("Spawn egg appearance", 0);
        ItemStack appearance = new ItemStack(currentItemStack.getItem());
        EEButton egg = actionButton(appearance.getItem().getName(appearance).getString() + "  >", 11, this::chooseEggAppearance);
        egg.setTooltip(Tooltip.create(Component.literal("Change the egg item without changing what it spawns.")));
        int y = action("Entity NBT...", "Edit the entity's raw data.", 39,
                () -> getMinecraft().gui.setScreen(new GuiNBTModifier(this, this::setEntityTag, getEntityTag())));
        if (supportsTrades()) {
            y = action("Trade NBT...", "Edit the raw Offers data.", y, () -> {
                CompoundTag offers = getEntityTag().getCompound("Offers").orElse(new CompoundTag());
                getMinecraft().gui.setScreen(new GuiNBTModifier(Component.literal("Offers"), this, this::setOffersTag, offers));
            });
        }
    }

    private String entityName() { return getCurrentEntityType(currentItemStack).getDescription().getString(); }

    private void chooseEntityType() {
        List<Tuple<String, EntityType<?>>> entities = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.stream().filter(EntityType::canSummon)
                .sorted(Comparator.comparing(t -> t.getDescription().getString()))
                .forEach(type -> entities.add(new Tuple<>(type.getDescription().getString(), type)));
        getMinecraft().gui.setScreen(new GuiButtonListSelector<>(this, Component.literal("Choose entity type"),
                entities, type -> { changeEntityType(type); return null; })
                .withCurrent(getCurrentEntityType(currentItemStack)));
    }

    private void changeEntityType(EntityType<?> type) {
        previewDirty = true;
        CompoundTag tag = getEntityTag();
        SpawnEggItem.byId(type).ifPresent(egg -> currentItemStack = currentItemStack.transmuteCopy(egg.value()));
        ItemUtils.setComponent(currentItemStack, DataComponents.ENTITY_DATA, TypedEntityData.of(type, tag));
    }

    private void chooseEggAppearance() {
        List<Tuple<String, SpawnEggItem>> eggs = new ArrayList<>();
        BuiltInRegistries.ITEM.stream().filter(item -> item instanceof SpawnEggItem)
                .map(item -> (SpawnEggItem) item)
                .sorted(Comparator.comparing(egg -> egg.getName(new ItemStack(egg)).getString()))
                .forEach(egg -> eggs.add(new Tuple<>(egg.getName(new ItemStack(egg)).getString(), egg)));
        getMinecraft().gui.setScreen(new GuiButtonListSelector<>(this, Component.literal("Choose egg appearance"),
                eggs, egg -> { changeEggAppearance(egg); return null; })
                .withCurrent(currentItemStack.getItem() instanceof SpawnEggItem egg ? egg : null));
    }

    private void changeEggAppearance(SpawnEggItem egg) {
        EntityType<?> type = getCurrentEntityType(currentItemStack);
        CompoundTag tag = getEntityTag();
        currentItemStack = currentItemStack.transmuteCopy(egg);
        ItemUtils.setComponent(currentItemStack, DataComponents.ENTITY_DATA, TypedEntityData.of(type, tag));
    }

    private int action(String title, String description, int y, Runnable callback) {
        EEButton button = actionButton(title, y, callback);
        button.setTooltip(Tooltip.create(Component.literal(description)));
        return y + 26;
    }

    private EEButton actionButton(String title, int y, Runnable callback) {
        EEButton button = new EEButton(layout.contentX(), 0, usableWidth(), 20,
                Component.literal(title), b -> callback.run());
        place(button, y);
        return button;
    }

    private int flag(String label, String help, String key, boolean inverted, int y, int x, int width) {
        return toggle(label, key, help, y, x, width,
                () -> ItemUtils.getBoolean(getEntityTag(), key) != inverted,
                value -> updateEntityTag(tag -> ItemUtils.putBoolean(tag, key, value != inverted)));
    }

    private int toggle(String title, String minecraftName, String description, int y, int x, int width,
                       java.util.function.BooleanSupplier getter, Consumer<Boolean> setter) {
        // Keep the exact Minecraft name visible; explanations are available over the entire row.
        int labelWidth = width - 44;
        decorations.add(g -> {
            g.text(font, fit(title, labelWidth), x, layout.viewTop() + y + 1 - scroll, TEXT, false);
            g.text(font, fit(minecraftName, labelWidth), x, layout.viewTop() + y + 12 - scroll, MUTED, false);
        });
        EEButton button = new EEButton(x, 0, width, 24,
                toggleLabel(getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            b.setMessage(toggleLabel(getter.getAsBoolean()));
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                // The full row is clickable, with a compact state button at its right edge.
                int stateX = getX() + getWidth() - 38;
                boolean on = getter.getAsBoolean();
                int fill = EETheme.controlColor(on, isHoveredOrFocused(), active);
                bevel(g, stateX, getY() + 2, 38, 20, fill);
                String state = on ? "On" : "Off";
                g.text(font, state, stateX + (38 - font.width(state)) / 2, getY() + 8,
                        on ? 0xFFFFFFFF : TEXT, false);
            }
            @Override
            protected MutableComponent createNarrationMessage() {
                return Component.literal(title + ", Minecraft tag " + minecraftName + ": "
                        + (getter.getAsBoolean() ? "On" : "Off"));
            }
        };
        button.setTooltip(Tooltip.create(Component.literal(description)));
        place(button, y);
        return y + 28;
    }

    private Component toggleLabel(boolean on) {
        return Component.literal(on ? "On" : "Off").withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.WHITE);
    }

    private int section(String value, int y) {
        label(value, y);
        decorations.add(g -> rect(g, layout.contentX(), layout.viewTop() + y + 12 - scroll,
                usableWidth(), 1, 0xFF999999));
        contentHeight = Math.max(contentHeight, y + 18);
        return y + 18;
    }

    private void label(String value, int y) {
        decorations.add(g -> g.text(font, fit(value, usableWidth()), layout.contentX(),
                layout.viewTop() + y - scroll, TEXT, false));
        contentHeight = Math.max(contentHeight, y + 9);
    }

    private int help(String value, int y) {
        var lines = font.split(Component.literal(value), usableWidth());
        decorations.add(g -> {
            for (int i = 0; i < lines.size(); i++)
                g.text(font, lines.get(i), layout.contentX(), layout.viewTop() + y + i * 11 - scroll, MUTED, false);
        });
        int bottom = y + lines.size() * 11;
        contentHeight = Math.max(contentHeight, bottom);
        return bottom;
    }

    private int usableWidth() { return layout.contentWidth() - 9; }

    private void place(AbstractWidget widget, int y) {
        addWidget(widget);
        contentWidgets.add(new PlacedWidget(widget, y));
        contentHeight = Math.max(contentHeight, y + widget.getHeight() + 2);
    }

    private int maxScroll() { return Math.max(0, contentHeight - (layout.viewBottom() - layout.viewTop())); }

    private void layoutContent() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        for (PlacedWidget placed : contentWidgets) {
            AbstractWidget widget = placed.widget();
            widget.setY(layout.viewTop() + placed.y() - scroll);
            widget.visible = widget.getY() < layout.viewBottom() && widget.getBottom() > layout.viewTop();
            if (!widget.visible && widget.isFocused()) clearFocus();
        }
    }

    private void save() {
        if (nameDocument != null && nameDocument.text().length() > 256) return;
        set(currentItemStack.copy());
        getMinecraft().gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        saveButton.active = nameDocument == null || nameDocument.text().length() <= 256;
        saveButton.setTooltip(Tooltip.create(Component.literal(saveButton.active
                ? "Apply all changes to this spawn egg. Ctrl+S" : "Custom name exceeds the 256 character limit.")));
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        bevel(g, layout.left(), layout.top(), layout.right() - layout.left(), layout.bottom() - layout.top(), 0xFFC6C6C6);
        int x = layout.left(), y = layout.top();
        g.item(currentItemStack, x + 10, y + 8);
        g.text(font, "Entity editor", x + 32, y + 12, TEXT, false);
        String summary = fit(entityName(), layout.right() - x - 128);
        g.text(font, summary, layout.right() - 10 - font.width(summary), y + 12, MUTED, false);
        renderPreview(g);
        rect(g, layout.contentX() - 6, layout.viewTop(), 1, layout.viewBottom() - layout.viewTop(), 0xFF999999);
        g.enableScissor(layout.contentX(), layout.viewTop(), layout.right() - 10, layout.viewBottom());
        for (var decoration : decorations) decoration.accept(g);
        boolean overFormatting = nameToolbar != null && nameToolbar.isMouseOver(mx, my);
        boolean inside = insideContent(mx, my) && !overFormatting;
        for (PlacedWidget placed : contentWidgets) {
            boolean showTooltip = inside && !(placed.widget() == nameBox && nameToolbar.hasSelection());
            placed.widget().extractRenderState(g, showTooltip ? mx : -1, showTooltip ? my : -1, delta);
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int h = layout.viewBottom() - layout.viewTop();
            int thumb = Math.max(16, h * h / contentHeight);
            rect(g, layout.right() - 15, layout.viewTop(), 4, h, 0xFF999999);
            bevel(g, layout.right() - 15, layout.viewTop() + (h - thumb) * scroll / maxScroll(), 4, thumb, 0xFFDDDDDD);
        }
        rect(g, x + 10, layout.bottom() - 44, layout.right() - x - 20, 1, 0xFF999999);
        g.text(font, isModified() ? "Unsaved changes" : "Changes apply when you save.",
                x + 10, layout.bottom() - 38, MUTED, false);
        if (maxScroll() > 0)
            g.text(font, scroll < maxScroll() ? "Scroll for more" : "Scroll up",
                    x + 10, layout.bottom() - 21, MUTED, false);
        super.extractRenderState(g, overFormatting ? -1 : mx, overFormatting ? -1 : my, delta);
        if (nameToolbar != null) nameToolbar.render(g, mx, my);
    }

    private void refreshPreview() {
        if (!previewDirty && previewLevel == mc.level) return;
        previewDirty = false;
        previewLevel = mc.level;
        previewEntity = null;
        previewMessage = mc.level == null ? "Open a world to preview" : "Preview unavailable";
        if (mc.level == null) return;
        try {
            EntityType<?> type = getCurrentEntityType(currentItemStack);
            // Difficulty and spawning rules must not hide a GUI-only model (e.g. a creeper in Peaceful).
            Entity entity = type.create(mc.level, new EntitySpawnRequest(EntitySpawnReason.LOAD, true));
            if (entity == null) return;
            CompoundTag visualTag = getEntityTag();
            if (!visualTag.getList("Passengers").orElseGet(ListTag::new).isEmpty()) {
                visualTag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
                previewEntity = EntityType.loadEntityRecursive(visualTag, mc.level,
                        new EntitySpawnRequest(EntitySpawnReason.LOAD, true), GuiEntityPreviewUtils::prepareStackPreview);
                if (previewEntity != null) {
                    GuiEntityPreviewUtils.restoreDisplayText(previewEntity, visualTag);
                    GuiEntityPreviewUtils.positionStackPreview(previewEntity);
                }
                return;
            }
            // Only this detached copy is loaded. The preview is never added to the world or saved back.
            for (String key : List.of("UUID", "Pos", "Motion", "Rotation", "Passengers", "Dimension"))
                visualTag.remove(key);
            GuiEntityPreviewUtils.loadPreviewData(entity, visualTag);
            entity.setPos(0, 0, 0);
            entity.setCustomNameVisible(false);
            previewEntity = entity;
            previewMessage = "";
        } catch (Exception ex) {
            // Unsupported modded entities or malformed raw data must not prevent editing and saving.
            com.dutchmtc.ee.EEMod.LOGGER.debug("Cannot create entity editor preview", ex);
            previewEntity = null;
        }
    }

    private void renderPreview(GuiGraphicsExtractor g) {
        refreshPreview();
        int x = layout.previewX(), y = layout.previewY(), w = layout.previewWidth(), h = layout.previewHeight();
        String name = fit(entityName(), w);
        g.text(font, name, x + (w - font.width(name)) / 2, layout.viewTop() + 1, TEXT, false);
        GuiUtils.drawGradientRect(g, x, y, x + w, y + h, 0xFF3B4544, 0xFF252C2B);
        rect(g, x, y, w, 1, 0xFF555555);
        rect(g, x, y, 1, h, 0xFF555555);
        rect(g, x, y + h - 1, w, 1, 0xFFFFFFFF);
        rect(g, x + w - 1, y, 1, h, 0xFFFFFFFF);
        if (previewEntity != null) {
            if (previewEntity instanceof net.minecraft.world.entity.Display display && previewEntity.getPassengers().isEmpty()) {
                GuiEntityPreviewUtils.renderDisplayInBox(g, x + 2, y + 2, w - 4, h - 4,
                        previewZoom, previewYaw, previewPitch, display);
                return;
            }
            if (!previewEntity.getPassengers().isEmpty()) {
                try {
                    GuiEntityPreviewUtils.renderStackInBox(g, x + 2, y + 2, w - 4, h - 4,
                            previewZoom, previewYaw, previewPitch, previewEntity);
                } catch (Exception ex) {
                    previewEntity = null;
                    com.dutchmtc.ee.EEMod.LOGGER.debug("Cannot render mob stack preview", ex);
                }
                return;
            }
            float entityW = Math.max(0.3f, previewEntity.getBbWidth());
            float entityH = Math.max(0.3f, previewEntity.getBbHeight());
            if (previewEntity instanceof net.minecraft.world.entity.LivingEntity living) {
                // Match the renderer's normalized attribute scale. Baby models have oversized
                // heads relative to their collision boxes, so reserve their adult-sized bounds.
                float entityScale = Math.max(0.01f, living.getScale() * living.getAgeScale());
                entityW /= entityScale;
                entityH /= entityScale;
            }
            float fitScale = Math.min((w - 14) / (entityW * 1.4f), (h - 14) / (entityH * 1.15f));
            int scale = Math.max(1, Math.round(fitScale * previewZoom));
            try {
                GuiEntityPreviewUtils.renderEntityInBox(g, x + 2, y + 2, w - 4, h - 4,
                        scale, 0, previewYaw, previewPitch, previewEntity);
            } catch (Exception ex) {
                com.dutchmtc.ee.EEMod.LOGGER.debug("Cannot render entity editor preview", ex);
                previewEntity = null;
                previewMessage = "Preview unavailable";
            }
        }
        if (previewEntity == null) {
            var lines = font.split(Component.literal(previewMessage), w - 10);
            for (int i = 0; i < lines.size(); i++)
                g.text(font, lines.get(i), x + 5, y + h / 2 - lines.size() * 5 + i * 10, 0xFFDDDDDD, false);
        }
    }

    private boolean insidePreview(double x, double y) {
        return x >= layout.previewX() && x < layout.previewX() + layout.previewWidth()
                && y >= layout.previewY() && y < layout.previewY() + layout.previewHeight();
    }

    private boolean insideContent(double x, double y) {
        return x >= layout.contentX() && x < layout.right() - 10
                && y >= layout.viewTop() && y < layout.viewBottom();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (nameToolbar != null && nameToolbar.click(event)) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && insidePreview(event.x(), event.y())) {
            draggingPreview = true;
            clearFocus();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && maxScroll() > 0 && event.x() >= layout.right() - 18 && event.x() < layout.right() - 9
                && event.y() >= layout.viewTop() && event.y() < layout.viewBottom()) {
            draggingScroll = true;
            scrollTo(event.y());
            return true;
        }
        if (insideContent(event.x(), event.y())) {
            super.mouseClicked(event, doubleClick);
        } else {
            // Clipped controls must not intercept clicks on the fixed header or footer.
            for (PlacedWidget placed : contentWidgets) placed.widget().visible = false;
            super.mouseClicked(event, doubleClick);
            layoutContent();
        }
        return true;
    }

    private void scrollTo(double y) {
        int h = layout.viewBottom() - layout.viewTop();
        int thumb = Math.max(16, h * h / Math.max(1, contentHeight));
        scroll = (int) ((y - layout.viewTop() - thumb / 2.0) * maxScroll() / Math.max(1, h - thumb));
        layoutContent();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingPreview && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            previewYaw = (float) ((previewYaw - dx * 2) % 360);
            previewPitch = Math.max(-35, Math.min(35, previewPitch - (float) dy));
            return true;
        }
        if (draggingScroll && event.button() == InputConstants.MOUSE_BUTTON_LEFT) { scrollTo(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && (draggingPreview || draggingScroll)) {
            draggingPreview = false;
            draggingScroll = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (insidePreview(mx, my)) {
            previewZoom = Math.max(0.5f, Math.min(2.0f, previewZoom + (float) vertical * 0.1f));
            return true;
        }
        if (insideContent(mx, my)) {
            scroll += vertical < 0 ? 28 : vertical > 0 ? -28 : 0;
            layoutContent();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDown() && event.key() == 83) { save(); return true; }
        if (event.key() == 266 || event.key() == 267) {
            scroll += (event.key() == 267 ? 1 : -1) * Math.max(28, layout.viewBottom() - layout.viewTop() - 22);
            layoutContent();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() { onCancel(); }

    private String fit(String value, int maxWidth) {
        return font.width(value) <= maxWidth ? value : font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) GuiUtils.drawRect(g, x, y, x + w, y + h, color);
    }

    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        EETheme.bevel(g, x, y, w, h, fill, false);
    }

    private void openMobSettings() {
        EntityType<?> entityType = getCurrentEntityType(currentItemStack);
        Identifier entityId = getEntityRegistry().getKey(entityType);
        String entityPath = entityId.getPath();

        CompoundTag tag = getEntityTag();

        if (com.dutchmtc.ee.utils.VisualEntityEditorData.supported(entityType)) {
            mc.gui.setScreen(new GuiVisualEntityEditor(this, entityType, tag, this::setEntityTag));
            return;
        }

        // Default enabled feature sets from mob_fields.json (if present)
        java.util.Set<String> featureSets = new java.util.LinkedHashSet<>();
        JsonObject entity = MobDataRepository.getEntity(entityPath);
        if (entity != null && entity.has("feature_sets") && entity.get("feature_sets").isJsonArray()) {
            JsonArray arr = entity.getAsJsonArray("feature_sets");
            for (int i = 0; i < arr.size(); i++) {
                if (arr.get(i).isJsonPrimitive()) {
                    featureSets.add(arr.get(i).getAsString());
                }
            }
        }

        MobPropertyEditorState state = MobPropertyEditorState.createDefault(entityPath, tag, featureSets);
        getMinecraft().gui.setScreen(new GuiMobFieldsEditor(GuiSpawnEggModifier.this, this::setEntityTag, state));
    }

    private void openEquipment() {
        CompoundTag tag = getEntityTag();
        ItemUtils.ContainerData equipment = getEquipmentData(tag);
        float[] dropChances = getEquipmentDropChances(tag);
        List<Component> slotNames = List.of(
            Component.literal("Main hand"),
            Component.literal("Off hand"),
            Component.literal("Head"),
            Component.literal("Chest"),
            Component.literal("Legs"),
            Component.literal("Feet"),
            Component.literal("Body"),
            Component.literal("Saddle")
        );
        getMinecraft().gui.setScreen(new GuiSpawnEggEquipmentModifier(this, Component.literal("Mob equipment"), newData -> {
            updateEntityTag(t -> {
                setEquipmentData(t, newData.equipment());
                if (getCurrentEntityType(currentItemStack) != EntityTypes.MANNEQUIN)
                    setEquipmentDropChances(t, newData.dropChances());
            });
        }, new GuiSpawnEggEquipmentModifier.EquipmentData(equipment, dropChances), slotNames, currentItemStack));
    }

    private void openAttributes() {
        CompoundTag tag = getEntityTag();
        List<CompoundTag> attributes = new ArrayList<>();
        if (ItemUtils.hasTag(tag, "attributes", 9)) {
            ListTag list = ItemUtils.getList(tag, "attributes", 10);
            for (int i = 0; i < list.size(); i++) {
                attributes.add(ItemUtils.getCompound(list, i));
            }
        } else if (ItemUtils.hasTag(tag, "Attributes", 9)) {
            ListTag list = ItemUtils.getList(tag, "Attributes", 10);
            for (int i = 0; i < list.size(); i++) {
                attributes.add(ItemUtils.getCompound(list, i));
            }
        }
        getMinecraft().gui.setScreen(new GuiEntityAttributeModifier(this, attributes, newAttrs -> {
            updateEntityTag(t -> {
                ListTag list = new ListTag();
                newAttrs.forEach(list::add);
                t.put("attributes", list);
                ItemUtils.remove(t, "Attributes"); // Remove legacy key
            });
        }));
    }

    private void openEffects() {
        CompoundTag tag = getEntityTag();
        List<CompoundTag> effects = new ArrayList<>();
        if (ItemUtils.hasTag(tag, "active_effects", 9)) {
            ListTag list = ItemUtils.getList(tag, "active_effects", 10);
            for (int i = 0; i < list.size(); i++) {
                effects.add(ItemUtils.getCompound(list, i));
            }
        } else if (ItemUtils.hasTag(tag, "ActiveEffects", 9)) {
            ListTag list = ItemUtils.getList(tag, "ActiveEffects", 10);
            for (int i = 0; i < list.size(); i++) {
                effects.add(ItemUtils.getCompound(list, i));
            }
        }
        getMinecraft().gui.setScreen(new GuiActiveEffectsModifier(this, effects, newEffects -> {
            updateEntityTag(t -> {
                ListTag list = new ListTag();
                newEffects.forEach(list::add);
                t.put("active_effects", list);
                ItemUtils.remove(t, "ActiveEffects"); // Remove legacy key
            });
        }));
    }

    private void openTrades() {
        List<CompoundTag> recipes = getTradeRecipes(getEntityTag());
        // Defer opening to the next tick to avoid the screen being overwritten by other UI transitions
        // (e.g. chat closing), which can look like the GUI "opens for 1 frame then closes".
        var mc = getMinecraft();
        mc.execute(() -> mc.gui.setScreen(new GuiVillagerTradesEditor(this, recipes, newRecipes -> {
            updateEntityTag(tag -> {
                setTradeRecipes(tag, newRecipes);
                ensureVillagerDataForTrading(tag);
            });
        })));
    }

    private void setOffersTag(CompoundTag offersTag) {
        updateEntityTag(tag -> {
            if (offersTag == null || offersTag.isEmpty()) {
                ItemUtils.remove(tag, "Offers");
            } else {
                tag.put("Offers", offersTag);
                ensureVillagerDataForTrading(tag);
            }
        });
    }

    private void ensureVillagerDataForTrading(CompoundTag entityTag) {
        if (entityTag == null) {
            return;
        }

        EntityType<?> type = getCurrentEntityType(currentItemStack);
        Identifier id = getEntityRegistry().getKey(type);
        if (id == null) {
            return;
        }

        // Villager trading can instantly close if VillagerData is missing or the profession is "none".
        // Only apply sane defaults for vanilla villager variants; don't clobber custom entities.
        String path = id.getPath();
        if (!"villager".equals(path) && !"zombie_villager".equals(path)) {
            return;
        }

        CompoundTag data = entityTag.getCompound("VillagerData").orElse(new CompoundTag());
        boolean changed = false;

        String profession = data.getString("profession").orElse("");
        if (profession.isEmpty() || "minecraft:none".equals(profession) || "none".equals(profession)) {
            data.putString("profession", "minecraft:farmer");
            changed = true;
        }

        String villagerType = data.getString("type").orElse("");
        if (villagerType.isEmpty()) {
            data.putString("type", "minecraft:plains");
            changed = true;
        }

        if (!ItemUtils.hasTag(data, "level", Tag.TAG_INT) && !ItemUtils.hasTag(data, "level", Tag.TAG_BYTE)) {
            data.putInt("level", 2);
            changed = true;
        }

        if (changed) {
            entityTag.put("VillagerData", data);
        }
    }

    private static List<CompoundTag> getTradeRecipes(CompoundTag entityTag) {
        List<CompoundTag> out = new ArrayList<>();
        if (!ItemUtils.hasTag(entityTag, "Offers", Tag.TAG_COMPOUND)) return out;
        CompoundTag offers = entityTag.getCompound("Offers").orElse(new CompoundTag());
        if (!ItemUtils.hasTag(offers, "Recipes", Tag.TAG_LIST)) return out;
        ListTag recipes = offers.getList("Recipes").orElse(new ListTag());
        for (int i = 0; i < recipes.size(); i++) {
            Tag t = recipes.get(i);
            if (t instanceof CompoundTag ct) {
                out.add(ct.copy());
            }
        }
        return out;
    }

    private static void setTradeRecipes(CompoundTag entityTag, List<CompoundTag> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            if (ItemUtils.hasTag(entityTag, "Offers", Tag.TAG_COMPOUND)) {
                CompoundTag offers = entityTag.getCompound("Offers").orElse(new CompoundTag());
                ItemUtils.remove(offers, "Recipes");
                if (offers.isEmpty()) {
                    ItemUtils.remove(entityTag, "Offers");
                } else {
                    entityTag.put("Offers", offers);
                }
            }
            return;
        }

        CompoundTag offers = entityTag.getCompound("Offers").orElse(new CompoundTag());
        ListTag list = new ListTag();
        for (CompoundTag recipe : recipes) {
            list.add(recipe);
        }
        offers.put("Recipes", list);
        entityTag.put("Offers", offers);
    }

    private boolean supportsTrades() {
        EntityType<?> type = getCurrentEntityType(currentItemStack);
        Identifier id = getEntityRegistry().getKey(type);
        if (id == null) return false;
        String path = id.getPath();
        return "villager".equals(path) || "zombie_villager".equals(path) || "wandering_trader".equals(path);
    }

    private CompoundTag getEntityTag() {
        TypedEntityData<EntityType<?>> entityData = ItemUtils.getComponent(currentItemStack, DataComponents.ENTITY_DATA);
        return entityData != null ? entityData.copyTagWithoutId() : new CompoundTag();
    }

    private void updateEntityTag(Consumer<CompoundTag> updater) {
        CompoundTag tag = getEntityTag();
        updater.accept(tag);
        setEntityTag(tag);
    }

    private void setEntityTag(CompoundTag newTag) {
        previewDirty = true;
        EntityType<?> type = getCurrentEntityType(currentItemStack);
        CompoundTag tag = newTag == null ? new CompoundTag() : newTag.copy();
        // Even an empty tag must keep an entity override when the egg item spawns something else.
        // Restore the item's default component instead of removing it: spawn eggs can supply
        // ENTITY_DATA themselves, and removing it would leave a needless removal patch.
        TypedEntityData<EntityType<?>> data = tag.isEmpty() && type == getEggEntityType(currentItemStack)
                ? new ItemStack(currentItemStack.getItem()).get(DataComponents.ENTITY_DATA)
                : TypedEntityData.of(type, tag);
        ItemUtils.setComponent(currentItemStack, DataComponents.ENTITY_DATA,
                data);
    }

    private static Component parseComponent(String json) {
        try {
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .result()
                    .orElse(Component.empty());
        } catch (Exception e) {
            return Component.empty();
        }
    }

    private static Component parseComponentSnbt(String snbt) {
        try {
            CompoundTag wrapper = TagParser.parseCompoundFully("{v:" + snbt + "}");
            Tag tag;
            if (snbt.startsWith("[")) {
                tag = wrapper.getList("v").orElse(new ListTag());
            } else {
                tag = wrapper.getCompound("v").orElse(new CompoundTag());
            }
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, tag)
                    .result()
                    .orElse(Component.empty());
        } catch (Exception e) {
            return Component.empty();
        }
    }

    private static Tag toComponentNbt(Component component) {
        return ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component).result().orElse(null);
    }

    private static String toComponentJson(Component component) {
        return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component)
                .result()
                .map(Object::toString)
                .orElse("{\"text\":\"\"}");
    }

    private static Component readEntityCustomName(CompoundTag entityTag) {
        if (ItemUtils.hasTag(entityTag, "CustomName", Tag.TAG_COMPOUND)) {
            CompoundTag raw = entityTag.getCompound("CustomName").orElse(new CompoundTag());
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, raw).result().orElse(Component.empty());
        }
        if (ItemUtils.hasTag(entityTag, "CustomName", Tag.TAG_LIST)) {
            ListTag raw = entityTag.getList("CustomName").orElse(new ListTag());
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, raw).result().orElse(Component.empty());
        }
        if (ItemUtils.hasTag(entityTag, "custom_name", Tag.TAG_STRING)) {
            String raw = ItemUtils.getString(entityTag, "custom_name");
            Component parsed = parseComponent(raw);
            if (!parsed.getString().isEmpty()) {
                return parsed;
            }
            return parseComponentSnbt(raw);
        }
        if (ItemUtils.hasTag(entityTag, "custom_name", Tag.TAG_COMPOUND)) {
            CompoundTag raw = entityTag.getCompound("custom_name").orElse(new CompoundTag());
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, raw).result().orElse(Component.empty());
        }
        if (ItemUtils.hasTag(entityTag, "custom_name", Tag.TAG_LIST)) {
            ListTag raw = entityTag.getList("custom_name").orElse(new ListTag());
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, raw).result().orElse(Component.empty());
        }
        if (ItemUtils.hasTag(entityTag, "CustomName", Tag.TAG_STRING)) {
            String raw = ItemUtils.getString(entityTag, "CustomName");
            // 1.21.5+ stores text components directly in NBT; legacy may still be JSON/SNBT-in-a-string
            Component parsed = parseComponent(raw);
            if (!parsed.getString().isEmpty()) return parsed;
            parsed = parseComponentSnbt(raw);
            if (!parsed.getString().isEmpty()) return parsed;
            // If it's a plain string, it's already a valid (string) text component.
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, StringTag.valueOf(raw)).result().orElse(Component.literal(raw));
        }
        return Component.empty();
    }

    private static EntityType<?> getEggEntityType(ItemStack stack) {
        if (stack.getItem() instanceof SpawnEggItem egg) {
            ItemStack baseStack = new ItemStack(egg);
            try {
                return egg.getType(baseStack);
            } catch (Throwable t) {
                return egg.getType(stack);
            }
        }
        return EntityTypes.PIG;
    }

    private static EntityType<?> getCurrentEntityType(ItemStack stack) {
        TypedEntityData<EntityType<?>> entityData = ItemUtils.getComponent(stack, DataComponents.ENTITY_DATA);
        return entityData != null ? entityData.type() : getEggEntityType(stack);
    }

    private static Component parseLegacyFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        String formatted = ChatUtils.translateColorCodes(input);
        StringBuilder buffer = new StringBuilder();
        List<net.minecraft.ChatFormatting> formats = new ArrayList<>();
        Integer rgbColor = null;
        MutableComponent out = Component.literal("");

        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (c == ChatUtils.MODIFIER && i + 1 < formatted.length()) {
                char code = Character.toLowerCase(formatted.charAt(i + 1));

                // §x§R§R§G§G§B§B
                if (code == 'x' && i + 13 < formatted.length()) {
                    int rgb = tryParseSectionHexColor(formatted, i);
                    if (rgb >= 0) {
                        if (buffer.length() > 0) {
                            MutableComponent part = Component.literal(buffer.toString());
                            if (!formats.isEmpty()) {
                                part = part.withStyle(formats.toArray(new net.minecraft.ChatFormatting[0]));
                            }
                            if (rgbColor != null) {
                                int cRgb = rgbColor;
                                part = part.withStyle(s -> s.withColor(cRgb));
                            }
                            out.append(part);
                            buffer.setLength(0);
                        }

                        formats.clear();
                        rgbColor = rgb;
                        i += 13;
                        continue;
                    }
                }

                net.minecraft.ChatFormatting fmt = legacyCodeToFormatting(code);
                if (fmt != null) {
                    if (buffer.length() > 0) {
                        MutableComponent part = Component.literal(buffer.toString());
                        if (!formats.isEmpty()) {
                            part = part.withStyle(formats.toArray(new net.minecraft.ChatFormatting[0]));
                        }
                        if (rgbColor != null) {
                            int cRgb = rgbColor;
                            part = part.withStyle(s -> s.withColor(cRgb));
                        }
                        out.append(part);
                        buffer.setLength(0);
                    }

                    if (code == 'r') {
                        formats.clear();
                        rgbColor = null;
                    } else if (com.dutchmtc.ee.utils.ChatUtils.isColor(fmt)) {
                        formats.clear();
                        formats.add(fmt);
                        rgbColor = null;
                    } else if (!formats.contains(fmt)) {
                        formats.add(fmt);
                    }

                    i++; // skip code
                    continue;
                }
            }
            buffer.append(c);
        }

        if (buffer.length() > 0) {
            MutableComponent part = Component.literal(buffer.toString());
            if (!formats.isEmpty()) {
                part = part.withStyle(formats.toArray(new net.minecraft.ChatFormatting[0]));
            }
            if (rgbColor != null) {
                int cRgb = rgbColor;
                part = part.withStyle(s -> s.withColor(cRgb));
            }
            out.append(part);
        }

        return out;
    }

    private static int tryParseSectionHexColor(String s, int sectionIndex) {
        // §x§R§R§G§G§B§B -> 14 chars total, starting at §
        if (sectionIndex + 13 >= s.length()) return -1;
        if (s.charAt(sectionIndex) != ChatUtils.MODIFIER) return -1;
        if (Character.toLowerCase(s.charAt(sectionIndex + 1)) != 'x') return -1;

        int rgb = 0;
        for (int j = 0; j < 6; j++) {
            int sepIndex = sectionIndex + 2 + j * 2;
            int digitIndex = sectionIndex + 3 + j * 2;
            if (s.charAt(sepIndex) != ChatUtils.MODIFIER) return -1;
            int d = Character.digit(s.charAt(digitIndex), 16);
            if (d < 0) return -1;
            rgb = (rgb << 4) | d;
        }
        return rgb;
    }

    private static net.minecraft.ChatFormatting legacyCodeToFormatting(char code) {
        for (net.minecraft.ChatFormatting f : net.minecraft.ChatFormatting.values()) {
            String s = f.toString();
            if (s.length() >= 2 && Character.toLowerCase(s.charAt(1)) == Character.toLowerCase(code)) {
                return f;
            }
        }
        return null;
    }
     
    ItemUtils.ContainerData getEquipmentData(CompoundTag tag) {
        NonNullList<ItemStack> stacks = NonNullList.withSize(8, ItemStack.EMPTY);
        net.minecraft.core.HolderLookup.Provider registryAccess = getRegistryAccess();

        // 1.21.5+ equipment format (preferred in 1.21.11)
        if (ItemUtils.hasTag(tag, "equipment", Tag.TAG_COMPOUND)) {
            CompoundTag equipment = ItemUtils.getCompound(tag, "equipment");
            stacks.set(0, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "mainhand")));
            stacks.set(1, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "offhand")));
            stacks.set(2, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "head")));
            stacks.set(3, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "chest")));
            stacks.set(4, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "legs")));
            stacks.set(5, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "feet")));
            stacks.set(6, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "body")));
            stacks.set(7, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(equipment, "saddle")));
        }
        
        // Legacy fallbacks (only fill slots not already present)
        if (stacks.get(0).isEmpty() && stacks.get(1).isEmpty() && ItemUtils.hasTag(tag, "hand_items", 9)) {
            ListTag list = ItemUtils.getList(tag, "hand_items", 10);
            for (int i = 0; i < list.size() && i < 2; i++) {
                stacks.set(i, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, i)));
            }
        } else if (stacks.get(0).isEmpty() && stacks.get(1).isEmpty() && ItemUtils.hasTag(tag, "HandItems", 9)) {
            ListTag list = ItemUtils.getList(tag, "HandItems", 10);
            for (int i = 0; i < list.size() && i < 2; i++) {
                stacks.set(i, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, i)));
            }
        }
        
        if (stacks.get(2).isEmpty() && stacks.get(3).isEmpty() && stacks.get(4).isEmpty() && stacks.get(5).isEmpty() && ItemUtils.hasTag(tag, "armor_items", 9)) {
            ListTag list = ItemUtils.getList(tag, "armor_items", 10);
            if (list.size() > 0) stacks.set(5, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 0)));
            if (list.size() > 1) stacks.set(4, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 1)));
            if (list.size() > 2) stacks.set(3, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 2)));
            if (list.size() > 3) stacks.set(2, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 3)));
        } else if (stacks.get(2).isEmpty() && stacks.get(3).isEmpty() && stacks.get(4).isEmpty() && stacks.get(5).isEmpty() && ItemUtils.hasTag(tag, "ArmorItems", 9)) {
            ListTag list = ItemUtils.getList(tag, "ArmorItems", 10);
            if (list.size() > 0) stacks.set(5, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 0)));
            if (list.size() > 1) stacks.set(4, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 1)));
            if (list.size() > 2) stacks.set(3, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 2)));
            if (list.size() > 3) stacks.set(2, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(list, 3)));
        }
        
        // Body Armor (Slot 6)
        if (stacks.get(6).isEmpty() && ItemUtils.hasTag(tag, "body_armor_item", 10)) {
            stacks.set(6, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "body_armor_item")));
        } else if (stacks.get(6).isEmpty() && ItemUtils.hasTag(tag, "ArmorItem", 10)) { // Horse (Legacy)
            stacks.set(6, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "ArmorItem")));
        } else if (stacks.get(6).isEmpty() && ItemUtils.hasTag(tag, "BodyArmorItem", 10)) { // Wolf (Legacy)
            stacks.set(6, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "BodyArmorItem")));
        } else if (stacks.get(6).isEmpty() && ItemUtils.hasTag(tag, "DecorItem", 10)) { // Llama (Legacy)
            stacks.set(6, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "DecorItem")));
        }
        
        // Saddle (Slot 7)
        if (stacks.get(7).isEmpty() && ItemUtils.hasTag(tag, "saddle", 10)) {
            stacks.set(7, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "saddle")));
        } else if (stacks.get(7).isEmpty() && ItemUtils.hasTag(tag, "SaddleItem", 10)) {
            stacks.set(7, ItemUtils.parseStack(registryAccess, ItemUtils.getCompound(tag, "SaddleItem")));
        } else if (stacks.get(7).isEmpty() && ItemUtils.getBoolean(tag, "Saddle")) {
            stacks.set(7, new ItemStack(net.minecraft.world.item.Items.SADDLE));
        }
        
        return new ItemUtils.ContainerData(new ItemUtils.ContainerSize(2, 4), stacks);
    }

    float[] getEquipmentDropChances(CompoundTag tag) {
        float[] chances = new float[8];
        Arrays.fill(chances, DEFAULT_EQUIPMENT_DROP_CHANCE);

        // 1.21.5+ format: drop_chances compound keyed by slot
        if (ItemUtils.hasTag(tag, "drop_chances", Tag.TAG_COMPOUND)) {
            CompoundTag dropChances = ItemUtils.getCompound(tag, "drop_chances");
            for (int i = 0; i < chances.length && i < EQUIPMENT_DROP_CHANCE_KEYS.length; i++) {
                chances[i] = readChance(dropChances, EQUIPMENT_DROP_CHANCE_KEYS[i], DEFAULT_EQUIPMENT_DROP_CHANCE);
            }
            return chances;
        }

        // Legacy lists (if present)
        if (ItemUtils.hasTag(tag, "HandDropChances", Tag.TAG_LIST)) {
            ListTag list = ItemUtils.getList(tag, "HandDropChances", Tag.TAG_FLOAT);
            if (!list.isEmpty()) {
                chances[0] = readChance(list, 0, DEFAULT_EQUIPMENT_DROP_CHANCE);
                chances[1] = readChance(list, 1, DEFAULT_EQUIPMENT_DROP_CHANCE);
            }
        } else if (ItemUtils.hasTag(tag, "hand_drop_chances", Tag.TAG_LIST)) {
            ListTag list = ItemUtils.getList(tag, "hand_drop_chances", Tag.TAG_FLOAT);
            if (!list.isEmpty()) {
                chances[0] = readChance(list, 0, DEFAULT_EQUIPMENT_DROP_CHANCE);
                chances[1] = readChance(list, 1, DEFAULT_EQUIPMENT_DROP_CHANCE);
            }
        }

        if (ItemUtils.hasTag(tag, "ArmorDropChances", Tag.TAG_LIST)) {
            ListTag list = ItemUtils.getList(tag, "ArmorDropChances", Tag.TAG_FLOAT);
            if (!list.isEmpty()) {
                chances[5] = readChance(list, 0, DEFAULT_EQUIPMENT_DROP_CHANCE); // feet
                chances[4] = readChance(list, 1, DEFAULT_EQUIPMENT_DROP_CHANCE); // legs
                chances[3] = readChance(list, 2, DEFAULT_EQUIPMENT_DROP_CHANCE); // chest
                chances[2] = readChance(list, 3, DEFAULT_EQUIPMENT_DROP_CHANCE); // head
            }
        } else if (ItemUtils.hasTag(tag, "armor_drop_chances", Tag.TAG_LIST)) {
            ListTag list = ItemUtils.getList(tag, "armor_drop_chances", Tag.TAG_FLOAT);
            if (!list.isEmpty()) {
                chances[5] = readChance(list, 0, DEFAULT_EQUIPMENT_DROP_CHANCE); // feet
                chances[4] = readChance(list, 1, DEFAULT_EQUIPMENT_DROP_CHANCE); // legs
                chances[3] = readChance(list, 2, DEFAULT_EQUIPMENT_DROP_CHANCE); // chest
                chances[2] = readChance(list, 3, DEFAULT_EQUIPMENT_DROP_CHANCE); // head
            }
        }

        if (ItemUtils.hasTag(tag, "body_armor_drop_chance", Tag.TAG_FLOAT) || ItemUtils.hasTag(tag, "body_armor_drop_chance", Tag.TAG_DOUBLE)) {
            chances[6] = readChance(tag, "body_armor_drop_chance", DEFAULT_EQUIPMENT_DROP_CHANCE);
        } else if (ItemUtils.hasTag(tag, "BodyArmorDropChance", Tag.TAG_FLOAT) || ItemUtils.hasTag(tag, "BodyArmorDropChance", Tag.TAG_DOUBLE)) {
            chances[6] = readChance(tag, "BodyArmorDropChance", DEFAULT_EQUIPMENT_DROP_CHANCE);
        }

        if (ItemUtils.hasTag(tag, "saddle_drop_chance", Tag.TAG_FLOAT) || ItemUtils.hasTag(tag, "saddle_drop_chance", Tag.TAG_DOUBLE)) {
            chances[7] = readChance(tag, "saddle_drop_chance", DEFAULT_EQUIPMENT_DROP_CHANCE);
        } else if (ItemUtils.hasTag(tag, "SaddleDropChance", Tag.TAG_FLOAT) || ItemUtils.hasTag(tag, "SaddleDropChance", Tag.TAG_DOUBLE)) {
            chances[7] = readChance(tag, "SaddleDropChance", DEFAULT_EQUIPMENT_DROP_CHANCE);
        }

        return chances;
    }

    void setEquipmentDropChances(CompoundTag tag, float[] chances) {
        if (chances == null || chances.length < 8) {
            return;
        }

        CompoundTag dropChances = new CompoundTag();
        for (int i = 0; i < 8 && i < EQUIPMENT_DROP_CHANCE_KEYS.length; i++) {
            float chance = chances[i];
            if (Float.isNaN(chance) || Float.isInfinite(chance)) {
                chance = DEFAULT_EQUIPMENT_DROP_CHANCE;
            }
            if (Math.abs(chance - DEFAULT_EQUIPMENT_DROP_CHANCE) > 1.0e-6f) {
                ItemUtils.putFloat(dropChances, EQUIPMENT_DROP_CHANCE_KEYS[i], chance);
            }
        }

        if (dropChances.isEmpty()) {
            ItemUtils.remove(tag, "drop_chances");
        } else {
            tag.put("drop_chances", dropChances);
        }

        // Remove legacy keys
        ItemUtils.remove(tag, "HandDropChances");
        ItemUtils.remove(tag, "hand_drop_chances");
        ItemUtils.remove(tag, "ArmorDropChances");
        ItemUtils.remove(tag, "armor_drop_chances");
        ItemUtils.remove(tag, "body_armor_drop_chance");
        ItemUtils.remove(tag, "BodyArmorDropChance");
        ItemUtils.remove(tag, "saddle_drop_chance");
        ItemUtils.remove(tag, "SaddleDropChance");
    }

    private static float readChance(CompoundTag tag, String key, float def) {
        if (ItemUtils.hasTag(tag, key, Tag.TAG_FLOAT)) {
            return ItemUtils.getFloat(tag, key);
        }
        if (ItemUtils.hasTag(tag, key, Tag.TAG_DOUBLE)) {
            return (float) ItemUtils.getDouble(tag, key);
        }
        if (ItemUtils.hasTag(tag, key, Tag.TAG_INT)) {
            return (float) ItemUtils.getInt(tag, key);
        }
        return def;
    }

    private static float readChance(ListTag list, int index, float def) {
        if (index < 0 || index >= list.size()) {
            return def;
        }
        Tag t = list.get(index);
        if (t == null) {
            return def;
        }
        if (t instanceof net.minecraft.nbt.FloatTag ft) {
            return ft.floatValue();
        }
        if (t instanceof net.minecraft.nbt.DoubleTag dt) {
            return (float) dt.doubleValue();
        }
        if (t instanceof net.minecraft.nbt.IntTag it) {
            return (float) it.intValue();
        }
        if (t instanceof net.minecraft.nbt.ShortTag st) {
            return (float) st.shortValue();
        }
        if (t instanceof net.minecraft.nbt.ByteTag bt) {
            return (float) bt.byteValue();
        }
        return def;
    }

    void setEquipmentData(CompoundTag tag, ItemUtils.ContainerData data) {
        // 1.21.5+ format: store equipment in the dedicated compound. (1.21.11 uses this)
        CompoundTag equipment = new CompoundTag();
        putEquipmentSlot(equipment, "mainhand", data.stacks().get(0));
        putEquipmentSlot(equipment, "offhand", data.stacks().get(1));
        putEquipmentSlot(equipment, "head", data.stacks().get(2));
        putEquipmentSlot(equipment, "chest", data.stacks().get(3));
        putEquipmentSlot(equipment, "legs", data.stacks().get(4));
        putEquipmentSlot(equipment, "feet", data.stacks().get(5));
        putEquipmentSlot(equipment, "body", data.stacks().get(6));
        putEquipmentSlot(equipment, "saddle", data.stacks().get(7));
        if (equipment.isEmpty()) {
            ItemUtils.remove(tag, "equipment");
        } else {
            tag.put("equipment", equipment);
        }

        // Remove legacy lists to avoid confusing/ignored data on 1.21.11
        ItemUtils.remove(tag, "hand_items");
        ItemUtils.remove(tag, "HandItems");
        ItemUtils.remove(tag, "armor_items");
        ItemUtils.remove(tag, "ArmorItems");
        
        EntityType<?> type = getCurrentEntityType(currentItemStack);
        Identifier id = getEntityRegistry().getKey(type);
        String path = id.getPath();

        // Body Armor (Slot 6)
        ItemStack bodyStack = data.stacks().get(6);
        CompoundTag bodyTag = getTagOrEmpty(bodyStack);
        if (!bodyStack.isEmpty()) tag.put("body_armor_item", bodyTag);
        else ItemUtils.remove(tag, "body_armor_item");
        
        if (path.equals("wolf")) {
            if (!bodyStack.isEmpty()) tag.put("BodyArmorItem", bodyTag);
            else ItemUtils.remove(tag, "BodyArmorItem");
        } else if (path.contains("llama")) {
            if (!bodyStack.isEmpty()) tag.put("DecorItem", bodyTag);
            else ItemUtils.remove(tag, "DecorItem");
        } else if (path.equals("horse")) {
            if (!bodyStack.isEmpty()) tag.put("ArmorItem", bodyTag);
            else ItemUtils.remove(tag, "ArmorItem");
        }
        
        // Saddle (Slot 7)
        ItemStack saddleStack = data.stacks().get(7);
        if (path.equals("pig") || path.equals("strider")) {
            ItemUtils.putBoolean(tag, "Saddle", !saddleStack.isEmpty());
            ItemUtils.remove(tag, "saddle");
            ItemUtils.remove(tag, "SaddleItem");
        } else {
            if (!saddleStack.isEmpty()) {
                tag.put("saddle", getTagOrEmpty(saddleStack));
            } else {
                ItemUtils.remove(tag, "saddle");
            }
            if (!saddleStack.isEmpty()) {
                tag.put("SaddleItem", getTagOrEmpty(saddleStack));
            } else {
                ItemUtils.remove(tag, "SaddleItem");
            }
            ItemUtils.remove(tag, "Saddle");
        }
    }

    private void putEquipmentSlot(CompoundTag equipment, String slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        equipment.put(slot, getTagOrEmpty(stack));
    }
    
    private CompoundTag getTagOrEmpty(ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        return (CompoundTag) ItemUtils.saveStack(stack, getRegistryAccess());
    }

    private net.minecraft.core.HolderLookup.Provider getRegistryAccess() {
        return mc.level != null ? mc.level.registryAccess() : VanillaRegistries.createWorldLookup();
    }

    private Registry<EntityType<?>> getEntityRegistry() {
        if (mc.level != null) {
            return mc.level.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE);
        }
        return BuiltInRegistries.ENTITY_TYPE;
    }

    private void addNameInput(int y) {
        Component currentName = readEntityCustomName(getEntityTag());
        if (nameDocument == null || !java.util.Objects.equals(currentName, nameSnapshot))
            nameDocument = new StyledTextDocument(currentName);
        nameSnapshot = currentName;
        StyledTextDocument name = nameDocument;
        nameBox = new StyledTextField(font, layout.contentX() + 4, 0, usableWidth() - 8, 20,
                Component.literal("Custom name (optional)"), name, 256);
        nameBox.setHint(Component.literal("Leave blank for default name").withColor(0xAEBBB5));
        nameBox.setTooltip(Tooltip.create(Component.literal("The entity's name, not the egg item's name. Leave blank to use its default name. Select text to change its color or style.")));
        nameBox.setStyledResponder(value -> {
            updateEntityTag(tag -> {
                if (value.getString().isEmpty()) {
                    ItemUtils.remove(tag, "custom_name");
                    ItemUtils.remove(tag, "CustomName");
                } else {
                    Tag nbt = toComponentNbt(value);
                    if (nbt != null) tag.put("CustomName", nbt);
                    ItemUtils.remove(tag, "custom_name");
                }
            });
            nameSnapshot = readEntityCustomName(getEntityTag());
        });
        place(nameBox, y);
        nameToolbar = nameBox.createToolbar(this, layout.contentX(), layout.right() - 10);
        decorations.add(g -> {
            int inputY = nameBox.getY();
            com.dutchmtc.ee.gui.components.EETextField.validationFrame(nameBox, layout.contentX(), inputY, usableWidth(), 20);
            rect(g, layout.contentX(), inputY, usableWidth(), 20, 0xFF555555);
            rect(g, layout.contentX() + 1, inputY + 1, usableWidth() - 2, 18, 0xFF343B39);
            rect(g, layout.contentX(), inputY + 19, usableWidth(), 1, 0xFFFFFFFF);
            rect(g, layout.contentX() + usableWidth() - 1, inputY, 1, 20, 0xFFFFFFFF);
        });
    }

}
