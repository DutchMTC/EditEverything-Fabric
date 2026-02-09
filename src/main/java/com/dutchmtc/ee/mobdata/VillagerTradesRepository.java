package com.dutchmtc.ee.mobdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class VillagerTradesRepository {
    private static final Identifier VILLAGER_TRADES_ID = Identifier.fromNamespaceAndPath("ee", "mobdata/villager_trades.json");

    private static @Nullable JsonObject cached;

    private VillagerTradesRepository() {
    }

    public static @Nullable JsonObject getVillagerTrades() {
        if (cached != null) {
            return cached;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }
        try {
            var opt = mc.getResourceManager().getResource(VILLAGER_TRADES_ID);
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

    public static @Nullable JsonObject getOffersSchema() {
        JsonObject root = getVillagerTrades();
        if (root == null) return null;
        if (!root.has("offers") || !root.get("offers").isJsonObject()) return null;
        return root.getAsJsonObject("offers");
    }

    public static @Nullable JsonObject getOfferEntryFieldSchema(String key) {
        JsonObject offers = getOffersSchema();
        if (offers == null) return null;
        if (!offers.has("entry_fields") || !offers.get("entry_fields").isJsonArray()) return null;
        JsonArray fields = offers.getAsJsonArray("entry_fields");
        for (JsonElement el : fields) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("key") && key.equals(obj.get("key").getAsString())) {
                return obj;
            }
        }
        return null;
    }
}

