package dev.andres.aetherscape.render;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Locale;

/**
 * Dependency-free procedural renderer shared by the preview and live wallpaper.
 * The art is deliberately geometric: layered mountains, hills, trees, ruins,
 * campfires, weather particles and slow parallax.
 */
public final class SceneRenderer {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private SceneState state;
    private long lastNanos;
    private float elapsed;
    private float worldOffset;
    private float launcherOffset;

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

    public int currentTargetFps() {
        return Math.max(10, Math.min(60, state.targetFps));
    }

    public void draw(Canvas canvas, int width, int height, SharedPreferences preferences, boolean preview) {
        if (width <= 0 || height <= 0) return;

        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        if (dt <= 0f || dt > 0.15f) dt = 1f / 30f;
        elapsed += dt;

        SceneState target = SceneState.fromPreferences(preferences);
        // Roughly 18–25 seconds for major weather changes, avoiding abrupt jumps.
        float alpha = 1f - (float) Math.exp(-dt * 0.055f);
        if (preview) alpha = Math.max(alpha, 0.018f);
        state.smoothToward(target, alpha);

        float speed = (8f + state.scrollSpeed * 28f) * (0.55f + state.motionIntensity * 0.65f);
        if (preview) speed *= 0.82f;
        worldOffset += dt * speed;

        Palette palette = Palette.forState(state);
        drawSky(canvas, width, height, palette);
        drawStars(canvas, width, height, palette);
        drawCelestial(canvas, width, height, palette);
        drawClouds(canvas, width, height, palette);
        drawMountains(canvas, width, height, palette);
        drawDistantFog(canvas, width, height, palette);
        drawGround(canvas, width, height, palette);
        drawWorldObjects(canvas, width, height, palette);
        drawWeather(canvas, width, height, palette);
        drawAtmosphere(canvas, width, height, palette);
        if (preview) drawPreviewHud(canvas, width, height, palette);
    }

    private void drawSky(Canvas canvas, int width, int height, Palette palette) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                0f, 0f, 0f, height,
                palette.skyTop, palette.skyBottom,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);

        float cloudDarken = state.cloud * 0.08f + state.rain * 0.10f;
        if (cloudDarken > 0.01f) {
            paint.setColor(Color.argb((int) (cloudDarken * 255), 24, 29, 43));
            canvas.drawRect(0, 0, width, height, paint);
        }
    }

    private void drawStars(Canvas canvas, int width, int height, Palette palette) {
        float night = state.nightFactor();
        if (!state.showStars || night < 0.03f) return;
        int count = 45 + (int) (state.effectIntensity * 65f);
        float topLimit = height * 0.56f;
        for (int i = 0; i < count; i++) {
            float x = hash01(i * 17 + 3) * width;
            float y = hash01(i * 29 + 11) * topLimit;
            float twinkle = 0.55f + 0.45f * (float) Math.sin(elapsed * (0.65f + hash01(i) * 1.4f) + i);
            float alpha = night * twinkle * (0.35f + hash01(i * 31) * 0.65f);
            float radius = 0.65f + hash01(i * 53) * 1.55f;
            if (state.showGlow && radius > 1.4f) {
                paint.setColor(Color.argb((int) (alpha * 55), 235, 240, 220));
                canvas.drawCircle(x, y, radius * 3.6f, paint);
            }
            paint.setColor(Color.argb((int) (alpha * 230), 246, 244, 221));
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private void drawCelestial(Canvas canvas, int width, int height, Palette palette) {
        float hour = state.hour;
        float dayProgress = (hour - 6f) / 12f;
        float night = state.nightFactor();
        boolean sunVisible = hour >= 5.3f && hour <= 19.8f;

        float x;
        float y;
        int core;
        if (sunVisible) {
            float p = clamp01(dayProgress);
            x = width * (0.08f + p * 0.84f);
            y = height * (0.37f - (float) Math.sin(p * Math.PI) * 0.27f);
            core = blend(Color.rgb(255, 236, 193), Color.rgb(255, 177, 114), state.duskFactor());
        } else {
            float p = hour < 6f ? (hour + 6f) / 12f : (hour - 18f) / 12f;
            p = clamp01(p);
            x = width * (0.12f + p * 0.76f);
            y = height * (0.31f - (float) Math.sin(p * Math.PI) * 0.18f);
            core = Color.rgb(231, 235, 220);
        }

        float radius = Math.max(10f, Math.min(width, height) * (sunVisible ? 0.035f : 0.026f));
        if (state.showGlow) {
            int glowAlpha = (int) ((sunVisible ? 58f : 34f) * (1f - state.cloud * 0.58f));
            for (int i = 5; i >= 1; i--) {
                paint.setColor(Color.argb(Math.max(1, glowAlpha / i), Color.red(core), Color.green(core), Color.blue(core)));
                canvas.drawCircle(x, y, radius * (1.5f + i * 0.85f), paint);
            }
        }
        paint.setColor(withAlpha(core, (int) (255f * (1f - state.cloud * 0.42f))));
        canvas.drawCircle(x, y, radius, paint);

        if (!sunVisible && night > 0.2f) {
            paint.setColor(withAlpha(palette.skyTop, 235));
            canvas.drawCircle(x + radius * 0.42f, y - radius * 0.16f, radius * 0.92f, paint);
        }
    }

    private void drawClouds(Canvas canvas, int width, int height, Palette palette) {
        int count = 2 + (int) (state.cloud * (8f + state.effectIntensity * 7f));
        if (count <= 2 && state.cloud < 0.08f) return;
        float parallax = state.parallax ? launcherOffset * width * 0.024f : 0f;
        for (int i = 0; i < count; i++) {
            float scale = 0.55f + hash01(i * 11 + 2) * 1.05f;
            float travel = elapsed * (2.5f + state.wind * 14f) * (0.6f + hash01(i * 13));
            float x = wrap(hash01(i * 37 + 1) * (width + 260f) + travel - 130f + parallax, width + 300f) - 150f;
            float y = height * (0.10f + hash01(i * 41 + 9) * 0.34f);
            float w = (70f + hash01(i * 19) * 95f) * scale;
            float h = w * (0.24f + hash01(i * 23) * 0.12f);
            int alpha = (int) ((42f + state.cloud * 78f) * (0.75f + hash01(i * 7) * 0.25f));
            int cloudColor = blend(palette.cloudLight, palette.cloudDark, state.rain * 0.72f + state.storm * 0.22f);
            paint.setColor(withAlpha(cloudColor, alpha));
            canvas.drawOval(new RectF(x - w * 0.45f, y - h * 0.10f, x + w * 0.55f, y + h * 0.75f), paint);
            canvas.drawCircle(x - w * 0.18f, y, h * 0.72f, paint);
            canvas.drawCircle(x + w * 0.08f, y - h * 0.18f, h * 0.86f, paint);
            canvas.drawCircle(x + w * 0.33f, y + h * 0.04f, h * 0.62f, paint);
        }
    }

    private void drawMountains(Canvas canvas, int width, int height, Palette palette) {
        float parallax = state.parallax ? launcherOffset * width : 0f;
        drawMountainLayer(canvas, width, height, worldOffset * 0.08f + parallax * 0.06f,
                height * 0.69f, height * 0.28f, 220f, palette.mountainFar, 0.42f);
        drawMountainLayer(canvas, width, height, worldOffset * 0.16f + parallax * 0.13f,
                height * 0.77f, height * 0.34f, 175f, palette.mountainMid, 0.62f);
        drawMountainLayer(canvas, width, height, worldOffset * 0.25f + parallax * 0.22f,
                height * 0.83f, height * 0.31f, 135f, palette.mountainNear, 0.80f);
    }

    private void drawMountainLayer(
            Canvas canvas,
            int width,
            int height,
            float offset,
            float baseline,
            float maxPeak,
            float spacing,
            int color,
            float depth
    ) {
        path.reset();
        path.moveTo(0, height);
        float worldLeft = offset;
        int start = (int) Math.floor(worldLeft / spacing) - 2;
        float firstX = start * spacing - worldLeft;
        path.lineTo(firstX, baseline);
        int end = start + (int) Math.ceil(width / spacing) + 6;
        for (int i = start; i <= end; i++) {
            float x = i * spacing - worldLeft;
            float peak = maxPeak * (0.45f + hash01(i * 47 + (int) (depth * 100)) * 0.72f);
            float shoulder = spacing * (0.22f + hash01(i * 67) * 0.20f);
            path.lineTo(x + shoulder, baseline - peak * 0.52f);
            path.lineTo(x + spacing * 0.52f, baseline - peak);
            path.lineTo(x + spacing - shoulder * 0.35f, baseline - peak * 0.24f);
            path.lineTo(x + spacing, baseline);
        }
        path.lineTo(width, height);
        path.close();
        paint.setColor(color);
        canvas.drawPath(path, paint);

        boolean winter = state.season == SceneState.Season.WINTER;
        boolean cold = state.temperatureC < 4f;
        float snowAlpha = state.snowCaps && (winter || cold || state.snow > 0.2f)
                ? clamp01(0.38f + state.snow * 0.55f + (cold ? 0.15f : 0f))
                : 0f;
        if (snowAlpha <= 0.02f || depth < 0.55f) return;

        for (int i = start; i <= end; i++) {
            float x = i * spacing - worldLeft + spacing * 0.52f;
            float peak = maxPeak * (0.45f + hash01(i * 47 + (int) (depth * 100)) * 0.72f);
            float topY = baseline - peak;
            float capW = spacing * (0.14f + hash01(i * 73) * 0.10f);
            float capH = peak * 0.17f;
            path.reset();
            path.moveTo(x, topY);
            path.lineTo(x - capW, topY + capH);
            path.lineTo(x - capW * 0.35f, topY + capH * 0.68f);
            path.lineTo(x, topY + capH * 1.12f);
            path.lineTo(x + capW * 0.30f, topY + capH * 0.70f);
            path.lineTo(x + capW, topY + capH);
            path.close();
            paint.setColor(Color.argb((int) (snowAlpha * 185), 225, 231, 229));
            canvas.drawPath(path, paint);
        }
    }

    private void drawDistantFog(Canvas canvas, int width, int height, Palette palette) {
        float fog = state.fog * state.effectIntensity;
        if (fog < 0.03f) return;
        int bands = 3 + (int) (fog * 4f);
        for (int i = 0; i < bands; i++) {
            float y = height * (0.50f + i * 0.065f);
            float drift = (float) Math.sin(elapsed * 0.08f + i) * width * 0.08f;
            float w = width * (0.72f + hash01(i * 89) * 0.38f);
            float h = height * (0.07f + hash01(i * 97) * 0.05f);
            paint.setColor(Color.argb((int) (fog * (25 + i * 8)),
                    Color.red(palette.fog), Color.green(palette.fog), Color.blue(palette.fog)));
            canvas.drawOval(new RectF(-w * 0.18f + drift, y, w + drift, y + h), paint);
            canvas.drawOval(new RectF(width - w * 0.70f - drift, y + h * 0.35f,
                    width + w * 0.20f - drift, y + h * 1.30f), paint);
        }
    }

    private void drawGround(Canvas canvas, int width, int height, Palette palette) {
        float parallax = state.parallax ? launcherOffset * width * 0.30f : 0f;
        drawHillLayer(canvas, width, height, worldOffset * 0.38f + parallax * 0.35f,
                height * 0.78f, height * 0.08f, palette.hillFar);
        drawHillLayer(canvas, width, height, worldOffset * 0.65f + parallax * 0.62f,
                height * 0.87f, height * 0.11f, palette.hillMid);
        drawHillLayer(canvas, width, height, worldOffset + parallax,
                height * 0.94f, height * 0.14f, palette.ground);
    }

    private void drawHillLayer(Canvas canvas, int width, int height, float offset, float base, float amplitude, int color) {
        path.reset();
        path.moveTo(0, height);
        int steps = Math.max(10, width / 55);
        for (int i = 0; i <= steps; i++) {
            float x = width * i / (float) steps;
            float worldX = x + offset;
            float y = base
                    - (float) Math.sin(worldX * 0.0045f) * amplitude * 0.46f
                    - (hashSmooth(worldX * 0.010f) - 0.5f) * amplitude;
            path.lineTo(x, y);
        }
        path.lineTo(width, height);
        path.close();
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    private void drawWorldObjects(Canvas canvas, int width, int height, Palette palette) {
        float parallax = state.parallax ? launcherOffset * width : 0f;
        drawTreeLayer(canvas, width, height, worldOffset * 0.62f + parallax * 0.56f,
                height * 0.82f, 0.62f, palette.treeFar, false);
        drawTreeLayer(canvas, width, height, worldOffset + parallax,
                height * 0.92f, 1f, palette.treeNear, true);
        drawStructures(canvas, width, height, worldOffset + parallax, palette);
        drawCampfires(canvas, width, height, worldOffset + parallax, palette);
    }

    private void drawTreeLayer(
            Canvas canvas,
            int width,
            int height,
            float offset,
            float base,
            float depth,
            int treeColor,
            boolean foreground
    ) {
        float spacing = (foreground ? 74f : 105f) + (1f - state.treeDensity) * 95f;
        float worldLeft = offset;
        int start = (int) Math.floor(worldLeft / spacing) - 3;
        int end = start + (int) Math.ceil(width / spacing) + 7;
        for (int i = start; i <= end; i++) {
            float chance = state.treeDensity * (foreground ? 0.95f : 0.72f);
            int biome = biomeAt(i * spacing);
            if (biome == 0) chance *= 0.45f;
            else if (biome == 1) chance *= 1.25f;
            else if (biome == 2) chance *= 0.62f;
            else if (biome == 4) chance *= 0.25f;
            if (hash01(i * 131 + (foreground ? 7 : 3)) > chance) continue;

            float x = i * spacing - worldLeft + hashSigned(i * 19) * spacing * 0.32f;
            float groundY = terrainY(x, offset, width, height, base);
            float scale = (0.70f + hash01(i * 43) * 0.78f) * depth;
            float sway = (float) Math.sin(elapsed * (0.9f + state.wind * 1.8f) + i * 0.7f)
                    * state.wind * 5.5f * scale;
            boolean deciduous = state.season != SceneState.Season.WINTER
                    && (biome == 0 || biome == 2 || hash01(i * 83) > 0.58f);
            if (deciduous) drawRoundTree(canvas, x, groundY, scale, sway, treeColor, i);
            else drawPine(canvas, x, groundY, scale, sway, treeColor, i);
        }
    }

    private float terrainY(float screenX, float offset, int width, int height, float base) {
        float worldX = screenX + offset;
        float amplitude = height * 0.07f;
        return base
                - (float) Math.sin(worldX * 0.0045f) * amplitude * 0.46f
                - (hashSmooth(worldX * 0.010f) - 0.5f) * amplitude;
    }

    private void drawPine(Canvas canvas, float x, float groundY, float scale, float sway, int color, int seed) {
        float height = (72f + hash01(seed * 17) * 78f) * scale;
        float trunk = Math.max(2f, 7f * scale);
        paint.setColor(darken(color, 0.76f));
        canvas.save();
        canvas.rotate(sway, x, groundY);
        canvas.drawRect(x - trunk / 2f, groundY - height * 0.76f, x + trunk / 2f, groundY, paint);
        paint.setColor(color);
        int tiers = 4 + (int) (hash01(seed * 53) * 2f);
        for (int tier = 0; tier < tiers; tier++) {
            float t = tier / (float) Math.max(1, tiers - 1);
            float centerY = groundY - height * (0.28f + t * 0.55f);
            float halfW = height * (0.25f - t * 0.115f);
            float tierH = height * (0.28f - t * 0.035f);
            path.reset();
            path.moveTo(x, centerY - tierH * 0.75f);
            path.lineTo(x - halfW, centerY + tierH * 0.38f);
            path.lineTo(x + halfW, centerY + tierH * 0.38f);
            path.close();
            canvas.drawPath(path, paint);
        }
        canvas.restore();
    }

    private void drawRoundTree(Canvas canvas, float x, float groundY, float scale, float sway, int color, int seed) {
        float height = (62f + hash01(seed * 23) * 64f) * scale;
        float trunk = Math.max(2f, 7f * scale);
        canvas.save();
        canvas.rotate(sway, x, groundY);
        paint.setColor(darken(color, 0.70f));
        canvas.drawRect(x - trunk / 2f, groundY - height * 0.67f, x + trunk / 2f, groundY, paint);
        int foliage = seasonFoliage(color);
        paint.setColor(foliage);
        float radius = height * 0.22f;
        canvas.drawCircle(x, groundY - height * 0.70f, radius, paint);
        canvas.drawCircle(x - radius * 0.72f, groundY - height * 0.60f, radius * 0.82f, paint);
        canvas.drawCircle(x + radius * 0.75f, groundY - height * 0.59f, radius * 0.88f, paint);
        canvas.drawCircle(x + radius * 0.18f, groundY - height * 0.84f, radius * 0.78f, paint);
        canvas.restore();
    }

    private int seasonFoliage(int base) {
        switch (state.season) {
            case SPRING: return blend(base, Color.rgb(91, 128, 100), 0.35f);
            case SUMMER: return blend(base, Color.rgb(42, 95, 72), 0.28f);
            case AUTUMN: return blend(base, Color.rgb(135, 78, 53), 0.65f);
            case WINTER: return blend(base, Color.rgb(46, 58, 64), 0.78f);
            default: return base;
        }
    }

    private void drawStructures(Canvas canvas, int width, int height, float offset, Palette palette) {
        float segment = 820f;
        int start = (int) Math.floor(offset / segment) - 2;
        int end = start + (int) Math.ceil(width / segment) + 5;
        for (int i = start; i <= end; i++) {
            int biome = biomeAt(i * segment);
            float x = i * segment - offset + segment * (0.28f + hash01(i * 31) * 0.44f);
            float y = terrainY(x, offset, width, height, height * 0.92f);
            if (biome == 2 && hash01(i * 101) < 0.78f) drawRuin(canvas, x, y, palette, i);
            else if (biome == 3 && hash01(i * 79) < 0.46f) drawTent(canvas, x, y, palette, i);
        }
    }

    private void drawRuin(Canvas canvas, float x, float groundY, Palette palette, int seed) {
        float scale = 0.72f + hash01(seed * 17) * 0.58f;
        float width = 72f * scale;
        float height = 88f * scale;
        paint.setColor(darken(palette.treeNear, 0.66f));
        canvas.drawRect(x - width * 0.50f, groundY - height, x - width * 0.26f, groundY, paint);
        canvas.drawRect(x + width * 0.22f, groundY - height * 0.82f, x + width * 0.48f, groundY, paint);
        canvas.drawRect(x - width * 0.48f, groundY - height, x + width * 0.48f, groundY - height * 0.78f, paint);
        paint.setColor(palette.ground);
        rect.set(x - width * 0.13f, groundY - height * 0.55f, x + width * 0.18f, groundY + 6f);
        canvas.drawOval(rect, paint);
    }

    private void drawTent(Canvas canvas, float x, float groundY, Palette palette, int seed) {
        float scale = 0.75f + hash01(seed * 29) * 0.45f;
        float w = 72f * scale;
        float h = 48f * scale;
        paint.setColor(blend(palette.hillMid, Color.rgb(133, 87, 67), 0.42f));
        path.reset();
        path.moveTo(x, groundY - h);
        path.lineTo(x - w * 0.55f, groundY);
        path.lineTo(x + w * 0.55f, groundY);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(darken(palette.ground, 0.82f));
        path.reset();
        path.moveTo(x, groundY - h);
        path.lineTo(x, groundY);
        path.lineTo(x + w * 0.20f, groundY);
        path.close();
        canvas.drawPath(path, paint);
        stroke.setStrokeWidth(Math.max(1.5f, 2.2f * scale));
        stroke.setColor(darken(palette.treeNear, 0.68f));
        canvas.drawLine(x, groundY - h - 8f * scale, x, groundY + 3f, stroke);
    }

    private void drawCampfires(Canvas canvas, int width, int height, float offset, Palette palette) {
        if (!state.showCampfires) return;
        float segment = 690f;
        int start = (int) Math.floor(offset / segment) - 2;
        int end = start + (int) Math.ceil(width / segment) + 5;
        float nightBoost = 0.35f + state.nightFactor() * 0.65f;
        for (int i = start; i <= end; i++) {
            if (hash01(i * 97 + 12) > 0.26f + state.sceneVariety * 0.20f) continue;
            float x = i * segment - offset + segment * (0.30f + hash01(i * 47) * 0.42f);
            float y = terrainY(x, offset, width, height, height * 0.92f) - 3f;
            float flicker = 0.82f + 0.18f * (float) Math.sin(elapsed * 7.5f + i);
            float radius = 10f + state.effectIntensity * 8f;
            if (state.showGlow) {
                for (int ring = 4; ring >= 1; ring--) {
                    paint.setColor(Color.argb((int) (nightBoost * 12f * (5 - ring)), 255, 144, 74));
                    canvas.drawCircle(x, y - radius * 0.6f, radius * (0.8f + ring * 0.62f), paint);
                }
            }
            paint.setColor(darken(palette.treeNear, 0.55f));
            stroke.setColor(darken(palette.treeNear, 0.50f));
            stroke.setStrokeWidth(3.5f);
            canvas.drawLine(x - 12f, y, x + 11f, y - 5f, stroke);
            canvas.drawLine(x - 10f, y - 5f, x + 12f, y, stroke);
            paint.setColor(Color.rgb(255, 137, 67));
            path.reset();
            path.moveTo(x, y - 4f);
            path.cubicTo(x - 13f, y - 16f, x - 3f, y - 28f * flicker, x + 1f, y - 35f * flicker);
            path.cubicTo(x + 7f, y - 24f, x + 14f, y - 15f, x, y - 4f);
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(Color.rgb(255, 230, 144));
            path.reset();
            path.moveTo(x, y - 6f);
            path.cubicTo(x - 5f, y - 15f, x, y - 23f * flicker, x + 2f, y - 27f * flicker);
            path.cubicTo(x + 7f, y - 16f, x + 7f, y - 11f, x, y - 6f);
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    private void drawWeather(Canvas canvas, int width, int height, Palette palette) {
        float intensity = state.effectIntensity;
        if (state.rain > 0.02f) drawRain(canvas, width, height, state.rain * intensity);
        if (state.snow > 0.02f) drawSnow(canvas, width, height, state.snow * intensity);
        if (state.season == SceneState.Season.AUTUMN && state.wind > 0.16f) {
            drawLeaves(canvas, width, height, state.wind * intensity);
        }
        if (state.showFireflies && state.nightFactor() > 0.20f && state.rain < 0.35f) {
            drawFireflies(canvas, width, height, palette);
        }
        if (state.showLightning && state.storm > 0.12f) drawLightning(canvas, width, height);
    }

    private void drawRain(Canvas canvas, int width, int height, float intensity) {
        int count = 35 + (int) (intensity * 185f);
        float fallSpeed = 360f + intensity * 680f;
        float slant = 12f + state.wind * 42f;
        stroke.setStrokeWidth(1f + intensity * 1.35f);
        stroke.setColor(Color.argb((int) (45 + intensity * 95), 202, 218, 224));
        for (int i = 0; i < count; i++) {
            float y = wrap(hash01(i * 59 + 4) * (height + 180f) + elapsed * fallSpeed, height + 220f) - 110f;
            float x = wrap(hash01(i * 71 + 8) * (width + 180f) + elapsed * state.wind * 85f + y * 0.16f,
                    width + 220f) - 110f;
            float len = 9f + hash01(i * 31) * 17f + intensity * 13f;
            canvas.drawLine(x, y, x + slant * 0.23f, y + len, stroke);
        }
    }

    private void drawSnow(Canvas canvas, int width, int height, float intensity) {
        int count = 28 + (int) (intensity * 115f);
        for (int i = 0; i < count; i++) {
            float speed = 24f + hash01(i * 17) * 54f + intensity * 40f;
            float y = wrap(hash01(i * 61 + 5) * (height + 80f) + elapsed * speed, height + 100f) - 50f;
            float xBase = hash01(i * 73 + 9) * width;
            float x = wrap(xBase + (float) Math.sin(elapsed * (0.6f + hash01(i) * 1.4f) + i) * 18f
                    + elapsed * state.wind * 24f, width + 30f) - 15f;
            float radius = 1.2f + hash01(i * 37) * 3.0f;
            paint.setColor(Color.argb((int) (90 + intensity * 120), 235, 239, 233));
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private void drawLeaves(Canvas canvas, int width, int height, float intensity) {
        int count = 8 + (int) (intensity * 38f);
        for (int i = 0; i < count; i++) {
            float speed = 30f + intensity * 95f;
            float x = wrap(hash01(i * 43) * (width + 80f) + elapsed * speed, width + 100f) - 50f;
            float y = wrap(hash01(i * 67) * height + elapsed * (12f + hash01(i) * 18f), height + 40f) - 20f;
            float size = 2.5f + hash01(i * 89) * 4.5f;
            int c = blend(Color.rgb(167, 91, 55), Color.rgb(204, 144, 68), hash01(i * 101));
            paint.setColor(Color.argb(145, Color.red(c), Color.green(c), Color.blue(c)));
            canvas.save();
            canvas.rotate(elapsed * 70f + i * 27f, x, y);
            path.reset();
            path.moveTo(x, y - size);
            path.lineTo(x + size * 0.65f, y);
            path.lineTo(x, y + size);
            path.lineTo(x - size * 0.65f, y);
            path.close();
            canvas.drawPath(path, paint);
            canvas.restore();
        }
    }

    private void drawFireflies(Canvas canvas, int width, int height, Palette palette) {
        float amount = state.effectIntensity * (1f - state.rain) * state.nightFactor();
        int count = 5 + (int) (amount * 26f);
        for (int i = 0; i < count; i++) {
            float x = hash01(i * 41 + 2) * width
                    + (float) Math.sin(elapsed * (0.35f + hash01(i) * 0.7f) + i) * 24f;
            float y = height * (0.56f + hash01(i * 53) * 0.32f)
                    + (float) Math.cos(elapsed * (0.42f + hash01(i * 7) * 0.6f) + i) * 13f;
            float pulse = 0.45f + 0.55f * (float) Math.sin(elapsed * 1.9f + i * 1.7f);
            if (state.showGlow) {
                paint.setColor(Color.argb((int) (pulse * 32f), 236, 210, 111));
                canvas.drawCircle(x, y, 9f, paint);
            }
            paint.setColor(Color.argb((int) (90 + pulse * 140f), 247, 224, 132));
            canvas.drawCircle(x, y, 1.4f + pulse, paint);
        }
    }

    private void drawLightning(Canvas canvas, int width, int height) {
        float cycle = elapsed % 10.5f;
        float trigger = 9.85f - state.storm * 0.75f;
        if (cycle < trigger) return;
        float flash = clamp01((cycle - trigger) / 0.09f);
        if (cycle > trigger + 0.18f) flash = clamp01(1f - (cycle - trigger - 0.18f) / 0.22f);
        flash *= state.storm;
        paint.setColor(Color.argb((int) (flash * 95f), 222, 224, 241));
        canvas.drawRect(0, 0, width, height, paint);

        float x = width * (0.25f + hash01((int) (elapsed / 10f) * 43) * 0.55f);
        float y = height * 0.12f;
        stroke.setColor(Color.argb((int) (flash * 240f), 235, 231, 255));
        stroke.setStrokeWidth(2.2f + state.storm * 1.8f);
        path.reset();
        path.moveTo(x, y);
        for (int i = 1; i <= 6; i++) {
            x += hashSigned(i * 71 + (int) elapsed) * width * 0.035f;
            y += height * (0.055f + hash01(i * 31) * 0.035f);
            path.lineTo(x, y);
        }
        canvas.drawPath(path, stroke);
    }

    private void drawAtmosphere(Canvas canvas, int width, int height, Palette palette) {
        float rainMist = state.rain * 0.10f + state.fog * 0.12f;
        if (rainMist > 0.01f) {
            paint.setColor(Color.argb((int) (rainMist * 255f),
                    Color.red(palette.fog), Color.green(palette.fog), Color.blue(palette.fog)));
            canvas.drawRect(0, 0, width, height, paint);
        }

        paint.setShader(new LinearGradient(0, height * 0.45f, 0, height,
                Color.TRANSPARENT,
                Color.argb(58, 6, 10, 16),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, height * 0.45f, width, height, paint);
        paint.setShader(null);
    }

    private void drawPreviewHud(Canvas canvas, int width, int height, Palette palette) {
        float margin = Math.max(12f, width * 0.025f);
        float boxH = Math.max(42f, height * 0.105f);
        rect.set(margin, margin, width - margin, margin + boxH);
        paint.setColor(Color.argb(88, 8, 14, 23));
        canvas.drawRoundRect(rect, 18f, 18f, paint);
        paint.setColor(Color.argb(225, 244, 246, 242));
        paint.setTextSize(Math.max(14f, Math.min(width, height) * 0.040f));
        paint.setFakeBoldText(true);
        String time = String.format(Locale.getDefault(), "%02d:%02d",
                (int) state.hour, (int) ((state.hour % 1f) * 60f));
        canvas.drawText(time, margin + 16f, margin + boxH * 0.58f, paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(Math.max(10f, Math.min(width, height) * 0.026f));
        paint.setColor(Color.argb(200, 226, 232, 227));
        String season = seasonLabel(state.season);
        String desc = state.weatherDescription == null || state.weatherDescription.isEmpty()
                ? "Paisaje dinámico"
                : state.weatherDescription;
        String text = season + " · " + desc;
        float textWidth = paint.measureText(text);
        if (textWidth > width * 0.62f) {
            while (text.length() > 8 && paint.measureText(text + "…") > width * 0.62f) {
                text = text.substring(0, text.length() - 1);
            }
            text += "…";
        }
        canvas.drawText(text, width - margin - 16f - paint.measureText(text), margin + boxH * 0.58f, paint);
    }

    private int biomeAt(float worldX) {
        int segment = (int) Math.floor(worldX / 760f);
        float v = hash01(segment * 181 + 17);
        float variety = state.sceneVariety;
        if (v < 0.20f + (1f - variety) * 0.12f) return 0; // open hills
        if (v < 0.48f) return 1; // forest
        if (v < 0.66f + variety * 0.08f) return 2; // ruins
        if (v < 0.86f) return 3; // camp / alpine
        return 4; // open / lake-like clearing
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

    private static final class Palette {
        final int skyTop;
        final int skyBottom;
        final int mountainFar;
        final int mountainMid;
        final int mountainNear;
        final int hillFar;
        final int hillMid;
        final int ground;
        final int treeFar;
        final int treeNear;
        final int cloudLight;
        final int cloudDark;
        final int fog;

        Palette(int skyTop, int skyBottom, int mountainFar, int mountainMid, int mountainNear,
                int hillFar, int hillMid, int ground, int treeFar, int treeNear,
                int cloudLight, int cloudDark, int fog) {
            this.skyTop = skyTop;
            this.skyBottom = skyBottom;
            this.mountainFar = mountainFar;
            this.mountainMid = mountainMid;
            this.mountainNear = mountainNear;
            this.hillFar = hillFar;
            this.hillMid = hillMid;
            this.ground = ground;
            this.treeFar = treeFar;
            this.treeNear = treeNear;
            this.cloudLight = cloudLight;
            this.cloudDark = cloudDark;
            this.fog = fog;
        }

        static Palette forState(SceneState s) {
            float h = ((s.hour % 24f) + 24f) % 24f;
            Keyframe[] frames = new Keyframe[] {
                    new Keyframe(0f,  Color.rgb(16, 21, 42), Color.rgb(43, 43, 69)),
                    new Keyframe(4.5f,Color.rgb(24, 30, 55), Color.rgb(91, 72, 91)),
                    new Keyframe(6.5f,Color.rgb(103, 104, 139), Color.rgb(230, 161, 134)),
                    new Keyframe(9f,  Color.rgb(114, 171, 195), Color.rgb(208, 213, 190)),
                    new Keyframe(13f, Color.rgb(92, 169, 205), Color.rgb(210, 218, 202)),
                    new Keyframe(17f, Color.rgb(111, 146, 169), Color.rgb(222, 182, 145)),
                    new Keyframe(19.5f,Color.rgb(90, 73, 119), Color.rgb(224, 132, 126)),
                    new Keyframe(22f, Color.rgb(29, 31, 57), Color.rgb(65, 52, 78)),
                    new Keyframe(24f, Color.rgb(16, 21, 42), Color.rgb(43, 43, 69))
            };
            Keyframe a = frames[0];
            Keyframe b = frames[frames.length - 1];
            for (int i = 0; i < frames.length - 1; i++) {
                if (h >= frames[i].hour && h <= frames[i + 1].hour) {
                    a = frames[i]; b = frames[i + 1]; break;
                }
            }
            float t = (h - a.hour) / Math.max(0.001f, b.hour - a.hour);
            int top = blend(a.top, b.top, t);
            int bottom = blend(a.bottom, b.bottom, t);

            int seasonTint;
            int foliage;
            switch (s.season) {
                case SPRING:
                    seasonTint = Color.rgb(158, 184, 162); foliage = Color.rgb(41, 78, 64); break;
                case SUMMER:
                    seasonTint = Color.rgb(128, 172, 154); foliage = Color.rgb(28, 66, 56); break;
                case AUTUMN:
                    seasonTint = Color.rgb(176, 132, 104); foliage = Color.rgb(72, 54, 47); break;
                case WINTER:
                default:
                    seasonTint = Color.rgb(151, 166, 178); foliage = Color.rgb(41, 49, 58); break;
            }
            top = blend(top, seasonTint, 0.09f);
            bottom = blend(bottom, seasonTint, 0.14f);

            float grayAmount = clamp01(s.cloud * 0.30f + s.rain * 0.24f + s.fog * 0.12f);
            int gray = Color.rgb(102, 108, 119);
            top = blend(top, gray, grayAmount);
            bottom = blend(bottom, gray, grayAmount * 0.92f);
            float darkness = 1f - s.storm * 0.18f - s.rain * 0.06f;
            top = darken(top, darkness);
            bottom = darken(bottom, darkness);

            int far = blend(bottom, Color.rgb(72, 79, 96), 0.62f);
            int mid = blend(far, Color.rgb(40, 49, 66), 0.54f);
            int near = blend(mid, Color.rgb(24, 33, 47), 0.60f);
            int hillFar = blend(near, foliage, 0.28f);
            int hillMid = blend(hillFar, Color.rgb(18, 29, 40), 0.50f);
            int ground = blend(hillMid, Color.rgb(10, 19, 29), 0.58f);
            int treeFar = blend(foliage, ground, 0.48f);
            int treeNear = blend(foliage, Color.rgb(9, 18, 25), 0.62f);
            int cloudLight = blend(Color.rgb(226, 226, 218), top, 0.48f);
            int cloudDark = blend(Color.rgb(76, 79, 91), top, 0.24f);
            int fog = blend(Color.rgb(194, 202, 201), bottom, 0.38f);
            return new Palette(top, bottom, far, mid, near, hillFar, hillMid, ground,
                    treeFar, treeNear, cloudLight, cloudDark, fog);
        }
    }

    private static final class Keyframe {
        final float hour;
        final int top;
        final int bottom;
        Keyframe(float hour, int top, int bottom) {
            this.hour = hour;
            this.top = top;
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
