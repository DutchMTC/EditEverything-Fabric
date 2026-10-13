package com.dutchmtc.ee.utils;

import com.google.common.collect.HashMultimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Only the profile changes; imported properties and unrelated item data survive. */
public final class HeadEditorData {
    private final ItemStack original, draft, preview;
    private String player, texture;
    private boolean custom;

    public HeadEditorData(ItemStack stack) {
        original = stack.copy();
        draft = stack.copy();
        preview = stack.copyWithCount(1);
        var profile = stack.get(DataComponents.PROFILE);
        var partial = profile == null ? null : profile.partialProfile();
        player =
                partial == null
                        ? ""
                        : partial.name() != null && !partial.name().isEmpty()
                                ? partial.name()
                                : partial.id() == null ? "" : partial.id().toString();
        texture = textureUrl();
        custom = !texture.isEmpty();
    }

    public String playerInput() {
        return player;
    }

    public String textureInput() {
        return texture;
    }

    public ItemStack preview() {
        return preview;
    }

    public ItemStack result() {
        return draft.copy();
    }

    public boolean changed() {
        return !ItemStack.matches(original, draft);
    }

    public boolean pending(boolean custom, String input) {
        return this.custom != custom || !input.equals(custom ? texture : player);
    }

    public static String error(boolean custom, String input) {
        if (input.isBlank())
            return custom ? "Enter a texture URL or value." : "Enter a player name or UUID.";
        try {
            profile(custom, input);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    public void choose(boolean custom, String input) {
        if (pending(custom, input)) setProfile(profile(custom, input));
        this.custom = custom;
        if (custom) texture = input;
        else player = input;
    }

    public void usePlayer(GameProfile profile) {
        player = profile.name();
        custom = false;
        setProfile(
                profile.properties().get("textures").isEmpty()
                        ? ResolvableProfile.createUnresolved(profile.id())
                        : ResolvableProfile.createResolved(profile));
    }

    private void setProfile(ResolvableProfile profile) {
        draft.set(DataComponents.PROFILE, profile);
        preview.set(DataComponents.PROFILE, profile);
    }

    public String textureUrl() {
        var profile = draft.get(DataComponents.PROFILE);
        if (profile == null) return "";
        for (var property : profile.partialProfile().properties().get("textures")) {
            try {
                return skinUrl(property.value());
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    public static ResolvableProfile profile(boolean custom, String input) {
        String text = input.trim();
        if (!custom) {
            if (text.matches("[A-Za-z0-9_]{1,16}")) return ResolvableProfile.createUnresolved(text);
            String uuid =
                    text.matches("[a-fA-F0-9]{32}")
                            ? text.substring(0, 8)
                                    + "-"
                                    + text.substring(8, 12)
                                    + "-"
                                    + text.substring(12, 16)
                                    + "-"
                                    + text.substring(16, 20)
                                    + "-"
                                    + text.substring(20)
                            : text;
            if (!uuid.matches(
                    "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
                throw new IllegalArgumentException(
                        "Use a player name (1–16 letters, numbers or underscores) or a full UUID.");
            return ResolvableProfile.createUnresolved(UUID.fromString(uuid));
        }
        String value;
        if (text.matches("[a-fA-F0-9]{32,64}"))
            text = "https://textures.minecraft.net/texture/" + text;
        if (text.startsWith("https://") || text.startsWith("http://")) {
            requireSkinUrl(text);
            JsonObject skin = new JsonObject();
            skin.addProperty("url", text);
            JsonObject textures = new JsonObject();
            textures.add("SKIN", skin);
            JsonObject root = new JsonObject();
            root.add("textures", textures);
            value =
                    Base64.getEncoder()
                            .encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
        } else {
            value = text;
            try {
                requireSkinUrl(skinUrl(value));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Use a Minecraft skin URL, texture hash or valid Base64 texture value.");
            }
        }
        var props = HashMultimap.<String, Property>create();
        props.put("textures", new Property("textures", value));
        return ResolvableProfile.createResolved(
                new GameProfile(
                        UUID.nameUUIDFromBytes(
                                ("EE:head:" + value).getBytes(StandardCharsets.UTF_8)),
                        "",
                        new PropertyMap(props)));
    }

    private static String skinUrl(String value) {
        var root =
                JsonParser.parseString(
                                new String(
                                        Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
                        .getAsJsonObject();
        return root.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
    }

    private static void requireSkinUrl(String value) {
        try {
            URI url = URI.create(value);
            if (!("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                    || !"textures.minecraft.net".equalsIgnoreCase(url.getHost())
                    || url.getUserInfo() != null
                    || url.getPort() != -1
                    || url.getQuery() != null
                    || url.getFragment() != null
                    || url.getPath() == null
                    || !url.getPath().matches("/texture/[a-fA-F0-9]{32,64}"))
                throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new IllegalArgumentException("Use a textures.minecraft.net skin URL.");
        }
    }

    public static byte[] downloadSkin(String url, Proxy proxy) throws java.io.IOException {
        requireSkinUrl(url);
        var connection = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection(proxy);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200)
                throw new java.io.IOException("Skin download failed");
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(2 * 1024 * 1024 + 1);
                if (bytes.length > 2 * 1024 * 1024
                        || bytes.length < 8
                        || bytes[0] != (byte) 137
                        || bytes[1] != 80
                        || bytes[2] != 78
                        || bytes[3] != 71) throw new java.io.IOException("Invalid skin image");
                return bytes;
            }
        } finally {
            connection.disconnect();
        }
    }
}
