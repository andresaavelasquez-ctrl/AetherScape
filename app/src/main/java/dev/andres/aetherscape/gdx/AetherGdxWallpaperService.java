package dev.andres.aetherscape.gdx;

import dev.andres.aetherscape.wallpaper.AetherWallpaperService;

/**
 * Migration alias for users upgrading from v0.5.
 *
 * The previous release registered this component name. Keeping it declared
 * lets Android reconnect an already-selected wallpaper after updating the APK,
 * but the implementation now uses the stable native layered engine.
 */
public final class AetherGdxWallpaperService extends AetherWallpaperService {
}
