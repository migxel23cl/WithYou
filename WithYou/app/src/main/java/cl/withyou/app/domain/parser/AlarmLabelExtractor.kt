package cl.withyou.app.domain.parser

/** Nombre de la alarma encontrado en la frase y las palabras que ocupa ([start] incluido, [end] excluido). */
data class AlarmLabelMatch(val label: String, val start: Int, val end: Int)

/**
 * Encuentra el nombre de una alarma dentro de la frase, conservando tildes y mayúsculas:
 * - "alarma a las ocho **para tomar la pastilla**"
 * - "despiértame a las siete **que diga levantarse**"
 * - "alarma **con el nombre doctor** a las cinco"
 * - "**recuérdame llamar a María** a las cinco"
 *
 * El nombre termina donde empieza una expresión de hora ("a las…", "mañana", "en 20 minutos"),
 * para que esa parte siga sirviendo para entender la hora.
 */
object AlarmLabelExtractor {

    private val REMINDER = setOf("recuerdame", "recuerdeme", "recordarme", "recordatorio")
    private val PERIOD_WORDS = setOf("manana", "tarde", "noche", "madrugada")

    /** Después de "para", estas palabras hablan del momento y no de un nombre: "para mañana", "para hoy". */
    private val TIME_AFTER_PARA = setOf(
        "manana", "hoy", "pasado", "esta", "este", "en", "dentro", "mediodia", "medianoche",
    )
    private val TRAILING_FILLER = setOf("por", "favor", "porfa", "porfavor", "ya", "po", "y", "ahora", "ahorita")
    private val CLOCK = Regex("\\d{1,2}:\\d{2}")

    fun extract(tokens: List<Token>): AlarmLabelMatch? {
        val words = tokens.map { it.normalized }
        val start = labelStart(words) ?: return null
        var end = start
        while (end < words.size && !startsTime(words, end)) end++
        while (end > start && words[end - 1] in TRAILING_FILLER) end--
        if (end <= start) return null
        val label = tokens.subList(start, end)
            .distinctBy { it.sourceIndex }
            .joinToString(" ") { it.original }
        return AlarmLabelMatch(label, start, end)
    }

    /** Primera palabra del nombre, según la expresión que lo introduce. */
    private fun labelStart(words: List<String>): Int? {
        for (i in words.indices) {
            val next = words.getOrNull(i + 1)
            val afterNext = words.getOrNull(i + 2)
            when {
                words[i] == "que" && next == "diga" -> return i + 2
                words[i] == "que" && next == "se" && afterNext == "llame" -> return i + 3
                words[i] == "con" && next == "el" && afterNext == "nombre" -> return i + 3
                (words[i] == "con" || words[i] == "de") && next == "nombre" -> return i + 2
                words[i] in REMINDER -> {
                    // "recuérdame que…", "recordatorio de…", "recordatorio para…"
                    return if (next == "que" || next == "de" || next == "para") i + 2 else i + 1
                }
                words[i] == "para" && next != null && !isTimeAfterPara(words, i) -> return i + 1
            }
        }
        return null
    }

    /** true si en [i] empieza una expresión de hora o de día. */
    private fun startsTime(words: List<String>, i: Int): Boolean {
        val w = words[i]
        val next = words.getOrNull(i + 1)
        val afterNext = words.getOrNull(i + 2)
        return (w == "a" && (next == "las" || next == "la")) ||
            (w == "para" && isTimeAfterPara(words, i)) ||
            (w == "de" && next == "la" && afterNext in PERIOD_WORDS) ||
            w == "manana" || w == "hoy" || w == "mediodia" || w == "medianoche" ||
            (w == "esta" && next in PERIOD_WORDS) ||
            (w == "en" && next != null && (next == "media" || isNumber(next))) ||
            (w == "dentro" && next == "de") ||
            CLOCK.matches(w) ||
            // Un número solo es hora si viene acompañado: "8 de la mañana", "8 am"; "2 pastillas" es parte del nombre.
            (w.all(Char::isDigit) && next in setOf("de", "am", "pm", "y", "horas", "hrs"))
    }

    /** "para mañana", "para las 8", "para la una" son hora; "para la feria" es un nombre. */
    private fun isTimeAfterPara(words: List<String>, i: Int): Boolean {
        val next = words.getOrNull(i + 1) ?: return false
        val afterNext = words.getOrNull(i + 2)
        return next in TIME_AFTER_PARA || isNumber(next) ||
            ((next == "la" || next == "las") && afterNext != null && isNumber(afterNext))
    }

    private fun isNumber(word: String): Boolean = NumberWords.read(listOf(word), 0) != null
}
