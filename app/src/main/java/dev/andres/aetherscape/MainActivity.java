package dev.andres.aetherscape;

import android.Manifest;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.andres.aetherscape.prefs.AppPreferences;
import dev.andres.aetherscape.ui.ScenePreviewView;
import dev.andres.aetherscape.wallpaper.AetherWallpaperService;
import dev.andres.aetherscape.weather.WeatherClient;
import dev.andres.aetherscape.weather.WeatherSnapshot;

/**
 * Configuration dashboard inspired by the supplied dynamic-wallpaper video:
 * time modes, live location, dynamic weather, manual atmosphere sliders,
 * scene/season controls and effect/performance tabs.
 */
public final class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int LOCATION_PERMISSION_REQUEST = 1501;
    private static final String[] TABS = {"Modos", "Clima", "Paisaje", "Efectos", "Rendimiento"};

    private static final int BG = Color.rgb(13, 19, 32);
    private static final int SURFACE = Color.rgb(25, 35, 51);
    private static final int SURFACE_SOFT = Color.rgb(31, 43, 61);
    private static final int TEXT = Color.rgb(240, 244, 244);
    private static final int TEXT_SOFT = Color.rgb(174, 187, 194);
    private static final int ACCENT = Color.rgb(168, 213, 199);
    private static final int ACCENT_DARK = Color.rgb(36, 76, 71);

    private SharedPreferences preferences;
    private LinearLayout settingsContent;
    private LinearLayout tabButtons;
    private TextView weatherStatus;
    private int currentTab;
    private LocationManager locationManager;
    private LocationListener oneShotLocationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppPreferences.ensureDefaults(this);
        preferences = AppPreferences.get(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

        buildUi();
        WeatherClient.refreshIfNeeded(this);
        if (preferences.getBoolean(AppPreferences.LIVE_LOCATION, true)
                && hasLocationPermission()) {
            captureLocation(false);
        }
    }

    @Override
    protected void onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        removeLocationListener();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(10), dp(16), dp(12));

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape) {
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.HORIZONTAL);
            body.setGravity(Gravity.CENTER_VERTICAL);
            body.setPadding(0, dp(8), 0, 0);

            FrameLayout previewCard = buildPreviewCard();
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 0.46f);
            previewParams.setMargins(0, 0, dp(12), 0);
            body.addView(previewCard, previewParams);

            body.addView(buildControlsColumn(), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 0.54f));
            root.addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            FrameLayout previewCard = buildPreviewCard();
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(285));
            previewParams.setMargins(0, dp(8), 0, dp(10));
            root.addView(previewCard, previewParams);
            root.addView(buildControlsColumn(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        setContentView(root);
        selectTab(currentTab);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(4), dp(2), dp(4));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("AetherScape", 24, TEXT, true);
        TextView subtitle = text("Live wallpaper climático · beta 0.9", 12, TEXT_SOFT, false);
        titleBox.addView(title);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView beta = text("BETA", 11, ACCENT, true);
        beta.setGravity(Gravity.CENTER);
        beta.setPadding(dp(12), dp(7), dp(12), dp(7));
        beta.setBackground(roundRect(Color.argb(70, 84, 142, 129), dp(18), Color.argb(110, 168, 213, 199), 1));
        header.addView(beta);
        return header;
    }

    private FrameLayout buildPreviewCard() {
        FrameLayout card = new FrameLayout(this);
        card.setClipToOutline(true);
        card.setBackground(roundRect(SURFACE, dp(24), Color.argb(60, 255, 255, 255), 1));

        ScenePreviewView preview = new ScenePreviewView(this);
        card.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView hint = text("Vista en vivo · toca el paisaje para activar luces y partículas", 11, Color.argb(210, 235, 239, 237), false);
        hint.setPadding(dp(12), dp(6), dp(12), dp(6));
        hint.setGravity(Gravity.CENTER);
        hint.setBackground(roundRect(Color.argb(95, 9, 15, 24), dp(14), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintParams.setMargins(dp(12), dp(12), dp(12), dp(12));
        card.addView(hint, hintParams);
        return card;
    }

    private LinearLayout buildControlsColumn() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        Button apply = button("Aplicar como fondo de pantalla", true);
        apply.setOnClickListener(v -> applyWallpaper());
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        applyParams.setMargins(0, 0, 0, dp(8));
        column.addView(apply, applyParams);

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabButtons = new LinearLayout(this);
        tabButtons.setOrientation(LinearLayout.HORIZONTAL);
        tabButtons.setPadding(0, 0, dp(4), 0);
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            Button tab = button(TABS[i], false);
            tab.setAllCaps(false);
            tab.setTextSize(12f);
            tab.setOnClickListener(v -> selectTab(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            params.setMargins(0, 0, dp(7), 0);
            tabButtons.addView(tab, params);
        }
        tabScroll.addView(tabButtons);
        column.addView(tabScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(8), 0, dp(20));
        settingsContent = new LinearLayout(this);
        settingsContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(settingsContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return column;
    }

    private void selectTab(int index) {
        currentTab = Math.max(0, Math.min(TABS.length - 1, index));
        if (tabButtons != null) {
            for (int i = 0; i < tabButtons.getChildCount(); i++) {
                View child = tabButtons.getChildAt(i);
                boolean selected = i == currentTab;
                child.setBackground(roundRect(selected ? ACCENT_DARK : SURFACE_SOFT,
                        dp(16), selected ? Color.argb(160, 168, 213, 199) : Color.argb(45, 255, 255, 255), 1));
                if (child instanceof Button) {
                    ((Button) child).setTextColor(selected ? ACCENT : TEXT_SOFT);
                }
            }
        }
        rebuildSettings();
    }

    private void rebuildSettings() {
        if (settingsContent == null) return;
        settingsContent.removeAllViews();
        weatherStatus = null;
        switch (currentTab) {
            case 0: buildModesTab(); break;
            case 1: buildWeatherTab(); break;
            case 2: buildLandscapeTab(); break;
            case 3: buildEffectsTab(); break;
            default: buildPerformanceTab(); break;
        }
    }

    private void buildModesTab() {
        LinearLayout statusCard = card();
        weatherStatus = text("", 13, TEXT, false);
        weatherStatus.setLineSpacing(0f, 1.18f);
        statusCard.addView(weatherStatus);
        settingsContent.addView(statusCard, cardParams());
        updateWeatherStatus();

        addSwitch(settingsContent, "Hora real", "Sincroniza amanecer, mediodía, atardecer y noche con el teléfono.",
                AppPreferences.LIVE_TIME, true, null);
        addSeek(settingsContent, "Hora de prueba", "Solo se usa al desactivar Hora real.",
                AppPreferences.MANUAL_HOUR, 18, 23, value -> String.format(Locale.getDefault(), "%02d:00", value));
        addSwitch(settingsContent, "Ubicación en vivo", "Guarda la última coordenada disponible para consultar el clima.",
                AppPreferences.LIVE_LOCATION, true, enabled -> {
                    if (enabled) requestOrCaptureLocation();
                });
        addSwitch(settingsContent, "Clima dinámico", "Usa el proveedor meteorológico seleccionado y mezcla condiciones actuales con el pronóstico cercano.",
                AppPreferences.DYNAMIC_WEATHER, true, enabled -> {
                    if (enabled) WeatherClient.refreshIfNeeded(this);
                });
        addSwitch(settingsContent, "Anticipar el pronóstico", "Oscurece el cielo, aumenta el viento y prepara la lluvia gradualmente.",
                AppPreferences.FORECAST_BLEND, true, null);
        addSpinner(settingsContent, "Frecuencia de actualización", "Android puede aplazar tareas; al mostrarse el fondo también se comprueba el caché.",
                AppPreferences.WEATHER_UPDATE_MIN,
                new String[]{"15 minutos", "30 minutos", "60 minutos", "120 minutos"},
                new int[]{15, 30, 60, 120}, 30);
    }

    private void buildWeatherTab() {
        LinearLayout statusCard = card();
        weatherStatus = text("", 13, TEXT, false);
        statusCard.addView(weatherStatus);
        settingsContent.addView(statusCard, cardParams());
        updateWeatherStatus();

        addStringSpinner(settingsContent, "Proveedor del clima", "Open-Meteo no necesita clave. Si Google te falla, cambia aquí el servicio.",
                AppPreferences.WEATHER_PROVIDER,
                new String[]{"Open-Meteo (sin clave)", "Google Weather API", "OpenWeatherMap", "WeatherAPI.com"},
                new String[]{"OPEN_METEO", "GOOGLE", "OPENWEATHERMAP", "WEATHERAPI"}, "OPEN_METEO");

        LinearLayout apiCard = card();
        apiCard.addView(text("Clave del servicio", 16, TEXT, true));
        apiCard.addView(text("Se guarda solo en este dispositivo. Open-Meteo funciona sin clave; los demás proveedores sí la requieren.",
                12, TEXT_SOFT, false));
        EditText keyInput = input("Clave / token", preferences.getString(AppPreferences.API_KEY, ""));
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        keyParams.setMargins(0, dp(10), 0, dp(8));
        apiCard.addView(keyInput, keyParams);
        Button saveKey = button("Guardar clave", false);
        saveKey.setOnClickListener(v -> {
            preferences.edit().putString(AppPreferences.API_KEY, keyInput.getText().toString().trim()).apply();
            toast("Clave guardada localmente");
            updateWeatherStatus();
        });
        apiCard.addView(saveKey, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        settingsContent.addView(apiCard, cardParams());

        LinearLayout locationCard = card();
        locationCard.addView(text("Ubicación", 16, TEXT, true));
        LinearLayout coords = new LinearLayout(this);
        coords.setOrientation(LinearLayout.HORIZONTAL);
        EditText lat = input("Latitud", preferences.getString(AppPreferences.LATITUDE, "-27.1004"));
        EditText lon = input("Longitud", preferences.getString(AppPreferences.LONGITUDE, "-52.6152"));
        lat.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        lon.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        LinearLayout.LayoutParams field = new LinearLayout.LayoutParams(0, dp(48), 1f);
        field.setMargins(0, dp(10), dp(6), dp(8));
        coords.addView(lat, field);
        LinearLayout.LayoutParams field2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        field2.setMargins(dp(6), dp(10), 0, dp(8));
        coords.addView(lon, field2);
        locationCard.addView(coords);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button useLocation = button("Usar mi ubicación", false);
        useLocation.setOnClickListener(v -> requestOrCaptureLocation());
        Button saveCoords = button("Guardar coordenadas", false);
        saveCoords.setOnClickListener(v -> {
            double latitude = AppPreferences.parseDouble(lat.getText().toString(), Double.NaN);
            double longitude = AppPreferences.parseDouble(lon.getText().toString(), Double.NaN);
            if (Double.isNaN(latitude) || Double.isNaN(longitude)
                    || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                toast("Coordenadas inválidas");
                return;
            }
            preferences.edit()
                    .putString(AppPreferences.LATITUDE, String.format(Locale.US, "%.6f", latitude))
                    .putString(AppPreferences.LONGITUDE, String.format(Locale.US, "%.6f", longitude))
                    .apply();
            toast("Coordenadas guardadas");
            updateWeatherStatus();
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(44), 1f);
        half.setMargins(0, 0, dp(5), 0);
        actions.addView(useLocation, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        half2.setMargins(dp(5), 0, 0, 0);
        actions.addView(saveCoords, half2);
        locationCard.addView(actions);
        settingsContent.addView(locationCard, cardParams());

        addStringSpinner(settingsContent, "Clima visual", "AUTO conserva los datos reales; los demás modos sirven para probar la escena.",
                AppPreferences.WEATHER_OVERRIDE,
                new String[]{"Automático", "Despejado", "Nublado", "Lluvia", "Tormenta", "Niebla", "Nieve", "Viento"},
                new String[]{"AUTO", "CLEAR", "CLOUDY", "RAIN", "STORM", "FOG", "SNOW", "WIND"}, "AUTO");
        addSeek(settingsContent, "Nubes adicionales", "Ajuste artístico sumado a la nubosidad real.",
                AppPreferences.CLOUD_BIAS, 0, 100, value -> value + "%");
        addSeek(settingsContent, "Precipitación adicional", "Permite intensificar lluvia o nieve para pruebas.",
                AppPreferences.RAIN_BIAS, 0, 100, value -> value + "%");
        addSeek(settingsContent, "Fuerza del viento", "Afecta nubes, lluvia, árboles y hojas de otoño.",
                AppPreferences.WIND_BIAS, 0, 100, value -> value + "%");
        addSeek(settingsContent, "Niebla ambiental", "Reduce la profundidad y suaviza montañas lejanas.",
                AppPreferences.FOG_BIAS, 0, 100, value -> value + "%");

        Button refresh = button("Actualizar clima ahora", true);
        refresh.setOnClickListener(v -> {
            updateWeatherStatus("Actualizando…");
            WeatherClient.refresh(this, (success, message) -> {
                toast(message);
                updateWeatherStatus();
            });
        });
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        refreshParams.setMargins(0, dp(4), 0, dp(12));
        settingsContent.addView(refresh, refreshParams);

        TextView attribution = text("Servicios compatibles: Open-Meteo, Google Weather API, OpenWeatherMap y WeatherAPI.com.",
                11, TEXT_SOFT, false);
        attribution.setGravity(Gravity.CENTER);
        attribution.setPadding(dp(8), dp(8), dp(8), dp(16));
        settingsContent.addView(attribution);
    }

    private void buildLandscapeTab() {
        addStringSpinner(settingsContent, "Estación", "AUTO detecta el hemisferio con la latitud guardada.",
                AppPreferences.SEASON_MODE,
                new String[]{"Automática", "Primavera", "Verano", "Otoño", "Invierno"},
                new String[]{"AUTO", "SPRING", "SUMMER", "AUTUMN", "WINTER"}, "AUTO");
        addSwitch(settingsContent, "Nieve en las cumbres", "Solo aparece en invierno, con frío o cuando el clima indica nieve.",
                AppPreferences.SNOW_CAPS, true, null);
        addSeek(settingsContent, "Densidad de árboles", "Alterna claros, colinas abiertas y bosques más profundos.",
                AppPreferences.TREE_DENSITY, 62, 100, value -> value + "%");
        addSeek(settingsContent, "Variedad del recorrido", "Controla la aparición de ruinas, campamentos, zonas alpinas y claros.",
                AppPreferences.SCENE_VARIETY, 72, 100, value -> value + "%");
        addSeek(settingsContent, "Velocidad del paisaje", "Desplazamiento continuo sin usar un vídeo en bucle.",
                AppPreferences.SCROLL_SPEED, 32, 100, value -> value + "%");

        LinearLayout note = card();
        note.addView(text("Cambios estacionales incluidos", 15, TEXT, true));
        note.addView(text("Primavera: verdes frescos y ambiente húmedo.\nVerano: tonos vivos y más luciérnagas.\nOtoño: follaje cobre, hojas al viento y luz cálida.\nInvierno: árboles desnudos, paleta fría y nieve condicional.",
                12, TEXT_SOFT, false));
        settingsContent.addView(note, cardParams());
    }

    private void buildEffectsTab() {
        addSwitch(settingsContent, "Estrellas", "Densidad y brillo adaptados a la noche y la nubosidad.",
                AppPreferences.SHOW_STARS, true, null);
        addSwitch(settingsContent, "Iluminación atmosférica", "Halos suaves para sol, luna, fogatas, estrellas y luciérnagas.",
                AppPreferences.SHOW_GLOW, true, null);
        addSwitch(settingsContent, "Luciérnagas", "Aparecen principalmente en noches secas y tranquilas.",
                AppPreferences.SHOW_FIREFLIES, true, null);
        addSwitch(settingsContent, "Fogatas y campamentos", "Puntos cálidos de luz integrados al recorrido procedural.",
                AppPreferences.SHOW_CAMPFIRES, true, null);
        addSwitch(settingsContent, "Relámpagos", "Destellos ocasionales cuando la probabilidad de tormenta es alta.",
                AppPreferences.SHOW_LIGHTNING, true, null);
        addSwitch(settingsContent, "Parallax", "Responde a páginas del launcher y al gesto dentro de la vista previa.",
                AppPreferences.PARALLAX, true, null);
        addSeek(settingsContent, "Intensidad de efectos", "Partículas, resplandores, estrellas, lluvia, nieve y niebla.",
                AppPreferences.EFFECT_INTENSITY, 70, 100, value -> value + "%");
        addSeek(settingsContent, "Intensidad de movimiento", "Movimiento de paisaje, nubes, árboles y partículas.",
                AppPreferences.MOTION_INTENSITY, 55, 100, value -> value + "%");
    }

    private void buildPerformanceTab() {
        addSpinner(settingsContent, "Límite máximo de fotogramas", "El motor puede bajar temporalmente la frecuencia cuando la escena está tranquila.",
                AppPreferences.TARGET_FPS,
                new String[]{"15 FPS", "30 FPS", "60 FPS"},
                new int[]{15, 30, 60}, 30);
        addSwitch(settingsContent, "Optimización adaptativa", "Reduce trabajo invisible y ajusta los FPS según lluvia, viento, interacción y movimiento, sin reducir la resolución final.",
                AppPreferences.ADAPTIVE_RENDERING, true, null);
        addSwitch(settingsContent, "Vista previa eficiente", "La miniatura de la aplicación usa un ritmo reducido cuando no la estás tocando; el fondo aplicado conserva su calidad.",
                AppPreferences.PREVIEW_ECO_MODE, true, null);
        addSwitch(settingsContent, "Ahorro de batería", "Fuerza 15 FPS y mantiene el motor detenido cuando el fondo no es visible.",
                AppPreferences.BATTERY_SAVER, false, null);

        LinearLayout info = card();
        info.addView(text("Motor optimizado beta 0.9", 16, TEXT, true));
        info.addView(text("• Capas lejanas agrupadas en una caché a resolución de pantalla\n• Texturas grandes decodificadas según su función visual\n• Sin lecturas de preferencias ni filtros nuevos en cada fotograma\n• Solo se dibujan las copias de capa realmente visibles\n• Vista previa suspendida al salir de la aplicación\n• Fondo detenido por completo cuando no es visible",
                12, TEXT_SOFT, false));
        settingsContent.addView(info, cardParams());

        Button reset = button("Restablecer configuración", false);
        reset.setOnClickListener(v -> {
            preferences.edit().clear().apply();
            AppPreferences.ensureDefaults(this);
            toast("Configuración restablecida");
            rebuildSettings();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        resetParams.setMargins(0, dp(4), 0, dp(12));
        settingsContent.addView(reset, resetParams);
    }

    private void applyWallpaper() {
        ComponentName component = new ComponentName(this, AetherWallpaperService.class);
        try {
            getPackageManager().setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            toast("Confirma AetherScape en la pantalla del sistema");
        } catch (RuntimeException directFailure) {
            try {
                Intent chooser = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
                startActivity(chooser);
                toast("Selecciona AetherScape en la lista de fondos vivos");
            } catch (RuntimeException chooserFailure) {
                toast("Este launcher no pudo abrir el selector de fondos vivos");
            }
        }
    }

    private void requestOrCaptureLocation() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        captureLocation(true);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void captureLocation(boolean showFeedback) {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            if (showFeedback) toast("Servicio de ubicación no disponible");
            return;
        }
        try {
            Location best = null;
            List<String> providers = locationManager.getProviders(true);
            for (String provider : providers) {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            }
            if (best != null) {
                saveLocation(best);
                if (showFeedback) toast("Ubicación guardada");
                WeatherClient.refreshIfNeeded(this);
                updateWeatherStatus();
                return;
            }

            String provider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    ? LocationManager.NETWORK_PROVIDER
                    : LocationManager.GPS_PROVIDER;
            oneShotLocationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    saveLocation(location);
                    removeLocationListener();
                    toast("Ubicación obtenida");
                    WeatherClient.refreshIfNeeded(MainActivity.this);
                    updateWeatherStatus();
                }

                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            };
            locationManager.requestLocationUpdates(provider, 0L, 0f, oneShotLocationListener, Looper.getMainLooper());
            if (showFeedback) toast("Buscando ubicación…");
        } catch (SecurityException | IllegalArgumentException error) {
            if (showFeedback) toast("No se pudo obtener la ubicación");
        }
    }

    private void saveLocation(Location location) {
        preferences.edit()
                .putString(AppPreferences.LATITUDE, String.format(Locale.US, "%.6f", location.getLatitude()))
                .putString(AppPreferences.LONGITUDE, String.format(Locale.US, "%.6f", location.getLongitude()))
                .apply();
    }

    private void removeLocationListener() {
        if (locationManager != null && oneShotLocationListener != null) {
            try {
                locationManager.removeUpdates(oneShotLocationListener);
            } catch (SecurityException ignored) {}
        }
        oneShotLocationListener = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (hasLocationPermission()) captureLocation(true);
            else toast("Sin permiso, puedes escribir latitud y longitud manualmente");
        }
    }

    private void updateWeatherStatus() {
        if (weatherStatus == null) return;
        WeatherSnapshot snapshot = WeatherSnapshot.fromPreferences(preferences);
        String key = preferences.getString(AppPreferences.API_KEY, "");
        String provider = preferences.getString(AppPreferences.WEATHER_PROVIDER, "OPEN_METEO");
        String location = String.format(Locale.getDefault(), "%.4f, %.4f",
                AppPreferences.latitude(preferences), AppPreferences.longitude(preferences));
        String updated = snapshot.updatedAt <= 0L
                ? "todavía sin sincronizar"
                : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(snapshot.updatedAt));
        String error = preferences.getString(AppPreferences.WEATHER_ERROR, "");
        boolean missingRequiredKey = providerRequiresKey(provider) && (key == null || key.trim().isEmpty());
        String status;
        if (missingRequiredKey) {
            status = "Proveedor configurado · añade una clave";
        } else if (snapshot.updatedAt <= 0L) {
            status = providerRequiresKey(provider) ? "Proveedor listo" : "Proveedor listo · sin clave requerida";
        } else {
            status = snapshot.description + " · " + Math.round(snapshot.temperatureC) + " °C";
        }
        String detail = "Proveedor: " + providerLabel(provider) + "\nUbicación: " + location + "\nÚltima actualización: " + updated;
        if (error != null && !error.isEmpty()) detail += "\n" + error;
        weatherStatus.setText(status + "\n" + detail);
    }

    private void updateWeatherStatus(String temporary) {
        if (weatherStatus != null) weatherStatus.setText(temporary);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (AppPreferences.WEATHER_UPDATED_AT.equals(key)
                || AppPreferences.WEATHER_ERROR.equals(key)
                || AppPreferences.API_KEY.equals(key)
                || AppPreferences.WEATHER_PROVIDER.equals(key)
                || AppPreferences.LATITUDE.equals(key)
                || AppPreferences.LONGITUDE.equals(key)) {
            updateWeatherStatus();
        }
    }

    private String providerLabel(String provider) {
        if ("GOOGLE".equals(provider)) return "Google Weather API";
        if ("OPENWEATHERMAP".equals(provider)) return "OpenWeatherMap";
        if ("WEATHERAPI".equals(provider)) return "WeatherAPI.com";
        return "Open-Meteo";
    }

    private boolean providerRequiresKey(String provider) {
        return !(provider == null || "OPEN_METEO".equals(provider));
    }

    private void addSwitch(LinearLayout parent, String title, String subtitle, String key,
                           boolean defaultValue, BooleanListener listener) {
        LinearLayout box = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, TEXT, true));
        labels.addView(text(subtitle, 11, TEXT_SOFT, false));
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(preferences.getBoolean(key, defaultValue));
        toggle.setButtonTintList(null);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(key, isChecked).apply();
            if (listener != null) listener.changed(isChecked);
        });
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(row);
        parent.addView(box, cardParams());
    }

    private void addSeek(LinearLayout parent, String title, String subtitle, String key,
                         int defaultValue, int max, ValueFormatter formatter) {
        LinearLayout box = card();
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = text(title, 15, TEXT, true);
        TextView valueView = text(formatter.format(preferences.getInt(key, defaultValue)), 12, ACCENT, true);
        valueView.setGravity(Gravity.END);
        titleRow.addView(titleView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(valueView);
        box.addView(titleRow);
        box.addView(text(subtitle, 11, TEXT_SOFT, false));

        SeekBar seek = new SeekBar(this);
        seek.setMax(max);
        seek.setProgress(Math.max(0, Math.min(max, preferences.getInt(key, defaultValue))));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueView.setText(formatter.format(progress));
                if (fromUser) preferences.edit().putInt(key, progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        seekParams.setMargins(0, dp(4), 0, 0);
        box.addView(seek, seekParams);
        parent.addView(box, cardParams());
    }

    private void addSpinner(LinearLayout parent, String title, String subtitle, String key,
                            String[] labels, int[] values, int defaultValue) {
        LinearLayout box = card();
        box.addView(text(title, 15, TEXT, true));
        box.addView(text(subtitle, 11, TEXT_SOFT, false));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new DarkSpinnerAdapter(this, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int current = preferences.getInt(key, defaultValue);
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) selected = i;
        spinner.setSelection(selected, false);
        spinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                preferences.edit().putInt(key, values[position]).apply()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(8), 0, 0);
        box.addView(spinner, params);
        parent.addView(box, cardParams());
    }

    private void addStringSpinner(LinearLayout parent, String title, String subtitle, String key,
                                  String[] labels, String[] values, String defaultValue) {
        LinearLayout box = card();
        box.addView(text(title, 15, TEXT, true));
        box.addView(text(subtitle, 11, TEXT_SOFT, false));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new DarkSpinnerAdapter(this, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String current = preferences.getString(key, defaultValue);
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
        spinner.setSelection(selected, false);
        spinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                preferences.edit().putString(key, values[position]).apply()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(8), 0, 0);
        box.addView(spinner, params);
        parent.addView(box, cardParams());
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundRect(SURFACE, dp(18), Color.argb(45, 255, 255, 255), 1));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(9));
        return params;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setLineSpacing(0f, 1.10f);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13f);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(primary ? Color.rgb(13, 27, 27) : TEXT);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(roundRect(primary ? ACCENT : SURFACE_SOFT, dp(16),
                primary ? Color.TRANSPARENT : Color.argb(55, 255, 255, 255), primary ? 0 : 1));
        return button;
    }

    private EditText input(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.rgb(125, 140, 151));
        input.setText(value == null ? "" : value);
        input.setTextColor(TEXT);
        input.setTextSize(13f);
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(roundRect(Color.rgb(19, 28, 42), dp(13), Color.argb(70, 255, 255, 255), 1));
        return input;
    }

    private GradientDrawable roundRect(int fill, int radius, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidthDp > 0) drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private interface ValueFormatter { String format(int value); }
    private interface BooleanListener { void changed(boolean value); }
    private interface PositionListener { void selected(int position); }

    private static final class DarkSpinnerAdapter extends ArrayAdapter<String> {
        DarkSpinnerAdapter(Context context, String[] values) {
            super(context, android.R.layout.simple_spinner_item, values);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            view.setTextColor(TEXT);
            view.setTextSize(13f);
            view.setPadding(12, 0, 12, 0);
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getDropDownView(position, convertView, parent);
            view.setTextColor(Color.rgb(20, 27, 34));
            view.setTextSize(13f);
            view.setPadding(18, 14, 18, 14);
            return view;
        }
    }

    private static final class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final PositionListener listener;
        SimpleItemSelectedListener(PositionListener listener) { this.listener = listener; }
        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            listener.selected(position);
        }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }
}
