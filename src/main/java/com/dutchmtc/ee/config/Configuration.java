package com.dutchmtc.ee.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.utils.SyncList;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.resources.Identifier;

public class Configuration {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ConfigData data = new ConfigData();
    private Path configPath;

    private transient Optional<Identifier> eventWorldIdParsed = Optional.empty();

    private Consumer<List<String>> customItemsCallback = (s) -> {
    };

    private final SyncList<String> customItems = new SyncList<String>(new ArrayList<>()) {
        @Override
        protected void onUpdate(List<String> data) {
            customItemsCallback.accept(data);
        }
    };

    public static class ConfigData {
        public List<String> items = new ArrayList<>();
        public boolean disableToolTip = false;
        /**
         * If true and {@link #eventWorldId} is set, player attributes are reset when leaving that world.
         */
        public boolean resetPlayerAttributesOnEventWorldExit = true;
        /**
         * Dimension identifier for the "event world" (e.g. "minecraft:overworld" or "my_mod:event").
         * When empty, the attribute reset feature is disabled.
         */
        public String eventWorldId = "";
    }

    public boolean doesDisableToolTip() {
        return data.disableToolTip;
    }

    public boolean doesResetPlayerAttributesOnEventWorldExit() {
        return data.resetPlayerAttributesOnEventWorldExit;
    }

    public Optional<Identifier> getEventWorldId() {
        return eventWorldIdParsed;
    }

    public SyncList<String> getCustomitems() {
        return customItems;
    }

    public void addCustomItemsCallback(Consumer<List<String>> consumer) {
        this.customItemsCallback = customItemsCallback.andThen(consumer);
    }

    public void save() {
        data.items = new ArrayList<>(customItems);
        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setDoesDisableToolTip(boolean doesDisableToolTip) {
        data.disableToolTip = doesDisableToolTip;
    }

    public void sync(File path) {
        sync(path.toPath());
    }

    public void sync(Path path) {
        this.configPath = path;
        File file = path.toFile();
        
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                data = GSON.fromJson(reader, ConfigData.class);
                if (data == null) data = new ConfigData();
            } catch (Exception e) {
                EEMod.LOGGER.error("Failed to load config", e);
                data = new ConfigData();
            }
        } else {
            // Defaults
            data.items = new ArrayList<>(List.of(EEMod.DEFAULT_CUSTOM_ITEMS));
        }

        eventWorldIdParsed = Optional.ofNullable(data.eventWorldId)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Identifier::tryParse)
                .filter(id -> id != null);
        
        // Sync to internal list
        customItems.applyUpdate(lst -> {
            lst.clear();
            if (data.items != null) {
                lst.addAll(data.items);
            }
        });
        
        save();
    }
}
