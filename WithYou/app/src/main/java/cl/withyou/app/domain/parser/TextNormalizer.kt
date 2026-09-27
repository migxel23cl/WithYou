package cl.withyou.app.domain.parser

import java.text.Normalizer
import java.util.Locale

/**
 * Palabra normalizada junto a la palabra original de la que proviene.
 * [sourceIndex] es la posición de la palabra original (separada por espacios) en el texto,
 * y sirve para recuperar el texto tal como se dijo (p. ej. el cuerpo de un mensaje).
 */
data class Token(val normalized: String, val original: String, val sourceIndex: Int)

/**
 * Normaliza el texto reconocido: minúsculas, sin tildes ni signos y con espacios colapsados.
 * Conserva ":" entre dígitos para no perder horas como "8:30".
 */
object TextNormalizer {

    private val whitespace = Regex("\\s+")
    private val combiningMarks = Regex("\\p{Mn}+")
    private val dottedTime = Regex("(\\d{1,2})[.h](\\d{2})(?!\\d)")
    private val invalidChars = Regex("[^a-z0-9:]")
    private val looseColon = Regex("(?<!\\d):|:(?!\\d)")

    fun normalize(text: String): String = words(text).joinToString(" ")

    fun words(text: String): List<String> = tokenize(text).map { it.normalized }

    fun tokenize(text: String): List<Token> {
        if (text.isBlank()) return emptyList()
        val tokens = mutableListOf<Token>()
        text.trim().split(whitespace).forEachIndexed { index, raw ->
            normalizeWord(raw).forEach { tokens += Token(it, raw, index) }
        }
        return mergeMeridiem(tokens)
    }

    private fun normalizeWord(raw: String): List<String> {
        val lower = raw.lowercase(Locale.ROOT)
        val withoutAccents = combiningMarks.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        val cleaned = dottedTime.replace(withoutAccents, "$1:$2")
            .replace(invalidChars, " ")
            .replace(looseColon, " ")
        return cleaned.split(' ').filter { it.isNotEmpty() }
    }

    /** Une "a. m." / "p. m." (que quedan como "a" "m") en "am" / "pm". */
    private fun mergeMeridiem(tokens: List<Token>): List<Token> {
        val result = mutableListOf<Token>()
        var i = 0
        while (i < tokens.size) {
            val current = tokens[i]
            val next = tokens.getOrNull(i + 1)
            if ((current.normalized == "a" || current.normalized == "p") && next?.normalized == "m") {
                result += current.copy(normalized = current.normalized + "m")
                i += 2
            } else {
                result += current
                i++
            }
        }
        return result
    }
}
