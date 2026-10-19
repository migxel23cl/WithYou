package cl.withyou.app.domain.parser

import cl.withyou.app.domain.model.AlarmDay
import cl.withyou.app.domain.model.DayPeriod
import cl.withyou.app.domain.model.TimeSpec

data class TimeExtraction(val time: TimeSpec, val day: AlarmDay) {
    /** true si se dijo una hora concreta o relativa (no solo "en la tarde"). */
    val hasTime: Boolean
        get() = time !is TimeSpec.MissingHour
}

/**
 * Extrae hora y día de una frase normalizada. Entiende, entre otras:
 * "a las 8", "8:30", "a las ocho y media", "las nueve menos cuarto", "un cuarto para las nueve",
 * "de la mañana/tarde/noche", "mediodía", "en 20 minutos", "mañana", "hoy".
 */
class TimeExtractor {

    fun extract(text: String): TimeExtraction = extract(TextNormalizer.words(text))

    fun extract(words: List<String>): TimeExtraction {
        val period = findPeriod(words)
        val time = findRelative(words)
            ?: findClock(words, period)
            ?: findFixedTime(words)
            ?: TimeSpec.MissingHour(period)
        return TimeExtraction(time, findDay(words))
    }

    private fun findDay(words: List<String>): AlarmDay {
        words.forEachIndexed { i, word ->
            val prev = words.getOrNull(i - 1)
            // "mañana" sin "la"/"esta" delante es el día siguiente; "de la mañana" es un momento del día.
            if (word == "manana" && prev !in setOf("la", "esta", "pasado")) return AlarmDay.TOMORROW
            if (word == "hoy") return AlarmDay.TODAY
            if (prev == "esta" && word in setOf("manana", "tarde", "noche")) return AlarmDay.TODAY
        }
        return AlarmDay.UNSPECIFIED
    }

    private fun findPeriod(words: List<String>): DayPeriod? {
        words.forEachIndexed { i, word ->
            val prev = words.getOrNull(i - 1)
            when {
                word == "madrugada" || word == "am" -> return DayPeriod.EARLY_MORNING
                word == "manana" && (prev == "la" || prev == "esta") -> return DayPeriod.MORNING
                word == "tarde" || word == "pm" || word == "mediodia" || (word == "dia" && prev == "medio") ->
                    return DayPeriod.AFTERNOON
                word == "noche" || word == "medianoche" -> return DayPeriod.NIGHT
            }
        }
        return null
    }

    /** "en 20 minutos", "dentro de una hora", "en media hora". */
    private fun findRelative(words: List<String>): TimeSpec? {
        for (i in words.indices) {
            val start = when {
                words[i] == "en" -> i + 1
                words[i] == "dentro" && words.getOrNull(i + 1) == "de" -> i + 2
                else -> continue
            }
            if (words.getOrNull(start) == "media" && words.getOrNull(start + 1) == "hora") {
                return TimeSpec.InMinutes(30)
            }
            val number = NumberWords.read(words, start) ?: continue
            val unitIndex = start + number.length
            when (words.getOrNull(unitIndex)) {
                in MINUTE_UNITS -> return TimeSpec.InMinutes(number.value)
                "hora", "horas" -> {
                    val half = words.getOrNull(unitIndex + 1) == "y" && words.getOrNull(unitIndex + 2) == "media"
                    return TimeSpec.InMinutes(number.value * 60 + if (half) 30 else 0)
                }
            }
        }
        return null
    }

    private fun findClock(words: List<String>, period: DayPeriod?): TimeSpec? {
        for (i in words.indices) {
            CLOCK_DIGITS.matchEntire(words[i])?.let { match ->
                return resolve(match.groupValues[1].toInt(), match.groupValues[2].toInt(), period)
            }
            minutesBeforeHour(words, i, period)?.let { return it }
            val hour = readHour(words, i) ?: continue
            return readMinutes(words, i + hour.length, hour.value, period)
        }
        return null
    }

    private fun findFixedTime(words: List<String>): TimeSpec? {
        words.forEachIndexed { i, word ->
            val next = words.getOrNull(i + 1)
            if (word == "mediodia" || (word == "medio" && next == "dia")) return TimeSpec.Exact(12, 0)
            if (word == "medianoche" || (word == "media" && next == "noche")) return TimeSpec.Exact(0, 0)
        }
        return null
    }

    /** Solo acepta un número como hora si el contexto lo indica, para no confundir "una alarma" con la 1. */
    private fun readHour(words: List<String>, i: Int): NumberMatch? {
        val number = NumberWords.read(words, i, max = 24) ?: return null
        val prev = words.getOrNull(i - 1)
        val next = words.getOrNull(i + number.length)
        val afterNext = words.getOrNull(i + number.length + 1)
        if (next in MINUTE_UNITS) return null
        val introduced = prev == "la" || prev == "las"
        val isDigit = words[i].all(Char::isDigit)
        val followedByTimeWord = next in setOf("de", "del", "am", "pm", "y", "menos", "hrs", "horas") ||
            (next == "en" && afterNext == "punto")
        return number.takeIf { introduced || isDigit || followedByTimeWord }
    }

    private fun readMinutes(words: List<String>, j: Int, hour: Int, period: DayPeriod?): TimeSpec {
        val word = words.getOrNull(j)
        val next = words.getOrNull(j + 1)
        return when {
            word == "y" && next == "media" -> resolve(hour, 30, period)
            word == "y" && next == "cuarto" -> resolve(hour, 15, period)
            word == "y" || word == "con" ->
                resolve(hour, NumberWords.read(words, j + 1, max = 59)?.value ?: 0, period)
            word == "menos" && next == "cuarto" -> subtract(resolve(hour, 0, period), 15)
            word == "menos" ->
                subtract(resolve(hour, 0, period), NumberWords.read(words, j + 1, max = 59)?.value ?: 0)
            // "ocho treinta"
            else -> resolve(hour, NumberWords.read(words, j, max = 59)?.value ?: 0, period)
        }
    }

    /** "un cuarto para las nueve", "diez para las ocho". */
    private fun minutesBeforeHour(words: List<String>, i: Int, period: DayPeriod?): TimeSpec? {
        val (minutes, length) = if (words[i] == "cuarto") {
            15 to 1
        } else {
            NumberWords.read(words, i, max = 30)?.let { it.value to it.length } ?: return null
        }
        var j = i + length
        if (words.getOrNull(j) in MINUTE_UNITS) j++
        if (words.getOrNull(j) != "para" || words.getOrNull(j + 1) !in setOf("la", "las")) return null
        val hour = NumberWords.read(words, j + 2, max = 24) ?: return null
        return subtract(resolve(hour.value, 0, period), minutes)
    }

    private fun resolve(hour: Int, minute: Int, period: DayPeriod?): TimeSpec = when {
        hour == 24 -> TimeSpec.Exact(0, minute)
        hour !in 1..12 -> TimeSpec.Exact(hour, minute)
        period != null -> TimeSpec.Exact(period.to24h(hour), minute)
        else -> TimeSpec.MissingPeriod(hour, minute)
    }

    private fun subtract(spec: TimeSpec, minutes: Int): TimeSpec = when (spec) {
        is TimeSpec.Exact -> {
            val total = Math.floorMod(spec.hour * 60 + spec.minute - minutes, 24 * 60)
            TimeSpec.Exact(total / 60, total % 60)
        }
        is TimeSpec.MissingPeriod -> {
            val total = Math.floorMod((spec.hour % 12) * 60 + spec.minute - minutes, 12 * 60)
            val hour = total / 60
            TimeSpec.MissingPeriod(if (hour == 0) 12 else hour, total % 60)
        }
        else -> spec
    }

    private companion object {
        val CLOCK_DIGITS = Regex("(\\d{1,2}):(\\d{2})")
        val MINUTE_UNITS = setOf("minuto", "minutos", "min")
    }
}
