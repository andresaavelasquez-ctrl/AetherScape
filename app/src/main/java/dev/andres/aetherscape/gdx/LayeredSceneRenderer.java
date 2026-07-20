package dev.andres.aetherscape.gdx;

import android.content.SharedPreferences;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.render.SceneState;

/**
 * GPU scene composer for AetherScape.
 *
 * v0.5 fixes the two problems reported in the previous build:
 * 1) every orientation now uses the same 1000-unit vertical world, so nothing stretches;
 * 2) all animated objects live in world coordinates and are anchored to the terrain,
 *    instead of being stamped at fixed screen coordinates.
 */
public final class LayeredSceneRenderer implements Disposable {
    private static final String TAG = "AetherScapeGPU";
    private static final float WORLD_HEIGHT = 1000f;
    private static final float LAYER_WIDTH = 2400f;
    private static final float BLOOM_SCALE = 0.25f;
    private static final Color WHITE = new Color(1f, 1f, 1f, 1f);

    private final SharedPreferences preferences;
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final OrthographicCamera worldCamera = new OrthographicCamera();
    private final OrthographicCamera screenCamera = new OrthographicCamera();
    private final SceneObjects objects = new SceneObjects();

    private final Texture stars;
    private final Texture cloudsFar;
    private final Texture cloudsNear;
    private final Texture mountainsFar;
    private final Texture mountainsMid;
    private final Texture mountainsHero;
    private final Texture mountainsNear;
    private final Texture snowCaps;
    private final Texture fogValley;
    private final Texture forestFar;
    private final Texture forestMid;
    private final Texture hillMid;
    private final Texture hillFront;
    private final Texture pineTall;
    private final Texture pineMedium;
    private final Texture pineSparse;
    private final Texture pineDead;
    private final Texture lantern;
    private final Texture lanternEmission;
    private final Texture campfire;
    private final Texture campfireEmission;
    private final Texture glow;
    private final Texture sunDisc;
    private final Texture moonCrescent;
    private final Texture noise;

    private FrameBuffer sceneFbo;
    private FrameBuffer emissionFbo;
    private FrameBuffer blurA;
    private FrameBuffer blurB;
    private TextureRegion sceneRegion;
    private TextureRegion emissionRegion;
    private TextureRegion blurARegion;
    private TextureRegion blurBRegion;
    private ShaderProgram blurShader;

    private SceneState state;
    private SceneState target;
    private int screenWidth = 1;
    private int screenHeight = 1;
    private int renderWidth = 1;
    private int renderHeight = 1;
    private int bloomWidth = 1;
    private int bloomHeight = 1;
    private float worldWidth = 562.5f;
    private float elapsed;
    private float scroll;
    private float launcherOffset;
    private float sensorOffset;
    private float effectiveOffset;
    private float interactionPulse;
    private float interactionGust;
    private float touchWorldX;
    private float touchWorldY;
    private boolean preview;
    private boolean portrait = true;
    private int targetFps;

    public LayeredSceneRenderer(SharedPreferences preferences) {
        this.preferences = preferences;
        state = SceneState.fromPreferences(preferences);
        target = state.copy();
        targetFps = clampFps(state.targetFps);

        stars = texture("aether/layers/stars.png");
        cloudsFar = texture("aether/layers/clouds_far.png");
        cloudsNear = texture("aether/layers/clouds_near.png");
        mountainsFar = texture("aether/layers/mountains_far.png");
        mountainsMid = texture("aether/layers/mountains_mid.png");
        mountainsHero = texture("aether/layers/mountains_hero.png");
        mountainsNear = texture("aether/layers/mountains_near.png");
        snowCaps = texture("aether/layers/snow_caps.png");
        fogValley = texture("aether/layers/fog_valley.png");
        forestFar = texture("aether/layers/forest_far.png");
        forestMid = texture("aether/layers/forest_mid.png");
        hillMid = texture("aether/layers/hill_mid.png");
        hillFront = texture("aether/layers/hill_front.png");
        pineTall = texture("aether/objects/pine_tall.png");
        pineMedium = texture("aether/objects/pine_medium.png");
        pineSparse = texture("aether/objects/pine_sparse.png");
        pineDead = texture("aether/objects/pine_dead.png");
        lantern = texture("aether/objects/lantern.png");
        lanternEmission = texture("aether/objects/lantern_emission.png");
        campfire = texture("aether/objects/campfire.png");
        campfireEmission = texture("aether/objects/campfire_emission.png");
        glow = texture("aether/objects/glow.png");
        sunDisc = texture("aether/objects/sun_disc.png");
        moonCrescent = texture("aether/objects/moon_crescent.png");
        noise = texture("aether/objects/noise_soft.png");

        ShaderProgram.pedantic = false;
        blurShader = new ShaderProgram(DEFAULT_VERTEX, BLUR_FRAGMENT);
        if (!blurShader.isCompiled()) {
            Gdx.app.error(TAG, "Bloom shader disabled: " + blurShader.getLog());
            blurShader.dispose();
            blurShader = null;
        }
    }

    public int targetFps() {
        return targetFps;
    }

    public void setLauncherOffset(float offset) {
        launcherOffset = MathUtils.clamp(offset, -1f, 1f);
    }

    public void setPreview(boolean preview) {
        this.preview = preview;
    }

    public void pulseLanterns() {
        interactionPulse = 1f;
        interactionGust = Math.max(interactionGust, 0.35f);
    }

    public void touch(float normalizedX, float normalizedY) {
        interactionPulse = 1f;
        interactionGust = 1f;
        touchWorldX = (MathUtils.clamp(normalizedX, 0f, 1f) - 0.5f) * worldWidth;
        touchWorldY = (1f - MathUtils.clamp(normalizedY, 0f, 1f)) * WORLD_HEIGHT;
    }

    public void reloadPreferences() {
        target = SceneState.fromPreferences(preferences);
        targetFps = clampFps(target.targetFps);
    }

    public void resize(int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        portrait = screenHeight >= screenWidth;

        // The vertical world NEVER changes with orientation. Landscape reveals more width.
        worldWidth = WORLD_HEIGHT * screenWidth / (float) screenHeight;
        worldCamera.setToOrtho(false, worldWidth, WORLD_HEIGHT);
        worldCamera.position.set(0f, WORLD_HEIGHT * 0.5f, 0f);
        worldCamera.update();

        float renderScale = preferences.getBoolean(AppPreferences.BATTERY_SAVER, false) ? 0.66f : 0.88f;
        if (screenWidth <= 720) renderScale = Math.max(renderScale, 0.94f);
        renderWidth = Math.max(320, Math.round(screenWidth * renderScale));
        renderHeight = Math.max(320, Math.round(screenHeight * renderScale));
        bloomWidth = Math.max(128, Math.round(renderWidth * BLOOM_SCALE));
        bloomHeight = Math.max(128, Math.round(renderHeight * BLOOM_SCALE));
        rebuildBuffers();
    }

    public void render() {
        float dt = MathUtils.clamp(Gdx.graphics.getDeltaTime(), 0f, 0.05f);
        if (dt == 0f) dt = 1f / Math.max(15, targetFps);
        elapsed += dt;

        target = SceneState.fromPreferences(preferences);
        float smooth = 1f - (float) Math.exp(-dt * 1.25f);
        state.smoothToward(target, smooth);
        targetFps = clampFps(state.targetFps);
        interactionPulse = Math.max(0f, interactionPulse - dt * 0.58f);
        interactionGust = Math.max(0f, interactionGust - dt * 0.24f);

        float sensorTarget = 0f;
        if (state.parallax && !preview) {
            // Small sensor contribution. It never overrules launcher scrolling.
            sensorTarget = MathUtils.clamp(-Gdx.input.getAccelerometerX() / 9.81f, -1f, 1f) * 0.16f;
        }
        sensorOffset = MathUtils.lerp(sensorOffset, sensorTarget, 1f - (float) Math.exp(-dt * 2.8f));
        float automaticDrift = (float) Math.sin(elapsed * 0.055f) * (preview ? 0.10f : 0.045f);
        effectiveOffset = state.parallax
                ? MathUtils.clamp(launcherOffset + sensorOffset + automaticDrift, -1f, 1f)
                : 0f;

        float speed = 10f + state.scrollSpeed * 26f;
        if (preview) speed *= 1.35f;
        scroll += dt * speed * (0.55f + state.motionIntensity * 0.70f);
        if (scroll > 1_000_000f) scroll -= 1_000_000f;

        if (sceneFbo == null) resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        renderScenePass();
        renderEmissionPass();
        blurEmission();
        compositeToScreen();
    }

    private void renderScenePass() {
        sceneFbo.begin();
        Gdx.gl.glViewport(0, 0, renderWidth, renderHeight);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glClearColor(0.02f, 0.025f, 0.045f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        drawSkyGradient();
        batch.setProjectionMatrix(worldCamera.combined);
        batch.begin();
        drawLayer(stars, 0.002f, 0f,
                state.showStars ? 0.70f * state.nightFactor() * (1f - state.cloud * 0.78f) : 0f,
                WHITE, 120f, 0f, 1f);
        drawCelestial(false);
        drawLayer(cloudsFar, 0.012f, 2.2f + effectiveWind() * 8f,
                0.18f + state.cloud * 0.48f,
                tint(0.89f, 0.84f, 0.92f, 1f), 430f, 0f, 1f);
        drawLayer(mountainsFar, 0.035f, 0f, 0.68f,
                paletteTint(0), 0f, 0f, 1f);
        drawLayer(fogValley, 0.030f, 0.35f,
                0.10f + state.fog * 0.38f,
                tint(0.90f, 0.84f, 0.92f, 1f), 720f, 0f, 1f);
        drawLayer(mountainsMid, 0.070f, 0f, 0.82f,
                paletteTint(1), 250f, 0f, 1f);
        drawLayer(mountainsHero, 0.105f, 0f, 0.98f,
                paletteTint(2), 0f, 0f, 1f);
        float snowAlpha = state.snowCaps && (state.season == SceneState.Season.WINTER
                || state.temperatureC < 4f || state.snow > 0.16f)
                ? MathUtils.clamp(0.32f + state.snow * 0.62f
                + (state.temperatureC < 4f ? 0.20f : 0f), 0f, 0.94f)
                : 0f;
        drawLayer(snowCaps, 0.105f, 0f, snowAlpha,
                tint(0.97f, 0.95f, 0.98f, 1f), 0f, 0f, 1f);
        drawLayer(mountainsNear, 0.155f, 0f, 0.98f,
                paletteTint(3), 610f, 0f, 1f);
        drawLayer(forestFar, 0.205f, 0f, 0.74f,
                seasonForestTint(0.78f), 830f, 0f, 1f);
        drawLayer(cloudsNear, 0.030f, 4.5f + effectiveWind() * 16f,
                state.cloud * 0.70f,
                tint(0.76f, 0.75f, 0.84f, 1f), 980f, 0f, 1f);
        drawLayer(forestMid, 0.285f, 0f, 0.92f,
                seasonForestTint(0.88f), 1210f, 0f, 1f);
        drawLayer(hillMid, 0.390f, 0f, 1f,
                tint(0.78f, 0.76f, 0.86f, 1f), 300f, 0f, 1f);
        objects.drawBack(batch, state, scroll, worldWidth, pineMedium, pineSparse, pineDead,
                effectiveWind(), elapsed);
        drawLayer(fogValley, 0.150f, 0.55f,
                state.fog * 0.34f + state.rain * 0.06f,
                tint(0.82f, 0.79f, 0.88f, 1f), 1480f, -45f, 1.05f);
        drawLayer(hillFront, 0.670f, 0f, 1f, WHITE, 900f, 0f, 1f);
        objects.drawFront(batch, state, scroll, worldWidth,
                pineTall, pineMedium, pineSparse,
                lantern, campfire, false, elapsed, effectiveWind(), interactionPulse);
        drawFireflies(false);
        batch.end();

        drawRainAndSnow();
        drawAtmosphereOverlays();
        sceneFbo.end();
    }

    private void renderEmissionPass() {
        emissionFbo.begin();
        Gdx.gl.glViewport(0, 0, bloomWidth, bloomHeight);
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(worldCamera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        drawCelestial(true);
        objects.drawFront(batch, state, scroll, worldWidth,
                pineTall, pineMedium, pineSparse,
                lanternEmission, campfireEmission, true, elapsed, effectiveWind(), interactionPulse);
        drawFireflies(true);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        emissionFbo.end();
    }

    private void blurEmission() {
        if (blurShader == null) return;
        screenCamera.setToOrtho(false, bloomWidth, bloomHeight);
        batch.setProjectionMatrix(screenCamera.combined);
        batch.setShader(blurShader);

        blurA.begin();
        Gdx.gl.glViewport(0, 0, bloomWidth, bloomHeight);
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        blurShader.setUniformf("u_direction", 1f / bloomWidth, 0f);
        batch.draw(emissionRegion, 0, 0, bloomWidth, bloomHeight);
        batch.end();
        blurA.end();

        blurB.begin();
        Gdx.gl.glViewport(0, 0, bloomWidth, bloomHeight);
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        blurShader.setUniformf("u_direction", 0f, 1f / bloomHeight);
        batch.draw(blurARegion, 0, 0, bloomWidth, bloomHeight);
        batch.end();
        blurB.end();
        batch.setShader(null);
    }

    private void compositeToScreen() {
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        Gdx.gl.glClearColor(0.01f, 0.015f, 0.025f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        screenCamera.setToOrtho(false, screenWidth, screenHeight);
        batch.setProjectionMatrix(screenCamera.combined);
        batch.setColor(Color.WHITE);
        batch.begin();
        batch.draw(sceneRegion, 0, 0, screenWidth, screenHeight);
        batch.end();

        if (state.showGlow) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            float pulse = 0.60f + interactionPulse * 0.32f;
            batch.setColor(1f, 0.91f, 0.84f, pulse);
            batch.begin();
            batch.draw(blurShader == null ? emissionRegion : blurBRegion,
                    0, 0, screenWidth, screenHeight);
            batch.end();
            batch.setColor(Color.WHITE);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }

        // Vignette protects icon legibility without flattening the sky.
        shapes.setProjectionMatrix(screenCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        int bands = 30;
        for (int i = 0; i < bands; i++) {
            float t = i / (float) bands;
            float y = screenHeight * t;
            float bottom = MathUtils.clamp((t - 0.55f) / 0.45f, 0f, 1f) * 0.24f;
            shapes.setColor(0.008f, 0.014f, 0.028f, bottom);
            shapes.rect(0, y, screenWidth, screenHeight / (float) bands + 1f);
        }
        shapes.end();

        batch.setProjectionMatrix(screenCamera.combined);
        batch.setColor(1f, 1f, 1f, 0.022f);
        batch.begin();
        float tile = 256f;
        for (float y = 0; y < screenHeight; y += tile) {
            for (float x = 0; x < screenWidth; x += tile) {
                batch.draw(noise, x, y, tile, tile);
            }
        }
        batch.end();
        batch.setColor(Color.WHITE);
    }

    private void drawSkyGradient() {
        Color top = skyTop();
        Color bottom = skyBottom();
        shapes.setProjectionMatrix(worldCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        int bands = 80;
        float left = -worldWidth * 0.5f;
        for (int i = 0; i < bands; i++) {
            float t = i / (float) (bands - 1);
            Color c = new Color(bottom).lerp(top, t);
            float gray = state.cloud * 0.21f + state.rain * 0.18f + state.storm * 0.18f;
            c.lerp(new Color(0.34f, 0.36f, 0.44f, 1f), gray);
            c.mul(1f - state.storm * 0.16f);
            shapes.setColor(c);
            shapes.rect(left, i * WORLD_HEIGHT / bands, worldWidth, WORLD_HEIGHT / bands + 1f);
        }
        shapes.end();
    }

    private void drawCelestial(boolean emission) {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        boolean sun = hour >= 5.2f && hour <= 20.0f;
        float progress = sun
                ? MathUtils.clamp((hour - 5.5f) / 14f, 0f, 1f)
                : MathUtils.clamp(hour < 6f ? (hour + 6f) / 12f : (hour - 18f) / 12f, 0f, 1f);
        float x = -worldWidth * 0.32f + worldWidth * progress * 0.70f + effectiveOffset * 12f;
        float y = sun ? 650f + MathUtils.sin(progress * MathUtils.PI) * 205f
                : 725f + MathUtils.sin(progress * MathUtils.PI) * 105f;
        float radius = sun ? 31f : 27f;
        float visibility = MathUtils.clamp(1f - state.cloud * 0.66f, 0.12f, 1f);

        if (emission) {
            float size = radius * (sun ? 11f : 8f);
            batch.setColor(sun
                    ? new Color(1f, 0.60f, 0.34f, 0.70f * visibility)
                    : new Color(0.78f, 0.84f, 1f, 0.46f * visibility));
            batch.draw(glow, x - size * 0.5f, y - size * 0.5f, size, size);
            batch.setColor(Color.WHITE);
            return;
        }

        Texture body = sun ? sunDisc : moonCrescent;
        batch.setColor(sun
                ? new Color(1f, 0.94f, 0.74f, visibility)
                : new Color(0.93f, 0.94f, 0.90f, visibility));
        batch.draw(body, x - radius, y - radius, radius * 2f, radius * 2f);
        batch.setColor(Color.WHITE);
    }

    private void drawLayer(Texture texture, float parallax, float driftPerSecond,
                           float alpha, Color tint, float phase, float yOffset, float heightScale) {
        if (alpha <= 0.001f) return;
        float layerHeight = WORLD_HEIGHT * heightScale;
        float offset = positiveModulo(
                scroll * parallax
                        + elapsed * driftPerSecond
                        + effectiveOffset * parallax * 150f
                        + phase,
                LAYER_WIDTH);
        float first = -LAYER_WIDTH * 1.5f - offset;
        batch.setColor(tint.r, tint.g, tint.b, MathUtils.clamp(alpha, 0f, 1f));
        int copies = Math.max(4, (int) Math.ceil(worldWidth / LAYER_WIDTH) + 4);
        for (int i = 0; i < copies; i++) {
            batch.draw(texture, first + i * LAYER_WIDTH, yOffset, LAYER_WIDTH, layerHeight);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawFireflies(boolean emission) {
        if (!state.showFireflies) return;
        float amount = state.nightFactor() * (1f - state.rain * 0.8f) * state.effectIntensity;
        if (preview) amount = Math.max(amount, 0.34f);
        if (amount < 0.03f) return;
        int count = 8 + (int) (amount * 34f);
        for (int i = 0; i < count; i++) {
            float x = -worldWidth * 0.5f + hash01(i * 41 + 3) * worldWidth;
            x += MathUtils.sin(elapsed * (0.32f + hash01(i * 13) * 0.42f) + i) * 24f;
            float y = 145f + hash01(i * 61 + 7) * 285f;
            y += MathUtils.cos(elapsed * (0.40f + hash01(i * 17) * 0.48f) + i) * 15f;
            if (interactionPulse > 0.01f && i < 12) {
                x = MathUtils.lerp(x, touchWorldX + MathUtils.sin(i * 2.1f) * (35f + i * 3f), interactionPulse * 0.68f);
                y = MathUtils.lerp(y, touchWorldY + MathUtils.cos(i * 1.7f) * (25f + i * 2f), interactionPulse * 0.42f);
            }
            float pulse = 0.55f + 0.45f * MathUtils.sin(elapsed * 1.7f + i * 1.31f);
            float size = emission ? 16f + pulse * 10f : 2.4f + pulse * 1.6f;
            batch.setColor(1f, 0.83f, 0.42f,
                    emission ? amount * 0.42f * pulse : amount * (0.48f + pulse * 0.42f));
            batch.draw(glow, x - size * 0.5f, y - size * 0.5f, size, size);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawRainAndSnow() {
        float wind = effectiveWind();
        shapes.setProjectionMatrix(worldCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (state.rain > 0.02f) {
            int drops = 45 + (int) (state.rain * 210f);
            shapes.setColor(0.62f, 0.70f, 0.82f, 0.18f + state.rain * 0.30f);
            for (int i = 0; i < drops; i++) {
                float phase = hash01(i * 97 + 17) * 1700f;
                float x = -worldWidth * 0.5f + positiveModulo(
                        hash01(i * 37) * worldWidth + elapsed * (70f + wind * 240f), worldWidth);
                float y = positiveModulo(phase - elapsed * (440f + state.rain * 520f), WORLD_HEIGHT + 90f);
                float slant = 4f + wind * 22f;
                shapes.rectLine(x, y, x + slant,
                        y - (13f + state.rain * 28f), 1.0f + state.rain * 0.8f);
            }
        }
        if (state.snow > 0.02f) {
            int flakes = 28 + (int) (state.snow * 120f);
            shapes.setColor(0.94f, 0.95f, 0.96f, 0.42f + state.snow * 0.34f);
            for (int i = 0; i < flakes; i++) {
                float x = -worldWidth * 0.5f + positiveModulo(
                        hash01(i * 67) * worldWidth
                                + MathUtils.sin(elapsed * 0.55f + i) * (20f + wind * 35f), worldWidth);
                float y = positiveModulo(hash01(i * 89) * 1200f
                        - elapsed * (42f + state.snow * 86f), WORLD_HEIGHT + 50f);
                shapes.circle(x, y, 1.2f + hash01(i * 13) * 2.5f, 8);
            }
        }
        shapes.end();
    }

    private void drawAtmosphereOverlays() {
        shapes.setProjectionMatrix(worldCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float left = -worldWidth * 0.5f;
        if (state.rain > 0.04f || state.fog > 0.04f) {
            shapes.setColor(0.29f, 0.31f, 0.41f,
                    state.rain * 0.075f + state.fog * 0.095f);
            shapes.rect(left, 0f, worldWidth, WORLD_HEIGHT);
        }
        if (state.storm > 0.15f) {
            float flashCycle = positiveModulo(elapsed, 10.7f);
            if (flashCycle > 10.42f) {
                float a = (1f - Math.abs(flashCycle - 10.55f) / 0.13f)
                        * state.storm * 0.34f;
                shapes.setColor(0.82f, 0.84f, 1f, Math.max(0f, a));
                shapes.rect(left, 0f, worldWidth, WORLD_HEIGHT);
            }
        }
        shapes.end();
    }

    private float effectiveWind() {
        return MathUtils.clamp(state.wind + interactionGust * 0.45f, 0f, 1f);
    }

    private Color paletteTint(int depth) {
        float dusk = state.duskFactor();
        float night = state.nightFactor();
        Color c;
        switch (depth) {
            case 0: c = new Color(0.94f, 0.89f, 0.97f, 1f); break;
            case 1: c = new Color(0.89f, 0.82f, 0.92f, 1f); break;
            case 2: c = new Color(0.85f, 0.77f, 0.88f, 1f); break;
            default: c = new Color(0.73f, 0.70f, 0.81f, 1f); break;
        }
        c.lerp(new Color(1f, 0.77f, 0.66f, 1f), dusk * (0.20f - depth * 0.027f));
        c.lerp(new Color(0.43f, 0.48f, 0.66f, 1f), night * 0.25f);
        return c;
    }

    private Color seasonForestTint(float brightness) {
        Color c;
        switch (state.season) {
            case SPRING: c = new Color(0.54f, 0.68f, 0.60f, 1f); break;
            case SUMMER: c = new Color(0.40f, 0.57f, 0.49f, 1f); break;
            case AUTUMN: c = new Color(0.66f, 0.47f, 0.41f, 1f); break;
            case WINTER:
            default: c = new Color(0.54f, 0.58f, 0.65f, 1f); break;
        }
        c.mul(brightness);
        return c;
    }

    private Color skyTop() {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        if (hour < 5f) return new Color(0.045f, 0.060f, 0.135f, 1f);
        if (hour < 8f) return blendColor(
                new Color(0.19f, 0.21f, 0.37f, 1f),
                new Color(0.47f, 0.48f, 0.64f, 1f), (hour - 5f) / 3f);
        if (hour < 17f) return new Color(0.39f, 0.60f, 0.72f, 1f);
        if (hour < 21f) return blendColor(
                new Color(0.37f, 0.50f, 0.63f, 1f),
                new Color(0.12f, 0.13f, 0.26f, 1f), (hour - 17f) / 4f);
        return new Color(0.042f, 0.052f, 0.125f, 1f);
    }

    private Color skyBottom() {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        if (hour < 5f) return new Color(0.15f, 0.13f, 0.24f, 1f);
        if (hour < 8f) return blendColor(
                new Color(0.39f, 0.30f, 0.44f, 1f),
                new Color(0.91f, 0.62f, 0.53f, 1f), (hour - 5f) / 3f);
        if (hour < 17f) return new Color(0.72f, 0.77f, 0.73f, 1f);
        if (hour < 21f) return blendColor(
                new Color(0.91f, 0.68f, 0.55f, 1f),
                new Color(0.30f, 0.21f, 0.37f, 1f), (hour - 17f) / 4f);
        return new Color(0.16f, 0.12f, 0.24f, 1f);
    }

    private Color blendColor(Color a, Color b, float t) {
        return new Color(a).lerp(b, MathUtils.clamp(t, 0f, 1f));
    }

    private Color tint(float r, float g, float b, float a) {
        return new Color(r, g, b, a);
    }

    private void rebuildBuffers() {
        disposeBuffers();
        try {
            sceneFbo = new FrameBuffer(Pixmap.Format.RGBA8888, renderWidth, renderHeight, false);
            emissionFbo = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
            blurA = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
            blurB = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
            sceneRegion = flipped(sceneFbo.getColorBufferTexture());
            emissionRegion = flipped(emissionFbo.getColorBufferTexture());
            blurARegion = flipped(blurA.getColorBufferTexture());
            blurBRegion = flipped(blurB.getColorBufferTexture());
        } catch (Throwable error) {
            // Low-memory fallback: rebuild at a much smaller resolution instead of a blank wallpaper.
            Gdx.app.error(TAG, "FBO allocation fallback", error);
            disposeBuffers();
            renderWidth = Math.max(320, screenWidth / 2);
            renderHeight = Math.max(320, screenHeight / 2);
            bloomWidth = Math.max(96, renderWidth / 5);
            bloomHeight = Math.max(96, renderHeight / 5);
            sceneFbo = new FrameBuffer(Pixmap.Format.RGBA8888, renderWidth, renderHeight, false);
            emissionFbo = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
            blurA = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
            blurB = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
            sceneRegion = flipped(sceneFbo.getColorBufferTexture());
            emissionRegion = flipped(emissionFbo.getColorBufferTexture());
            blurARegion = flipped(blurA.getColorBufferTexture());
            blurBRegion = flipped(blurB.getColorBufferTexture());
        }
    }

    private TextureRegion flipped(Texture texture) {
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        TextureRegion region = new TextureRegion(texture);
        region.flip(false, true);
        return region;
    }

    private Texture texture(String path) {
        try {
            Texture texture = new Texture(Gdx.files.internal(path), false);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } catch (Throwable error) {
            Gdx.app.error(TAG, "Missing visual asset: " + path, error);
            Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
            pixmap.setColor(0f, 0f, 0f, 0f);
            pixmap.fill();
            Texture fallback = new Texture(pixmap);
            pixmap.dispose();
            return fallback;
        }
    }

    private void disposeBuffers() {
        if (sceneFbo != null) sceneFbo.dispose();
        if (emissionFbo != null) emissionFbo.dispose();
        if (blurA != null) blurA.dispose();
        if (blurB != null) blurB.dispose();
        sceneFbo = emissionFbo = blurA = blurB = null;
    }

    @Override
    public void dispose() {
        disposeBuffers();
        batch.dispose();
        shapes.dispose();
        if (blurShader != null) blurShader.dispose();
        stars.dispose();
        cloudsFar.dispose();
        cloudsNear.dispose();
        mountainsFar.dispose();
        mountainsMid.dispose();
        mountainsHero.dispose();
        mountainsNear.dispose();
        snowCaps.dispose();
        fogValley.dispose();
        forestFar.dispose();
        forestMid.dispose();
        hillMid.dispose();
        hillFront.dispose();
        pineTall.dispose();
        pineMedium.dispose();
        pineSparse.dispose();
        pineDead.dispose();
        lantern.dispose();
        lanternEmission.dispose();
        campfire.dispose();
        campfireEmission.dispose();
        glow.dispose();
        sunDisc.dispose();
        moonCrescent.dispose();
        noise.dispose();
    }

    private static int clampFps(int value) {
        return Math.max(15, Math.min(60, value));
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

    private static float midTerrain(float worldX) {
        float local = positiveModulo(worldX, LAYER_WIDTH) / LAYER_WIDTH * MathUtils.PI2;
        return 205f + MathUtils.sin(local + 0.35f) * 34f
                + MathUtils.sin(local * 2.0f + 1.15f) * 18f;
    }

    private static float frontTerrain(float worldX) {
        float local = positiveModulo(worldX, LAYER_WIDTH) / LAYER_WIDTH * MathUtils.PI2;
        return 86f + MathUtils.sin(local + 1.05f) * 25f
                + MathUtils.sin(local * 2.0f + 0.25f) * 12f;
    }

    private static final String DEFAULT_VERTEX =
            "attribute vec4 a_position;\n" +
            "attribute vec4 a_color;\n" +
            "attribute vec2 a_texCoord0;\n" +
            "uniform mat4 u_projTrans;\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_texCoords;\n" +
            "void main(){\n" +
            " v_color = a_color;\n" +
            " v_color.a = v_color.a * (255.0/254.0);\n" +
            " v_texCoords = a_texCoord0;\n" +
            " gl_Position = u_projTrans * a_position;\n" +
            "}";

    private static final String BLUR_FRAGMENT =
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_texCoords;\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform vec2 u_direction;\n" +
            "void main(){\n" +
            " vec4 c = texture2D(u_texture, v_texCoords) * 0.227027;\n" +
            " c += texture2D(u_texture, v_texCoords + u_direction * 1.384615) * 0.316216;\n" +
            " c += texture2D(u_texture, v_texCoords - u_direction * 1.384615) * 0.316216;\n" +
            " c += texture2D(u_texture, v_texCoords + u_direction * 3.230769) * 0.070270;\n" +
            " c += texture2D(u_texture, v_texCoords - u_direction * 3.230769) * 0.070270;\n" +
            " gl_FragColor = c * v_color;\n" +
            "}";

    /** Semi-procedural objects anchored to the same analytic terrain as the hill layers. */
    private static final class SceneObjects {
        private static final float SEGMENT = 560f;

        void drawBack(SpriteBatch batch, SceneState state, float scroll, float worldWidth,
                      Texture pineMedium, Texture pineSparse, Texture pineDead,
                      float wind, float elapsed) {
            float layerScroll = scroll * 0.39f;
            int start = (int) Math.floor((layerScroll - worldWidth) / SEGMENT) - 3;
            int end = start + (int) Math.ceil(worldWidth * 2f / SEGMENT) + 7;
            batch.setColor(0.47f, 0.49f, 0.60f, 0.66f);
            for (int segment = start; segment <= end; segment++) {
                float origin = segment * SEGMENT - layerScroll;
                int count = 3 + (int) (hash01(segment * 83) * 3f);
                for (int i = 0; i < count; i++) {
                    float worldX = segment * SEGMENT + 55f + i * 105f
                            + hash01(segment * 131 + i * 19) * 70f;
                    float x = worldX - layerScroll;
                    float y = midTerrain(worldX) - 5f;
                    float height = 135f + hash01(segment * 47 + i * 11) * 125f;
                    Texture t = (i % 4 == 0) ? pineDead : (i % 2 == 0 ? pineSparse : pineMedium);
                    float width = height * t.getWidth() / (float) t.getHeight();
                    float sway = MathUtils.sin(elapsed * (0.42f + wind * 0.72f) + segment + i)
                            * wind * 1.2f;
                    batch.draw(t, x, y, width * 0.5f, 0f, width, height,
                            1f, 1f, sway, 0, 0, t.getWidth(), t.getHeight(), false, false);
                }
            }
            batch.setColor(Color.WHITE);
        }

        void drawFront(SpriteBatch batch, SceneState state, float scroll, float worldWidth,
                       Texture pineTall, Texture pineMedium, Texture pineSparse,
                       Texture lightTexture, Texture fireTexture, boolean emission,
                       float elapsed, float wind, float interactionPulse) {
            float layerScroll = scroll * 0.67f;
            int start = (int) Math.floor((layerScroll - worldWidth) / SEGMENT) - 3;
            int end = start + (int) Math.ceil(worldWidth * 2f / SEGMENT) + 7;
            for (int segment = start; segment <= end; segment++) {
                int biome = Math.abs(segment * 37) % 6;
                int count = biome == 1 ? 4 : (biome == 4 ? 1 : 2);
                if (!emission) {
                    for (int i = 0; i < count; i++) {
                        float worldX = segment * SEGMENT + 35f + i * 132f
                                + hash01(segment * 211 + i * 17) * 68f;
                        float x = worldX - layerScroll;
                        float y = frontTerrain(worldX) - 5f;
                        float height = 205f + hash01(segment * 97 + i * 23)
                                * (biome == 1 ? 220f : 145f);
                        Texture t = i % 3 == 0 ? pineTall : (i % 2 == 0 ? pineSparse : pineMedium);
                        float width = height * t.getWidth() / (float) t.getHeight();
                        float sway = MathUtils.sin(elapsed * (0.52f + wind * 1.15f) + segment + i)
                                * wind * 2.2f;
                        batch.draw(t, x, y, width * 0.5f, 0f, width, height,
                                1f, 1f, sway, 0, 0, t.getWidth(), t.getHeight(), false, false);
                    }
                }

                if (biome == 2 || biome == 5) {
                    float worldX = segment * SEGMENT + SEGMENT * 0.54f;
                    float x = worldX - layerScroll;
                    float y = frontTerrain(worldX) - 4f;
                    float h = 165f;
                    float w = h * lightTexture.getWidth() / (float) lightTexture.getHeight();
                    float flicker = 0.88f + MathUtils.sin(elapsed * 4.0f + segment) * 0.06f
                            + interactionPulse * 0.08f;
                    batch.setColor(1f, emission ? 0.76f : 1f, emission ? 0.52f : 1f,
                            emission ? MathUtils.clamp(flicker, 0f, 1f) : 1f);
                    batch.draw(lightTexture, x, y, w, h);
                    batch.setColor(Color.WHITE);
                }

                if (state.showCampfires && biome == 3) {
                    float worldX = segment * SEGMENT + SEGMENT * 0.62f;
                    float x = worldX - layerScroll;
                    float y = frontTerrain(worldX) - 2f;
                    float h = 67f;
                    float w = h * fireTexture.getWidth() / (float) fireTexture.getHeight();
                    float flicker = 0.80f + MathUtils.sin(elapsed * 5.3f + segment) * 0.10f
                            + interactionPulse * 0.10f;
                    batch.setColor(1f, 1f, 1f, emission ? MathUtils.clamp(flicker, 0f, 1f) : 1f);
                    batch.draw(fireTexture, x, y, w, h);
                    batch.setColor(Color.WHITE);
                }
            }
        }
    }
}
