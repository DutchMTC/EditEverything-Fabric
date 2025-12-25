package fr.atesab.act.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.atesab.act.ACTMod;
import fr.atesab.act.utils.SyncList;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Configuration {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ConfigData data = new ConfigData();
    private Path configPath;

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
    }

    public boolean doesDisableToolTip() {
        return data.disableToolTip;
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
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Defaults
            data.items = new ArrayList<>(List.of(ACTMod.DEFAULT_CUSTOM_ITEMS));
        }
        
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
