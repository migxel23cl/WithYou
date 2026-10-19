# Avance Fase 2 (Desarrollo) — Withyou

> Última actualización: 07-10-2026 · Estado del código al 27-09-2026
> Proyecto Capstone Duoc UC · Metodología Cascada · Equipo: Miguel Pereira (jefe de proyecto), Baltazar Ordoñez (voz e interpretación), José Luis Donoso (interfaz y accesibilidad), Joaquín Mendoza (pruebas y documentación técnica)

## 1. Resumen ejecutivo

Se inició la Fase 2 siguiendo el diseño aprobado en la Fase 1 y el orden de trabajo definido en `CLAUDE.md` (MVP primero). Están completos los **pasos 1, 2 y 3** del plan de desarrollo y parte del **paso 4**.

La aplicación funciona de punta a punta en un teléfono real (Samsung Galaxy J2 Core, Android 8.1 Go, 1 GB de RAM):

1. El usuario toca el micrófono y habla en español de Chile.
2. La app reconoce la instrucción, la interpreta y la ejecuta.
3. La app responde **por voz y en pantalla al mismo tiempo**.

Funciones operativas: consultar la hora, consultar la fecha, encender y apagar la linterna, subir y bajar el volumen.

| Indicador | Valor |
|---|---|
| Archivos de código fuente | 27 (1.681 líneas) |
| Archivos de pruebas | 6 (733 líneas) |
| Pruebas automatizadas | 61, todas aprobadas |
| Requerimientos funcionales | 6 de 13 completos, 3 parciales |
| Dispositivo de prueba | Samsung J2 Core (SM-J260M), Android 8.1 Go, 1 GB RAM |

## 2. Entorno y herramientas

| Elemento | Versión o herramienta |
|---|---|
| Lenguaje | Kotlin 1.9.22 |
| IDE | Android Studio |
| Compilación | Gradle 8.2.1 + Android Gradle Plugin 8.2.2, con catálogo de versiones (`gradle/libs.versions.toml`) |
| SDK | minSdk 26 (Android 8.0), compileSdk y targetSdk 34 |
| JDK | 17 |
| Librerías | AndroidX Core, AppCompat, Material Design 3, ConstraintLayout, Lifecycle (ViewModel), Fragment, Navigation Component, Room 2.6.1 (con KSP), DataStore 1.0.0, Kotlin Coroutines 1.7.3 |
| Pruebas | JUnit 4.13.2, kotlinx-coroutines-test; Espresso configurado (sin pruebas aún) |

## 3. Arquitectura implementada (MVVM por capas)

```
VIEW         ui/ (Fragments, Activity, AndroidResponseTexts) + res/ + AndroidManifest.xml
  │  eventos (tocar micrófono)                ▲ estado (StateFlow)
  ▼                                           │
VIEWMODEL    ui/home/HomeViewModel, HomeUiState, ResponseTexts
  │
  ▼
MODEL        domain/   lógica de negocio (Kotlin puro, sin Android)
             services/ acceso al teléfono (detrás de interfaces)
             data/     base de datos y preferencias (por construir)

di/AppContainer  → conecta las capas (inyección de dependencias manual)
test/            → pruebas de cada capa
```

Las dependencias van siempre hacia abajo: la View conoce al ViewModel y el ViewModel conoce al Model. **El ViewModel nunca conoce a la View** ni usa el `Context` de una Activity.

`ui/` agrupa por pantalla (convención de Google): en `ui/home/` están juntos el Fragment, el ViewModel y el estado de Inicio. La separación de capas la da quién conoce a quién, no la carpeta.

### Principios de ingeniería de software aplicados

- **Responsabilidad única (SRP):** cada clase tiene una sola función (normalizar texto, extraer horas, buscar contactos, interpretar, decidir).
- **Inversión de dependencias (DIP):** el ViewModel depende de interfaces (`SpeechService`, `TtsService`, `ActionExecutor`, `ResponseTexts`), no de clases de Android.
- **Inyección de dependencias** por constructor, centralizada en `AppContainer`. Se eligió la opción manual en vez de Hilt por ser más simple de mantener para el equipo.
- **Flujo de datos unidireccional:** los eventos van de la View al ViewModel y el estado del ViewModel a la View. Hay una sola fuente de verdad y el estado sobrevive a la rotación de pantalla.
- **Modelado con tipos sellados** (`sealed interface`) para intenciones, decisiones y estados. El compilador obliga a manejar todos los casos, lo que refuerza el RNF-07.
- **Dominio independiente de la plataforma:** `IntentParser` y `CommandProcessor` se prueban con JUnit sin dispositivo.
- **Textos externalizados:** el dominio entrega decisiones estructuradas y la interfaz arma las frases desde `strings.xml`.
- **Trazabilidad:** los comentarios del código citan los requerimientos que cumplen (`// RF-13`, `// RNF-06`).
- **Análisis de causa raíz:** los errores en el dispositivo se diagnosticaron con Logcat antes de cambiar código (ver sección 7).

## 4. Estructura del proyecto

| Carpeta / archivo | Capa | Contenido |
|---|---|---|
| `ui/MainActivity.kt` | View | Ventana principal: barra superior, barra de navegación inferior y contenedor de pantallas |
| `ui/PlaceholderFragment.kt` | View | Pantalla temporal para Mensajes, Contactos y Ajustes |
| `ui/home/HomeFragment.kt` | View | Dibuja Inicio según el estado; pide el permiso del micrófono con explicación previa |
| `ui/home/AndroidResponseTexts.kt` | View | Arma las frases de respuesta desde `strings.xml` |
| `ui/home/HomeViewModel.kt` | ViewModel | Coordina escuchar → interpretar → decidir → ejecutar → responder; vigilante de 10 s |
| `ui/home/HomeUiState.kt` | ViewModel | Estados de la pantalla: `Idle`, `Listening`, `Processing`, `Responded` |
| `ui/home/ResponseTexts.kt` | ViewModel | Interfaz de las frases (para no depender de Android) |
| `domain/model/` | Model | `UserIntent`, `Decision`, `ParseResult`, `TimeSpec`, `Contact` |
| `domain/parser/` | Model | `TextNormalizer`, `NumberWords`, `TimeExtractor`, `StringSimilarity`, `ContactMatcher`, `IntentParser` |
| `domain/processor/` | Model | `CommandProcessor` |
| `services/` | Model | Interfaces `SpeechService`, `TtsService`, `ActionExecutor` y sus implementaciones `Android*` |
| `di/AppContainer.kt` | Infraestructura | Crea una sola vez el parser, el procesador y los servicios |
| `WithYouApp.kt` | Infraestructura | Clase `Application`; crea el `AppContainer` |
| `res/layout/` | View | `activity_main.xml`, `fragment_home.xml`, `fragment_placeholder.xml` |
| `res/values/` | View | `strings.xml` (todos los textos en español), `colors.xml`, `dimens.xml`, `themes.xml` |
| `res/drawable/`, `res/menu/`, `res/navigation/` | View | Íconos, barra inferior y mapa de navegación |
| `AndroidManifest.xml` | View | Permisos (`RECORD_AUDIO`) y visibilidad del reconocedor y del motor de voz |
| `app/src/test/` | Pruebas | Una clase de prueba por componente del Model y del ViewModel |

## 5. Módulos desarrollados

### 5.1 Núcleo de interpretación (`domain/`)

| Componente | Función |
|---|---|
| `TextNormalizer` | Minúsculas, sin tildes ni signos; conserva formatos de hora ("8:30", "a. m.") |
| `NumberWords` | Números escritos en palabras a valor numérico ("treinta y cinco" → 35) |
| `TimeExtractor` | Expresiones horarias coloquiales: "a las ocho y media de la noche", "un cuarto para las nueve", "mediodía", "en 20 minutos". Distingue "mañana" (día siguiente) de "de la mañana" (momento del día) |
| `ContactMatcher` | Coincidencia difusa (Jaro-Winkler) para tolerar errores de reconocimiento; detecta ambigüedades como dos contactos "María" |
| `IntentParser` | Basado en reglas y patrones. Reconoce 10 intenciones (alarma, llamada, mensaje, linterna, volumen, hora, fecha, sí, no, desconocida) y devuelve hasta 3 candidatas con su confianza (0 a 1) |
| `CommandProcessor` | Decide: ejecutar (no crítica), pedir confirmación (crítica), pedir aclaración (ambigüedad o baja confianza), pedir un dato faltante o rechazar. Valida hora, número de teléfono y largo del mensaje |

### 5.2 Servicios del dispositivo (`services/`)

| Componente | Función |
|---|---|
| `AndroidSpeechService` | Voz a texto con `SpeechRecognizer` en es-CL; hasta 3 transcripciones alternativas y resultados parciales. En equipos Android Go usa modo en línea; si el modo sin conexión falla, reintenta en línea automáticamente |
| `AndroidTtsService` | Texto a voz con `TextToSpeech` en español (es-CL con alternativas de respaldo), a velocidad 0,9 |
| `AndroidActionExecutor` | Linterna con `CameraManager` (sin permiso de cámara) y volumen con `AudioManager`. No baja el volumen a cero para que el usuario siga escuchando las respuestas |

### 5.3 Interfaz (`ui/`, `res/`)

- **Pantalla de Inicio** según el mockup:
  - Fecha, pregunta "¿En qué puedo ayudarte?" y botón circular de micrófono que cambia a verde mientras escucha.
  - Tarjeta "Dijiste: «…»" y tarjeta de respuesta con color según resultado (éxito, información o error).
  - Grilla de acciones rápidas (aún muestran "disponible pronto") y barra de navegación inferior.
- **Navegación** con Navigation Component: Inicio, Mensajes, Contactos y Ajustes, todas a un nivel desde Inicio.
- **Estilo:** paleta del mockup (`#1F4E8C`, `#E6F1FB`, `#CFE5F7`, `#1E9E57`, `#D32F2F`), texto base de 20sp, títulos de 28sp y elementos táctiles de 56dp o más.
- **Permiso de micrófono:** diálogo previo en lenguaje simple; si se deniega de forma permanente, ofrece abrir los Ajustes del teléfono.

## 6. Trazabilidad de requerimientos

### Funcionales

| ID | Requerimiento | Estado | Implementación |
|---|---|---|---|
| RF-01 | Capturar audio | ✅ Completo | `AndroidSpeechService`, botón de micrófono |
| RF-02 | Voz a texto | ✅ Completo | `SpeechRecognizer` es-CL, 3 alternativas |
| RF-03 | Identificar la intención | ✅ Completo | `IntentParser` (10 intenciones) |
| RF-04 | Aclaración ante ambigüedad | 🟡 Parcial | Lógica en `CommandProcessor`; falta la pantalla "¿Quisiste decir…?" |
| RF-05 | Alarmas por voz | 🟡 Parcial | Interpretación y validación listas; falta ejecutar y confirmar |
| RF-06 | Llamar por voz | ⬜ Pendiente | Interpretación y búsqueda de contactos listas |
| RF-07 | Mensajes por voz | ⬜ Pendiente | Interpretación (incluido el texto del mensaje) lista |
| RF-08 | Linterna y volumen | ✅ Completo | `AndroidActionExecutor` |
| RF-09 | Hora y fecha | ✅ Completo | Respuesta hablada en lenguaje natural |
| RF-10 | Confirmar acciones críticas | 🟡 Parcial | Regla en `CommandProcessor`; falta la pantalla de confirmación |
| RF-11 | Contactos frecuentes | ⬜ Pendiente | Room configurado |
| RF-12 | Preferencias de accesibilidad | ⬜ Pendiente | DataStore configurado |
| RF-13 | Retroalimentación visual y auditiva simultánea | ✅ Completo | El ViewModel muestra y dice cada respuesta en el mismo momento |

### No funcionales

| ID | Requerimiento | Estado | Evidencia |
|---|---|---|---|
| RNF-01 | Texto ≥ 18sp, táctiles ≥ 56dp | ✅ Aplicado | `dimens.xml` |
| RNF-02 | Máximo 2 niveles de navegación | ✅ Aplicado | Todas las secciones a un nivel desde Inicio |
| RNF-03 | Respuesta ≤ 3 s | 🟡 Instrumentado | Latencia registrada en Logcat (`WithYou.Home`); falta la medición formal |
| RNF-04 | Estable con 1–1,5 GB de RAM | 🟡 En validación | Funciona en Samsung J2 Core de 1 GB; falta prueba prolongada |
| RNF-05 | Datos cifrados | ⬜ Pendiente | Paso 8 |
| RNF-06 | Permisos mínimos con explicación | ✅ Aplicado | Solo `RECORD_AUDIO`, con diálogo explicativo previo |
| RNF-07 | Sin fallos silenciosos | ✅ Aplicado | Toda decisión produce respuesta; vigilante de 10 s evita esperas infinitas |
| RNF-08 | Compatible con TalkBack | 🟡 Parcial | `contentDescription`, región en vivo y encabezados accesibles; falta probar con TalkBack |

### Casos de uso

| CU | Estado |
|---|---|
| CU-04 Linterna y volumen | ✅ Operativo |
| CU-05 Hora y fecha | ✅ Operativo |
| CU-01 Alarma, CU-06 Aclaración, CU-08 Confirmación | 🟡 Lógica lista, falta interfaz |
| CU-02 Llamada, CU-03 Mensaje, CU-07 Contactos | ⬜ Pendientes |

## 7. Pruebas

### 7.1 Pruebas automatizadas (JUnit)

| Clase de prueba | Qué valida | Pruebas |
|---|---|---|
| `TextNormalizerTest` | Normalización de texto | 2 |
| `TimeExtractorTest` | Más de 40 expresiones horarias coloquiales | 3 |
| `ContactMatcherTest` | Búsqueda difusa, ambigüedades, nombres por parentesco | 8 |
| `IntentParserTest` | Tablas de frases reales en español de Chile → intención esperada | 11 |
| `CommandProcessorTest` | Confirmación, aclaración, validación de hora y teléfono, flujo completo | 21 |
| `HomeViewModelTest` | Flujo de la pantalla con servicios simulados | 16 |
| **Total** | | **61, todas aprobadas** |

Ejecución: `.\gradlew.bat testDebugUnitTest`

**Técnica:** dobles de prueba (*fakes*) reemplazan el micrófono, la voz y el hardware. El flujo completo se valida sin dispositivo, incluidos errores del reconocedor y tiempos de espera simulados.

### 7.2 Pruebas en dispositivo real

Samsung Galaxy J2 Core (SM-J260M), Android 8.1 Go, 1 GB de RAM (representa al público objetivo del RNF-04). Hora, fecha, linterna y volumen operan correctamente por voz.

## 8. Problemas encontrados y soluciones

| # | Problema observado | Causa raíz | Solución |
|---|---|---|---|
| 1 | "Sin conexión a internet" teniendo conexión | Android Go no tiene motor de reconocimiento sin conexión; al pedir modo offline el sistema falla y lo reporta como error de red | Detección de equipos de baja RAM para usar modo en línea, con reintento automático si el modo offline falla |
| 2 | La app quedaba indefinidamente en "Te escucho…" | El reintento reutilizaba el mismo reconocedor y el cierre de la sesión fallida cancelaba la nueva | Reconocedor nuevo en cada reintento, más un vigilante de 10 s que siempre entrega una respuesta (RNF-07) |
| 3 | La app no respondía por voz | El motor de texto a voz de Google estaba desactivado en el teléfono | Se activó en Ajustes. Queda como requisito de instalación |

## 9. Decisiones técnicas

| Decisión | Justificación |
|---|---|
| `minSdk 26` (Android 8.0) | Cubre equipos antiguos de poca RAM (decisión abierta 1) |
| Room para contactos e historial; DataStore para preferencias | Resuelve la inconsistencia entre documentos (decisión abierta 2) |
| La intención se llama `UserIntent` y no `Intent` | Evita confusión con `android.content.Intent` |
| "A las 8" sin mañana/tarde pide aclaración | Una alarma a la hora equivocada es un error grave para el usuario objetivo |
| Inyección de dependencias manual (no Hilt) | Más simple de mantener para el equipo |
| El volumen no baja de 1 | El usuario debe seguir escuchando las respuestas |
| Velocidad de voz 0,9 | Mejor comprensión para adultos mayores |
| Reconocimiento en línea en equipos Android Go | No existe motor offline en esos equipos |

## 10. Supuestos y limitaciones

- **Android Go requiere internet para reconocer voz.** El sistema no permite descargar el idioma para uso sin conexión. La síntesis de voz sí funciona sin conexión. Alternativa evaluable: reconocedor incluido en la app (p. ej. Vosk, ~40 MB, vocabulario restringido), que implica cambiar el stack y requiere una prueba de concepto midiendo RAM, latencia y precisión.
- **Motor de voz:** el dispositivo debe tener activo "Servicios de voz de Google".
- **Alarmas:** `AlarmClock` programa la alarma para la próxima vez que llegue esa hora; "mañana a las 8" dicho a las 7:00 sonaría el mismo día. Pendiente de definir con el equipo.

## 11. Trabajo pendiente

1. **Paso 4 (resto):** alarma con pantalla de confirmación (sí/no por voz o botón) y pantalla de resultado.
2. **Paso 5:** contactos frecuentes (Room), pantalla de Contactos y llamada con confirmación y aclaración.
3. **Paso 6:** mensajes (dictado, lectura en voz alta y confirmación).
4. **Paso 7:** preferencias (tamaño de texto, volumen de voz) con DataStore.
5. **Paso 8:** prueba con TalkBack, cifrado (SQLCipher + Android Keystore), PIN para Ajustes y Contactos, pruebas Espresso y medición formal del RNF-03.
6. **Mejoras menores de arquitectura:** mover el formato de la fecha de Inicio del Fragment al ViewModel; crear `ContactRepository` y `PreferencesRepository` en `data/`.
7. **Gestión:** instalar Git, publicar el repositorio en GitHub y trabajar con ramas por funcionalidad (aún no hay historial de commits).
