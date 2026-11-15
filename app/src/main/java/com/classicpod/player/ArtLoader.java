package com.classicpod.player;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ArtLoader {
    private static final Uri ALBUM_ART = Uri.parse("content://media/external/audio/albumart");
    private static ArtLoader instance;

    private final Context app;
    private final LruCache<Long, Bitmap> cache;
    private final ExecutorService exec;
    private final Handler main = new Handler(Looper.getMainLooper());

    public static synchronized ArtLoader get(Context context) {
        if (instance == null) {
            instance = new ArtLoader(context.getApplicationContext());
        }
        return instance;
    }

    private ArtLoader(Context app) {
        this.app = app;
        int max = (int) (Runtime.getRuntime().maxMemory() / 16L);
        cache = new LruCache<Long, Bitmap>(max) {
            @Override
            protected int sizeOf(Long key, Bitmap value) {
                return value.getByteCount();
            }
        };
        exec = Executors.newFixedThreadPool(1);
    }

    public void bind(ImageView view, long albumId, int sizePx) {
        view.setTag(Long.valueOf(albumId));
        Bitmap cached = cache.get(Long.valueOf(albumId));
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(R.drawable.artwork_placeholder);
        if (albumId <= 0) {
            return;
        }
        exec.execute(new LoadJob(view, albumId, sizePx));
    }

    public Bitmap peek(long albumId) {
        Bitmap b = cache.get(Long.valueOf(albumId));
        if (b == null || b.isRecycled()) {
            return null;
        }
        return b;
    }

    public Bitmap loadSync(long albumId, int sizePx) {
        Bitmap cached = peek(albumId);
        if (cached != null) {
            return cached;
        }
        Bitmap decoded = decode(albumId, sizePx);
        if (decoded != null) {
            cache.put(Long.valueOf(albumId), decoded);
        }
        return decoded;
    }

    private Bitmap decode(long albumId, int sizePx) {
        Uri uri = ContentUris.withAppendedId(ALBUM_ART, albumId);
        InputStream in = null;
        try {
            in = app.getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            in.close();
            in = app.getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inSampleSize = sample(bounds.outWidth, bounds.outHeight, sizePx);
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static int sample(int w, int h, int target) {
        int sample = 1;
        int max = Math.max(w, h);
        while (max / sample > target * 2 && sample < 16) {
            sample *= 2;
        }
        return sample;
    }

    private final class LoadJob implements Runnable {
        private final ImageView view;
        private final long albumId;
        private final int sizePx;

        LoadJob(ImageView view, long albumId, int sizePx) {
            this.view = view;
            this.albumId = albumId;
            this.sizePx = sizePx;
        }

        @Override
        public void run() {
            final Bitmap bmp = decode(albumId, sizePx);
            if (bmp != null) {
                cache.put(Long.valueOf(albumId), bmp);
            }
            main.post(new Runnable() {
                @Override
                public void run() {
                    Object tag = view.getTag();
                    if (tag instanceof Long && ((Long) tag).longValue() == albumId) {
                        if (bmp != null) {
                            view.setImageBitmap(bmp);
                        } else {
                            view.setImageResource(R.drawable.artwork_placeholder);
                        }
                    }
                }
            });
        }
    }
}
