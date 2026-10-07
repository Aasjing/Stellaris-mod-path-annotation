package com.stellaris.modmanager;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ModPathSettings {

    private static final Logger LOG = Logger.getInstance(ModPathSettings.class);

    private static final Path SETTINGS_FILE =
            Paths.get(System.getProperty("user.home"), ".stellaris-mod-manager", "settings.properties");
    private static final String DIRECTORY_KEY_PREFIX = "workshop.directory.";
    private static final String LEGACY_DIRECTORY_KEY = "workshop.directory";

    private static volatile List<String> workshopDirectories = readWorkshopDirectories();

    public static List<String> getWorkshopDirectories() {
        return workshopDirectories;
    }

    public static void setWorkshopDirectories(List<String> paths) {
        List<String> cleaned = new ArrayList<>();
        for (String path : paths) {
            if (path != null && !path.isBlank() && !cleaned.contains(path)) {
                cleaned.add(path);
            }
        }
        workshopDirectories = List.copyOf(cleaned);
        save();
    }

    public static boolean addWorkshopDirectory(String path) {
        if (path == null || path.isBlank() || workshopDirectories.contains(path)) {
            return false;
        }
        List<String> paths = new ArrayList<>(workshopDirectories);
        paths.add(path);
        setWorkshopDirectories(paths);
        return true;
    }

    public static void removeWorkshopDirectory(String path) {
        List<String> paths = new ArrayList<>(workshopDirectories);
        if (paths.remove(path)) {
            setWorkshopDirectories(paths);
        }
    }

    public static void clearWorkshopDirectories() {
        setWorkshopDirectories(List.of());
    }

    private static List<String> readWorkshopDirectories() {
        Properties properties = load();
        List<String> paths = new ArrayList<>();

        String legacy = properties.getProperty(LEGACY_DIRECTORY_KEY, "").trim();
        if (!legacy.isEmpty()) {
            paths.add(legacy);
        }

        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(DIRECTORY_KEY_PREFIX))
                .sorted(Comparator.comparingInt(ModPathSettings::keyIndex))
                .forEach(key -> {
                    String path = properties.getProperty(key, "").trim();
                    if (!path.isEmpty() && !paths.contains(path)) {
                        paths.add(path);
                    }
                });

        return List.copyOf(paths);
    }

    private static int keyIndex(String key) {
        try {
            return Integer.parseInt(key.substring(DIRECTORY_KEY_PREFIX.length()));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static Properties load() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(SETTINGS_FILE)) {
            return properties;
        }
        try (var in = Files.newInputStream(SETTINGS_FILE)) {
            properties.load(in);
        } catch (IOException e) {
            LOG.info("Failed to read settings: " + SETTINGS_FILE, e);
        }
        return properties;
    }

    private static void save() {
        Properties properties = load();
        for (String key : new ArrayList<>(properties.stringPropertyNames())) {
            if (key.startsWith(DIRECTORY_KEY_PREFIX) || key.equals(LEGACY_DIRECTORY_KEY)) {
                properties.remove(key);
            }
        }
        for (int index = 0; index < workshopDirectories.size(); index++) {
            properties.setProperty(DIRECTORY_KEY_PREFIX + index, workshopDirectories.get(index));
        }
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (var out = Files.newOutputStream(SETTINGS_FILE)) {
                properties.store(out, "Stellaris mod manager - settings");
            }
        } catch (IOException e) {
            LOG.info("Failed to save settings: " + SETTINGS_FILE, e);
        }
    }
}
