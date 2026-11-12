package com.gathin.wallpapermanager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BrowserAdapter extends RecyclerView.Adapter<BrowserAdapter.ViewHolder> {

    public interface OnBrowserActionListener {
        void onFolderClicked(File folder);
        void onFileClicked(File file);
        void onItemLongClicked(File file);
        void onSelectionChanged(int count);
    }

    private final Context context;
    private final List<File> items = new ArrayList<>();
    private final Set<File> selectedFiles = new HashSet<>();
    private final OnBrowserActionListener listener;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public BrowserAdapter(Context context, OnBrowserActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<File> newItems) {
        items.clear();
        selectedFiles.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(0);
    }

    public List<File> getItems() {
        return items;
    }

    public Set<File> getSelectedFiles() {
        return new HashSet<>(selectedFiles);
    }

    public void toggleSelection(File file) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            selectedFiles.add(file);
        }
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selectedFiles.size());
    }

    public void selectAll() {
        for (File f : items) {
            if (f.isFile()) {
                selectedFiles.add(f);
            }
        }
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selectedFiles.size());
    }

    public void clearSelection() {
        selectedFiles.clear();
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(0);
    }

    public static boolean isImageFile(String name) {
        if (name == null) return false;
        String l = name.toLowerCase(Locale.ROOT);
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_entry, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = items.get(position);
        holder.name.setText(file.getName());

        if (file.isDirectory()) {
            holder.icon.setImageResource(R.drawable.ic_folder);
            holder.checkbox.setVisibility(View.GONE);
            File[] children = file.listFiles();
            int count = children != null ? children.length : 0;
            holder.details.setText("Folder • " + count + " items");
            holder.name.setTextColor(Color.parseColor("#FFD54F"));
        } else {
            holder.checkbox.setVisibility(View.VISIBLE);
            boolean selected = selectedFiles.contains(file);
            holder.checkbox.setImageResource(selected ? R.drawable.ic_check_circle : R.drawable.ic_circle_outline);
            long kb = file.length() / 1024;
            holder.details.setText(kb > 1024 ? String.format(Locale.ROOT, "%.1f MB", kb / 1024f) : kb + " KB");
            holder.name.setTextColor(Color.parseColor("#FFFFFF"));

            if (isImageFile(file.getName())) {
                holder.icon.setImageResource(R.drawable.ic_image);
                final String path = file.getAbsolutePath();
                holder.icon.setTag(path);
                thumbnailExecutor.execute(() -> {
                    Bitmap bmp = ImageCache.decodeSampledBitmap(file, 80, 80);
                    mainHandler.post(() -> {
                        if (path.equals(holder.icon.getTag()) && bmp != null) {
                            holder.icon.setImageBitmap(bmp);
                        }
                    });
                });
            } else {
                holder.icon.setImageResource(R.drawable.ic_image);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                if (file.isDirectory()) {
                    listener.onFolderClicked(file);
                } else {
                    listener.onFileClicked(file);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClicked(file);
            }
            return true;
        });

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f)
                    .scaleY(hasFocus ? 1.03f : 1.0f)
                    .translationZ(hasFocus ? 6f : 0f)
                    .setDuration(120)
                    .start();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView details;
        final ImageView checkbox;

        ViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.fileIcon);
            name = v.findViewById(R.id.fileName);
            details = v.findViewById(R.id.fileDetails);
            checkbox = v.findViewById(R.id.fileCheckbox);
        }
    }
}
