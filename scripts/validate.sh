#!/usr/bin/env bash
set -euo pipefail

required=(
  settings.gradle
  build.gradle
  app/build.gradle
  app/src/main/AndroidManifest.xml
  app/src/main/java/dev/andres/aetherscape/MainActivity.java
  app/src/main/java/dev/andres/aetherscape/wallpaper/AetherWallpaperService.java
  app/src/main/java/dev/andres/aetherscape/gdx/AetherGdxWallpaperService.java
  app/src/main/java/dev/andres/aetherscape/render/LayeredCanvasRenderer.java
  app/src/main/java/dev/andres/aetherscape/render/SceneState.java
  app/src/main/java/dev/andres/aetherscape/ui/ScenePreviewView.java
  app/src/main/java/dev/andres/aetherscape/weather/WeatherClient.java
  app/src/main/assets/aether/layers/mountains_hero.png
  app/src/main/assets/aether/layers/mountains_near.png
  app/src/main/assets/aether/layers/forest_far.png
  app/src/main/assets/aether/layers/forest_mid.png
  app/src/main/assets/aether/layers/hill_mid.png
  app/src/main/assets/aether/layers/hill_front.png
  app/src/main/assets/aether/objects/lantern.png
  app/src/main/res/xml/wallpaper.xml
  app/src/main/res/drawable/wallpaper_thumb.png
  tools/generate_visual_assets.py
  tools/render_v06_previews.py
)

for file in "${required[@]}"; do
  [[ -f "$file" ]] || { echo "Missing required file: $file" >&2; exit 1; }
done

grep -q 'android.service.wallpaper.WallpaperService' app/src/main/AndroidManifest.xml
grep -q 'wallpaper.AetherWallpaperService' app/src/main/AndroidManifest.xml
grep -q 'gdx.AetherGdxWallpaperService' app/src/main/AndroidManifest.xml
grep -q 'AetherWallpaperService.class' app/src/main/java/dev/andres/aetherscape/MainActivity.java
grep -q "versionName '0.6.0-beta.7'" app/build.gradle
grep -q 'LayeredCanvasRenderer' app/src/main/java/dev/andres/aetherscape/wallpaper/AetherWallpaperService.java
grep -q 'lockHardwareCanvas' app/src/main/java/dev/andres/aetherscape/wallpaper/AetherWallpaperService.java
grep -q 'drawEmergencyFrame' app/src/main/java/dev/andres/aetherscape/wallpaper/AetherWallpaperService.java
grep -q 'SEGMENT_WIDTH' app/src/main/java/dev/andres/aetherscape/render/LayeredCanvasRenderer.java
grep -q 'open mountain vista' app/src/main/java/dev/andres/aetherscape/render/LayeredCanvasRenderer.java
grep -q 'postInvalidateOnAnimation' app/src/main/java/dev/andres/aetherscape/ui/ScenePreviewView.java
grep -q 'Open-Meteo' app/src/main/java/dev/andres/aetherscape/MainActivity.java

python - <<'PY'
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image
ET.parse('app/src/main/AndroidManifest.xml')
ET.parse('app/src/main/res/xml/wallpaper.xml')
for path in Path('app/src/main/assets/aether').rglob('*.png'):
    with Image.open(path) as image:
        image.verify()
print('XML and PNG assets verified.')
PY

echo "AetherScape v0.6 native layered package looks complete."
