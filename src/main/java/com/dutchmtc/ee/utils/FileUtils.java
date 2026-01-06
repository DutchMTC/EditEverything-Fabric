package com.dutchmtc.ee.utils;

import com.dutchmtc.ee.internalcommand.InternalCommandModule;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

@InternalCommandModule(name = "file")
public class FileUtils {

    /**
     * units to format {@link #sizeUnit(long, String)}
     */
    public static final String[] UNITS = {"", "k", "M", "G", "T", "P", "E", "Z", "Y"};

    /**
     * format a number with an unit
     *
     * @param size     the number
     * @param unitName the unit to append to the prefix (k, M, G, etc.)
     * @return the formatted string
     */
    public static String sizeUnit(long size, String unitName) {
        var c = size;
        var i = 0;
        while (c >= 1000L && i < UNITS.length - 1) {
            c /= 1000L;
            i++;
        }

        return c + UNITS[i] + unitName;
    }

    /**
     * get the extension of a file
     *
     * @param f the file
     * @return the extension
     */
    public static String fileExt(File f) {
        if (f.exists() && f.isDirectory())
            return "";
        String n = f.getName();

        int index = n.lastIndexOf('.');

        return index == -1 ? "" : n.substring(index + 1);
    }

    /**
     * get a stream from a file in a mod jar
     *
     * @param modId the mod id
     * @param path  the path in the jar
     * @return the stream
     * @throws IOException error while loading from the jar
     */
    public static InputStream fetchFromModJar(String modId, String path) throws IOException {
        var container = FabricLoader.getInstance().getModContainer(modId)
                .orElseThrow(() -> new RuntimeException("Can't find modid " + modId));
        var pathInMod = container.findPath(path).orElseThrow(() -> new IOException("File not found in mod: " + path));
        return Files.newInputStream(pathInMod);
    }

    private FileUtils() {
    }

}
