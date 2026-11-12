package com.gathin.wallpapermanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class FileUtils {

    public static boolean copyFile(File src, File dest) {
        if (src == null || !src.exists()) return false;
        if (dest.getParentFile() != null && !dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean moveFile(File src, File dest) {
        if (src == null || !src.exists()) return false;
        if (src.renameTo(dest)) return true;
        // Fallback across different filesystems / mounts
        if (copyFile(src, dest)) {
            return src.delete();
        }
        return false;
    }

    public static List<File> getUsbLocations() {
        List<File> list = new ArrayList<>();
        File storage = new File("/storage");
        if (storage.exists() && storage.isDirectory()) {
            File[] files = storage.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName().toLowerCase();
                    if (!name.equals("self") && !name.equals("emulated") && f.canRead()) {
                        list.add(f);
                    }
                }
            }
        }
        File mnt = new File("/mnt/media_rw");
        if (mnt.exists() && mnt.isDirectory()) {
            File[] files = mnt.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.canRead() && !list.contains(f)) {
                        list.add(f);
                    }
                }
            }
        }
        return list;
    }
}
