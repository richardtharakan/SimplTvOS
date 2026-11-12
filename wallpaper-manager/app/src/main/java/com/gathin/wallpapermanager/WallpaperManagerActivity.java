package com.gathin.wallpapermanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class WallpaperManagerActivity extends Activity implements
        WallpaperAdapter.OnWallpaperActionListener,
        BrowserAdapter.OnBrowserActionListener {

    private File activeWallpapersDir;
    private File inactiveWallpapersDir;

    // UI elements
    private TextView tabWallpapers;
    private TextView tabBrowser;
    private View viewWallpapers;
    private View viewBrowser;

    // Gallery UI
    private TextView wallpaperCount;
    private TextView btnSwitchOrder;
    private String switchOrder = "shuffle";
    private RecyclerView wallpaperGrid;
    private WallpaperAdapter wallpaperAdapter;
    private final List<WallpaperItem> wallpaperItems = new ArrayList<>();
    private WallpaperItem selectedOrFocusedItem;

    // Browser UI
    private TextView txtCurrentPath;
    private TextView txtSelectionCount;
    private TextView btnPaste;
    private RecyclerView browserRecycler;
    private BrowserAdapter browserAdapter;
    private File currentBrowseDir;
    private final List<File> clipboardFiles = new ArrayList<>();
    private boolean isCutMode = false;

    // Preview UI
    private View previewOverlay;
    private ImageView previewImageView;
    private TextView previewImageTitle;
    private TextView previewIndexText;
    private TextView btnPreviewToggleActive;
    private TextView btnPreviewSetCurrent;
    private int previewCurrentIndex = 0;

    // Transfer server
    private TransferServer server;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallpaper_manager);

        activeWallpapersDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Wallpapers");
        if (!activeWallpapersDir.exists()) activeWallpapersDir.mkdirs();

        inactiveWallpapersDir = new File(activeWallpapersDir, ".inactive");
        if (!inactiveWallpapersDir.exists()) inactiveWallpapersDir.mkdirs();

        currentBrowseDir = activeWallpapersDir;

        bindViews();
        setupTabs();
        setupWallpapersGallery();
        setupBrowser();
        setupPreviewOverlay();

        // Start Wi-Fi background transfer server
        server = new TransferServer(activeWallpapersDir, () -> mainHandler.post(this::loadWallpapers));
        server.start();

        loadWallpapers();
    }

    private void bindViews() {
        tabWallpapers = findViewById(R.id.tabWallpapers);
        tabBrowser = findViewById(R.id.tabBrowser);
        viewWallpapers = findViewById(R.id.viewWallpapers);
        viewBrowser = findViewById(R.id.viewBrowser);

        wallpaperCount = findViewById(R.id.wallpaperCount);
        wallpaperGrid = findViewById(R.id.wallpaperGrid);

        txtCurrentPath = findViewById(R.id.txtCurrentPath);
        txtSelectionCount = findViewById(R.id.txtSelectionCount);
        btnPaste = findViewById(R.id.btnPaste);
        browserRecycler = findViewById(R.id.browserRecycler);

        previewOverlay = findViewById(R.id.fullscreenPreviewOverlay);
        previewImageView = findViewById(R.id.previewImageView);
        previewImageTitle = findViewById(R.id.previewImageTitle);
        previewIndexText = findViewById(R.id.previewIndexText);
        btnPreviewToggleActive = findViewById(R.id.btnPreviewToggleActive);
        btnPreviewSetCurrent = findViewById(R.id.btnPreviewSetCurrent);
    }

    private void setupTabs() {
        tabWallpapers.setOnClickListener(v -> switchTab(true));
        tabBrowser.setOnClickListener(v -> switchTab(false));
    }

    private void switchTab(boolean showWallpapers) {
        tabWallpapers.setSelected(showWallpapers);
        tabWallpapers.setTextColor(showWallpapers ? 0xFFFFFFFF : 0xFF90A4AE);

        tabBrowser.setSelected(!showWallpapers);
        tabBrowser.setTextColor(!showWallpapers ? 0xFFFFFFFF : 0xFF90A4AE);

        viewWallpapers.setVisibility(showWallpapers ? View.VISIBLE : View.GONE);
        viewBrowser.setVisibility(!showWallpapers ? View.VISIBLE : View.GONE);

        if (showWallpapers) {
            loadWallpapers();
            findViewById(R.id.btnFullscreenPreview).requestFocus();
        } else {
            loadBrowserDirectory(currentBrowseDir);
            findViewById(R.id.btnNavWallpapers).requestFocus();
        }
    }

    private void setupWallpapersGallery() {
        wallpaperGrid.setLayoutManager(new GridLayoutManager(this, 4));
        wallpaperAdapter = new WallpaperAdapter(this, this);
        wallpaperGrid.setAdapter(wallpaperAdapter);

        findViewById(R.id.btnFullscreenPreview).setOnClickListener(v -> {
            if (!wallpaperItems.isEmpty()) {
                int index = selectedOrFocusedItem != null ? wallpaperItems.indexOf(selectedOrFocusedItem) : 0;
                if (index < 0) index = 0;
                openPreview(index);
            } else {
                Toast.makeText(this, "No wallpapers to preview", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnSelectAll).setOnClickListener(v -> selectAllWallpapers(true));
        findViewById(R.id.btnUnselectAll).setOnClickListener(v -> selectAllWallpapers(false));
        btnSwitchOrder = findViewById(R.id.btnSwitchOrder);
        loadOrderSetting();
        if (btnSwitchOrder != null) {
            btnSwitchOrder.setOnClickListener(v -> toggleSwitchOrder());
        }
        findViewById(R.id.btnReceive).setOnClickListener(v -> showReceiveInstructions());
        findViewById(R.id.btnQuickShare).setOnClickListener(v -> shareFocusedWallpaper());

        wallpaperGrid.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                view.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        int pos = wallpaperGrid.getChildAdapterPosition(v);
                        if (pos >= 0 && pos < wallpaperItems.size()) {
                            selectedOrFocusedItem = wallpaperItems.get(pos);
                        }
                    }
                });
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {}
        });
    }

    private void setupBrowser() {
        browserRecycler.setLayoutManager(new LinearLayoutManager(this));
        browserAdapter = new BrowserAdapter(this, this);
        browserRecycler.setAdapter(browserAdapter);

        findViewById(R.id.btnNavUp).setOnClickListener(v -> {
            if (currentBrowseDir != null && currentBrowseDir.getParentFile() != null && currentBrowseDir.getParentFile().canRead()) {
                loadBrowserDirectory(currentBrowseDir.getParentFile());
            } else {
                Toast.makeText(this, "Already at top folder", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnNavWallpapers).setOnClickListener(v -> loadBrowserDirectory(activeWallpapersDir));

        findViewById(R.id.btnNavUsb).setOnClickListener(v -> {
            List<File> usbs = FileUtils.getUsbLocations();
            if (usbs.isEmpty()) {
                Toast.makeText(this, "No USB drives connected", Toast.LENGTH_SHORT).show();
            } else if (usbs.size() == 1) {
                loadBrowserDirectory(usbs.get(0));
            } else {
                String[] names = new String[usbs.size()];
                for (int i = 0; i < usbs.size(); i++) names[i] = "💾 " + usbs.get(i).getName();
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Select USB Storage")
                        .setItems(names, (dialog, which) -> loadBrowserDirectory(usbs.get(which)))
                        .show();
            }
        });

        findViewById(R.id.btnNavDownloads).setOnClickListener(v -> {
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dl.exists()) loadBrowserDirectory(dl);
            else loadBrowserDirectory(Environment.getExternalStorageDirectory());
        });

        findViewById(R.id.btnSelectAllBrowser).setOnClickListener(v -> browserAdapter.selectAll());
        findViewById(R.id.btnClearSelectionBrowser).setOnClickListener(v -> browserAdapter.clearSelection());
        findViewById(R.id.btnCopy).setOnClickListener(v -> copySelectedFiles(false));
        findViewById(R.id.btnCut).setOnClickListener(v -> copySelectedFiles(true));
        findViewById(R.id.btnPaste).setOnClickListener(v -> pasteClipboardFiles());
        findViewById(R.id.btnAddToWallpapers).setOnClickListener(v -> addSelectedToWallpapers());
        findViewById(R.id.btnDeleteFiles).setOnClickListener(v -> deleteSelectedBrowserFiles());

        updatePasteButtonState();
    }

    private void setupPreviewOverlay() {
        findViewById(R.id.btnPreviewClose).setOnClickListener(v -> closePreview());

        btnPreviewToggleActive.setOnClickListener(v -> {
            if (previewCurrentIndex >= 0 && previewCurrentIndex < wallpaperItems.size()) {
                WallpaperItem item = wallpaperItems.get(previewCurrentIndex);
                toggleWallpaperActive(item);
                updatePreviewDetails(item);
            }
        });

        btnPreviewSetCurrent.setOnClickListener(v -> {
            if (previewCurrentIndex >= 0 && previewCurrentIndex < wallpaperItems.size()) {
                WallpaperItem item = wallpaperItems.get(previewCurrentIndex);
                setActiveWallpaper(item.getFile());
            }
        });
    }

    // --- Wallpapers Gallery Logic ---

    private void loadWallpapers() {
        wallpaperItems.clear();
        android.util.Log.d("WallpaperManager", "activeWallpapersDir=" + activeWallpapersDir + " exists=" + activeWallpapersDir.exists() + " canRead=" + activeWallpapersDir.canRead());

        // 1. Active files (in primary wallpapers dir)
        File[] actives = activeWallpapersDir.listFiles((d, name) -> isImageName(name));
        int activeCount = 0;
        if (actives != null) {
            for (File f : actives) {
                if (f.isFile()) {
                    wallpaperItems.add(new WallpaperItem(f, true));
                    activeCount++;
                }
            }
        }

        // 2. Inactive files (in .inactive dir)
        File[] inactives = inactiveWallpapersDir.listFiles((d, name) -> isImageName(name));
        int inactiveCount = 0;
        if (inactives != null) {
            for (File f : inactives) {
                if (f.isFile()) {
                    wallpaperItems.add(new WallpaperItem(f, false));
                    inactiveCount++;
                }
            }
        }

        Collections.sort(wallpaperItems, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        wallpaperAdapter.setItems(wallpaperItems);

        wallpaperCount.setText(activeCount + " active • " + inactiveCount + " unselected");

        if (!wallpaperItems.isEmpty() && selectedOrFocusedItem == null) {
            selectedOrFocusedItem = wallpaperItems.get(0);
        }
    }

    private boolean isImageName(String name) {
        if (name == null) return false;
        String l = name.toLowerCase(Locale.ROOT);
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
    }

    private void toggleWallpaperActive(WallpaperItem item) {
        File src = item.getFile();
        File dest;
        if (item.isActive()) {
            dest = new File(inactiveWallpapersDir, src.getName());
            if (FileUtils.moveFile(src, dest)) {
                item.setFile(dest);
                item.setActive(false);
                Toast.makeText(this, "Unselected from rotation: " + dest.getName(), Toast.LENGTH_SHORT).show();
            }
        } else {
            dest = new File(activeWallpapersDir, src.getName());
            if (FileUtils.moveFile(src, dest)) {
                item.setFile(dest);
                item.setActive(true);
                Toast.makeText(this, "Selected for rotation: " + dest.getName(), Toast.LENGTH_SHORT).show();
            }
        }
        wallpaperAdapter.notifyDataSetChanged();
        loadWallpapers();

        Intent intent = new Intent("com.gathin.tvhome.ACTION_WALLPAPER_CHANGED");
        intent.setPackage("com.gathin.tvhome");
        sendBroadcast(intent);
    }

    private void loadOrderSetting() {
        File orderFile = new File(activeWallpapersDir, "order.txt");
        if (orderFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(orderFile))) {
                String line = reader.readLine();
                if (line != null && ("sequential".equalsIgnoreCase(line.trim()) || "shuffle".equalsIgnoreCase(line.trim()))) {
                    switchOrder = line.trim().toLowerCase(java.util.Locale.ROOT);
                }
            } catch (Exception ignored) {}
        }
        updateSwitchOrderUi();
    }

    private void updateSwitchOrderUi() {
        if (btnSwitchOrder == null) return;
        if ("sequential".equals(switchOrder)) {
            btnSwitchOrder.setText("🔁 Mode: In Order");
            btnSwitchOrder.setTextColor(0xFF00E5FF);
        } else {
            btnSwitchOrder.setText("🔀 Mode: Random");
            btnSwitchOrder.setTextColor(0xFFFFD54F);
        }
    }

    private void toggleSwitchOrder() {
        if ("sequential".equals(switchOrder)) {
            switchOrder = "shuffle";
            Toast.makeText(this, "🔀 Wallpaper Mode: Random Switching", Toast.LENGTH_SHORT).show();
        } else {
            switchOrder = "sequential";
            Toast.makeText(this, "🔁 Wallpaper Mode: In Order (Sequential)", Toast.LENGTH_SHORT).show();
        }
        updateSwitchOrderUi();
        try {
            File orderFile = new File(activeWallpapersDir, "order.txt");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(orderFile)) {
                fos.write(switchOrder.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}

        Intent intent = new Intent("com.gathin.tvhome.ACTION_WALLPAPER_CHANGED");
        intent.setPackage("com.gathin.tvhome");
        intent.putExtra("order", switchOrder);
        sendBroadcast(intent);
    }

    private void selectAllWallpapers(boolean activate) {
        int moved = 0;
        if (activate) {
            File[] inactives = inactiveWallpapersDir.listFiles((d, name) -> isImageName(name));
            if (inactives != null) {
                for (File f : inactives) {
                    if (f.isFile() && FileUtils.moveFile(f, new File(activeWallpapersDir, f.getName()))) {
                        moved++;
                    }
                }
            }
            Toast.makeText(this, "Selected all wallpapers (" + moved + " activated)", Toast.LENGTH_SHORT).show();
        } else {
            File[] actives = activeWallpapersDir.listFiles((d, name) -> isImageName(name));
            if (actives != null) {
                for (File f : actives) {
                    if (f.isFile() && FileUtils.moveFile(f, new File(inactiveWallpapersDir, f.getName()))) {
                        moved++;
                    }
                }
            }
            Toast.makeText(this, "Unselected all wallpapers (" + moved + " moved to unselected)", Toast.LENGTH_SHORT).show();
        }
        loadWallpapers();
    }

    @Override
    public void onWallpaperClicked(WallpaperItem item, int position) {
        selectedOrFocusedItem = item;
        String statusLabel = item.isActive() ? "✗ Unselect (Remove from Rotation)" : "✓ Select (Add to Active Rotation)";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(item.getName())
                .setItems(new String[]{
                        "👁️ Preview Fullscreen",
                        statusLabel,
                        "⭐ Set as Active Wallpaper Now",
                        "📋 Copy File",
                        "📤 Quick Share",
                        "🗑️ Delete Permanently"
                }, (dialog, which) -> {
                    if (which == 0) {
                        openPreview(position);
                    } else if (which == 1) {
                        toggleWallpaperActive(item);
                    } else if (which == 2) {
                        setActiveWallpaper(item.getFile());
                    } else if (which == 3) {
                        clipboardFiles.clear();
                        clipboardFiles.add(item.getFile());
                        isCutMode = false;
                        updatePasteButtonState();
                        Toast.makeText(this, "Copied " + item.getName() + " to clipboard", Toast.LENGTH_SHORT).show();
                    } else if (which == 4) {
                        shareFile(item.getFile());
                    } else if (which == 5) {
                        confirmDeleteWallpaper(item);
                    }
                })
                .show();
    }

    @Override
    public void onWallpaperLongClicked(WallpaperItem item, int position) {
        toggleWallpaperActive(item);
    }

    private void confirmDeleteWallpaper(WallpaperItem item) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Delete Wallpaper")
                .setMessage("Permanently remove \"" + item.getName() + "\" from TV storage?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean ok = item.getFile().delete();
                    if (ok) {
                        Toast.makeText(this, "Deleted " + item.getName(), Toast.LENGTH_SHORT).show();
                        loadWallpapers();
                    } else {
                        Toast.makeText(this, "Could not delete file", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setActiveWallpaper(File file) {
        try {
            if (file.getParentFile() != null && file.getParentFile().equals(inactiveWallpapersDir)) {
                File dest = new File(activeWallpapersDir, file.getName());
                if (FileUtils.moveFile(file, dest)) {
                    file = dest;
                }
            }

            File currentFile = new File(activeWallpapersDir, "current.txt");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(currentFile)) {
                fos.write(file.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            Intent intent = new Intent("com.gathin.tvhome.ACTION_WALLPAPER_CHANGED");
            intent.setPackage("com.gathin.tvhome");
            intent.putExtra("image", file.getName());
            sendBroadcast(intent);

            Toast.makeText(this, "✅ Active wallpaper set to " + file.getName(), Toast.LENGTH_SHORT).show();
            loadWallpapers();
        } catch (Exception e) {
            Toast.makeText(this, "Wallpaper active: " + file.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFocusedWallpaper() {
        if (selectedOrFocusedItem != null && selectedOrFocusedItem.getFile().exists()) {
            shareFile(selectedOrFocusedItem.getFile());
        } else {
            Toast.makeText(this, "Select a wallpaper first", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(File file) {
        try {
            Uri contentUri = FileProvider.getUriForFile(this,
                    "com.gathin.wallpapermanager.fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share via Quick Share / Nearby Share"));
        } catch (Exception e) {
            Toast.makeText(this, "Quick Share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showReceiveInstructions() {
        String ip = TransferServer.getLocalIpAddress();
        String url = "http://" + ip + ":" + TransferServer.PORT;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_receive_qr, null);
        ImageView imgQr = dialogView.findViewById(R.id.imgQrCode);
        TextView txtUrl = dialogView.findViewById(R.id.txtWebUrl);
        TextView btnClose = dialogView.findViewById(R.id.btnCloseQrDialog);

        txtUrl.setText(url);

        try {
            Bitmap qrBitmap = generateQrCode(url, 400);
            imgQr.setImageBitmap(qrBitmap);
        } catch (Exception e) {
            imgQr.setVisibility(View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(dialogView)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        btnClose.requestFocus();
    }

    private Bitmap generateQrCode(String text, int targetPixelSize) {
        io.nayuki.qrcodegen.QrCode qr = io.nayuki.qrcodegen.QrCode.encodeText(text, io.nayuki.qrcodegen.QrCode.Ecc.MEDIUM);
        int border = 2;
        int scale = Math.max(1, targetPixelSize / (qr.size + border * 2));
        int imgSize = (qr.size + border * 2) * scale;
        Bitmap bitmap = Bitmap.createBitmap(imgSize, imgSize, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < imgSize; y++) {
            for (int x = 0; x < imgSize; x++) {
                int qx = x / scale - border;
                int qy = y / scale - border;
                boolean dark = qx >= 0 && qx < qr.size && qy >= 0 && qy < qr.size && qr.getModule(qx, qy);
                bitmap.setPixel(x, y, dark ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bitmap;
    }

    // --- Fullscreen Preview Logic ---

    private void openPreview(int index) {
        if (wallpaperItems.isEmpty()) return;
        previewCurrentIndex = Math.max(0, Math.min(index, wallpaperItems.size() - 1));
        previewOverlay.setVisibility(View.VISIBLE);
        showPreviewAt(previewCurrentIndex);
        btnPreviewToggleActive.requestFocus();
    }

    private void showPreviewAt(int index) {
        if (index < 0 || index >= wallpaperItems.size()) return;
        WallpaperItem item = wallpaperItems.get(index);
        previewImageTitle.setText(item.getName());
        previewIndexText.setText((index + 1) + " / " + wallpaperItems.size());

        updatePreviewDetails(item);

        // Async load high-res preview downsampled to screen resolution (1920x1080)
        previewImageView.setImageDrawable(null);
        final String path = item.getFile().getAbsolutePath();
        previewImageView.setTag(path);

        new Thread(() -> {
            Bitmap bmp = ImageCache.decodeSampledBitmap(item.getFile(), 1920, 1080);
            mainHandler.post(() -> {
                if (path.equals(previewImageView.getTag()) && bmp != null) {
                    previewImageView.setImageBitmap(bmp);
                }
            });
        }).start();
    }

    private void updatePreviewDetails(WallpaperItem item) {
        if (item.isActive()) {
            btnPreviewToggleActive.setText("✓ Active in Rotation");
            btnPreviewToggleActive.setTextColor(0xFF00E676);
        } else {
            btnPreviewToggleActive.setText("✗ Unselected (Inactive)");
            btnPreviewToggleActive.setTextColor(0xFF90A4AE);
        }
    }

    private void closePreview() {
        previewOverlay.setVisibility(View.GONE);
        wallpaperGrid.requestFocus();
    }

    // --- Browser & Folder Navigation Logic ---

    private void loadBrowserDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            Toast.makeText(this, "Cannot access folder", Toast.LENGTH_SHORT).show();
            return;
        }
        currentBrowseDir = dir;
        txtCurrentPath.setText(dir.getAbsolutePath());

        File[] files = dir.listFiles();
        android.util.Log.d("Browser", "loadBrowserDirectory dir=" + dir + " files count=" + (files != null ? files.length : -1));
        List<File> directories = new ArrayList<>();
        List<File> regularFiles = new ArrayList<>();

        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && !f.isHidden()) {
                    directories.add(f);
                } else if (f.isFile() && !f.isHidden()) {
                    regularFiles.add(f);
                }
            }
        }

        Collections.sort(directories, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        Collections.sort(regularFiles, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        List<File> combined = new ArrayList<>();
        combined.addAll(directories);
        combined.addAll(regularFiles);
        android.util.Log.d("Browser", "combined size=" + combined.size());
        browserAdapter.setItems(combined);
    }

    @Override
    public void onFolderClicked(File folder) {
        loadBrowserDirectory(folder);
    }

    @Override
    public void onFileClicked(File file) {
        if (BrowserAdapter.isImageFile(file.getName())) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(file.getName())
                    .setItems(new String[]{
                            "⭐ Add to Active Wallpapers",
                            "👁️ Preview Image",
                            "☑️ Toggle Selection Checkbox",
                            "📋 Copy",
                            "✂️ Move",
                            "🗑️ Delete"
                    }, (dialog, which) -> {
                        if (which == 0) {
                            addSingleFileToWallpapers(file);
                        } else if (which == 1) {
                            openSingleImagePreview(file);
                        } else if (which == 2) {
                            browserAdapter.toggleSelection(file);
                        } else if (which == 3) {
                            clipboardFiles.clear();
                            clipboardFiles.add(file);
                            isCutMode = false;
                            updatePasteButtonState();
                            Toast.makeText(this, "Copied " + file.getName(), Toast.LENGTH_SHORT).show();
                        } else if (which == 4) {
                            clipboardFiles.clear();
                            clipboardFiles.add(file);
                            isCutMode = true;
                            updatePasteButtonState();
                            Toast.makeText(this, "Marked to move " + file.getName(), Toast.LENGTH_SHORT).show();
                        } else if (which == 5) {
                            confirmDeleteBrowserFile(file);
                        }
                    })
                    .show();
        } else {
            browserAdapter.toggleSelection(file);
        }
    }

    private void openSingleImagePreview(File file) {
        List<WallpaperItem> temp = new ArrayList<>();
        temp.add(new WallpaperItem(file, true));
        wallpaperItems.clear();
        wallpaperItems.addAll(temp);
        openPreview(0);
    }

    private void addSingleFileToWallpapers(File file) {
        File dest = new File(activeWallpapersDir, file.getName());
        boolean ok = FileUtils.copyFile(file, dest);
        if (ok) {
            Toast.makeText(this, "⭐ Added " + file.getName() + " to Wallpapers!", Toast.LENGTH_SHORT).show();
            loadWallpapers();
        } else {
            Toast.makeText(this, "Failed to copy file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onItemLongClicked(File file) {
        browserAdapter.toggleSelection(file);
    }

    @Override
    public void onSelectionChanged(int count) {
        txtSelectionCount.setText(count + " selected");
    }

    private void copySelectedFiles(boolean isCut) {
        Set<File> selected = browserAdapter.getSelectedFiles();
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select files first", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboardFiles.clear();
        clipboardFiles.addAll(selected);
        isCutMode = isCut;
        updatePasteButtonState();
        Toast.makeText(this, (isCut ? "Moving " : "Copied ") + selected.size() + " files to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void pasteClipboardFiles() {
        if (clipboardFiles.isEmpty()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        int success = 0;
        for (File src : clipboardFiles) {
            File dest = new File(currentBrowseDir, src.getName());
            if (isCutMode) {
                if (FileUtils.moveFile(src, dest)) success++;
            } else {
                if (FileUtils.copyFile(src, dest)) success++;
            }
        }
        Toast.makeText(this, "Pasted " + success + " files into " + currentBrowseDir.getName(), Toast.LENGTH_SHORT).show();
        if (isCutMode) {
            clipboardFiles.clear();
            isCutMode = false;
        }
        updatePasteButtonState();
        loadBrowserDirectory(currentBrowseDir);
        loadWallpapers();
    }

    private void addSelectedToWallpapers() {
        Set<File> selected = browserAdapter.getSelectedFiles();
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select images first", Toast.LENGTH_SHORT).show();
            return;
        }
        int count = 0;
        for (File src : selected) {
            if (BrowserAdapter.isImageFile(src.getName())) {
                File dest = new File(activeWallpapersDir, src.getName());
                if (FileUtils.copyFile(src, dest)) {
                    count++;
                }
            }
        }
        Toast.makeText(this, "⭐ Added " + count + " new wallpapers to active rotation!", Toast.LENGTH_LONG).show();
        browserAdapter.clearSelection();
        loadWallpapers();
    }

    private void deleteSelectedBrowserFiles() {
        Set<File> selected = browserAdapter.getSelectedFiles();
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select files first", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Delete Selected")
                .setMessage("Delete " + selected.size() + " files permanently?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    int del = 0;
                    for (File f : selected) {
                        if (f.delete()) del++;
                    }
                    Toast.makeText(this, "Deleted " + del + " files", Toast.LENGTH_SHORT).show();
                    browserAdapter.clearSelection();
                    loadBrowserDirectory(currentBrowseDir);
                    loadWallpapers();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteBrowserFile(File file) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Delete File")
                .setMessage("Delete \"" + file.getName() + "\" permanently?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (file.delete()) {
                        Toast.makeText(this, "Deleted " + file.getName(), Toast.LENGTH_SHORT).show();
                        loadBrowserDirectory(currentBrowseDir);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updatePasteButtonState() {
        if (clipboardFiles.isEmpty()) {
            btnPaste.setText("📥 Paste");
            btnPaste.setEnabled(false);
            btnPaste.setAlpha(0.5f);
        } else {
            btnPaste.setText("📥 Paste (" + clipboardFiles.size() + ")");
            btnPaste.setEnabled(true);
            btnPaste.setAlpha(1.0f);
        }
    }

    // --- Key Events (D-pad & Back) ---

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (previewOverlay.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (previewCurrentIndex > 0) {
                    showPreviewAt(--previewCurrentIndex);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (previewCurrentIndex < wallpaperItems.size() - 1) {
                    showPreviewAt(++previewCurrentIndex);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                closePreview();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (previewOverlay.getVisibility() == View.VISIBLE) {
            closePreview();
            return;
        }
        if (viewBrowser.getVisibility() == View.VISIBLE) {
            if (currentBrowseDir != null && currentBrowseDir.getParentFile() != null && currentBrowseDir.getParentFile().canRead()
                    && !currentBrowseDir.getAbsolutePath().equals("/sdcard")
                    && !currentBrowseDir.getAbsolutePath().equals("/storage/emulated/0")) {
                loadBrowserDirectory(currentBrowseDir.getParentFile());
                return;
            } else {
                switchTab(true);
                return;
            }
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWallpapers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) server.stop();
    }
}
