package dev.andres.aetherscape.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.render.SceneState;

/**
 * Animated lightweight preview using the same transparent layers as the GPU wallpaper.
 * It deliberately avoids creating a second libGDX context inside the settings process.
 */
public final class ScenePreviewView extends View
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final float WORLD_HEIGHT = 1000f;
    private static final float LAYER_WIDTH = 2400f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences preferences;
    private final List<Bitmap> owned = new ArrayList<>();

    private final Bitmap stars;
    private final Bitmap cloudsFar;
    private final Bitmap cloudsNear;
    private final Bitmap mountainsFar;
    private final Bitmap mountainsMid;
    private final Bitmap mountainsHero;
    private final Bitmap mountainsNear;
    private final Bitmap snowCaps;
    private final Bitmap fogValley;
    private final Bitmap forestFar;
    private final Bitmap forestMid;
    private final Bitmap hillMid;
    private final Bitmap hillFront;
    private final Bitmap pineTall;
    private final Bitmap pineMedium;
    private final Bitmap pineSparse;
    private final Bitmap pineDead;
    private final Bitmap lantern;
    private final Bitmap campfire;

    private SceneState state;
    private SceneState target;
    private long lastNanos;
    private float elapsed;
    private float scroll;
    private float parallax;
    private float pulse;
    private float touchX = 0.5f;
    private float touchY = 0.5f;

    public ScenePreviewView(Context context) {
        this(context, null);
    }

    public ScenePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        preferences = AppPreferences.get(context);
        state = SceneState.fromPreferences(preferences);
        target = state.copy();
        stars = asset("aether/layers/stars.png", 2);
        cloudsFar = asset("aether/layers/clouds_far.png", 2);
        cloudsNear = asset("aether/layers/clouds_near.png", 2);
        mountainsFar = asset("aether/layers/mountains_far.png", 2);
        mountainsMid = asset("aether/layers/mountains_mid.png", 2);
        mountainsHero = asset("aether/layers/mountains_hero.png", 2);
        mountainsNear = asset("aether/layers/mountains_near.png", 2);
        snowCaps = asset("aether/layers/snow_caps.png", 2);
        fogValley = asset("aether/layers/fog_valley.png", 2);
        forestFar = asset("aether/layers/forest_far.png", 2);
        forestMid = asset("aether/layers/forest_mid.png", 2);
        hillMid = asset("aether/layers/hill_mid.png", 2);
        hillFront = asset("aether/layers/hill_front.png", 2);
        pineTall = asset("aether/objects/pine_tall.png", 1);
        pineMedium = asset("aether/objects/pine_medium.png", 1);
        pineSparse = asset("aether/objects/pine_sparse.png", 1);
        pineDead = asset("aether/objects/pine_dead.png", 1);
        lantern = asset("aether/objects/lantern.png", 1);
        campfire = asset("aether/objects/campfire.png", 1);
        setClickable(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preferences.registerOnSharedPreferenceChangeListener(this);
        lastNanos = System.nanoTime();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 1f / 30f : (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        dt = Math.max(0f, Math.min(0.05f, dt));
        elapsed += dt;
        target = SceneState.fromPreferences(preferences);
        state.smoothToward(target, 1f - (float) Math.exp(-dt * 1.4f));
        scroll += dt * (18f + state.scrollSpeed * 30f) * (0.65f + state.motionIntensity * 0.65f);
        pulse = Math.max(0f, pulse - dt * 0.65f);

        float scale = getHeight() / WORLD_HEIGHT;
        float visibleWorldWidth = getWidth() / scale;
        drawSky(canvas);
        drawLayer(canvas, stars, scale, visibleWorldWidth, 0.002f, 0f,
                state.showStars ? 0.70f * state.nightFactor() * (1f - state.cloud * 0.78f) : 0f, 120f);
        drawCelestial(canvas, scale, visibleWorldWidth);
        drawLayer(canvas, cloudsFar, scale, visibleWorldWidth, 0.012f,
                2.2f + state.wind * 8f, 0.18f + state.cloud * 0.48f, 430f);
        drawLayer(canvas, mountainsFar, scale, visibleWorldWidth, 0.035f, 0f, 0.70f, 0f);
        drawLayer(canvas, fogValley, scale, visibleWorldWidth, 0.030f, 0.35f,
                0.10f + state.fog * 0.38f, 720f);
        drawLayer(canvas, mountainsMid, scale, visibleWorldWidth, 0.070f, 0f, 0.83f, 250f);
        drawLayer(canvas, mountainsHero, scale, visibleWorldWidth, 0.105f, 0f, 0.98f, 0f);
        float snow = state.snowCaps && (state.season == SceneState.Season.WINTER
                || state.temperatureC < 4f || state.snow > 0.16f)
                ? Math.min(0.94f, 0.32f + state.snow * 0.62f + (state.temperatureC < 4f ? 0.20f : 0f))
                : 0f;
        drawLayer(canvas, snowCaps, scale, visibleWorldWidth, 0.105f, 0f, snow, 0f);
        drawLayer(canvas, mountainsNear, scale, visibleWorldWidth, 0.155f, 0f, 0.98f, 610f);
        drawLayer(canvas, forestFar, scale, visibleWorldWidth, 0.205f, 0f, 0.75f, 830f);
        drawLayer(canvas, cloudsNear, scale, visibleWorldWidth, 0.030f,
                4.5f + state.wind * 16f, state.cloud * 0.70f, 980f);
        drawLayer(canvas, forestMid, scale, visibleWorldWidth, 0.285f, 0f, 0.92f, 1210f);
        drawLayer(canvas, hillMid, scale, visibleWorldWidth, 0.390f, 0f, 1f, 300f);
        drawObjects(canvas, scale, visibleWorldWidth, false);
        drawLayer(canvas, fogValley, scale, visibleWorldWidth, 0.150f, 0.55f,
                state.fog * 0.34f + state.rain * 0.06f, 1480f);
        drawLayer(canvas, hillFront, scale, visibleWorldWidth, 0.670f, 0f, 1f, 900f);
        drawObjects(canvas, scale, visibleWorldWidth, true);
        drawWeather(canvas);
        drawFireflies(canvas, scale, visibleWorldWidth);
        drawAtmosphere(canvas);

        postInvalidateOnAnimation();
    }

    private void drawSky(Canvas canvas) {
        int top = skyTopColor();
        int bottom = skyBottomColor();
        float gray = Math.min(0.55f, state.cloud * 0.21f + state.rain * 0.18f + state.storm * 0.18f);
        top = blend(top, Color.rgb(87, 92, 112), gray);
        bottom = blend(bottom, Color.rgb(87, 92, 112), gray);
        shapePaint.setShader(new LinearGradient(0, 0, 0, getHeight(), top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), shapePaint);
        shapePaint.setShader(null);
    }

    private void drawCelestial(Canvas canvas, float scale, float visibleWorldWidth) {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        boolean sun = hour >= 5.2f && hour <= 20f;
        float progress = sun
                ? clamp((hour - 5.5f) / 14f)
                : clamp(hour < 6f ? (hour + 6f) / 12f : (hour - 18f) / 12f);
        float worldX = -visibleWorldWidth * 0.32f + visibleWorldWidth * progress * 0.70f + parallax * 12f;
        float worldY = sun ? 650f + (float) Math.sin(progress * Math.PI) * 205f
                : 725f + (float) Math.sin(progress * Math.PI) * 105f;
        float x = (worldX + visibleWorldWidth * 0.5f) * scale;
        float y = (WORLD_HEIGHT - worldY) * scale;
        float radius = (sun ? 31f : 27f) * scale;
        float visibility = Math.max(0.12f, 1f - state.cloud * 0.66f);

        shapePaint.setShader(new RadialGradient(x, y, radius * (sun ? 6.2f : 4.8f),
                sun ? Color.argb((int) (112 * visibility), 255, 167, 108)
                        : Color.argb((int) (82 * visibility), 205, 220, 255),
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius * (sun ? 6.2f : 4.8f), shapePaint);
        shapePaint.setShader(null);
        shapePaint.setColor(sun
                ? Color.argb((int) (255 * visibility), 255, 240, 190)
                : Color.argb((int) (255 * visibility), 236, 239, 226));
        if (sun) {
            canvas.drawCircle(x, y, radius, shapePaint);
        } else {
            canvas.drawCircle(x, y, radius, shapePaint);
            shapePaint.setColor(blend(skyTopColor(), skyBottomColor(), 0.45f));
            canvas.drawCircle(x + radius * .40f, y - radius * .12f, radius * .92f, shapePaint);
        }
    }

    private void drawLayer(Canvas canvas, Bitmap bitmap, float scale, float visibleWorldWidth,
                           float depth, float driftPerSecond, float alpha, float phase) {
        if (bitmap == null || alpha <= 0.001f) return;
        float offset = positiveModulo(scroll * depth + elapsed * driftPerSecond
                + parallax * depth * 150f + phase, LAYER_WIDTH);
        float first = -LAYER_WIDTH * 1.5f - offset;
        float widthPx = LAYER_WIDTH * scale;
        float heightPx = WORLD_HEIGHT * scale;
        paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
        Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        int copies = Math.max(4, (int) Math.ceil(visibleWorldWidth / LAYER_WIDTH) + 4);
        for (int i = 0; i < copies; i++) {
            float worldX = first + i * LAYER_WIDTH;
            float x = (worldX + visibleWorldWidth * .5f) * scale;
            canvas.drawBitmap(bitmap, src, new RectF(x, 0, x + widthPx, heightPx), paint);
        }
        paint.setAlpha(255);
    }

    private void drawObjects(Canvas canvas, float scale, float visibleWorldWidth, boolean front) {
        float layerDepth = front ? .67f : .39f;
        float layerScroll = scroll * layerDepth;
        float segmentSize = 560f;
        int start = (int) Math.floor((layerScroll - visibleWorldWidth) / segmentSize) - 3;
        int end = start + (int) Math.ceil(visibleWorldWidth * 2f / segmentSize) + 7;
        for (int segment = start; segment <= end; segment++) {
            int biome = Math.abs(segment * 37) % 6;
            int count = front ? (biome == 1 ? 4 : (biome == 4 ? 1 : 2)) : 4;
            for (int i = 0; i < count; i++) {
                float worldX = segment * segmentSize + (front ? 35f : 55f)
                        + i * (front ? 132f : 105f) + hash01(segment * 211 + i * 17) * 68f;
                float screenWorldX = worldX - layerScroll;
                if (screenWorldX < -visibleWorldWidth * .75f || screenWorldX > visibleWorldWidth * .75f) continue;
                float terrain = front ? frontTerrain(worldX) : midTerrain(worldX);
                float heightWorld = front
                        ? 205f + hash01(segment * 97 + i * 23) * (biome == 1 ? 220f : 145f)
                        : 135f + hash01(segment * 47 + i * 11) * 125f;
                Bitmap tree = i % 4 == 0 ? pineDead : (i % 3 == 0 ? pineTall : (i % 2 == 0 ? pineSparse : pineMedium));
                drawObject(canvas, tree, screenWorldX, terrain - 5f, heightWorld, scale, visibleWorldWidth,
                        (float) Math.sin(elapsed * (.45f + state.wind) + segment + i) * state.wind * 2f);
            }
            if (front && (biome == 2 || biome == 5)) {
                float worldX = segment * segmentSize + segmentSize * .54f;
                float screenWorldX = worldX - layerScroll;
                drawGlow(canvas, screenWorldX, frontTerrain(worldX) + 115f, 52f + pulse * 20f,
                        scale, visibleWorldWidth, Color.rgb(255, 202, 126));
                drawObject(canvas, lantern, screenWorldX, frontTerrain(worldX) - 4f,
                        165f, scale, visibleWorldWidth, 0f);
            }
            if (front && state.showCampfires && biome == 3) {
                float worldX = segment * segmentSize + segmentSize * .62f;
                float screenWorldX = worldX - layerScroll;
                drawGlow(canvas, screenWorldX, frontTerrain(worldX) + 36f, 43f + pulse * 18f,
                        scale, visibleWorldWidth, Color.rgb(255, 142, 78));
                drawObject(canvas, campfire, screenWorldX, frontTerrain(worldX) - 2f,
                        67f, scale, visibleWorldWidth, 0f);
            }
        }
    }

    private void drawObject(Canvas canvas, Bitmap bitmap, float worldX, float baseY, float heightWorld,
                            float scale, float visibleWorldWidth, float rotation) {
        if (bitmap == null) return;
        float widthWorld = heightWorld * bitmap.getWidth() / (float) bitmap.getHeight();
        float x = (worldX + visibleWorldWidth * .5f) * scale;
        float bottom = (WORLD_HEIGHT - baseY) * scale;
        float width = widthWorld * scale;
        float height = heightWorld * scale;
        canvas.save();
        canvas.rotate(rotation, x + width * .5f, bottom);
        canvas.drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                new RectF(x, bottom - height, x + width, bottom), paint);
        canvas.restore();
    }

    private void drawGlow(Canvas canvas, float worldX, float worldY, float radiusWorld,
                          float scale, float visibleWorldWidth, int color) {
        float x = (worldX + visibleWorldWidth * .5f) * scale;
        float y = (WORLD_HEIGHT - worldY) * scale;
        float radius = radiusWorld * scale;
        shapePaint.setShader(new RadialGradient(x, y, radius,
                Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, shapePaint);
        shapePaint.setShader(null);
    }

    private void drawWeather(Canvas canvas) {
        if (state.rain > .02f) {
            shapePaint.setColor(Color.argb((int) (50 + state.rain * 70), 160, 181, 209));
            shapePaint.setStrokeWidth(1f + state.rain * 1.4f);
            int count = 38 + (int) (state.rain * 130f);
            for (int i = 0; i < count; i++) {
                float x = positiveModulo(hash01(i * 37) * getWidth() + elapsed * (65f + state.wind * 180f), getWidth());
                float y = positiveModulo(hash01(i * 97 + 17) * getHeight() - elapsed * (240f + state.rain * 320f), getHeight());
                canvas.drawLine(x, y, x + 3f + state.wind * 10f, y + 8f + state.rain * 13f, shapePaint);
            }
        }
        if (state.snow > .02f) {
            shapePaint.setColor(Color.argb((int) (120 + state.snow * 90), 238, 241, 244));
            int count = 22 + (int) (state.snow * 70f);
            for (int i = 0; i < count; i++) {
                float x = positiveModulo(hash01(i * 67) * getWidth() + (float) Math.sin(elapsed + i) * 18f, getWidth());
                float y = positiveModulo(hash01(i * 89) * getHeight() + elapsed * (25f + state.snow * 40f), getHeight());
                canvas.drawCircle(x, y, 1.2f + hash01(i * 13) * 2.2f, shapePaint);
            }
        }
    }

    private void drawFireflies(Canvas canvas, float scale, float visibleWorldWidth) {
        if (!state.showFireflies) return;
        float amount = Math.max(.26f, state.nightFactor()) * (1f - state.rain * .8f) * state.effectIntensity;
        int count = 8 + (int) (amount * 18f);
        for (int i = 0; i < count; i++) {
            float worldX = -visibleWorldWidth * .5f + hash01(i * 41 + 3) * visibleWorldWidth
                    + (float) Math.sin(elapsed * (.32f + hash01(i * 13) * .42f) + i) * 24f;
            float worldY = 145f + hash01(i * 61 + 7) * 285f
                    + (float) Math.cos(elapsed * (.40f + hash01(i * 17) * .48f) + i) * 15f;
            if (pulse > .01f && i < 10) {
                float targetWorldX = (touchX - .5f) * visibleWorldWidth;
                float targetWorldY = (1f - touchY) * WORLD_HEIGHT;
                worldX = lerp(worldX, targetWorldX + (float) Math.sin(i * 2.1f) * (35f + i * 3f), pulse * .68f);
                worldY = lerp(worldY, targetWorldY + (float) Math.cos(i * 1.7f) * (25f + i * 2f), pulse * .42f);
            }
            float x = (worldX + visibleWorldWidth * .5f) * scale;
            float y = (WORLD_HEIGHT - worldY) * scale;
            float flicker = .55f + .45f * (float) Math.sin(elapsed * 1.7f + i * 1.31f);
            shapePaint.setShader(new RadialGradient(x, y, 9f + flicker * 5f,
                    Color.argb((int) (130 * flicker), 255, 214, 112),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, 12f, shapePaint);
            shapePaint.setShader(null);
        }
    }

    private void drawAtmosphere(Canvas canvas) {
        if (state.rain > .04f || state.fog > .04f) {
            shapePaint.setColor(Color.argb((int) ((state.rain * .075f + state.fog * .095f) * 255),
                    74, 79, 104));
            canvas.drawRect(0, 0, getWidth(), getHeight(), shapePaint);
        }
        shapePaint.setShader(new LinearGradient(0, getHeight() * .55f, 0, getHeight(),
                Color.TRANSPARENT, Color.argb(72, 3, 8, 18), Shader.TileMode.CLAMP));
        canvas.drawRect(0, getHeight() * .55f, getWidth(), getHeight(), shapePaint);
        shapePaint.setShader(null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            touchX = event.getX() / Math.max(1f, getWidth());
            touchY = event.getY() / Math.max(1f, getHeight());
            parallax = (touchX - .5f) * 2f;
            pulse = 1f;
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            parallax *= .35f;
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
        target = SceneState.fromPreferences(sharedPreferences);
        postInvalidateOnAnimation();
    }

    private Bitmap asset(String path, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sampleSize);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = getContext().getAssets().open(path)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            if (bitmap != null) owned.add(bitmap);
            return bitmap;
        } catch (IOException ignored) {
            return null;
        }
    }

    private int skyTopColor() {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        if (hour < 5f) return Color.rgb(12, 16, 35);
        if (hour < 8f) return blend(Color.rgb(48, 54, 94), Color.rgb(120, 122, 163), (hour - 5f) / 3f);
        if (hour < 17f) return Color.rgb(99, 153, 184);
        if (hour < 21f) return blend(Color.rgb(94, 127, 160), Color.rgb(31, 33, 66), (hour - 17f) / 4f);
        return Color.rgb(11, 14, 32);
    }

    private int skyBottomColor() {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        if (hour < 5f) return Color.rgb(38, 33, 61);
        if (hour < 8f) return blend(Color.rgb(99, 77, 112), Color.rgb(232, 158, 135), (hour - 5f) / 3f);
        if (hour < 17f) return Color.rgb(184, 196, 186);
        if (hour < 21f) return blend(Color.rgb(232, 174, 140), Color.rgb(77, 54, 94), (hour - 17f) / 4f);
        return Color.rgb(41, 31, 61);
    }

    private static int blend(int a, int b, float t) {
        t = clamp(t);
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static float midTerrain(float worldX) {
        float local = positiveModulo(worldX, LAYER_WIDTH) / LAYER_WIDTH * (float) (Math.PI * 2.0);
        return 205f + (float) Math.sin(local + .35f) * 34f
                + (float) Math.sin(local * 2f + 1.15f) * 18f;
    }

    private static float frontTerrain(float worldX) {
        float local = positiveModulo(worldX, LAYER_WIDTH) / LAYER_WIDTH * (float) (Math.PI * 2.0);
        return 86f + (float) Math.sin(local + 1.05f) * 25f
                + (float) Math.sin(local * 2f + .25f) * 12f;
    }

    private static float positiveModulo(float value, float max) {
        float result = value % max;
        return result < 0f ? result + max : result;
    }

    private static float hash01(int value) {
        int x = value;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return (x & 0x7fffffff) / (float) 0x7fffffff;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
