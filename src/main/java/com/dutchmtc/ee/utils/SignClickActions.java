package com.dutchmtc.ee.utils;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.world.level.block.entity.SignText;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/** Placed signs execute root events on each line, rather than events on individual letters. */
public final class SignClickActions {
    private SignClickActions() {}

    public enum Kind {
        COMMAND("Run command", "Command", "/say Hello", "Runs immediately as the player who clicks. Relative coordinates start at the sign."),
        LINK("Open link", "Web address", "https://example.com", "Shows an Open link dialog using Minecraft's browser confirmation settings."),
        COPY("Copy text", "Text to copy", "Text", "Shows a dialog with a Copy text button."),
        SUGGEST("Suggest command", "Command or chat message", "/help", "Sends a clickable chat message. Open chat and click it to fill the chat box without sending."),
        DIALOG("Show dialog", "Dialog ID or JSON", "minecraft:quick_actions", "Opens a registered dialog or an inline dialog definition. Requires a server that supports dialogs."),
        CUSTOM("Custom action", "Action ID", "example:action", "Sends an action ID and optional SNBT payload to a server handler. Requires a matching plugin or mod.");

        public final String label, fieldLabel, example, description;
        Kind(String label, String fieldLabel, String example, String description) {
            this.label = label; this.fieldLabel = fieldLabel; this.example = example; this.description = description;
        }
    }

    public record Draft(Kind kind, String value, String payload) {}

    public static ClickEvent get(SignText text, int slot) {
        return text.getMessages(false).get(slot).getStyle().getClickEvent();
    }

    public static SignText set(SignText text, int slot, ClickEvent event) {
        Component normal = text.getMessages(false).get(slot);
        Component filtered = text.getMessages(true).get(slot);
        return text.asMutable().setLine(slot, normal.copy().withStyle(s -> s.withClickEvent(event)),
                filtered.copy().withStyle(s -> s.withClickEvent(event))).asImmutable();
    }

    public static int count(SignText text) {
        int count = 0;
        for (int slot = 0; slot < 4; slot++) if (get(text, slot) != null) count++;
        return count;
    }

    public static boolean executable(ClickEvent event) {
        return event instanceof ClickEvent.RunCommand || event instanceof ClickEvent.ShowDialog || event instanceof ClickEvent.Custom;
    }

    /** Keep click behavior separate from formatting, so selecting/resetting letters cannot erase an action. */
    public static Component textOnly(Component component) {
        var result = Component.empty();
        component.visit((style, value) -> {
            result.append(Component.literal(value).withStyle(style.withClickEvent(null)));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public static Component withText(SignText text, int slot, Component replacement) {
        return replacement.copy().withStyle(s -> s.withClickEvent(get(text, slot)));
    }

    public static ClickEvent create(Draft draft, HolderLookup.Provider registries) {
        if (draft.value().isBlank()) throw new IllegalArgumentException("Enter " + draft.kind().fieldLabel.toLowerCase() + ".");
        String value = draft.value();
        ClickEvent event = switch (draft.kind()) {
            case COMMAND -> new ClickEvent.RunCommand(value.trim());
            case LINK -> {
                URI uri;
                try { uri = URI.create(value.trim()); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("Enter a valid https:// or http:// address."); }
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getHost().isBlank())
                    throw new IllegalArgumentException("Enter a valid https:// or http:// address.");
                yield wrap(new ClickEvent.OpenUrl(uri));
            }
            case COPY -> wrap(new ClickEvent.CopyToClipboard(value));
            case SUGGEST -> suggestion(value, registries);
            case DIALOG -> {
                var input = value.trim().startsWith("{") ? JsonParser.parseString(value) : new com.google.gson.JsonPrimitive(value.trim());
                yield new ClickEvent.ShowDialog(Dialog.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), input).getOrThrow());
            }
            case CUSTOM -> {
                Optional<net.minecraft.nbt.Tag> payload = Optional.empty();
                if (!draft.payload().isBlank()) {
                    try { payload = Optional.of(TagParser.create(NbtOps.INSTANCE).parseFully(draft.payload())); }
                    catch (Exception e) { throw new IllegalArgumentException("Invalid payload: " + e.getMessage()); }
                }
                yield new ClickEvent.Custom(net.minecraft.resources.Identifier.parse(value.trim()), payload);
            }
        };
        // Use vanilla's codec validation before an invalid action can reach an item or crash rendering.
        ClickEvent.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), event).getOrThrow();
        return event;
    }

    private static ClickEvent.ShowDialog wrap(ClickEvent event) {
        Kind kind = clientKind(event);
        String value = clientValue(event);
        String button = kind == Kind.SUGGEST ? "Put in chat" : kind.label;
        var common = new CommonDialogData(Component.literal(kind.label), Optional.empty(), true, false,
                DialogAction.CLOSE, List.of(new PlainMessage(Component.literal(value), 300)), List.of());
        var dialog = new ConfirmationDialog(common,
                new ActionButton(new CommonButtonData(Component.literal(button), 150), Optional.of(new StaticAction(event))),
                new ActionButton(new CommonButtonData(Component.literal("Close"), 150), Optional.empty()));
        return new ClickEvent.ShowDialog(Holder.direct(dialog));
    }

    // A dialog's suggest_command event only inserts into its previous screen, which is normally
    // null when opened from a sign. A native tellraw suggestion works on unmodified clients.
    private static ClickEvent.RunCommand suggestion(String value, HolderLookup.Provider registries) {
        Component message = Component.literal("Put in chat: " + value).withStyle(s -> s.withColor(0xFFFF55)
                .withUnderlined(true).withClickEvent(new ClickEvent.SuggestCommand(value)));
        var encoded = ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), message).getOrThrow();
        return new ClickEvent.RunCommand("/tellraw @s " + encoded);
    }

    private static Kind clientKind(ClickEvent event) {
        if (event instanceof ClickEvent.OpenUrl) return Kind.LINK;
        if (event instanceof ClickEvent.CopyToClipboard) return Kind.COPY;
        if (event instanceof ClickEvent.SuggestCommand) return Kind.SUGGEST;
        return null;
    }

    private static String clientValue(ClickEvent event) {
        return switch (event) {
            case ClickEvent.OpenUrl link -> link.uri().toString();
            case ClickEvent.CopyToClipboard copy -> copy.value();
            case ClickEvent.SuggestCommand suggest -> suggest.command();
            default -> "";
        };
    }

    public static Draft describe(ClickEvent event, HolderLookup.Provider registries) {
        if (event == null) return new Draft(Kind.COMMAND, "", "");
        if (event instanceof ClickEvent.RunCommand command) {
            if (command.command().startsWith("/tellraw @s ")) {
                try {
                    var message = ComponentSerialization.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE),
                            JsonParser.parseString(command.command().substring(12))).getOrThrow();
                    if (message.getStyle().getClickEvent() instanceof ClickEvent.SuggestCommand suggest
                            && suggestion(suggest.command(), registries).equals(command))
                        return new Draft(Kind.SUGGEST, suggest.command(), "");
                } catch (Exception ignored) { /* Other tellraw commands remain normal command actions. */ }
            }
            return new Draft(Kind.COMMAND, command.command(), "");
        }
        if (event instanceof ClickEvent.Custom custom)
            return new Draft(Kind.CUSTOM, custom.id().toString(), custom.payload().map(Object::toString).orElse(""));
        // Convert imported direct URL/copy/suggestion events to executable sign actions when edited.
        if (clientKind(event) != null) return new Draft(clientKind(event), clientValue(event), "");
        if (event instanceof ClickEvent.ShowDialog show) {
            if (show.dialog().unwrapKey().isPresent())
                return new Draft(Kind.DIALOG, show.dialog().unwrapKey().get().identifier().toString(), "");
            if (show.dialog().value() instanceof ConfirmationDialog dialog
                    && dialog.yesButton().action().orElse(null) instanceof StaticAction action
                    && clientKind(action.value()) != null && wrap(action.value()).dialog().value().equals(dialog))
                return new Draft(clientKind(action.value()), clientValue(action.value()), "");
            String encoded = Dialog.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), show.dialog()).getOrThrow().toString();
            if (encoded.startsWith("\"")) encoded = JsonParser.parseString(encoded).getAsString();
            return new Draft(Kind.DIALOG, encoded, "");
        }
        return null;
    }
}
