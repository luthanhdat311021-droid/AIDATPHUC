package com.studymind.nativeapp.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import com.studymind.nativeapp.BuildConfig;
import com.studymind.nativeapp.data.Async;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Minimal async image loader with an in-memory cache (avatars and the SePay VietQR image). */
public final class ImageLoader {
    private ImageLoader() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    public static void load(ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        // Avatars uploaded earlier through the web backend are stored as "/uploads/…"
        String full = url.startsWith("/") ? BuildConfig.WEB_URL + url : url;
        view.setTag(full);
        Bitmap cached = CACHE.get(full);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        Async.run(() -> {
            HttpURLConnection conn = (HttpURLConnection) new URL(full).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            } finally {
                conn.disconnect();
            }
        }, new Async.Callback<Bitmap>() {
            @Override public void onSuccess(Bitmap b) {
                if (b == null) return;
                CACHE.put(full, b);
                if (full.equals(view.getTag())) view.setImageBitmap(b);
            }
            @Override public void onError(String m) { /* keep the placeholder */ }
        });
    }
}
