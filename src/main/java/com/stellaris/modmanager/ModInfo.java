package com.stellaris.modmanager;

public class ModInfo {
    private String folderName;
    private String modName;
    private String version;
    private String supportedVersion;
    private String modPath;

    public ModInfo(String folderName, String modName, String version, String supportedVersion, String modPath) {
        this.folderName = folderName;
        this.modName = modName;
        this.version = version;
        this.supportedVersion = supportedVersion;
        this.modPath = modPath;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getModName() {
        return modName;
    }

    public void setModName(String modName) {
        this.modName = modName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSupportedVersion() {
        return supportedVersion;
    }

    public void setSupportedVersion(String supportedVersion) {
        this.supportedVersion = supportedVersion;
    }

    public String getModPath() {
        return modPath;
    }

    public void setModPath(String modPath) {
        this.modPath = modPath;
    }
}
