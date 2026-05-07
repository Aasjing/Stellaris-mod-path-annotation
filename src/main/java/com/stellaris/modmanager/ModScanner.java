package com.stellaris.modmanager;

import com.intellij.openapi.diagnostic.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class ModScanner {

    private static final Logger LOG = Logger.getInstance(ModScanner.class);

    private static final String WORKSHOP_RELATIVE_PATH = "steamapps/workshop/content/281990";

    private static final List<String> WINDOWS_STEAM_PATHS = Arrays.asList(
            "C:/Program Files (x86)/Steam",
            "C:/Program Files/Steam",
            "D:/Steam",
            "E:/Steam"
    );

    private static final List<String> MACOS_STEAM_PATHS = Arrays.asList(
            System.getProperty("user.home") + "/Library/Application Support/Steam"
    );

    private static final List<String> LINUX_STEAM_PATHS = Arrays.asList(
            System.getProperty("user.home") + "/.steam/steam",
            System.getProperty("user.home") + "/.local/share/Steam",
            System.getProperty("user.home") + "/.var/app/com.valvesoftware.Steam/.local/share/Steam"
    );

    private static final List<String> THUMBNAIL_NAMES = Arrays.asList(
            "thumbnail.png", "thumbnail.jpg", "thumbnail.jpeg",
            "thumb.png", "thumb.jpg", "thumb.jpeg",
            "preview.png", "preview.jpg"
    );

    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern SINGLE_QUOTED_VALUE_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*'([^']*)'");

    public static List<String> findWorkshopDirectories() {
        List<String> steamRoots = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            steamRoots.addAll(findWindowsSteamRoots());
        } else if (os.contains("mac")) {
            steamRoots.addAll(MACOS_STEAM_PATHS);
        } else {
            steamRoots.addAll(LINUX_STEAM_PATHS);
        }

        List<String> validWorkshopPaths = new ArrayList<>();
        for (String root : steamRoots) {
            Path workshopPath = Paths.get(root, WORKSHOP_RELATIVE_PATH);
            if (Files.isDirectory(workshopPath)) {
                validWorkshopPaths.add(workshopPath.toString());
                LOG.info("Found workshop directory: " + workshopPath);
            }
        }

        if (validWorkshopPaths.isEmpty()) {
            LOG.warn("No Stellaris workshop directory found in any known Steam library location");
        }

        return validWorkshopPaths;
    }

    private static List<String> findWindowsSteamRoots() {
        List<String> roots = new ArrayList<>();

        for (String defaultPath : WINDOWS_STEAM_PATHS) {
            if (Files.isDirectory(Paths.get(defaultPath))) {
                roots.add(defaultPath);
            }
        }

        Path libraryFoldersVdf = findLibraryFoldersVdf(roots);
        if (libraryFoldersVdf != null) {
            roots.addAll(parseWindowsLibraryFolders(libraryFoldersVdf));
        }

        return roots;
    }

    private static Path findLibraryFoldersVdf(List<String> existingRoots) {
        for (String root : existingRoots) {
            Path vdf = Paths.get(root, "steamapps/libraryfolders.vdf");
            if (Files.isRegularFile(vdf)) {
                return vdf;
            }
        }
        return null;
    }

    private static List<String> parseWindowsLibraryFolders(Path vdfPath) {
        List<String> paths = new ArrayList<>();
        Pattern pathPattern = Pattern.compile("\"path\"\\s+\"([^\"]+)\"");

        try {
            String content = Files.readString(vdfPath);
            Matcher matcher = pathPattern.matcher(content);
            while (matcher.find()) {
                String libraryPath = matcher.group(1).replace("\\\\", "\\");
                paths.add(libraryPath);
            }
        } catch (IOException e) {
            LOG.warn("Failed to read libraryfolders.vdf: " + vdfPath, e);
        }

        return paths;
    }

    public static List<ModInfo> scanMods(String workshopPath) {
        List<ModInfo> mods = new ArrayList<>();

        try (Stream<Path> paths = Files.list(Paths.get(workshopPath))) {
            List<Path> modFolders = paths
                    .filter(Files::isDirectory)
                    .collect(Collectors.toList());

            for (Path modFolder : modFolders) {
                Path descriptorFile = modFolder.resolve("descriptor.mod");
                if (Files.exists(descriptorFile)) {
                    ModInfo modInfo = parseDescriptorFile(modFolder, descriptorFile);
                    if (modInfo != null) {
                        mods.add(modInfo);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to scan mods in: " + workshopPath, e);
        }

        return mods;
    }

    private static ModInfo parseDescriptorFile(Path modFolder, Path descriptorFile) {
        try {
            String content = Files.readString(descriptorFile);

            String modName = extractValue(content, "name");
            String version = extractValue(content, "version");
            String supportedVersion = extractValue(content, "supported_version");

            String folderName = modFolder.getFileName().toString();

            if (modName == null || modName.isEmpty()) {
                modName = folderName;
            }

            String thumbnailPath = findThumbnail(modFolder);
            long folderSize = calculateFolderSize(modFolder);
            long lastModified = getLastModified(modFolder);

            return new ModInfo(
                    folderName,
                    modName,
                    version != null ? version : "N/A",
                    supportedVersion != null ? supportedVersion : "N/A",
                    modFolder.toString(),
                    thumbnailPath,
                    folderSize,
                    lastModified
            );
        } catch (IOException e) {
            LOG.warn("Failed to parse descriptor file: " + descriptorFile, e);
            return null;
        }
    }

    private static String findThumbnail(Path modFolder) {
        for (String name : THUMBNAIL_NAMES) {
            Path thumbnailFile = modFolder.resolve(name);
            if (Files.isRegularFile(thumbnailFile)) {
                return thumbnailFile.toString();
            }
        }
        return null;
    }

    private static long calculateFolderSize(Path folder) {
        try (Stream<Path> stream = Files.walk(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private static long getLastModified(Path folder) {
        try {
            return Files.getLastModifiedTime(folder).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String extractValue(String content, String key) {
        Matcher matcher = QUOTED_VALUE_PATTERN.matcher(content);
        while (matcher.find()) {
            if (key.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }

        matcher = SINGLE_QUOTED_VALUE_PATTERN.matcher(content);
        while (matcher.find()) {
            if (key.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }

        return null;
    }
}
