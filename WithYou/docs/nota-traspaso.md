# Nota de traspaso: Withyou, de la Fase 2 a la Fase 3

> 27-11-2026 · Versión de la app: **0.1.2** · Preparada por Miguel Pereira (jefe de proyecto)
> Para quien continúe el proyecto en la Fase 3: el equipo (Baltasar, José Luis, Joaquín), otra sesión de trabajo o una IA.
> El informe completo de la Fase 2 está en [`avance-fase2.md`](avance-fase2.md). Esta nota resume lo necesario para **cerrar el proyecto**.

---

## 1. Estado en una línea

**La Fase 2 (Desarrollo) está terminada y entregada en GitHub** (último push: 25-11-2026, revisado el 27-11-2026).

- La app cumple los 13 RF, los 8 RNF y los 8 casos de uso, y está validada en dos teléfonos: Samsung J2 Core (gama baja) y Samsung S25 FE (gama alta).
- Las **158 pruebas unitarias pasan** (se ejecutaron de nuevo el 27-11-2026). También hay 18 pruebas en el teléfono.
- Las evidencias de la Fase 2 están completas, con los nombres exactos del Excel del curso, y se agregaron los 4 documentos que pide la metodología Cascada.

**Ahora empieza la Fase 3 (Cierre, semanas 15 a 18):** pruebas con adultos mayores, documentación final, presentación final y defensa.

## 2. Qué pide la Fase 3

Según el Excel «Resumen evidencias», con los **nombres exactos**. El profesor revisa que los archivos se llamen así.

| Carpeta | Archivo | Quién |
|---|---|---|
| `Fase 3/Evidencias Individuales` | `Apellido_Nombre_3.1_APT122_DiarioReflexionFase3.docx` (uno por integrante: `Pereira_Miguel_…`, `Ordóñez_Baltasar_…`, `Donoso_Jose_…`, `Mendoza_Joaquin_…`) | Cada integrante |
| `Fase 3/Evidencias Grupales` | `PLANILLA DE EVALUACIÓN FASE 3.xlsx` | Grupo |
| `Fase 3/Evidencias Grupales` | `Presentación Final del proyecto (Español).pptx` | Grupo |
| `Fase 3/Evidencias Grupales` | `Presentación Final del proyecto (Inglés).pptx` (**optativo**) | Grupo |

Además, el anexo «Artefactos y documentos solicitados por metodología» pide:

- Una **sección de innovación en el documento final de cierre**, que responda tres preguntas: ¿qué problema resuelve?, ¿qué hace diferente a la solución? y ¿qué valor agrega? El texto ya está redactado en el SRS (`Fase 2/Evidencias Proyecto/1_Documento_Requerimientos_SRS.docx`, sección 6); hay que copiarlo y ajustarlo.
- El **README** del repositorio actualizado. Debe tener: nombre, descripción, tecnologías, cómo ejecutar, integrantes con roles, metodología y arquitectura. Hoy los tiene todos; solo hay que actualizar el estado y los resultados.

Hoy la carpeta `Fase 3/` solo tiene `entrega 3.txt` (vacío). Ese archivo se usa para que exista la carpeta en GitHub. **Hay que borrarlo cuando se suban los archivos reales.**

## 3. Plan sugerido para la Fase 3

| # | Tarea | Responsable sugerido | Resultado |
|---|---|---|---|
| 1 | **Arreglar la evidencia vacía de la Fase 1:** `Mendoza_Joaquin_1.3_APT122_AutoevaluacionFase1.docx` pesa 2 bytes en GitHub | Joaquín | El archivo real subido con el mismo nombre |
| 2 | **Pruebas con adultos mayores** (estaban planificadas para la semana 14): 5 a 8 personas, una lista fija de tareas (alarma, llamada, mensaje, linterna, hora) y anotar cada frase que dicen | Joaquín (pruebas), con todo el equipo | Porcentaje de frases entendidas, errores y comentarios. Con eso se cierra el objetivo específico 4 y la decisión abierta 7 (reglas o NLU) |
| 3 | **Ajustar el `IntentParser`** con las frases que no entendió (cada frase nueva va con su prueba en `IntentParserTest`) | Baltasar | Nueva versión (0.1.3) solo si hace falta |
| 4 | **Prueba manual con TalkBack** activado, recorriendo todas las pantallas | José Luis | Lista de problemas o confirmación de que funciona |
| 5 | **Versión release firmada** (App Bundle o APK firmado con una clave del proyecto) | Miguel | APK final para la defensa. **La clave no se sube a GitHub** |
| 6 | **Presentación final**: partir de la presentación de la Fase 2 y del guion de 8:50 (`Escritorio\Guion presentación final — WithYou.docx`, 19-11-2026), y agregar los resultados de las pruebas con usuarios | Todo el equipo | `Presentación Final del proyecto (Español).pptx` |
| 7 | **Diarios de reflexión 3.1** | Cada integrante | 4 archivos con su nombre |
| 8 | **Documento de cierre** con la sección de innovación, y actualizar el README y estos documentos con los resultados finales | Miguel | Documentación final |
| 9 | **Revisar los nombres** de todos los archivos contra el Excel antes de subir | Miguel | Ningún archivo con nombre distinto |

Antes de empezar, conviene que el equipo decida las tres dudas abiertas de la sección 9.

## 4. Dónde está cada cosa

Hay **dos copias del proyecto**, y es importante no confundirlas:

| Qué | Dónde | Para qué |
|---|---|---|
| **Repositorio de desarrollo** | `Escritorio\Claude Code\WithYou` (rama `main`, **sin remoto**) | Aquí se programa y se prueba. Una rama por funcionalidad |
| **Repositorio de entrega** | `Escritorio\Capstone Entrega\WithYou` ↔ https://github.com/migxel23cl/WithYou | Lo que ve el profesor: evidencias por fase y el código en la subcarpeta `WithYou/` |
| Evidencias del curso | `Antonio Varas_AldoMartinez_802°D_Grupo 6/Fase 1`, `Fase 2` y `Fase 3` del repositorio de entrega | Individuales, grupales y del proyecto |
| Documentos de Cascada | `Fase 2/Evidencias Proyecto/`: `1_Documento_Requerimientos_SRS.docx`, `2_Documento_Diseno.docx`, `3_Plan_Pruebas_Evidencias.docx`, `4_Manual_Tecnico_Despliegue.docx` | Requerimientos, diseño (con UML y modelo de datos), pruebas y manual |
| Evidencias de sistema | `Fase 2/Evidencias Proyecto/Aplicacion_WithYou-v0.1.2.apk` y `Modelo_de_Datos.png` | La aplicación y la base de datos |
| Diagramas | `docs/diagramas/` (fuente Mermaid `.mmd` e imagen `.png`) | Arquitectura, componentes, casos de uso, clases, secuencia y modelo de datos |
| APK actual | `Escritorio\Withyou-v0.1.2.apk` (versión debug, 27,3 MB) | Instalar en teléfonos |
| Guion de la presentación final | `Escritorio\Guion presentación final — WithYou.docx` | Base para la Fase 3 |
| Presentación de avance de la Fase 2 | `Escritorio\Capstone FASE 1\…\Fase 2\Evidencias Proyecto\` | Ya no está en GitHub; solo se dejó la presentación final |
| Informe completo de la Fase 2 | `docs/avance-fase2.md` | Trazabilidad, pruebas, mediciones, problemas y decisiones |
| Contexto para Claude Code | `CLAUDE.md` | Se carga solo al abrir Claude Code en la carpeta |

**Para pasar código nuevo del repositorio de desarrollo al de entrega** (después de unir los cambios a `main`):

```bash
cd "Escritorio/Claude Code/WithYou"
git archive main | tar -x -C "../../Capstone Entrega/WithYou/WithYou"
```

Luego, en `Capstone Entrega\WithYou`, revisar los cambios y hacer commit y push.

**Para editar los documentos de Cascada:** se editan directamente en Word. Se generaron con un script temporal que no quedó guardado. Si cambia un diagrama, se edita su `.mmd` en `docs/diagramas/`, se vuelve a dibujar (por ejemplo, en mermaid.live) y se reemplaza la imagen en el Word.

## 5. Reglas de la entrega (aprendidas en la revisión del profesor)

1. **Los nombres de los archivos son exactamente los del Excel**, con tildes y paréntesis, y **sin prefijo de grupo**: nada de `Grupo6_` ni `Grupo 6_`. Las evidencias individuales usan `Apellido_Nombre_…`.
2. **Los archivos que el Excel no nombra** (documentos propios, imágenes, APK) llevan un nombre descriptivo sin prefijo, por ejemplo `Acta_De_Constitucion.docx`.
3. **Las carpetas también se llaman como en el Excel:** `Evidencias Individuales`, `Evidencias Grupales`, `Evidencias Proyecto` (singular).
4. **Una sola presentación por fase**, la final.
5. **No subir archivos vacíos ni con doble extensión** (`.docx.docx`). Hay que revisar el tamaño antes de subir.
6. **La página de GitHub no acepta archivos de más de 25 MB.** El APK pesa 27,3 MB, así que se sube con **GitHub Desktop**.
7. **Miguel hace los push.** Claude Code prepara los archivos en `Capstone Entrega\WithYou` y no sube nada por su cuenta.
8. **Docker no aplica** (decisión del 25-11-2026): Withyou es una app nativa sin servidor. Se justifica en el Documento de Diseño (sección 8) y en el Manual Técnico. **No agregar Docker ni otra tecnología o infraestructura sin consultar antes con el equipo.**

## 6. Cómo ponerlo a andar en otro computador

1. Instalar **Android Studio** con el **SDK 34** y **JDK 17**.
2. Clonar el repositorio y abrir la carpeta `WithYou/` en Android Studio. Android Studio crea `local.properties` con la ruta del SDK; ese archivo **no se sube a Git** porque cada computador tiene la suya.
3. Conectar un teléfono con **depuración USB** y presionar **Run**.

**Ojo con la firma:** la versión debug se firma con una clave distinta en cada computador. Si un teléfono tiene la app instalada desde otro computador, hay que **desinstalarla primero**, y eso borra sus contactos.

El paso a paso completo está en `4_Manual_Tecnico_Despliegue.docx`.

## 7. Arquitectura: lo que hay que respetar

**MVVM por capas con Repository** e inyección de dependencias manual (`di/AppContainer.kt`).

```
View (Fragments + XML)  ──eventos──►  ViewModel (StateFlow)  ──►  Dominio (Kotlin puro)
                        ◄──estado───                          ──►  Repository (Room cifrado, DataStore)
                                                              ──►  Servicios (interfaces → APIs de Android)
```

1. **El ViewModel nunca conoce la View** ni un `Context` de Activity.
2. **`domain/` no importa nada de Android**, y por eso se prueba con JUnit.
3. **Las APIs del teléfono van detrás de interfaces** (`SpeechService`, `TtsService`, `ActionExecutor`, `PhoneContactReader`).
4. **Los ViewModels usan los Repository**, nunca Room ni DataStore directamente.
5. **Ningún texto escrito en el código:** todo va en `strings.xml`, en español.

**Piezas clave:**

- **`AssistantViewModel`** (`ui/assistant/`) es el único ViewModel de la conversación; lo comparten Inicio, Confirmar, Aclarar y Resultado.
- **`MainActivity`** muestra la pantalla que indica `AssistantUiState.screen`.
- **`IntentParser`** propone hasta 3 intenciones con su confianza, y **`CommandProcessor`** decide qué hacer. Pide aclaración si la confianza es menor a 0,8 o si dos opciones quedan a menos de 0,1.
- **`AlarmLabelExtractor`** separa el nombre de la alarma antes de entender la hora.
- **`AndroidSpeechService`** prueba una cadena de reconocedores (sin conexión → en línea → otros instalados). Si falla en un teléfono nuevo, el registro `WithYou.Speech` dice por qué.

## 8. Cómo probar

| Pruebas | Comando | Notas |
|---|---|---|
| Unitarias (158) | `.\gradlew.bat testDebugUnitTest` | Sin teléfono, unos segundos. Pasaron el 27-11-2026 |
| En el teléfono (18) | `.\gradlew.bat assembleDebug assembleDebugAndroidTest`, instalar ambos APK con `adb install -r` y luego `adb shell am instrument -w cl.withyou.app.test/androidx.test.runner.AndroidJUnitRunner` | Teléfono desbloqueado y sin tocarlo |

- **No usar `connectedAndroidTest`:** Gradle desinstala la app al terminar y se pierden los contactos y permisos.
- **Si hay un PIN creado,** `WithoutPinRule` lo quita durante la prueba y lo restaura al terminar.
- **Al terminar,** desinstalar el APK de pruebas con `adb uninstall cl.withyou.app.test`.

**Trampas del teléfono de prueba (Samsung J2 Core, Android 8.1 Go):**

| Síntoma | Qué hacer |
|---|---|
| `adb devices` muestra «unauthorized» | Desbloquear el teléfono y aceptar «¿Permitir depuración USB?» |
| «No hay conexión a internet» teniendo internet | Android Go necesita internet para reconocer voz; mantener la conexión |
| La app no habla | Activar «Servicios de voz de Google» en Ajustes → Aplicaciones |
| Las llamadas dan «sin red móvil» | Es correcto: el teléfono no tiene chip |
| Se pierden líneas de Logcat | Guardar el registro en el computador mientras se prueba (`adb logcat … > archivo`) |

## 9. Decisiones tomadas y dudas abiertas

**Ya decididas (no reabrir sin motivo):**

- Mensajes con `ACTION_SENDTO`: el usuario toca Enviar (06-11-2026).
- Contactos desde la agenda eligiendo uno a la vez, sin `READ_CONTACTS` (18-11-2026).
- El PIN solo protege Ajustes y los cambios en Contactos.
- Toda acción crítica pasa por Confirmar.
- «A las 8», sin decir si es de la mañana o de la noche, pide aclaración.
- El nombre de la alarma es opcional. «Recuérdame…» crea una alarma con nombre.
- Docker no aplica (25-11-2026).
- Objetivos vigentes: los del informe 2.6, un objetivo general y 4 específicos. Los cambios respecto al diseño de la Fase 1 están en el Documento de Diseño, sección 9.

**Dudas abiertas para el equipo:**

1. **Alarmas de «mañana»:** «mañana a las 8» dicho a las 7:00 suena el mismo día, por cómo funciona el Reloj del sistema.
2. **PIN olvidado:** hoy solo se recupera borrando los datos de la app.
3. **«Alarma para una reunión»:** no se reconoce como nombre, porque «una» se confunde con «la una». Hoy se resuelve con el botón «Ponerle nombre».

**Mejoras futuras (fuera del alcance):** envío automático de SMS, reconocimiento de voz sin internet en Android Go (por ejemplo, Vosk) y escuchar automáticamente después de una pregunta.

## 10. Para continuar con Claude Code

Abrir Claude Code en `Escritorio\Claude Code\WithYou`: `CLAUDE.md` se carga solo. Al empezar, conviene pedirle que lea esta nota. Tener presente:

- Los cambios de código van en una rama, con pruebas, y se unen a `main` después de probarlos en el teléfono.
- Para la entrega, pedirle que prepare los archivos en `Capstone Entrega\WithYou` con los nombres del Excel. **El push lo hace Miguel.**
- **Cualquier tecnología nueva (Docker, servidores, librerías) se consulta antes.**
