package cl.withyou.app.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class ChoiceParserTest {

    @Test
    fun `entiende la opcion elegida`() {
        val cases = mapOf(
            "la primera" to 0,
            "uno" to 0,
            "opción 2" to 1,
            "la segunda por favor" to 1,
            "el número tres" to 2,
            "la tercera" to 2,
            "la última" to 2,
        )
        cases.forEach { (phrase, expected) ->
            assertEquals("Frase: \"$phrase\"", expected, ChoiceParser.parse(phrase, optionCount = 3))
        }
    }

    @Test
    fun `no confunde una instruccion con una eleccion`() {
        listOf("pon una alarma a las ocho", "qué hora es", "", "la tercera").zip(listOf(3, 3, 3, 2))
            .forEach { (phrase, count) ->
                assertEquals("Frase: \"$phrase\"", null, ChoiceParser.parse(phrase, count))
            }
    }

    @Test
    fun `elige mañana o noche diciendo el momento del dia`() {
        val cases = mapOf(
            "de la mañana" to 0,
            "en la madrugada" to 0,
            "de la noche" to 1,
            "en la tarde" to 1,
            "a las ocho de la noche" to 1,
            "no sé" to null,
        )
        cases.forEach { (phrase, expected) ->
            assertEquals("Frase: \"$phrase\"", expected, ChoiceParser.parsePeriod(phrase))
        }
    }
}
