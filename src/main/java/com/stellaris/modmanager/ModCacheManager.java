package com.stellaris.modmanager;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModCacheManager {

    private static final Path CACHE_DIR;
    private static final Path CACHE_FILE;

    static {
        CACHE_DIR = Paths.get(System.getProperty("user.home"), ".stellaris-mod-manager");
        CACHE_FILE = CACHE_DIR.resolve("mod_cache.dat");
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException ignored) {
        }
    }

    public static class CacheEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        public String modName;
        public String version;
        public String supportedVersion;
        public String thumbnailPath;
        public long folderSize;
        public long lastModified;
        public String modPath;

        public CacheEntry() {
        }

        public CacheEntry(ModInfo info) {
            this.modName = info.modName();
            this.version = info.version();
            this.supportedVersion = info.supportedVersion();
            this.thumbnailPath = info.thumbnailPath();
            this.folderSize = info.folderSize();
            this.lastModified = info.lastModified();
            this.modPath = info.modPath();
        }

        public boolean isUpToDate(Path modFolder) {
            try {
                return Files.getLastModifiedTime(modFolder).toMillis() == lastModified;
            } catch (IOException e) {
                return false;
            }
        }

        public ModInfo toModInfo(String folderName) {
            return new ModInfo(
                    folderName,
                    modName,
                    version,
                    supportedVersion,
                    modPath,
                    thumbnailPath,
                    folderSize,
                    lastModified
            );
        }
    }

    @SuppressWarnings("unchecked")
    public static ConcurrentHashMap<String, CacheEntry> load() {
        if (!Files.exists(CACHE_FILE)) {
            return new ConcurrentHashMap<>();
        }
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(CACHE_FILE)))) {
            Object obj = ois.readObject();
            if (obj instanceof ConcurrentHashMap) {
                return (ConcurrentHashMap<String, CacheEntry>) obj;
            }
        } catch (Exception ignored) {
        }
        return new ConcurrentHashMap<>();
    }

    public static void save(ConcurrentHashMap<String, CacheEntry> cache) {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(CACHE_FILE)))) {
            oos.writeObject(cache);
        } catch (IOException ignored) {
        }
    }
}
