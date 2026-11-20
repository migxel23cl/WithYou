package cl.withyou.app.domain.validation

/** Reglas del nombre de una alarma ("Tomar la pastilla"). */
object AlarmLabelPolicy {

    /** Lo que cabe cómodo en la pantalla del Reloj del teléfono. */
    const val MAX_LENGTH = 40

    private val WHITESPACE = Regex("\\s+")
    private val EDGE_SYMBOLS = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")

    /**
     * Limpia el nombre: quita espacios y signos sobrantes en los bordes, pone la primera letra en
     * mayúscula y lo acorta si es muy largo. Devuelve null si no tiene letras.
     */
    fun clean(raw: String?): String? {
        if (raw == null) return null
        val text = raw.replace(WHITESPACE, " ").replace(EDGE_SYMBOLS, "").trim()
        if (text.none(Char::isLetter)) return null
        return text.take(MAX_LENGTH).trimEnd().replaceFirstChar { it.uppercase() }
    }
}
