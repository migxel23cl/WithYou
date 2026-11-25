# Nota de traspaso — Withyou

> 25-11-2026 · Versión de la app: **0.1.2** · Preparada por Miguel Pereira (jefe de proyecto)
> Para quien continúe el proyecto: el equipo (Baltazar, José Luis, Joaquín), otra sesión de trabajo o una IA.
> El informe completo está en [`avance-fase2.md`](avance-fase2.md); esta nota resume lo necesario para **seguir trabajando**.

---

## 1. Estado en una línea

**La Fase 2 (Desarrollo) está terminada:** los 8 pasos del plan, los 13 RF, los 8 RNF y los 8 casos de uso funcionan y están validados en **dos teléfonos** (Samsung J2 Core de gama baja y Samsung S25 FE de gama alta). Todo está en `main`, con **158 pruebas unitarias y 18 en el teléfono**, todas aprobadas. Después del plan se agregaron: importar contactos desde la agenda, la corrección del reconocimiento de voz (0.1.1) y el nombre de las alarmas por voz (0.1.2). **Falta:** pruebas con adultos mayores y una prueba manual con TalkBack. El repositorio ya está publicado en GitHub, con los documentos de la metodología (25-11-2026).

## 2. Qué se construyó (resumen por paso)

| Paso | Resultado | Rama |
|---|---|---|
| 1 | Proyecto Android: Kotlin, Views/XML, MVVM, Navigation, Room, DataStore | `main` (commit base) |
| 2 | Núcleo de interpretación en Kotlin puro: `IntentParser` + `CommandProcessor`, con pruebas JUnit | `main` (commit base) |
| 3 | Voz a texto (`SpeechRecognizer` es-CL) y texto a voz (`TextToSpeech`); pantalla Inicio | `main` (commit base) |
| 4 | Hora, fecha, linterna, volumen y **alarma con confirmación**; pantallas Confirmar, Aclarar y Resultado | `feature/paso-4-alarma-confirmacion` |
| 5 | **Contactos frecuentes** (Room + `ContactRepository`) y **llamadas** con confirmación | `feature/paso-5-contactos-llamadas` |
| 6 | **Mensajes** dictados, leídos en voz alta y confirmados; pantalla Mensajes | `feature/paso-6-mensajes` |
| 7 | **Preferencias** (DataStore): tamaño de texto, volumen de voz, respuesta por voz; pantalla Ajustes | `feature/paso-7-preferencias` |
| 8 | **Cifrado** (SQLCipher + Keystore), **PIN del cuidador**, accesibilidad, **diseño responsive**, Espresso y mediciones | `feature/paso-8-seguridad-accesibilidad` |
| Extra | **Agregar contactos desde la agenda** del teléfono, sin permisos | `feature/importar-contactos-agenda` |
| Corrección | **Reconocimiento de voz en más teléfonos** (Samsung S25 FE): cadena de respaldo de reconocedores. Versión 0.1.1 | `fix/reconocimiento-sin-idioma-offline` |
| Extra | **Nombre de la alarma por voz** ("para tomar la pastilla", "recuérdame…") y botón "Ponerle nombre" en Confirmar. Versión 0.1.2 | `feature/nombre-alarma` |

Todas las ramas ya están unidas con `main`. Se pueden borrar o conservar como historial.

## 3. Dónde está cada cosa

| Qué | Dónde |
|---|---|
| Código fuente | `C:\Users\Miguel Pereira\Desktop\Claude Code\WithYou` (repositorio Git, rama `main`) |
| App instalable | `Escritorio\Withyou-v0.1.2.apk` (versión *debug*, 27,3 MB). Las versiones anteriores quedaron obsoletas |
| Informe de la Fase 2 | `docs/avance-fase2.md` (copia en `Escritorio\Informe-Fase2-Withyou.md`) |
| Portada para GitHub | `README.md` |
| Repositorio de entrega (GitHub) | https://github.com/migxel23cl/WithYou · copia local en `Escritorio\Capstone Entrega\WithYou` (evidencias + código en `WithYou/`) |
| Documentos de Cascada (SRS, Diseño, Pruebas, Manual) | `Fase 2/Evidencias Proyecto` del repositorio de entrega |
| Diagramas UML, ER y arquitectura | `docs/diagramas/` (fuente Mermaid + imagen) |
| Contexto, reglas y estado del proyecto | `CLAUDE.md` (lo lee Claude Code al iniciar cada sesión) |
| Evidencia del tiempo de respuesta | `docs/evidencia-rnf03-latencias.log` |
| Reporte de pruebas unitarias | `app/build/reports/tests/testDebugUnitTest/index.html` (se genera al correr las pruebas) |
| Presentación de avance | `Capstone FASE 1\…\Fase 2\Evidencias Proyecto\Grupo 6_Presentacion_APP_Accesibilidad_Adulto_Mayor_Fase2.pptx` |

## 4. Cómo ponerlo a andar en otro computador

1. Instalar **Android Studio** con el **SDK 34** y **JDK 17**.
2. Copiar o clonar el repositorio y abrirlo en Android Studio. Android Studio crea `local.properties` con la ruta del SDK; ese archivo **no se sube a Git** porque cada computador tiene la suya.
3. Conectar un teléfono con **depuración USB** y presionar **Run**.

**Ojo con la firma:** la versión *debug* se firma con una clave distinta en cada computador. Si un teléfono tiene la app instalada desde otro computador, hay que **desinstalarla primero**, y eso borra sus contactos.

## 5. Arquitectura: lo que hay que respetar

**MVVM por capas con Repository** e inyección de dependencias manual (`di/AppContainer.kt`).

```
View (Fragments + XML)  ──eventos──►  ViewModel (StateFlow)  ──►  Dominio (Kotlin puro)
                        ◄──estado───                          ──►  Repository (Room cifrado, DataStore)
                                                              ──►  Servicios (interfaces → APIs de Android)
```

**Reglas** (todas revisadas con búsquedas automáticas sobre el código):

1. **El ViewModel nunca conoce la View** ni un `Context` de Activity.
2. **`domain/` no importa nada de Android**, y por eso se prueba con JUnit.
3. **Las APIs del teléfono van detrás de interfaces** (`SpeechService`, `TtsService`, `ActionExecutor`, `PhoneContactReader`).
4. **Los ViewModels usan los Repository**, nunca Room ni DataStore directamente.
5. **Ningún texto escrito en el código:** todo va en `strings.xml`, en español.

**Piezas clave para orientarse:**
- **`AssistantViewModel`** (`ui/assistant/`) es el único ViewModel de la conversación. Lo comparten Inicio, Confirmar, Aclarar y Resultado. Guarda qué está pendiente: una confirmación, una elección o un dato faltante.
- **`MainActivity`** muestra la pantalla que indica `AssistantUiState.screen`. Los Fragments no navegan entre ellos.
- **Los demás ViewModels** son `ContactsViewModel`, `MessagesViewModel`, `SettingsViewModel` y `SecurityViewModel` (PIN).
- **Cómo se entiende una frase:** `IntentParser` propone hasta 3 intenciones con su confianza, y `CommandProcessor` decide si ejecutar, confirmar, aclarar, pedir un dato o rechazar.
- **Nombre de la alarma:** `AlarmLabelExtractor` separa el nombre ("para tomar la pastilla", "que diga…", "recuérdame…") **antes** de entender la hora, y `AlarmLabelPolicy` lo limpia (máximo 40 caracteres). Se guarda en `SetAlarm.label`; desde Confirmar se dicta con el dato faltante `ALARM_LABEL`.
- **Reconocimiento de voz:** `AndroidSpeechService` prueba una cadena de reconocedores (sin conexión → en línea → otros instalados, primero Google). Si falla en un teléfono nuevo, el registro `WithYou.Speech` dice por qué.
- **`di/AppContainer`** crea una sola vez cada pieza (base de datos, repositorios, servicios, parser) y se la entrega a los ViewModels. Para cambiar una implementación se toca solo ese archivo.

## 6. Convenciones del equipo

- **Git:** una rama por funcionalidad (`feature/…`) y commits en español. Se une a `main` **solo después de probar en el teléfono**.
- **Toda funcionalidad nueva va con pruebas.** El parser se prueba con tablas de frases reales.
- **Antes de agregar una dependencia, se explica por qué.** Las actuales son SQLCipher (cifrado), RecyclerView y espresso-accessibility (solo pruebas).
- **Cada vez que se termina algo**, se actualizan `docs/avance-fase2.md` y la sección "Estado actual" de `CLAUDE.md`.
- **Diseño responsive:**
  - Todo diálogo nuevo usa `AppDialog` (botones fijos abajo).
  - En las pantallas con botones de acción, los botones van **fuera** del `ScrollView`.
  - Nada de alturas fijas en contenedores con texto.
  - `ResponsiveUiTest` revisa todo esto con texto al 160 %.

## 7. Cómo probar

| Pruebas | Comando | Notas |
|---|---|---|
| Unitarias (158) | `.\gradlew.bat testDebugUnitTest` | Sin teléfono, unos segundos |
| En el teléfono (18) | `.\gradlew.bat assembleDebug assembleDebugAndroidTest`, instalar ambos APK con `adb install -r` y luego `adb shell am instrument -w cl.withyou.app.test/androidx.test.runner.AndroidJUnitRunner` | Ver trampas abajo |

**Trampas conocidas de las pruebas en el teléfono:**
- **No usar `connectedAndroidTest`:** Gradle desinstala la app al terminar y se pierden los contactos y permisos. Por eso se usa `am instrument`.
- **Las pruebas de interfaz (Espresso) necesitan el teléfono desbloqueado** y sin tocarlo.
- **Si el usuario dejó un PIN,** `WithoutPinRule` lo quita durante la prueba y lo restaura al terminar.
- **Al terminar,** desinstalar el APK de pruebas con `adb uninstall cl.withyou.app.test`.

## 8. Trampas del teléfono de prueba (Samsung J2 Core, Android 8.1 Go)

| Síntoma | Causa | Qué hacer |
|---|---|---|
| `adb devices` muestra **"unauthorized"** | Se pierde la autorización de depuración | Desbloquear el teléfono y aceptar "¿Permitir depuración USB?" (marcar "Permitir siempre") |
| "No hay conexión a internet" teniendo internet | Android Go no tiene reconocimiento sin conexión | Ya está resuelto en el código (usa el modo en línea); solo hay que tener internet |
| "Este teléfono no tiene reconocimiento de voz disponible" en un teléfono nuevo (p. ej. S25 FE) | Falta el español sin conexión o el reconocedor predeterminado es solo local | Resuelto en la versión 0.1.1 (cadena de respaldo). Si vuelve a pasar en otro modelo, revisar el registro `WithYou.Speech`: muestra el código de error y los reconocedores del teléfono |
| La app no habla | "Servicios de voz de Google" desactivado | Activarlo en Ajustes → Aplicaciones |
| Las llamadas dan "sin red móvil" | El teléfono de prueba no tiene chip | Es correcto: la app sí entregó la llamada |
| Se pierden líneas del registro (Logcat) | El registro del teléfono es de solo 256 KB | Guardar el registro en el computador mientras se prueba (`adb logcat ... > archivo`) |

## 9. Decisiones importantes ya tomadas (no reabrir sin motivo)

- **Mensajes con `ACTION_SENDTO`:** se abre Mensajes con el texto listo y el usuario toca Enviar. El envío automático (`SmsManager`) quedó como mejora futura (decisión del 06-11-2026).
- **Agregar desde la agenda eligiendo un contacto a la vez,** sin `READ_CONTACTS` (decisión del 18-11-2026).
- **El PIN solo protege Ajustes y los cambios en Contactos.** Llamar, enviar mensajes y hablar con la app siguen sin PIN.
- **Toda acción crítica pasa por Confirmar,** incluido el botón verde de llamar en Contactos y las opciones elegidas en "No entendí bien".
- **"A las 8" sin mañana ni noche pide aclaración,** porque una alarma a la hora equivocada es un error grave para este usuario.
- **El nombre de la alarma es opcional:** se dice en la frase o con el botón "Ponerle nombre"; nunca se pregunta siempre. Sin nombre, la alarma se llama "Withyou". Decir "no" al pedir el nombre vuelve a la confirmación sin perder la alarma.
- **"Recuérdame…" crea una alarma con nombre,** no una llamada ni un mensaje ("recuérdame llamar a María a las cinco").
- **Docker no aplica** (decisión del 25-11-2026): Withyou es una app nativa sin servidor. Se justifica en el Documento de Diseño y en el Manual Técnico; no agregar Docker sin consultarlo con el equipo.

Lista completa con sus justificaciones: sección 9 del informe.

## 10. Pendiente y próximos pasos

**Por hacer (en este orden sugerido):**
1. **Publicar en GitHub.** Crear un repositorio **privado y vacío**, dar acceso al equipo y al profesor, y luego:
   ```bash
   git remote add origin https://github.com/USUARIO/withyou.git
   ```
   ```bash
   git push -u origin main
   ```
2. **Pruebas con usuarios** (semana 14): medir cuántas frases de adultos mayores reales entiende la app. Si falla mucho, revisar las reglas del `IntentParser` (decisión abierta 7).
3. **Prueba manual con TalkBack activado.** La verificación automática de Espresso ya pasa, pero falta que una persona recorra la app escuchándola.
4. **Versión *release*** firmada con una clave del proyecto, si se va a entregar a usuarios o publicar. Conviene un App Bundle, que reduce el tamaño de unos 27 MB a unos 10 MB.

**Pendientes de definir con el equipo:**
- **Alarmas de "mañana":** "mañana a las 8" dicho a las 7:00 suena el mismo día, por cómo funciona el Reloj del sistema.
- **Recuperar un PIN olvidado:** hoy solo se puede borrando los datos de la app.
- **"Alarma para una reunión":** no se reconoce como nombre porque "una" se confunde con "la una". Hoy se resuelve con el botón "Ponerle nombre".

**Mejoras futuras (fuera del MVP):**
- Envío automático de SMS.
- Reconocimiento de voz sin internet en Android Go (por ejemplo con Vosk).
- Escuchar automáticamente después de una pregunta, sin tener que tocar el micrófono.

## 11. Para continuar con Claude Code

Abrir Claude Code en la carpeta del proyecto: `CLAUDE.md` se carga solo, con el contexto, las reglas y el estado. Basta con pedir la tarea, por ejemplo "prepara la versión release" o "publica el repositorio en GitHub". Conviene recordarle que lea esta nota y `docs/avance-fase2.md` si la tarea es grande.
