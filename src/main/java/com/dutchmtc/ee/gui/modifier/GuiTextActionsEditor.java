package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.*;
import com.dutchmtc.ee.gui.selector.GuiResourcePicker;
import com.dutchmtc.ee.gui.selector.GuiTypeListSelector;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;

import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

/** Direct text events for books; unlike signs these do not need server-command wrappers. */
public final class GuiTextActionsEditor extends EESettingsScreen<Style> {
    private enum ClickKind {
        NONE("None"),
        COMMAND("Run command"),
        LINK("Open link"),
        PAGE("Go to page"),
        COPY("Copy text"),
        DIALOG("Show dialog"),
        CUSTOM("Custom action"),
        KEEP("Keep existing");
        final String label;

        ClickKind(String label) {
            this.label = label;
        }
    }

    private enum HoverKind {
        NONE("None"),
        TEXT("Text"),
        ITEM("Item"),
        ENTITY("Entity"),
        KEEP("Keep existing");
        final String label;

        HoverKind(String label) {
            this.label = label;
        }
    }

    private final HolderLookup.Provider registries;
    private final Style original;
    private final int pageCount;
    private ClickKind clickKind;
    private HoverKind hoverKind;
    private final EnumMap<ClickKind, String> clickValues = new EnumMap<>(ClickKind.class);
    private String payload = "",
            insertion,
            entityId = UUID.randomUUID().toString(),
            entityName = "";
    private Component hoverText = Component.empty();
    private ItemStack hoverItem = ItemStack.EMPTY;
    private EntityType<?> hoverEntity;
    private HoverEvent originalEntity;

    public GuiTextActionsEditor(
            Screen parent,
            Style style,
            int pageCount,
            HolderLookup.Provider registries,
            Consumer<Style> setter) {
        super(parent, "Text actions", setter);
        this.registries = registries;
        this.original = style;
        this.pageCount = pageCount;
        insertion = Objects.requireNonNullElse(style.getInsertion(), "");
        ClickEvent click = style.getClickEvent();
        clickKind =
                switch (click) {
                    case null -> ClickKind.NONE;
                    case ClickEvent.RunCommand c -> {
                        clickValues.put(ClickKind.COMMAND, c.command());
                        yield ClickKind.COMMAND;
                    }
                    case ClickEvent.OpenUrl c -> {
                        clickValues.put(ClickKind.LINK, c.uri().toString());
                        yield ClickKind.LINK;
                    }
                    case ClickEvent.ChangePage c -> {
                        clickValues.put(ClickKind.PAGE, Integer.toString(c.page()));
                        yield ClickKind.PAGE;
                    }
                    case ClickEvent.CopyToClipboard c -> {
                        clickValues.put(ClickKind.COPY, c.value());
                        yield ClickKind.COPY;
                    }
                    case ClickEvent.ShowDialog c -> {
                        clickValues.put(
                                ClickKind.DIALOG,
                                Dialog.CODEC
                                        .encodeStart(
                                                registries.createSerializationContext(
                                                        JsonOps.INSTANCE),
                                                c.dialog())
                                        .getOrThrow()
                                        .toString());
                        yield ClickKind.DIALOG;
                    }
                    case ClickEvent.Custom c -> {
                        clickValues.put(ClickKind.CUSTOM, c.id().toString());
                        payload = c.payload().map(Object::toString).orElse("");
                        yield ClickKind.CUSTOM;
                    }
                    default -> ClickKind.KEEP;
                };
        HoverEvent hover = style.getHoverEvent();
        hoverKind =
                switch (hover) {
                    case null -> HoverKind.NONE;
                    case HoverEvent.ShowText h -> {
                        hoverText = h.value();
                        yield HoverKind.TEXT;
                    }
                    case HoverEvent.ShowItem h -> {
                        hoverItem = h.item().create();
                        yield HoverKind.ITEM;
                    }
                    case HoverEvent.ShowEntity h -> {
                        originalEntity = h;
                        hoverEntity = h.entity().type;
                        entityId = h.entity().uuid.toString();
                        entityName = h.entity().name.map(Component::getString).orElse("");
                        yield HoverKind.ENTITY;
                    }
                    default -> HoverKind.KEEP;
                };
    }

    protected void buildRows() {
        validateAction();
        List<ClickKind> kinds =
                new ArrayList<>(
                        List.of(
                                ClickKind.NONE,
                                ClickKind.COMMAND,
                                ClickKind.LINK,
                                ClickKind.PAGE,
                                ClickKind.COPY,
                                ClickKind.DIALOG,
                                ClickKind.CUSTOM));
        if (original.getClickEvent() != null && clickKind == ClickKind.KEEP)
            kinds.add(ClickKind.KEEP);
        row(
                "On click",
                "Choose what happens when the selected words are clicked in the signed book.",
                dropdown(clickKind.label, kinds, k -> k.label, k -> clickKind = k),
                180);
        if (clickKind != ClickKind.NONE && clickKind != ClickKind.KEEP) {
            String label =
                    switch (clickKind) {
                        case COMMAND -> "Command";
                        case LINK -> "Web address";
                        case PAGE -> "Page number";
                        case COPY -> "Text to copy";
                        case DIALOG -> "Dialog ID / JSON";
                        default -> "Action ID";
                    };
            String help =
                    switch (clickKind) {
                        case COMMAND ->
                                "Runs as the reader, with their permissions. Include the leading /."
                                    + " This does not grant operator permissions.";
                        case LINK ->
                                "Use https:// or http://. Minecraft asks the reader before opening"
                                    + " the website.";
                        case PAGE -> "Jump to a page in this book, from 1 to " + pageCount + ".";
                        case DIALOG ->
                                "Registered dialog ID or inline JSON. Requires a server that"
                                    + " supports dialogs.";
                        case CUSTOM ->
                                "A custom action ID handled by the reader's server. Requires a"
                                    + " matching plugin or mod.";
                        default -> "Copies this text to the reader's clipboard.";
                    };
            EditBox value =
                    text(
                            "Click value " + clickKind.name(),
                            clickValues.getOrDefault(clickKind, ""),
                            v -> {
                                clickValues.put(clickKind, v);
                                validateAction();
                            });
            value.setMaxLength(32767);
            row(label, help, value, 190);
            if (clickKind == ClickKind.CUSTOM) {
                EditBox field =
                        text(
                                "Payload",
                                payload,
                                v -> {
                                    payload = v;
                                    validateAction();
                                });
                field.setMaxLength(32767);
                row(
                        "Payload (optional)",
                        "Optional SNBT payload for the server action handler.",
                        field,
                        190);
            }
        }
        var hoverKinds =
                new ArrayList<>(
                        List.of(HoverKind.NONE, HoverKind.TEXT, HoverKind.ITEM, HoverKind.ENTITY));
        if (original.getHoverEvent() != null && hoverKind == HoverKind.KEEP)
            hoverKinds.add(HoverKind.KEEP);
        row(
                "On hover",
                "Show a tooltip when the reader points at the selected words.",
                dropdown(hoverKind.label, hoverKinds, k -> k.label, k -> hoverKind = k),
                180);
        if (hoverKind == HoverKind.TEXT)
            row(
                    "Tooltip text",
                    "Supports colors and formatting.",
                    action(
                            hoverText.getString().isEmpty()
                                    ? "Write tooltip..."
                                    : "Edit tooltip...",
                            () ->
                                    mc.gui.setScreen(
                                            new GuiStyledTextEditor(
                                                    this,
                                                    "Hover text",
                                                    hoverText,
                                                    1024,
                                                    v -> hoverText = v))),
                    180);
        if (hoverKind == HoverKind.ITEM) {
            row(
                    "Tooltip item",
                    "Choose an item to show its name and tooltip.",
                    action(
                            hoverItem.isEmpty()
                                    ? "Choose item..."
                                    : hoverItem.getHoverName().getString(),
                            () ->
                                    mc.gui.setScreen(
                                            new GuiTypeListSelector(
                                                    this,
                                                    Component.literal("Hover item"),
                                                    v -> {
                                                        hoverItem = v.copy();
                                                        return this;
                                                    },
                                                    BuiltInRegistries.ITEM.stream()
                                                            .map(ItemStack::new)))),
                    180);
            if (!hoverItem.isEmpty())
                row(
                        "Item details",
                        "Edit the tooltip item's name, lore and components.",
                        action(
                                "Edit item...",
                                () ->
                                        mc.gui.setScreen(
                                                new GuiItemStackModifier(
                                                        this,
                                                        hoverItem.copy(),
                                                        v -> hoverItem = v.copy()))),
                        180);
        }
        if (hoverKind == HoverKind.ENTITY) {
            row(
                    "Entity type",
                    "The entity shown in the hover tooltip.",
                    action(
                            hoverEntity == null
                                    ? "Choose entity..."
                                    : hoverEntity.getDescription().getString(),
                            () -> {
                                Map<Identifier, String> entries = new HashMap<>();
                                BuiltInRegistries.ENTITY_TYPE.forEach(
                                        e ->
                                                entries.put(
                                                        BuiltInRegistries.ENTITY_TYPE.getKey(e),
                                                        e.getDescription().getString()));
                                mc.gui.setScreen(
                                        new GuiResourcePicker(
                                                this,
                                                "Hover entity",
                                                entries,
                                                hoverEntity == null
                                                        ? null
                                                        : BuiltInRegistries.ENTITY_TYPE.getKey(
                                                                hoverEntity),
                                                id ->
                                                        hoverEntity =
                                                                BuiltInRegistries.ENTITY_TYPE
                                                                        .getValue(id),
                                                "entity"));
                            }),
                    180);
            row(
                    "Entity UUID",
                    "The entity's unique ID. Required by Minecraft.",
                    text(
                            "Entity UUID",
                            entityId,
                            v -> {
                                entityId = v;
                                validateAction();
                            }),
                    190);
            row(
                    "Entity name",
                    "Optional display name for the tooltip.",
                    text("Entity name", entityName, v -> entityName = v),
                    190);
        }
        row(
                "Shift-click text",
                "Optional text inserted into chat by clients/screens supporting shift-click"
                    + " insertion.",
                text("Insertion", insertion, v -> insertion = v),
                190);
        validateAction();
    }

    private ClickEvent clickEvent() {
        String value = clickValues.getOrDefault(clickKind, "");
        return switch (clickKind) {
            case NONE -> null;
            case KEEP -> original.getClickEvent();
            case COMMAND -> {
                if (!value.startsWith("/")
                        || value.trim().length() < 2
                        || value.indexOf('\n') >= 0
                        || value.indexOf('\r') >= 0)
                    throw new IllegalArgumentException("Enter one command starting with /.");
                yield new ClickEvent.RunCommand(value);
            }
            case LINK -> {
                URI uri = URI.create(value.trim());
                if (!("http".equalsIgnoreCase(uri.getScheme())
                                || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null)
                    throw new IllegalArgumentException(
                            "Enter a valid http:// or https:// address.");
                yield new ClickEvent.OpenUrl(uri);
            }
            case PAGE -> {
                int page = Integer.parseInt(value);
                if (page < 1 || page > pageCount)
                    throw new IllegalArgumentException("Enter a page from 1 to " + pageCount + ".");
                yield new ClickEvent.ChangePage(page);
            }
            case COPY -> {
                if (value.isEmpty()) throw new IllegalArgumentException("Enter the text to copy.");
                yield new ClickEvent.CopyToClipboard(value);
            }
            case DIALOG -> {
                if (value.isBlank())
                    throw new IllegalArgumentException("Enter a dialog ID or JSON.");
                yield new ClickEvent.ShowDialog(
                        Dialog.CODEC
                                .parse(
                                        registries.createSerializationContext(JsonOps.INSTANCE),
                                        value.trim().startsWith("{")
                                                        || value.trim().startsWith("\"")
                                                ? JsonParser.parseString(value)
                                                : new com.google.gson.JsonPrimitive(value))
                                .getOrThrow());
            }
            case CUSTOM -> {
                if (value.isBlank()) throw new IllegalArgumentException("Enter an action ID.");
                try {
                    yield new ClickEvent.Custom(
                            Identifier.parse(value),
                            payload.isBlank()
                                    ? Optional.empty()
                                    : Optional.of(
                                            TagParser.create(NbtOps.INSTANCE).parseFully(payload)));
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    throw new IllegalArgumentException("Invalid SNBT payload: " + e.getMessage());
                }
            }
        };
    }

    private HoverEvent hoverEvent() {
        return switch (hoverKind) {
            case NONE -> null;
            case KEEP -> original.getHoverEvent();
            case TEXT -> new HoverEvent.ShowText(hoverText);
            case ITEM -> {
                if (hoverItem.isEmpty())
                    throw new IllegalArgumentException("Choose a tooltip item.");
                yield new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(hoverItem));
            }
            case ENTITY -> {
                if (hoverEntity == null)
                    throw new IllegalArgumentException("Choose an entity type.");
                UUID uuid;
                try {
                    uuid = UUID.fromString(entityId);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Enter a valid entity UUID.");
                }
                if (originalEntity instanceof HoverEvent.ShowEntity original
                        && original.entity().type == hoverEntity
                        && original.entity().uuid.equals(uuid)
                        && original.entity()
                                .name
                                .map(Component::getString)
                                .orElse("")
                                .equals(entityName)) yield original;
                yield new HoverEvent.ShowEntity(
                        new HoverEvent.EntityTooltipInfo(
                                hoverEntity,
                                uuid,
                                entityName.isBlank()
                                        ? Optional.empty()
                                        : Optional.of(Component.literal(entityName))));
            }
        };
    }

    private void validateAction() {
        errors.clear();
        try {
            ClickEvent event = clickEvent();
            if (event != null)
                ClickEvent.CODEC
                        .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), event)
                        .getOrThrow();
        } catch (Exception e) {
            errors.put(
                    "Click value " + clickKind.name(),
                    clickKind == ClickKind.PAGE
                            ? "Enter a page number from 1 to " + pageCount + "."
                            : Objects.requireNonNullElse(
                                    e.getMessage(), "Check the click action."));
        }
        try {
            HoverEvent event = hoverEvent();
            if (event != null)
                HoverEvent.CODEC
                        .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), event)
                        .getOrThrow();
        } catch (Exception e) {
            errors.put(
                    "Entity UUID",
                    Objects.requireNonNullElse(e.getMessage(), "Check the hover details."));
        }
        for (var child : children())
            if (child instanceof EditBox field)
                EETextField.validate(field, errors.get(field.getMessage().getString()));
    }

    protected Style result() {
        return original.withClickEvent(clickEvent())
                .withHoverEvent(hoverEvent())
                .withInsertion(insertion.isEmpty() ? null : insertion);
    }

    public boolean isModified() {
        try {
            return !result().equals(original);
        } catch (Exception e) {
            return true;
        }
    }
}
