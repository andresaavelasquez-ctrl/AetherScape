package dev.andres.aetherscape.gdx;

import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidLiveWallpaperService;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.weather.WeatherClient;

/** GPU-backed live wallpaper entry point. */
public final class AetherGdxWallpaperService extends AndroidLiveWallpaperService {
    @Override
    public void onCreateApplication() {
        AppPreferences.ensureDefaults(this);
        WeatherClient.refreshIfNeeded(this);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.r = 8;
        config.g = 8;
        config.b = 8;
        config.a = 8;
        config.depth = 0;
        config.stencil = 0;
        config.numSamples = 0;
        config.useAccelerometer = false;
        config.useGyroscope = false;
        config.useCompass = false;
        config.useRotationVectorSensor = false;
        config.useWakelock = false;
        config.disableAudio = true;
        config.getTouchEventsForLiveWallpaper = false;
        config.useImmersiveMode = false;
        config.useGL30 = false;
        config.renderUnderCutout = true;

        initialize(new AetherGdxApplication(getApplicationContext()), config);
    }
}
