package cl.withyou.app.domain.parser

import cl.withyou.app.domain.validation.AlarmLabelPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmLabelExtractorTest {

    private fun labelOf(phrase: String): String? =
        AlarmLabelPolicy.clean(AlarmLabelExtractor.extract(TextNormalizer.tokenize(phrase))?.label)

    @Test
    fun `encuentra el nombre dicho de distintas formas`() {
        val cases = mapOf(
            "pon una alarma a las ocho para tomar la pastilla" to "Tomar la pastilla",
            "pon una alarma para tomar la pastilla a las ocho" to "Tomar la pastilla",
            "despiértame a las siete que diga levantarse" to "Levantarse",
            "alarma con el nombre doctor a las cinco de la tarde" to "Doctor",
            "alarma con nombre remedio a las nueve de la noche" to "Remedio",
            "alarma que se llame gimnasio para mañana a las siete" to "Gimnasio",
            "recuérdame llamar a María a las cinco" to "Llamar a María",
            "recuérdame que tengo médico mañana a las ocho" to "Tengo médico",
            "recordatorio para regar las plantas a las seis de la tarde" to "Regar las plantas",
            "alarma para mañana a las ocho para ir al médico" to "Ir al médico",
            "pon una alarma a las ocho para tomar 2 pastillas" to "Tomar 2 pastillas",
            "alarma en 20 minutos para sacar el pan del horno" to "Sacar el pan del horno",
            "alarma para la feria a las 7 por favor" to "La feria",
        )
        cases.forEach { (phrase, expected) ->
            assertEquals("Frase: \"$phrase\"", expected, labelOf(phrase))
        }
    }

    @Test
    fun `sin nombre no inventa uno`() {
        listOf(
            "pon una alarma para mañana a las ocho",
            "despiértame a las siete de la mañana",
            "alarma para las 8",
            "pon una alarma para hoy a las cinco",
            "alarma en 20 minutos",
            "alarma para la una",
        ).forEach { phrase ->
            assertNull("Frase: \"$phrase\"", labelOf(phrase))
        }
    }

    @Test
    fun `el nombre conserva tildes y no deja el resto de la frase`() {
        val tokens = TextNormalizer.tokenize("Pon una alarma para la reunión con Begoña a las 3 de la tarde")
        val match = AlarmLabelExtractor.extract(tokens)!!
        assertEquals("la reunión con Begoña", match.label)
        assertEquals(listOf("a", "las", "3", "de", "la", "tarde"), tokens.map { it.normalized }.subList(match.end, tokens.size))
    }
}
