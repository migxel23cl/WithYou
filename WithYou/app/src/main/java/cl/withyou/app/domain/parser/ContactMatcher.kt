package cl.withyou.app.domain.parser

import cl.withyou.app.domain.model.Contact
import cl.withyou.app.domain.model.ContactTarget

/**
 * Busca en los contactos frecuentes el nombre dicho por el usuario con coincidencia difusa,
 * para tolerar errores del reconocimiento de voz ("Maria" / "María", "Pedro" / "Pedró").
 */
class ContactMatcher(
    private val matchThreshold: Double = 0.88,
    private val suggestionThreshold: Double = 0.75,
    private val tieMargin: Double = 0.04,
) {

    fun match(spokenName: String, contacts: List<Contact>): ContactTarget {
        val spoken = TextNormalizer.normalize(spokenName)
        if (spoken.isEmpty()) return ContactTarget.NotFound(null)

        val scored = contacts
            .map { it to score(spoken, it.name) }
            .sortedByDescending { it.second }
        val top = scored.firstOrNull()
        if (top == null || top.second < matchThreshold) {
            val suggestions = scored.filter { it.second >= suggestionThreshold }.take(MAX_OPTIONS).map { it.first }
            return ContactTarget.NotFound(spokenName, suggestions)
        }

        val tied = scored.filter { it.second >= matchThreshold && top.second - it.second <= tieMargin }
        return if (tied.size == 1) {
            ContactTarget.Resolved(top.first)
        } else {
            ContactTarget.Ambiguous(spokenName, tied.take(MAX_OPTIONS).map { it.first })
        }
    }

    /**
     * Compara contra el nombre completo y contra cada palabra del nombre, así "maría"
     * coincide con "María González". Si se dijeron varias palabras, promedia cada una.
     */
    fun score(normalizedSpoken: String, contactName: String): Double {
        val name = TextNormalizer.normalize(contactName)
        if (name.isEmpty()) return 0.0
        if (name == normalizedSpoken) return 1.0

        val nameParts = name.split(' ')
        val spokenParts = normalizedSpoken.split(' ')
        val full = jaroWinkler(normalizedSpoken, name)
        val byWords = spokenParts
            .map { spokenPart -> nameParts.maxOf { jaroWinkler(spokenPart, it) } }
            .average()
        return maxOf(full, byWords)
    }

    private companion object {
        const val MAX_OPTIONS = 3
    }
}
