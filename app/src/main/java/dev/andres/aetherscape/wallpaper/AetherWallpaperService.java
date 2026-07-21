package dev.andres.aetherscape.wallpaper;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.render.LayeredCanvasRenderer;
import dev.andres.aetherscape.weather.WeatherClient;

/**
 * Compatibility-first live wallpaper engine.
 *
 * This service renders directly into Android's WallpaperService surface and
 * does not depend on a secondary OpenGL lifecycle. It fixes the black screen
 * reported after applying the previous GPU build while keeping the layered,
 * animated visual system.
 */
public class AetherWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new AetherEngine();
    }

    private final class AetherEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final HandlerThread renderThread = new HandlerThread("AetherNativeLayerEngine");
        private Handler renderHandler;
        private SharedPreferences preferences;
        private volatile LayeredCanvasRenderer renderer;
        private volatile boolean visible;
        private volatile boolean surfaceReady;
        private int width = 1;
        private int height = 1;
        private long lastFrameNanos;

        private final Runnable frame = new Runnable() {
            @Override
            public void run() {
                if (!surfaceReady || renderHandler == null) return;
                drawFrame();
                if (visible && surfaceReady) {
                    int fps = renderer == null ? 30 : renderer.recommendedFps();
                    renderHandler.postDelayed(this, Math.max(16L, 1000L / Math.max(15, fps)));
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            surfaceHolder.setFormat(PixelFormat.RGBA_8888);
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(true);

            AppPreferences.ensureDefaults(AetherWallpaperService.this);
            preferences = AppPreferences.get(AetherWallpaperService.this);
            preferences.registerOnSharedPreferenceChangeListener(this);
            renderThread.start();
            renderHandler = new Handler(renderThread.getLooper());
            // Decode the large scene assets on the dedicated rendering thread,
            // never on Android's main thread or the launcher animation thread.
            renderHandler.post(() -> {
                if (!renderThread.isAlive()) return;
                renderer = new LayeredCanvasRenderer(
                        AetherWallpaperService.this.getApplicationContext(), preferences, false);
                schedule(true);
            });
        }

        @Override
        public void onDestroy() {
            visible = false;
            surfaceReady = false;
            if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
            Handler handler = renderHandler;
            if (handler != null) {
                handler.removeCallbacks(frame);
                handler.post(() -> {
                    LayeredCanvasRenderer current = renderer;
                    renderer = null;
                    if (current != null) current.dispose();
                });
            }
            renderThread.quitSafely();
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (isVisible) {
                WeatherClient.refreshIfNeeded(AetherWallpaperService.this);
                schedule(true);
            } else if (renderHandler != null) {
                renderHandler.removeCallbacks(frame);
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceReady = true;
            lastFrameNanos = 0L;
            // Draw once even before the launcher reports visibility. This avoids
            // Android displaying a black placeholder during wallpaper switching.
            schedule(true);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int newWidth, int newHeight) {
            super.onSurfaceChanged(holder, format, newWidth, newHeight);
            width = Math.max(1, newWidth);
            height = Math.max(1, newHeight);
            surfaceReady = true;
            lastFrameNanos = 0L;
            schedule(true);
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
            schedule(true);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            if (renderHandler != null) renderHandler.removeCallbacks(frame);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep,
                                     float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            Handler handler = renderHandler;
            if (handler != null) handler.post(() -> {
                LayeredCanvasRenderer current = renderer;
                if (current != null) current.setLauncherOffset((xOffset - 0.5f) * 2f);
                schedule(false);
            });
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            final int action = event.getActionMasked();
            final float nx = event.getX() / Math.max(1f, width);
            final float ny = event.getY() / Math.max(1f, height);
            Handler handler = renderHandler;
            if (handler != null) handler.post(() -> {
                LayeredCanvasRenderer current = renderer;
                if (current == null) return;
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    current.touch(nx, ny);
                    schedule(false);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    current.releaseTouch();
                }
            });
            super.onTouchEvent(event);
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras,
                                boolean resultRequested) {
            if ("android.wallpaper.tap".equals(action)
                    || "android.wallpaper.secondaryTap".equals(action)
                    || "android.home.drop".equals(action)) {
                final float nx = x / Math.max(1f, width);
                final float ny = y / Math.max(1f, height);
                Handler handler = renderHandler;
                if (handler != null) handler.post(() -> {
                    LayeredCanvasRenderer current = renderer;
                    if (current == null) return;
                    current.touch(nx, ny);
                    current.pulseLights();
                    schedule(false);
                });
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Handler handler = renderHandler;
            if (handler != null) handler.post(() -> {
                LayeredCanvasRenderer current = renderer;
                if (current != null) current.reloadPreferences();
                schedule(false);
            });
        }

        private void schedule(boolean forceOneFrame) {
            if (renderHandler == null || !surfaceReady) return;
            renderHandler.removeCallbacks(frame);
            if (forceOneFrame || visible) renderHandler.post(frame);
        }

        private void drawFrame() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                try {
                    canvas = holder.lockHardwareCanvas();
                } catch (Throwable hardwareFailure) {
                    canvas = holder.lockCanvas();
                }
                if (canvas == null) return;
                int actualWidth = width > 1 ? width : canvas.getWidth();
                int actualHeight = height > 1 ? height : canvas.getHeight();
                long now = System.nanoTime();
                float dt = lastFrameNanos == 0L
                        ? 1f / 30f
                        : (now - lastFrameNanos) / 1_000_000_000f;
                lastFrameNanos = now;
                if (renderer != null) {
                    renderer.draw(canvas, actualWidth, actualHeight, dt);
                } else {
                    canvas.drawColor(android.graphics.Color.rgb(9, 13, 26));
                }
            } catch (Throwable renderingFailure) {
                if (canvas != null && renderer != null) {
                    try {
                        renderer.drawEmergencyFrame(canvas,
                                width > 1 ? width : canvas.getWidth(),
                                height > 1 ? height : canvas.getHeight());
                    } catch (Throwable ignored) {
                        canvas.drawColor(android.graphics.Color.rgb(9, 13, 26));
                    }
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Throwable ignored) {
                        // Surface was replaced while the frame was being posted.
                    }
                }
            }
        }
    }
}
