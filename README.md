# WithYou

Aplicación Android que permite a las personas mayores usar su teléfono **hablándole con naturalidad**, en español de Chile. Con una instrucción como «pon una alarma para mañana a las ocho» o «llama a María», la app entiende lo que se pide, confirma las acciones importantes y responde **por voz y en pantalla al mismo tiempo**.

No es un asistente general: es una capa de accesibilidad enfocada en pocas tareas cotidianas, con confirmación de acciones críticas, tolerancia a errores y bajo consumo, pensada para funcionar en teléfonos de gama baja.

> Proyecto Capstone · Ingeniería en Informática · Duoc UC, sede Antonio Varas · Docente: Aldo Martínez · Metodología Cascada

## Funciones

| Función | Ejemplo de instrucción | Confirmación |
|---|---|---|
| Consultar la hora y la fecha | «¿Qué hora es?», «¿Qué día es hoy?» | No |
| Linterna | «Enciende la linterna» | No |
| Volumen | «Sube el volumen», «más bajo» | No |
| Alarmas | «Pon una alarma a las ocho y media de la noche» | Sí |
| Llamadas | «Llama a mi hija» | Sí |
| Mensajes | «Mándale un mensaje a Pedro diciéndole que voy tarde» | Sí, con lectura del mensaje en voz alta |
| Contactos frecuentes | Agregar, editar y eliminar desde la pantalla Contactos | — |
| Preferencias | Tamaño del texto (100 % a 160 %), volumen de la voz y respuesta por voz | — |

Si la app no está segura de lo que se dijo, muestra **«No entendí bien»** con hasta 3 opciones, que se pueden elegir tocando o por voz («la primera», «de la noche»). Las acciones críticas siempre pasan por la pantalla **Confirmar**.

## Estado del proyecto

Desarrollo en 8 pasos, con el producto mínimo viable primero:

- [x] 1. Base del proyecto y arquitectura
- [x] 2. Núcleo de interpretación (`IntentParser`, `CommandProcessor`)
- [x] 3. Reconocimiento y síntesis de voz, pantalla de Inicio
- [x] 4. Flujo completo con confirmación: hora, fecha, linterna, volumen y alarmas
- [x] 5. Contactos frecuentes (Room) y llamadas
- [x] 6. Mensajes por voz
- [x] 7. Preferencias de accesibilidad (DataStore)
- [ ] 8. TalkBack, cifrado de datos, PIN, pruebas Espresso y medición del tiempo de respuesta

13 de 13 requerimientos funcionales y 8 de 8 casos de uso implementados. El detalle, con la trazabilidad de requerimientos, los problemas encontrados y las decisiones técnicas, está en [`WithYou/docs/avance-fase2.md`](WithYou/docs/avance-fase2.md).

## Tecnologías

| Elemento | Tecnología |
|---|---|
| Lenguaje | Kotlin 1.9.22 · JDK 17 |
| Compilación | Gradle 8.2.1 + Android Gradle Plugin 8.2.2 |
| Android | minSdk 26 (Android 8.0) · targetSdk 34 |
| Interfaz | Android Views + XML, ViewBinding, Material Design 3, Navigation Component |
| Estado | ViewModel, StateFlow, Kotlin Coroutines |
| Voz | `SpeechRecognizer` (es-CL) y `TextToSpeech` |
| Interpretación | `IntentParser` propio, basado en reglas y patrones |
| Acciones | `AlarmClock`, `ACTION_CALL` / `ACTION_DIAL`, `ACTION_SENDTO`, `CameraManager`, `AudioManager` |
| Datos | Room (contactos) y Jetpack DataStore (preferencias) |
| Pruebas | JUnit 4, kotlinx-coroutines-test, pruebas instrumentadas de Room y DataStore |

## Arquitectura

MVVM por capas con Repository e inyección de dependencias manual:

```
View        ui/          Fragments, Activity y layouts XML: dibujan el estado y avisan los toques
  ↓ eventos               ↑ estado (StateFlow)
ViewModel   ui/assistant/AssistantViewModel: coordina la conversación
  ↓
Model       domain/      interpretación y decisión (Kotlin puro, sin Android)
            services/    voz, texto a voz y acciones del teléfono, detrás de interfaces
            data/        ContactRepository → Room · PreferencesRepository → DataStore

di/AppContainer          crea y conecta las capas
```

Flujo de una instrucción: el usuario habla → `SpeechService` transcribe → `IntentParser.parse()` reconoce la intención → `CommandProcessor.process()` decide (ejecutar, confirmar, aclarar, pedir un dato o rechazar) → `ActionExecutor` actúa sobre el teléfono → la respuesta se muestra y se dice al mismo tiempo.

El ViewModel nunca conoce a la View ni usa el `Context` de Android, por lo que toda la lógica se prueba sin dispositivo.

## Estructura del repositorio

```
WithYou/                                     Proyecto de Android Studio
├── app/src/main/java/cl/withyou/app/
│   ├── ui/          Pantallas: home, confirm, clarify, result, contacts, messages, settings, assistant
│   ├── domain/      model, parser, processor, validation
│   ├── services/    SpeechService, TtsService, ActionExecutor y sus implementaciones Android
│   ├── data/        Room (local), repositorios y preferencias
│   └── di/          AppContainer
├── app/src/test/            Pruebas unitarias
├── app/src/androidTest/     Pruebas instrumentadas
├── docs/avance-fase2.md     Avance de la Fase 2
└── CLAUDE.md                Contexto técnico del proyecto
Antonio Varas_AldoMartinez_802°D_Grupo 6/   Entregables académicos por fase
```

## Cómo ejecutar

**Requisitos:** Android Studio con JDK 17 y un teléfono o emulador con Android 8.0 o superior.

1. Clonar el repositorio y abrir la carpeta `WithYou/` en Android Studio.
2. Esperar la sincronización de Gradle.
3. Ejecutar la configuración `app` en el dispositivo.

En el teléfono deben estar activos los **Servicios de voz de Google** (reconocimiento y síntesis de voz). En equipos **Android Go** el reconocimiento de voz necesita conexión a internet, porque el sistema no incluye un motor sin conexión.

La app pide el permiso del micrófono la primera vez que se toca el botón y el de llamadas al guardar el primer contacto, siempre con una explicación previa. Si se niega el permiso de llamadas, igual se puede llamar: se abre el teléfono con el número marcado.

## Pruebas

Desde la carpeta `WithYou/`:

```bash
# Pruebas unitarias (130)
./gradlew testDebugUnitTest        # Windows: .\gradlew.bat testDebugUnitTest
```

Las 6 pruebas instrumentadas (Room y DataStore reales) se ejecutan en un teléfono conectado. `connectedAndroidTest` desinstala la app al terminar; para conservarla, instalar el APK de pruebas y ejecutarlas con `adb shell am instrument`.

Las pruebas usan dobles (*fakes*) del micrófono, la voz y el hardware, de modo que la conversación completa se valida sin dispositivo, incluidos los errores del reconocedor y los tiempos de espera.

**Dispositivo de referencia:** Samsung Galaxy J2 Core (Android 8.1 Go, 1 GB de RAM), que representa al usuario objetivo.

## Equipo

| Integrante | Rol |
|---|---|
| Miguel Pereira | Jefe de proyecto |
| Baltazar Ordoñez | Desarrollo de voz e interpretación |
| José Luis Donoso | Desarrollo de interfaz y accesibilidad |
| Joaquín Mendoza | Pruebas y documentación técnica |
