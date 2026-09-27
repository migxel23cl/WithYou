# CLAUDE.md — Withyou: App de accesibilidad por voz para adultos mayores

> Contexto para Claude Code. Colócalo en la raíz del repositorio Android. Responde y comenta en **español**.

## 1. Qué estamos construyendo

App **Android nativa** que permite a adultos mayores usar su teléfono **hablándole en lenguaje natural** (español de Chile): llamar, enviar mensajes, poner alarmas, encender la linterna, subir/bajar volumen y preguntar la hora/fecha, sin navegar menús.

Flujo base de toda interacción:
1. **Instrucción hablada**: "pon una alarma para mañana a las ocho".
2. **Reconocimiento e interpretación**: voz → texto → intención + parámetros.
3. **Confirmación**: "Entendí que quieres una alarma para mañana a las 8:00. ¿La configuro?"
4. **Ejecución y retroalimentación**: se ejecuta la acción y se confirma **por voz y en pantalla a la vez**.

No es un asistente general. Es una capa de accesibilidad acotada a unas pocas tareas cotidianas, con **confirmación de acciones críticas** y **tolerancia a errores** (nunca falla en silencio: si no entiende, ofrece opciones).

- Contexto: proyecto Capstone de Duoc UC (Ing. en Informática), profesor Aldo Martínez, metodología **Cascada**. Ya terminó la Fase 1 (Inicio, análisis y diseño documentados). **Ahora empieza la Fase 2: desarrollo.**
- Equipo: Miguel Pereira (jefe de proyecto), Baltazar Ordoñez (voz e interpretación), José Luis Donoso (interfaz y accesibilidad), Joaquín Mendoza (pruebas y documentación técnica).
- El alcance es limitado por tiempo (los riesgos altos identificados son alcance excesivo, ambigüedad del lenguaje natural y falta de tiempo). **Prioriza un MVP que funcione de punta a punta antes de sumar funcionalidades.**

## 2. Stack definido (no cambiar sin avisar)

| Componente | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| Arquitectura | MVVM por capas + Repository |
| UI | Android Views + **XML** (no Compose), ViewBinding, Navigation Component |
| Estado | ViewModel + StateFlow (o LiveData), coroutines |
| Voz → texto | `android.speech.SpeechRecognizer` + `RecognitionListener` |
| Texto → voz | `android.speech.tts.TextToSpeech` |
| Interpretación | `IntentParser` **local, basado en reglas y patrones**, hecho desde cero (Dialogflow se evaluó como alternativa, no se usa) |
| Acciones | `AlarmClock`/`AlarmManager`, Intents (`ACTION_DIAL`/`ACTION_CALL`, `ACTION_SENDTO`), `PackageManager`, `CameraManager` (linterna), `AudioManager` (volumen) |
| Datos | **Room** (contactos frecuentes, historial) + **Jetpack DataStore** (preferencias: tamaño de texto, volumen de voz, retroalimentación auditiva) |
| Pruebas | JUnit (lógica) + Espresso (UI) |
| Versionado | Git / GitHub |

## 3. Arquitectura

```
View (Activity/Fragment + XML)
   │  eventos UI (tocar micrófono, "Sí", "Cancelar")
   ▼
ViewModel  ── expone UiState (StateFlow) ──►  View observa y se redibuja
   │  peticiones
   ▼
Dominio: IntentParser → CommandProcessor
   │
   ├── Datos: ContactRepository (Room), PreferencesRepository (DataStore)
   └── Servicios: SpeechService, TtsService, ActionExecutor (APIs nativas)
```

**Reglas:**
- La View conoce al ViewModel; **el ViewModel nunca conoce a la View** (ni `Context` de Activity). Usa `Application` context o inyecta los servicios.
- `IntentParser` y `CommandProcessor` son **Kotlin puro, sin dependencias de Android**, para poder probarlos con JUnit.
- Las APIs nativas quedan detrás de interfaces (`ActionExecutor`, `SpeechService`, `TtsService`) para poder reemplazarlas por fakes en las pruebas.
- Inyección de dependencias simple (un `AppContainer` manual o Hilt, lo que sea más simple de mantener para el equipo).

### Estructura de paquetes sugerida
```
cl.withyou.app
├── ui/            home/, confirm/, dictate/, clarify/, contacts/, result/, settings/
├── domain/
│   ├── model/     Intent (sealed class), Slot, ParseResult, Decision
│   ├── parser/    IntentParser, TextNormalizer, TimeExtractor, ContactMatcher
│   └── processor/ CommandProcessor
├── data/
│   ├── local/     AppDatabase (Room), ContactDao, ContactEntity, CommandLogEntity
│   ├── prefs/     PreferencesRepository (DataStore)
│   └── repository/ContactRepository
├── services/      SpeechService, TtsService, ActionExecutor (+ implementaciones Android)
└── di/            AppContainer
```

## 4. Diseño del núcleo: IntentParser + CommandProcessor

**Intenciones (sealed class):** `SetAlarm(hora, minuto, dia)`, `Call(contacto)`, `SendMessage(contacto, texto?)`, `Flashlight(on/off)`, `Volume(up/down)`, `AskTime`, `AskDate`, `Confirm`, `Deny`, `Unknown`.

**IntentParser** (texto → `ParseResult` con intención candidata(s) + confianza 0..1):
1. Normalizar: minúsculas, quitar tildes y signos, colapsar espacios.
2. Reglas por intención con palabras clave/regex en español coloquial:
   - Alarma: "alarma", "despiértame", "ponme una alarma", "a las 8", "a las ocho y media", "8:30", "de la mañana/tarde/noche", "mañana", "hoy".
   - Llamada: "llama a X", "llámame a X", "quiero hablar con X", "marca a X".
   - Mensaje: "mándale/envíale un mensaje a X", "escríbele a X", "dile a X que ...".
   - Linterna: "enciende/prende/apaga la linterna/luz".
   - Volumen: "sube/baja el volumen", "más fuerte", "más bajo".
   - Hora/fecha: "qué hora es", "qué día es hoy", "qué fecha es".
   - Sí/No: "sí", "dale", "ya", "confirmo", "correcto" / "no", "cancela", "mejor no".
3. Extraer parámetros: números en palabras ("ocho", "ocho y media", "un cuarto para las nueve") y dígitos; contacto con **coincidencia difusa** (Levenshtein/Jaro-Winkler) contra los contactos frecuentes.
4. Calcular confianza y devolver hasta 3 candidatos.

**CommandProcessor** (ParseResult → `Decision`):
- Confianza alta + acción **no crítica** (linterna, volumen, hora/fecha) → `Execute`.
- Acción **crítica** (llamada, mensaje, alarma, cambios de configuración) → `AskConfirmation` (RF-10, CU-08).
- Parámetro faltante o ambiguo ("alarma en la tarde", dos contactos "María") → `AskClarification` con opciones (RF-04, CU-06).
- Confianza baja → `AskClarification` con los candidatos ("¿Quisiste decir…?"). **Nunca** falla en silencio (RNF-07).
- Validar y sanitizar los parámetros antes de ejecutar (hora válida, número telefónico válido).

**Esto es lo más importante del proyecto: escríbelo primero y con muchas pruebas JUnit** (tabla de frases → intención esperada).

## 5. Requerimientos

### Funcionales
| ID | Requerimiento | Prioridad |
|---|---|---|
| RF-01 | Capturar audio por micrófono al activar el modo escucha | Alta |
| RF-02 | Convertir voz a texto (SpeechRecognizer) | Alta |
| RF-03 | Identificar la intención del usuario | Alta |
| RF-04 | Pedir aclaración ante ambigüedad (no solo decir "falló") | Alta |
| RF-05 | Crear alarmas por voz | Alta |
| RF-06 | Llamar a un contacto por voz | Alta |
| RF-07 | Redactar y enviar mensaje de texto por voz | Media |
| RF-08 | Linterna y volumen por voz | Media |
| RF-09 | Responder hora y fecha | Media |
| RF-10 | Confirmar acciones críticas antes de ejecutarlas | Alta |
| RF-11 | Registrar y editar contactos frecuentes | Media |
| RF-12 | Preferencias de accesibilidad (tamaño de texto, volumen de voz, etc.) | Media |
| RF-13 | Retroalimentación visual **y** auditiva simultánea de cada acción | Alta |

### No funcionales
| ID | Requerimiento | Prioridad |
|---|---|---|
| RNF-01 | Elementos grandes: texto ≥ 18sp (títulos más grandes), táctiles ≥ 56dp | Alta |
| RNF-02 | Máximo 2 niveles de navegación para cualquier función principal | Alta |
| RNF-03 | Respuesta a comando de voz ≤ 3 s en condiciones normales | Media |
| RNF-04 | Estable en equipos de 1–1,5 GB de RAM (app liviana, sin modelos pesados) | Media |
| RNF-05 | Datos personales locales cifrados (contactos, historial) | Alta |
| RNF-06 | Solo los permisos estrictamente necesarios, pedidos en tiempo de ejecución explicando para qué | Alta |
| RNF-07 | Manejo de comandos no reconocidos sin fallos silenciosos ni cierres | Alta |
| RNF-08 | Compatible con TalkBack (`contentDescription`, orden de foco, etc.) | Media |

## 6. Casos de uso

- **CU-01 Alarma**: "pon una alarma a las 8" → confirma la hora → crea → confirma por voz y en pantalla. Si la hora es ambigua ("en la tarde") → pide aclaración.
- **CU-02 Llamada**: "llama a María" → busca en frecuentes → pide confirmación → llama. Si hay varios nombres parecidos → pide elegir.
- **CU-03 Mensaje**: "envíale un mensaje a Pedro" → pide el contenido → el usuario dicta → la app lo lee en voz alta → el usuario confirma. Puede repetirlo para corregirlo.
- **CU-04 Funciones**: "enciende la linterna" / "sube el volumen" → ejecuta → retroalimentación.
- **CU-05 Hora/fecha**: "¿qué hora es?" → responde por voz y en pantalla.
- **CU-06 Aclaración**: comando poco claro → "¿Quisiste decir…?" con opciones → el usuario elige → se ejecuta.
- **CU-07 Contactos frecuentes**: el usuario o un cuidador agrega/edita nombre y número; quedan disponibles para los comandos.
- **CU-08 Confirmar acción crítica**: "¿Confirmas que quieres…?" → "sí"/"no" (por voz o botón) → ejecuta o cancela.

## 7. Pantallas (según mockup de la Fase 1)

Estilo: fondo celeste claro, barra superior azul oscuro con título blanco y botón volver circular, tarjetas blancas con bordes redondeados, íconos grandes, tipografía redondeada y en negrita. Colores aproximados: azul primario `#1F4E8C`, fondo `#E6F1FB`, tarjeta de acento `#CFE5F7`, verde de confirmación `#1E9E57`, rojo de cancelar `#D32F2F`.

1. **Inicio**: fecha arriba, "¿En qué puedo ayudarte?", **botón circular grande con micrófono ("Toca para hablar")**, grilla 2×2 de "Acciones rápidas" (Llamar, Mensajes, Alarma, Linterna) y barra inferior (Inicio, Mensajes, Contactos, Ajustes).
2. **Confirmar acción**: tarjeta "Comando detectado · ¿Llamar a María?" con el número, burbuja del asistente ("Voy a llamar a María. ¿Confirmas?"), botón verde grande **"Sí, llamar"** y botón rojo grande **"Cancelar"**. También acepta "sí"/"no" por voz.
3. **Dictar mensaje**: "¿A quién le escribes?" con lista de contactos seleccionables (check) y micrófono grande "Dictar mensaje".
4. **No entendí bien**: "¿Quisiste decir alguna de estas opciones?" con 3 opciones numeradas (p. ej. "Llamar a María", "Mensaje a María", "Alarma para mañana") y el botón "Intentar de nuevo".
5. **Contactos**: botón "Agregar contacto" y lista con avatar, nombre, número y botón verde de llamar.
6. **Resultado**: check verde grande, "Alarma creada para las 8:00", indicador "Reproduciendo confirmación…", "Volviendo al inicio en unos segundos…" y botón "Volver al inicio".
7. **Preferencias**: slider de tamaño de texto con vista previa, slider de volumen de voz y switch de retroalimentación auditiva.

Todo a ≤ 2 niveles desde Inicio (RNF-02).

## 8. Seguridad

- Mínimo privilegio y permisos en runtime con explicación previa en lenguaje simple.
- Cifrar contactos e historial (p. ej. Room + SQLCipher, con clave en Android Keystore).
- Confirmación obligatoria antes de llamadas, mensajes y cambios de configuración.
- PIN simple para entrar a Ajustes/Contactos (evita cambios accidentales; el cuidador lo administra).
- Validar y sanitizar lo interpretado antes de pasarlo a un Intent.
- No enviar datos a terceros sin consentimiento (Ley 19.628, Chile). No registrar el audio.

## 9. Decisiones abiertas (valores por defecto propuestos, confirmar con el equipo)

1. **minSdk**: propuesto **26 (Android 8.0)**, que cubre equipos antiguos de poca RAM; targetSdk el más reciente estable.
2. **Contactos: Room vs DataStore**: los documentos no coinciden (CU-07 dice DataStore, el doc de arquitectura dice solo DataStore y el contexto del proyecto dice Room). **Usar Room** para contactos/historial y DataStore solo para preferencias.
3. **Alarmas**: usar el Intent `AlarmClock.ACTION_SET_ALARM` (+ `EXTRA_SKIP_UI`, permiso normal `SET_ALARM`), que crea la alarma en el reloj del sistema. `AlarmManager` solo sirve para alarmas propias de la app.
4. **Llamadas**: `ACTION_DIAL` no pide permiso, pero obliga a apretar "llamar" en el marcador. `ACTION_CALL` + `CALL_PHONE` llama directo después de nuestra confirmación (encaja con el botón "Sí, llamar" del mockup). Propuesta: `ACTION_CALL` con fallback a `ACTION_DIAL` si se niega el permiso.
5. **SMS**: `ACTION_SENDTO` (`smsto:`) no pide permiso, pero abre la app de mensajes. `SmsManager` envía directo, pero necesita `SEND_SMS` y Google Play lo restringe. Propuesta MVP: `ACTION_SENDTO`.
6. **Offline / latencia**: probar `RecognizerIntent.EXTRA_PREFER_OFFLINE` y el paquete de idioma es-CL; medir el tiempo contra el RNF-03. Usar `EXTRA_LANGUAGE = "es-CL"`.
7. **Reglas vs NLU**: empezar con reglas; si fallan mucho en pruebas con usuarios, evaluar una alternativa más adelante.

## 10. Orden de trabajo sugerido (MVP primero)

1. Crear el proyecto (Kotlin, Views/XML, ViewBinding, Navigation, Room, DataStore, coroutines), el `.gitignore` y el esqueleto de paquetes.
2. `domain/`: modelos + `TextNormalizer` + `TimeExtractor` + `IntentParser` + `CommandProcessor` **con pruebas JUnit**.
3. `SpeechService` + `TtsService` (es-CL) y la pantalla de Inicio con el micrófono, mostrando el texto reconocido.
4. Flujo completo de punta a punta de **hora/fecha** (sin permisos) → **linterna/volumen** → **alarma** con confirmación.
5. Room + pantalla de Contactos + **llamada** con confirmación y aclaración.
6. **Mensaje** (dictado + lectura en voz alta + confirmación).
7. Preferencias (DataStore) aplicadas a toda la UI y al TTS.
8. Accesibilidad (TalkBack), cifrado, PIN, pruebas Espresso y medición del RNF-03.

## 11. Convenciones

- Código en inglés (clases y métodos); **textos de UI y voz en español** en `strings.xml` (nada hardcodeado).
- Commits pequeños y descriptivos; una rama por funcionalidad.
- Cada funcionalidad nueva va con pruebas. El parser se prueba con tablas de frases reales.
- Antes de agregar una dependencia nueva, explica por qué.
