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

echo "AetherScape source package looks complete."
