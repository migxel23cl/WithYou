# Avance Fase 2 (Desarrollo) — Withyou

> Última actualización: 06-11-2026 · Paso 7 completo y validado en el teléfono
> Proyecto Capstone Duoc UC · Metodología Cascada · Equipo: Miguel Pereira (jefe de proyecto), Baltazar Ordoñez (voz e interpretación), José Luis Donoso (interfaz y accesibilidad), Joaquín Mendoza (pruebas y documentación técnica)

## 1. Resumen ejecutivo

Se inició la Fase 2 siguiendo el diseño aprobado en la Fase 1 y el orden de trabajo definido en `CLAUDE.md` (MVP primero). Están completos los **pasos 1 a 7** del plan de desarrollo (de 8). Solo queda el paso 8 (accesibilidad, seguridad y pruebas finales).

La aplicación funciona de punta a punta en un teléfono real (Samsung Galaxy J2 Core, Android 8.1 Go, 1 GB de RAM):

1. El usuario toca el micrófono y habla en español de Chile.
2. La app reconoce la instrucción, la interpreta y la ejecuta.
3. La app responde **por voz y en pantalla al mismo tiempo**.

Funciones operativas: consultar la hora y la fecha, encender y apagar la linterna, subir y bajar el volumen, **crear alarmas por voz con confirmación** (pantallas Confirmar, Aclarar y Resultado), **registrar contactos frecuentes**, **llamarlos** y **enviarles mensajes dictados**, siempre con confirmación, y **ajustar el tamaño del texto, el volumen de la voz y la respuesta por voz**.

| Indicador | Valor |
|---|---|
| Pruebas automatizadas | 130 unitarias + 6 instrumentadas en el teléfono, todas aprobadas |
| Requerimientos funcionales | 13 de 13 completos |
| Casos de uso operativos | 8 de 8 |
| Control de versiones | Git, una rama por paso |
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
VIEWMODEL    ui/assistant/AssistantViewModel, AssistantUiState, ResponseTexts
  │
  ▼
MODEL        domain/   lógica de negocio (Kotlin puro, sin Android)
             services/ acceso al teléfono (detrás de interfaces)
             data/     ContactRepository → Room · PreferencesRepository → DataStore

di/AppContainer  → conecta las capas (inyección de dependencias manual)
test/            → pruebas de cada capa
```

Las dependencias van siempre hacia abajo: la View conoce al ViewModel y el ViewModel conoce al Model. **El ViewModel nunca conoce a la View** ni usa el `Context` de una Activity.

`ui/` agrupa por pantalla (convención de Google): `ui/home/`, `ui/confirm/`, `ui/clarify/` y `ui/result/` tienen cada uno su Fragment. Como una misma conversación pasa por varias pantallas, las cuatro comparten **un solo `AssistantViewModel`** (en `ui/assistant/`), ligado a la Activity. La separación de capas la da quién conoce a quién, no la carpeta.

**Navegación según el estado:** `AssistantUiState.screen` indica qué pantalla debe verse y `MainActivity` la muestra. Los Fragments no navegan entre ellos ni deciden nada: dibujan el estado y avisan los toques al ViewModel.

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
| `ui/confirm/ConfirmFragment.kt` | View | Pantalla Confirmar: "Sí, …" (verde) y "Cancelar" (rojo), o sí/no por voz |
| `ui/clarify/ClarifyFragment.kt` | View | Pantalla "No entendí bien": hasta 3 opciones, "Intentar de nuevo" y "Cancelar" |
| `ui/result/ResultFragment.kt` | View | Pantalla Resultado: check verde y vuelta automática al Inicio |
| `ui/assistant/AssistantViews.kt` | View | Partes de interfaz repetidas (micrófono de respuesta, "Dijiste…", botón volver) |
| `ui/assistant/AndroidResponseTexts.kt` | View | Arma las frases de respuesta desde `strings.xml` |
| `ui/assistant/AssistantViewModel.kt` | ViewModel | Conversación de varios pasos: escuchar → interpretar → decidir → confirmar, aclarar o pedir un dato → ejecutar → responder; vigilante de 10 s |
| `ui/assistant/AssistantUiState.kt` | ViewModel | Estado: pantalla visible, estado de la voz, lo escuchado, el mensaje, la confirmación y las opciones |
| `ui/assistant/ResponseTexts.kt` | ViewModel | Interfaz de las frases (para no depender de Android) |
| `domain/model/` | Model | `UserIntent`, `Decision`, `ParseResult`, `TimeSpec`, `Contact`, `AccessibilityPreferences` (con los rangos seguros de texto y voz) |
| `domain/parser/` | Model | `TextNormalizer`, `NumberWords`, `TimeExtractor`, `StringSimilarity`, `ContactMatcher`, `IntentParser`, `ChoiceParser` |
| `domain/processor/` | Model | `CommandProcessor` |
| `domain/validation/` | Model | `ContactValidator`: limpia y valida nombres y teléfonos (lo usan el formulario y el `CommandProcessor`) |
| `data/local/` | Model | `AppDatabase` (Room), `ContactEntity`, `ContactDao`. Esquema exportado en `app/schemas/` |
| `data/repository/` | Model | `ContactRepository` (interfaz) y `RoomContactRepository`: entrega contactos del dominio, no entidades de Room |
| `data/prefs/` | Model | `PreferencesRepository` (interfaz) y `DataStorePreferencesRepository`: tamaño de texto, volumen de voz y respuesta por voz |
| `ui/contacts/` | View + ViewModel | `ContactsFragment` y `ContactsAdapter` (View), `ContactsViewModel` (valida y guarda mediante el Repository) |
| `ui/messages/` | View + ViewModel | `MessagesFragment` y `RecipientAdapter` (View), `MessagesViewModel` (contacto elegido como destinatario) |
| `ui/MicPermissionRequester.kt`, `ui/TabNavigation.kt` | View | Permiso del micrófono con explicación previa y navegación entre pestañas, compartidos por Inicio y Mensajes |
| `ui/settings/` | View + ViewModel | `SettingsFragment` (View) y `SettingsViewModel` (guarda mediante el Repository; lo comparte con `MainActivity`) |
| `services/` | Model | Interfaces `SpeechService`, `TtsService`, `ActionExecutor` y sus implementaciones `Android*` |
| `di/AppContainer.kt` | Infraestructura | Crea una sola vez la base de datos, el repositorio, el parser, el procesador y los servicios |
| `WithYouApp.kt` | Infraestructura | Clase `Application`; crea el `AppContainer` |
| `res/layout/` | View | `activity_main.xml`, `fragment_home.xml`, `fragment_confirm.xml`, `fragment_clarify.xml`, `fragment_result.xml`, `fragment_contacts.xml`, `item_contact.xml`, `dialog_contact_form.xml`, `fragment_messages.xml`, `item_recipient.xml`, `fragment_settings.xml` |
| `res/values/` | View | `strings.xml` (todos los textos en español), `colors.xml`, `dimens.xml`, `themes.xml` |
| `res/drawable/`, `res/menu/`, `res/navigation/` | View | Íconos, barra inferior y mapa de navegación |
| `AndroidManifest.xml` | View | Permisos (`RECORD_AUDIO`, `SET_ALARM` y `CALL_PHONE`) y visibilidad del reconocedor, del motor de voz, del reloj, del teléfono y de la app de mensajes |
| `app/src/test/` | Pruebas | Una clase de prueba por componente del Model y del ViewModel |

## 5. Módulos desarrollados

### 5.1 Núcleo de interpretación (`domain/`)

| Componente | Función |
|---|---|
| `TextNormalizer` | Minúsculas, sin tildes ni signos; conserva formatos de hora ("8:30", "a. m.") |
| `NumberWords` | Números escritos en palabras a valor numérico ("treinta y cinco" → 35) |
| `TimeExtractor` | Expresiones horarias coloquiales: "a las ocho y media de la noche", "un cuarto para las nueve", "mediodía", "en 20 minutos". Distingue "mañana" (día siguiente) de "de la mañana" (momento del día) |
| `ContactMatcher` | Coincidencia difusa (Jaro-Winkler) para tolerar errores de reconocimiento; detecta ambigüedades como dos contactos "María" |
| `IntentParser` | Basado en reglas y patrones. Reconoce 10 intenciones (alarma, llamada, mensaje, linterna, volumen, hora, fecha, sí, no, desconocida) y devuelve hasta 3 candidatas con su confianza (0 a 1). Con `completeSlot` completa un dato que faltaba ("¿A qué hora?" → "a las ocho"), respetando lo dicho antes ("en la tarde" + "a las cinco" = 17:00) |
| `ChoiceParser` | Entiende la opción elegida en "¿Quisiste decir…?": "la primera", "opción dos", "la última", o "de la noche" para elegir entre mañana y noche |
| `CommandProcessor` | Decide: ejecutar (no crítica), pedir confirmación (crítica), pedir aclaración (ambigüedad o baja confianza), pedir un dato faltante o rechazar. Valida hora, número de teléfono y largo del mensaje |

### 5.2 Servicios del dispositivo (`services/`)

| Componente | Función |
|---|---|
| `AndroidSpeechService` | Voz a texto con `SpeechRecognizer` en es-CL; hasta 3 transcripciones alternativas y resultados parciales. En equipos Android Go usa modo en línea; si el modo sin conexión falla, reintenta en línea automáticamente |
| `AndroidTtsService` | Texto a voz con `TextToSpeech` en español (es-CL con alternativas de respaldo), a velocidad 0,9 |
| `AndroidActionExecutor` | Linterna con `CameraManager` (sin permiso de cámara), volumen con `AudioManager` (sin bajarlo a cero), alarmas con `AlarmClock.ACTION_SET_ALARM` en el reloj del sistema (suenan aunque Withyou esté cerrada), llamadas con `ACTION_CALL` (o `ACTION_DIAL` sin permiso) y mensajes con `ACTION_SENDTO`, que abre la app de Mensajes con el texto listo |

### 5.3 Interfaz (`ui/`, `res/`)

- **Pantalla de Inicio** según el mockup:
  - Fecha, pregunta "¿En qué puedo ayudarte?" y botón circular de micrófono que cambia a verde mientras escucha.
  - Tarjeta "Dijiste: «…»" y tarjeta de respuesta con color según resultado (éxito, información o error).
  - Grilla de acciones rápidas: **Alarma** pregunta la hora y **Linterna** la enciende o apaga; Llamar y Mensajes abren su sección.
- **Pantalla Confirmar** (CU-08): tarjeta "Comando detectado", burbuja del asistente, botón verde "Sí, …", botón rojo "Cancelar" y micrófono para responder "sí" o "no".
- **Pantalla No entendí bien** (CU-06): hasta 3 opciones numeradas que se leen en voz alta, "Intentar de nuevo" y "Cancelar".
- **Pantalla Contactos** (CU-07, mockup 5): botón "Agregar contacto" y lista con la inicial, el nombre, el número y un botón verde de llamar (que pasa por Confirmar). Tocar un contacto abre el formulario para editarlo o eliminarlo; el formulario marca los errores sin cerrarse.
- **Pantalla Mensajes** (CU-03, mockup 3): "¿A quién le escribes?", contactos que se marcan con un check al elegirlos y micrófono grande "Dictar mensaje". Si no hay contactos, ofrece ir a Contactos.
- **Pantalla Ajustes** (RF-12, mockup 7): tamaño del texto (100 % a 160 %) con vista previa, volumen de la voz (20 % a 100 %) con una muestra al soltar el control, y "Responder también con la voz". El tamaño se aplica a toda la app.
- **Confirmar un mensaje**: la app lee el mensaje en voz alta y ofrece "Sí, enviar", "Corregir mensaje" (dictarlo de nuevo) y "Cancelar".
- **Pantalla Resultado**: check verde, "Reproduciendo confirmación…" y vuelta automática al Inicio a los 8 s. En estas tres pantallas se oculta la barra inferior.
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
| RF-04 | Aclaración ante ambigüedad | ✅ Completo | `CommandProcessor` + pantalla "No entendí bien" + `ChoiceParser` |
| RF-05 | Alarmas por voz | ✅ Completo | `AlarmClock.ACTION_SET_ALARM`, con confirmación y pantalla de resultado |
| RF-06 | Llamar por voz | ✅ Completo | "Llama a María" → Confirmar → `ACTION_CALL` (o `ACTION_DIAL` sin permiso) |
| RF-07 | Mensajes por voz | ✅ Completo | Dictado → lectura en voz alta → confirmación → `ACTION_SENDTO` (el usuario toca Enviar en Mensajes) |
| RF-08 | Linterna y volumen | ✅ Completo | `AndroidActionExecutor` |
| RF-09 | Hora y fecha | ✅ Completo | Respuesta hablada en lenguaje natural |
| RF-10 | Confirmar acciones críticas | ✅ Completo | Regla en `CommandProcessor` + pantalla Confirmar (botones o sí/no por voz) |
| RF-11 | Contactos frecuentes | ✅ Completo | Room + `ContactRepository` + pantalla Contactos (agregar, editar, eliminar) |
| RF-12 | Preferencias de accesibilidad | ✅ Completo | `PreferencesRepository` (DataStore) + pantalla Ajustes; aplicadas a toda la interfaz y a la voz |
| RF-13 | Retroalimentación visual y auditiva simultánea | ✅ Completo | El ViewModel muestra y dice cada respuesta en el mismo momento |

### No funcionales

| ID | Requerimiento | Estado | Evidencia |
|---|---|---|---|
| RNF-01 | Texto ≥ 18sp, táctiles ≥ 56dp | ✅ Aplicado | `dimens.xml`; además el usuario puede agrandar todo el texto hasta 160 % (RF-12) |
| RNF-02 | Máximo 2 niveles de navegación | ✅ Aplicado | Todas las secciones a un nivel desde Inicio |
| RNF-03 | Respuesta ≤ 3 s | 🟡 Instrumentado | Latencia registrada en Logcat (`WithYou.Home`); falta la medición formal |
| RNF-04 | Estable con 1–1,5 GB de RAM | 🟡 En validación | Funciona en Samsung J2 Core de 1 GB; falta prueba prolongada |
| RNF-05 | Datos cifrados | ⬜ Pendiente | Paso 8 |
| RNF-06 | Permisos mínimos con explicación | ✅ Aplicado | `RECORD_AUDIO` y `CALL_PHONE` se piden con un diálogo explicativo previo; si se niega el de llamadas, igual se puede llamar con `ACTION_DIAL` |
| RNF-07 | Sin fallos silenciosos | ✅ Aplicado | Toda decisión produce respuesta; vigilante de 10 s evita esperas infinitas |
| RNF-08 | Compatible con TalkBack | 🟡 Parcial | `contentDescription`, región en vivo y encabezados accesibles; falta probar con TalkBack |

### Casos de uso

| CU | Estado |
|---|---|
| CU-01 Alarma | ✅ Operativo |
| CU-04 Linterna y volumen | ✅ Operativo |
| CU-05 Hora y fecha | ✅ Operativo |
| CU-06 Aclaración | ✅ Operativo |
| CU-08 Confirmar acción crítica | ✅ Operativo |
| CU-02 Llamada | ✅ Operativo |
| CU-07 Contactos frecuentes | ✅ Operativo |
| CU-03 Mensaje | ✅ Operativo |

## 7. Pruebas

### 7.1 Pruebas automatizadas (JUnit)

| Clase de prueba | Qué valida | Pruebas |
|---|---|---|
| `TextNormalizerTest` | Normalización de texto | 2 |
| `TimeExtractorTest` | Más de 40 expresiones horarias coloquiales | 3 |
| `ContactMatcherTest` | Búsqueda difusa, ambigüedades, nombres por parentesco | 8 |
| `IntentParserTest` | Tablas de frases reales en español de Chile → intención esperada; completar datos faltantes | 14 |
| `ChoiceParserTest` | Elegir opciones por voz ("la primera", "de la noche") | 3 |
| `CommandProcessorTest` | Confirmación, aclaración, validación de hora y teléfono, flujo completo | 20 |
| `ContactValidatorTest` | Teléfonos y nombres válidos e inválidos | 3 |
| `ContactsViewModelTest` | Guardar, validar, editar y eliminar contactos con un repositorio en memoria | 4 |
| `MessagesViewModelTest` | Elegir destinatario y qué pasa si se elimina | 3 |
| `AccessibilityPreferencesTest` | Rangos seguros de texto y voz, valores por defecto | 3 |
| `SettingsViewModelTest` | Guardar cada preferencia, límites, muestra de voz con el volumen elegido | 5 |
| `AssistantViewModelTest` | Conversación completa con servicios simulados: confirmar, cancelar, aclarar, pedir datos, resultado, llamadas, mensajes, volumen y voz desactivada, errores y tiempos de espera | 63 |
| **Total** | | **130, todas aprobadas** |

### 7.1.1 Pruebas instrumentadas (en el teléfono)

| Clase de prueba | Qué valida | Pruebas |
|---|---|---|
| `ContactRepositoryTest` | Room real (SQLite en memoria): guardar, ordenar por nombre, editar sin duplicar y eliminar | 3 |
| `PreferencesRepositoryTest` | DataStore real en un archivo temporal: valores por defecto, guardar y leer, límites | 3 |

Se ejecutan con `adb shell am instrument` para no desinstalar la app del teléfono (Gradle la desinstala al terminar `connectedAndroidTest`).

Ejecución: `.\gradlew.bat testDebugUnitTest`

**Técnica:** dobles de prueba (*fakes*) reemplazan el micrófono, la voz y el hardware. El flujo completo se valida sin dispositivo, incluidos errores del reconocedor y tiempos de espera simulados.

### 7.2 Pruebas en dispositivo real

Samsung Galaxy J2 Core (SM-J260M), Android 8.1 Go, 1 GB de RAM (representa al público objetivo del RNF-04). Hora, fecha, linterna, volumen, **alarmas** y **llamadas** con confirmación operan correctamente por voz. Las alarmas quedan creadas en la app Reloj de Samsung. La llamada se inicia correctamente; el teléfono de prueba no tiene chip, por lo que el sistema muestra "sin red móvil", lo que confirma que la app entregó la llamada al sistema. Los **mensajes** dictados llegan a la app de Mensajes con el destinatario y el texto correctos; el usuario toca Enviar. Las **preferencias** (tamaño de texto, volumen de voz y respuesta por voz) se aplican y se mantienen al cerrar la app.

## 8. Problemas encontrados y soluciones

| # | Problema observado | Causa raíz | Solución |
|---|---|---|---|
| 1 | "Sin conexión a internet" teniendo conexión | Android Go no tiene motor de reconocimiento sin conexión; al pedir modo offline el sistema falla y lo reporta como error de red | Detección de equipos de baja RAM para usar modo en línea, con reintento automático si el modo offline falla |
| 2 | La app quedaba indefinidamente en "Te escucho…" | El reintento reutilizaba el mismo reconocedor y el cierre de la sesión fallida cancelaba la nueva | Reconocedor nuevo en cada reintento, más un vigilante de 10 s que siempre entrega una respuesta (RNF-07) |
| 3 | La app no respondía por voz | El motor de texto a voz de Google estaba desactivado en el teléfono | Se activó en Ajustes. Queda como requisito de instalación |
| 4 | En "No entendí bien" no había cómo cancelar (solo "Intentar de nuevo") | Error de diseño: la salida dependía de la flecha de volver, poco evidente para un adulto mayor | Botón rojo "Cancelar" también en esa pantalla (detectado en la prueba en el teléfono) |

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
| Un solo `AssistantViewModel` compartido por Inicio, Confirmar, Aclarar y Resultado | Una conversación pasa por varias pantallas y debe recordar qué está pendiente |
| La navegación la decide el estado (`MainActivity` observa `screen`) | Los Fragments no navegan ni deciden: solo dibujan y avisan eventos |
| Elegir una opción en "No entendí bien" igual pasa por Confirmar si la acción es crítica | Cumple RF-10 sin excepciones; evita llamadas o alarmas por un toque accidental |
| La alarma usa el reloj del sistema (`AlarmClock`) | Suena aunque la app esté cerrada y el usuario la ve en su Reloj de siempre |
| Los ViewModels dependen de la interfaz `ContactRepository`, no de Room | Capa Repository de MVVM: se prueban con un repositorio en memoria y se puede cambiar la base de datos (p. ej. cifrarla en el paso 8) sin tocarlos |
| El botón verde de llamar en Contactos también pasa por Confirmar | RF-10 sin excepciones: evita llamadas por un toque accidental |
| Llamada con `ACTION_CALL` y respaldo `ACTION_DIAL` | Decisión abierta 4 del diseño: llama directo tras confirmar; si no hay permiso, abre el teléfono con el número marcado |
| El permiso de llamadas se pide al guardar el primer contacto | Es cuando el cuidador configura la app; se explica antes en lenguaje simple (RNF-06) |
| Al iniciar una llamada la app no habla (solo muestra el resultado) | Excepción justificada al RF-13: la voz sonaría encima de la llamada |
| Validación de teléfono en un solo lugar (`ContactValidator`) | La usan el formulario y el `CommandProcessor`; evita reglas duplicadas que se contradigan |
| Mensajes con `ACTION_SENDTO`: se abre Mensajes con el texto listo y el usuario toca Enviar | Decisión abierta 5 del diseño, **ratificada por el equipo tras probarla** (06-11-2026): no requiere el permiso peligroso `SEND_SMS`, que Google Play restringe, y el usuario ve el SMS antes de enviarlo |
| Al dictar desde Mensajes, la pregunta "¿Qué le quieres decir?" se muestra pero no se dice | El micrófono ya está escuchando y captaría la voz de la app |
| Permiso del micrófono en un componente reutilizable (`MicPermissionRequester`) | Inicio y Mensajes lo necesitan; evita duplicar lógica de la View |
| Rangos seguros de las preferencias en el dominio (texto 100–160 %, voz 20–100 %) | Ningún valor guardado puede dejar la app ilegible o muda |
| Tamaño de texto con `fontScale` en `MainActivity`, leído al iniciar | Android exige definirlo antes de dibujar cualquier vista (antes de que existan los ViewModels); al cambiarlo la Activity se redibuja y conserva la pantalla |
| Volumen de la voz por frase (`KEY_PARAM_VOLUME` de TextToSpeech) | Cambia solo la voz de la app, no el volumen general del teléfono |
| Desactivar la respuesta por voz no oculta nada en pantalla | La información siempre se muestra; RF-13 se cumple por defecto y el usuario puede preferir solo pantalla |

## 10. Supuestos y limitaciones

- **Android Go requiere internet para reconocer voz.** El sistema no permite descargar el idioma para uso sin conexión. La síntesis de voz sí funciona sin conexión. Alternativa evaluable: reconocedor incluido en la app (p. ej. Vosk, ~40 MB, vocabulario restringido), que implica cambiar el stack y requiere una prueba de concepto midiendo RAM, latencia y precisión.
- **Motor de voz:** el dispositivo debe tener activo "Servicios de voz de Google".
- **Alarmas:** `AlarmClock` programa la alarma para la próxima vez que llegue esa hora; "mañana a las 8" dicho a las 7:00 sonaría el mismo día. Pendiente de definir con el equipo.

## 11. Trabajo pendiente

1. **Paso 8:** prueba con TalkBack, cifrado (SQLCipher + Android Keystore), PIN para Ajustes y Contactos, pruebas Espresso y medición formal del RNF-03.
2. **Gestión:** publicar el repositorio en GitHub. Git ya está en uso localmente: `main` más una rama por paso (`feature/paso-4-…` a `feature/paso-7-preferencias`).

### Mejoras futuras (fuera del alcance del MVP)

- **Envío automático de mensajes:** hoy se abre la app de Mensajes con el texto listo y el usuario toca Enviar. Se podría enviar directo con `SmsManager` y el permiso `SEND_SMS` (explicado antes de pedirlo, con la confirmación de la app y con respaldo a `ACTION_SENDTO` si se niega). Implica: permiso peligroso, restricción de Google Play para publicar la app y posibles cobros de SMS sin que el usuario vea la app de Mensajes. **Decisión del equipo (06-11-2026): mantener `ACTION_SENDTO` y evaluar este cambio a futuro.**
- **Reconocimiento de voz sin internet en Android Go:** evaluar un reconocedor incluido en la app (p. ej. Vosk), ver sección 10.
- **Escucha automática después de una pregunta** ("¿A qué hora?") sin tener que tocar el micrófono.

**Corregido en el paso 4:** el formato de la fecha de Inicio salió del Fragment y ahora lo entrega el ViewModel (`todayHeader()`), cumpliendo la regla de que la View no contiene lógica.
