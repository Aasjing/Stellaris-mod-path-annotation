package com.stellaris.modmanager;

import com.intellij.openapi.diagnostic.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.*;

public class ModScanner {

    private static final Logger LOG = Logger.getInstance(ModScanner.class);

    private static final String WORKSHOP_APP_ID = "281990";
    private static final String WORKSHOP_RELATIVE_PATH = "steamapps/workshop/content/" + WORKSHOP_APP_ID;
    private static final String WORKSHOP_ACF_NAME = "appworkshop_" + WORKSHOP_APP_ID + ".acf";
    private static final String STEAM_REGISTRY_KEY = "HKCU\\Software\\Valve\\Steam";
    private static final long REGISTRY_QUERY_TIMEOUT_SECONDS = 3;

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
    private static final Pattern REGISTRY_STEAM_PATH_PATTERN =
            Pattern.compile("^\\s*SteamPath\\s+REG_SZ\\s+(.+?)\\s*$");
    private static final Pattern WORKSHOP_ITEM_ID_PATTERN = Pattern.compile("^\\s*\"(\\d+)\"\\s*$");
    private static final Pattern WORKSHOP_ITEM_SIZE_PATTERN = Pattern.compile("\"size\"\\s+\"(\\d+)\"");
    private static final Pattern WORKSHOP_ITEM_TIME_UPDATED_PATTERN =
            Pattern.compile("\"timeupdated\"\\s+\"(\\d+)\"");

    private record WorkshopItem(long size, long timeUpdated) {
    }

    public static List<String> findWorkshopDirectories() {
        List<String> manualDirectories = findManualWorkshopDirectories();
        if (!manualDirectories.isEmpty()) {
            return manualDirectories;
        }

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
            if (!Files.isDirectory(workshopPath) || containsPath(validWorkshopPaths, workshopPath)) {
                continue;
            }
            validWorkshopPaths.add(workshopPath.toString());
            LOG.info("Found workshop directory: " + workshopPath);
        }

        if (validWorkshopPaths.isEmpty()) {
            LOG.warn("No Stellaris workshop directory found in any known Steam library location");
        }

        return validWorkshopPaths;
    }

    private static List<String> findManualWorkshopDirectories() {
        List<String> directories = new ArrayList<>();
        for (String configured : ModPathSettings.getWorkshopDirectories()) {
            Path resolved = resolveWorkshopDirectory(configured);
            if (resolved == null || containsPath(directories, resolved)) {
                continue;
            }
            directories.add(resolved.toString());
        }
        if (!directories.isEmpty()) {
            LOG.info("Using manually configured workshop directories: " + directories);
        }
        return directories;
    }

    public static Path resolveWorkshopDirectory(String pickedPath) {
        if (pickedPath == null || pickedPath.isBlank()) {
            return null;
        }
        Path picked = Paths.get(pickedPath);
        if (!Files.isDirectory(picked)) {
            LOG.warn("Configured workshop directory is not a directory: " + picked);
            return null;
        }

        if (Files.isRegularFile(picked.resolve("descriptor.mod"))) {
            return picked.getParent();
        }
        if (containsModFolder(picked)) {
            return picked;
        }

        for (String relative : new String[]{
                WORKSHOP_RELATIVE_PATH,
                "workshop/content/" + WORKSHOP_APP_ID,
                "content/" + WORKSHOP_APP_ID}) {
            Path candidate = picked.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }

        LOG.warn("No mod folders found in the configured directory: " + picked);
        return null;
    }

    private static boolean containsModFolder(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.anyMatch(entry -> Files.isDirectory(entry)
                    && Files.isRegularFile(entry.resolve("descriptor.mod")));
        } catch (IOException e) {
            return false;
        }
    }

    private static List<String> findWindowsSteamRoots() {
        List<String> roots = new ArrayList<>();

        addIfDirectory(roots, readSteamPathFromRegistry());
        for (String defaultPath : WINDOWS_STEAM_PATHS) {
            addIfDirectory(roots, defaultPath);
        }

        Path libraryFoldersVdf = findLibraryFoldersVdf(roots);
        if (libraryFoldersVdf != null) {
            for (String libraryPath : parseWindowsLibraryFolders(libraryFoldersVdf)) {
                addIfDirectory(roots, libraryPath);
            }
        }

        return roots;
    }

    private static String readSteamPathFromRegistry() {
        Process process = null;
        try {
            process = new ProcessBuilder("reg", "query", STEAM_REGISTRY_KEY, "/v", "SteamPath")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(REGISTRY_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = REGISTRY_STEAM_PATH_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        return matcher.group(1);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOG.warn("Failed to read Steam installation path from registry", e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

    private static void addIfDirectory(List<String> paths, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        Path candidate = Paths.get(path);
        if (!Files.isDirectory(candidate) || containsPath(paths, candidate)) {
            return;
        }
        paths.add(candidate.toString());
    }

    private static boolean containsPath(List<String> paths, Path candidate) {
        String key = canonicalKey(candidate);
        for (String path : paths) {
            if (canonicalKey(Paths.get(path)).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
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
        return scanMods(workshopPath, new ConcurrentHashMap<>());
    }

    public static List<ModInfo> scanMods(String workshopPath,
                                          ConcurrentHashMap<String, ModCacheManager.CacheEntry> cache) {
        List<ModInfo> mods = new ArrayList<>();
        Path workshopDir = Paths.get(workshopPath);
        Map<String, WorkshopItem> workshopItems = readWorkshopItems(workshopDir);

        try (Stream<Path> paths = Files.list(workshopDir)) {
            List<Path> modFolders = paths
                    .filter(Files::isDirectory)
                    .collect(Collectors.toList());

            for (Path modFolder : modFolders) {
                Path descriptorFile = modFolder.resolve("descriptor.mod");
                if (Files.exists(descriptorFile)) {
                    ModInfo modInfo = parseDescriptorFile(modFolder, descriptorFile, cache, workshopItems);
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

    private static Map<String, WorkshopItem> readWorkshopItems(Path workshopPath) {
        Map<String, WorkshopItem> items = new HashMap<>();
        Path contentDir = workshopPath.getParent();
        Path workshopDir = contentDir == null ? null : contentDir.getParent();
        if (workshopDir == null) {
            return items;
        }

        Path acfFile = workshopDir.resolve(WORKSHOP_ACF_NAME);
        if (!Files.isRegularFile(acfFile)) {
            return items;
        }

        try {
            String currentId = null;
            long currentSize = 0;
            long currentTimeUpdated = 0;
            for (String line : Files.readAllLines(acfFile)) {
                Matcher idMatcher = WORKSHOP_ITEM_ID_PATTERN.matcher(line);
                if (idMatcher.matches()) {
                    if (currentId != null) {
                        mergeItem(items, currentId, currentSize, currentTimeUpdated);
                    }
                    currentId = idMatcher.group(1);
                    currentSize = 0;
                    currentTimeUpdated = 0;
                    continue;
                }
                if (currentId == null) {
                    continue;
                }
                Matcher sizeMatcher = WORKSHOP_ITEM_SIZE_PATTERN.matcher(line);
                if (sizeMatcher.find()) {
                    currentSize = Long.parseLong(sizeMatcher.group(1));
                    continue;
                }
                Matcher timeMatcher = WORKSHOP_ITEM_TIME_UPDATED_PATTERN.matcher(line);
                if (timeMatcher.find()) {
                    currentTimeUpdated = Long.parseLong(timeMatcher.group(1));
                }
            }
            if (currentId != null) {
                mergeItem(items, currentId, currentSize, currentTimeUpdated);
            }
        } catch (IOException | NumberFormatException e) {
            LOG.warn("Failed to read workshop item records: " + acfFile, e);
        }

        return items;
    }

    private static void mergeItem(Map<String, WorkshopItem> items, String id, long size, long timeUpdated) {
        items.merge(id, new WorkshopItem(size, timeUpdated),
                (existing, added) -> new WorkshopItem(
                        Math.max(existing.size(), added.size()),
                        Math.max(existing.timeUpdated(), added.timeUpdated())));
    }

    private static ModInfo parseDescriptorFile(Path modFolder, Path descriptorFile,
                                                ConcurrentHashMap<String, ModCacheManager.CacheEntry> cache,
                                                Map<String, WorkshopItem> workshopItems) {
        try {
            String content = Files.readString(descriptorFile);

            Map<String, String> values = parseAllValues(content);

            String modName = values.get("name");
            String version = values.get("version");
            String supportedVersion = values.get("supported_version");

            String folderName = modFolder.getFileName().toString();

            if (modName == null || modName.isEmpty()) {
                modName = folderName;
            }

            long lastModified = getLastModified(modFolder);
            WorkshopItem item = workshopItems.get(folderName);
            ModCacheManager.CacheEntry cached = cache.get(folderName);

            long folderSize;
            if (item != null && item.size() > 0) {
                folderSize = item.size();
            } else if (cached != null && cached.isUpToDate(modFolder)) {
                folderSize = cached.folderSize;
            } else {
                folderSize = calculateFolderSize(modFolder);
            }

            String thumbnailPath = findThumbnail(modFolder, values.get("picture"));

            if (version == null) {
                version = "N/A";
            }
            if (supportedVersion == null) {
                supportedVersion = "N/A";
            }

            return new ModInfo(
                    folderName,
                    modName,
                    version,
                    supportedVersion,
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

    private static String findThumbnail(Path modFolder, String picture) {
        if (picture != null && !picture.isBlank()) {
            Path declaredPicture = modFolder.resolve(picture.replace('\\', '/'));
            if (Files.isRegularFile(declaredPicture)) {
                return declaredPicture.toString();
            }
        }

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

    private static Map<String, String> parseAllValues(String content) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = QUOTED_VALUE_PATTERN.matcher(content);
        while (matcher.find()) {
            values.putIfAbsent(matcher.group(1), matcher.group(2));
        }

        matcher = SINGLE_QUOTED_VALUE_PATTERN.matcher(content);
        while (matcher.find()) {
            values.putIfAbsent(matcher.group(1), matcher.group(2));
        }

        return values;
    }
}
