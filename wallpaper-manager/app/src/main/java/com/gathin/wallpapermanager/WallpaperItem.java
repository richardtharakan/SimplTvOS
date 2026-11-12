package com.gathin.wallpapermanager;

import java.io.File;

public final class WallpaperItem {
    private File file;
    private boolean active;

    public WallpaperItem(File file, boolean active) {
        this.file = file;
        this.active = active;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getName() {
        return file != null ? file.getName() : "";
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
