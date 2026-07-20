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

import java.util.Calendar;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.render.SceneState;

/**
 * GPU renderer built around art-directed 2D layers.
 *
 * The camera uses virtual world units and never scales objects differently on X/Y,
 * so portrait and landscape crop the same world instead of stretching mountains.
 */
public final class LayeredSceneRenderer implements Disposable {
    private static final float WORLD_HEIGHT = 1000f;
    private static final float LAYER_WIDTH = 2000f;
    private static final float BLOOM_SCALE = 0.25f;

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
    private final Texture hillForeground;
    private final Texture pineTall;
    private final Texture pineMedium;
    private final Texture pineSparse;
    private final Texture pineDead;
    private final Texture lantern;
    private final Texture lanternEmission;
    private final Texture campfire;
    private final Texture campfireEmission;
    private final Texture glow;
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
    private float lanternPulse;
    private boolean preview;
    private int targetFps;

    public LayeredSceneRenderer(SharedPreferences preferences) {
        this.preferences = preferences;
        state = SceneState.fromPreferences(preferences);
        target = state.copy();
        targetFps = Math.max(15, Math.min(60, state.targetFps));

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
        hillForeground = texture("aether/layers/hill_foreground.png");
        pineTall = texture("aether/objects/pine_tall.png");
        pineMedium = texture("aether/objects/pine_medium.png");
        pineSparse = texture("aether/objects/pine_sparse.png");
        pineDead = texture("aether/objects/pine_dead.png");
        lantern = texture("aether/objects/lantern.png");
        lanternEmission = texture("aether/objects/lantern_emission.png");
        campfire = texture("aether/objects/campfire.png");
        campfireEmission = texture("aether/objects/campfire_emission.png");
        glow = texture("aether/objects/glow.png");
        noise = texture("aether/objects/noise_soft.png");

        ShaderProgram.pedantic = false;
        blurShader = new ShaderProgram(DEFAULT_VERTEX, BLUR_FRAGMENT);
        if (!blurShader.isCompiled()) {
            Gdx.app.error("AetherScape", "Bloom shader error: " + blurShader.getLog());
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
        lanternPulse = 1f;
    }

    public void reloadPreferences() {
        target = SceneState.fromPreferences(preferences);
        targetFps = Math.max(15, Math.min(60, target.targetFps));
    }

    public void resize(int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        boolean portrait = screenHeight >= screenWidth;

        // Same scale in both axes. Landscape intentionally sees more world horizontally.
        float viewHeight = portrait ? WORLD_HEIGHT : 760f;
        worldWidth = viewHeight * screenWidth / (float) screenHeight;
        worldCamera.setToOrtho(false, worldWidth, viewHeight);
        worldCamera.position.set(0f, portrait ? 500f : 455f, 0f);
        worldCamera.update();

        float renderScale = preferences.getBoolean(AppPreferences.BATTERY_SAVER, false) ? 0.67f : 0.86f;
        if (screenWidth <= 720) renderScale = Math.max(renderScale, 0.92f);
        renderWidth = Math.max(320, Math.round(screenWidth * renderScale));
        renderHeight = Math.max(320, Math.round(screenHeight * renderScale));
        bloomWidth = Math.max(128, Math.round(renderWidth * BLOOM_SCALE));
        bloomHeight = Math.max(128, Math.round(renderHeight * BLOOM_SCALE));

        rebuildBuffers();
    }

    public void render() {
        float dt = Math.min(0.05f, Math.max(0f, Gdx.graphics.getDeltaTime()));
        elapsed += dt;
        target = SceneState.fromPreferences(preferences);
        float smooth = 1f - (float) Math.exp(-dt * 0.75f);
        state.smoothToward(target, smooth);
        targetFps = Math.max(15, Math.min(60, state.targetFps));
        lanternPulse = Math.max(0f, lanternPulse - dt * 0.8f);

        float speed = 4.5f + state.scrollSpeed * 14f;
        scroll += dt * speed * (0.60f + state.motionIntensity * 0.55f);
        if (scroll > 100000f) scroll -= 100000f;

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
        drawLayer(stars, 0.003f, state.showStars ? 0.58f * state.nightFactor() * (1f - state.cloud * 0.72f) : 0f, Color.WHITE);
        drawLayer(cloudsFar, 0.020f + state.wind * 0.025f, 0.32f + state.cloud * 0.50f,
                tint(0.85f, 0.79f, 0.87f, 1f));
        drawCelestial(false);
        drawLayer(mountainsFar, 0.035f, 0.70f, paletteTint(0));
        drawLayer(mountainsMid, 0.075f, 0.86f, paletteTint(1));
        drawLayer(mountainsHero, 0.115f, 0.96f, paletteTint(2));
        float snowAlpha = state.snowCaps && (state.season == SceneState.Season.WINTER || state.temperatureC < 4f || state.snow > 0.16f)
                ? MathUtils.clamp(0.38f + state.snow * 0.60f + (state.temperatureC < 4f ? 0.18f : 0f), 0f, 0.92f)
                : 0f;
        drawLayer(snowCaps, 0.115f, snowAlpha, new Color(0.96f, 0.94f, 0.96f, 1f));
        drawLayer(fogValley, 0.055f, 0.18f + state.fog * 0.70f + state.rain * 0.10f,
                tint(0.88f, 0.82f, 0.90f, 1f));
        drawLayer(mountainsNear, 0.175f, 1f, paletteTint(3));
        drawLayer(forestFar, 0.205f, 0.75f, seasonForestTint(0.78f));
        drawLayer(cloudsNear, 0.065f + state.wind * 0.060f, state.cloud * 0.72f,
                tint(0.72f, 0.72f, 0.80f, 1f));
        drawLayer(forestMid, 0.305f, 0.92f, seasonForestTint(0.90f));
        batch.end();

        drawWeatherBehindForeground();

        batch.begin();
        objects.drawBack(batch, state, scroll, worldWidth, pineMedium, pineSparse, pineDead);
        drawLayer(hillForeground, 0.50f, 1f, Color.WHITE);
        objects.drawFront(batch, state, scroll, worldWidth, pineTall, pineMedium, pineSparse,
                lantern, campfire, false, elapsed);
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
        objects.drawFront(batch, state, scroll, worldWidth, pineTall, pineMedium, pineSparse,
                lanternEmission, campfireEmission, true, elapsed);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        emissionFbo.end();
    }

    private void blurEmission() {
        if (blurShader == null) return;
        blurA.begin();
        Gdx.gl.glViewport(0, 0, bloomWidth, bloomHeight);
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        screenCamera.setToOrtho(false, bloomWidth, bloomHeight);
        batch.setProjectionMatrix(screenCamera.combined);
        batch.setShader(blurShader);
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
        batch.setShader(null);
        blurB.end();
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
            batch.setColor(1f, 0.90f, 0.82f, 0.62f + lanternPulse * 0.25f);
            batch.begin();
            batch.draw(blurShader == null ? emissionRegion : blurBRegion, 0, 0, screenWidth, screenHeight);
            batch.end();
            batch.setColor(Color.WHITE);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }

        // Subtle texture and vignette hide flat gradients and protect icon readability.
        shapes.setProjectionMatrix(screenCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            float t = i / (float) bands;
            float y = screenHeight * t;
            float alpha = MathUtils.clamp((t - 0.50f) / 0.50f, 0f, 1f) * 0.23f;
            shapes.setColor(0.01f, 0.018f, 0.035f, alpha);
            shapes.rect(0, y, screenWidth, screenHeight / (float) bands + 1f);
        }
        shapes.end();

        batch.setProjectionMatrix(screenCamera.combined);
        batch.setColor(1f, 1f, 1f, 0.028f);
        batch.begin();
        float tile = 256f;
        for (float y = 0; y < screenHeight; y += tile) {
            for (float x = 0; x < screenWidth; x += tile) batch.draw(noise, x, y, tile, tile);
        }
        batch.end();
        batch.setColor(Color.WHITE);
    }

    private void drawSkyGradient() {
        Color top = skyTop();
        Color bottom = skyBottom();
        shapes.setProjectionMatrix(worldCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        int bands = 72;
        float left = -worldWidth * 0.5f;
        float height = worldCamera.viewportHeight;
        for (int i = 0; i < bands; i++) {
            float t = i / (float) (bands - 1);
            Color c = new Color(bottom).lerp(top, t);
            float weatherGray = state.cloud * 0.24f + state.rain * 0.16f + state.storm * 0.16f;
            c.lerp(new Color(0.35f, 0.37f, 0.45f, 1f), weatherGray);
            c.mul(1f - state.storm * 0.18f);
            shapes.setColor(c);
            shapes.rect(left, i * height / bands, worldWidth, height / bands + 1f);
        }
        shapes.end();
    }

    private void drawCelestial(boolean emission) {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        boolean sun = hour >= 5.2f && hour <= 20.0f;
        float progress = sun ? MathUtils.clamp((hour - 5.5f) / 14f, 0f, 1f)
                : MathUtils.clamp(hour < 6f ? (hour + 6f) / 12f : (hour - 18f) / 12f, 0f, 1f);
        float x = -worldWidth * 0.34f + worldWidth * progress * 0.72f + launcherOffset * 8f;
        float y = sun ? 650f + MathUtils.sin(progress * MathUtils.PI) * 210f
                : 720f + MathUtils.sin(progress * MathUtils.PI) * 110f;
        float radius = sun ? 32f : 24f;
        float visibility = 1f - state.cloud * 0.62f;
        if (emission) {
            float size = radius * (sun ? 10f : 7f);
            batch.setColor(sun ? new Color(1f, 0.64f, 0.38f, 0.75f * visibility)
                    : new Color(0.82f, 0.88f, 1f, 0.45f * visibility));
            batch.draw(glow, x - size * 0.5f, y - size * 0.5f, size, size);
            batch.setColor(Color.WHITE);
            return;
        }

        batch.setColor(sun ? new Color(1f, 0.93f, 0.72f, visibility)
                : new Color(0.91f, 0.93f, 0.86f, visibility));
        batch.draw(glow, x - radius, y - radius, radius * 2f, radius * 2f);
        batch.setColor(Color.WHITE);
    }

    private void drawLayer(Texture texture, float speed, float alpha, Color tint) {
        if (alpha <= 0.001f) return;
        float offset = positiveModulo(scroll * speed + launcherOffset * speed * 90f, LAYER_WIDTH);
        float first = -LAYER_WIDTH * 1.5f - offset;
        batch.setColor(tint.r, tint.g, tint.b, MathUtils.clamp(alpha, 0f, 1f));
        for (int i = 0; i < 4; i++) {
            batch.draw(texture, first + i * LAYER_WIDTH, 0f, LAYER_WIDTH, WORLD_HEIGHT);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawWeatherBehindForeground() {
        if (state.fog < 0.04f) return;
        batch.setProjectionMatrix(worldCamera.combined);
        batch.setColor(0.76f, 0.72f, 0.82f, state.fog * 0.16f);
        batch.begin();
        drawLayer(fogValley, 0.10f, state.fog * 0.55f, batch.getColor());
        batch.end();
        batch.setColor(Color.WHITE);
    }

    private void drawRainAndSnow() {
        shapes.setProjectionMatrix(worldCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (state.rain > 0.02f) {
            int drops = 35 + (int) (state.rain * 180f);
            shapes.setColor(0.64f, 0.72f, 0.82f, 0.18f + state.rain * 0.26f);
            for (int i = 0; i < drops; i++) {
                float phase = hash01(i * 97 + 17) * 1600f;
                float x = -worldWidth * 0.5f + positiveModulo(hash01(i * 37) * worldWidth
                        + elapsed * (90f + state.wind * 220f), worldWidth);
                float y = positiveModulo(phase - elapsed * (420f + state.rain * 480f), worldCamera.viewportHeight + 80f);
                float slant = 4f + state.wind * 18f;
                shapes.rectLine(x, y, x + slant, y - (12f + state.rain * 26f), 1.1f + state.rain * 0.7f);
            }
        }
        if (state.snow > 0.02f) {
            int flakes = 24 + (int) (state.snow * 100f);
            shapes.setColor(0.92f, 0.93f, 0.94f, 0.42f + state.snow * 0.32f);
            for (int i = 0; i < flakes; i++) {
                float x = -worldWidth * 0.5f + positiveModulo(hash01(i * 67) * worldWidth
                        + MathUtils.sin(elapsed * 0.6f + i) * 20f, worldWidth);
                float y = positiveModulo(hash01(i * 89) * 1200f - elapsed * (40f + state.snow * 80f),
                        worldCamera.viewportHeight + 50f);
                shapes.circle(x, y, 1.2f + hash01(i * 13) * 2.4f, 8);
            }
        }
        shapes.end();
    }

    private void drawAtmosphereOverlays() {
        shapes.setProjectionMatrix(worldCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float left = -worldWidth * 0.5f;
        if (state.rain > 0.04f || state.fog > 0.04f) {
            shapes.setColor(0.30f, 0.32f, 0.42f, state.rain * 0.08f + state.fog * 0.10f);
            shapes.rect(left, 0f, worldWidth, worldCamera.viewportHeight);
        }
        if (state.storm > 0.15f) {
            float flashCycle = positiveModulo(elapsed, 11f);
            if (flashCycle > 10.75f) {
                float a = (1f - Math.abs(flashCycle - 10.87f) / 0.12f) * state.storm * 0.34f;
                shapes.setColor(0.82f, 0.84f, 1f, Math.max(0f, a));
                shapes.rect(left, 0f, worldWidth, worldCamera.viewportHeight);
            }
        }
        shapes.end();
    }

    private Color paletteTint(int depth) {
        float dusk = state.duskFactor();
        float night = state.nightFactor();
        Color c;
        switch (depth) {
            case 0: c = new Color(0.93f, 0.88f, 0.96f, 1f); break;
            case 1: c = new Color(0.89f, 0.82f, 0.91f, 1f); break;
            case 2: c = new Color(0.86f, 0.78f, 0.88f, 1f); break;
            default: c = new Color(0.76f, 0.72f, 0.82f, 1f); break;
        }
        c.lerp(new Color(1f, 0.78f, 0.68f, 1f), dusk * (0.18f - depth * 0.025f));
        c.lerp(new Color(0.45f, 0.50f, 0.67f, 1f), night * 0.24f);
        return c;
    }

    private Color seasonForestTint(float brightness) {
        Color c;
        switch (state.season) {
            case SPRING: c = new Color(0.55f, 0.68f, 0.61f, 1f); break;
            case SUMMER: c = new Color(0.42f, 0.58f, 0.50f, 1f); break;
            case AUTUMN: c = new Color(0.66f, 0.48f, 0.42f, 1f); break;
            case WINTER:
            default: c = new Color(0.55f, 0.59f, 0.66f, 1f); break;
        }
        c.mul(brightness);
        return c;
    }

    private Color skyTop() {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        if (hour < 5f) return new Color(0.045f, 0.065f, 0.14f, 1f);
        if (hour < 8f) return blendColor(new Color(0.20f, 0.22f, 0.38f, 1f), new Color(0.47f, 0.48f, 0.64f, 1f), (hour - 5f) / 3f);
        if (hour < 17f) return new Color(0.39f, 0.61f, 0.73f, 1f);
        if (hour < 21f) return blendColor(new Color(0.38f, 0.51f, 0.64f, 1f), new Color(0.13f, 0.14f, 0.27f, 1f), (hour - 17f) / 4f);
        return new Color(0.045f, 0.055f, 0.13f, 1f);
    }

    private Color skyBottom() {
        float hour = ((state.hour % 24f) + 24f) % 24f;
        if (hour < 5f) return new Color(0.16f, 0.14f, 0.25f, 1f);
        if (hour < 8f) return blendColor(new Color(0.40f, 0.31f, 0.45f, 1f), new Color(0.91f, 0.62f, 0.53f, 1f), (hour - 5f) / 3f);
        if (hour < 17f) return new Color(0.73f, 0.78f, 0.74f, 1f);
        if (hour < 21f) return blendColor(new Color(0.91f, 0.69f, 0.56f, 1f), new Color(0.31f, 0.22f, 0.38f, 1f), (hour - 17f) / 4f);
        return new Color(0.17f, 0.13f, 0.25f, 1f);
    }

    private Color blendColor(Color a, Color b, float t) {
        return new Color(a).lerp(b, MathUtils.clamp(t, 0f, 1f));
    }

    private Color tint(float r, float g, float b, float a) {
        return new Color(r, g, b, a);
    }

    private void rebuildBuffers() {
        disposeBuffers();
        sceneFbo = new FrameBuffer(Pixmap.Format.RGBA8888, renderWidth, renderHeight, false);
        emissionFbo = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
        blurA = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
        blurB = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
        sceneRegion = flipped(sceneFbo.getColorBufferTexture());
        emissionRegion = flipped(emissionFbo.getColorBufferTexture());
        blurARegion = flipped(blurA.getColorBufferTexture());
        blurBRegion = flipped(blurB.getColorBufferTexture());
    }

    private TextureRegion flipped(Texture texture) {
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        TextureRegion region = new TextureRegion(texture);
        region.flip(false, true);
        return region;
    }

    private Texture texture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path), false);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
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
        stars.dispose(); cloudsFar.dispose(); cloudsNear.dispose(); mountainsFar.dispose();
        mountainsMid.dispose(); mountainsHero.dispose(); mountainsNear.dispose(); snowCaps.dispose(); fogValley.dispose();
        forestFar.dispose(); forestMid.dispose(); hillForeground.dispose(); pineTall.dispose();
        pineMedium.dispose(); pineSparse.dispose(); pineDead.dispose(); lantern.dispose();
        lanternEmission.dispose(); campfire.dispose(); campfireEmission.dispose(); glow.dispose(); noise.dispose();
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
            "#ifdef GL_ES\\n" +
            "precision mediump float;\\n" +
            "#endif\\n" +
            "varying vec4 v_color;\\n" +
            "varying vec2 v_texCoords;\\n" +
            "uniform sampler2D u_texture;\\n" +
            "uniform vec2 u_direction;\\n" +
            "void main(){\\n" +
            " vec4 c = texture2D(u_texture, v_texCoords) * 0.227027;\\n" +
            " c += texture2D(u_texture, v_texCoords + u_direction * 1.384615) * 0.316216;\\n" +
            " c += texture2D(u_texture, v_texCoords - u_direction * 1.384615) * 0.316216;\\n" +
            " c += texture2D(u_texture, v_texCoords + u_direction * 3.230769) * 0.070270;\\n" +
            " c += texture2D(u_texture, v_texCoords - u_direction * 3.230769) * 0.070270;\\n" +
            " gl_FragColor = c * v_color;\\n" +
            "}";

    /** Recycled semi-procedural scene objects, never stamped in screen coordinates. */
    private static final class SceneObjects {
        private static final float SEGMENT = 620f;

        void drawBack(SpriteBatch batch, SceneState state, float scroll, float worldWidth,
                      Texture pineMedium, Texture pineSparse, Texture pineDead) {
            float layerScroll = scroll * 0.39f;
            int start = (int) Math.floor((layerScroll - worldWidth) / SEGMENT) - 2;
            int end = start + (int) Math.ceil(worldWidth * 2f / SEGMENT) + 6;
            Color tint = new Color(0.48f, 0.49f, 0.60f, 0.68f);
            batch.setColor(tint);
            for (int segment = start; segment <= end; segment++) {
                float origin = segment * SEGMENT - layerScroll;
                int count = 2 + (int) (hash01(segment * 83) * 3f);
                for (int i = 0; i < count; i++) {
                    float x = origin + 70f + i * 130f + hash01(segment * 131 + i * 19) * 85f;
                    float y = 140f + hash01(segment * 73 + i) * 55f;
                    float height = 150f + hash01(segment * 47 + i * 11) * 115f;
                    Texture t = (i % 3 == 0) ? pineDead : (i % 2 == 0 ? pineSparse : pineMedium);
                    float width = height * t.getWidth() / (float) t.getHeight();
                    batch.draw(t, x, y, width, height);
                }
            }
            batch.setColor(Color.WHITE);
        }

        void drawFront(SpriteBatch batch, SceneState state, float scroll, float worldWidth,
                       Texture pineTall, Texture pineMedium, Texture pineSparse,
                       Texture lightTexture, Texture fireTexture, boolean emission, float elapsed) {
            float layerScroll = scroll * 0.72f;
            int start = (int) Math.floor((layerScroll - worldWidth) / SEGMENT) - 2;
            int end = start + (int) Math.ceil(worldWidth * 2f / SEGMENT) + 6;
            for (int segment = start; segment <= end; segment++) {
                float origin = segment * SEGMENT - layerScroll;
                int biome = Math.abs(segment * 37) % 5;

                if (!emission) {
                    int count = biome == 1 ? 4 : 2;
                    for (int i = 0; i < count; i++) {
                        float x = origin + 60f + i * 150f + hash01(segment * 211 + i * 17) * 80f;
                        float y = 80f + hash01(segment * 53 + i) * 42f;
                        float height = 240f + hash01(segment * 97 + i * 23) * (biome == 1 ? 250f : 170f);
                        Texture t = i % 3 == 0 ? pineTall : (i % 2 == 0 ? pineSparse : pineMedium);
                        float width = height * t.getWidth() / (float) t.getHeight();
                        float sway = MathUtils.sin(elapsed * (0.5f + state.wind) + segment + i) * state.wind * 1.8f;
                        batch.draw(t, x, y, width * 0.5f, 0f, width, height, 1f, 1f, sway,
                                0, 0, t.getWidth(), t.getHeight(), false, false);
                    }
                }

                if (biome == 2 || biome == 4) {
                    float x = origin + SEGMENT * 0.53f;
                    float y = 95f;
                    float h = 205f;
                    float w = h * lightTexture.getWidth() / (float) lightTexture.getHeight();
                    float flicker = 0.87f + MathUtils.sin(elapsed * 4.2f + segment) * 0.07f;
                    batch.setColor(1f, emission ? 0.76f : 1f, emission ? 0.52f : 1f,
                            emission ? flicker : 1f);
                    batch.draw(lightTexture, x, y, w, h);
                    batch.setColor(Color.WHITE);
                }

                if (state.showCampfires && biome == 3) {
                    float x = origin + SEGMENT * 0.61f;
                    float y = 105f;
                    float h = 78f;
                    float w = h * fireTexture.getWidth() / (float) fireTexture.getHeight();
                    batch.setColor(1f, 1f, 1f, emission ? 0.82f : 1f);
                    batch.draw(fireTexture, x, y, w, h);
                    batch.setColor(Color.WHITE);
                }
            }
        }
    }
}
