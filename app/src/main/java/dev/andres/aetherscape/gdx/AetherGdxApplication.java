package dev.andres.aetherscape.gdx;

import android.content.Context;
import android.content.SharedPreferences;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.android.AndroidWallpaperListener;

import dev.andres.aetherscape.prefs.AppPreferences;

/** libGDX lifecycle bridge used by the live wallpaper service. */
public final class AetherGdxApplication extends ApplicationAdapter
        implements AndroidWallpaperListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private final Context context;
    private SharedPreferences preferences;
    private LayeredSceneRenderer renderer;
    private float launcherOffset;
    private boolean preview;
    private int appliedFps = -1;

    public AetherGdxApplication(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void create() {
        AppPreferences.ensureDefaults(context);
        preferences = AppPreferences.get(context);
        preferences.registerOnSharedPreferenceChangeListener(this);
        renderer = new LayeredSceneRenderer(preferences);
        renderer.setLauncherOffset(launcherOffset);
        renderer.setPreview(preview);

        // Force real animation. Some launchers restore the wallpaper with on-demand
        // rendering after preview; continuous rendering prevents the scene freezing.
        Gdx.graphics.setContinuousRendering(true);
        applyFps();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (renderer == null) return false;
                float nx = screenX / (float) Math.max(1, Gdx.graphics.getWidth());
                float ny = screenY / (float) Math.max(1, Gdx.graphics.getHeight());
                renderer.touch(nx, ny);
                Gdx.graphics.requestRendering();
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                return touchDown(screenX, screenY, pointer, 0);
            }
        });
    }

    @Override
    public void resize(int width, int height) {
        if (renderer != null) renderer.resize(width, height);
        Gdx.graphics.requestRendering();
    }

    @Override
    public void render() {
        if (renderer != null) {
            renderer.render();
            applyFps();
        }
    }

    @Override
    public void resume() {
        Gdx.graphics.setContinuousRendering(true);
        Gdx.graphics.requestRendering();
    }

    @Override
    public void dispose() {
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
        if (renderer != null) renderer.dispose();
    }

    @Override
    public void offsetChange(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep,
                             int xPixelOffset, int yPixelOffset) {
        launcherOffset = (xOffset - 0.5f) * 2f;
        if (renderer != null) renderer.setLauncherOffset(launcherOffset);
        if (Gdx.graphics != null) Gdx.graphics.requestRendering();
    }

    @Override
    public void previewStateChange(boolean isPreview) {
        preview = isPreview;
        if (renderer != null) renderer.setPreview(isPreview);
        if (Gdx.graphics != null) Gdx.graphics.requestRendering();
    }

    @Override
    public void iconDropped(int x, int y) {
        if (renderer != null) renderer.pulseLanterns();
        if (Gdx.graphics != null) Gdx.graphics.requestRendering();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (renderer != null) renderer.reloadPreferences();
        appliedFps = -1;
        if (Gdx.graphics != null) {
            Gdx.graphics.setContinuousRendering(true);
            Gdx.graphics.requestRendering();
        }
    }

    private void applyFps() {
        if (renderer == null) return;
        int fps = renderer.targetFps();
        if (fps != appliedFps) {
            Gdx.graphics.setForegroundFPS(fps);
            appliedFps = fps;
        }
    }
}
