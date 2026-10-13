package com.dutchmtc.ee.gui.modifier.mob;

import com.dutchmtc.ee.gui.components.EESettingsScreen;
import com.dutchmtc.ee.gui.components.EETextField;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;

/** Online player selection plus explicit UUIDs, with no name-to-UUID network guesses. */
public final class GuiMobPlayerEditor extends EESettingsScreen<UUID> {
    private record Player(UUID id, String name) {}

    private final UUID original;
    private UUID chosen;
    private String draft;
    private final boolean mobReference;
    private java.util.function.Function<UUID, String> restriction = id -> null;

    public GuiMobPlayerEditor restrict(java.util.function.Function<UUID, String> check) {
        restriction = check;
        return this;
    }

    public GuiMobPlayerEditor(Screen parent, String title, UUID current, Consumer<UUID> setter) {
        this(parent, title, current, setter, false);
    }

    public GuiMobPlayerEditor(
            Screen parent,
            String title,
            UUID current,
            Consumer<UUID> setter,
            boolean mobReference) {
        super(parent, title, setter);
        this.mobReference = mobReference;
        original = chosen = current;
        draft = current == null ? "" : current.toString();
    }

    @Override
    protected void buildRows() {
        var players = new ArrayList<Player>();
        players.add(new Player(null, "None"));
        if (mobReference && mc.level != null && mc.player != null) {
            for (var mob :
                    mc.level.getEntitiesOfClass(
                            net.minecraft.world.entity.Mob.class,
                            mc.player.getBoundingBox().inflate(64)))
                players.add(
                        new Player(
                                mob.getUUID(),
                                mob.getName().getString()
                                        + " · "
                                        + mob.getUUID().toString().substring(0, 8)));
        } else {
            if (mc.player != null) players.add(new Player(mc.player.getUUID(), "Me"));
            if (mc.getConnection() != null)
                for (var player : mc.getConnection().getOnlinePlayers()) {
                    var profile = player.getProfile();
                    if (mc.player == null || !profile.id().equals(mc.player.getUUID()))
                        players.add(new Player(profile.id(), profile.name()));
                }
        }
        String label = chosen == null ? "None" : name(chosen);
        row(
                mobReference ? "Mob" : "Player",
                mobReference
                        ? "Choose a nearby mob. None clears the reference."
                        : "Choose yourself or a player on this server. None clears the reference.",
                dropdown(
                        label,
                        players,
                        Player::name,
                        p -> {
                            chosen = p.id;
                            draft = chosen == null ? "" : chosen.toString();
                            values.put("uuid", draft);
                            errors.remove("uuid");
                        }),
                156);
        var input =
                text(
                        "uuid",
                        draft,
                        v -> {
                            draft = v;
                            try {
                                chosen =
                                        v.isBlank()
                                                ? null
                                                : com.dutchmtc.ee.mobdata.MobPropertyValues
                                                        .parseUUID(v);
                                errors.remove("uuid");
                            } catch (IllegalArgumentException ex) {
                                errors.put(
                                        "uuid",
                                        "Enter a complete UUID, or choose "
                                                + (mobReference ? "a mob" : "a player")
                                                + " above.");
                            }
                        });
        input.setMaxLength(128);
        input.setHint(Component.literal("Optional UUID").withColor(0xAEBBB5));
        EETextField.validate(input, errors.get("uuid"));
        row(
                "UUID",
                mobReference
                        ? "UUID of the mob that owns this entity."
                        : "Use a UUID to choose an offline player. A player name alone is not a"
                              + " UUID.",
                input,
                200);
    }

    @Override
    protected int preferredWidth() {
        return 320;
    }

    private String name(UUID id) {
        if (mobReference) {
            if (mc.level != null)
                for (var entity : mc.level.entitiesForRendering())
                    if (entity.getUUID().equals(id)) return entity.getName().getString();
            return "Mob UUID";
        }
        if (mc.player != null && id.equals(mc.player.getUUID())) return "Me";
        if (mc.getConnection() != null)
            for (var info : mc.getConnection().getOnlinePlayers())
                if (info.getProfile().id().equals(id)) return info.getProfile().name();
        return "Offline player";
    }

    @Override
    protected UUID result() {
        return chosen;
    }

    @Override
    protected String invalidReason() {
        String error = super.invalidReason();
        return error != null ? error : restriction.apply(chosen);
    }

    @Override
    public boolean isModified() {
        return !Objects.equals(original, chosen) || errors.containsKey("uuid");
    }
}
