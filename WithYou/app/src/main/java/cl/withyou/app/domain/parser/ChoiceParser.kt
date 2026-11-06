package cl.withyou.app.domain.parser

/**
 * Entiende cuál opción eligió el usuario en la pantalla "¿Quisiste decir…?" (CU-06):
 * "la primera", "opción dos", "el 3", "la última".
 */
object ChoiceParser {

    /** Una elección es una frase corta; así "pon una alarma" no se toma como "opción 1". */
    private const val MAX_WORDS = 4

    private val ORDINALS = mapOf(
        "1" to 0, "uno" to 0, "una" to 0, "primera" to 0, "primero" to 0,
        "2" to 1, "dos" to 1, "segunda" to 1, "segundo" to 1,
        "3" to 2, "tres" to 2, "tercera" to 2, "tercero" to 2,
    )
    private val LAST = setOf("ultima", "ultimo")

    private val MORNING_WORDS = setOf("manana", "madrugada", "am")
    private val EVENING_WORDS = setOf("tarde", "noche", "pm")

    /** Índice (desde 0) de la opción elegida, o null si la frase no es una elección. */
    fun parse(text: String, optionCount: Int): Int? {
        val words = TextNormalizer.words(text)
        if (words.isEmpty() || words.size > MAX_WORDS) return null
        val index = when {
            words.any { it in LAST } -> optionCount - 1
            else -> words.firstNotNullOfOrNull { ORDINALS[it] } ?: return null
        }
        return index.takeIf { it in 0 until optionCount }
    }

    /**
     * Para elegir entre "8:00 de la mañana" (opción 0) y "8:00 de la noche" (opción 1)
     * diciendo solo el momento del día.
     */
    fun parsePeriod(text: String): Int? {
        val words = TextNormalizer.words(text)
        // Algo más largo que una elección: "a las ocho de la noche" también vale.
        if (words.isEmpty() || words.size > MAX_WORDS + 2) return null
        return when {
            words.any { it in MORNING_WORDS } -> 0
            words.any { it in EVENING_WORDS } -> 1
            else -> null
        }
    }
}
