# Renderer V3 GPU

Esta beta sustituye el motor del fondo vivo por un compositor 2D acelerado mediante libGDX y OpenGL ES.

## Capas

1. Gradiente dinámico del cielo
2. Estrellas
3. Nubes lejanas
4. Montañas lejanas
5. Montañas intermedias
6. Pico principal
7. Niebla de valle
8. Montañas cercanas
9. Bosque lejano
10. Nubes cercanas
11. Bosque medio
12. Objetos semiprocedurales
13. Colina del primer plano
14. Árboles, faroles y fogatas
15. Precipitación y corrección atmosférica
16. Bloom y grano final

## Orientación

La cámara utiliza altura virtual constante y calcula el ancho visible con la relación de aspecto. Los objetos no se deforman. En horizontal se ve una zona más ancha; en vertical se recorta lateralmente.

## Bloom

Las luces se dibujan en un framebuffer emisivo a un cuarto de resolución, se desenfocan horizontal y verticalmente y se componen de manera aditiva sobre la escena.

## Edición futura

El script `tools/generate_visual_assets.py` regenera todas las capas PNG y la previsualización. Esto permite mejorar el arte sin reescribir el motor.
