package dev.andres.aetherscape.render;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Locale;

/**
 * Native aspect-safe 2D renderer shared by the preview and live wallpaper.
 *
 * The renderer works in logical world units derived from the shortest viewport
 * dimension. This prevents mountains and trees from stretching when switching
 * between portrait and landscape. Every foreground object is anchored to the
 * scrolling world; there are no fixed decorative trees stamped on the screen.
 */
public final class SceneRenderer {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private SceneState state;
    private long lastNanos;
    private float elapsed;
    /** Logical world units, independent of resolution and orientation. */
    private float worldOffset;
    private float launcherOffset;
    private int viewportWidth;
    private int viewportHeight;

    public SceneRenderer(SharedPreferences preferences) {
        state = SceneState.fromPreferences(preferences);
        lastNanos = System.nanoTime();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setLauncherOffset(float offset) {
        launcherOffset = Math.max(-1f, Math.min(1f, offset));
    }

    public void onSurfaceChanged(int width, int height) {
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
        lastNanos = System.nanoTime();
    }

    public int currentTargetFps() {
        return Math.max(10, Math.min(60, state.targetFps));
    }

    public void draw(Canvas canvas, int width, int height, SharedPreferences preferences, boolean preview) {
        if (width <= 0 || height <= 0) return;
        if (width != viewportWidth || height != viewportHeight) onSurfaceChanged(width, height);

        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        if (dt <= 0f || dt > 0.18f) dt = 1f / 30f;
        elapsed += dt;

        SceneState target = SceneState.fromPreferences(preferences);
        float alpha = 1f - (float) Math.exp(-dt * 0.060f);
        if (preview) alpha = Math.max(alpha, 0.020f);
        state.smoothToward(target, alpha);

        float speed = (10f + state.scrollSpeed * 34f) * (0.50f + state.motionIntensity * 0.68f);
        if (preview) speed *= 0.82f;
        worldOffset += dt * speed;
        if (worldOffset > 500_000f) worldOffset -= 500_000f;

        Layout layout = Layout.from(width, height);
        Palette palette = Palette.forState(state);

        // Explicit source clear prevents stale/ghost pixels on launchers that reuse buffers.
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC);
        drawSky(canvas, layout, palette);
        drawStars(canvas, layout);
        drawCelestial(canvas, layout, palette);
        drawClouds(canvas, layout, palette);
        drawMountainLayers(canvas, layout, palette);
        drawValleyMist(canvas, layout, palette);
        drawFarForest(canvas, layout, palette);
        drawHills(canvas, layout, palette);
        drawWorldObjects(canvas, layout, palette);
        drawWeather(canvas, layout, palette);
        drawAtmosphere(canvas, layout, palette);
        if (preview) drawPreviewHud(canvas, layout, palette);
    }

    private void drawSky(Canvas canvas, Layout l, Palette p) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                0f, 0f, 0f, l.height,
                new int[]{p.skyTop, p.skyMid, p.skyBottom},
                new float[]{0f, 0.56f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, l.width, l.height, paint);
        paint.setShader(null);

        float dim = state.cloud * 0.08f + state.rain * 0.11f + state.storm * 0.10f;
        if (dim > 0.005f) {
            paint.setColor(Color.argb((int) (clamp01(dim) * 255f), 16, 21, 34));
            canvas.drawRect(0f, 0f, l.width, l.height, paint);
        }
    }

    private void drawStars(Canvas canvas, Layout l) {
        float night = state.nightFactor();
        if (!state.showStars || night < 0.03f) return;
        int count = 42 + (int) (state.effectIntensity * 82f * (l.portrait ? 1f : 1.25f));
        float topLimit = l.height * (l.portrait ? 0.48f : 0.55f);
        for (int i = 0; i < count; i++) {
            float x = hash01(i * 17 + 3) * l.width;
            float y = hash01(i * 29 + 11) * topLimit;
            float twinkle = 0.58f + 0.42f * (float) Math.sin(elapsed * (0.45f + hash01(i) * 1.15f) + i);
            float cloudFade = 1f - state.cloud * 0.72f;
            float a = night * twinkle * cloudFade * (0.32f + hash01(i * 31) * 0.68f);
            float r = l.u * (0.75f + hash01(i * 53) * 1.65f);
            if (state.showGlow && r > l.u * 1.25f) {
                paint.setColor(Color.argb((int) (a * 42f), 235, 238, 221));
                canvas.drawCircle(x, y, r * 4f, paint);
            }
            paint.setColor(Color.argb((int) (a * 228f), 246, 244, 224));
            canvas.drawCircle(x, y, r, paint);
        }
    }

    private void drawCelestial(Canvas canvas, Layout l, Palette p) {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        boolean sun = hour >= 5.2f && hour <= 20.0f;
        float x;
        float y;
        int core;

        if (sun) {
            float progress = clamp01((hour - 5.5f) / 14f);
            x = l.width * (0.10f + progress * 0.80f);
            float arc = (float) Math.sin(progress * Math.PI);
            y = l.height * (l.portrait ? 0.38f : 0.36f) - arc * l.minDim * (l.portrait ? 0.24f : 0.18f);
            core = blend(Color.rgb(255, 240, 201), Color.rgb(255, 181, 126), state.duskFactor());
        } else {
            float progress = hour < 6f ? (hour + 6f) / 12f : (hour - 18f) / 12f;
            progress = clamp01(progress);
            x = l.width * (0.14f + progress * 0.72f);
            y = l.height * 0.30f - (float) Math.sin(progress * Math.PI) * l.minDim * 0.15f;
            core = Color.rgb(235, 237, 223);
        }

        float radius = l.minDim * (sun ? 0.041f : 0.030f);
        float visibility = clamp01(1f - state.cloud * 0.62f);
        if (state.showGlow) {
            float glowRadius = radius * (sun ? 6.7f : 5.0f);
            int outer = sun ? Color.argb((int) (54f * visibility), 255, 192, 139)
                    : Color.argb((int) (36f * visibility), 220, 225, 218);
            paint.setShader(new RadialGradient(
                    x, y, glowRadius,
                    new int[]{withAlpha(core, (int) (92f * visibility)), outer, Color.TRANSPARENT},
                    new float[]{0f, 0.34f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(x, y, glowRadius, paint);
            paint.setShader(null);

            // Subtle graphic halo rings, softened by low alpha.
            for (int ring = 1; ring <= 4; ring++) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, l.u * 1.2f));
                paint.setColor(withAlpha(core, (int) ((17f - ring * 2.5f) * visibility)));
                canvas.drawCircle(x, y, radius * (1.7f + ring * 0.85f), paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        paint.setColor(withAlpha(core, (int) (255f * visibility)));
        canvas.drawCircle(x, y, radius, paint);

        if (!sun && state.nightFactor() > 0.18f) {
            paint.setColor(withAlpha(p.skyTop, 242));
            canvas.drawCircle(x + radius * 0.42f, y - radius * 0.16f, radius * 0.92f, paint);
        }
    }

    private void drawClouds(Canvas canvas, Layout l, Palette p) {
        int count = 2 + (int) (state.cloud * (9f + state.effectIntensity * 8f));
        if (state.cloud < 0.08f) count = 1;
        float parallax = state.parallax ? launcherOffset * l.minDim * 0.035f : 0f;
        float travelBase = elapsed * (3.2f + state.wind * 16f);

        for (int i = 0; i < count; i++) {
            float depth = 0.45f + hash01(i * 11 + 2) * 0.55f;
            float width = l.minDim * (0.16f + hash01(i * 19) * 0.22f) * depth;
            float height = width * (0.22f + hash01(i * 23) * 0.10f);
            float travel = travelBase * (0.52f + hash01(i * 13) * 0.85f);
            float x = wrap(hash01(i * 37 + 1) * (l.width + width * 2f) + travel + parallax, l.width + width * 2f) - width;
            float y = l.height * (0.08f + hash01(i * 41 + 9) * (l.portrait ? 0.34f : 0.40f));
            int cloudColor = blend(p.cloudLight, p.cloudDark, state.rain * 0.70f + state.storm * 0.24f);
            int alpha = (int) ((38f + state.cloud * 92f) * (0.68f + depth * 0.32f));

            // Diffuse under-shadow creates the soft, misty reference look.
            paint.setColor(withAlpha(p.cloudDark, Math.max(7, alpha / 4)));
            rect.set(x - width * 0.52f, y + height * 0.10f, x + width * 0.56f, y + height * 1.02f);
            canvas.drawOval(rect, paint);

            paint.setColor(withAlpha(cloudColor, alpha));
            rect.set(x - width * 0.50f, y, x + width * 0.52f, y + height * 0.72f);
            canvas.drawOval(rect, paint);
            canvas.drawCircle(x - width * 0.24f, y, height * 0.70f, paint);
            canvas.drawCircle(x + width * 0.02f, y - height * 0.20f, height * 0.88f, paint);
            canvas.drawCircle(x + width * 0.27f, y + height * 0.02f, height * 0.64f, paint);
        }
    }

    private void drawMountainLayers(Canvas canvas, Layout l, Palette p) {
        // Broad spacing and width-based height prevent portrait mountains becoming needles.
        drawMountainLayer(canvas, l, p, 0.055f, 0.61f, 470f, 720f, p.mountainFar, 0.26f, 118);
        drawMountainLayer(canvas, l, p, 0.105f, 0.69f, 610f, 620f, p.mountainMid, 0.38f, 233);
        drawMountainLayer(canvas, l, p, 0.165f, 0.75f, l.portrait ? 820f : 610f,
                l.portrait ? 780f : 720f, p.mountainHero, 0.55f, 419);
        drawMountainLayer(canvas, l, p, 0.235f, 0.81f, 560f, 520f, p.mountainNear, 0.72f, 607);
    }

    private void drawMountainLayer(Canvas canvas, Layout l, Palette p, float depth, float baselineRatio,
                                   float amplitudeWorld, float spacingWorld, int color, float facetStrength,
                                   int salt) {
        float parallax = state.parallax ? launcherOffset * 42f * depth : 0f;
        float worldLeft = worldOffset * depth - parallax;
        int start = (int) Math.floor(worldLeft / spacingWorld) - 3;
        int end = start + (int) Math.ceil(l.width / (spacingWorld * l.u)) + 7;
        float base = l.height * baselineRatio;

        for (int i = start; i <= end; i++) {
            float worldStart = i * spacingWorld;
            float x0 = (worldStart - worldLeft) * l.u;
            float x1 = x0 + spacingWorld * l.u;
            float width = x1 - x0;
            float center = x0 + width * (0.40f + hash01(i * 31 + salt) * 0.18f);
            float peakHeight = amplitudeWorld * l.u * (0.74f + hash01(i * 47 + salt) * 0.32f);
            float baseY = base + hashSigned(i * 61 + salt) * l.u * 34f;
            float peakY = baseY - peakHeight;
            float leftShoulderX = center - width * (0.19f + hash01(i * 71 + salt) * 0.11f);
            float rightShoulderX = center + width * (0.19f + hash01(i * 83 + salt) * 0.13f);
            float leftShoulderY = baseY - peakHeight * (0.42f + hash01(i * 89 + salt) * 0.18f);
            float rightShoulderY = baseY - peakHeight * (0.31f + hash01(i * 97 + salt) * 0.18f);

            path.reset();
            path.moveTo(x0 - l.u * 4f, l.height);
            path.lineTo(x0 - l.u * 4f, baseY);
            path.lineTo(leftShoulderX, leftShoulderY);
            path.lineTo(center, peakY);
            path.lineTo(rightShoulderX, rightShoulderY);
            path.lineTo(x1 + l.u * 4f, baseY);
            path.lineTo(x1 + l.u * 4f, l.height);
            path.close();
            paint.setColor(color);
            canvas.drawPath(path, paint);

            // Cool shadow facet.
            path.reset();
            path.moveTo(center, peakY);
            path.lineTo(rightShoulderX, rightShoulderY);
            path.lineTo(x1 + l.u * 4f, baseY);
            path.lineTo(center + width * 0.035f, baseY - peakHeight * 0.10f);
            path.close();
            paint.setColor(withAlpha(darken(color, 0.70f), (int) (90f + facetStrength * 80f)));
            canvas.drawPath(path, paint);

            // Warm atmospheric highlight on the sunrise-facing facet.
            if (state.duskFactor() > 0.08f) {
                path.reset();
                path.moveTo(center, peakY);
                path.lineTo(leftShoulderX, leftShoulderY);
                path.lineTo(center - width * 0.04f, baseY - peakHeight * 0.12f);
                path.close();
                paint.setColor(withAlpha(p.warmLight, (int) (state.duskFactor() * 34f * (1f - depth * 0.45f))));
                canvas.drawPath(path, paint);
            }

            drawSnowCap(canvas, l, p, i, depth, center, peakY, peakHeight, width);
        }
    }

    private void drawSnowCap(Canvas canvas, Layout l, Palette p, int seed, float depth,
                             float centerX, float peakY, float peakHeight, float mountainWidth) {
        boolean winter = state.season == SceneState.Season.WINTER;
        boolean cold = state.temperatureC < 4f;
        if (!state.snowCaps || (!winter && !cold && state.snow < 0.18f) || depth < 0.10f) return;
        float amount = clamp01(0.42f + state.snow * 0.48f + (cold ? 0.15f : 0f));
        float capW = mountainWidth * (0.12f + hash01(seed * 101) * 0.07f);
        float capH = peakHeight * (0.10f + hash01(seed * 113) * 0.06f);

        path.reset();
        path.moveTo(centerX, peakY);
        path.lineTo(centerX - capW, peakY + capH);
        path.lineTo(centerX - capW * 0.48f, peakY + capH * 0.66f);
        path.lineTo(centerX - capW * 0.16f, peakY + capH * 1.15f);
        path.lineTo(centerX + capW * 0.12f, peakY + capH * 0.70f);
        path.lineTo(centerX + capW * 0.47f, peakY + capH * 1.02f);
        path.lineTo(centerX + capW, peakY + capH);
        path.close();
        paint.setColor(Color.argb((int) (amount * 180f), 226, 231, 232));
        canvas.drawPath(path, paint);
    }

    private void drawValleyMist(Canvas canvas, Layout l, Palette p) {
        float amount = clamp01(0.12f + state.fog * 0.78f + state.rain * 0.14f);
        int bands = 4 + (int) (amount * 4f);
        for (int i = 0; i < bands; i++) {
            float y = l.height * (0.55f + i * (l.portrait ? 0.050f : 0.060f));
            float drift = (float) Math.sin(elapsed * 0.055f + i * 1.37f) * l.minDim * 0.10f;
            float w = l.width * (0.64f + hash01(i * 83) * 0.48f);
            float h = l.minDim * (0.065f + hash01(i * 97) * 0.045f);
            int a = (int) ((12f + amount * 32f) * (1f - i * 0.08f));
            paint.setColor(withAlpha(p.fog, Math.max(3, a)));
            rect.set(-w * 0.25f + drift, y, w + drift, y + h);
            canvas.drawOval(rect, paint);
            rect.set(l.width - w * 0.72f - drift, y + h * 0.26f,
                    l.width + w * 0.20f - drift, y + h * 1.17f);
            canvas.drawOval(rect, paint);
        }
    }

    private void drawFarForest(Canvas canvas, Layout l, Palette p) {
        drawTreeLayer(canvas, l, p, 0.28f, 0.76f, 148f, 0.16f, 0.28f, p.treeFar, false, 301);
        drawTreeLayer(canvas, l, p, 0.39f, 0.82f, 112f, 0.19f, 0.35f, blend(p.treeFar, p.treeNear, 0.30f), false, 401);
    }

    private void drawHills(Canvas canvas, Layout l, Palette p) {
        drawHillLayer(canvas, l, 0.36f, 0.79f, 0.075f, p.hillFar, 11);
        drawHillLayer(canvas, l, 0.62f, 0.88f, 0.105f, p.hillMid, 23);
        drawHillLayer(canvas, l, 1.00f, 0.95f, 0.135f, p.ground, 37);
    }

    private void drawHillLayer(Canvas canvas, Layout l, float depth, float baseRatio,
                               float amplitudeRatio, int color, int salt) {
        float parallax = state.parallax ? launcherOffset * l.minDim * 0.13f * depth : 0f;
        float offsetPx = worldOffset * depth * l.u + parallax;
        float base = l.height * baseRatio;
        float amplitude = l.minDim * amplitudeRatio;
        path.reset();
        path.moveTo(0f, l.height);
        int steps = Math.max(18, l.width / Math.max(24, (int) (l.minDim * 0.045f)));
        for (int i = 0; i <= steps; i++) {
            float x = l.width * i / (float) steps;
            float worldX = (x + offsetPx) / l.u;
            float y = base
                    - (float) Math.sin(worldX * 0.0062f + salt) * amplitude * 0.38f
                    - (hashSmooth(worldX * 0.013f + salt) - 0.5f) * amplitude;
            path.lineTo(x, y);
        }
        path.lineTo(l.width, l.height);
        path.close();
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    private void drawWorldObjects(Canvas canvas, Layout l, Palette p) {
        drawTreeLayer(canvas, l, p, 0.66f, 0.89f, 104f, 0.18f, 0.44f, p.treeFar, false, 503);
        drawTreeLayer(canvas, l, p, 1.00f, 0.965f, 82f, 0.20f, 0.52f, p.treeNear, true, 701);
        drawHeroTrees(canvas, l, p);
        drawWorldLanternTrail(canvas, l, p);
        drawStructures(canvas, l, p);
        drawCampfires(canvas, l, p);
    }

    private void drawTreeLayer(Canvas canvas, Layout l, Palette p, float depth, float baseRatio,
                               float spacingWorld, float minHeightRatio, float maxHeightRatio,
                               int color, boolean foreground, int salt) {
        float parallaxWorld = state.parallax ? launcherOffset * 58f * depth : 0f;
        float worldLeft = worldOffset * depth - parallaxWorld;
        int start = (int) Math.floor(worldLeft / spacingWorld) - 4;
        int end = start + (int) Math.ceil(l.width / (spacingWorld * l.u)) + 9;
        float chanceBase = state.treeDensity * (foreground ? 0.94f : 0.77f);

        for (int i = start; i <= end; i++) {
            int biome = biomeAt(i * spacingWorld + salt);
            float chance = chanceBase;
            if (biome == 0) chance *= 0.48f;
            else if (biome == 1) chance *= 1.18f;
            else if (biome == 2) chance *= 0.76f;
            else if (biome == 4) chance *= 0.34f;
            if (hash01(i * 131 + salt) > chance) continue;

            float worldX = i * spacingWorld + hashSigned(i * 19 + salt) * spacingWorld * 0.30f;
            float x = (worldX - worldLeft) * l.u;
            float groundY = terrainY(worldX, l, baseRatio, foreground ? 0.10f : 0.075f, salt);
            float ratio = minHeightRatio + hash01(i * 43 + salt) * (maxHeightRatio - minHeightRatio);
            float treeHeight = l.minDim * ratio;
            float sway = (float) Math.sin(elapsed * (0.65f + state.wind * 1.6f) + i * 0.71f)
                    * state.wind * 2.7f;
            boolean deciduous = state.season != SceneState.Season.WINTER
                    && (biome == 0 || biome == 2 || hash01(i * 83 + salt) > 0.68f);
            if (deciduous) drawRoundTree(canvas, l, x, groundY, treeHeight, sway, color, i + salt);
            else drawPine(canvas, l, x, groundY, treeHeight, sway, color, i + salt);
        }
    }

    private void drawHeroTrees(Canvas canvas, Layout l, Palette p) {
        final float depth = 0.92f;
        final float segment = 1700f;
        float parallaxWorld = state.parallax ? launcherOffset * 72f : 0f;
        float worldLeft = worldOffset * depth - parallaxWorld;
        int start = (int) Math.floor(worldLeft / segment) - 2;
        int end = start + (int) Math.ceil(l.width / (segment * l.u)) + 4;

        for (int i = start; i <= end; i++) {
            float segmentStart = i * segment;
            for (int j = 0; j < 2; j++) {
                float worldX = segmentStart + (j == 0 ? 190f : 470f) + hashSigned(i * 47 + j * 101) * 90f;
                float x = (worldX - worldLeft) * l.u;
                if (x < -l.minDim * 0.40f || x > l.width + l.minDim * 0.40f) continue;
                float groundY = terrainY(worldX, l, 0.93f, 0.11f, 83);
                float heroRatio;
                if (l.portrait) heroRatio = j == 0 ? 1.34f : 0.96f;
                else heroRatio = j == 0 ? 0.72f : 0.54f;
                float height = l.minDim * heroRatio
                        * (0.88f + hash01(i * 59 + j * 13) * 0.22f);
                float sway = (float) Math.sin(elapsed * (0.42f + state.wind) + i + j) * state.wind * 2.2f;
                drawPine(canvas, l, x, groundY, height, sway, darken(p.treeNear, 0.80f), i * 7 + j + 900);
            }
        }
    }

    private float terrainY(float worldX, Layout l, float baseRatio, float amplitudeRatio, int salt) {
        float amplitude = l.minDim * amplitudeRatio;
        return l.height * baseRatio
                - (float) Math.sin(worldX * 0.0062f + salt) * amplitude * 0.36f
                - (hashSmooth(worldX * 0.013f + salt) - 0.5f) * amplitude;
    }

    private void drawPine(Canvas canvas, Layout l, float x, float groundY, float height,
                          float swayDegrees, int color, int seed) {
        float trunk = Math.max(l.u * 3f, height * 0.038f);
        float crownTop = groundY - height;
        float crownBottom = groundY - height * 0.06f;
        canvas.save();
        canvas.rotate(swayDegrees, x, groundY);

        paint.setColor(darken(color, 0.62f));
        canvas.drawRect(x - trunk * 0.5f, groundY - height * 0.82f, x + trunk * 0.5f, groundY, paint);

        int tiers = 7 + (int) (hash01(seed * 53) * 4f);
        for (int tier = 0; tier < tiers; tier++) {
            float t = tier / (float) Math.max(1, tiers - 1);
            float centerY = lerp(crownTop + height * 0.08f, crownBottom, t);
            float halfW = height * (0.055f + t * 0.145f) * (0.88f + hash01(seed * 73 + tier) * 0.20f);
            float tierH = height * (0.105f - t * 0.028f);
            float asym = hashSigned(seed * 83 + tier) * halfW * 0.10f;
            path.reset();
            path.moveTo(x + asym * 0.10f, centerY - tierH);
            path.lineTo(x - halfW + asym, centerY + tierH * 0.34f);
            path.lineTo(x - halfW * 0.58f, centerY + tierH * 0.12f);
            path.lineTo(x, centerY + tierH * 0.48f);
            path.lineTo(x + halfW * 0.62f, centerY + tierH * 0.12f);
            path.lineTo(x + halfW + asym, centerY + tierH * 0.34f);
            path.close();
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }
        canvas.restore();
    }

    private void drawRoundTree(Canvas canvas, Layout l, float x, float groundY, float height,
                               float swayDegrees, int color, int seed) {
        float trunk = Math.max(l.u * 3f, height * 0.045f);
        canvas.save();
        canvas.rotate(swayDegrees, x, groundY);
        paint.setColor(darken(color, 0.64f));
        canvas.drawRect(x - trunk * 0.5f, groundY - height * 0.66f, x + trunk * 0.5f, groundY, paint);
        int foliage = seasonFoliage(color);
        float r = height * 0.20f;
        paint.setColor(foliage);
        canvas.drawCircle(x, groundY - height * 0.72f, r, paint);
        canvas.drawCircle(x - r * 0.70f, groundY - height * 0.61f, r * 0.82f, paint);
        canvas.drawCircle(x + r * 0.73f, groundY - height * 0.60f, r * 0.87f, paint);
        canvas.drawCircle(x + r * 0.14f, groundY - height * 0.84f, r * 0.76f, paint);
        canvas.restore();
    }

    private int seasonFoliage(int base) {
        switch (state.season) {
            case SPRING: return blend(base, Color.rgb(83, 119, 96), 0.42f);
            case SUMMER: return blend(base, Color.rgb(35, 82, 65), 0.34f);
            case AUTUMN: return blend(base, Color.rgb(128, 72, 53), 0.68f);
            case WINTER: return blend(base, Color.rgb(43, 53, 62), 0.82f);
            default: return base;
        }
    }

    private void drawWorldLanternTrail(Canvas canvas, Layout l, Palette p) {
        final float depth = 1f;
        final float segment = 2100f;
        float parallaxWorld = state.parallax ? launcherOffset * 78f : 0f;
        float worldLeft = worldOffset * depth - parallaxWorld;
        int start = (int) Math.floor(worldLeft / segment) - 2;
        int end = start + (int) Math.ceil(l.width / (segment * l.u)) + 4;

        for (int i = start; i <= end; i++) {
            if (hash01(i * 107 + 19) > 0.70f) continue;
            float baseWorld = i * segment;
            float firstWorld = baseWorld + 360f;
            float secondWorld = baseWorld + 1120f;
            float x1 = (firstWorld - worldLeft) * l.u;
            float x2 = (secondWorld - worldLeft) * l.u;
            if (x2 < -l.minDim * 0.2f || x1 > l.width + l.minDim * 0.2f) continue;
            float y1 = terrainY(firstWorld, l, 0.94f, 0.12f, 37);
            float y2 = terrainY(secondWorld, l, 0.94f, 0.12f, 37);
            float postH = l.minDim * 0.18f;
            float top1 = y1 - postH;
            float top2 = y2 - postH * 0.92f;

            paint.setColor(darken(p.treeNear, 0.52f));
            float postW = Math.max(l.u * 4f, l.minDim * 0.005f);
            canvas.drawRect(x1 - postW * 0.5f, top1, x1 + postW * 0.5f, y1 + l.u * 12f, paint);
            canvas.drawRect(x2 - postW * 0.5f, top2, x2 + postW * 0.5f, y2 + l.u * 12f, paint);

            stroke.setStrokeWidth(Math.max(l.u * 1.3f, 1f));
            stroke.setColor(Color.argb(135, 78, 55, 57));
            path.reset();
            path.moveTo(x1, top1 + postH * 0.14f);
            path.quadTo((x1 + x2) * 0.5f, Math.max(top1, top2) + postH * 0.35f,
                    x2, top2 + postH * 0.16f);
            canvas.drawPath(path, stroke);

            for (int f = 1; f < 12; f++) {
                float t = f / 12f;
                float x = lerp(x1, x2, t);
                float lineY = quadratic(lerp(top1 + postH * 0.14f, top2 + postH * 0.16f, t),
                        Math.max(top1, top2) + postH * 0.35f, t);
                paint.setColor(f % 2 == 0 ? Color.argb(190, 112, 42, 48) : Color.argb(175, 48, 49, 67));
                float fw = l.minDim * 0.008f;
                float fh = l.minDim * 0.012f;
                path.reset();
                path.moveTo(x, lineY);
                path.lineTo(x - fw, lineY + fh);
                path.lineTo(x + fw, lineY + fh);
                path.close();
                canvas.drawPath(path, paint);
            }
            drawLantern(canvas, l, p, x1, top1 + postH * 0.09f);
            drawLantern(canvas, l, p, x2, top2 + postH * 0.10f);
        }
    }

    private void drawLantern(Canvas canvas, Layout l, Palette p, float x, float y) {
        float lampW = l.minDim * 0.024f;
        float lampH = l.minDim * 0.050f;
        if (state.showGlow) {
            float radius = l.minDim * 0.085f;
            paint.setShader(new RadialGradient(
                    x, y + lampH * 0.45f, radius,
                    new int[]{Color.argb(100, 255, 216, 145), Color.argb(34, 255, 190, 105), Color.TRANSPARENT},
                    new float[]{0f, 0.36f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(x, y + lampH * 0.45f, radius, paint);
            paint.setShader(null);
        }
        paint.setColor(Color.argb(245, 239, 207, 145));
        rect.set(x - lampW * 0.5f, y, x + lampW * 0.5f, y + lampH);
        canvas.drawRoundRect(rect, lampW * 0.18f, lampW * 0.18f, paint);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, l.u * 1.4f));
        stroke.setColor(Color.argb(210, 34, 35, 40));
        canvas.drawRoundRect(rect, lampW * 0.18f, lampW * 0.18f, stroke);
    }

    private void drawStructures(Canvas canvas, Layout l, Palette p) {
        float depth = 1f;
        float segment = 2400f;
        float worldLeft = worldOffset * depth - (state.parallax ? launcherOffset * 78f : 0f);
        int start = (int) Math.floor(worldLeft / segment) - 2;
        int end = start + (int) Math.ceil(l.width / (segment * l.u)) + 4;
        for (int i = start; i <= end; i++) {
            int biome = biomeAt(i * segment);
            float worldX = i * segment + 1540f + hashSigned(i * 31) * 180f;
            float x = (worldX - worldLeft) * l.u;
            if (x < -l.minDim * 0.20f || x > l.width + l.minDim * 0.20f) continue;
            float y = terrainY(worldX, l, 0.945f, 0.12f, 37);
            if (biome == 2 && hash01(i * 101) < 0.72f) drawRuin(canvas, l, x, y, p, i);
            else if (biome == 3 && hash01(i * 79) < 0.46f) drawTent(canvas, l, x, y, p, i);
        }
    }

    private void drawRuin(Canvas canvas, Layout l, float x, float groundY, Palette p, int seed) {
        float scale = l.minDim * (0.085f + hash01(seed * 17) * 0.035f);
        float w = scale;
        float h = scale * 1.25f;
        paint.setColor(darken(p.treeNear, 0.61f));
        canvas.drawRect(x - w * 0.50f, groundY - h, x - w * 0.25f, groundY, paint);
        canvas.drawRect(x + w * 0.20f, groundY - h * 0.82f, x + w * 0.48f, groundY, paint);
        canvas.drawRect(x - w * 0.48f, groundY - h, x + w * 0.48f, groundY - h * 0.78f, paint);
        paint.setColor(p.ground);
        rect.set(x - w * 0.13f, groundY - h * 0.54f, x + w * 0.17f, groundY + l.u * 4f);
        canvas.drawOval(rect, paint);
    }

    private void drawTent(Canvas canvas, Layout l, float x, float groundY, Palette p, int seed) {
        float w = l.minDim * (0.12f + hash01(seed * 23) * 0.035f);
        float h = w * 0.72f;
        paint.setColor(blend(p.treeNear, Color.rgb(77, 58, 58), 0.48f));
        path.reset();
        path.moveTo(x, groundY - h);
        path.lineTo(x - w * 0.55f, groundY);
        path.lineTo(x + w * 0.55f, groundY);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(darken(p.ground, 0.75f));
        path.reset();
        path.moveTo(x, groundY - h);
        path.lineTo(x, groundY);
        path.lineTo(x + w * 0.18f, groundY);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawCampfires(Canvas canvas, Layout l, Palette p) {
        if (!state.showCampfires) return;
        float depth = 1f;
        float segment = 1900f;
        float worldLeft = worldOffset * depth - (state.parallax ? launcherOffset * 78f : 0f);
        int start = (int) Math.floor(worldLeft / segment) - 2;
        int end = start + (int) Math.ceil(l.width / (segment * l.u)) + 4;
        for (int i = start; i <= end; i++) {
            if (hash01(i * 149 + 5) > 0.42f) continue;
            float worldX = i * segment + 1320f + hashSigned(i * 31) * 150f;
            float x = (worldX - worldLeft) * l.u;
            if (x < -l.minDim * 0.15f || x > l.width + l.minDim * 0.15f) continue;
            float y = terrainY(worldX, l, 0.945f, 0.12f, 37);
            drawFire(canvas, l, x, y);
        }
    }

    private void drawFire(Canvas canvas, Layout l, float x, float y) {
        float size = l.minDim * 0.036f;
        if (state.showGlow) {
            float radius = size * 4.3f;
            paint.setShader(new RadialGradient(x, y - size * 0.65f, radius,
                    new int[]{Color.argb(100, 255, 174, 93), Color.argb(24, 255, 118, 67), Color.TRANSPARENT},
                    new float[]{0f, 0.38f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y - size * 0.65f, radius, paint);
            paint.setShader(null);
        }
        paint.setColor(Color.rgb(240, 116, 74));
        path.reset();
        path.moveTo(x, y - size * 1.55f);
        path.lineTo(x - size * 0.64f, y);
        path.lineTo(x + size * 0.64f, y);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(Color.rgb(255, 225, 141));
        path.reset();
        path.moveTo(x + size * 0.08f, y - size * 1.12f);
        path.lineTo(x - size * 0.28f, y);
        path.lineTo(x + size * 0.42f, y);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawWeather(Canvas canvas, Layout l, Palette p) {
        if (state.rain > 0.03f) drawRain(canvas, l, state.rain * state.effectIntensity);
        if (state.snow > 0.03f) drawSnow(canvas, l, state.snow * state.effectIntensity);
        if (state.season == SceneState.Season.AUTUMN && state.wind > 0.16f && state.rain < 0.45f) {
            drawLeaves(canvas, l, state.wind * state.effectIntensity);
        }
        if (state.showFireflies && state.nightFactor() > 0.10f && state.rain < 0.30f) {
            drawFireflies(canvas, l);
        }
        if (state.showLightning && state.storm > 0.20f) drawLightning(canvas, l);
    }

    private void drawRain(Canvas canvas, Layout l, float intensity) {
        int count = 38 + (int) (intensity * (l.portrait ? 160f : 210f));
        float slant = l.minDim * (0.018f + state.wind * 0.035f);
        float len = l.minDim * (0.025f + intensity * 0.026f);
        stroke.setStrokeWidth(Math.max(1f, l.u * (0.9f + intensity * 0.9f)));
        stroke.setColor(Color.argb((int) (48f + intensity * 92f), 186, 202, 214));
        float travel = elapsed * l.minDim * (0.48f + intensity * 0.66f);
        for (int i = 0; i < count; i++) {
            float x = hash01(i * 29 + 17) * (l.width + slant * 2f) - slant;
            float y = wrap(hash01(i * 41 + 5) * l.height + travel * (0.72f + hash01(i * 7) * 0.52f), l.height + len) - len;
            canvas.drawLine(x, y, x + slant, y + len, stroke);
        }
    }

    private void drawSnow(Canvas canvas, Layout l, float intensity) {
        int count = 28 + (int) (intensity * 115f);
        float travel = elapsed * l.minDim * (0.045f + intensity * 0.06f);
        for (int i = 0; i < count; i++) {
            float drift = (float) Math.sin(elapsed * (0.45f + hash01(i) * 0.5f) + i) * l.minDim * (0.012f + state.wind * 0.025f);
            float x = hash01(i * 31 + 13) * l.width + drift;
            float y = wrap(hash01(i * 47 + 7) * l.height + travel * (0.68f + hash01(i * 17)), l.height);
            float r = l.u * (1.2f + hash01(i * 59) * 2.2f);
            paint.setColor(Color.argb((int) (95f + intensity * 110f), 232, 236, 235));
            canvas.drawCircle(x, y, r, paint);
        }
    }

    private void drawLeaves(Canvas canvas, Layout l, float intensity) {
        int count = 8 + (int) (intensity * 35f);
        for (int i = 0; i < count; i++) {
            float speed = l.minDim * (0.020f + hash01(i * 17) * 0.050f) * (0.55f + intensity);
            float x = wrap(hash01(i * 41) * (l.width + l.minDim * 0.2f) + elapsed * speed, l.width + l.minDim * 0.2f) - l.minDim * 0.1f;
            float y = l.height * (0.30f + hash01(i * 53) * 0.58f)
                    + (float) Math.sin(elapsed * 1.1f + i) * l.minDim * 0.025f;
            paint.setColor(i % 3 == 0 ? Color.argb(145, 139, 74, 48) : Color.argb(130, 102, 67, 50));
            canvas.save();
            canvas.rotate((elapsed * 90f + i * 47f) % 360f, x, y);
            rect.set(x - l.u * 3.4f, y - l.u * 1.7f, x + l.u * 3.4f, y + l.u * 1.7f);
            canvas.drawOval(rect, paint);
            canvas.restore();
        }
    }

    private void drawFireflies(Canvas canvas, Layout l) {
        float amount = state.effectIntensity * (1f - state.rain) * state.nightFactor();
        int count = 4 + (int) (amount * 28f);
        for (int i = 0; i < count; i++) {
            float x = hash01(i * 41 + 2) * l.width
                    + (float) Math.sin(elapsed * (0.35f + hash01(i) * 0.7f) + i) * l.minDim * 0.025f;
            float y = l.height * (0.57f + hash01(i * 53) * 0.32f)
                    + (float) Math.cos(elapsed * (0.42f + hash01(i * 7) * 0.6f) + i) * l.minDim * 0.014f;
            float pulse = 0.45f + 0.55f * (float) Math.sin(elapsed * 1.9f + i * 1.7f);
            if (state.showGlow) {
                paint.setColor(Color.argb((int) (pulse * 30f), 236, 210, 111));
                canvas.drawCircle(x, y, l.u * 9f, paint);
            }
            paint.setColor(Color.argb((int) (90f + pulse * 140f), 247, 224, 132));
            canvas.drawCircle(x, y, l.u * (1.2f + pulse), paint);
        }
    }

    private void drawLightning(Canvas canvas, Layout l) {
        float cycle = elapsed % 10.5f;
        float trigger = 9.85f - state.storm * 0.75f;
        if (cycle < trigger) return;
        float flash = clamp01((cycle - trigger) / 0.09f);
        if (cycle > trigger + 0.18f) flash = clamp01(1f - (cycle - trigger - 0.18f) / 0.22f);
        flash *= state.storm;
        paint.setColor(Color.argb((int) (flash * 92f), 222, 224, 241));
        canvas.drawRect(0f, 0f, l.width, l.height, paint);

        float x = l.width * (0.24f + hash01((int) (elapsed / 10f) * 43) * 0.56f);
        float y = l.height * 0.10f;
        stroke.setColor(Color.argb((int) (flash * 240f), 235, 231, 255));
        stroke.setStrokeWidth(l.u * (2.2f + state.storm * 1.8f));
        path.reset();
        path.moveTo(x, y);
        for (int i = 1; i <= 6; i++) {
            x += hashSigned(i * 71 + (int) elapsed) * l.minDim * 0.040f;
            y += l.height * (0.050f + hash01(i * 31) * 0.030f);
            path.lineTo(x, y);
        }
        canvas.drawPath(path, stroke);
    }

    private void drawAtmosphere(Canvas canvas, Layout l, Palette p) {
        float rainMist = state.rain * 0.09f + state.fog * 0.15f;
        if (rainMist > 0.008f) {
            paint.setColor(withAlpha(p.fog, (int) (rainMist * 255f)));
            canvas.drawRect(0f, 0f, l.width, l.height, paint);
        }

        paint.setShader(new LinearGradient(0f, l.height * 0.42f, 0f, l.height,
                new int[]{Color.TRANSPARENT, Color.argb(18, 7, 10, 18), Color.argb(106, 4, 8, 15)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, l.height * 0.42f, l.width, l.height, paint);
        paint.setShader(null);

        // Edge vignette adds depth without crushing the center.
        float radius = Math.max(l.width, l.height) * 0.78f;
        paint.setShader(new RadialGradient(l.width * 0.52f, l.height * 0.46f, radius,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(76, 3, 7, 14)},
                new float[]{0f, 0.64f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, l.width, l.height, paint);
        paint.setShader(null);
    }

    private void drawPreviewHud(Canvas canvas, Layout l, Palette p) {
        float margin = Math.max(l.u * 14f, l.width * 0.025f);
        float boxH = Math.max(l.u * 50f, l.height * 0.105f);
        rect.set(margin, margin, l.width - margin, margin + boxH);
        paint.setColor(Color.argb(92, 8, 14, 23));
        canvas.drawRoundRect(rect, l.u * 18f, l.u * 18f, paint);
        paint.setColor(Color.argb(225, 244, 246, 242));
        paint.setTextSize(Math.max(l.u * 17f, l.minDim * 0.040f));
        paint.setFakeBoldText(true);
        String time = String.format(Locale.getDefault(), "%02d:%02d",
                (int) state.hour, (int) ((state.hour % 1f) * 60f));
        canvas.drawText(time, margin + l.u * 16f, margin + boxH * 0.58f, paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(Math.max(l.u * 12f, l.minDim * 0.026f));
        paint.setColor(Color.argb(200, 226, 232, 227));
        String desc = state.weatherDescription == null || state.weatherDescription.isEmpty()
                ? "Paisaje dinámico"
                : state.weatherDescription;
        String text = seasonLabel(state.season) + " · " + desc;
        while (text.length() > 8 && paint.measureText(text + "…") > l.width * 0.62f) {
            text = text.substring(0, text.length() - 1);
        }
        if (!text.endsWith(desc)) text += "…";
        canvas.drawText(text, l.width - margin - l.u * 16f - paint.measureText(text),
                margin + boxH * 0.58f, paint);
    }

    private int biomeAt(float worldX) {
        int segment = (int) Math.floor(worldX / 760f);
        float v = hash01(segment * 181 + 17);
        float variety = state.sceneVariety;
        if (v < 0.20f + (1f - variety) * 0.12f) return 0;
        if (v < 0.48f) return 1;
        if (v < 0.66f + variety * 0.08f) return 2;
        if (v < 0.86f) return 3;
        return 4;
    }

    private static String seasonLabel(SceneState.Season season) {
        switch (season) {
            case SPRING: return "Primavera";
            case SUMMER: return "Verano";
            case AUTUMN: return "Otoño";
            case WINTER: return "Invierno";
            default: return "Estación";
        }
    }

    private static float quadratic(float linearY, float controlY, float t) {
        float arch = 4f * t * (1f - t);
        return lerp(linearY, controlY, arch);
    }

    private static final class Layout {
        final int width;
        final int height;
        final float minDim;
        final float u;
        final boolean portrait;

        Layout(int width, int height, float minDim, float u, boolean portrait) {
            this.width = width;
            this.height = height;
            this.minDim = minDim;
            this.u = u;
            this.portrait = portrait;
        }

        static Layout from(int width, int height) {
            float min = Math.max(1f, Math.min(width, height));
            return new Layout(width, height, min, min / 1000f, height >= width);
        }
    }

    private static final class Palette {
        final int skyTop;
        final int skyMid;
        final int skyBottom;
        final int mountainFar;
        final int mountainMid;
        final int mountainHero;
        final int mountainNear;
        final int hillFar;
        final int hillMid;
        final int ground;
        final int treeFar;
        final int treeNear;
        final int cloudLight;
        final int cloudDark;
        final int fog;
        final int warmLight;

        Palette(int skyTop, int skyMid, int skyBottom,
                int mountainFar, int mountainMid, int mountainHero, int mountainNear,
                int hillFar, int hillMid, int ground, int treeFar, int treeNear,
                int cloudLight, int cloudDark, int fog, int warmLight) {
            this.skyTop = skyTop;
            this.skyMid = skyMid;
            this.skyBottom = skyBottom;
            this.mountainFar = mountainFar;
            this.mountainMid = mountainMid;
            this.mountainHero = mountainHero;
            this.mountainNear = mountainNear;
            this.hillFar = hillFar;
            this.hillMid = hillMid;
            this.ground = ground;
            this.treeFar = treeFar;
            this.treeNear = treeNear;
            this.cloudLight = cloudLight;
            this.cloudDark = cloudDark;
            this.fog = fog;
            this.warmLight = warmLight;
        }

        static Palette forState(SceneState s) {
            float h = ((s.hour % 24f) + 24f) % 24f;
            Keyframe[] frames = new Keyframe[]{
                    new Keyframe(0f, Color.rgb(14, 19, 39), Color.rgb(29, 31, 58), Color.rgb(52, 43, 68)),
                    new Keyframe(4.5f, Color.rgb(21, 27, 52), Color.rgb(58, 52, 83), Color.rgb(108, 77, 96)),
                    new Keyframe(6.5f, Color.rgb(78, 87, 132), Color.rgb(151, 115, 143), Color.rgb(232, 160, 139)),
                    new Keyframe(9f, Color.rgb(99, 154, 188), Color.rgb(154, 188, 198), Color.rgb(206, 211, 197)),
                    new Keyframe(13f, Color.rgb(80, 151, 190), Color.rgb(143, 184, 199), Color.rgb(207, 214, 201)),
                    new Keyframe(17f, Color.rgb(88, 119, 154), Color.rgb(161, 135, 153), Color.rgb(229, 180, 149)),
                    new Keyframe(19.5f, Color.rgb(72, 62, 108), Color.rgb(143, 94, 126), Color.rgb(229, 139, 128)),
                    new Keyframe(22f, Color.rgb(25, 27, 52), Color.rgb(49, 42, 70), Color.rgb(72, 53, 78)),
                    new Keyframe(24f, Color.rgb(14, 19, 39), Color.rgb(29, 31, 58), Color.rgb(52, 43, 68))
            };
            Keyframe a = frames[0];
            Keyframe b = frames[frames.length - 1];
            for (int i = 0; i < frames.length - 1; i++) {
                if (h >= frames[i].hour && h <= frames[i + 1].hour) {
                    a = frames[i];
                    b = frames[i + 1];
                    break;
                }
            }
            float t = (h - a.hour) / Math.max(0.001f, b.hour - a.hour);
            int top = blend(a.top, b.top, t);
            int midSky = blend(a.mid, b.mid, t);
            int bottom = blend(a.bottom, b.bottom, t);

            int seasonTint;
            int foliage;
            switch (s.season) {
                case SPRING:
                    seasonTint = Color.rgb(147, 178, 158);
                    foliage = Color.rgb(38, 76, 62);
                    break;
                case SUMMER:
                    seasonTint = Color.rgb(120, 164, 150);
                    foliage = Color.rgb(27, 64, 54);
                    break;
                case AUTUMN:
                    seasonTint = Color.rgb(170, 122, 101);
                    foliage = Color.rgb(70, 51, 47);
                    break;
                case WINTER:
                default:
                    seasonTint = Color.rgb(145, 159, 176);
                    foliage = Color.rgb(39, 47, 57);
                    break;
            }
            top = blend(top, seasonTint, 0.075f);
            midSky = blend(midSky, seasonTint, 0.10f);
            bottom = blend(bottom, seasonTint, 0.13f);

            float grayAmount = clamp01(s.cloud * 0.27f + s.rain * 0.25f + s.fog * 0.10f);
            int gray = Color.rgb(93, 100, 113);
            top = blend(top, gray, grayAmount);
            midSky = blend(midSky, gray, grayAmount * 0.95f);
            bottom = blend(bottom, gray, grayAmount * 0.88f);
            float darkness = 1f - s.storm * 0.20f - s.rain * 0.06f;
            top = darken(top, darkness);
            midSky = darken(midSky, darkness);
            bottom = darken(bottom, darkness);

            int far = blend(bottom, Color.rgb(107, 102, 124), 0.52f);
            int mid = blend(far, Color.rgb(70, 69, 91), 0.48f);
            int hero = blend(mid, Color.rgb(54, 54, 73), 0.42f);
            int near = blend(hero, Color.rgb(28, 34, 49), 0.55f);
            int hillFar = blend(near, foliage, 0.27f);
            int hillMid = blend(hillFar, Color.rgb(17, 27, 40), 0.50f);
            int ground = blend(hillMid, Color.rgb(7, 14, 25), 0.58f);
            int treeFar = blend(foliage, ground, 0.47f);
            int treeNear = blend(foliage, Color.rgb(6, 13, 22), 0.64f);
            int cloudLight = blend(Color.rgb(225, 227, 225), midSky, 0.52f);
            int cloudDark = blend(Color.rgb(67, 72, 86), top, 0.26f);
            int fog = blend(Color.rgb(193, 199, 203), bottom, 0.42f);
            int warm = Color.rgb(255, 186, 132);
            return new Palette(top, midSky, bottom, far, mid, hero, near, hillFar, hillMid,
                    ground, treeFar, treeNear, cloudLight, cloudDark, fog, warm);
        }
    }

    private static final class Keyframe {
        final float hour;
        final int top;
        final int mid;
        final int bottom;

        Keyframe(float hour, int top, int mid, int bottom) {
            this.hour = hour;
            this.top = top;
            this.mid = mid;
            this.bottom = bottom;
        }
    }

    private static float hash01(int value) {
        int x = value;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return (x & 0x7fffffff) / (float) 0x7fffffff;
    }

    private static float hashSigned(int value) {
        return hash01(value) * 2f - 1f;
    }

    private static float hashSmooth(float value) {
        int i = (int) Math.floor(value);
        float f = value - i;
        float a = hash01(i * 157 + 13);
        float b = hash01((i + 1) * 157 + 13);
        float t = f * f * (3f - 2f * f);
        return a + (b - a) * t;
    }

    private static float wrap(float value, float max) {
        if (max <= 0f) return 0f;
        float out = value % max;
        return out < 0f ? out + max : out;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    private static int blend(int a, int b, float t) {
        t = clamp01(t);
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a), aa = Color.alpha(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b), ba = Color.alpha(b);
        return Color.argb(
                (int) (aa + (ba - aa) * t),
                (int) (ar + (br - ar) * t),
                (int) (ag + (bg - ag) * t),
                (int) (ab + (bb - ab) * t)
        );
    }

    private static int darken(int color, float factor) {
        factor = Math.max(0f, factor);
        return Color.argb(Color.alpha(color),
                Math.min(255, (int) (Color.red(color) * factor)),
                Math.min(255, (int) (Color.green(color) * factor)),
                Math.min(255, (int) (Color.blue(color) * factor)));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
