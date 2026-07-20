package dev.andres.aetherscape.weather;

import android.content.SharedPreferences;

import dev.andres.aetherscape.prefs.AppPreferences;

/** Immutable weather values normalized for the visual engine. */
public final class WeatherSnapshot {
    public final String condition;
    public final String description;
    public final float temperatureC;
    public final float cloud;
    public final float rain;
    public final float wind;
    public final float fog;
    public final float thunder;
    public final float upcomingRain;
    public final float upcomingCloud;
    public final float upcomingWind;
    public final float upcomingThunder;
    public final long updatedAt;

    public WeatherSnapshot(
            String condition,
            String description,
            float temperatureC,
            float cloud,
            float rain,
            float wind,
            float fog,
            float thunder,
            float upcomingRain,
            float upcomingCloud,
            float upcomingWind,
            float upcomingThunder,
            long updatedAt
    ) {
        this.condition = condition == null ? "CLEAR" : condition;
        this.description = description == null ? "" : description;
        this.temperatureC = temperatureC;
        this.cloud = clamp01(cloud);
        this.rain = clamp01(rain);
        this.wind = clamp01(wind);
        this.fog = clamp01(fog);
        this.thunder = clamp01(thunder);
        this.upcomingRain = clamp01(upcomingRain);
        this.upcomingCloud = clamp01(upcomingCloud);
        this.upcomingWind = clamp01(upcomingWind);
        this.upcomingThunder = clamp01(upcomingThunder);
        this.updatedAt = updatedAt;
    }

    public static WeatherSnapshot fromPreferences(SharedPreferences p) {
        return new WeatherSnapshot(
                p.getString(AppPreferences.WEATHER_CONDITION, "CLEAR"),
                p.getString(AppPreferences.WEATHER_DESCRIPTION, "Clima de demostración"),
                p.getFloat(AppPreferences.WEATHER_TEMP_C, 18f),
                p.getFloat(AppPreferences.WEATHER_CLOUD, 0.15f),
                p.getFloat(AppPreferences.WEATHER_RAIN, 0f),
                p.getFloat(AppPreferences.WEATHER_WIND, 0.15f),
                p.getFloat(AppPreferences.WEATHER_FOG, 0.08f),
                p.getFloat(AppPreferences.WEATHER_THUNDER, 0f),
                p.getFloat(AppPreferences.WEATHER_UPCOMING_RAIN, 0f),
                p.getFloat(AppPreferences.WEATHER_UPCOMING_CLOUD, 0.15f),
                p.getFloat(AppPreferences.WEATHER_UPCOMING_WIND, 0.15f),
                p.getFloat(AppPreferences.WEATHER_UPCOMING_THUNDER, 0f),
                p.getLong(AppPreferences.WEATHER_UPDATED_AT, 0L)
        );
    }

    public boolean isFresh(long maxAgeMillis) {
        return updatedAt > 0L && System.currentTimeMillis() - updatedAt <= maxAgeMillis;
    }

    public static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
