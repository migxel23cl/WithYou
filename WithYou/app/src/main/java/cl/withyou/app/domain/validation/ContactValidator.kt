package cl.withyou.app.domain.validation

/**
 * Valida y limpia los datos de un contacto. Lo usan el formulario de Contactos (CU-07)
 * y el CommandProcessor antes de llamar (§8: validar antes de pasar datos a un Intent).
 */
object ContactValidator {

    const val MAX_NAME_LENGTH = 40

    private val PHONE_CHARS = Regex("\\+?[0-9 ()-]+")
    private val WHITESPACE = Regex("\\s+")

    /** Deja solo dígitos (y el "+" inicial). Devuelve null si el número no es válido. */
    fun sanitizePhone(raw: String): String? {
        val trimmed = raw.trim()
        if (!PHONE_CHARS.matches(trimmed)) return null
        val digits = trimmed.filter(Char::isDigit)
        if (digits.length !in 8..15) return null
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    /** Quita espacios de sobra. Devuelve null si el nombre está vacío, es muy largo o no tiene letras. */
    fun cleanName(raw: String): String? {
        val name = raw.trim().replace(WHITESPACE, " ")
        return name.takeIf { it.length in 1..MAX_NAME_LENGTH && it.any(Char::isLetter) }
    }
}
