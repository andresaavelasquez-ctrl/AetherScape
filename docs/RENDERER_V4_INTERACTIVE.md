# Renderer V4 interactivo (v0.5.0-beta.6)

Esta revisión corrige la vista previa estática y la pérdida de animación observada en algunos launchers.

## Cambios estructurales

- `MainActivity` integra una vista animada ligera que usa los mismos recursos, evitando crear dos contextos libGDX dentro del mismo proceso.
- `AetherGdxApplication` fuerza `setContinuousRendering(true)`.
- El servicio acepta eventos táctiles del fondo.
- La cámara usa siempre 1000 unidades verticales.
- Los objetos se posicionan en coordenadas del mundo y consultan la misma función de terreno usada para generar las colinas.
- Las capas faltantes producen texturas transparentes de respaldo en vez de detener el renderer.
- Si la GPU no puede reservar los FrameBuffers grandes, se reconstruyen a media resolución.

## Interacción

- Desplazamiento del launcher: parallax.
- Inclinación suave: parallax secundario.
- Toque o arrastre: ráfaga temporal, bloom más intenso y agrupación de luciérnagas.
