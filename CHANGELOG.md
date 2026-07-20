## v0.4.0-beta.5 — GPU Layer Composer

- Migración del live wallpaper desde Canvas a libGDX/OpenGL ES 2.0.
- Cámara ortográfica en unidades virtuales: vertical y horizontal muestran el mismo mundo sin estirar montañas.
- Paisaje reconstruido como capas artísticas transparentes independientes.
- Montañas lejanas, medias, principales y cercanas con perspectiva atmosférica.
- Bosques, niebla de valle, colinas y objetos del mundo reciclados por segmentos.
- Bloom real de dos pasadas mediante FrameBuffer y shader gaussiano.
- Mapas emisivos separados para sol, faroles y fogatas.
- Lluvia, nieve, tormenta, viento, clima y estaciones conectados al nuevo renderer.
- Vista previa estática alineada con la nueva dirección visual.
- Open-Meteo continúa disponible sin clave, junto a los otros proveedores.

## v0.3.0-beta.4

- Renderer 2D nativo rehecho con unidades lógicas independientes de la resolución.
- Corrección de proporciones en modo vertical y horizontal: las montañas ya no usan la altura completa como escala.
- Eliminación de árboles decorativos fijos que parecían estampados sobre la pantalla.
- Todos los árboles grandes, faroles, estructuras y fogatas quedan anclados al mundo desplazable.
- Nuevo borrado completo del búfer para evitar rastros o píxeles fantasma entre fotogramas.
- Canvas acelerado por hardware con respaldo automático al Canvas convencional.
- Montañas más anchas, con hombros, facetas, niebla de valle y cumbres condicionales.
- Pinos más detallados y asimétricos, con tamaños separados para paisaje y retrato.
- Bloom mediante gradientes radiales para sol, luna, faroles y fogatas.
- Mejor adaptación del parallax y de la cámara tras cambios de orientación.

## v0.2.0-beta.3

- Corrección de compilación: se añadió la interpolación faltante usada por la ruta de faroles.
- Versión Android y nombres de artefacto actualizados.
- Script de publicación más robusto al recrear etiquetas.
- GitHub Actions ahora muestra stacktrace completo en fallos.

## v0.2.0-beta.2

- Mejora del estilo visual del paisaje para acercarlo a la referencia cozy/aesthetic.
- Nuevas capas atmosféricas: picos principales, pinos de encuadre y faroles con bloom suave.
- Selector de proveedor meteorológico.
- Soporte para Open-Meteo (sin clave), Google Weather API, OpenWeatherMap y WeatherAPI.com.

# Changelog

## 0.1.0-beta.1

- Primer prototipo instalable de live wallpaper nativo.
- Paisaje procedural continuo con colinas, bosques, montañas, ruinas y campamentos.
- Ciclo de amanecer, día, atardecer y noche.
- Estaciones automáticas por hemisferio y selección manual.
- Montañas nevadas solo cuando corresponde a estación, temperatura o precipitación.
- Google Weather API: condiciones actuales y mezcla de las próximas seis horas.
- Lluvia, nieve, niebla, viento, tormenta, relámpagos y hojas de otoño.
- Iluminación de sol, luna, estrellas, fogatas y luciérnagas.
- Interfaz de personalización vertical y horizontal.
- Modos de 15, 30 y 60 FPS, con pausa cuando el fondo no es visible.
- Workflows de GitHub para APK beta y Release por tag.
