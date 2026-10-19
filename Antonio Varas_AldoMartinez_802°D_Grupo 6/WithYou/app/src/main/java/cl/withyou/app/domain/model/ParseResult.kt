package cl.withyou.app.domain.model

/** Intención candidata con su nivel de confianza entre 0 y 1. */
data class Candidate(val intent: UserIntent, val confidence: Double)

/**
 * Resultado de interpretar una frase: hasta 3 candidatos ordenados de mayor a menor confianza.
 * Si no se reconoció nada, contiene un único candidato [UserIntent.Unknown] con confianza 0.
 */
data class ParseResult(
    val normalizedText: String,
    val candidates: List<Candidate>,
) {
    val best: Candidate
        get() = candidates.firstOrNull() ?: Candidate(UserIntent.Unknown, 0.0)
}
