package dev.andres.aetherscape.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.andres.aetherscape.prefs.AppPreferences;

/**
 * Tiny Google Weather API client for the beta.
 *
 * It intentionally uses only framework APIs so the project remains dependency-light.
 * For production, move the standard API key behind a small backend/proxy.
 */
public final class WeatherClient {
    public interface Callback {
        void onComplete(boolean success, String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AetherWeather");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private WeatherClient() {}

    public static void refreshIfNeeded(Context context) {
        SharedPreferences p = AppPreferences.get(context);
        if (!p.getBoolean(AppPreferences.DYNAMIC_WEATHER, true)) return;
        int minutes = Math.max(15, p.getInt(AppPreferences.WEATHER_UPDATE_MIN, 30));
        long updated = p.getLong(AppPreferences.WEATHER_UPDATED_AT, 0L);
        if (updated == 0L || System.currentTimeMillis() - updated >= minutes * 60_000L) {
            refresh(context, null);
        }
    }

    public static void refresh(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences p = AppPreferences.get(app);
        String key = p.getString(AppPreferences.API_KEY, "");
        if (key == null || key.trim().isEmpty()) {
            complete(callback, false, "Falta la clave de Google Weather API");
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            complete(callback, false, "Ya hay una actualización en curso");
            return;
        }

        final double latitude = AppPreferences.latitude(p);
        final double longitude = AppPreferences.longitude(p);
        final String apiKey = key.trim();

        EXECUTOR.execute(() -> {
            try {
                JSONObject current = requestJson(buildCurrentUrl(apiKey, latitude, longitude));
                JSONObject hourly = requestJson(buildHourlyUrl(apiKey, latitude, longitude, 6));
                WeatherSnapshot snapshot = parse(current, hourly);
                save(app, snapshot, null);
                complete(callback, true, snapshot.description.isEmpty()
                        ? "Clima actualizado"
                        : "Actualizado: " + snapshot.description);
            } catch (Exception error) {
                String message = readableError(error);
                save(app, null, message);
                complete(callback, false, message);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    private static URL buildCurrentUrl(String key, double lat, double lon) throws IOException {
        Uri uri = Uri.parse("https://weather.googleapis.com/v1/currentConditions:lookup")
                .buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("location.latitude", format(lat))
                .appendQueryParameter("location.longitude", format(lon))
                .appendQueryParameter("unitsSystem", "METRIC")
                .appendQueryParameter("languageCode", "es")
                .build();
        return new URL(uri.toString());
    }

    private static URL buildHourlyUrl(String key, double lat, double lon, int hours) throws IOException {
        Uri uri = Uri.parse("https://weather.googleapis.com/v1/forecast/hours:lookup")
                .buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("location.latitude", format(lat))
                .appendQueryParameter("location.longitude", format(lon))
                .appendQueryParameter("hours", String.valueOf(hours))
                .appendQueryParameter("unitsSystem", "METRIC")
                .appendQueryParameter("languageCode", "es")
                .build();
        return new URL(uri.toString());
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static JSONObject requestJson(URL url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(15_000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "AetherScape/0.1 beta");
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readAll(stream);
            if (code < 200 || code >= 300) {
                String apiMessage = extractApiMessage(body);
                throw new IOException("HTTP " + code + (apiMessage.isEmpty() ? "" : ": " + apiMessage));
            }
            return new JSONObject(body);
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static String extractApiMessage(String body) {
        if (body == null || body.isEmpty()) return "";
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            return error == null ? "" : error.optString("message", "");
        } catch (JSONException ignored) {
            return "";
        }
    }

    private static WeatherSnapshot parse(JSONObject current, JSONObject hourly) {
        JSONObject conditionObject = current.optJSONObject("weatherCondition");
        String condition = conditionObject == null ? "CLEAR" : conditionObject.optString("type", "CLEAR");
        JSONObject descriptionObject = conditionObject == null ? null : conditionObject.optJSONObject("description");
        String description = descriptionObject == null ? condition : descriptionObject.optString("text", condition);

        float temperature = degrees(current.optJSONObject("temperature"), 18f);
        float cloud = percent(current.optDouble("cloudCover", 0d));
        float rain = precipitation(current.optJSONObject("precipitation"));
        float wind = wind(current.optJSONObject("wind"));
        float thunder = percent(current.optDouble("thunderstormProbability", 0d));
        float fog = fog(current, condition);

        float upcomingRain = rain;
        float upcomingCloud = cloud;
        float upcomingWind = wind;
        float upcomingThunder = thunder;

        JSONArray hours = hourly.optJSONArray("forecastHours");
        if (hours != null) {
            int limit = Math.min(hours.length(), 6);
            for (int i = 0; i < limit; i++) {
                JSONObject hour = hours.optJSONObject(i);
                if (hour == null) continue;
                upcomingRain = Math.max(upcomingRain, precipitation(hour.optJSONObject("precipitation")));
                upcomingCloud = Math.max(upcomingCloud, percent(hour.optDouble("cloudCover", 0d)));
                upcomingWind = Math.max(upcomingWind, wind(hour.optJSONObject("wind")));
                upcomingThunder = Math.max(upcomingThunder,
                        percent(hour.optDouble("thunderstormProbability", 0d)));
            }
        }

        return new WeatherSnapshot(
                condition,
                description,
                temperature,
                cloud,
                rain,
                wind,
                fog,
                thunder,
                upcomingRain,
                upcomingCloud,
                upcomingWind,
                upcomingThunder,
                System.currentTimeMillis()
        );
    }

    private static float degrees(JSONObject object, float fallback) {
        return object == null ? fallback : (float) object.optDouble("degrees", fallback);
    }

    private static float percent(double percent) {
        return WeatherSnapshot.clamp01((float) (percent / 100d));
    }

    private static float precipitation(JSONObject object) {
        if (object == null) return 0f;
        JSONObject probability = object.optJSONObject("probability");
        float chance = probability == null ? 0f : percent(probability.optDouble("percent", 0d));
        JSONObject qpf = object.optJSONObject("qpf");
        float quantity = qpf == null ? 0f : (float) qpf.optDouble("quantity", 0d);
        float amount = WeatherSnapshot.clamp01(quantity / 8f);
        return WeatherSnapshot.clamp01(Math.max(chance * 0.78f, amount));
    }

    private static float wind(JSONObject object) {
        if (object == null) return 0f;
        JSONObject speed = object.optJSONObject("speed");
        JSONObject gust = object.optJSONObject("gust");
        float speedValue = speed == null ? 0f : (float) speed.optDouble("value", 0d);
        float gustValue = gust == null ? speedValue : (float) gust.optDouble("value", speedValue);
        return WeatherSnapshot.clamp01(Math.max(speedValue / 42f, gustValue / 65f));
    }

    private static float fog(JSONObject current, String condition) {
        float typeFog = containsAny(condition, "FOG", "HAZE", "MIST", "SMOKE", "DUST") ? 0.78f : 0f;
        JSONObject visibility = current.optJSONObject("visibility");
        if (visibility == null) return typeFog;
        float distance = (float) visibility.optDouble("distance", 16d);
        String unit = visibility.optString("unit", "KILOMETERS");
        if (unit.contains("MILE")) distance *= 1.60934f;
        float visibilityFog = WeatherSnapshot.clamp01(1f - distance / 12f);
        return Math.max(typeFog, visibilityFog);
    }

    private static boolean containsAny(String value, String... needles) {
        String upper = value == null ? "" : value.toUpperCase(Locale.US);
        for (String needle : needles) if (upper.contains(needle)) return true;
        return false;
    }

    private static void save(Context context, WeatherSnapshot snapshot, String error) {
        SharedPreferences.Editor editor = AppPreferences.get(context).edit();
        if (snapshot != null) {
            editor.putString(AppPreferences.WEATHER_CONDITION, snapshot.condition)
                    .putString(AppPreferences.WEATHER_DESCRIPTION, snapshot.description)
                    .putFloat(AppPreferences.WEATHER_TEMP_C, snapshot.temperatureC)
                    .putFloat(AppPreferences.WEATHER_CLOUD, snapshot.cloud)
                    .putFloat(AppPreferences.WEATHER_RAIN, snapshot.rain)
                    .putFloat(AppPreferences.WEATHER_WIND, snapshot.wind)
                    .putFloat(AppPreferences.WEATHER_FOG, snapshot.fog)
                    .putFloat(AppPreferences.WEATHER_THUNDER, snapshot.thunder)
                    .putFloat(AppPreferences.WEATHER_UPCOMING_RAIN, snapshot.upcomingRain)
                    .putFloat(AppPreferences.WEATHER_UPCOMING_CLOUD, snapshot.upcomingCloud)
                    .putFloat(AppPreferences.WEATHER_UPCOMING_WIND, snapshot.upcomingWind)
                    .putFloat(AppPreferences.WEATHER_UPCOMING_THUNDER, snapshot.upcomingThunder)
                    .putLong(AppPreferences.WEATHER_UPDATED_AT, snapshot.updatedAt)
                    .putString(AppPreferences.WEATHER_ERROR, "");
        } else {
            editor.putString(AppPreferences.WEATHER_ERROR, error == null ? "Error desconocido" : error);
        }
        editor.apply();
    }

    private static void complete(Callback callback, boolean success, String message) {
        if (callback == null) return;
        MAIN.post(() -> callback.onComplete(success, message));
    }

    private static String readableError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
        return "No se pudo actualizar el clima: " + message;
    }
}
