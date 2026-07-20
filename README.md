# AetherScape

**AetherScape v0.4.0-beta.5** es una aplicación Android de fondo vivo climático. Esta actualización reemplaza el renderer principal basado en `Canvas` por un compositor acelerado mediante **libGDX 1.14.2 y OpenGL ES 2.0**.

![GPU renderer preview](docs/renderer-v3-gpu-preview.png)

## Novedades principales

- Paisaje dividido en capas PNG transparentes y editables.
- Cámara ortográfica en unidades virtuales, sin deformar montañas ni árboles al cambiar de orientación.
- Composición distinta por recorte: vertical muestra una zona más estrecha; horizontal muestra una zona más ancha del mismo mundo.
- Montañas lejanas, medias, principales y cercanas con profundidad atmosférica.
- Bosques, niebla de valle, nubes, colinas y objetos semiprocedurales reciclados por segmentos.
- Bloom de dos pasadas con framebuffers y shader de desenfoque.
- Mapas emisivos independientes para sol, faroles y fogatas.
- Lluvia, nieve, viento, niebla, tormentas y estaciones conectados al renderer GPU.
- Open-Meteo sin clave y proveedores opcionales con clave.

## Arquitectura

```text
MainActivity (Android nativo)
 ├─ configuración
 ├─ ubicación
 ├─ clima
 └─ vista previa artística

AetherGdxWallpaperService
 └─ AetherGdxApplication
     └─ LayeredSceneRenderer
         ├─ cámara ortográfica
         ├─ capas 2D
         ├─ segmentos reciclados
         ├─ framebuffer de escena
         ├─ framebuffer emisivo
         ├─ blur horizontal/vertical
         └─ composición final
```

El antiguo renderer `Canvas` permanece en el código únicamente como referencia y respaldo de desarrollo; el servicio registrado en el manifiesto utiliza el nuevo renderer GPU.

## Capas incluidas

```text
app/src/main/assets/aether/
 ├─ layers/
 │  ├─ stars.png
 │  ├─ clouds_far.png
 │  ├─ clouds_near.png
 │  ├─ mountains_far.png
 │  ├─ mountains_mid.png
 │  ├─ mountains_hero.png
 │  ├─ mountains_near.png
 │  ├─ fog_valley.png
 │  ├─ forest_far.png
 │  ├─ forest_mid.png
 │  └─ hill_foreground.png
 └─ objects/
    ├─ pine_*.png
    ├─ lantern.png
    ├─ lantern_emission.png
    ├─ campfire.png
    ├─ campfire_emission.png
    ├─ glow.png
    └─ noise_soft.png
```

El script `tools/generate_visual_assets.py` permite regenerar y mejorar estas capas sin modificar el motor.

## Requisitos de compilación

- Android 8.0 o superior (`minSdk 26`).
- Java 17.
- Android SDK 36 y Build Tools 36.0.0.
- Gradle 9.5.0.
- Conexión a Maven Central durante la primera compilación para descargar libGDX.

## Compilar

```bash
gradle --no-daemon --stacktrace :app:assembleDebug
```

APK esperado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Publicar desde Termux

```bash
bash scripts/validate.sh
bash scripts/publish-termux.sh AetherScape v0.4.0-beta.5
```

Consulta [docs/TERMUX_GITHUB.md](docs/TERMUX_GITHUB.md) para el procedimiento completo.

## Clima

La pestaña **Clima** permite elegir:

- Open-Meteo, sin clave.
- Google Weather API.
- OpenWeatherMap.
- WeatherAPI.com.

Las condiciones se transforman en valores continuos de nubosidad, lluvia, nieve, viento, niebla y tormenta. El renderer interpola esos valores para evitar cambios bruscos.

## Estado de esta beta

Esta es la primera migración funcional hacia el motor GPU. El objetivo de las siguientes versiones será mejorar las ilustraciones de cada capa, añadir más segmentos artísticos y afinar el consumo de batería en dispositivos reales.
