package dev.andres.aetherscape.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.render.LayeredCanvasRenderer;

/**
 * Efficient live preview using the same renderer as the wallpaper. Heavy
 * texture decoding happens away from Android's UI thread, the preview stops
 * when hidden, and idle animation uses adaptive frame pacing.
 */
public final class ScenePreviewView extends View
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "AetherPreviewAssetLoader");
        thread.setDaemon(true);
        return thread;
    });

    private final SharedPreferences preferences;
    private final Paint loadingPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private volatile LayeredCanvasRenderer renderer;
    private long lastNanos;
    private boolean active;
    private int loadGeneration;

    public ScenePreviewView(Context context) {
        this(context, null);
    }

    public ScenePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        preferences = AppPreferences.get(context);
        setClickable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preferences.registerOnSharedPreferenceChangeListener(this);
        active = getWindowVisibility() == VISIBLE && getVisibility() == VISIBLE;
        lastNanos = System.nanoTime();
        loadRendererAsync();
        if (active) postInvalidateOnAnimation();
    }

    private void loadRendererAsync() {
        if (renderer != null) return;
        final int generation = ++loadGeneration;
        final Context app = getContext().getApplicationContext();
        LOADER.execute(() -> {
            LayeredCanvasRenderer loaded = new LayeredCanvasRenderer(app, preferences, true);
            post(() -> {
                if (generation == loadGeneration && isAttachedToWindow()) {
                    renderer = loaded;
                    lastNanos = System.nanoTime();
                    invalidate();
                } else {
                    loaded.dispose();
                }
            });
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        active = false;
        loadGeneration++;
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        LayeredCanvasRenderer current = renderer;
        renderer = null;
        if (current != null) LOADER.execute(current::dispose);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        active = visibility == VISIBLE && getVisibility() == VISIBLE && isAttachedToWindow();
        if (active) {
            lastNanos = System.nanoTime();
            invalidate();
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        active = visibility == VISIBLE && getWindowVisibility() == VISIBLE && isAttachedToWindow();
        if (active) invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active || getWidth() <= 0 || getHeight() <= 0) return;
        LayeredCanvasRenderer current = renderer;
        if (current == null) {
            loadingPaint.setShader(new LinearGradient(0, 0, 0, getHeight(),
                    Color.rgb(13, 19, 34), Color.rgb(57, 52, 80), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), loadingPaint);
            loadingPaint.setShader(null);
            postInvalidateDelayed(250L);
            return;
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 1f / 20f : (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        current.draw(canvas, getWidth(), getHeight(), dt);
        int fps = current.recommendedFps();
        postInvalidateDelayed(Math.max(16L, 1000L / Math.max(10, fps)));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        LayeredCanvasRenderer current = renderer;
        if (current == null) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            current.touch(event.getX() / Math.max(1f, getWidth()),
                    event.getY() / Math.max(1f, getHeight()));
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            current.releaseTouch();
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        LayeredCanvasRenderer current = renderer;
        if (current != null) current.reloadPreferences();
        if (active) invalidate();
    }
}
