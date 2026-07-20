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
  keystore/aetherscape-beta.jks
)

for file in "${required[@]}"; do
  [[ -f "$file" ]] || { echo "Missing required file: $file" >&2; exit 1; }
done

grep -q 'android.service.wallpaper.WallpaperService' app/src/main/AndroidManifest.xml
grep -q 'wallpaper.AetherWallpaperService' app/src/main/AndroidManifest.xml
grep -q 'gdx.AetherGdxWallpaperService' app/src/main/AndroidManifest.xml
grep -q 'AetherWallpaperService.class' app/src/main/java/dev/andres/aetherscape/MainActivity.java
grep -q "versionName '0.7.1-beta.10'" app/build.gradle
grep -q 'LayeredCanvasRenderer' app/src/main/java/dev/andres/aetherscape/wallpaper/AetherWallpaperService.java
grep -q 'lockHardwareCanvas' app/src/main/java/dev/andres/aetherscape/wallpaper/AetherWallpaperService.java
grep -q 'drawEmergencyFrame' app/src/main/java/dev/andres/aetherscape/wallpaper/AetherWallpaperService.java
grep -q 'SEGMENT_WIDTH' app/src/main/java/dev/andres/aetherscape/render/LayeredCanvasRenderer.java
grep -q 'open mountain vista' app/src/main/java/dev/andres/aetherscape/render/LayeredCanvasRenderer.java
grep -q 'signingConfigs' app/build.gradle
grep -q 'aetherscape-beta.jks' app/build.gradle
grep -q 'postInvalidateOnAnimation' app/src/main/java/dev/andres/aetherscape/ui/ScenePreviewView.java
grep -q 'Open-Meteo' app/src/main/java/dev/andres/aetherscape/MainActivity.java
grep -q '!keystore/aetherscape-beta.jks' .gitignore
grep -q 'git add -f keystore/aetherscape-beta.jks' scripts/publish-termux.sh

# Verify the bundled beta key is the exact continuity key from beta 0.7.
EXPECTED_KEY_SHA256="f6f4c46bc373134ef532ee2ac92e61d2f2811a8645bbdd63c70ca6b4732ad54a"
if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL_KEY_SHA256="$(sha256sum keystore/aetherscape-beta.jks | awk '{print $1}')"
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL_KEY_SHA256="$(shasum -a 256 keystore/aetherscape-beta.jks | awk '{print $1}')"
else
  echo "No se encontró una herramienta SHA-256 para verificar el keystore." >&2
  exit 1
fi
[[ "$ACTUAL_KEY_SHA256" == "$EXPECTED_KEY_SHA256" ]] || {
  echo "El keystore beta cambió. Eso rompería las actualizaciones sobre la app instalada." >&2
  exit 1
}

if command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  echo "Python no está instalado. En Termux ejecuta: pkg install python" >&2
  exit 1
fi

"$PYTHON_BIN" - <<'PY'
import struct
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def verify_png(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if len(data) < 33 or data[:8] != PNG_SIGNATURE:
        raise ValueError(f"{path}: firma PNG inválida")

    offset = 8
    saw_ihdr = False
    saw_iend = False
    width = height = 0

    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        chunk_start = offset + 8
        chunk_end = chunk_start + length
        crc_end = chunk_end + 4
        if crc_end > len(data):
            raise ValueError(f"{path}: chunk PNG truncado")

        chunk_data = data[chunk_start:chunk_end]
        stored_crc = struct.unpack(">I", data[chunk_end:crc_end])[0]
        calculated_crc = zlib.crc32(chunk_type)
        calculated_crc = zlib.crc32(chunk_data, calculated_crc) & 0xFFFFFFFF
        if stored_crc != calculated_crc:
            name = chunk_type.decode("ascii", errors="replace")
            raise ValueError(f"{path}: CRC inválido en chunk {name}")

        if chunk_type == b"IHDR":
            if saw_ihdr or length != 13:
                raise ValueError(f"{path}: IHDR inválido")
            width, height = struct.unpack(">II", chunk_data[:8])
            if width <= 0 or height <= 0:
                raise ValueError(f"{path}: dimensiones PNG inválidas")
            saw_ihdr = True
        elif chunk_type == b"IEND":
            if length != 0:
                raise ValueError(f"{path}: IEND inválido")
            saw_iend = True
            offset = crc_end
            break

        offset = crc_end

    if not saw_ihdr or not saw_iend:
        raise ValueError(f"{path}: faltan chunks IHDR/IEND")
    return width, height


ET.parse("app/src/main/AndroidManifest.xml")
ET.parse("app/src/main/res/xml/wallpaper.xml")

paths = sorted(Path("app/src/main/assets/aether").rglob("*.png"))
paths.append(Path("app/src/main/res/drawable/wallpaper_thumb.png"))
if not paths:
    raise RuntimeError("No se encontraron recursos PNG para validar")

for path in paths:
    verify_png(path)

print(f"XML válido y {len(paths)} recursos PNG verificados sin Pillow.")
PY

echo "AetherScape v0.7.1-beta.10 package looks complete."
