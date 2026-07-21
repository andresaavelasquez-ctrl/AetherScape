package dev.andres.aetherscape.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide bitmap pool shared by the live wallpaper and the
 * configuration preview. If the wallpaper already owns a high-resolution
 * layer, opening the app reuses that exact bitmap instead of decoding a second
 * copy. Entries are reference-counted and recycled after their final user exits.
 */
final class SceneBitmapPool {
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private SceneBitmapPool() {}

    static synchronized Bitmap acquire(Context context, String path, int requestedSample) {
        int sample = Math.max(1, requestedSample);
        Entry best = null;
        for (Entry entry : ENTRIES) {
            if (!entry.path.equals(path) || entry.bitmap == null || entry.bitmap.isRecycled()) continue;
            // Reuse only the same decoding profile. This prevents the tiny app
            // preview from drawing the wallpaper's much larger source textures.
            if (entry.sample == sample) {
                best = entry;
                break;
            }
        }
        if (best != null) {
            best.references++;
            return best.bitmap;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        options.inMutable = true; // Forces a software bitmap compatible with the cached Canvas.
        try (InputStream stream = context.getAssets().open(path)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            if (bitmap != null) ENTRIES.add(new Entry(path, sample, bitmap));
            return bitmap;
        } catch (IOException ignored) {
            return null;
        }
    }

    static synchronized void release(Bitmap bitmap) {
        if (bitmap == null) return;
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry entry = ENTRIES.get(i);
            if (entry.bitmap != bitmap) continue;
            entry.references--;
            if (entry.references <= 0) {
                if (!entry.bitmap.isRecycled()) entry.bitmap.recycle();
                ENTRIES.remove(i);
            }
            return;
        }
        // Non-pooled emergency assets can still be safely released here.
        if (!bitmap.isRecycled()) bitmap.recycle();
    }

    private static final class Entry {
        final String path;
        final int sample;
        final Bitmap bitmap;
        int references = 1;

        Entry(String path, int sample, Bitmap bitmap) {
            this.path = path;
            this.sample = sample;
            this.bitmap = bitmap;
        }
    }
}
