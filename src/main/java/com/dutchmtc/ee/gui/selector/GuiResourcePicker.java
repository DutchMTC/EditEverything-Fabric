package com.dutchmtc.ee.gui.selector;

import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.utils.Tuple;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.*;
import java.util.function.Consumer;

/** The universal choice list, with optional resource ID input and sound/model previews. */
public final class GuiResourcePicker extends GuiButtonListSelector<Identifier> {
    private final boolean closed, sound, allowNone;
    private final ItemStack modelPreview;
    private final String choiceName;
    private EEButton use, play;

    public GuiResourcePicker(
            Screen parent,
            String title,
            Collection<Identifier> choices,
            Identifier current,
            Consumer<Identifier> setter,
            ItemStack preview,
            boolean sound,
            boolean allowNone) {
        super(
                parent,
                Component.literal(title),
                resourceEntries(choices, allowNone),
                id -> {
                    setter.accept(id);
                    return null;
                });
        closed = false;
        this.sound = sound;
        this.allowNone = allowNone;
        modelPreview = preview.copy();
        choiceName = sound ? "sound" : "model";
        if (current != null || allowNone) withCurrent(current);
        withSearchKeys(id -> id == null ? "" : id.toString());
        withDetails(id -> Component.literal(id == null ? "None" : id.toString()));
        if (!sound && !modelPreview.isEmpty()) withIcons(this::preview);
    }

    /** Named choices are restricted to the supplied registry and commit on one click. */
    public GuiResourcePicker(
            Screen parent,
            String title,
            Map<Identifier, String> choices,
            Identifier current,
            Consumer<Identifier> setter,
            String choiceName) {
        super(
                parent,
                Component.literal(title),
                namedEntries(choices),
                id -> {
                    setter.accept(id);
                    return null;
                });
        closed = true;
        sound = false;
        allowNone = false;
        modelPreview = ItemStack.EMPTY;
        this.choiceName = choiceName;
        withCurrent(current);
        withSearchKeys(id -> id.toString());
        if ("mob".equals(choiceName))
            withIcons(
                    id ->
                            BuiltInRegistries.ENTITY_TYPE
                                    .getOptional(id)
                                    .flatMap(SpawnEggItem::byId)
                                    .map(h -> new ItemStack(h.value()))
                                    .orElse(ItemStack.EMPTY));
    }

    private static List<Tuple<String, Identifier>> resourceEntries(
            Collection<Identifier> choices, boolean none) {
        var result = new ArrayList<Tuple<String, Identifier>>();
        if (none) result.add(new Tuple<>("None", null));
        choices.stream()
                .distinct()
                .sorted(Comparator.comparing(Identifier::toString))
                .forEach(id -> result.add(new Tuple<>(resourceName(id), id)));
        return result;
    }

    private static List<Tuple<String, Identifier>> namedEntries(Map<Identifier, String> choices) {
        var result = new ArrayList<Tuple<String, Identifier>>();
        choices.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .forEach(
                        entry -> {
                            String label = entry.getValue();
                            if (choices.values().stream().filter(label::equalsIgnoreCase).count()
                                    > 1) label += " (" + entry.getKey() + ")";
                            result.add(new Tuple<>(label, entry.getKey()));
                        });
        return result;
    }

    private static String resourceName(Identifier id) {
        String path = id.getPath().replace('_', ' ').replace('.', ' ').replace('/', ' ');
        return path.isEmpty()
                ? id.toString()
                : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    @Override
    protected String searchHint() {
        return closed ? "Search..." : "Search or enter namespace:id...";
    }

    @Override
    protected int extraHeight() {
        return closed ? 0 : 28;
    }

    @Override
    protected void buildFooter(int x, int y, int width) {
        use = null;
        if (closed) {
            super.buildFooter(x, y, width);
            return;
        }
        int half = (width - 6) / 2;
        addRenderableWidget(
                new EEButton(x, y, half, 20, Component.literal("Cancel"), b -> onClose()));
        use =
                addRenderableWidget(
                        new EEButton(
                                x + half + 6,
                                y,
                                width - half - 6,
                                20,
                                Component.literal("Use " + choiceName),
                                b -> apply()));
    }

    @Override
    protected void buildExtraControls() {
        play = null;
        if (!closed && sound)
            play =
                    addRenderableWidget(
                            new EEButton(
                                    left + panelWidth - 62,
                                    top + panelHeight - 58,
                                    48,
                                    20,
                                    Component.literal("Play"),
                                    b -> playSound()) {
                                @Override
                                protected void extractContents(
                                        GuiGraphicsExtractor g, int mx, int my, float dt) {
                                    EETheme.bevel(
                                            g,
                                            getX(),
                                            getY(),
                                            getWidth(),
                                            getHeight(),
                                            active && isHoveredOrFocused()
                                                    ? EETheme.CONTROL_HOVER
                                                    : EETheme.CONTROL,
                                            false);
                                    g.text(
                                            font,
                                            getMessage(),
                                            getX() + (getWidth() - font.width(getMessage())) / 2,
                                            getY() + 6,
                                            active ? EETheme.TEXT : EETheme.MUTED,
                                            false);
                                }
                            });
    }

    @Override
    protected void filterChanged() {
        if (!closed && query.contains(":")) {
            Identifier typed = typedId();
            if (typed != null) {
                current = typed;
                hasCurrent = true;
            }
        }
        refresh();
    }

    private String invalidReason() {
        if (!closed && query.contains(":") && typedId() == null)
            return "Enter a complete lowercase resource ID, such as "
                    + (sound ? "minecraft:entity.pig.ambient" : "minecraft:diamond")
                    + ", or choose a listed resource.";
        if (!hasCurrent || current == null && !allowNone)
            return "Choose a " + choiceName + " first.";
        return null;
    }

    private void refresh() {
        String invalid = invalidReason();
        EETextField.validate(
                search, !closed && query.contains(":") && typedId() == null ? invalid : null);
        if (use != null) {
            use.active = invalid == null;
            use.setTooltip(invalid == null ? null : Tooltip.create(Component.literal(invalid)));
        }
        if (play != null) {
            play.active = invalid == null && current != null;
            play.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    play.active
                                            ? "Preview this sound without applying it."
                                            : invalid != null
                                                    ? invalid
                                                    : "Choose a sound to preview.")));
        }
    }

    private Identifier typedId() {
        String raw = query.trim();
        Identifier id = Identifier.tryParse(raw);
        return raw.indexOf(':') > 0 && id != null && !id.getPath().isEmpty() ? id : null;
    }

    @Override
    public void select(Identifier value) {
        if (closed) {
            if (choices.stream().anyMatch(c -> Objects.equals(c.b, value))) super.select(value);
            return;
        }
        current = value;
        hasCurrent = true;
        if (query.contains(":")) {
            search.setValue("");
            for (int i = 0; i < filtered.size(); i++)
                if (Objects.equals(filtered.get(i).b, current)) {
                    focusRow(i);
                    break;
                }
        }
        refresh();
    }

    @Override
    protected void focusedChoice(Identifier value) {
        if (!closed) {
            current = value;
            hasCurrent = true;
            refresh();
        }
    }

    private void apply() {
        refresh();
        if (invalidReason() == null) super.select(current);
    }

    private void playSound() {
        if (invalidReason() != null || current == null) return;
        mc.getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(current), 1));
    }

    private ItemStack preview(Identifier id) {
        var stack = modelPreview.copyWithCount(1);
        if (id != null) stack.set(DataComponents.ITEM_MODEL, id);
        else if (allowNone) {
            var original = new ItemStack(stack.getItem()).get(DataComponents.ITEM_MODEL);
            if (original != null) stack.set(DataComponents.ITEM_MODEL, original);
        }
        return stack;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean enter =
                event.key() == InputConstants.KEY_RETURN
                        || event.key() == InputConstants.KEY_NUMPADENTER;
        if (!closed && enter && (search.isFocused() || rows.contains(getFocused()))) {
            if (rows.contains(getFocused())) super.keyPressed(event);
            else if (!hasCurrent
                    && !query.isBlank()
                    && !query.contains(":")
                    && !filtered.isEmpty()) {
                current = filtered.getFirst().b;
                hasCurrent = true;
            }
            apply();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void renderExtras(GuiGraphicsExtractor g, int mx, int my, float dt) {
        if (closed) return;
        int x = left + 12, y = top + panelHeight - 60, w = panelWidth - 24;
        EETheme.bevel(g, x, y, w, 24, EETheme.INPUT, true);
        int indent = 6;
        if (!sound && !modelPreview.isEmpty()) {
            NativeItemPreviewState.draw(
                    g, preview(hasCurrent ? current : null), x + 2, y + 2, 20, 20);
            indent = 28;
        }
        String label =
                !hasCurrent
                        ? "Choose a " + choiceName
                        : current == null ? "None" : current.toString();
        int available = w - indent - 6 - (sound ? 54 : 0);
        g.text(font, fit(label, available), x + indent, y + 8, EETheme.LIGHT, false);
        if (mx >= x
                && mx < x + w - (sound ? 54 : 0)
                && my >= y
                && my < y + 24
                && font.width(label) > available)
            g.setTooltipForNextFrame(font, Component.literal(label), mx, my);
    }
}
