package com.dutchmtc.ee.mobdata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
public final class MobDataRepository {
    private static final Identifier MOB_FIELDS_ID = Identifier.fromNamespaceAndPath("ee", "mobdata/mob_fields.json");

    private static @Nullable JsonObject cached;

    private MobDataRepository() {
    }

    public static @Nullable JsonObject getMobFields() {
        if (cached != null) {
            return cached;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }
        try {
            var opt = mc.getResourceManager().getResource(MOB_FIELDS_ID);
            if (opt.isEmpty()) {
                return null;
            }
            Resource res = opt.get();
            try (Reader reader = new InputStreamReader(res.open(), StandardCharsets.UTF_8)) {
                cached = JsonParser.parseReader(reader).getAsJsonObject();
                return cached;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static @Nullable JsonObject getEntity(String entityPath) {
        JsonObject root = getMobFields();
        if (root == null) return null;
        JsonObject entities = root.getAsJsonObject("entities");
        if (entities == null) return null;
        if (!entities.has(entityPath) || !entities.get(entityPath).isJsonObject()) return null;
        return entities.getAsJsonObject(entityPath);
    }

    public static @Nullable JsonObject getEntityFields() {
        JsonObject root = getMobFields();
        if (root == null) return null;
        if (!root.has("entity_fields") || !root.get("entity_fields").isJsonObject()) return null;
        return root.getAsJsonObject("entity_fields");
    }

    public static @Nullable JsonObject getEntityFieldDef(String key) {
        JsonObject entityFields = getEntityFields();
        if (entityFields == null) return null;
        if (!entityFields.has(key) || !entityFields.get(key).isJsonObject()) return null;
        return entityFields.getAsJsonObject(key);
    }
}
