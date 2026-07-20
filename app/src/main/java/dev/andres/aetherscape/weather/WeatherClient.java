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
 * Multi-provider weather client for the beta.
 *
 * Supported providers:
 * - Open-Meteo (no key)
 * - Google Weather API
 * - OpenWeatherMap
 * - WeatherAPI.com
 */
public final class WeatherClient {
    public interface Callback {
        void onComplete(boolean success, String message);
    }

    public enum Provider {
        OPEN_METEO(false, "Open-Meteo"),
        GOOGLE(true, "Google Weather API"),
        OPENWEATHERMAP(true, "OpenWeatherMap"),
        WEATHERAPI(true, "WeatherAPI.com");

        final boolean requiresKey;
        final String label;

        Provider(boolean requiresKey, String label) {
            this.requiresKey = requiresKey;
            this.label = label;
        }

        static Provider fromPrefs(SharedPreferences p) {
            String raw = p.getString(AppPreferences.WEATHER_PROVIDER, "OPEN_METEO");
            if (raw != null) {
                try { return Provider.valueOf(raw); } catch (IllegalArgumentException ignored) {}
            }
            return OPEN_METEO;
        }
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
        Provider provider = Provider.fromPrefs(p);
        String key = trim(p.getString(AppPreferences.API_KEY, ""));
        if (provider.requiresKey && key.isEmpty()) {
            complete(callback, false, "Falta la clave para " + provider.label);
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            complete(callback, false, "Ya hay una actualización en curso");
            return;
        }

        final double latitude = AppPreferences.latitude(p);
        final double longitude = AppPreferences.longitude(p);

        EXECUTOR.execute(() -> {
            try {
                WeatherSnapshot snapshot;
                switch (provider) {
                    case GOOGLE: snapshot = refreshGoogle(key, latitude, longitude); break;
                    case OPENWEATHERMAP: snapshot = refreshOpenWeather(key, latitude, longitude); break;
                    case WEATHERAPI: snapshot = refreshWeatherApi(key, latitude, longitude); break;
                    case OPEN_METEO:
                    default: snapshot = refreshOpenMeteo(latitude, longitude); break;
                }
                save(app, snapshot, null);
                complete(callback, true, provider.label + " · " + (snapshot.description.isEmpty()
                        ? "Clima actualizado"
                        : snapshot.description));
            } catch (Exception error) {
                String message = readableError(error);
                save(app, null, message);
                complete(callback, false, message);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    private static WeatherSnapshot refreshGoogle(String key, double lat, double lon) throws Exception {
        JSONObject current = requestJson(buildGoogleCurrentUrl(key, lat, lon));
        JSONObject hourly = requestJson(buildGoogleHourlyUrl(key, lat, lon, 6));
        return parseGoogle(current, hourly);
    }

    private static WeatherSnapshot refreshOpenMeteo(double lat, double lon) throws Exception {
        JSONObject root = requestJson(buildOpenMeteoUrl(lat, lon));
        return parseOpenMeteo(root);
    }

    private static WeatherSnapshot refreshOpenWeather(String key, double lat, double lon) throws Exception {
        JSONObject current = requestJson(buildOpenWeatherCurrentUrl(key, lat, lon));
        JSONObject forecast = requestJson(buildOpenWeatherForecastUrl(key, lat, lon));
        return parseOpenWeather(current, forecast);
    }

    private static WeatherSnapshot refreshWeatherApi(String key, double lat, double lon) throws Exception {
        JSONObject root = requestJson(buildWeatherApiUrl(key, lat, lon));
        return parseWeatherApi(root);
    }

    private static URL buildGoogleCurrentUrl(String key, double lat, double lon) throws IOException {
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

    private static URL buildGoogleHourlyUrl(String key, double lat, double lon, int hours) throws IOException {
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

    private static URL buildOpenMeteoUrl(double lat, double lon) throws IOException {
        Uri uri = Uri.parse("https://api.open-meteo.com/v1/forecast")
                .buildUpon()
                .appendQueryParameter("latitude", format(lat))
                .appendQueryParameter("longitude", format(lon))
                .appendQueryParameter("timezone", "auto")
                .appendQueryParameter("forecast_hours", "6")
                .appendQueryParameter("current", "temperature_2m,cloud_cover,precipitation,precipitation_probability,weather_code,wind_speed_10m,wind_gusts_10m,visibility")
                .appendQueryParameter("hourly", "cloud_cover,precipitation,precipitation_probability,weather_code,wind_speed_10m,wind_gusts_10m,visibility")
                .build();
        return new URL(uri.toString());
    }

    private static URL buildOpenWeatherCurrentUrl(String key, double lat, double lon) throws IOException {
        Uri uri = Uri.parse("https://api.openweathermap.org/data/2.5/weather")
                .buildUpon()
                .appendQueryParameter("appid", key)
                .appendQueryParameter("lat", format(lat))
                .appendQueryParameter("lon", format(lon))
                .appendQueryParameter("units", "metric")
                .appendQueryParameter("lang", "es")
                .build();
        return new URL(uri.toString());
    }

    private static URL buildOpenWeatherForecastUrl(String key, double lat, double lon) throws IOException {
        Uri uri = Uri.parse("https://api.openweathermap.org/data/2.5/forecast")
                .buildUpon()
                .appendQueryParameter("appid", key)
                .appendQueryParameter("lat", format(lat))
                .appendQueryParameter("lon", format(lon))
                .appendQueryParameter("units", "metric")
                .appendQueryParameter("lang", "es")
                .appendQueryParameter("cnt", "6")
                .build();
        return new URL(uri.toString());
    }

    private static URL buildWeatherApiUrl(String key, double lat, double lon) throws IOException {
        Uri uri = Uri.parse("https://api.weatherapi.com/v1/forecast.json")
                .buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("q", format(lat) + "," + format(lon))
                .appendQueryParameter("days", "1")
                .appendQueryParameter("aqi", "no")
                .appendQueryParameter("alerts", "no")
                .appendQueryParameter("lang", "es")
                .build();
        return new URL(uri.toString());
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static JSONObject requestJson(URL url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(16_000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "AetherScape/0.2 beta");
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
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
            if (error != null) return error.optString("message", "");
            if (root.has("message")) return root.optString("message", "");
        } catch (JSONException ignored) { }
        return "";
    }

    private static WeatherSnapshot parseGoogle(JSONObject current, JSONObject hourly) {
        JSONObject conditionObject = current.optJSONObject("weatherCondition");
        String condition = conditionObject == null ? "CLEAR" : conditionObject.optString("type", "CLEAR");
        JSONObject descriptionObject = conditionObject == null ? null : conditionObject.optJSONObject("description");
        String description = descriptionObject == null ? condition : descriptionObject.optString("text", condition);

        float temperature = degrees(current.optJSONObject("temperature"), 18f);
        float cloud = percent(current.optDouble("cloudCover", 0d));
        float rain = googlePrecipitation(current.optJSONObject("precipitation"));
        float wind = googleWind(current.optJSONObject("wind"));
        float thunder = percent(current.optDouble("thunderstormProbability", 0d));
        float fog = visibilityFogKm(kmFromVisibility(current.optJSONObject("visibility")), containsAny(condition, "FOG", "HAZE", "MIST", "SMOKE", "DUST"));

        float upcomingRain = rain, upcomingCloud = cloud, upcomingWind = wind, upcomingThunder = thunder;
        JSONArray hours = hourly.optJSONArray("forecastHours");
        if (hours != null) {
            int limit = Math.min(hours.length(), 6);
            for (int i = 0; i < limit; i++) {
                JSONObject hour = hours.optJSONObject(i);
                if (hour == null) continue;
                upcomingRain = Math.max(upcomingRain, googlePrecipitation(hour.optJSONObject("precipitation")));
                upcomingCloud = Math.max(upcomingCloud, percent(hour.optDouble("cloudCover", 0d)));
                upcomingWind = Math.max(upcomingWind, googleWind(hour.optJSONObject("wind")));
                upcomingThunder = Math.max(upcomingThunder, percent(hour.optDouble("thunderstormProbability", 0d)));
            }
        }
        return snapshot(condition, description, temperature, cloud, rain, wind, fog, thunder, upcomingRain, upcomingCloud, upcomingWind, upcomingThunder);
    }

    private static WeatherSnapshot parseOpenMeteo(JSONObject root) {
        JSONObject current = root.optJSONObject("current");
        JSONObject hourly = root.optJSONObject("hourly");
        int code = current == null ? 0 : current.optInt("weather_code", 0);
        String condition = openMeteoCondition(code);
        String description = openMeteoDescription(code);
        float temperature = current == null ? 18f : (float) current.optDouble("temperature_2m", 18d);
        float cloud = current == null ? 0f : percent(current.optDouble("cloud_cover", 0d));
        float rain = normalizePrecipitation(current == null ? 0f : (float) current.optDouble("precipitation", 0d),
                current == null ? 0f : percent(current.optDouble("precipitation_probability", 0d)));
        float wind = normalizeWindKmh(current == null ? 0f : (float) current.optDouble("wind_speed_10m", 0d),
                current == null ? 0f : (float) current.optDouble("wind_gusts_10m", 0d));
        float fog = visibilityFogKm(metersToKm(current == null ? 10000f : (float) current.optDouble("visibility", 10000d)), code == 45 || code == 48);
        float thunder = (code == 95 || code == 96 || code == 99) ? 0.90f : 0f;

        float upcomingRain = rain, upcomingCloud = cloud, upcomingWind = wind, upcomingThunder = thunder;
        if (hourly != null) {
            JSONArray codes = hourly.optJSONArray("weather_code");
            JSONArray clouds = hourly.optJSONArray("cloud_cover");
            JSONArray rains = hourly.optJSONArray("precipitation");
            JSONArray probs = hourly.optJSONArray("precipitation_probability");
            JSONArray winds = hourly.optJSONArray("wind_speed_10m");
            JSONArray gusts = hourly.optJSONArray("wind_gusts_10m");
            int limit = arrayMinLen(6, codes, clouds, rains, probs, winds, gusts);
            for (int i = 0; i < limit; i++) {
                upcomingCloud = Math.max(upcomingCloud, percent(arrayDouble(clouds, i)));
                upcomingRain = Math.max(upcomingRain, normalizePrecipitation((float) arrayDouble(rains, i), percent(arrayDouble(probs, i))));
                upcomingWind = Math.max(upcomingWind, normalizeWindKmh((float) arrayDouble(winds, i), (float) arrayDouble(gusts, i)));
                int hourCode = (int) arrayDouble(codes, i);
                if (hourCode == 95 || hourCode == 96 || hourCode == 99) upcomingThunder = Math.max(upcomingThunder, 0.90f);
            }
        }
        return snapshot(condition, description, temperature, cloud, rain, wind, fog, thunder, upcomingRain, upcomingCloud, upcomingWind, upcomingThunder);
    }

    private static WeatherSnapshot parseOpenWeather(JSONObject current, JSONObject forecast) {
        JSONArray weatherArray = current.optJSONArray("weather");
        JSONObject primary = weatherArray == null ? null : weatherArray.optJSONObject(0);
        String main = primary == null ? "Clear" : primary.optString("main", "Clear");
        String description = primary == null ? main : primary.optString("description", main);
        String condition = main.toUpperCase(Locale.US);

        JSONObject mainObject = current.optJSONObject("main");
        float temperature = mainObject == null ? 18f : (float) mainObject.optDouble("temp", 18d);
        JSONObject cloudsObj = current.optJSONObject("clouds");
        float cloud = cloudsObj == null ? 0f : percent(cloudsObj.optDouble("all", 0d));
        float rain = openWeatherRain(current);
        JSONObject windObj = current.optJSONObject("wind");
        float wind = normalizeWindMs(windObj == null ? 0f : (float) windObj.optDouble("speed", 0d),
                windObj == null ? 0f : (float) windObj.optDouble("gust", 0d));
        float visibilityKm = current.has("visibility") ? ((float) current.optDouble("visibility", 10000d) / 1000f) : 10f;
        float fog = visibilityFogKm(visibilityKm, containsAny(condition, "FOG", "MIST", "HAZE", "SMOKE"));
        float thunder = containsAny(condition, "THUNDER") ? 0.92f : 0f;

        float upcomingRain = rain, upcomingCloud = cloud, upcomingWind = wind, upcomingThunder = thunder;
        JSONArray list = forecast.optJSONArray("list");
        if (list != null) {
            int limit = Math.min(6, list.length());
            for (int i = 0; i < limit; i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                JSONObject itemClouds = item.optJSONObject("clouds");
                JSONObject itemWind = item.optJSONObject("wind");
                upcomingCloud = Math.max(upcomingCloud, percent(itemClouds == null ? 0d : itemClouds.optDouble("all", 0d)));
                upcomingRain = Math.max(upcomingRain, openWeatherRain(item));
                upcomingWind = Math.max(upcomingWind, normalizeWindMs(itemWind == null ? 0f : (float) itemWind.optDouble("speed", 0d),
                        itemWind == null ? 0f : (float) itemWind.optDouble("gust", 0d)));
                JSONArray w = item.optJSONArray("weather");
                JSONObject one = w == null ? null : w.optJSONObject(0);
                String m = one == null ? "" : one.optString("main", "");
                if (containsAny(m, "THUNDER")) upcomingThunder = Math.max(upcomingThunder, 0.92f);
            }
        }
        return snapshot(condition, description, temperature, cloud, rain, wind, fog, thunder, upcomingRain, upcomingCloud, upcomingWind, upcomingThunder);
    }

    private static WeatherSnapshot parseWeatherApi(JSONObject root) {
        JSONObject current = root.optJSONObject("current");
        JSONObject conditionObj = current == null ? null : current.optJSONObject("condition");
        String description = conditionObj == null ? "Clear" : conditionObj.optString("text", "Clear");
        String condition = classifyTextCondition(description);
        float temperature = current == null ? 18f : (float) current.optDouble("temp_c", 18d);
        float cloud = current == null ? 0f : percent(current.optDouble("cloud", 0d));
        float rain = normalizePrecipitation(current == null ? 0f : (float) current.optDouble("precip_mm", 0d), cloud * 0.2f);
        float wind = normalizeWindKmh(current == null ? 0f : (float) current.optDouble("wind_kph", 0d),
                current == null ? 0f : (float) current.optDouble("gust_kph", current.optDouble("wind_kph", 0d)));
        float fog = visibilityFogKm(current == null ? 10f : (float) current.optDouble("vis_km", 10d), containsAny(description, "fog", "mist", "haze", "nebl", "bruma"));
        float thunder = containsAny(description, "thunder", "tormenta") ? 0.90f : 0f;

        float upcomingRain = rain, upcomingCloud = cloud, upcomingWind = wind, upcomingThunder = thunder;
        JSONObject forecast = root.optJSONObject("forecast");
        JSONArray days = forecast == null ? null : forecast.optJSONArray("forecastday");
        if (days != null && days.length() > 0) {
            JSONObject day0 = days.optJSONObject(0);
            JSONArray hours = day0 == null ? null : day0.optJSONArray("hour");
            if (hours != null) {
                int limit = Math.min(6, hours.length());
                for (int i = 0; i < limit; i++) {
                    JSONObject hour = hours.optJSONObject(i);
                    if (hour == null) continue;
                    JSONObject hourCondition = hour.optJSONObject("condition");
                    String hourText = hourCondition == null ? "" : hourCondition.optString("text", "");
                    upcomingCloud = Math.max(upcomingCloud, percent(hour.optDouble("cloud", 0d)));
                    upcomingRain = Math.max(upcomingRain, normalizePrecipitation((float) hour.optDouble("precip_mm", 0d), (float) hour.optDouble("chance_of_rain", 0d) / 100f));
                    upcomingWind = Math.max(upcomingWind, normalizeWindKmh((float) hour.optDouble("wind_kph", 0d), (float) hour.optDouble("gust_kph", hour.optDouble("wind_kph", 0d))));
                    if (containsAny(hourText, "thunder", "tormenta")) upcomingThunder = Math.max(upcomingThunder, 0.90f);
                }
            }
        }
        return snapshot(condition, description, temperature, cloud, rain, wind, fog, thunder, upcomingRain, upcomingCloud, upcomingWind, upcomingThunder);
    }

    private static WeatherSnapshot snapshot(String condition, String description, float temperature,
                                            float cloud, float rain, float wind, float fog, float thunder,
                                            float upcomingRain, float upcomingCloud, float upcomingWind, float upcomingThunder) {
        return new WeatherSnapshot(condition, description, temperature, cloud, rain, wind, fog, thunder, upcomingRain, upcomingCloud, upcomingWind, upcomingThunder, System.currentTimeMillis());
    }

    private static float degrees(JSONObject object, float fallback) {
        return object == null ? fallback : (float) object.optDouble("degrees", fallback);
    }

    private static float percent(double percent) {
        return WeatherSnapshot.clamp01((float) (percent / 100d));
    }

    private static float googlePrecipitation(JSONObject object) {
        if (object == null) return 0f;
        JSONObject probability = object.optJSONObject("probability");
        float chance = probability == null ? 0f : percent(probability.optDouble("percent", 0d));
        JSONObject qpf = object.optJSONObject("qpf");
        float quantity = qpf == null ? 0f : (float) qpf.optDouble("quantity", 0d);
        return normalizePrecipitation(quantity, chance);
    }

    private static float googleWind(JSONObject object) {
        if (object == null) return 0f;
        JSONObject speed = object.optJSONObject("speed");
        JSONObject gust = object.optJSONObject("gust");
        float speedValue = speed == null ? 0f : (float) speed.optDouble("value", 0d);
        float gustValue = gust == null ? speedValue : (float) gust.optDouble("value", speedValue);
        return normalizeWindKmh(speedValue, gustValue);
    }

    private static float kmFromVisibility(JSONObject visibility) {
        if (visibility == null) return 10f;
        float distance = (float) visibility.optDouble("distance", 16d);
        String unit = visibility.optString("unit", "KILOMETERS");
        if (unit.contains("MILE")) distance *= 1.60934f;
        return distance;
    }

    private static float metersToKm(float meters) {
        return meters / 1000f;
    }

    private static float visibilityFogKm(float visibilityKm, boolean explicitFog) {
        float visibilityFog = WeatherSnapshot.clamp01(1f - visibilityKm / 12f);
        return Math.max(explicitFog ? 0.78f : 0f, visibilityFog);
    }

    private static float normalizePrecipitation(float mm, float chance) {
        float amount = WeatherSnapshot.clamp01(mm / 8f);
        return WeatherSnapshot.clamp01(Math.max(chance * 0.78f, amount));
    }

    private static float normalizeWindKmh(float speed, float gust) {
        return WeatherSnapshot.clamp01(Math.max(speed / 42f, gust / 65f));
    }

    private static float normalizeWindMs(float speed, float gust) {
        return WeatherSnapshot.clamp01(Math.max(speed / 18f, gust / 27f));
    }

    private static float openWeatherRain(JSONObject root) {
        float mm = 0f;
        JSONObject rain = root.optJSONObject("rain");
        if (rain != null) mm = (float) Math.max(rain.optDouble("1h", 0d), rain.optDouble("3h", 0d) / 3d);
        JSONObject snow = root.optJSONObject("snow");
        if (snow != null) mm = Math.max(mm, (float) Math.max(snow.optDouble("1h", 0d), snow.optDouble("3h", 0d) / 3d));
        return normalizePrecipitation(mm, 0f);
    }

    private static int arrayMinLen(int max, JSONArray... arrays) {
        int limit = max;
        for (JSONArray a : arrays) {
            if (a == null) return 0;
            limit = Math.min(limit, a.length());
        }
        return limit;
    }

    private static double arrayDouble(JSONArray a, int index) {
        return a == null ? 0d : a.optDouble(index, 0d);
    }

    private static String openMeteoCondition(int code) {
        if (code == 0) return "CLEAR";
        if (code == 1 || code == 2 || code == 3) return "CLOUDY";
        if (code == 45 || code == 48) return "FOG";
        if (code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86) return "SNOW";
        if (code == 95 || code == 96 || code == 99) return "THUNDER";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "RAIN";
        return "CLOUDY";
    }

    private static String openMeteoDescription(int code) {
        switch (code) {
            case 0: return "Despejado";
            case 1: return "Mayormente despejado";
            case 2: return "Parcialmente nublado";
            case 3: return "Nublado";
            case 45: case 48: return "Niebla";
            case 51: case 53: case 55: return "Llovizna";
            case 61: case 63: case 65: return "Lluvia";
            case 66: case 67: return "Lluvia helada";
            case 71: case 73: case 75: return "Nieve";
            case 77: return "Granizo de nieve";
            case 80: case 81: case 82: return "Chubascos";
            case 85: case 86: return "Chubascos de nieve";
            case 95: return "Tormenta";
            case 96: case 99: return "Tormenta con granizo";
            default: return "Clima variable";
        }
    }

    private static String classifyTextCondition(String text) {
        String upper = text == null ? "" : text.toUpperCase(Locale.US);
        if (containsAny(upper, "THUNDER", "STORM", "TORMENTA")) return "THUNDER";
        if (containsAny(upper, "SNOW", "SLEET", "NIEVE", "GRANIZO")) return "SNOW";
        if (containsAny(upper, "FOG", "MIST", "HAZE", "BRUMA", "NIEBLA")) return "FOG";
        if (containsAny(upper, "RAIN", "SHOWER", "DRIZZLE", "LLUV")) return "RAIN";
        if (containsAny(upper, "CLOUD", "NUBL")) return "CLOUDY";
        return "CLEAR";
    }

    private static boolean containsAny(String value, String... needles) {
        String upper = value == null ? "" : value.toUpperCase(Locale.US);
        for (String needle : needles) if (upper.contains(needle.toUpperCase(Locale.US))) return true;
        return false;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
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
