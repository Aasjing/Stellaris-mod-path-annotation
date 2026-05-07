package com.stellaris.modmanager;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class ModScanner {
    
    private static final String WORKSHOP_PATH = "SteamLibrary\\steamapps\\workshop\\content\\281990";
    
    public static List<String> findWorkshopDirectories() {
        List<String> paths = new ArrayList<>();
        
        String userHome = System.getProperty("user.home");
        
        List<Path> potentialRoots = Arrays.asList(
            Paths.get(userHome, WORKSHOP_PATH),
            Paths.get("C:/", WORKSHOP_PATH),
            Paths.get("D:/", WORKSHOP_PATH),
            Paths.get("E:/", WORKSHOP_PATH)
        );
        
        for (Path root : potentialRoots) {
            if (Files.exists(root) && Files.isDirectory(root)) {
                paths.add(root.toString());
            }
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
            e.printStackTrace();
        }
        
        return mods;
    }
    
    private static ModInfo parseDescriptorFile(Path modFolder, Path descriptorFile) {
        try {
            String content = new String(Files.readAllBytes(descriptorFile));
            
            String modName = extractValue(content, "name");
            String version = extractValue(content, "version");
            String supportedVersion = extractValue(content, "supported_version");
            
            String folderName = modFolder.getFileName().toString();
            
            if (modName == null || modName.isEmpty()) {
                modName = folderName;
            }
            
            return new ModInfo(
                folderName,
                modName,
                version != null ? version : "N/A",
                supportedVersion != null ? supportedVersion : "N/A",
                modFolder.toString()
            );
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private static String extractValue(String content, String key) {
        Pattern pattern = Pattern.compile(key + "\\s*=\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        pattern = Pattern.compile(key + "\\s*=\\s*'([^']*)'");
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
}
