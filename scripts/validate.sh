#!/usr/bin/env bash
set -euo pipefail

required=(
  settings.gradle
  build.gradle
  app/build.gradle
  app/src/main/AndroidManifest.xml
  app/src/main/java/dev/andres/aetherscape/MainActivity.java
  app/src/main/java/dev/andres/aetherscape/wallpaper/AetherWallpaperService.java
  app/src/main/java/dev/andres/aetherscape/render/SceneRenderer.java
  app/src/main/java/dev/andres/aetherscape/weather/WeatherClient.java
  app/src/main/res/xml/wallpaper.xml
)

for file in "${required[@]}"; do
  [[ -f "$file" ]] || { echo "Missing required file: $file" >&2; exit 1; }
done

grep -q 'android.service.wallpaper.WallpaperService' app/src/main/AndroidManifest.xml
grep -q 'compileSdk 36' app/build.gradle
grep -q 'versionName' app/build.gradle
grep -q 'private static float lerp' app/src/main/java/dev/andres/aetherscape/render/SceneRenderer.java

grep -q "versionName '0.3.0-beta.4'" app/build.gradle
grep -q 'lockHardwareCanvas' app/src/main/java/dev/andres/aetherscape/wallpaper/AetherWallpaperService.java
grep -q 'Logical world units' app/src/main/java/dev/andres/aetherscape/render/SceneRenderer.java
grep -q 'drawHeroTrees' app/src/main/java/dev/andres/aetherscape/render/SceneRenderer.java
! grep -q 'drawFramingPines' app/src/main/java/dev/andres/aetherscape/render/SceneRenderer.java

echo "AetherScape v0.3 renderer package looks complete."
