package dev.andres.aetherscape.wallpaper;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.render.SceneRenderer;
import dev.andres.aetherscape.weather.WeatherClient;

/** Native live wallpaper service with adaptive FPS and visibility-aware rendering. */
public final class AetherWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new AetherEngine();
    }

    private final class AetherEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final HandlerThread renderThread = new HandlerThread("AetherWallpaperRenderer");
        private Handler renderHandler;
        private SharedPreferences preferences;
        private SceneRenderer renderer;
        private volatile boolean visible;
        private volatile boolean surfaceReady;
        private volatile float launcherOffset;
        private int width;
        private int height;

        private final Runnable frame = new Runnable() {
            @Override
            public void run() {
                if (!visible || !surfaceReady || renderHandler == null) return;
                drawFrame();
                int fps = renderer == null ? 30 : renderer.currentTargetFps();
                renderHandler.postDelayed(this, Math.max(16L, 1000L / Math.max(10, fps)));
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            AppPreferences.ensureDefaults(AetherWallpaperService.this);
            preferences = AppPreferences.get(AetherWallpaperService.this);
            renderer = new SceneRenderer(preferences);
            preferences.registerOnSharedPreferenceChangeListener(this);
            renderThread.start();
            renderHandler = new Handler(renderThread.getLooper());
            setTouchEventsEnabled(false);
        }

        @Override
        public void onDestroy() {
            visible = false;
            surfaceReady = false;
            if (renderHandler != null) renderHandler.removeCallbacksAndMessages(null);
            if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
            renderThread.quitSafely();
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (isVisible) {
                WeatherClient.refreshIfNeeded(AetherWallpaperService.this);
                scheduleNow();
            } else if (renderHandler != null) {
                renderHandler.removeCallbacks(frame);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int newWidth, int newHeight) {
            super.onSurfaceChanged(holder, format, newWidth, newHeight);
            width = newWidth;
            height = newHeight;
            surfaceReady = true;
            scheduleNow();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceReady = true;
            scheduleNow();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            if (renderHandler != null) renderHandler.removeCallbacks(frame);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep,
                                     int xPixelOffset, int yPixelOffset) {
            launcherOffset = (xOffset - 0.5f) * 2f;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (visible) scheduleNow();
        }

        private void scheduleNow() {
            if (renderHandler == null || !visible || !surfaceReady) return;
            renderHandler.removeCallbacks(frame);
            renderHandler.post(frame);
        }

        private void drawFrame() {
            Canvas canvas = null;
            SurfaceHolder holder = getSurfaceHolder();
            try {
                canvas = holder.lockCanvas();
                if (canvas == null || renderer == null) return;
                int w = width > 0 ? width : canvas.getWidth();
                int h = height > 0 ? height : canvas.getHeight();
                renderer.setLauncherOffset(launcherOffset);
                renderer.draw(canvas, w, h, preferences, false);
            } catch (RuntimeException ignored) {
                // Surface can disappear between visibility and lockCanvas callbacks.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                        // The surface was destroyed while posting.
                    }
                }
            }
        }
    }
}
