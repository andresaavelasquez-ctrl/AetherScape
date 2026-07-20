# Renderer 2D nativo v2

## Problemas corregidos

- Las montañas de la beta anterior escalaban parte de su altura con el alto total de la pantalla, creando agujas demasiado largas en retrato.
- Los pinos de encuadre estaban dibujados en coordenadas fijas de pantalla y por eso parecían estampados.
- Algunos launchers reutilizan el búfer del wallpaper; ahora se limpia mediante `PorterDuff.Mode.SRC` antes de cada cuadro.

## Nueva cámara

Todos los tamaños físicos usan `min(width, height) / 1000` como unidad lógica. El alto de una montaña depende principalmente del lado corto, mientras que la posición vertical sigue siendo un porcentaje del alto disponible. En horizontal se aplican proporciones específicas a los árboles protagonistas.

## Capas

1. Cielo de tres tonos.
2. Estrellas y cuerpo celeste con bloom radial.
3. Nubes por profundidad.
4. Cuatro bandas de montañas con facetas.
5. Niebla de valle.
6. Bosques lejanos.
7. Colinas y suelo.
8. Árboles normales y árboles protagonistas anclados al mundo.
9. Faroles, banderines, estructuras y fogatas.
10. Lluvia, nieve, hojas, luciérnagas y relámpagos.
11. Niebla final y viñeta.

## Motor

El proyecto no usa JavaScript. Sigue siendo una aplicación Android nativa. El servicio intenta `lockHardwareCanvas()` y, si el launcher no lo admite, vuelve automáticamente a `lockCanvas()`.
