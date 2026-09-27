# Auditoría de diseño y comportamiento — Mihon Windows

Fecha: 2026-09-26. Rama: `codex/suwayomi-local-engine`. HEAD: `98fdd24`.
Commits revisados: `d76f387`, `98fdd24`, con `9b5739b` como referencia anterior al rediseño.

## Conclusión

El diseño está implementado parcialmente. La paleta, títulos serif, paneles, cuadrícula y varias acciones reales existen. La composición, jerarquía, navegación, lector y adaptación a ventanas pequeñas todavía tienen diferencias importantes. No es correcto considerar terminada la fidelidad visual ni interpretar una compilación o suite verde como aceptación de las diez vistas.

Hay defectos funcionales independientes del aspecto. Algunos son regresiones del rediseño; otros ya existían y ahora afectan a las nuevas acciones. El proyecto tiene servicios reutilizables suficientes para corregirlos sin sustituir la arquitectura.

## Evidencia y límites

- Lectura del documento de diseño, apertura visual de los diez PNG, revisión de roadmap/decisión Suwayomi y documentación del motor/release.
- Inspección de shell, lector, portadas, carga de páginas, repositorio, actualización de biblioteca, adaptador Suwayomi, arranque y empaquetado.
- Inspección actual del ejecutable empaquetado en biblioteca y lector con el perfil aislado `build/ui-test-profile`. CBZ de tres páginas numeradas. Se reprodujo el desacoplamiento entre slider y página visible en webtoon.
- Las diferencias de otras vistas se identifican por sus composables frente a los PNG; no se presenta esta revisión como una nueva prueba interactiva exhaustiva de las diez pantallas.
- El usuario comunica parpadeo cada 2–3 segundos. El código confirma un cambio de geometría ligado a un temporizador de 2800 ms. Las capturas puntuales no confirman el ciclo completo ni su causa exclusiva. No hay evidencia para culpar al controlador gráfico.
- No se modificó el código de la aplicación durante esta auditoría. No se instaló software ni se alteró el perfil personal.

## Hallazgos priorizados

### F01 · P1 · El autoocultado cambia el tamaño del área de lectura

Evidencia: `DesktopReader.kt:386`, `:429`, `:499`, `:518`, `:567`.

La cabecera y el pie se insertan/eliminan dentro de la misma `Column` que contiene las páginas con `weight(1f)`. A los 2800 ms desaparecen; un movimiento, pulsación o tecla los muestra. Cada transición vuelve a medir el área de lectura. En modos paginados cambia el encuadre y, según el ajuste, la escala; en continuo cambia el viewport y el padding inferior, que depende de su altura.

Esto es una explicación fuerte del salto/parpadeo comunicado. La repetición sin entrada física requiere todavía registrar eventos y cambios de visibilidad; no debe afirmarse que las capturas mueven el ratón sin demostrarlo.

Corrección: viewport estable en un `Box`, controles superpuestos y transición de opacidad; impedir autoocultado mientras un control tiene foco o se arrastra el progreso. Conservar una vía explícita de mostrar/ocultar.

Aceptación: observar al menos 30 segundos en los seis modos, con ratón quieto y después con movimiento/teclado. La posición y escala de la página deben permanecer iguales al ocultar controles. Probar también una página larga y foco de teclado en el pie.

### F02 · P1 · El slider en webtoon/vertical guarda una posición que no está mostrando

Evidencia: `DesktopReader.kt:485`, `:493`, `:568`, `:304`.

El slider modifica `pageIndex`, pero no `requestedScroll`. El desplazamiento real depende de `requestedScroll`; el efecto de observación solo emite cuando cambia el primer elemento visible. El efecto de persistencia sí guarda el nuevo índice.

Reproducción realizada: CBZ de tres páginas, webtoon mostrando la página 3; pulsar el inicio de la barra. La barra pasa al inicio y la imagen sigue siendo la 3, incluso en una observación posterior. Cambiar a página simple muestra entonces la 1.

Impacto: progreso visual y guardado incoherentes; al saltar al final puede marcarse leído/sincronizarse un capítulo que no se ha mostrado. Es una regresión del slider añadido en el rediseño.

Corrección: una operación de navegación común para slider, botones y teclas; solicitar desplazamiento en los modos continuos y guardar la posición efectivamente mostrada. Prueba con salto adelante/atrás, cierre/reapertura y cambio de modo.

### F03 · P1 · Portadas de Suwayomi persistidas con un puerto efímero

Evidencia: `LocalSuwayomiEngine.kt:51`, `SuwayomiSource.kt:65`, `SuwayomiClient.kt:146`, `DesktopMangaRepository.kt:44`, `DesktopCover.kt:77`.

El motor elige un puerto local nuevo. `toManga` convierte la ruta de la portada en una URL absoluta de ese puerto. El repositorio persiste `thumbnail_url`; cuando el manga ya existe, `ensureManga` devuelve el registro sin actualizar sus metadatos. `DesktopCover` usa literalmente la URL guardada.

Consecuencia deducida del código: si cambia el puerto tras reiniciar, las portadas guardadas apuntan al servidor anterior y fallan en biblioteca/historial/descargas. Refrescar fuentes por sí solo no corrige la URL persistida. Este defecto antecede al rediseño.

Corrección: persistir una referencia estable al medio y resolverla contra el cliente activo; migrar las referencias loopback anteriores de manera restringida a medios propios de Suwayomi. No aplicar un reemplazo indiscriminado a URLs de otras fuentes. Añadir prueba de dos arranques con puertos distintos.

### F04 · P1 · Las lecturas descargadas dependen de refrescar la fuente online

Evidencia: `DesktopShell.kt:204`, `:228`, `:1820`; `DesktopPageLoader.kt:81`.

Continuar desde biblioteca/historial llama `openStoredManga`, exige fuente instalada y después espera `getMangaUpdate(..., true, true)` para crear el lector. El loader puede leer páginas descargadas, pero se llega a él solamente después de esa petición. Si la fuente falla sin conexión, el flujo se bloquea antes de aprovechar la descarga. La vista Descargas tampoco ofrece abrir el capítulo completado.

Confirmado por recorrido del código; no se desconectó la red en esta sesión. Afecta al requisito de lectura offline, aunque la caché aislada tenga pruebas correctas.

Corrección: abrir capítulos persistidos y descargas completas primero; refrescar metadatos aparte. Prueba de extremo a extremo: descargar, reiniciar, cortar red, abrir desde biblioteca/historial/descargas.

### F05 · P1 · Peticiones antiguas pueden sobrescribir una fuente o manga nuevos

Evidencia: `DesktopShell.kt:182`, `:204`, `:1364`.

Cada búsqueda/detalle inicia un `scope.launch` independiente sin cancelar el anterior ni comprobar identidad antes de publicar. `browseItems` tampoco se limpia al iniciar otra petición. Si A termina después de B, puede sobrescribir resultados de B; las tarjetas usan la variable global `source` para abrirlos. Un detalle que termina después de navegar a otra sección puede volver a activar `selectedManga`.

Riesgo demostrado por las escrituras y ausencia de control de orden; falta reproducción con latencia controlada. La búsqueda ya tenía este patrón antes del rediseño.

Corrección: Job cancelable más token/identidad de petición; estado asociado a fuente, consulta y manga. Preservar `CancellationException`. Prueba determinista completando B antes que A.

### F06 · P2 · Asignación de categorías no tiene estado observable propio

Evidencia: `DesktopShell.kt:1086`.

El valor de las casillas proviene de una consulta síncrona durante composición. El callback escribe en DB y solo asigna `message = "Categories updated"`. Después del primer cambio, repetir ese mismo mensaje no garantiza recomposición: las casillas pueden conservar el estado anterior y el siguiente callback usa el conjunto capturado anteriormente.

Defecto de sincronización visible en código, preexistente; requiere reproducir varias asignaciones seguidas en el mismo detalle.

Corrección: estado de selección explícito/observable, actualizado tras la transacción; no usar un mensaje de éxito para provocar recomposición. Probar marcar dos categorías y desmarcar una sin abandonar la pantalla.

### F07 · P2 · La doble página no garantiza páginas contiguas

Evidencia: `DesktopReader.kt:541`, `:547`, `:553`, `:559`, `:696`.

Dos cajas ocupan mitades iguales de todo el viewport. Cada imagen se centra y ajusta dentro de su caja, mientras sombra, borde y lomo se dibujan sobre las cajas. Con páginas verticales y ventana muy ancha, queda espacio entre los bordes reales de las páginas: el lomo no une las imágenes.

Corrección: medir ambas proporciones, calcular una altura común que permita mostrar las dos completas, colocar sus bordes interiores contiguos y aplicar sombra/lomo al grupo real. Mantener el orden RTL y la portada sola. No recortar para simular la unión.

### F08 · P2 · Ajuste y gestos del lector necesitan una definición consistente

Evidencia: `DesktopReader.kt:483`, `:696`, `:706`, `:711`.

WIDTH/HEIGHT se implementan cambiando solo una dimensión del contenedor de `Image`, siempre con `ContentScale.Fit`. En la prueba actual, “Fit width” mantiene la imagen de prueba de 360 píxeles de ancho en una ventana de 2505 píxeles; no llena el ancho. ORIGINAL tampoco garantiza tamaño original si las restricciones lo reducen.

Además, todas las páginas consumen arrastres y cambian `pan`, incluso a zoom 1 y en webtoon; pueden interferir con el desplazamiento vertical. No hay límites de pan ni restauración al volver a zoom 1. VERTICAL y WEBTOON comparten exactamente el mismo renderer.

Corrección: definir ajuste de página completa versus ancho/alto, pan acotado solo cuando corresponda y gesto vertical reservado al scroll continuo. Validar imágenes pequeñas, grandes, apaisadas y muy largas.

### F09 · P2 · Cabeceras y navegación no garantizan uso a tamaño mínimo/DPI alto

Evidencia: `DesktopDesign.kt:91`, `DesktopShell.kt:414`, `:1197`, `:1751`, `:1702`; `Main.kt:64`.

La cabecera es una Row con texto sin peso y acciones de ancho fijo (250 dp en biblioteca/historial). La barra lateral mide 190 dp y no tiene scroll ni alternativa compacta. Categorías reserva 170 dp y añade hasta cinco portadas en una Row sin adaptación. La altura del pie del lector crece con todos sus botones en FlowRow.

El tamaño mínimo nativo no demuestra que quepa el contenido. A DPI alto disminuye el espacio lógico disponible. Hace falta comprobar las acciones completas, no solo que la ventana se pueda abrir.

Corrección: cabecera adaptable a dos filas, anchos flexibles, sidebar desplazable/compacta y controles secundarios del lector en menú accesible. Validar 800×560 y 125/150/200 % sin recortes.

### F10 · P2 · Consultas y carga de extensiones en composición/hilo de UI

Evidencia: `DesktopShell.kt:1173`, `:1221`, `:1685`, `:1776`, `:2371`, `:1564`.

Hay consultas SQLite por manga/fila durante composición. Categorías repite consultas de pertenencia para cada categoría y cada manga. Cambios de búsqueda o recomposiciones vuelven a pagarlas. Instalar un paquete local `.mihonext` se ejecuta síncronamente desde el botón; también hay refrescos de fuentes que pueden cargar extensiones desde UI.

Esto es un riesgo claro de bloqueos/latencia, no una cifra de rendimiento medida. Las pruebas de escala del repositorio no miden el tiempo de frame de Compose.

Corrección: preparar modelos de pantalla en IO, consultar membresías/progreso por lotes y mantener estado observable. Extraer composables por pantalla reutilizando repositorios y servicios existentes.

### F11 · P2 · Los resultados se cortan en la primera página

Evidencia: `DesktopShell.kt:192`, `:194`; `SuwayomiSource.kt:36`.

Browse/search piden siempre página 1 y descartan `hasNextPage`, que el adaptador sí devuelve. No existe “cargar más”. Es una limitación funcional preexistente que puede parecer que no encuentra mangas.

Corrección: paginación con estado de carga/error independiente y deduplicación por fuente+URL; no sustituirla por búsquedas globales ficticias.

### F12 · P2 · Estados y tema incompletos

Evidencia: `DesktopShell.kt:487`, `:1117`, `DesktopCover.kt:48`, `DesktopDesign.kt:33`.

Los errores se colorean buscando palabras inglesas en un mensaje global. Al cargar detalles, la lista vacía muestra “No chapters available” mientras sigue cargando. Las portadas usan “Cover unavailable” también durante carga. La paleta no define todos los colores de contenedores Material: se observó una pista violeta en el slider, ajena a la propuesta.

Corrección: estados de carga/contenido/vacío/error específicos de cada pantalla, mensajes con severidad explícita, retry conectado y colores completos de los componentes usados. Reutilizar recursos i18n existentes sin ampliar el alcance a una traducción completa.

### F13 · P2 · Empaquetar sin propiedad vuelve a 1.0.3

Evidencia: `desktopApp/build.gradle.kts:59`; instrucciones iniciales de `PHASE_12_RELEASE_STATUS.md`.

La entrega 1.0.4 se construyó con override; el valor por defecto sigue siendo 1.0.3 y el comando de ejemplo del documento no incluye el override. Es fácil regenerar un instalador con versión anterior. El hash del launcher `Mihon.exe` por sí solo no identifica el código Kotlin: este está en los JAR adyacentes.

Corrección: una fuente inequívoca de versión para la entrega, comando completo reproducible y manifiesto de hashes del contenido de la distribución. Mantener upgradeUuid. Instalación limpia/upgrade/desinstalación y firma siguen pendientes; no cerrar P12/P13.

## Comparación por pantalla

| Vista | Implementado | Diferencias o trabajo pendiente |
|---|---|---|
| Biblioteca | Grid adaptable, portada real, búsqueda, filtro por categoría, continuar | Tarjetas de 160 dp mucho más densas que la referencia; encabezado duplicado; falta presentación de progreso; continuar usa refresco online incluso con descarga |
| Actualizaciones | Capítulos reales, estado leído, fecha, actualizar, leer y descargar | Sin agrupación cronológica por fechas; jerarquía visual reducida a tarjetas uniformes; revisar encabezado estrecho y estados simultáneos |
| Historial | Portada, capítulo, fecha/hora, posición persistida, continuar | Sin grupos de fecha ni barra de progreso; consulta por fila; continuar depende de red |
| Fuentes | Fuente local, instaladas, idiomas visibles, lista desplazable | Local no tiene el protagonismo del mockup; no hay composición amplia de selección + contenido; falta búsqueda de fuentes |
| Buscar | Consulta por fuente, idioma, resultados reales y detalles | Hereda la vista de fuentes, campo de enlaces ocupa la zona principal; no hay panel contextual amplio; primera página y carreras entre peticiones |
| Extensiones | Keiyoushi/Suwayomi y `.mihonext`, confianza local correctamente diferenciada | Sección Desktop conserva presentación de formulario; instalación local bloqueante; conviene jerarquía de catálogo/instaladas/gestión coherente |
| Categorías | Crear, conteos, miniaturas, abrir biblioteca y asignar desde detalles | Layout rígido, consultas multiplicadas y casillas no observables. Renombrar/reordenar de los PNG no se considera obligación si no existía soporte |
| Ajustes | Migración, rutas, enlaces, trackers y programación conservados | Organización parcial: integraciones largas y repetidas; opciones existentes del lector siguen aisladas del panel de lectura propuesto |
| Descargas | Cola real, progreso por páginas, pausa/reanudar/reintentar/cancelar | Lista plana sin separación clara de estados; no abrir completado desde aquí; no exigir velocidades/espacio inventados de la imagen |
| Lector | Seis modos seleccionables, progreso, zoom, filtros, brillo, marcadores, guardado, teclas | Autoocultado inestable, slider continuo incorrecto, doble página separada y controles demasiado extensos; VERTICAL/WEBTOON sin diferenciación |
| Detalles/capítulos | Portada, datos, biblioteca, categorías, trackers y lectura | Demasiada gestión de trackers antes de capítulos; carga confundida con vacío; faltan presentación clara de leído/progreso y retorno contextual |
| Diálogos | Material tematizado para apariencia y atajos | Colores de contenedor incompletos; controles cíclicos poco descubribles; falta validación de foco/DPI |

En navegación se sustituyeron iconos por números. La tira salvia se dibuja con ancho de 3 dp pero sin altura ni contenido (`DesktopShell.kt:446`), por lo que no aporta la marca vertical prevista. La selección depende del borde y texto. Los números pueden conservarse como ayuda de atajos junto a iconos, no ocupar su lugar visual.

La ausencia de niebla/ilustración ambiental no es un fallo prioritario: el documento la considera secundaria. Tampoco se penaliza conservar colores reales, no distribuir mangas ficticios ni adaptar extensiones al motor posterior al mockup. Los PNG difieren entre sí en navegación; se debe unificar con la lista de secciones del documento, no copiar literalmente todas sus variantes.

## Qué conservar y cómo mejorar la estructura

- Conservar `Source.displayName()` y sus etiquetas de idioma; no equivalen a traducir toda la UI.
- Conservar Suwayomi en JVM separada, `.bin`, runtime con `javaw.exe`, loopback y la vía independiente `.mihonext`.
- Conservar repositorio SQLDelight, preferencias, cola, servicios de plataforma y planificadores.
- Dividir el shell de aproximadamente 2500 líneas en pantallas con estado y acciones explícitos. Reducir bloques repetidos de trackers mediante componentes concretos, sin crear un nuevo framework genérico.
- Unificar la navegación del lector: ya hay lógica en `reader-core` y otra variante en Desktop. Mantener una política comprobable de portada, spreads y cambio de modo evita divergencias; mover lógica compartida requerirá gates Android correspondientes.
- Probar recorridos completos además de helpers. Las dos pruebas nuevas de spreads verifican índices, pero no medición, foco, autoocultado, slider ni integración con scroll.

## Orden de corrección recomendado

1. Lector: viewport estable, slider/scroll/progreso sincronizados y pruebas de regresión del parpadeo comunicado.
2. Integridad funcional: portadas tras reiniciar, acceso offline, peticiones fuera de orden, estado de categorías.
3. Fidelidad visual: navegación/iconos, encabezados únicos, escala de tarjetas, grupos por fecha, doble página realmente contigua, controles discretos.
4. Responsividad y rendimiento: 800×560/DPI, foco visible, IO fuera de composición, paginación y errores/reintento por pantalla.
5. Nueva distribución con versión superior, verificación de JAR/recursos y recorridos locales/online. Mantener gates de Windows limpio pendientes.

## Verificaciones de esta auditoría

- `:desktopApp:desktopTest :desktopApp:spotlessCheck`: BUILD SUCCESSFUL; varias tareas estaban UP-TO-DATE.
- Para comprobar ejecución real, `:desktopApp:desktopTest --rerun`: BUILD SUCCESSFUL, 37 tests, 0 fallos, 0 errores, 2 omitidos (35 ejecutados sin fallo).
- Omitidos: arranque/parada real del motor e imágenes reales de Keiyoushi en `SuwayomiSourceTest`. Esta suite verde no acredita esos dos recorridos.
- UI actual: biblioteca y lector del ejecutable 1.0.4, perfil aislado, fallo del slider reproducido. No se verificó de nuevo lectura online, instalación, todas las vistas a tamaño mínimo ni DPI del sistema.
- No hay cambios Android/compartidos en esta auditoría; no se ejecutaron gates Android ni se regeneraron instaladores.
- El único archivo añadido por la auditoría es este informe. `.vscode/` y `docs/ai/` siguen preservados y fuera de commits. No se creó commit ni se hizo push/PR/merge.

## Seguimiento de correcciones — 2026-09-27

La auditoría anterior describe el estado de 2026-09-26. El trabajo posterior corrigió la geometría del lector al ocultar controles, sincronizó barra y desplazamiento continuo, recompuso la doble página con imágenes completas y contiguas, y reparó el cambio de modo con número impar de páginas. Una prueba local CBZ de tres páginas y un capítulo online de MangaDex (EN) de 39 páginas abrieron desde la distribución.

Se corrigieron las referencias de portadas de Suwayomi tras cambios del puerto local, la apertura de capítulos persistidos antes del refresco de la fuente, las peticiones de búsqueda/detalle fuera de orden y el estado de casillas de categorías. En el perfil aislado se marcaron dos categorías consecutivas y se desmarcó una sin salir del detalle; el resultado persistió tras reiniciar. La biblioteca, historial, actualizaciones y categorías cargan sus datos fuera del hilo de composición. El detalle obtiene sus trackers en una consulta en IO. Los avisos de error tienen severidad explícita.

La presentación ahora incluye navegación con iconos, búsqueda de fuentes, paginación de resultados, agrupación cronológica de historial/actualizaciones, tarjetas y acciones de descargas, menús compactos del lector y estados de carga/error/vacío donde aplican. Se recorrieron las diez secciones, detalles y lector en ventana amplia y aproximadamente 800×560; en esta última se verificó el acceso a los controles principales. El idioma de cada fuente continúa visible, incluida la búsqueda.

Límites de la evidencia local: no se modificó el DPI del sistema; no se cortó la red para una prueba completa de lectura descargada tras reinicio; no se reprodujeron carreras con latencia controlada; el autoocultado se observó durante 31 segundos en una sesión, no en todos los modos. La carga inicial de fuentes y algunas operaciones puntuales de configuración siguen siendo síncronas. Los gates de instalación, actualización y desinstalación en Windows limpio, avisos de terceros y firma siguen pendientes según `PHASE_12_RELEASE_STATUS.md`; no cerrar P12/P13 con esta revisión local.
