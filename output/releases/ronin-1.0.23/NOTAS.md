# Ronin 1.0.23 para Windows

## Portadas personalizadas

La ficha de cada manga permite elegir una imagen PNG o JPEG de tu equipo,
cambiarla, mostrarla completa o rellenando el espacio y restaurar la portada
original. Los controles aparecen debajo de la imagen y siguen el idioma elegido.

Ronin guarda una copia independiente en su carpeta de datos `custom-covers`.
La selección se conserva al reiniciar y aparece también en biblioteca, búsquedas,
categorías, historial, actualizaciones y descargas. Las actualizaciones de la
fuente no reemplazan la portada personalizada. Restaurar la original elimina
solamente la copia personalizada; no modifica el manga ni el archivo elegido.

Se aceptan imágenes de hasta 16 MB y 32 millones de píxeles. Las imágenes grandes
se reducen conservando sus proporciones y se guardan como PNG, con un máximo de
1920 píxeles en su lado mayor. Un archivo inválido conserva la portada anterior.

Las portadas personalizadas se guardan en este dispositivo; las copias `.tachibk`
actuales no incluyen estos archivos de imagen.

## Validación

Pruebas de persistencia, aislamiento por manga y fuente, reemplazo, restauración,
archivo inválido y normalización de imágenes. Pruebas de escritorio y revisión
de formato. El instalador conserva el perfil de las versiones anteriores.
Cierra Ronin antes de instalar.
