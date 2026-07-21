# Renderer optimization — beta 0.9

## Problemas corregidos

1. Dos renderers podían decodificar simultáneamente cientos de megabytes de PNG.
2. La vista previa solicitaba un nuevo fotograma al ritmo del monitor, aunque su contenido apenas cambiara.
3. Cada fotograma consultaba todas las preferencias y creaba filtros de color nuevos.
4. Cada capa dibujaba cuatro mosaicos aunque solo uno o dos fueran visibles.
5. La carga de recursos sucedía en el hilo principal.

## Arquitectura nueva

- `SceneBitmapPool`: recursos inmutables con conteo de referencias.
- Perfil wallpaper: capas generales `inSampleSize=2`, montaña principal `inSampleSize=1`.
- Perfil preview: capas generales `inSampleSize=8`, montaña principal `inSampleSize=4`.
- Caché de fondo: cielo y capas lejanas se precomponen a la resolución exacta de la superficie.
- Render foreground: colinas cercanas, árboles, luces y clima siguen siendo dinámicos.
- Hilo `AetherNativeLayerEngine`: carga y dibujo del wallpaper fuera del hilo principal.
- Hilo `AetherPreviewAssetLoader`: carga de la miniatura fuera del hilo de interfaz.
- Pacing adaptativo: 15/24/30/60 FPS según contexto y ajustes.

## Principio de calidad

La resolución de la superficie final no se reduce. El motor elimina trabajo redundante y utiliza el nivel de fuente necesario para cada plano. Los elementos protagonistas mantienen más detalle que niebla, estrellas o montañas lejanas.
