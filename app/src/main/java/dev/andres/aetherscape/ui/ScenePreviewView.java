package dev.andres.aetherscape.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.render.LayeredCanvasRenderer;

/**
 * Settings preview backed by the exact same scene engine as the applied live
 * wallpaper. This prevents preview-only layouts that differ from the home
 * screen result.
 */
public final class ScenePreviewView extends View
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final SharedPreferences preferences;
    private LayeredCanvasRenderer renderer;
    private long lastNanos;

    public ScenePreviewView(Context context) {
        this(context, null);
    }

    public ScenePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        preferences = AppPreferences.get(context);
        setClickable(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (renderer == null) {
            renderer = new LayeredCanvasRenderer(getContext().getApplicationContext(), preferences);
            renderer.setPreview(true);
        }
        preferences.registerOnSharedPreferenceChangeListener(this);
        lastNanos = System.nanoTime();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        if (renderer != null) {
            renderer.dispose();
            renderer = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (renderer == null || getWidth() <= 0 || getHeight() <= 0) return;
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 1f / 30f : (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        renderer.draw(canvas, getWidth(), getHeight(), dt);
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (renderer == null) return super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            renderer.touch(event.getX() / Math.max(1f, getWidth()),
                    event.getY() / Math.max(1f, getHeight()));
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            renderer.releaseTouch();
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
        if (renderer != null) renderer.reloadPreferences();
        postInvalidateOnAnimation();
    }
}
