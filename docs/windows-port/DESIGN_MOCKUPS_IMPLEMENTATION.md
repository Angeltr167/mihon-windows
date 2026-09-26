# Mihon Windows — diseño visual propuesto

Estado: **propuesta para implementación futura**. Este documento no cambia la aplicación, no sustituye `WINDOWS_PORT_PHASE_TREE.md` y no declara terminadas las validaciones de release. Los diez mockups están en [`mockups/`](mockups/).

## Intención

Trasladar a toda la aplicación el ambiente del [mockup del lector](mockups/reader.png): manga como protagonista, interfaz oscura y silenciosa, sensación de libro abierto, blanco y negro con un acento verde salvia muy moderado. Debe sentirse como una aplicación Windows útil, no como una ilustración decorativa. Conservar la lógica actual de biblioteca, fuentes, extensiones, descargas, backup y Android; esta propuesta afecta primero a presentación e interacción.

## Sistema visual compartido

| Elemento | Dirección de diseño |
| --- | --- |
| Fondo | Grafito casi negro (`#0B1013` aproximado), con textura brumosa muy tenue solo en espacios vacíos. No detrás de textos o páginas. |
| Superficies | Paneles `#11191D`–`#182125` aproximados, bordes finos y sombras suaves. Nada de tarjetas brillantes o efectos neón. |
| Texto | Blanco marfil `#E8E9E2` para títulos y acciones; gris `#A5ABA9` para metadatos. Contraste suficiente en ambos tamaños. |
| Acento | Verde salvia pálido `#C6DEA1` aproximado para selección, progreso y acción primaria; nunca como fondo dominante. |
| Tipografía | Títulos de sección con serif editorial sobria; controles, listas y datos en sans-serif legible. Usar recursos i18n existentes cuando correspondan. |
| Navegación | Barra lateral persistente y compacta; selección inequívoca mediante acento y etiqueta, no solo color. Barra superior y acciones contextuales discretas. |
| Contenido | Portadas reales del usuario en la app; las series, imágenes y extensiones de los mockups son ficticias. No incorporar esas portadas como contenido predeterminado. |

La interfaz debe funcionar con teclado, foco visible, lector de pantalla donde Compose Desktop lo permita, escalado de texto y DPI alto. En ventanas pequeñas, las acciones se distribuyen o desplazan sin ocultar contenido esencial. Animaciones mínimas y opción de reducir movimiento si se añaden transiciones.

## Lector: experiencia de libro

Referencia: [lector](mockups/reader.png).

- Mostrar las páginas sobre un entorno oscuro que desaparezca visualmente al leer. El modo de doble página presenta dos imágenes contiguas con sombra y lomo central **puramente visuales**: no recortar, deformar ni alterar los archivos CBZ o las páginas online.
- Conservar los modos existentes (una página, doble página, vertical/webtoon) y sus direcciones. El usuario elige el modo; el aspecto de libro se aplica al modo doble, no se impone a todos los capítulos. Respetar el orden de lectura LTR/RTL seleccionado y mostrar la portada o una página impar sola cuando corresponda.
- Cabecera mínima: manga, capítulo y posición. Pie mínimo: progreso, navegación anterior/siguiente, ajuste de página, selector de modo y acceso a acciones. Controles ocultables durante la lectura; reaparecen por interacción de ratón o teclado.
- Mantener zoom, ajuste, brillo/filtros, marcadores, guardado de página, progreso y atajos ya existentes. Los estados de carga, error y reintento deben ser legibles sin cubrir la página completa con controles.
- La página debe caber en la ventana sin perder proporción. En doble página, priorizar ver ambas páginas completas; el zoom permite inspeccionar el detalle. Probar tanto CBZ local como lectura online y capítulos largos.

## Secciones

| Vista | Mockup | Composición y datos reales |
| --- | --- | --- |
| Biblioteca | [library.png](mockups/library.png) | Cuadrícula adaptable de portadas reales, título, capítulo/progreso y acceso a continuar leyendo. Buscar, filtrar u ordenar solo si existe comportamiento equivalente o se implementa explícitamente. |
| Actualizaciones | [updates.png](mockups/updates.png) | Lista cronológica de capítulos nuevos, agrupada por fecha; acción de actualizar y acceso al capítulo. Estado vacío y errores visibles. |
| Historial | [history.png](mockups/history.png) | Lista de lectura reciente con portada, capítulo, momento y progreso persistido; acción de continuar. |
| Fuentes | [sources.png](mockups/sources.png) | Fuente local destacada y fuentes Desktop realmente instaladas. No mostrar fuentes ficticias ni presentar APK Android como compatible. |
| Buscar | [search.png](mockups/search.png) | Campo principal, fuente seleccionada y resultados reales; detalles de manga como panel o pantalla según el espacio disponible. |
| Extensiones | [extensions.png](mockups/extensions.png) | Flujo claro para `.mihonext`, índice HTTPS, huella de firma y confianza explícita. No llamar «autor verificado» a una huella que el usuario solamente haya aceptado; distinguir confianza local de identidad verificada. |
| Categorías | [categories.png](mockups/categories.png) | Categorías reales con recuento y vista previa de portadas; crear/asignar. Renombrar/reordenar del mockup es ilustrativo hasta que tenga soporte de datos y acciones. |
| Ajustes | [settings.png](mockups/settings.png) | Agrupar opciones reales de lectura, biblioteca, migración `.tachibk`, almacenamiento y enlaces. No ofrecer cambiar ubicación de datos ni migrar preferencias Android si esas operaciones no existen. |
| Descargas | [downloads.png](mockups/downloads.png) | Cola, en curso, pausadas, fallidas y completadas con progreso/reintento/cancelación reales. Medidores de velocidad o espacio solo si hay datos confiables. |

Cada vista necesita estados **vacío, cargando, error y contenido** con la misma estética; no diseñar únicamente el caso lleno de portadas. Manga details y lista de capítulos siguen el patrón visual de Biblioteca/Buscar aunque no tengan mockup independiente.

## Reglas de implementación posterior

1. No cambiar el esquema ni duplicar servicios solo para dibujar esta interfaz. Conectar los componentes nuevos a los estados y repositorios existentes.
2. Mantener las rutas de lectura local/CBZ y online, persistencia de progreso y comportamiento Android. Ninguna imagen generada se distribuye como manga de ejemplo.
3. No convertir detalles ficticios de los mockups en promesas de producto: nombres de series/fuentes, métricas, botones sin lógica, identidad de firmantes y rutas de almacenamiento son material ilustrativo.
4. Implementar por cortes verificables: sistema visual + shell; listas y detalles; lector; estados/responsividad/accesibilidad. Probar cada corte antes de sustituir la UI anterior.
5. Criterio de aceptación visual: las diez vistas usan la misma navegación, escala, colores y componentes; el lector conserva la sensación de libro y permite leer cómodamente un CBZ real de principio a fin en ventana y pantalla completa.

Prompt visual usado para los mockups: «Interfaz de Mihon Windows de alta fidelidad; grafito oscuro, manga original monocromo, acento salvia tenue, tipografía editorial y controles prácticos. Lector inmersivo a doble página con lomo sutil; aplicar la misma identidad a cada sección». Generados con la herramienta integrada de imágenes; son referencias de diseño, no capturas de una implementación.
