# AetherScape

![Concept preview](docs/concept-preview.png)

**AetherScape** es una base beta de fondo de pantalla vivo para Android. Genera un paisaje 2D minimalista, cozy y continuo que cambia con la hora, el clima real, el pronóstico cercano y la estación del año.

## Estado de esta beta

La versión `0.1.0-beta.1` ya contiene una aplicación Android funcional y un `WallpaperService` nativo. No utiliza un vídeo en bucle: dibuja cada cuadro con un motor procedural ligero basado en Canvas.

Incluye:

- Vista previa animada dentro de la aplicación.
- Aplicación directa como live wallpaper.
- Compatibilidad vertical y horizontal.
- Amanecer, mañana, mediodía, tarde, atardecer, noche y madrugada.
- Montañas, colinas, bosques, ruinas, tiendas y fogatas conectados en un recorrido continuo.
- Estaciones automáticas según fecha y hemisferio.
- Primavera, verano, otoño e invierno seleccionables manualmente.
- Cumbres nevadas solo en invierno, con temperaturas bajas o con clima de nieve.
- Nubes, lluvia, nieve, niebla, viento, tormenta, relámpagos y hojas otoñales.
- Iluminación ambiental para sol, luna, estrellas, fogatas y luciérnagas.
- Parallax mediante desplazamiento del launcher y gesto en la vista previa.
- Ajustes de 15, 30 o 60 FPS y modo de ahorro de batería.
- Integración beta con Google Weather API.
- Consulta de condiciones actuales y de las próximas seis horas.
- GitHub Actions para compilar APK y publicar Releases por tag.

## Requisitos

- Android 8.0 o superior (`minSdk 26`).
- Java 17 para compilar.
- Android SDK 36 y Build Tools 36.0.0.
- Gradle 9.5.0.
- Una clave de Google Weather API para clima real.

## Compilar

El repositorio no incluye binarios de Gradle. GitHub Actions instala la versión requerida automáticamente.

```bash
gradle --no-daemon :app:assembleDebug
```

APK esperado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Publicar desde Termux

Consulta [docs/TERMUX_GITHUB.md](docs/TERMUX_GITHUB.md).

Flujo rápido:

```bash
bash scripts/validate.sh
bash scripts/publish-termux.sh AetherScape v0.1.0-beta.1
```

El tag activa `.github/workflows/release.yml`, compila el APK y crea una prerelease automáticamente.

## Activar el clima real

1. Crea o solicita una clave de Google Weather API.
2. Instala y abre AetherScape.
3. Entra en **Clima**.
4. Pega la clave y pulsa **Guardar clave**.
5. Concede ubicación o escribe latitud y longitud manualmente.
6. Pulsa **Actualizar clima ahora**.

La clave se guarda en `SharedPreferences` del dispositivo. Esto sirve para una beta personal. Antes de publicar una versión para muchos usuarios, mueve la llamada meteorológica a un backend/proxy para no distribuir una clave estándar dentro del cliente.

## Arquitectura

```text
MainActivity
 ├─ ScenePreviewView
 ├─ Preferencias y controles
 ├─ Permiso/lectura de ubicación
 └─ WeatherClient

AetherWallpaperService
 └─ SceneRenderer
      ├─ Ciclo horario
      ├─ Clima y pronóstico interpolados
      ├─ Estación y hemisferio
      ├─ Generador de segmentos
      ├─ Parallax
      ├─ Iluminación
      └─ Partículas
```

### Paquetes principales

- `prefs/AppPreferences.java`: claves, valores iniciales y coordenadas.
- `weather/WeatherClient.java`: Google Weather API y caché local.
- `render/SceneState.java`: mezcla de hora, clima, pronóstico y estación.
- `render/SceneRenderer.java`: paisaje procedural y efectos.
- `wallpaper/AetherWallpaperService.java`: ciclo de renderizado visible/invisible.
- `ui/ScenePreviewView.java`: vista previa reutilizando el mismo motor.

## Diseño meteorológico

Las condiciones no se cambian como escenas independientes. El motor normaliza valores de `0.0` a `1.0`:

```text
nubosidad
lluvia/nieve
viento
niebla
tormenta
```

Luego interpola gradualmente hacia el nuevo estado. El pronóstico de seis horas se mezcla con menor intensidad para preparar visualmente una lluvia o tormenta futura.

## Limitaciones conocidas de la beta

- El renderizador usa Canvas; una versión posterior podrá migrar a OpenGL ES para más capas y shaders.
- La ubicación se guarda como última coordenada conocida; no se mantiene un rastreo continuo.
- La actualización meteorológica se comprueba al abrir la aplicación o cuando el fondo vuelve a ser visible.
- La clave API se guarda localmente y no debe usarse así en una distribución pública masiva.
- Los horarios de amanecer y anochecer se aproximan por hora local; una siguiente versión puede usar sunrise/sunset del pronóstico diario.
- Esta entrega contiene el código fuente y automatización de compilación. El APK se genera en GitHub Actions después del primer push.

## Próxima etapa sugerida

La siguiente beta debería añadir shaders de luz y niebla, transición entre biomas con curvas de densidad, pronóstico diario para amanecer/anochecer reales, fondos de bloqueo independientes y un sistema de paquetes visuales.
