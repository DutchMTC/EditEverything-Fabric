package com.dutchmtc.ee.mobdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FeatureSetRepository {
    private static final Identifier FEATURE_SETS_ID = Identifier.fromNamespaceAndPath("ee", "mobdata/feature_sets.json");

    private static @Nullable JsonObject cached;

    private FeatureSetRepository() {
    }

    public static @Nullable JsonObject getFeatureSets() {
        if (cached != null) {
            return cached;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }
        try {
            var opt = mc.getResourceManager().getResource(FEATURE_SETS_ID);
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

    public static List<String> getFeatureSetFieldRefs(String featureSetKey) {
        JsonObject root = getFeatureSets();
        if (root == null) return Collections.emptyList();
        JsonObject sets = root.getAsJsonObject("feature_sets");
        if (sets == null) return Collections.emptyList();
        if (!sets.has(featureSetKey) || !sets.get(featureSetKey).isJsonArray()) return Collections.emptyList();
        JsonArray arr = sets.getAsJsonArray(featureSetKey);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            if (arr.get(i).isJsonPrimitive()) {
                out.add(arr.get(i).getAsString());
            }
        }
        return out;
    }
}
