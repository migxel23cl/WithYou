package cl.withyou.app.domain.parser

import cl.withyou.app.domain.model.AlarmDay
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.domain.model.ContactTarget
import cl.withyou.app.domain.model.DayPeriod
import cl.withyou.app.domain.model.TimeSpec
import cl.withyou.app.domain.model.UserIntent
import cl.withyou.app.domain.model.VolumeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserTest {

    private val parser = IntentParser()

    private val maria = Contact(1, "María González", "+56911111111")
    private val pedro = Contact(2, "Pedro Soto", "+56922222222")
    private val hija = Contact(3, "Hija", "+56933333333")
    private val contacts = listOf(maria, pedro, hija)

    private fun assertBest(cases: List<Pair<String, UserIntent>>, minConfidence: Double = 0.8) {
        cases.forEach { (phrase, expected) ->
            val best = parser.parse(phrase, contacts).best
            assertEquals("Frase: \"$phrase\"", expected, best.intent)
            assertTrue(
                "Frase: \"$phrase\" con confianza ${best.confidence} < $minConfidence",
                best.confidence >= minConfidence,
            )
        }
    }

    @Test
    fun `hora y fecha`() = assertBest(
        listOf(
            "¿Qué hora es?" to UserIntent.AskTime,
            "dime la hora por favor" to UserIntent.AskTime,
            "qué hora son" to UserIntent.AskTime,
            "¿Qué día es hoy?" to UserIntent.AskDate,
            "qué fecha es" to UserIntent.AskDate,
            "a cuánto estamos" to UserIntent.AskDate,
        ),
    )

    @Test
    fun linterna() = assertBest(
        listOf(
            "enciende la linterna" to UserIntent.Flashlight(true),
            "prende la luz" to UserIntent.Flashlight(true),
            "préndeme la linterna por favor" to UserIntent.Flashlight(true),
            "apaga la linterna" to UserIntent.Flashlight(false),
            "Apágame la luz" to UserIntent.Flashlight(false),
        ),
    )

    @Test
    fun volumen() = assertBest(
        listOf(
            "sube el volumen" to UserIntent.Volume(VolumeDirection.UP),
            "súbele el volumen" to UserIntent.Volume(VolumeDirection.UP),
            "más volumen" to UserIntent.Volume(VolumeDirection.UP),
            "más fuerte, no escucho nada" to UserIntent.Volume(VolumeDirection.UP),
            "baja el volumen" to UserIntent.Volume(VolumeDirection.DOWN),
            "bájale el sonido" to UserIntent.Volume(VolumeDirection.DOWN),
            "más bajito por favor" to UserIntent.Volume(VolumeDirection.DOWN),
        ),
    )

    @Test
    fun `si y no`() = assertBest(
        listOf(
            "sí" to UserIntent.Confirm,
            "Sí, por favor" to UserIntent.Confirm,
            "dale" to UserIntent.Confirm,
            "ya po" to UserIntent.Confirm,
            "está bien" to UserIntent.Confirm,
            "correcto" to UserIntent.Confirm,
            "confirmo" to UserIntent.Confirm,
            "no" to UserIntent.Deny,
            "No, gracias" to UserIntent.Deny,
            "mejor no" to UserIntent.Deny,
            "cancela" to UserIntent.Deny,
            "cancela la llamada" to UserIntent.Deny,
            "ahora no" to UserIntent.Deny,
        ),
    )

    @Test
    fun alarmas() = assertBest(
        listOf(
            "pon una alarma para mañana a las ocho de la mañana" to
                UserIntent.SetAlarm(TimeSpec.Exact(8, 0), AlarmDay.TOMORROW),
            "despiértame a las 7:30" to UserIntent.SetAlarm(TimeSpec.MissingPeriod(7, 30)),
            "alarma a las ocho y media de la noche" to UserIntent.SetAlarm(TimeSpec.Exact(20, 30)),
            "ponme una alarma a las 20:15" to UserIntent.SetAlarm(TimeSpec.Exact(20, 15)),
            "pon la alarma un cuarto para las nueve de la mañana" to UserIntent.SetAlarm(TimeSpec.Exact(8, 45)),
            "despiértame en 20 minutos" to UserIntent.SetAlarm(TimeSpec.InMinutes(20)),
            "pon una alarma en la tarde" to UserIntent.SetAlarm(TimeSpec.MissingHour(DayPeriod.AFTERNOON)),
            "pon una alarma" to UserIntent.SetAlarm(TimeSpec.MissingHour(null)),
        ),
    )

    @Test
    fun llamadas() = assertBest(
        listOf(
            "llama a María" to UserIntent.Call(ContactTarget.Resolved(maria)),
            "Llámame a Pedro por favor" to UserIntent.Call(ContactTarget.Resolved(pedro)),
            "quiero hablar con mi hija" to UserIntent.Call(ContactTarget.Resolved(hija)),
            "marca a pedro" to UserIntent.Call(ContactTarget.Resolved(pedro)),
            "hazme una llamada a María" to UserIntent.Call(ContactTarget.Resolved(maria)),
            "llama a Roberto" to UserIntent.Call(ContactTarget.NotFound("Roberto")),
            "quiero llamar" to UserIntent.Call(ContactTarget.NotFound(null)),
        ),
    )

    @Test
    fun mensajes() = assertBest(
        listOf(
            "mándale un mensaje a Pedro" to UserIntent.SendMessage(ContactTarget.Resolved(pedro)),
            "envíale un mensaje a mi hija" to UserIntent.SendMessage(ContactTarget.Resolved(hija)),
            "escríbele a María" to UserIntent.SendMessage(ContactTarget.Resolved(maria)),
            "Dile a Pedro que llego tarde a almorzar" to
                UserIntent.SendMessage(ContactTarget.Resolved(pedro), "Llego tarde a almorzar"),
            "mándale un mensaje a María que la quiero mucho" to
                UserIntent.SendMessage(ContactTarget.Resolved(maria), "La quiero mucho"),
            "mensaje para Pedro diciéndole que ya voy" to
                UserIntent.SendMessage(ContactTarget.Resolved(pedro), "Ya voy"),
            "quiero mandar un mensaje" to UserIntent.SendMessage(ContactTarget.NotFound(null)),
        ),
    )

    @Test
    fun `frases sin sentido devuelven Unknown`() {
        listOf("hola cómo estás", "", "   ", "el perro está en el patio").forEach { phrase ->
            val result = parser.parse(phrase, contacts)
            assertEquals("Frase: \"$phrase\"", UserIntent.Unknown, result.best.intent)
            assertEquals(0.0, result.best.confidence, 0.0)
        }
    }

    @Test
    fun `solo un nombre ofrece llamar o escribir con baja confianza`() {
        val result = parser.parse("María", contacts)
        assertEquals(
            listOf(
                UserIntent.Call(ContactTarget.Resolved(maria)),
                UserIntent.SendMessage(ContactTarget.Resolved(maria)),
            ),
            result.candidates.map { it.intent },
        )
        assertTrue(result.best.confidence < 0.8)
    }

    @Test
    fun `una hora suelta sugiere una alarma con baja confianza`() {
        val result = parser.parse("a las ocho de la mañana", contacts)
        assertEquals(UserIntent.SetAlarm(TimeSpec.Exact(8, 0)), result.best.intent)
        assertTrue(result.best.confidence < 0.8)
    }

    @Test
    fun `nunca devuelve mas de tres candidatos`() {
        val result = parser.parse("enciende la luz y sube el volumen y dime la hora y llama a Pedro", contacts)
        assertTrue(result.candidates.size <= 3)
    }
}
