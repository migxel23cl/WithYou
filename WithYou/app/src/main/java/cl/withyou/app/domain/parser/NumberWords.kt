package cl.withyou.app.domain.parser

/** Número leído y cuántas palabras ocupó ("treinta y cinco" ocupa 3). */
data class NumberMatch(val value: Int, val length: Int)

/** Lee números escritos con dígitos o con palabras en español ("ocho", "veinticinco", "treinta y cinco"). */
object NumberWords {

    private val units = mapOf(
        "cero" to 0, "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
    )

    private val tens = mapOf("veinte" to 20, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50)

    private val others = mapOf(
        "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
        "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veintiun" to 21, "veintiuno" to 21, "veintiuna" to 21, "veintidos" to 22, "veintitres" to 23,
        "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27,
        "veintiocho" to 28, "veintinueve" to 29,
    )

    private val digits = Regex("\\d{1,4}")

    /**
     * Lee un número que empieza en [words][start]. Devuelve null si no hay número
     * o si supera [max]. Un compuesto que supera [max] ("veinte y cinco" como hora)
     * se lee solo como la decena.
     */
    fun read(words: List<String>, start: Int, max: Int = Int.MAX_VALUE): NumberMatch? {
        val word = words.getOrNull(start) ?: return null
        if (digits.matches(word)) {
            return word.toInt().takeIf { it <= max }?.let { NumberMatch(it, 1) }
        }
        tens[word]?.let { base ->
            val unit = if (words.getOrNull(start + 1) == "y") words.getOrNull(start + 2)?.let(units::get) else null
            if (unit != null && unit > 0 && base + unit <= max) return NumberMatch(base + unit, 3)
            return base.takeIf { it <= max }?.let { NumberMatch(it, 1) }
        }
        val value = units[word] ?: others[word] ?: return null
        return value.takeIf { it <= max }?.let { NumberMatch(it, 1) }
    }
}
