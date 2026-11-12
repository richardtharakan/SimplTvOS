package com.gathin.wallpapermanager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.File;

public final class ImageCache {

    private static final int CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 8);
    private static final LruCache<String, Bitmap> memoryCache = new LruCache<String, Bitmap>(CACHE_SIZE) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    public static Bitmap get(String key) {
        return memoryCache.get(key);
    }

    public static void put(String key, Bitmap bitmap) {
        if (key != null && bitmap != null) {
            memoryCache.put(key, bitmap);
        }
    }

    public static Bitmap decodeSampledBitmap(File file, int reqWidth, int reqHeight) {
        if (file == null || !file.exists() || file.length() == 0) return null;
        String cacheKey = file.getAbsolutePath() + "_" + reqWidth + "x" + reqHeight;
        Bitmap cached = get(cacheKey);
        if (cached != null && !cached.isRecycled()) return cached;

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565; // save 50% RAM

            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bmp != null) {
                put(cacheKey, bmp);
            }
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
