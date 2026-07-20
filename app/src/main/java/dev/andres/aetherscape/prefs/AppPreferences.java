package dev.andres.aetherscape.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/** Centralized preference keys and typed helpers for the beta. */
public final class AppPreferences {
    public static final String FILE = "aetherscape_settings";

    public static final String LIVE_TIME = "live_time";
    public static final String MANUAL_HOUR = "manual_hour";
    public static final String DYNAMIC_WEATHER = "dynamic_weather";
    public static final String LIVE_LOCATION = "live_location";
    public static final String API_KEY = "google_weather_api_key";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String WEATHER_UPDATE_MIN = "weather_update_min";
    public static final String WEATHER_OVERRIDE = "weather_override";
    public static final String FORECAST_BLEND = "forecast_blend";

    public static final String CLOUD_BIAS = "cloud_bias";
    public static final String RAIN_BIAS = "rain_bias";
    public static final String WIND_BIAS = "wind_bias";
    public static final String FOG_BIAS = "fog_bias";

    public static final String SEASON_MODE = "season_mode";
    public static final String SNOW_CAPS = "snow_caps";
    public static final String TREE_DENSITY = "tree_density";
    public static final String SCENE_VARIETY = "scene_variety";
    public static final String SCROLL_SPEED = "scroll_speed";

    public static final String SHOW_STARS = "show_stars";
    public static final String SHOW_GLOW = "show_glow";
    public static final String SHOW_FIREFLIES = "show_fireflies";
    public static final String SHOW_CAMPFIRES = "show_campfires";
    public static final String SHOW_LIGHTNING = "show_lightning";
    public static final String PARALLAX = "parallax";
    public static final String EFFECT_INTENSITY = "effect_intensity";
    public static final String MOTION_INTENSITY = "motion_intensity";

    public static final String TARGET_FPS = "target_fps";
    public static final String BATTERY_SAVER = "battery_saver";

    public static final String WEATHER_CONDITION = "weather_condition";
    public static final String WEATHER_DESCRIPTION = "weather_description";
    public static final String WEATHER_TEMP_C = "weather_temp_c";
    public static final String WEATHER_CLOUD = "weather_cloud";
    public static final String WEATHER_RAIN = "weather_rain";
    public static final String WEATHER_WIND = "weather_wind";
    public static final String WEATHER_FOG = "weather_fog";
    public static final String WEATHER_THUNDER = "weather_thunder";
    public static final String WEATHER_UPCOMING_RAIN = "weather_upcoming_rain";
    public static final String WEATHER_UPCOMING_CLOUD = "weather_upcoming_cloud";
    public static final String WEATHER_UPCOMING_WIND = "weather_upcoming_wind";
    public static final String WEATHER_UPCOMING_THUNDER = "weather_upcoming_thunder";
    public static final String WEATHER_UPDATED_AT = "weather_updated_at";
    public static final String WEATHER_ERROR = "weather_error";

    private AppPreferences() {}

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(Context context) {
        SharedPreferences p = get(context);
        if (p.getBoolean("defaults_written", false)) return;
        p.edit()
                .putBoolean("defaults_written", true)
                .putBoolean(LIVE_TIME, true)
                .putInt(MANUAL_HOUR, 18)
                .putBoolean(DYNAMIC_WEATHER, true)
                .putBoolean(LIVE_LOCATION, true)
                .putInt(WEATHER_UPDATE_MIN, 30)
                .putString(WEATHER_OVERRIDE, "AUTO")
                .putBoolean(FORECAST_BLEND, true)
                .putInt(CLOUD_BIAS, 0)
                .putInt(RAIN_BIAS, 0)
                .putInt(WIND_BIAS, 0)
                .putInt(FOG_BIAS, 0)
                .putString(SEASON_MODE, "AUTO")
                .putBoolean(SNOW_CAPS, true)
                .putInt(TREE_DENSITY, 62)
                .putInt(SCENE_VARIETY, 72)
                .putInt(SCROLL_SPEED, 32)
                .putBoolean(SHOW_STARS, true)
                .putBoolean(SHOW_GLOW, true)
                .putBoolean(SHOW_FIREFLIES, true)
                .putBoolean(SHOW_CAMPFIRES, true)
                .putBoolean(SHOW_LIGHTNING, true)
                .putBoolean(PARALLAX, true)
                .putInt(EFFECT_INTENSITY, 70)
                .putInt(MOTION_INTENSITY, 55)
                .putInt(TARGET_FPS, 30)
                .putBoolean(BATTERY_SAVER, false)
                .putString(WEATHER_CONDITION, "CLEAR")
                .putString(WEATHER_DESCRIPTION, "Clima de demostración")
                .putFloat(WEATHER_TEMP_C, 18f)
                .putFloat(WEATHER_CLOUD, 0.15f)
                .putFloat(WEATHER_RAIN, 0f)
                .putFloat(WEATHER_WIND, 0.15f)
                .putFloat(WEATHER_FOG, 0.08f)
                .putFloat(WEATHER_THUNDER, 0f)
                .putFloat(WEATHER_UPCOMING_RAIN, 0f)
                .putFloat(WEATHER_UPCOMING_CLOUD, 0.15f)
                .putFloat(WEATHER_UPCOMING_WIND, 0.15f)
                .putFloat(WEATHER_UPCOMING_THUNDER, 0f)
                .apply();
    }

    public static double latitude(SharedPreferences p) {
        return parseDouble(p.getString(LATITUDE, "-27.1004"), -27.1004);
    }

    public static double longitude(SharedPreferences p) {
        return parseDouble(p.getString(LONGITUDE, "-52.6152"), -52.6152);
    }

    public static double parseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
