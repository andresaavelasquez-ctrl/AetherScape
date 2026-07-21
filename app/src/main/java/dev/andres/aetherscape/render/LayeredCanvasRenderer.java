package dev.andres.aetherscape.render;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import dev.andres.aetherscape.prefs.AppPreferences;

/**
 * Hardware-accelerated Canvas scene engine shared by the settings preview and
 * the actual Android live wallpaper.
 *
 * The renderer uses a fixed 1000-unit vertical world, so rotating the device
 * changes the visible width instead of stretching mountains and trees. Scene
 * objects are placed by curated segment templates rather than unconstrained
 * random placement, keeping the hero mountain and horizon readable.
 */
public final class LayeredCanvasRenderer {
    private static final float WORLD_HEIGHT = 1000f;
    private static final float LAYER_WIDTH = 2400f;
    private static final float SEGMENT_WIDTH = 960f;

    private final SharedPreferences preferences;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint weatherPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
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

    private float celestialWorldX;
    private float celestialWorldY;
    private float celestialVisibility;
    private boolean celestialSun;

    private SceneState state;
    private SceneState target;
    private float elapsed;
    private float scroll;
    private float launcherOffset;
    private float previewOffset;
    private float touchX = 0.5f;
    private float touchY = 0.5f;
    private float pulse;
    private float gustPulse;
    private boolean preview;

    public LayeredCanvasRenderer(Context context, SharedPreferences preferences) {
        this.preferences = preferences;
        state = SceneState.fromPreferences(preferences);
        target = state.copy();

        stars = asset(context, "aether/layers/stars.png", 1);
        cloudsFar = asset(context, "aether/layers/clouds_far.png", 1);
        cloudsNear = asset(context, "aether/layers/clouds_near.png", 1);
        mountainsFar = asset(context, "aether/layers/mountains_far.png", 1);
        mountainsMid = asset(context, "aether/layers/mountains_mid.png", 1);
        mountainsHero = asset(context, "aether/layers/mountains_hero.png", 1);
        mountainsNear = asset(context, "aether/layers/mountains_near.png", 1);
        snowCaps = asset(context, "aether/layers/snow_caps.png", 1);
        fogValley = asset(context, "aether/layers/fog_valley.png", 1);
        forestFar = asset(context, "aether/layers/forest_far.png", 1);
        forestMid = asset(context, "aether/layers/forest_mid.png", 1);
        hillMid = asset(context, "aether/layers/hill_mid.png", 1);
        hillFront = asset(context, "aether/layers/hill_front.png", 1);

        pineTall = asset(context, "aether/objects/pine_tall.png", 1);
        pineMedium = asset(context, "aether/objects/pine_medium.png", 1);
        pineSparse = asset(context, "aether/objects/pine_sparse.png", 1);
        pineDead = asset(context, "aether/objects/pine_dead.png", 1);
        lantern = asset(context, "aether/objects/lantern.png", 1);
        campfire = asset(context, "aether/objects/campfire.png", 1);
    }

    public int targetFps() {
        return Math.max(15, Math.min(60, state.targetFps));
    }

    public void setPreview(boolean preview) {
        this.preview = preview;
    }

    public void setLauncherOffset(float offset) {
        launcherOffset = clampSigned(offset);
    }

    public void touch(float normalizedX, float normalizedY) {
        touchX = clamp01(normalizedX);
        touchY = clamp01(normalizedY);
        previewOffset = clampSigned((touchX - 0.5f) * 2f);
        pulse = 1f;
        gustPulse = 1f;
    }

    public void releaseTouch() {
        previewOffset *= 0.35f;
    }

    public void pulseLights() {
        pulse = 1f;
        gustPulse = Math.max(gustPulse, 0.35f);
    }

    public void reloadPreferences() {
        target = SceneState.fromPreferences(preferences);
    }

    public void draw(Canvas canvas, int width, int height, float deltaSeconds) {
        if (canvas == null || width <= 0 || height <= 0) return;
        float dt = Math.max(0f, Math.min(0.05f, deltaSeconds));
        if (dt <= 0f) dt = 1f / Math.max(15, targetFps());

        elapsed += dt;
        target = SceneState.fromPreferences(preferences);
        state.smoothToward(target, 1f - (float) Math.exp(-dt * 1.5f));
        pulse = Math.max(0f, pulse - dt * 0.72f);
        gustPulse = Math.max(0f, gustPulse - dt * 0.30f);

        float speed = (9f + state.scrollSpeed * 23f) * (0.48f + state.motionIntensity * 0.68f);
        if (preview) speed *= 1.20f;
        scroll += dt * speed;
        if (scroll > 1_000_000f) scroll -= 1_000_000f;

        float scale = height / WORLD_HEIGHT;
        float visibleWorldWidth = width / Math.max(0.001f, scale);
        float automatic = (float) Math.sin(elapsed * 0.055f) * (preview ? 0.055f : 0.026f);
        float parallax = state.parallax
                ? clampSigned(launcherOffset + previewOffset + automatic)
                : 0f;

        canvas.drawColor(Color.rgb(7, 11, 22));
        drawSky(canvas, width, height);

        drawLayer(canvas, stars, scale, visibleWorldWidth, parallax,
                0.002f, 0f,
                state.showStars ? 0.72f * state.nightFactor() * (1f - state.cloud * 0.80f) : 0f,
                100f, 0f, 1f, null);
        drawCelestial(canvas, scale, visibleWorldWidth, parallax);
        drawCelestialAtmosphere(canvas, width, height, scale, visibleWorldWidth);

        drawLayer(canvas, cloudsFar, scale, visibleWorldWidth, parallax,
                0.010f, 1.8f + effectiveWind() * 7f,
                0.14f + state.cloud * 0.44f,
                360f, 0f, 1f, atmosphericFilter(0.94f));

        drawLayer(canvas, mountainsFar, scale, visibleWorldWidth, parallax,
                0.025f, 0f, 0.62f,
                0f, 0f, 1f, atmosphericFilter(0.90f));
        drawLayer(canvas, fogValley, scale, visibleWorldWidth, parallax,
                0.028f, 0.24f,
                0.08f + state.fog * 0.30f,
                670f, 0f, 1f, null);
        drawLayer(canvas, mountainsMid, scale, visibleWorldWidth, parallax,
                0.055f, 0f, 0.78f,
                190f, 0f, 1f, atmosphericFilter(0.96f));

        // The hero range remains the visual focus. It moves very slowly and is
        // deliberately left clear by the front object templates.
        drawLayer(canvas, mountainsHero, scale, visibleWorldWidth, parallax,
                0.078f, 0f, 0.98f,
                0f, 0f, 1f, seasonalMountainFilter());
        float snow = state.snowCaps && (state.season == SceneState.Season.WINTER
                || state.temperatureC < 4f || state.snow > 0.14f)
                ? Math.min(0.94f, 0.28f + state.snow * 0.64f
                + (state.temperatureC < 4f ? 0.22f : 0f))
                : 0f;
        drawLayer(canvas, snowCaps, scale, visibleWorldWidth, parallax,
                0.078f, 0f, snow,
                0f, 0f, 1f, null);

        drawLayer(canvas, mountainsNear, scale, visibleWorldWidth, parallax,
                0.115f, 0f, 0.88f,
                510f, 0f, 1f, seasonalMountainFilter());
        drawLayer(canvas, forestFar, scale, visibleWorldWidth, parallax,
                0.165f, 0f, 0.64f,
                760f, 0f, 1f, forestFilter(0.82f));
        drawLayer(canvas, forestMid, scale, visibleWorldWidth, parallax,
                0.225f, 0f, 0.78f,
                1110f, 0f, 1f, forestFilter(0.92f));

        drawLayer(canvas, hillMid, scale, visibleWorldWidth, parallax,
                0.320f, 0f, 1f,
                240f, 0f, 1f, null);
        drawBackObjects(canvas, scale, visibleWorldWidth, parallax);

        drawLayer(canvas, fogValley, scale, visibleWorldWidth, parallax,
                0.120f, 0.40f,
                state.fog * 0.28f + state.rain * 0.05f,
                1370f, -40f, 1.04f, null);
        drawLayer(canvas, cloudsNear, scale, visibleWorldWidth, parallax,
                0.024f, 3.5f + effectiveWind() * 13f,
                state.cloud * 0.56f,
                900f, 0f, 1f, atmosphericFilter(0.86f));

        drawLayer(canvas, hillFront, scale, visibleWorldWidth, parallax,
                0.580f, 0f, 1f,
                780f, 0f, 1f, null);
        drawGroundLight(canvas, width, height, scale, visibleWorldWidth);
        drawFrontObjects(canvas, scale, visibleWorldWidth, parallax);

        drawFireflies(canvas, scale, visibleWorldWidth);
        drawWeather(canvas, width, height);
        drawAtmosphere(canvas, width, height);
    }

    private void drawSky(Canvas canvas, int width, int height) {
        int top = skyTopColor();
        int bottom = skyBottomColor();
        float gray = Math.min(0.58f, state.cloud * 0.22f + state.rain * 0.20f + state.storm * 0.20f);
        top = blend(top, Color.rgb(82, 87, 108), gray);
        bottom = blend(bottom, Color.rgb(92, 91, 108), gray * 0.92f);
        shapePaint.setShader(new LinearGradient(0, 0, 0, height, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, shapePaint);
        shapePaint.setShader(null);

        // Cooler upper vignette and warmer horizon haze add cinematic depth.
        shapePaint.setShader(new LinearGradient(0, 0, 0, height * 0.52f,
                Color.argb(42, 6, 10, 24), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height * 0.52f, shapePaint);
        shapePaint.setShader(null);

        int horizonAlpha = (int) (34 * (0.34f + state.effectIntensity * 0.66f) * (1f - state.rain * 0.55f));
        shapePaint.setShader(new LinearGradient(0, height * 0.48f, 0, height,
                Color.TRANSPARENT, Color.argb(horizonAlpha, 255, 173, 126), Shader.TileMode.CLAMP));
        canvas.drawRect(0, height * 0.48f, width, height, shapePaint);
        shapePaint.setShader(null);
    }

    private void drawCelestial(Canvas canvas, float scale, float visibleWorldWidth, float parallax) {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        boolean sun = hour >= 5.2f && hour <= 20f;
        float progress = sun
                ? clamp01((hour - 5.5f) / 14f)
                : clamp01(hour < 6f ? (hour + 6f) / 12f : (hour - 18f) / 12f);
        float worldX = -visibleWorldWidth * 0.29f + visibleWorldWidth * progress * 0.67f + parallax * 10f;
        float worldY = sun
                ? 655f + (float) Math.sin(progress * Math.PI) * 208f
                : 728f + (float) Math.sin(progress * Math.PI) * 108f;
        float x = (worldX + visibleWorldWidth * 0.5f) * scale;
        float y = (WORLD_HEIGHT - worldY) * scale;
        float radius = (sun ? 34f : 27f) * scale;
        float visibility = Math.max(0.10f, 1f - state.cloud * 0.72f);

        celestialWorldX = worldX;
        celestialWorldY = worldY;
        celestialVisibility = visibility;
        celestialSun = sun;

        if (state.showGlow) {
            float outerGlowRadius = radius * (sun ? 9.1f : 6.4f);
            int outer = sun
                    ? Color.argb((int) (58 * visibility), 255, 182, 125)
                    : Color.argb((int) (36 * visibility), 210, 221, 255);
            shapePaint.setShader(new RadialGradient(x, y, outerGlowRadius, outer, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, outerGlowRadius, shapePaint);
            shapePaint.setShader(null);

            float innerGlowRadius = radius * (sun ? 5.8f : 4.3f);
            int center = sun
                    ? Color.argb((int) (112 * visibility), 255, 174, 112)
                    : Color.argb((int) (82 * visibility), 205, 219, 255);
            shapePaint.setShader(new RadialGradient(x, y, innerGlowRadius, center, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, innerGlowRadius, shapePaint);
            shapePaint.setShader(null);
        }

        if (sun) {
            // Main sun disc with soft spherical shading.
            shapePaint.setShader(new RadialGradient(x - radius * 0.26f, y - radius * 0.24f, radius * 1.55f,
                    Color.argb((int) (255 * visibility), 255, 247, 206),
                    Color.argb((int) (255 * visibility), 255, 228, 164), Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, radius, shapePaint);
            shapePaint.setShader(null);
            shapePaint.setColor(Color.argb((int) (48 * visibility), 224, 164, 110));
            canvas.drawCircle(x + radius * 0.18f, y + radius * 0.16f, radius * 0.96f, shapePaint);
        } else {
            // Full moon body with earthshine and a soft shadow that leaves a crescent.
            shapePaint.setColor(Color.argb((int) (48 * visibility), 188, 196, 220));
            canvas.drawCircle(x, y, radius * 1.04f, shapePaint);
            shapePaint.setColor(Color.argb((int) (255 * visibility), 236, 239, 228));
            canvas.drawCircle(x, y, radius, shapePaint);
            shapePaint.setColor(blend(skyTopColor(), skyBottomColor(), 0.42f));
            canvas.drawCircle(x + radius * 0.46f, y - radius * 0.08f, radius * 0.94f, shapePaint);
        }
    }

    private void drawCelestialAtmosphere(Canvas canvas, int width, int height,
                                         float scale, float visibleWorldWidth) {
        float visibility = celestialVisibility * state.effectIntensity;
        if (visibility <= 0.03f) return;
        float x = (celestialWorldX + visibleWorldWidth * 0.5f) * scale;
        float y = (WORLD_HEIGHT - celestialWorldY) * scale;

        int hazeColor = celestialSun
                ? Color.argb((int) (56 * visibility), 255, 190, 136)
                : Color.argb((int) (28 * visibility), 200, 212, 246);
        float hazeRadius = height * (celestialSun ? 0.38f : 0.28f);
        shapePaint.setShader(new RadialGradient(x, y, hazeRadius, hazeColor,
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, hazeRadius, shapePaint);
        shapePaint.setShader(null);

        float horizonY = height * 0.70f;
        float beamHalfWidth = width * (celestialSun ? 0.14f : 0.09f);
        int beamAlpha = (int) ((celestialSun ? 26 : 12) * visibility * (1f - state.cloud * 0.52f));
        if (beamAlpha > 0) {
            shapePaint.setShader(new LinearGradient(x, y, x, horizonY,
                    Color.argb(beamAlpha, celestialSun ? 255 : 210, celestialSun ? 192 : 217, celestialSun ? 140 : 245),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawRect(x - beamHalfWidth, y, x + beamHalfWidth, horizonY, shapePaint);
            shapePaint.setShader(null);
        }
    }

    private void drawLayer(Canvas canvas, Bitmap bitmap, float scale, float visibleWorldWidth,
                           float parallaxOffset, float depth, float driftPerSecond,
                           float alpha, float phase, float yOffset, float heightScale,
                           ColorMatrixColorFilter filter) {
        if (bitmap == null || alpha <= 0.001f) return;
        float offset = positiveModulo(scroll * depth + elapsed * driftPerSecond
                + parallaxOffset * depth * 150f + phase, LAYER_WIDTH);
        float first = -LAYER_WIDTH * 1.5f - offset;
        float widthPx = LAYER_WIDTH * scale;
        float heightPx = WORLD_HEIGHT * heightScale * scale;
        float yPx = -yOffset * scale;
        bitmapPaint.setAlpha((int) (255 * clamp01(alpha)));
        bitmapPaint.setColorFilter(filter);
        Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        int copies = Math.max(4, (int) Math.ceil(visibleWorldWidth / LAYER_WIDTH) + 4);
        for (int i = 0; i < copies; i++) {
            float worldX = first + i * LAYER_WIDTH;
            float x = (worldX + visibleWorldWidth * 0.5f) * scale;
            canvas.drawBitmap(bitmap, src, new RectF(x, yPx, x + widthPx, yPx + heightPx), bitmapPaint);
        }
        bitmapPaint.setAlpha(255);
        bitmapPaint.setColorFilter(null);
    }

    /** Curated, low-contrast tree groupings behind the front ridge. */
    private void drawBackObjects(Canvas canvas, float scale, float visibleWorldWidth, float parallax) {
        float layerScroll = scroll * 0.34f + parallax * 20f;
        int start = (int) Math.floor((layerScroll - visibleWorldWidth) / SEGMENT_WIDTH) - 2;
        int end = start + (int) Math.ceil(visibleWorldWidth * 2f / SEGMENT_WIDTH) + 5;
        bitmapPaint.setAlpha(150);
        bitmapPaint.setColorFilter(forestFilter(0.80f));
        for (int segment = start; segment <= end; segment++) {
            int template = positiveMod(segment, 4);
            float[] positions = template == 0
                    ? new float[]{0.07f, 0.18f, 0.80f, 0.91f}
                    : template == 1
                    ? new float[]{0.05f, 0.15f, 0.24f, 0.76f, 0.86f, 0.94f}
                    : template == 2
                    ? new float[]{0.08f, 0.18f, 0.27f, 0.83f, 0.92f}
                    : new float[]{0.05f, 0.14f, 0.78f, 0.87f, 0.95f};
            for (int i = 0; i < positions.length; i++) {
                float worldX = segment * SEGMENT_WIDTH + positions[i] * SEGMENT_WIDTH;
                float screenWorldX = worldX - layerScroll;
                if (!visible(screenWorldX, visibleWorldWidth, 220f)) continue;
                float corridor = heroCorridor(worldX, 170f, 470f);
                if (corridor <= 0.08f) continue;
                Bitmap tree = corridor < 0.45f ? pineSparse : pickBackTree(segment, i);
                float height = (112f + hash01(segment * 91 + i * 37) * 118f) * (0.72f + corridor * 0.28f);
                float y = midTerrain(worldX) - 4f;
                float sway = (float) Math.sin(elapsed * (0.38f + effectiveWind() * 0.58f)
                        + segment * 0.8f + i) * effectiveWind() * 0.8f;
                drawObject(canvas, tree, screenWorldX, y, height, scale, visibleWorldWidth, sway, bitmapPaint);
            }
        }
        bitmapPaint.setAlpha(255);
        bitmapPaint.setColorFilter(null);
    }

    /**
     * Foreground templates intentionally leave a large central clearing. This
     * prevents the trees from covering the hero mountain and makes every scene
     * read as a designed composition rather than a random forest dump.
     */
    private void drawFrontObjects(Canvas canvas, float scale, float visibleWorldWidth, float parallax) {
        float layerScroll = scroll * 0.58f + parallax * 44f;
        int start = (int) Math.floor((layerScroll - visibleWorldWidth) / SEGMENT_WIDTH) - 2;
        int end = start + (int) Math.ceil(visibleWorldWidth * 2f / SEGMENT_WIDTH) + 5;
        bitmapPaint.setColorFilter(forestFilter(1f));
        for (int segment = start; segment <= end; segment++) {
            int template = positiveMod(segment, 5);
            float[] positions;
            float[] sizes;
            switch (template) {
                case 0: // open mountain vista
                    positions = new float[]{0.05f, 0.15f, 0.90f};
                    sizes = new float[]{0.88f, 0.62f, 0.76f};
                    break;
                case 1: // forest entrance, grouped at edges
                    positions = new float[]{0.03f, 0.11f, 0.20f, 0.81f, 0.90f, 0.97f};
                    sizes = new float[]{0.88f, 0.68f, 0.54f, 0.52f, 0.72f, 0.92f};
                    break;
                case 2: // lantern trail
                    positions = new float[]{0.05f, 0.16f, 0.84f, 0.95f};
                    sizes = new float[]{0.78f, 0.56f, 0.54f, 0.78f};
                    break;
                case 3: // quiet clearing
                    positions = new float[]{0.05f, 0.15f, 0.92f};
                    sizes = new float[]{0.68f, 0.50f, 0.70f};
                    break;
                default: // sparse ridge
                    positions = new float[]{0.07f, 0.21f, 0.84f, 0.95f};
                    sizes = new float[]{0.74f, 0.46f, 0.52f, 0.80f};
                    break;
            }

            for (int i = 0; i < positions.length; i++) {
                float worldX = segment * SEGMENT_WIDTH + positions[i] * SEGMENT_WIDTH;
                float screenWorldX = worldX - layerScroll;
                if (!visible(screenWorldX, visibleWorldWidth, 300f)) continue;
                float corridor = heroCorridor(worldX, 230f, 560f);
                if (corridor <= 0.05f) continue;
                Bitmap tree = corridor < 0.52f ? pineSparse : pickFrontTree(segment, i, template);
                if (corridor < 0.40f && tree == pineDead) tree = pineSparse;
                float height = (205f + hash01(segment * 113 + i * 29) * 105f) * sizes[i];
                height *= (0.60f + corridor * 0.40f);
                // Caps prevent needle-like trees on extremely tall screens.
                height = Math.min(315f, Math.max(108f, height));
                float y = frontTerrain(worldX) - 3f;
                float sway = (float) Math.sin(elapsed * (0.50f + effectiveWind() * 0.92f)
                        + segment * 0.7f + i) * effectiveWind() * 1.55f;
                drawObject(canvas, tree, screenWorldX, y, height, scale, visibleWorldWidth, sway, bitmapPaint);
            }

            if (template == 2) {
                drawLantern(canvas, scale, visibleWorldWidth, layerScroll,
                        segment * SEGMENT_WIDTH + SEGMENT_WIDTH * 0.37f);
                drawLantern(canvas, scale, visibleWorldWidth, layerScroll,
                        segment * SEGMENT_WIDTH + SEGMENT_WIDTH * 0.66f);
            } else if (template == 3 && state.showCampfires) {
                drawCampfire(canvas, scale, visibleWorldWidth, layerScroll,
                        segment * SEGMENT_WIDTH + SEGMENT_WIDTH * 0.57f);
            } else if (template == 4) {
                drawLantern(canvas, scale, visibleWorldWidth, layerScroll,
                        segment * SEGMENT_WIDTH + SEGMENT_WIDTH * 0.54f);
            }
        }
        bitmapPaint.setColorFilter(null);
    }

    private void drawLantern(Canvas canvas, float scale, float visibleWorldWidth,
                             float layerScroll, float worldX) {
        float screenWorldX = worldX - layerScroll;
        if (!visible(screenWorldX, visibleWorldWidth, 120f)) return;
        float y = frontTerrain(worldX) - 2f;
        float flicker = 0.90f + (float) Math.sin(elapsed * 4.2f + worldX * 0.01f) * 0.06f
                + pulse * 0.08f;
        drawGlow(canvas, screenWorldX + 7f, y + 125f, 56f + pulse * 18f,
                scale, visibleWorldWidth, Color.rgb(255, 201, 126), flicker);
        drawObject(canvas, lantern, screenWorldX, y, 164f,
                scale, visibleWorldWidth, 0f, bitmapPaint);
    }

    private void drawCampfire(Canvas canvas, float scale, float visibleWorldWidth,
                              float layerScroll, float worldX) {
        float screenWorldX = worldX - layerScroll;
        if (!visible(screenWorldX, visibleWorldWidth, 100f)) return;
        float y = frontTerrain(worldX) - 1f;
        float flicker = 0.84f + (float) Math.sin(elapsed * 5.5f + worldX * 0.012f) * 0.10f
                + pulse * 0.10f;
        drawGlow(canvas, screenWorldX + 16f, y + 40f, 54f + pulse * 16f,
                scale, visibleWorldWidth, Color.rgb(255, 143, 78), flicker);
        drawObject(canvas, campfire, screenWorldX, y, 67f,
                scale, visibleWorldWidth, 0f, bitmapPaint);
    }

    private void drawObject(Canvas canvas, Bitmap bitmap, float worldX, float baseY,
                            float heightWorld, float scale, float visibleWorldWidth,
                            float rotation, Paint paint) {
        if (bitmap == null) return;
        float widthWorld = heightWorld * bitmap.getWidth() / (float) bitmap.getHeight();
        float x = (worldX + visibleWorldWidth * 0.5f) * scale;
        float bottom = (WORLD_HEIGHT - baseY) * scale;
        float width = widthWorld * scale;
        float height = heightWorld * scale;
        drawGroundShadow(canvas, x, bottom, width, height, worldX, visibleWorldWidth);
        canvas.save();
        canvas.rotate(rotation, x + width * 0.5f, bottom);
        canvas.drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                new RectF(x, bottom - height, x + width, bottom), paint);
        canvas.restore();
    }

    private void drawGroundShadow(Canvas canvas, float x, float bottom, float width, float height,
                                  float worldX, float visibleWorldWidth) {
        float lightStrength = celestialVisibility * (celestialSun ? (0.32f + (1f - state.nightFactor()) * 0.34f) : 0.12f);
        if (lightStrength <= 0.02f) return;
        float direction = clampSigned((worldX - celestialWorldX) / Math.max(1f, visibleWorldWidth * 0.55f));
        float shadowWidth = width * (0.42f + (celestialSun ? 0.22f : 0.08f));
        float shadowHeight = Math.max(8f, width * 0.16f);
        float dx = direction * shadowWidth * 0.28f;
        shapePaint.setColor(Color.argb((int) (44 * lightStrength), 3, 7, 15));
        canvas.drawOval(new RectF(x + width * 0.18f + dx, bottom - shadowHeight * 0.40f,
                x + width * 0.18f + dx + shadowWidth, bottom + shadowHeight * 0.22f), shapePaint);
        shapePaint.setColor(Color.argb((int) (18 * lightStrength), 3, 7, 15));
        canvas.drawOval(new RectF(x + width * 0.08f + dx, bottom - shadowHeight * 0.62f,
                x + width * 0.08f + dx + shadowWidth * 1.18f, bottom + shadowHeight * 0.34f), shapePaint);
    }

    private void drawGlow(Canvas canvas, float worldX, float worldY, float radiusWorld,
                          float scale, float visibleWorldWidth, int color, float strength) {
        if (!state.showGlow) return;
        float x = (worldX + visibleWorldWidth * 0.5f) * scale;
        float y = (WORLD_HEIGHT - worldY) * scale;
        float radius = radiusWorld * scale;
        int alpha = (int) (112 * clamp01(strength));
        shapePaint.setShader(new RadialGradient(x, y, radius,
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, shapePaint);
        shapePaint.setShader(null);
    }

    private void drawGroundLight(Canvas canvas, int width, int height,
                                 float scale, float visibleWorldWidth) {
        float lightAmount = celestialVisibility * state.effectIntensity * (celestialSun ? 0.55f : 0.24f);
        if (lightAmount <= 0.02f) return;
        float x = (celestialWorldX + visibleWorldWidth * 0.5f) * scale;
        float y = height * 0.79f;
        float rx = width * (celestialSun ? 0.28f : 0.22f);
        float ry = height * (celestialSun ? 0.16f : 0.10f);
        int alpha = (int) (30f * lightAmount * (1f - state.rain * 0.65f));
        shapePaint.setShader(new RadialGradient(x, y, Math.max(rx, ry),
                Color.argb(alpha, celestialSun ? 255 : 194, celestialSun ? 186 : 208, celestialSun ? 124 : 236),
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.scale(1f, ry / Math.max(1f, rx), x, y);
        canvas.drawCircle(x, y, rx, shapePaint);
        canvas.restore();
        shapePaint.setShader(null);
    }

    private void drawWeather(Canvas canvas, int width, int height) {
        float wind = effectiveWind();
        if (state.rain > 0.02f) {
            weatherPaint.setColor(Color.argb((int) (42 + state.rain * 82), 158, 181, 211));
            weatherPaint.setStrokeWidth(0.8f + state.rain * 1.25f);
            int count = 45 + (int) (state.rain * 155f);
            for (int i = 0; i < count; i++) {
                float x = positiveModulo(hash01(i * 37) * width
                        + elapsed * (60f + wind * 170f), width);
                float y = positiveModulo(hash01(i * 97 + 17) * height
                        - elapsed * (230f + state.rain * 350f), height);
                canvas.drawLine(x, y, x + 3f + wind * 12f,
                        y + 8f + state.rain * 15f, weatherPaint);
            }
        }
        if (state.snow > 0.02f) {
            weatherPaint.setColor(Color.argb((int) (118 + state.snow * 92), 238, 241, 244));
            int count = 24 + (int) (state.snow * 78f);
            for (int i = 0; i < count; i++) {
                float x = positiveModulo(hash01(i * 67) * width
                        + (float) Math.sin(elapsed * 0.9f + i) * (18f + wind * 18f), width);
                float y = positiveModulo(hash01(i * 89) * height
                        + elapsed * (24f + state.snow * 42f), height);
                canvas.drawCircle(x, y, 1.1f + hash01(i * 13) * 2.2f, weatherPaint);
            }
        }
    }

    private void drawFireflies(Canvas canvas, float scale, float visibleWorldWidth) {
        if (!state.showFireflies) return;
        float amount = state.nightFactor() * (1f - state.rain * 0.82f) * state.effectIntensity;
        if (preview) amount = Math.max(amount, 0.24f);
        if (amount < 0.03f) return;
        int count = 6 + (int) (amount * 20f);
        for (int i = 0; i < count; i++) {
            float worldX = -visibleWorldWidth * 0.5f + hash01(i * 41 + 3) * visibleWorldWidth
                    + (float) Math.sin(elapsed * (0.30f + hash01(i * 13) * 0.38f) + i) * 22f;
            float worldY = 145f + hash01(i * 61 + 7) * 270f
                    + (float) Math.cos(elapsed * (0.38f + hash01(i * 17) * 0.44f) + i) * 14f;
            if (pulse > 0.01f && i < 10) {
                float targetWorldX = (touchX - 0.5f) * visibleWorldWidth;
                float targetWorldY = (1f - touchY) * WORLD_HEIGHT;
                worldX = lerp(worldX, targetWorldX + (float) Math.sin(i * 2.1f) * (32f + i * 3f), pulse * 0.65f);
                worldY = lerp(worldY, targetWorldY + (float) Math.cos(i * 1.7f) * (22f + i * 2f), pulse * 0.40f);
            }
            float x = (worldX + visibleWorldWidth * 0.5f) * scale;
            float y = (WORLD_HEIGHT - worldY) * scale;
            float flicker = 0.55f + 0.45f * (float) Math.sin(elapsed * 1.7f + i * 1.31f);
            shapePaint.setShader(new RadialGradient(x, y, 8f + flicker * 5f,
                    Color.argb((int) (120 * flicker), 255, 214, 112),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, 12f, shapePaint);
            shapePaint.setShader(null);
        }
    }

    private void drawAtmosphere(Canvas canvas, int width, int height) {
        if (state.rain > 0.04f || state.fog > 0.04f) {
            shapePaint.setColor(Color.argb((int) ((state.rain * 0.070f + state.fog * 0.090f) * 255),
                    72, 78, 103));
            canvas.drawRect(0, 0, width, height, shapePaint);
        }
        shapePaint.setShader(new LinearGradient(0, height * 0.56f, 0, height,
                Color.TRANSPARENT, Color.argb(80, 3, 8, 18), Shader.TileMode.CLAMP));
        canvas.drawRect(0, height * 0.56f, width, height, shapePaint);
        shapePaint.setShader(null);
    }

    private Bitmap pickBackTree(int segment, int index) {
        int value = positiveMod(segment * 17 + index * 7, 7);
        if (value == 0) return pineDead;
        if (value <= 2) return pineSparse;
        return pineMedium;
    }

    private Bitmap pickFrontTree(int segment, int index, int template) {
        int value = positiveMod(segment * 23 + index * 11 + template * 3, 9);
        if (value == 0 && template == 4) return pineDead;
        if (value <= 3) return pineSparse;
        if (value >= 7) return pineTall;
        return pineMedium;
    }

    private float effectiveWind() {
        return clamp01(state.wind + gustPulse * 0.42f);
    }

    private ColorMatrixColorFilter forestFilter(float depth) {
        float night = state.nightFactor();
        float r = 0.88f, g = 0.92f, b = 1.00f;
        switch (state.season) {
            case SPRING:
                r = 0.82f; g = 1.03f; b = 0.90f; break;
            case SUMMER:
                r = 0.72f; g = 0.96f; b = 0.83f; break;
            case AUTUMN:
                r = 1.04f; g = 0.80f; b = 0.70f; break;
            case WINTER:
                r = 0.86f; g = 0.92f; b = 1.02f; break;
        }
        float dark = (0.82f + depth * 0.16f) * (1f - night * 0.10f);
        ColorMatrix matrix = new ColorMatrix(new float[]{
                r * dark, 0, 0, 0, 0,
                0, g * dark, 0, 0, 0,
                0, 0, b * dark, 0, 0,
                0, 0, 0, 1, 0
        });
        return new ColorMatrixColorFilter(matrix);
    }

    private ColorMatrixColorFilter atmosphericFilter(float brightness) {
        float cloudDimming = 1f - state.cloud * 0.06f - state.rain * 0.07f;
        float value = brightness * cloudDimming;
        ColorMatrix matrix = new ColorMatrix();
        matrix.setScale(value, value, value * 1.03f, 1f);
        return new ColorMatrixColorFilter(matrix);
    }

    private ColorMatrixColorFilter seasonalMountainFilter() {
        float r = 1f, g = 1f, b = 1f;
        if (state.season == SceneState.Season.AUTUMN) {
            r = 1.03f; g = 0.95f; b = 0.92f;
        } else if (state.season == SceneState.Season.WINTER) {
            r = 0.94f; g = 0.98f; b = 1.05f;
        }
        float dim = 1f - state.rain * 0.06f - state.storm * 0.10f;
        ColorMatrix matrix = new ColorMatrix();
        matrix.setScale(r * dim, g * dim, b * dim, 1f);
        return new ColorMatrixColorFilter(matrix);
    }

    private Bitmap asset(Context context, String path, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sampleSize);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        try (InputStream stream = context.getAssets().open(path)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            if (bitmap != null) owned.add(bitmap);
            return bitmap;
        } catch (IOException ignored) {
            return null;
        }
    }

    public void dispose() {
        for (Bitmap bitmap : owned) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        owned.clear();
    }

    public void drawEmergencyFrame(Canvas canvas, int width, int height) {
        canvas.drawColor(Color.rgb(9, 13, 26));
        shapePaint.setShader(new LinearGradient(0, 0, 0, height,
                Color.rgb(28, 33, 62), Color.rgb(104, 77, 111), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, shapePaint);
        shapePaint.setShader(null);
        shapePaint.setColor(Color.rgb(33, 35, 55));
        android.graphics.Path ridge = new android.graphics.Path();
        ridge.moveTo(0, height);
        ridge.lineTo(0, height * 0.76f);
        ridge.lineTo(width * 0.25f, height * 0.60f);
        ridge.lineTo(width * 0.48f, height * 0.74f);
        ridge.lineTo(width * 0.68f, height * 0.56f);
        ridge.lineTo(width, height * 0.73f);
        ridge.lineTo(width, height);
        ridge.close();
        canvas.drawPath(ridge, shapePaint);
    }

    private int skyTopColor() {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        if (hour < 5f) return Color.rgb(12, 16, 35);
        if (hour < 8f) return blend(Color.rgb(47, 53, 91), Color.rgb(116, 120, 161), (hour - 5f) / 3f);
        if (hour < 17f) return Color.rgb(98, 151, 181);
        if (hour < 21f) return blend(Color.rgb(91, 124, 157), Color.rgb(30, 32, 65), (hour - 17f) / 4f);
        return Color.rgb(11, 14, 32);
    }

    private int skyBottomColor() {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        if (hour < 5f) return Color.rgb(38, 33, 61);
        if (hour < 8f) return blend(Color.rgb(98, 77, 111), Color.rgb(231, 158, 136), (hour - 5f) / 3f);
        if (hour < 17f) return Color.rgb(183, 195, 186);
        if (hour < 21f) return blend(Color.rgb(231, 174, 140), Color.rgb(76, 54, 93), (hour - 17f) / 4f);
        return Color.rgb(41, 31, 61);
    }

    private static boolean visible(float worldX, float visibleWorldWidth, float margin) {
        return worldX >= -visibleWorldWidth * 0.5f - margin
                && worldX <= visibleWorldWidth * 0.5f + margin;
    }

    private static float midTerrain(float worldX) {
        float local = positiveModulo(worldX, LAYER_WIDTH) / LAYER_WIDTH * (float) (Math.PI * 2.0);
        return 202f + (float) Math.sin(local + 0.32f) * 30f
                + (float) Math.sin(local * 2f + 1.15f) * 14f;
    }

    private static float frontTerrain(float worldX) {
        float local = positiveModulo(worldX, LAYER_WIDTH) / LAYER_WIDTH * (float) (Math.PI * 2.0);
        return 82f + (float) Math.sin(local + 1.02f) * 22f
                + (float) Math.sin(local * 2f + 0.22f) * 9f;
    }

    private static float heroCorridor(float worldX, float inner, float outer) {
        float local = positiveModulo(worldX, LAYER_WIDTH);
        float distance = Math.abs(local - LAYER_WIDTH * 0.5f);
        distance = Math.min(distance, LAYER_WIDTH - distance);
        return clamp01((distance - inner) / Math.max(1f, outer - inner));
    }

    private static int blend(int a, int b, float t) {
        t = clamp01(t);
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static int positiveMod(int value, int max) {
        int result = value % max;
        return result < 0 ? result + max : result;
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

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float clampSigned(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
