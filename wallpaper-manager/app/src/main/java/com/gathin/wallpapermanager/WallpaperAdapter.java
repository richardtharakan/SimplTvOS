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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.ViewHolder> {

    public interface OnWallpaperActionListener {
        void onWallpaperClicked(WallpaperItem item, int position);
        void onWallpaperLongClicked(WallpaperItem item, int position);
    }

    private final Context context;
    private final List<WallpaperItem> items = new ArrayList<>();
    private final OnWallpaperActionListener listener;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WallpaperAdapter(Context context, OnWallpaperActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<WallpaperItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public List<WallpaperItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallpaper, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WallpaperItem item = items.get(position);
        File file = item.getFile();

        holder.name.setText(file.getName());
        long kb = file.length() / 1024;
        holder.details.setText(kb + " KB");

        if (item.isActive()) {
            holder.imgCheckStatus.setImageResource(R.drawable.ic_check_circle);
            holder.txtStatus.setText("ACTIVE");
            holder.txtStatus.setTextColor(Color.parseColor("#00E676"));
            holder.badgeStatus.setBackgroundColor(Color.parseColor("#E60B1A12"));
        } else {
            holder.imgCheckStatus.setImageResource(R.drawable.ic_circle_outline);
            holder.txtStatus.setText("UNSELECTED");
            holder.txtStatus.setTextColor(Color.parseColor("#90A4AE"));
            holder.badgeStatus.setBackgroundColor(Color.parseColor("#E61E232E"));
        }

        // Set placeholder or async thumbnail
        holder.thumbnail.setImageDrawable(null);
        final String path = file.getAbsolutePath();
        holder.thumbnail.setTag(path);

        thumbnailExecutor.execute(() -> {
            Bitmap thumb = ImageCache.decodeSampledBitmap(file, 280, 160);
            mainHandler.post(() -> {
                if (path.equals(holder.thumbnail.getTag())) {
                    if (thumb != null) {
                        holder.thumbnail.setImageBitmap(thumb);
                    }
                }
            });
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos >= 0 && pos < items.size()) {
                    listener.onWallpaperClicked(items.get(pos), pos);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos >= 0 && pos < items.size()) {
                    listener.onWallpaperLongClicked(items.get(pos), pos);
                }
            }
            return true;
        });

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.05f : 1.0f)
                    .scaleY(hasFocus ? 1.05f : 1.0f)
                    .translationZ(hasFocus ? 8f : 0f)
                    .setDuration(120)
                    .start();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final View badgeStatus;
        final ImageView imgCheckStatus;
        final TextView txtStatus;
        final TextView name;
        final TextView details;

        ViewHolder(View v) {
            super(v);
            thumbnail = v.findViewById(R.id.wallpaperThumb);
            badgeStatus = v.findViewById(R.id.badgeStatus);
            imgCheckStatus = v.findViewById(R.id.imgCheckStatus);
            txtStatus = v.findViewById(R.id.txtStatus);
            name = v.findViewById(R.id.wallpaperName);
            details = v.findViewById(R.id.wallpaperDetails);
        }
    }
}
