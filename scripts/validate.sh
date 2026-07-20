#!/usr/bin/env bash
set -euo pipefail

required=(
  settings.gradle
  build.gradle
  app/build.gradle
  app/src/main/AndroidManifest.xml
  app/src/main/java/dev/andres/aetherscape/MainActivity.java
  app/src/main/java/dev/andres/aetherscape/gdx/AetherGdxWallpaperService.java
  app/src/main/java/dev/andres/aetherscape/gdx/AetherGdxApplication.java
  app/src/main/java/dev/andres/aetherscape/gdx/LayeredSceneRenderer.java
  app/src/main/java/dev/andres/aetherscape/weather/WeatherClient.java
  app/src/main/assets/aether/layers/mountains_hero.png
  app/src/main/assets/aether/layers/fog_valley.png
  app/src/main/assets/aether/layers/snow_caps.png
  app/src/main/assets/aether/layers/hill_mid.png
  app/src/main/assets/aether/layers/hill_front.png
  tools/generate_visual_assets.py
  app/src/main/assets/aether/objects/lantern_emission.png
  app/src/main/assets/aether/objects/sun_disc.png
  app/src/main/assets/aether/objects/moon_crescent.png
  app/src/main/res/xml/wallpaper.xml
)

for file in "${required[@]}"; do
  [[ -f "$file" ]] || { echo "Missing required file: $file" >&2; exit 1; }
done

grep -q 'android.service.wallpaper.WallpaperService' app/src/main/AndroidManifest.xml
grep -q 'AetherGdxWallpaperService' app/src/main/AndroidManifest.xml
grep -q 'AetherGdxWallpaperService.class' app/src/main/java/dev/andres/aetherscape/MainActivity.java
grep -q 'compileSdk 36' app/build.gradle
grep -q "versionName '0.5.0-beta.6'" app/build.gradle
grep -q 'gdx-backend-android' app/build.gradle
grep -q 'natives-arm64-v8a' app/build.gradle
grep -q 'AndroidLiveWallpaperService' app/src/main/java/dev/andres/aetherscape/gdx/AetherGdxWallpaperService.java
grep -q 'FrameBuffer' app/src/main/java/dev/andres/aetherscape/gdx/LayeredSceneRenderer.java
grep -q 'BLUR_FRAGMENT' app/src/main/java/dev/andres/aetherscape/gdx/LayeredSceneRenderer.java
grep -q 'WORLD_HEIGHT' app/src/main/java/dev/andres/aetherscape/gdx/LayeredSceneRenderer.java
grep -q 'setContinuousRendering(true)' app/src/main/java/dev/andres/aetherscape/gdx/AetherGdxApplication.java
grep -q 'postInvalidateOnAnimation' app/src/main/java/dev/andres/aetherscape/ui/ScenePreviewView.java
grep -q 'getTouchEventsForLiveWallpaper = true' app/src/main/java/dev/andres/aetherscape/gdx/AetherGdxWallpaperService.java
grep -q 'Open-Meteo' app/src/main/java/dev/andres/aetherscape/MainActivity.java

echo "AetherScape v0.5 interactive GPU package looks complete."
