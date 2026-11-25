# Informe Fase 2 (Desarrollo) — Withyou

> Última actualización: 25-11-2026 · **Los 8 pasos del plan completos** y validados en dos teléfonos · versión 0.1.2 · documentos de la metodología listos (sección 13)
> Proyecto Capstone Duoc UC · Metodología Cascada · Equipo: Miguel Pereira (jefe de proyecto), Baltasar Ordoñez (voz e interpretación), José Luis Donoso (interfaz y accesibilidad), Joaquín Mendoza (pruebas y documentación técnica)

## 1. Resumen ejecutivo

Se inició la Fase 2 siguiendo el diseño aprobado en la Fase 1 y el orden de trabajo definido en `CLAUDE.md` (MVP primero). Están completos **los 8 pasos** del plan de desarrollo: la aplicación cumple los 13 requerimientos funcionales y los 8 no funcionales, y se agregó la importación de contactos desde la agenda del teléfono.

La aplicación funciona de punta a punta en un teléfono real (Samsung Galaxy J2 Core, Android 8.1 Go, 1 GB de RAM):

1. El usuario toca el micrófono y habla en español de Chile.
2. La app reconoce la instrucción, la interpreta y la ejecuta.
3. La app responde **por voz y en pantalla al mismo tiempo**.

Funciones operativas: consultar la hora y la fecha, encender y apagar la linterna, subir y bajar el volumen, **crear alarmas por voz con confirmación y con nombre** ("para tomar la pastilla") (pantallas Confirmar, Aclarar y Resultado), **registrar contactos frecuentes**, **llamarlos** y **enviarles mensajes dictados**, siempre con confirmación, **ajustar el tamaño del texto, el volumen de la voz y la respuesta por voz**, y **agregar contactos desde la agenda del teléfono**. Los contactos se guardan cifrados y el cuidador puede proteger Ajustes y Contactos con un PIN.

| Indicador | Valor |
|---|---|
| Pasos del plan de desarrollo | 8 de 8 completos |
| Requerimientos funcionales | 13 de 13 completos |
| Requerimientos no funcionales | 8 de 8 cumplidos |
| Casos de uso operativos | 8 de 8 |
| Pruebas automatizadas | 158 unitarias + 18 instrumentadas en el teléfono (8 de interfaz con Espresso), todas aprobadas |
| Tiempo de respuesta (RNF-03) | 424 ms en promedio y 572 ms en el peor caso desde la última palabra (meta ≤ 3 s) |
| Memoria (RNF-04) | 38 MB en uso (4,4 % de la RAM del teléfono de 1 GB) |
| Control de versiones | Git, una rama por paso |
| Dispositivos de prueba | Samsung J2 Core (SM-J260M), Android 8.1 Go, 1 GB de RAM (gama baja) · Samsung Galaxy S25 FE (gama alta) |

## 2. Entorno y herramientas

| Elemento | Versión o herramienta |
|---|---|
| Lenguaje | Kotlin 1.9.22 |
| IDE | Android Studio |
| Compilación | Gradle 8.2.1 + Android Gradle Plugin 8.2.2, con catálogo de versiones (`gradle/libs.versions.toml`) |
| SDK | minSdk 26 (Android 8.0), compileSdk y targetSdk 34 |
| JDK | 17 |
| Librerías | AndroidX Core, AppCompat, Material Design 3, ConstraintLayout, RecyclerView 1.3.2, Lifecycle (ViewModel), Fragment, Navigation Component, Room 2.6.1 (con KSP), DataStore 1.0.0, Kotlin Coroutines 1.7.3 |
| Seguridad | SQLCipher 4.6.1 (cifrado de la base de datos), Android Keystore (clave), PBKDF2 de `javax.crypto` (PIN) |
| Pruebas | JUnit 4.13.2, kotlinx-coroutines-test, Espresso 3.5.1 y espresso-accessibility (verificador de accesibilidad de Google) |
| Control de versiones | Git, una rama por paso y merge a `main` al validar cada paso en el teléfono |

## 3. Arquitectura implementada (MVVM por capas)

```
VIEW         ui/ (Fragments, Activity, AndroidResponseTexts) + res/ + AndroidManifest.xml
  │  eventos (tocar micrófono)                ▲ estado (StateFlow)
  ▼                                           │
VIEWMODEL    AssistantViewModel (conversación) · ContactsViewModel · MessagesViewModel
             SettingsViewModel · SecurityViewModel (PIN)
  │
  ▼
MODEL        domain/   lógica de negocio (Kotlin puro, sin Android)
             services/ acceso al teléfono (detrás de interfaces)
             data/     ContactRepository → Room cifrado (SQLCipher)
                       PreferencesRepository, PinRepository → DataStore

di/AppContainer  → conecta las capas (inyección de dependencias manual)
test/            → pruebas de cada capa
```

Las dependencias van siempre hacia abajo: la View conoce al ViewModel y el ViewModel conoce al Model. **El ViewModel nunca conoce a la View** ni usa el `Context` de una Activity.

`ui/` agrupa por pantalla (convención de Google): `ui/home/`, `ui/confirm/`, `ui/clarify/` y `ui/result/` tienen cada uno su Fragment. Como una misma conversación pasa por varias pantallas, las cuatro comparten **un solo `AssistantViewModel`** (en `ui/assistant/`), ligado a la Activity. La separación de capas la da quién conoce a quién, no la carpeta.

**Navegación según el estado:** `AssistantUiState.screen` indica qué pantalla debe verse y `MainActivity` la muestra. Los Fragments no navegan entre ellos ni deciden nada: dibujan el estado y avisan los toques al ViewModel.

### Principios de ingeniería de software aplicados

- **Responsabilidad única (SRP):** cada clase tiene una sola función (normalizar texto, extraer horas, buscar contactos, interpretar, decidir).
- **Inversión de dependencias (DIP):** los ViewModels dependen de interfaces (`SpeechService`, `TtsService`, `ActionExecutor`, `PhoneContactReader`, `ResponseTexts` y los Repository), no de clases de Android ni de Room o DataStore.
- **Inyección de dependencias** por constructor, centralizada en `AppContainer`. Se eligió la opción manual en vez de Hilt por ser más simple de mantener para el equipo.
- **Flujo de datos unidireccional:** los eventos van de la View al ViewModel y el estado del ViewModel a la View. Hay una sola fuente de verdad y el estado sobrevive a la rotación de pantalla.
- **Modelado con tipos sellados** (`sealed interface`) para intenciones, decisiones y estados. El compilador obliga a manejar todos los casos, lo que refuerza el RNF-07.
- **Dominio independiente de la plataforma:** `IntentParser` y `CommandProcessor` se prueban con JUnit sin dispositivo.
- **Textos externalizados:** el dominio entrega decisiones estructuradas y la interfaz arma las frases desde `strings.xml`.
- **Trazabilidad:** los comentarios del código citan los requerimientos que cumplen (`// RF-13`, `// RNF-06`).
- **Análisis de causa raíz:** los errores en el dispositivo se diagnosticaron con Logcat y capturas de pantalla antes de cambiar código (ver sección 8).
- **Pruebas como red de seguridad:** cada funcionalidad nueva va con pruebas; las pruebas Espresso encontraron 5 problemas reales de diseño y accesibilidad que no se habían visto a mano (sección 8).

## 4. Estructura del proyecto

| Carpeta / archivo | Capa | Contenido |
|---|---|---|
| `ui/MainActivity.kt` | View | Ventana principal: barra superior, barra de navegación inferior y contenedor de pantallas |
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
| `domain/parser/` | Model | `TextNormalizer`, `NumberWords`, `TimeExtractor`, `StringSimilarity`, `ContactMatcher`, `IntentParser`, `ChoiceParser`, `AlarmLabelExtractor` |
| `domain/processor/` | Model | `CommandProcessor` |
| `domain/validation/` | Model | `ContactValidator`: limpia y valida nombres y teléfonos (lo usan el formulario, la importación y el `CommandProcessor`). `AlarmLabelPolicy`: limpia el nombre de una alarma (máximo 40 caracteres) |
| `domain/security/` | Model | `PinHasher` y `PinPolicy`: el PIN se guarda como hash PBKDF2 con sal aleatoria, nunca el PIN mismo |
| `data/local/` | Model | `AppDatabase` (Room **cifrado con SQLCipher**), `DatabasePassphrase` (clave protegida por el Android Keystore), `ContactEntity`, `ContactDao`. Esquema exportado en `app/schemas/` |
| `data/repository/` | Model | `ContactRepository` (interfaz) y `RoomContactRepository`: entrega contactos del dominio, no entidades de Room |
| `data/prefs/` | Model | `PreferencesRepository` (tamaño de texto, volumen de voz y respuesta por voz) y `PinRepository` (hash del PIN del cuidador), ambos con DataStore |
| `ui/contacts/` | View + ViewModel | `ContactsFragment` y `ContactsAdapter` (View), `ContactsViewModel` (valida, guarda e importa desde la agenda mediante el Repository) |
| `ui/messages/` | View + ViewModel | `MessagesFragment` y `RecipientAdapter` (View), `MessagesViewModel` (contacto elegido como destinatario) |
| `ui/MicPermissionRequester.kt`, `ui/TabNavigation.kt` | View | Permiso del micrófono con explicación previa y navegación entre pestañas, compartidos por Inicio y Mensajes |
| `ui/settings/` | View + ViewModel | `SettingsFragment` (View) y `SettingsViewModel` (guarda mediante el Repository; lo comparte con `MainActivity`) |
| `ui/security/` | View + ViewModel | `SecurityViewModel` (PIN del cuidador: bloqueado, desbloqueado, crear, cambiar, quitar) y `PinDialogs` (View) |
| `ui/AppDialog.kt` | View | Diálogo responsive de toda la app: título, mensaje y campos se desplazan; los botones quedan siempre visibles abajo |
| `services/` | Model | Interfaces `SpeechService`, `TtsService`, `ActionExecutor`, `PhoneContactReader` y sus implementaciones `Android*` |
| `di/AppContainer.kt` | Infraestructura | Crea una sola vez la base de datos cifrada, los Repository (contactos, preferencias y PIN), el parser, el procesador y los servicios |
| `WithYouApp.kt` | Infraestructura | Clase `Application`; crea el `AppContainer` |
| `res/layout/` | View | `activity_main.xml`, `fragment_home.xml`, `fragment_confirm.xml`, `fragment_clarify.xml`, `fragment_result.xml`, `fragment_contacts.xml`, `item_contact.xml`, `dialog_contact_form.xml`, `fragment_messages.xml`, `item_recipient.xml`, `fragment_settings.xml`, `dialog_app.xml`, `dialog_pin.xml` |
| `res/values/` | View | `strings.xml` (todos los textos en español), `colors.xml`, `dimens.xml`, `themes.xml` |
| `res/drawable/`, `res/menu/`, `res/navigation/` | View | Íconos, barra inferior y mapa de navegación |
| `AndroidManifest.xml` | View | Permisos (`RECORD_AUDIO`, `SET_ALARM` y `CALL_PHONE`) y visibilidad del reconocedor, del motor de voz, del reloj, del teléfono, de la app de mensajes y de la agenda. **No usa `READ_CONTACTS`** |
| `app/src/test/` | Pruebas | Una clase de prueba por componente del Model y del ViewModel (JUnit) |
| `app/src/androidTest/` | Pruebas | Pruebas en el teléfono: Room, DataStore, cifrado e interfaz con Espresso y el verificador de accesibilidad |

## 5. Módulos desarrollados

### 5.1 Núcleo de interpretación (`domain/`)

| Componente | Función |
|---|---|
| `TextNormalizer` | Minúsculas, sin tildes ni signos; conserva formatos de hora ("8:30", "a. m.") |
| `NumberWords` | Números escritos en palabras a valor numérico ("treinta y cinco" → 35) |
| `TimeExtractor` | Expresiones horarias coloquiales: "a las ocho y media de la noche", "un cuarto para las nueve", "mediodía", "en 20 minutos". Distingue "mañana" (día siguiente) de "de la mañana" (momento del día) |
| `ContactMatcher` | Coincidencia difusa (Jaro-Winkler) para tolerar errores de reconocimiento; detecta ambigüedades como dos contactos "María" |
| `IntentParser` | Basado en reglas y patrones. Reconoce 10 intenciones (alarma, llamada, mensaje, linterna, volumen, hora, fecha, sí, no, desconocida) y devuelve hasta 3 candidatas con su confianza (0 a 1). Con `completeSlot` completa un dato que faltaba ("¿A qué hora?" → "a las ocho"), respetando lo dicho antes ("en la tarde" + "a las cinco" = 17:00) |
| `AlarmLabelExtractor` | Encuentra el nombre de una alarma en la frase ("para tomar la pastilla", "que diga…", "con el nombre…", "recuérdame…") y lo separa de la hora, para que "tomar 2 pastillas a las 8" se entienda como las 8 y "recuérdame llamar a María" sea una alarma y no una llamada |
| `ChoiceParser` | Entiende la opción elegida en "¿Quisiste decir…?": "la primera", "opción dos", "la última", o "de la noche" para elegir entre mañana y noche |
| `CommandProcessor` | Decide: ejecutar (no crítica), pedir confirmación (crítica), pedir aclaración (ambigüedad o baja confianza), pedir un dato faltante o rechazar. Valida hora, número de teléfono y largo del mensaje |

### 5.2 Servicios del dispositivo (`services/`)

| Componente | Función |
|---|---|
| `AndroidSpeechService` | Voz a texto con `SpeechRecognizer` en es-CL; hasta 3 transcripciones alternativas y resultados parciales. Si el reconocimiento falla antes de que el usuario hable, sigue una cadena de respaldo: reconocedor predeterminado sin conexión → el mismo en línea → los demás reconocedores instalados (primero los de Google). Recuerda el que funcionó |
| `AndroidTtsService` | Texto a voz con `TextToSpeech` en español (es-CL con alternativas de respaldo), a velocidad 0,9 |
| `AndroidActionExecutor` | Linterna con `CameraManager` (sin permiso de cámara), volumen con `AudioManager` (sin bajarlo a cero), alarmas con `AlarmClock.ACTION_SET_ALARM` en el reloj del sistema (suenan aunque Withyou esté cerrada), llamadas con `ACTION_CALL` (o `ACTION_DIAL` sin permiso) y mensajes con `ACTION_SENDTO`, que abre la app de Mensajes con el texto listo |
| `AndroidPhoneContactReader` | Lee el número elegido en la agenda del teléfono. Sin permiso `READ_CONTACTS`: Android da acceso solo al contacto elegido |

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
- **Confirmar una alarma**: muestra y lee el nombre si se dijo; el botón "Ponerle nombre" (o "Cambiar nombre") permite dictarlo. El nombre queda guardado en la alarma del Reloj.
- **Pantalla Resultado**: check verde, "Reproduciendo confirmación…" y vuelta automática al Inicio a los 8 s. En Confirmar, Aclarar y Resultado se oculta la barra inferior para no distraer.
- **Navegación** con Navigation Component: Inicio, Mensajes, Contactos y Ajustes, todas a un nivel desde Inicio.
- **Estilo:** paleta del mockup (`#1F4E8C`, `#E6F1FB`, `#CFE5F7`, `#1E9E57`, `#D32F2F`), texto base de 20sp, títulos de 28sp y elementos táctiles de 56dp o más.
- **Permiso de micrófono:** diálogo previo en lenguaje simple; si se deniega de forma permanente, ofrece abrir los Ajustes del teléfono.
- **Agregar desde mis contactos:** en Contactos, abre la agenda del teléfono; se elige una persona y queda en frecuentes. No duplica números y valida igual que el formulario.
- **PIN del cuidador** (§8): en Ajustes se crea, cambia o quita (se escribe dos veces). Con PIN, Ajustes muestra "Ajustes protegidos" y agregar, editar o eliminar contactos lo pide. Ver y llamar a un contacto, y todo lo que se hace por voz, sigue sin PIN. Se vuelve a bloquear al salir de la pantalla.
- **Diseño responsive:** en Confirmar y Aclarar los botones quedan fijos abajo y solo el texto se desplaza; todos los diálogos usan `AppDialog`, con los botones siempre visibles. Comprobado con texto al 160 % en una pantalla de 540×960 y con el teclado abierto.

## 6. Trazabilidad de requerimientos

### Funcionales

| ID | Requerimiento | Estado | Implementación |
|---|---|---|---|
| RF-01 | Capturar audio | ✅ Completo | `AndroidSpeechService`, botón de micrófono |
| RF-02 | Voz a texto | ✅ Completo | `SpeechRecognizer` es-CL, 3 alternativas |
| RF-03 | Identificar la intención | ✅ Completo | `IntentParser` (10 intenciones) |
| RF-04 | Aclaración ante ambigüedad | ✅ Completo | `CommandProcessor` + pantalla "No entendí bien" + `ChoiceParser` |
| RF-05 | Alarmas por voz | ✅ Completo | `AlarmClock.ACTION_SET_ALARM`, con confirmación y pantalla de resultado; nombre opcional dicho en la frase o desde Confirmar |
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
| RNF-03 | Respuesta ≤ 3 s | ✅ Cumplido | 10 frases medidas en el teléfono: 424 ms en promedio y 572 ms en el peor caso desde la última palabra (ver 7.3) |
| RNF-04 | Estable con 1–1,5 GB de RAM | ✅ Cumplido | Samsung J2 Core de 1 GB: 38 MB en uso (4,4 % de la RAM); sin cierres en todas las pruebas |
| RNF-05 | Datos cifrados | ✅ Cumplido | Room con SQLCipher (AES-256), clave protegida por el Android Keystore; el PIN se guarda como hash PBKDF2. Comprobado en el teléfono: el archivo no muestra nombres ni números |
| RNF-06 | Permisos mínimos con explicación | ✅ Aplicado | `RECORD_AUDIO` y `CALL_PHONE` se piden con un diálogo explicativo previo; si se niega el de llamadas, igual se puede llamar con `ACTION_DIAL`. Importar contactos y enviar mensajes no requieren permisos |
| RNF-07 | Sin fallos silenciosos | ✅ Aplicado | Toda decisión produce respuesta; vigilante de 10 s evita esperas infinitas |
| RNF-08 | Compatible con TalkBack | ✅ Cumplido | `contentDescription`, regiones en vivo y encabezados; las pruebas Espresso pasan el verificador de accesibilidad de Google (nombres para TalkBack, tamaño táctil y contraste) |

### Casos de uso

| CU | Estado |
|---|---|
| CU-01 Alarma | ✅ Operativo |
| CU-02 Llamada | ✅ Operativo |
| CU-03 Mensaje | ✅ Operativo |
| CU-04 Linterna y volumen | ✅ Operativo |
| CU-05 Hora y fecha | ✅ Operativo |
| CU-06 Aclaración | ✅ Operativo |
| CU-07 Contactos frecuentes | ✅ Operativo (también desde la agenda del teléfono) |
| CU-08 Confirmar acción crítica | ✅ Operativo |

## 7. Pruebas

### 7.1 Pruebas automatizadas (JUnit)

| Clase de prueba | Qué valida | Pruebas |
|---|---|---|
| `TextNormalizerTest` | Normalización de texto | 2 |
| `TimeExtractorTest` | Más de 40 expresiones horarias coloquiales | 3 |
| `ContactMatcherTest` | Búsqueda difusa, ambigüedades, nombres por parentesco | 8 |
| `IntentParserTest` | Tablas de frases reales en español de Chile → intención esperada; completar datos faltantes; alarmas con nombre | 16 |
| `AlarmLabelExtractorTest` | Nombre de la alarma dicho de distintas formas, frases sin nombre, tildes conservadas | 3 |
| `AlarmLabelPolicyTest` | Limpieza del nombre, límite de 40 caracteres, nombres sin letras | 2 |
| `ChoiceParserTest` | Elegir opciones por voz ("la primera", "de la noche") | 3 |
| `CommandProcessorTest` | Confirmación, aclaración, validación de hora y teléfono, flujo completo; la alarma relativa conserva su nombre | 21 |
| `ContactValidatorTest` | Teléfonos y nombres válidos e inválidos | 3 |
| `ContactsViewModelTest` | Guardar, validar, editar y eliminar contactos; importar desde la agenda (duplicados, números y nombres inválidos) | 9 |
| `MessagesViewModelTest` | Elegir destinatario y qué pasa si se elimina | 3 |
| `AccessibilityPreferencesTest` | Rangos seguros de texto y voz, valores por defecto | 3 |
| `SettingsViewModelTest` | Guardar cada preferencia, límites, muestra de voz con el volumen elegido | 5 |
| `PinHasherTest` | PIN correcto e incorrecto, sal distinta cada vez, datos dañados, 4 dígitos | 4 |
| `SecurityViewModelTest` | Crear, bloquear, desbloquear, cambiar y quitar el PIN | 6 |
| `AssistantViewModelTest` | Conversación completa con servicios simulados: confirmar, cancelar, aclarar, pedir datos, resultado, llamadas, mensajes, volumen y voz desactivada, errores, tiempos de espera, medición de latencia y nombre de la alarma | 67 |
| **Total** | | **158, todas aprobadas** |

Ejecución: `.\gradlew.bat testDebugUnitTest`

**Técnica:** dobles de prueba (*fakes*) reemplazan el micrófono, la voz, el hardware y los Repository. El flujo completo se valida sin dispositivo, incluidos errores del reconocedor y tiempos de espera simulados.

### 7.1.1 Pruebas instrumentadas (en el teléfono)

| Clase de prueba | Qué valida | Pruebas |
|---|---|---|
| `ContactRepositoryTest` | Room real (SQLite en memoria): guardar, ordenar por nombre, editar sin duplicar y eliminar | 3 |
| `PreferencesRepositoryTest` | DataStore real en un archivo temporal: valores por defecto, guardar y leer, límites | 3 |
| `EncryptedDatabaseTest` | Cifrado: el archivo no tiene cabecera SQLite ni muestra nombres o números; otra clave no lo abre; la clave del Keystore se recupera | 4 |
| `AppUiTest` (Espresso) | Inicio, las 4 pestañas, formulario con errores, confirmar y cancelar una llamada, diálogos con botones completos | 6 |
| `ResponsiveUiTest` (Espresso) | Con texto al 160 % en 540×960: botones de Confirmar y de los diálogos completos | 2 |
| **Total** | | **18, todas aprobadas** |

Las pruebas Espresso corren con el **verificador de accesibilidad de Google** (`espresso-accessibility`) activo en cada acción. Quitan el PIN del cuidador durante la prueba y lo restauran al terminar (`WithoutPinRule`).

Se ejecutan con `adb shell am instrument` para no desinstalar la app del teléfono (Gradle la desinstala al terminar `connectedAndroidTest`). Las de interfaz necesitan el teléfono desbloqueado.

### 7.2 Pruebas en dispositivo real

Samsung Galaxy J2 Core (SM-J260M), Android 8.1 Go, 1 GB de RAM (representa al público objetivo del RNF-04). Hora, fecha, linterna, volumen, **alarmas** y **llamadas** con confirmación operan correctamente por voz. Las alarmas quedan creadas en la app Reloj de Samsung. La llamada se inicia correctamente; el teléfono de prueba no tiene chip, por lo que el sistema muestra "sin red móvil", lo que confirma que la app entregó la llamada al sistema. Los **mensajes** dictados llegan a la app de Mensajes con el destinatario y el texto correctos; el usuario toca Enviar. Las **preferencias** (tamaño de texto, volumen de voz y respuesta por voz) se aplican y se mantienen al cerrar la app. El **PIN** protege Ajustes y los cambios en Contactos, y **agregar desde la agenda** trae el contacto elegido.

**Samsung Galaxy S25 FE** (gama alta), probado por una persona ajena al desarrollo, con el APK instalado desde un archivo: la versión 0.1.0 no reconocía la voz (problema 10); la versión 0.1.1 funciona correctamente. La app quedó validada en un equipo de gama baja y en uno de gama alta.

### 7.3 Mediciones en el teléfono (RNF-03 y RNF-04)

**Tiempo de respuesta**, 10 frases por voz (Samsung J2 Core, Android 8.1 Go, 1 GB de RAM, por internet). Registro original en `docs/evidencia-rnf03-latencias.log`.

| # | Frase | Desde la última palabra | Desde "fin del habla" (Google) | Total desde tocar el micrófono |
|---|---|---|---|---|
| 1 | ¿Qué hora es? | — * | 131 ms | 2.757 ms |
| 2 | ¿Qué día es hoy? | 64 ms | 101 ms | 2.886 ms |
| 3 | Enciende la linterna | 481 ms | 82 ms | 2.809 ms |
| 4 | Apaga la linterna | 479 ms | 77 ms | 2.668 ms |
| 5 | Sube el volumen | 572 ms | 142 ms | 2.846 ms |
| 6 | Baja el volumen | 366 ms | 128 ms | 2.579 ms |
| 7 | ¿Qué hora es? | 399 ms | 132 ms | 2.627 ms |
| 8 | Pon una alarma para mañana a las ocho de la mañana | 548 ms | 92 ms | 4.512 ms ** |
| 9 | ¿Qué día es hoy? | 498 ms | 95 ms | 2.615 ms |
| 10 | Enciende la linterna | 408 ms | 73 ms | 2.582 ms |

| Medición | Promedio | Peor caso | Meta |
|---|---|---|---|
| **Desde la última palabra hasta la respuesta** (lo que espera el usuario) | **424 ms** | **572 ms** | ≤ 3.000 ms ✅ |
| Desde el "fin del habla" de Google (trabajo propio de la app) | 105 ms | 142 ms | — |
| Total desde que se toca el micrófono (incluye lo que se demora en hablar) | 2.888 ms | 4.512 ms | — |

\* El reconocedor entregó el texto sin resultados parciales previos. \*\* Frase larga: el total incluye unos 3 s que toma decirla.

**Cómo se midió:** la app registra tres momentos: cuando se toca el micrófono, cuando llega la última palabra reconocida y cuando el reconocedor de Google avisa el fin del habla (lo hace después de esperar un silencio, por eso subestima la espera). La última palabra reconocida llega una fracción de segundo después de dicha, así que la espera real puede ser algo mayor que 0,4 s, pero queda muy por debajo de 3 s.

**Memoria:** 38 MB (PSS) con la app recién usada, de 880 MB de RAM del teléfono (4,4 %).

## 8. Problemas encontrados y soluciones

| # | Problema observado | Causa raíz | Solución |
|---|---|---|---|
| 1 | "Sin conexión a internet" teniendo conexión | Android Go no tiene motor de reconocimiento sin conexión; al pedir modo offline el sistema falla y lo reporta como error de red | Detección de equipos de baja RAM para usar modo en línea, con reintento automático si el modo offline falla |
| 2 | La app quedaba indefinidamente en "Te escucho…" | El reintento reutilizaba el mismo reconocedor y el cierre de la sesión fallida cancelaba la nueva | Reconocedor nuevo en cada reintento, más un vigilante de 10 s que siempre entrega una respuesta (RNF-07) |
| 3 | La app no respondía por voz | El motor de texto a voz de Google estaba desactivado en el teléfono | Se activó en Ajustes. Queda como requisito de instalación |
| 4 | En "No entendí bien" no había cómo cancelar (solo "Intentar de nuevo") | Error de diseño: la salida dependía de la flecha de volver, poco evidente para un adulto mayor | Botón rojo "Cancelar" también en esa pantalla (detectado en la prueba en el teléfono) |
| 5 | En Confirmar, "Cancelar" quedaba medio cortado en una pantalla de 540×960 | El contenido medía ~548 dp y la pantalla disponible 504 dp | Botones fijos abajo y solo el texto se desplaza (detectado con Espresso) |
| 6 | En los diálogos los botones se apilaban y "Cancelar" o "Guardar" quedaban fuera; al crear el PIN no aparecían el segundo campo ni los botones | El diálogo estándar de Android no se adapta a letra grande en pantallas pequeñas | `AppDialog` propio: contenido desplazable y botones siempre visibles; se usa en todos los diálogos (detectado con Espresso y por el equipo) |
| 7 | El error del teléfono quedaba fuera de la vista del formulario; la indicación "Nombre (por…" se cortaba; errores en letra de 12 sp | Formulario sin desplazamiento automático y estilos estándar de Material | El formulario se desplaza hasta el error; indicación corta y ayudas y errores de 18 sp (RNF-01) |
| 8 | La lista vacía de contactos era un elemento sin nombre para TalkBack | La lista seguía visible aunque no tuviera elementos | Se oculta cuando está vacía (detectado por el verificador de accesibilidad) |
| 9 | La primera medición del RNF-03 daba ~100 ms y faltaban 3 de 10 frases | Se medía desde el "fin del habla" de Google, que llega después de un silencio; el registro del teléfono (256 KB) borró las primeras frases | Medición desde tres momentos y registro guardado en el computador durante la prueba |
| 10 | En un Samsung Galaxy S25 FE el micrófono no funcionaba: "Este teléfono no tiene reconocimiento de voz disponible" | En teléfonos que no son Android Go la app pide primero el reconocimiento sin conexión; el S25 FE no tenía el español disponible sin conexión y respondía "idioma no disponible". La app solo reintentaba en línea ante errores de red, y en algunos Samsung el reconocedor predeterminado funciona solo dentro del teléfono | Cadena de respaldo: sin conexión → en línea → otros reconocedores instalados (primero Google), recordando el que funcionó; mensaje propio para "idioma no disponible" y registro del código de error. Versión 0.1.1, validada en el S25 FE |

## 9. Decisiones técnicas

| Decisión | Justificación |
|---|---|
| `minSdk 26` (Android 8.0) | Cubre equipos antiguos de poca RAM (decisión abierta 1) |
| Room para contactos; DataStore para preferencias y el PIN | Resuelve la inconsistencia entre documentos (decisión abierta 2). El historial no se implementó (ver sección 10) |
| La intención se llama `UserIntent` y no `Intent` | Evita confusión con `android.content.Intent` |
| "A las 8" sin mañana/tarde pide aclaración | Una alarma a la hora equivocada es un error grave para el usuario objetivo |
| Inyección de dependencias manual (no Hilt) | Más simple de mantener para el equipo |
| El volumen no baja de 1 | El usuario debe seguir escuchando las respuestas |
| Velocidad de voz 0,9 | Mejor comprensión para adultos mayores |
| Reconocimiento en línea en equipos Android Go | No existe motor offline en esos equipos |
| Un solo `AssistantViewModel` compartido por Inicio, Confirmar, Aclarar y Resultado | Una conversación pasa por varias pantallas y debe recordar qué está pendiente |
| La fecha de Inicio la entrega el ViewModel (`todayHeader()`), no el Fragment | Regla de MVVM: la View no contiene lógica (corregido en el paso 4 tras revisar el código) |
| La navegación la decide el estado (`MainActivity` observa `screen`) | Los Fragments no navegan ni deciden: solo dibujan y avisan eventos |
| Elegir una opción en "No entendí bien" igual pasa por Confirmar si la acción es crítica | Cumple RF-10 sin excepciones; evita llamadas o alarmas por un toque accidental |
| La alarma usa el reloj del sistema (`AlarmClock`) | Suena aunque la app esté cerrada y el usuario la ve en su Reloj de siempre |
| Los ViewModels dependen de la interfaz `ContactRepository`, no de Room | Capa Repository de MVVM: se prueban con un repositorio en memoria y se pudo cifrar la base de datos en el paso 8 sin modificar ningún ViewModel |
| El botón verde de llamar en Contactos también pasa por Confirmar | RF-10 sin excepciones: evita llamadas por un toque accidental |
| Llamada con `ACTION_CALL` y respaldo `ACTION_DIAL` | Decisión abierta 4 del diseño: llama directo tras confirmar; si no hay permiso, abre el teléfono con el número marcado |
| El permiso de llamadas se pide al guardar el primer contacto | Es cuando el cuidador configura la app; se explica antes en lenguaje simple (RNF-06) |
| Al iniciar una llamada la app no habla (solo muestra el resultado) | Excepción justificada al RF-13: la voz sonaría encima de la llamada |
| Validación de teléfono en un solo lugar (`ContactValidator`) | La usan el formulario, la importación desde la agenda y el `CommandProcessor`; evita reglas duplicadas que se contradigan |
| Mensajes con `ACTION_SENDTO`: se abre Mensajes con el texto listo y el usuario toca Enviar | Decisión abierta 5 del diseño, **ratificada por el equipo tras probarla** (06-11-2026): no requiere el permiso peligroso `SEND_SMS`, que Google Play restringe, y el usuario ve el SMS antes de enviarlo |
| Al dictar desde Mensajes, la pregunta "¿Qué le quieres decir?" se muestra pero no se dice | El micrófono ya está escuchando y captaría la voz de la app |
| Permiso del micrófono en un componente reutilizable (`MicPermissionRequester`) | Inicio y Mensajes lo necesitan; evita duplicar lógica de la View |
| Rangos seguros de las preferencias en el dominio (texto 100–160 %, voz 20–100 %) | Ningún valor guardado puede dejar la app ilegible o muda |
| Tamaño de texto con `fontScale` en `MainActivity`, leído al iniciar | Android exige definirlo antes de dibujar cualquier vista (antes de que existan los ViewModels); al cambiarlo la Activity se redibuja y conserva la pantalla |
| Volumen de la voz por frase (`KEY_PARAM_VOLUME` de TextToSpeech) | Cambia solo la voz de la app, no el volumen general del teléfono |
| Desactivar la respuesta por voz no oculta nada en pantalla | La información siempre se muestra; RF-13 se cumple por defecto y el usuario puede preferir solo pantalla |
| SQLCipher para cifrar Room, con clave aleatoria protegida por el Android Keystore | Lo indica el §8 del diseño; la clave nunca queda en el código ni en un archivo sin proteger. Costo: la app pasa de ~7 MB a ~27 MB (código nativo para 4 procesadores; con App Bundle cada teléfono descarga solo el suyo) |
| Al instalar la versión cifrada se elimina la base antigua sin cifrar | No puede quedar ningún dato personal en texto plano; los contactos de prueba se volvieron a agregar (acordado con el equipo) |
| PIN solo para Ajustes y cambios en Contactos; ver y llamar siguen libres | El §8 busca evitar cambios accidentales, no impedir que el adulto mayor use la app |
| PIN guardado como hash PBKDF2 con sal, se bloquea al salir de la pantalla | Aunque se lea el archivo no se obtiene el PIN; no queda desbloqueado por olvido |
| Todos los diálogos con `AppDialog` (botones fijos abajo) | Diseño responsive: funciona en pantallas pequeñas, con texto al 160 % y con teclado abierto |
| Agregar contactos eligiendo en la agenda (sin `READ_CONTACTS`) en vez de importar todos | Decidido con el equipo (18-11-2026): no requiere permisos (RNF-06) y mantiene la lista corta de frecuentes |
| El nombre de la alarma es opcional: se dice en la frase o con "Ponerle nombre", nunca se pregunta siempre | Preguntar siempre sería un paso más para un adulto mayor; sin nombre la alarma se llama "Withyou" |
| "Recuérdame…" y "recordatorio…" crean una alarma con nombre | Encaja con el uso más frecuente del público objetivo (medicamentos, citas) sin agregar una función nueva |
| Decir "no" al pedir el nombre vuelve a la confirmación | No se pierde la alarma por arrepentirse de ponerle nombre |
| Cadena de respaldo de reconocedores de voz | Cada marca y modelo trae reconocedores distintos (Android Go sin motor sin conexión, Samsung con un reconocedor solo local); probar alternativas antes de mostrar un error hace que la app funcione en más teléfonos sin pedir configuración al usuario (RNF-07) |

## 10. Supuestos y limitaciones

- **Android Go requiere internet para reconocer voz.** El sistema no permite descargar el idioma para uso sin conexión. La síntesis de voz sí funciona sin conexión. Alternativa evaluable: reconocedor incluido en la app (p. ej. Vosk, ~40 MB, vocabulario restringido), que implica cambiar el stack y requiere una prueba de concepto midiendo RAM, latencia y precisión.
- **Motor de voz:** el dispositivo debe tener activo "Servicios de voz de Google".
- **Alarmas:** `AlarmClock` programa la alarma para la próxima vez que llegue esa hora; "mañana a las 8" dicho a las 7:00 sonaría el mismo día. Pendiente de definir con el equipo.
- **PIN olvidado:** no hay forma de recuperarlo desde la app; el cuidador debe borrar los datos de Withyou desde los Ajustes del teléfono (se pierden también los contactos frecuentes).
- **Nombre de la alarma:** "para una reunión" no se reconoce como nombre, porque "una" se confunde con "la una"; en ese caso se usa el botón "Ponerle nombre".
- **Historial de comandos:** el diseño mencionaba guardarlo (`CommandLogEntity`), pero ningún requerimiento lo exige y no se implementó, para no almacenar datos personales sin necesidad.

## 11. Trabajo pendiente

Los 8 pasos del plan están completos. Queda:

1. **Pruebas con usuarios** (semana 14 del plan): probar con adultos mayores reales la tasa de frases entendidas, para decidir si las reglas del `IntentParser` bastan (decisión abierta 7).
2. **Prueba con TalkBack activado** por una persona, complementaria a la verificación automática de Espresso.
3. **Gestión:** publicar el repositorio en GitHub. Git ya está en uso localmente: `main` más una rama por paso (`feature/paso-4-…` a `feature/paso-8-seguridad-accesibilidad` y `feature/importar-contactos-agenda`).

### Mejoras futuras (fuera del alcance del MVP)

- **Envío automático de mensajes:** hoy se abre la app de Mensajes con el texto listo y el usuario toca Enviar. Se podría enviar directo con `SmsManager` y el permiso `SEND_SMS` (explicado antes de pedirlo, con la confirmación de la app y con respaldo a `ACTION_SENDTO` si se niega). Implica: permiso peligroso, restricción de Google Play para publicar la app y posibles cobros de SMS sin que el usuario vea la app de Mensajes. **Decisión del equipo (06-11-2026): mantener `ACTION_SENDTO` y evaluar este cambio a futuro.**
- **Reconocimiento de voz sin internet en Android Go:** evaluar un reconocedor incluido en la app (p. ej. Vosk), ver sección 10.
- **Escucha automática después de una pregunta** ("¿A qué hora?") sin tener que tocar el micrófono.

## 12. Entregable

| Elemento | Detalle |
|---|---|
| Aplicación | `Withyou-v0.1.2.apk` (versión de prueba *debug*, 27,3 MB), generada desde `main`. Incluye la corrección de la 0.1.1 (problema 10) y el nombre de las alarmas; se instala encima de versiones anteriores sin perder datos |
| Código fuente | Repositorio Git del proyecto (rama `main`) |
| Evidencia de pruebas | Reporte de JUnit en `app/build/reports/tests/testDebugUnitTest/index.html`; tiempos del RNF-03 en `docs/evidencia-rnf03-latencias.log` |

**Requisitos del teléfono:** Android 8.0 o superior, internet (en Android Go el reconocimiento de voz lo necesita) y "Servicios de voz de Google" activado.

## 13. Documentos de la metodología (25-11-2026)

El anexo "Artefactos y documentos solicitados por metodología" pide cuatro documentos para Cascada. Se redactaron a partir de la Fase 1 y de este informe, y están en `Fase 2/Evidencias Proyecto` del repositorio de entrega:

| Documento | Archivo | Páginas |
|---|---|---|
| Requerimientos (SRS simplificado), con la sección de innovación | `1_Documento_Requerimientos_SRS.docx` | 13 |
| Diseño: arquitectura, componentes, UML, modelo de datos, seguridad, despliegue y cambios respecto a la Fase 1 | `2_Documento_Diseno.docx` | 13 |
| Plan de pruebas y evidencias | `3_Plan_Pruebas_Evidencias.docx` | 9 |
| Manual técnico y de despliegue | `4_Manual_Tecnico_Despliegue.docx` | 6 |

Los diagramas (arquitectura, componentes, casos de uso, clases, secuencia y modelo de datos) están en `docs/diagramas/`, en formato Mermaid y como imagen.

**Decisión (25-11-2026):** el anexo pide Docker para todos los proyectos, pero **no aplica** a Withyou: es una app nativa que corre completa en el teléfono, sin servidor ni base de datos externa. Se justifica en el Documento de Diseño (sección 8) y en el Manual Técnico.

**Instalación:** abrir el APK, permitir la instalación desde esa fuente y, si Google Play Protect lo advierte, elegir "Instalar de todas formas" (la app no viene de la tienda). La versión *debug* está firmada con una clave de desarrollo; para entregar a usuarios conviene una versión *release* firmada con una clave del proyecto.
