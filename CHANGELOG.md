## v0.7.1-beta.10

- Corrige el empaquetado: el keystore beta ya no se pierde por la regla `*.jks` de `.gitignore`.
- El script de publicación fuerza la inclusión del mismo keystore de beta 0.7.
- La validación comprueba el SHA-256 del keystore para impedir cambios accidentales de firma.
- Mantiene `dev.andres.aetherscape.beta` y la misma firma para actualizaciones sobre la app anterior.

## v0.7.0-beta.9

- Reorganiza todavía más la composición del paisaje para despejar la montaña central y evitar árboles desordenados o mal ubicados.
- Mejora la nitidez del fondo aplicado cargando todas las capas principales a resolución completa.
- Añade iluminación atmosférica reforzada para sol y luna, además de sombras suaves proyectadas en árboles y objetos.
- Refina la lectura visual del terreno con un baño de luz sobre la colina frontal.
- Introduce una firma fija del APK (`dev.andres.aetherscape.beta`) para que, desde esta versión en adelante, las próximas betas puedan instalarse encima sin desinstalar la aplicación anterior.

## v0.6.1-beta.8

- Corrige el fallo de GitHub Actions `ModuleNotFoundError: No module named PIL`.
- La validación de PNG ahora usa únicamente la biblioteca estándar de Python.
- Verifica firma PNG, IHDR, dimensiones, CRC de chunks e IEND sin instalar Pillow.
- Mantiene intacto el renderer nativo y la composición de la beta 7.

## v0.6.0-beta.7 — Native Layer Fix

- Sustituido el servicio libGDX activo por un `WallpaperService` nativo compatible.
- Unificado el renderer de la vista previa y el fondo aplicado.
- Corregida la pantalla negra al aplicar el live wallpaper.
- Añadido alias de migración para instalaciones que tenían activo el servicio de la beta 0.5.
- Primer fotograma forzado durante la creación y cambio de superficie.
- Respaldo automático entre Canvas acelerado y Canvas convencional.
- Escena de emergencia si ocurre un fallo de renderizado.
- Árboles reorganizados mediante cinco plantillas de composición.
- Centro visual despejado para la montaña principal.
- Bosques agrupados y cordillera cercana menos puntiaguda.
- Colinas y objetos anclados a las mismas funciones de terreno.
- Interacciones táctiles, faroles, clima y parallax conservados.

## v0.5.0-beta.6

- Renderer GPU interactivo y recursos por capas.
- Vista previa animada.
- Clima multiproveedor.
