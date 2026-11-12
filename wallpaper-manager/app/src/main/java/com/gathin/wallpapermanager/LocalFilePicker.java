package com.gathin.wallpapermanager;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Built-in TV file browser to pick and import images from USB drives or internal storage.
 */
public final class LocalFilePicker {

    public interface OnImportListener {
        void onImageImported(File importedFile);
    }

    public static void show(Context context, File targetWallpapersDir, OnImportListener listener) {
        // Start at root storage options
        showRootPicker(context, targetWallpapersDir, listener);
    }

    private static void showRootPicker(Context context, File targetDir, OnImportListener listener) {
        List<File> locations = new ArrayList<>();
        List<String> names = new ArrayList<>();

        // 1. USB Storage drives under /storage/
        File storageRoot = new File("/storage");
        if (storageRoot.exists() && storageRoot.isDirectory()) {
            File[] storages = storageRoot.listFiles();
            if (storages != null) {
                for (File f : storages) {
                    String n = f.getName().toLowerCase();
                    if (!n.equals("self") && !n.equals("emulated") && f.canRead()) {
                        locations.add(f);
                        names.add("💾 USB Drive (" + f.getName() + ")");
                    }
                }
            }
        }

        // 2. Download folder
        File download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (download.exists()) {
            locations.add(download);
            names.add("📥 Downloads folder");
        }

        // 3. Pictures folder
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (pictures.exists()) {
            locations.add(pictures);
            names.add("🖼️ Pictures folder");
        }

        // 4. Internal Storage Root
        File internal = Environment.getExternalStorageDirectory();
        if (internal.exists()) {
            locations.add(internal);
            names.add("📱 Internal Storage (/sdcard)");
        }

        if (locations.isEmpty()) {
            Toast.makeText(context, "No storage locations found", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select Source (USB / Internal)")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    showFolderBrowser(context, locations.get(which), targetDir, listener);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showFolderBrowser(Context context, File currentFolder, File targetDir, OnImportListener listener) {
        File[] allFiles = currentFolder.listFiles();
        List<File> subFolders = new ArrayList<>();
        List<File> imageFiles = new ArrayList<>();

        if (allFiles != null) {
            for (File f : allFiles) {
                if (f.isDirectory() && !f.isHidden() && f.canRead()) {
                    subFolders.add(f);
                } else if (f.isFile() && isImageFile(f.getName())) {
                    imageFiles.add(f);
                }
            }
        }

        Collections.sort(subFolders, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        Collections.sort(imageFiles, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        List<String> displayItems = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        // Add back / parent option if not at root
        if (currentFolder.getParentFile() != null) {
            displayItems.add("⬅️ .. (Parent folder)");
            actions.add(() -> showFolderBrowser(context, currentFolder.getParentFile(), targetDir, listener));
        }

        // Add folders
        for (File dir : subFolders) {
            displayItems.add("📁 " + dir.getName());
            actions.add(() -> showFolderBrowser(context, dir, targetDir, listener));
        }

        // Add images
        for (File img : imageFiles) {
            long kb = img.length() / 1024;
            displayItems.add("🖼️ " + img.getName() + " (" + kb + " KB)");
            actions.add(() -> confirmImport(context, img, targetDir, listener));
        }

        if (displayItems.isEmpty()) {
            displayItems.add("(Empty folder - no images found)");
            actions.add(() -> {});
        }

        new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("📁 " + currentFolder.getName())
                .setItems(displayItems.toArray(new String[0]), (dialog, which) -> {
                    actions.get(which).run();
                })
                .setNeutralButton("Change Drive", (d, w) -> showRootPicker(context, targetDir, listener))
                .setNegativeButton("Close", null)
                .show();
    }

    private static void confirmImport(Context context, File sourceFile, File targetDir, OnImportListener listener) {
        new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Import Wallpaper")
                .setMessage("Import \"" + sourceFile.getName() + "\" into TV Wallpapers & Screensavers?")
                .setPositiveButton("Import", (dialog, which) -> {
                    try {
                        File dest = new File(targetDir, sourceFile.getName());
                        copyFile(sourceFile, dest);
                        Toast.makeText(context, "✅ Imported " + sourceFile.getName(), Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onImageImported(dest);
                    } catch (Exception e) {
                        Toast.makeText(context, "Failed to import: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }
}
