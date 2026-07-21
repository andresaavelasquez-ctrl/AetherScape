package dev.andres.aetherscape.render;

import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.weather.WeatherSnapshot;

/** Normalized scene state consumed by the renderer. */
public final class SceneState {
    public enum Season { SPRING, SUMMER, AUTUMN, WINTER }

    public float hour;
    public float cloud;
    public float rain;
    public float snow;
    public float wind;
    public float fog;
    public float storm;
    public float temperatureC;
    public Season season;

    public float scrollSpeed;
    public float treeDensity;
    public float sceneVariety;
    public float effectIntensity;
    public float motionIntensity;
    public boolean showStars;
    public boolean showGlow;
    public boolean showFireflies;
    public boolean showCampfires;
    public boolean showLightning;
    public boolean parallax;
    public boolean snowCaps;
    public int targetFps;
    public boolean adaptiveRendering;
    public String weatherDescription;

    public SceneState() {
        hour = 18f;
        season = Season.AUTUMN;
        weatherDescription = "Clima de demostración";
    }

    public static SceneState fromPreferences(SharedPreferences p) {
        SceneState out = new SceneState();
        Calendar now = Calendar.getInstance();
        boolean liveTime = p.getBoolean(AppPreferences.LIVE_TIME, true);
        out.hour = liveTime
                ? now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f + now.get(Calendar.SECOND) / 3600f
                : p.getInt(AppPreferences.MANUAL_HOUR, 18);

        double latitude = AppPreferences.latitude(p);
        out.season = resolveSeason(p.getString(AppPreferences.SEASON_MODE, "AUTO"), now, latitude);

        WeatherSnapshot weather = WeatherSnapshot.fromPreferences(p);
        out.temperatureC = weather.temperatureC;
        out.weatherDescription = weather.description;

        boolean dynamic = p.getBoolean(AppPreferences.DYNAMIC_WEATHER, true);
        if (dynamic) {
            out.cloud = weather.cloud;
            out.rain = weather.rain;
            out.wind = weather.wind;
            out.fog = weather.fog;
            out.storm = weather.thunder;

            if (p.getBoolean(AppPreferences.FORECAST_BLEND, true)) {
                // Foreshadow incoming conditions without jumping abruptly to the future state.
                out.cloud = Math.max(out.cloud, weather.upcomingCloud * 0.86f);
                out.wind = Math.max(out.wind, weather.upcomingWind * 0.74f);
                out.storm = Math.max(out.storm, weather.upcomingThunder * 0.42f);
                out.rain = Math.max(out.rain, weather.upcomingRain * 0.22f);
            }
        } else {
            out.cloud = 0.18f;
            out.rain = 0f;
            out.wind = 0.15f;
            out.fog = 0.06f;
            out.storm = 0f;
        }

        String override = p.getString(AppPreferences.WEATHER_OVERRIDE, "AUTO");
        applyOverride(out, override);

        out.cloud = clamp01(out.cloud + p.getInt(AppPreferences.CLOUD_BIAS, 0) / 160f);
        out.rain = clamp01(out.rain + p.getInt(AppPreferences.RAIN_BIAS, 0) / 135f);
        out.wind = clamp01(out.wind + p.getInt(AppPreferences.WIND_BIAS, 0) / 150f);
        out.fog = clamp01(out.fog + p.getInt(AppPreferences.FOG_BIAS, 0) / 125f);

        String condition = weather.condition == null ? "" : weather.condition.toUpperCase(Locale.US);
        boolean snowCondition = containsAny(condition, "SNOW", "SLEET", "BLIZZARD", "ICE", "FLURR");
        out.snow = snowCondition ? Math.max(0.36f, out.rain) : 0f;
        if (!snowCondition && out.temperatureC <= 0.5f && out.rain > 0.3f) {
            out.snow = out.rain * 0.7f;
        }
        if (out.snow > 0f) out.rain *= 0.22f;

        out.scrollSpeed = p.getInt(AppPreferences.SCROLL_SPEED, 32) / 100f;
        out.treeDensity = p.getInt(AppPreferences.TREE_DENSITY, 62) / 100f;
        out.sceneVariety = p.getInt(AppPreferences.SCENE_VARIETY, 72) / 100f;
        out.effectIntensity = p.getInt(AppPreferences.EFFECT_INTENSITY, 70) / 100f;
        out.motionIntensity = p.getInt(AppPreferences.MOTION_INTENSITY, 55) / 100f;
        out.showStars = p.getBoolean(AppPreferences.SHOW_STARS, true);
        out.showGlow = p.getBoolean(AppPreferences.SHOW_GLOW, true);
        out.showFireflies = p.getBoolean(AppPreferences.SHOW_FIREFLIES, true);
        out.showCampfires = p.getBoolean(AppPreferences.SHOW_CAMPFIRES, true);
        out.showLightning = p.getBoolean(AppPreferences.SHOW_LIGHTNING, true);
        out.parallax = p.getBoolean(AppPreferences.PARALLAX, true);
        out.snowCaps = p.getBoolean(AppPreferences.SNOW_CAPS, true);
        out.targetFps = p.getBoolean(AppPreferences.BATTERY_SAVER, false)
                ? 15
                : p.getInt(AppPreferences.TARGET_FPS, 30);
        out.adaptiveRendering = p.getBoolean(AppPreferences.ADAPTIVE_RENDERING, true);
        return out;
    }

    private static Season resolveSeason(String mode, Calendar now, double latitude) {
        if (mode != null && !"AUTO".equals(mode)) {
            try {
                return Season.valueOf(mode);
            } catch (IllegalArgumentException ignored) {
                // Fall through to automatic resolution.
            }
        }
        int month = now.get(Calendar.MONTH) + 1;
        Season northern;
        if (month >= 3 && month <= 5) northern = Season.SPRING;
        else if (month >= 6 && month <= 8) northern = Season.SUMMER;
        else if (month >= 9 && month <= 11) northern = Season.AUTUMN;
        else northern = Season.WINTER;

        if (latitude >= 0) return northern;
        switch (northern) {
            case SPRING: return Season.AUTUMN;
            case SUMMER: return Season.WINTER;
            case AUTUMN: return Season.SPRING;
            default: return Season.SUMMER;
        }
    }

    private static void applyOverride(SceneState state, String override) {
        if (override == null || "AUTO".equals(override)) return;
        switch (override) {
            case "CLEAR":
                state.cloud = 0.08f; state.rain = 0f; state.wind = 0.12f; state.fog = 0f; state.storm = 0f;
                break;
            case "CLOUDY":
                state.cloud = 0.82f; state.rain = 0.05f; state.wind = 0.22f; state.fog = 0.12f; state.storm = 0f;
                break;
            case "RAIN":
                state.cloud = 0.92f; state.rain = 0.65f; state.wind = 0.42f; state.fog = 0.25f; state.storm = 0.08f;
                break;
            case "STORM":
                state.cloud = 1f; state.rain = 0.95f; state.wind = 0.82f; state.fog = 0.30f; state.storm = 0.92f;
                break;
            case "FOG":
                state.cloud = 0.68f; state.rain = 0.08f; state.wind = 0.07f; state.fog = 0.90f; state.storm = 0f;
                break;
            case "SNOW":
                state.cloud = 0.88f; state.rain = 0.70f; state.wind = 0.35f; state.fog = 0.28f; state.storm = 0.03f;
                state.temperatureC = -3f;
                break;
            case "WIND":
                state.cloud = 0.38f; state.rain = 0.04f; state.wind = 0.92f; state.fog = 0.06f; state.storm = 0.04f;
                break;
            default:
                break;
        }
    }

    public void smoothToward(SceneState target, float alpha) {
        hour = lerpHour(hour, target.hour, alpha);
        cloud = lerp(cloud, target.cloud, alpha);
        rain = lerp(rain, target.rain, alpha);
        snow = lerp(snow, target.snow, alpha);
        wind = lerp(wind, target.wind, alpha);
        fog = lerp(fog, target.fog, alpha);
        storm = lerp(storm, target.storm, alpha);
        temperatureC = lerp(temperatureC, target.temperatureC, alpha);
        scrollSpeed = lerp(scrollSpeed, target.scrollSpeed, alpha);
        treeDensity = lerp(treeDensity, target.treeDensity, alpha);
        sceneVariety = lerp(sceneVariety, target.sceneVariety, alpha);
        effectIntensity = lerp(effectIntensity, target.effectIntensity, alpha);
        motionIntensity = lerp(motionIntensity, target.motionIntensity, alpha);
        season = target.season;
        showStars = target.showStars;
        showGlow = target.showGlow;
        showFireflies = target.showFireflies;
        showCampfires = target.showCampfires;
        showLightning = target.showLightning;
        parallax = target.parallax;
        snowCaps = target.snowCaps;
        targetFps = target.targetFps;
        adaptiveRendering = target.adaptiveRendering;
        weatherDescription = target.weatherDescription;
    }

    public SceneState copy() {
        SceneState c = new SceneState();
        c.hour = hour; c.cloud = cloud; c.rain = rain; c.snow = snow; c.wind = wind; c.fog = fog;
        c.storm = storm; c.temperatureC = temperatureC; c.season = season;
        c.scrollSpeed = scrollSpeed; c.treeDensity = treeDensity; c.sceneVariety = sceneVariety;
        c.effectIntensity = effectIntensity; c.motionIntensity = motionIntensity;
        c.showStars = showStars; c.showGlow = showGlow; c.showFireflies = showFireflies;
        c.showCampfires = showCampfires; c.showLightning = showLightning; c.parallax = parallax;
        c.snowCaps = snowCaps; c.targetFps = targetFps; c.adaptiveRendering = adaptiveRendering; c.weatherDescription = weatherDescription;
        return c;
    }

    public float nightFactor() {
        float h = ((hour % 24f) + 24f) % 24f;
        if (h < 4.5f || h >= 21f) return 1f;
        if (h < 7f) return 1f - (h - 4.5f) / 2.5f;
        if (h >= 18.5f) return (h - 18.5f) / 2.5f;
        return 0f;
    }

    public float duskFactor() {
        float h = ((hour % 24f) + 24f) % 24f;
        float dawn = 1f - Math.min(1f, Math.abs(h - 6f) / 2.2f);
        float dusk = 1f - Math.min(1f, Math.abs(h - 19f) / 2.2f);
        return Math.max(dawn, dusk);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpHour(float a, float b, float t) {
        float diff = ((b - a + 36f) % 24f) - 12f;
        float value = a + diff * t;
        return (value + 24f) % 24f;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
