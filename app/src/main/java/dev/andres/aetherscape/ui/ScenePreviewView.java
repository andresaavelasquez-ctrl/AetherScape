package dev.andres.aetherscape.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import dev.andres.aetherscape.R;

/** Lightweight static preview of the GPU art direction. The applied wallpaper is fully animated. */
public final class ScenePreviewView extends View {
    private final Bitmap preview;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private float parallax;

    public ScenePreviewView(Context context) { this(context, null); }

    public ScenePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        preview = BitmapFactory.decodeResource(getResources(), R.drawable.wallpaper_thumb);
        setClickable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (preview == null || getWidth() <= 0 || getHeight() <= 0) return;
        float scale = Math.max(getWidth() / (float) preview.getWidth(), getHeight() / (float) preview.getHeight());
        float w = preview.getWidth() * scale;
        float h = preview.getHeight() * scale;
        float extra = Math.max(0f, w - getWidth());
        float x = (getWidth() - w) * 0.5f + parallax * extra * 0.28f;
        float y = (getHeight() - h) * 0.5f;
        canvas.drawBitmap(preview, new Rect(0, 0, preview.getWidth(), preview.getHeight()),
                new RectF(x, y, x + w, y + h), paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            parallax = (event.getX() / Math.max(1f, getWidth()) - 0.5f) * 2f;
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            parallax *= 0.3f;
            invalidate();
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}
