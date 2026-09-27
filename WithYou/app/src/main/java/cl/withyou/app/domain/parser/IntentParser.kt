package cl.withyou.app.domain.parser

import cl.withyou.app.domain.model.Candidate
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.domain.model.ContactTarget
import cl.withyou.app.domain.model.ParseResult
import cl.withyou.app.domain.model.UserIntent
import cl.withyou.app.domain.model.VolumeDirection

/**
 * Interpreta una frase en español de Chile y devuelve hasta 3 intenciones candidatas
 * con su confianza. Funciona con reglas y patrones locales, sin conexión a internet.
 */
class IntentParser(
    private val timeExtractor: TimeExtractor = TimeExtractor(),
    private val contactMatcher: ContactMatcher = ContactMatcher(),
) {

    fun parse(text: String, contacts: List<Contact> = emptyList()): ParseResult {
        val tokens = TextNormalizer.tokenize(text)
        val words = tokens.map { it.normalized }
        val normalized = words.joinToString(" ")
        val candidates = mutableListOf<Candidate>()

        if (words.isNotEmpty()) {
            val exclusive = detectYesNo(words, candidates)
            if (!exclusive) {
                val time = timeExtractor.extract(words)
                val mentionsAlarm = ALARM.containsMatchIn(normalized)

                detectMessage(normalized, tokens, contacts, candidates)
                detectCall(normalized, tokens, contacts, candidates)
                if (mentionsAlarm) candidates += Candidate(UserIntent.SetAlarm(time.time, time.day), STRONG)
                detectFlashlight(normalized, candidates)
                detectVolume(normalized, candidates)
                if (!mentionsAlarm) detectTimeAndDate(normalized, words, candidates)

                // Sin palabra clave, una hora suelta ("a las ocho") probablemente es una alarma.
                if (candidates.none { it.confidence >= STRONG_ENOUGH } && time.hasTime && !mentionsAlarm) {
                    candidates += Candidate(UserIntent.SetAlarm(time.time, time.day), GUESS)
                }
                if (candidates.isEmpty()) detectContactOnly(tokens, contacts, candidates)
            }
        }
        return ParseResult(normalized, rank(candidates))
    }

    /** Devuelve true si la frase es solo una cancelación y no hay que buscar otras intenciones. */
    private fun detectYesNo(words: List<String>, out: MutableList<Candidate>): Boolean {
        if (words.first() in CANCEL_VERBS) {
            out += Candidate(UserIntent.Deny, SURE)
            return true
        }
        if (words.size > MAX_YES_NO_WORDS) return false

        val hasNegative = words.any { it in NEGATIVE }
        val hasAffirmative = words.any { it in AFFIRMATIVE } ||
            words.containsSequence("esta", "bien") ||
            words.containsSequence("de", "acuerdo") ||
            words.containsSequence("asi", "es")
        when {
            hasNegative && words.all { it in NEGATIVE || it in FILLER } ->
                out += Candidate(UserIntent.Deny, SURE)
            !hasNegative && hasAffirmative && words.all { it in AFFIRMATIVE || it in FILLER } ->
                out += Candidate(UserIntent.Confirm, SURE)
        }
        return false
    }

    private fun detectMessage(
        normalized: String,
        tokens: List<Token>,
        contacts: List<Contact>,
        out: MutableList<Candidate>,
    ) {
        for (pattern in MESSAGE_PATTERNS) {
            val match = pattern.find(normalized) ?: continue
            val restStart = match.groups[1]?.let { wordIndexAt(normalized, it.range.first) }
            if (restStart == null) {
                out += Candidate(UserIntent.SendMessage(ContactTarget.NotFound(null)), STRONG)
                return
            }
            val rest = tokens.subList(restStart, tokens.size)
            val delimiter = rest.indexOfFirst { it.normalized in MESSAGE_DELIMITERS }
            val nameTokens = if (delimiter >= 0) rest.subList(0, delimiter) else rest

            var bodyStart = if (delimiter >= 0) restStart + delimiter + 1 else tokens.size
            if (tokens.getOrNull(bodyStart)?.normalized == "que") bodyStart++ // "diciéndole que…"
            val text = if (bodyStart < tokens.size) originalText(tokens, bodyStart) else null

            out += Candidate(UserIntent.SendMessage(resolveTarget(nameTokens, contacts), text), STRONG)
            return
        }
    }

    private fun detectCall(
        normalized: String,
        tokens: List<Token>,
        contacts: List<Contact>,
        out: MutableList<Candidate>,
    ) {
        val match = CALL.find(normalized) ?: return
        val restStart = match.groups[1]?.let { wordIndexAt(normalized, it.range.first) }
        val rest = if (restStart == null) emptyList() else tokens.subList(restStart, tokens.size)
        val delimiter = rest.indexOfFirst { it.normalized in MESSAGE_DELIMITERS }
        val nameTokens = if (delimiter >= 0) rest.subList(0, delimiter) else rest
        out += Candidate(UserIntent.Call(resolveTarget(nameTokens, contacts)), STRONG)
    }

    private fun detectFlashlight(normalized: String, out: MutableList<Candidate>) {
        if (!LIGHT.containsMatchIn(normalized)) return
        val on = LIGHT_ON.containsMatchIn(normalized)
        val off = LIGHT_OFF.containsMatchIn(normalized)
        when {
            on && !off -> out += Candidate(UserIntent.Flashlight(turnOn = true), SURE)
            off && !on -> out += Candidate(UserIntent.Flashlight(turnOn = false), SURE)
            else -> {
                out += Candidate(UserIntent.Flashlight(turnOn = true), GUESS)
                out += Candidate(UserIntent.Flashlight(turnOn = false), GUESS)
            }
        }
    }

    private fun detectVolume(normalized: String, out: MutableList<Candidate>) {
        val up = UserIntent.Volume(VolumeDirection.UP)
        val down = UserIntent.Volume(VolumeDirection.DOWN)
        if (VOLUME.containsMatchIn(normalized)) {
            val goesUp = VOLUME_UP.containsMatchIn(normalized)
            val goesDown = VOLUME_DOWN.containsMatchIn(normalized)
            when {
                goesUp && !goesDown -> out += Candidate(up, SURE)
                goesDown && !goesUp -> out += Candidate(down, SURE)
                else -> {
                    out += Candidate(up, GUESS)
                    out += Candidate(down, GUESS)
                }
            }
            return
        }
        when {
            LOUDER.containsMatchIn(normalized) -> out += Candidate(up, MEDIUM)
            QUIETER.containsMatchIn(normalized) -> out += Candidate(down, MEDIUM)
        }
    }

    private fun detectTimeAndDate(normalized: String, words: List<String>, out: MutableList<Candidate>) {
        when {
            ASK_TIME.containsMatchIn(normalized) -> out += Candidate(UserIntent.AskTime, SURE)
            words == listOf("hora") -> out += Candidate(UserIntent.AskTime, WEAK)
        }
        when {
            ASK_DATE.containsMatchIn(normalized) -> out += Candidate(UserIntent.AskDate, SURE)
            words == listOf("fecha") -> out += Candidate(UserIntent.AskDate, WEAK)
        }
    }

    /** Si solo se dijo un nombre ("María"), se ofrece llamar o escribirle. */
    private fun detectContactOnly(tokens: List<Token>, contacts: List<Contact>, out: MutableList<Candidate>) {
        if (contacts.isEmpty()) return
        val target = resolveTarget(tokens, contacts)
        if (target is ContactTarget.NotFound) return
        out += Candidate(UserIntent.Call(target), GUESS)
        out += Candidate(UserIntent.SendMessage(target), GUESS - 0.05)
    }

    private fun resolveTarget(nameTokens: List<Token>, contacts: List<Contact>): ContactTarget {
        val name = cleanName(nameTokens)
        if (name.isEmpty()) return ContactTarget.NotFound(null)
        val spokenName = name.distinctBy { it.sourceIndex }
            .joinToString(" ") { token -> token.original.filter { it.isLetterOrDigit() } }
        return contactMatcher.match(spokenName, contacts)
    }

    /** Quita palabras que rodean al nombre: "a mi hija por favor" → "hija". */
    private fun cleanName(tokens: List<Token>): List<Token> =
        tokens.dropWhile { it.normalized in NAME_PREFIXES }.dropLastWhile { it.normalized in NAME_SUFFIXES }

    /** Texto tal como se dijo, desde la palabra [fromWord], con la primera letra en mayúscula. */
    private fun originalText(tokens: List<Token>, fromWord: Int): String? {
        val firstSource = tokens[fromWord].sourceIndex
        return tokens.filter { it.sourceIndex >= firstSource }
            .distinctBy { it.sourceIndex }
            .joinToString(" ") { it.original }
            .trim()
            .replaceFirstChar { it.uppercase() }
            .ifBlank { null }
    }

    /** Índice de palabra que corresponde a una posición de carácter en el texto normalizado. */
    private fun wordIndexAt(normalized: String, charIndex: Int): Int =
        normalized.substring(0, charIndex).count { it == ' ' }

    private fun rank(candidates: List<Candidate>): List<Candidate> {
        if (candidates.isEmpty()) return listOf(Candidate(UserIntent.Unknown, 0.0))
        return candidates
            .groupBy { it.intent }
            .map { (_, same) -> same.maxBy { it.confidence } }
            .sortedByDescending { it.confidence }
            .take(MAX_CANDIDATES)
    }

    private fun List<String>.containsSequence(first: String, second: String): Boolean =
        zipWithNext().any { it.first == first && it.second == second }

    private companion object {
        const val MAX_CANDIDATES = 3
        const val MAX_YES_NO_WORDS = 6

        const val SURE = 0.95
        const val STRONG = 0.92
        const val MEDIUM = 0.85
        const val STRONG_ENOUGH = 0.8
        const val WEAK = 0.6
        const val GUESS = 0.5

        val AFFIRMATIVE = setOf(
            "si", "sip", "dale", "ya", "ok", "okay", "okey", "confirmo", "confirmar", "confirma",
            "correcto", "claro", "bueno", "hazlo", "exacto", "afirmativo", "perfecto", "listo", "vale",
        )
        val NEGATIVE = setOf(
            "no", "nop", "cancela", "cancelar", "cancelalo", "cancelala", "detente", "olvidalo",
            "nada", "negativo", "para", "alto",
        )
        val CANCEL_VERBS = setOf("cancela", "cancelar", "cancelalo", "cancelala", "olvidalo", "detente")
        val FILLER = setOf(
            "por", "favor", "porfa", "po", "pues", "que", "es", "asi", "esta", "bien", "de", "acuerdo",
            "mejor", "gracias", "eso", "lo", "la", "mhm", "todavia", "ahora", "mijo", "mija",
        )

        val MESSAGE_DELIMITERS = setOf("que", "diciendo", "diciendole")
        val NAME_PREFIXES = setOf(
            "a", "al", "para", "con", "un", "una", "el", "la", "los", "las", "mi", "mis", "de", "del",
            "mensaje", "mensajito", "whatsapp", "wasap", "sms", "texto", "recado", "nota", "llamada",
        )
        val NAME_SUFFIXES = setOf(
            "por", "favor", "porfa", "porfavor", "ahora", "ahorita", "altiro", "al", "tiro",
            "rapido", "urgente", "ya", "po", "y",
        )

        val MESSAGE_PATTERNS = listOf(
            Regex(
                "\\b(?:manda|mandale|mandar|mandarle|envia|enviale|enviar|enviarle|escribe|escribele|" +
                    "escribir|escribirle|redacta)\\b(?: (?:un|una|el|la))?" +
                    "(?: (?:mensaje|mensajito|whatsapp|wasap|sms|texto|recado|nota))?(?: (.+))?",
            ),
            Regex("\\b(?:mensaje|mensajito|recado|whatsapp|wasap|sms)\\b(?: (.+))?"),
            Regex("\\b(?:dile|digale|dijale|avisale|cuentale)\\b(?: (.+))?"),
        )
        val CALL = Regex(
            "\\b(?:llama|llamame|llamale|llamalo|llamala|llamar|llamarle|llamarlo|llamarla|llamada|" +
                "llamen|marca|marcale|marcame|marcar|marcarle|telefonea|telefonear|comunicame con|" +
                "comunicarme con|conectame con|hablar con)\\b(?: (.+))?",
        )
        val ALARM = Regex(
            "\\b(?:alarma|alarmas|despiertame|despiertenme|despertador|despertarme|despertar|levantarme)\\b",
        )
        val LIGHT = Regex("\\b(?:linterna|luz|foco|lampara)\\b")
        val LIGHT_ON = Regex(
            "\\b(?:enciende|encender|enciendeme|encienda|prende|prender|prendeme|prenda|activa|activar|pon|poner|ilumina)\\b",
        )
        val LIGHT_OFF = Regex(
            "\\b(?:apaga|apagar|apagame|apague|desactiva|desactivar|quita|quitar|saca|sacar)\\b",
        )
        val VOLUME = Regex("\\b(?:volumen|sonido)\\b")
        val VOLUME_UP = Regex("\\b(?:sube|subir|subele|subeme|subelo|aumenta|aumentar|aumentale|mas)\\b")
        val VOLUME_DOWN = Regex("\\b(?:baja|bajar|bajale|bajame|bajalo|disminuye|disminuir|reduce|reducir|menos)\\b")
        val LOUDER = Regex("\\b(?:mas fuerte|mas alto|no escucho|no oigo|no se escucha|no se oye|muy bajito)\\b")
        val QUIETER = Regex("\\b(?:mas bajo|mas bajito|mas suave|menos fuerte|muy fuerte)\\b")
        val ASK_TIME = Regex("\\b(?:que hora|hora es|hora son|dime la hora|la hora)\\b")
        val ASK_DATE = Regex(
            "\\b(?:que dia|que fecha|la fecha|a cuanto estamos|dia es hoy|dia de hoy|fecha de hoy|que mes)\\b",
        )
    }
}
