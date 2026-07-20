# Renderer V5 nativo

## Objetivo

Corregir el fondo negro al aplicarlo y ordenar la composición del paisaje.

## Motor

- `WallpaperService` nativo.
- `SurfaceHolder.lockHardwareCanvas()` con respaldo `lockCanvas()`.
- Un solo `LayeredCanvasRenderer` para preview y wallpaper.
- Altura virtual fija de 1000 unidades.

## Orden de capas

1. Cielo.
2. Estrellas.
3. Sol o luna.
4. Nubes lejanas.
5. Montañas lejanas.
6. Niebla de valle.
7. Montañas medias.
8. Montaña principal.
9. Nieve condicional.
10. Cordillera cercana.
11. Bosque lejano.
12. Bosque medio.
13. Colina media.
14. Árboles posteriores agrupados.
15. Niebla delantera.
16. Colina frontal.
17. Árboles, faroles y fogatas.
18. Partículas y atmósfera.

## Plantillas de árboles

Los objetos se generan por segmentos de 960 unidades. Cada segmento elige una composición estable:

- Vista abierta de montaña.
- Entrada de bosque.
- Sendero con faroles.
- Claro tranquilo.
- Cresta dispersa.

Las plantillas concentran árboles en los extremos y reservan el centro para la montaña principal.
