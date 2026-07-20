package dev.andres.aetherscape.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.render.SceneRenderer;

/** Animated in-app preview using the same renderer as the live wallpaper. */
public final class ScenePreviewView extends View implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final SharedPreferences preferences;
    private final SceneRenderer renderer;
    private boolean running;
    private float touchParallax;

    public ScenePreviewView(Context context) {
        this(context, null);
    }

    public ScenePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        preferences = AppPreferences.get(context);
        renderer = new SceneRenderer(preferences);
        setFocusable(false);
        setClickable(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        preferences.registerOnSharedPreferenceChangeListener(this);
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        renderer.onSurfaceChanged(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        renderer.setLauncherOffset(touchParallax);
        renderer.draw(canvas, getWidth(), getHeight(), preferences, true);
        if (running) postInvalidateDelayed(Math.max(16L, 1000L / renderer.currentTargetFps()));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touchParallax = (event.getX() / Math.max(1f, getWidth()) - 0.5f) * 2f;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchParallax *= 0.35f;
                invalidate();
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        invalidate();
    }
}
