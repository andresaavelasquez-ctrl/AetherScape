package dev.andres.aetherscape.gdx;

import android.content.Context;
import android.content.SharedPreferences;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidWallpaperListener;

import dev.andres.aetherscape.prefs.AppPreferences;

/** libGDX lifecycle bridge used by the wallpaper service. */
public final class AetherGdxApplication extends ApplicationAdapter
        implements AndroidWallpaperListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private final Context context;
    private SharedPreferences preferences;
    private LayeredSceneRenderer renderer;
    private float launcherOffset;
    private boolean preview;

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
        Gdx.graphics.setForegroundFPS(renderer.targetFps());
    }

    @Override
    public void resize(int width, int height) {
        if (renderer != null) renderer.resize(width, height);
    }

    @Override
    public void render() {
        if (renderer != null) renderer.render();
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
    }

    @Override
    public void previewStateChange(boolean isPreview) {
        preview = isPreview;
        if (renderer != null) renderer.setPreview(isPreview);
    }

    @Override
    public void iconDropped(int x, int y) {
        if (renderer != null) renderer.pulseLanterns();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (renderer != null) {
            renderer.reloadPreferences();
            Gdx.graphics.setForegroundFPS(renderer.targetFps());
        }
    }
}
