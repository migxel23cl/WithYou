package cl.withyou.app.domain.model

/** Hora interpretada a partir de lo que dijo el usuario. */
sealed interface TimeSpec {

    /** Hora exacta en formato 24 h. */
    data class Exact(val hour: Int, val minute: Int) : TimeSpec

    /** Hora entre 1 y 12 sin decir si es de mañana, tarde o noche ("a las 8"). */
    data class MissingPeriod(val hour: Int, val minute: Int) : TimeSpec

    /** No se dijo la hora; puede venir solo el momento del día ("en la tarde"). */
    data class MissingHour(val period: DayPeriod? = null) : TimeSpec

    /** Hora relativa a ahora ("en 20 minutos"). */
    data class InMinutes(val minutes: Int) : TimeSpec
}

/** Momento del día dicho por el usuario. */
enum class DayPeriod {
    /** "de la madrugada", "a. m." */
    EARLY_MORNING,

    /** "de la mañana" */
    MORNING,

    /** "de la tarde", "del mediodía", "p. m." */
    AFTERNOON,

    /** "de la noche" */
    NIGHT;

    /** Convierte una hora de 1 a 12 a formato 24 h según el momento del día. */
    fun to24h(hour12: Int): Int = when (this) {
        EARLY_MORNING -> if (hour12 == 12) 0 else hour12
        MORNING -> hour12
        AFTERNOON -> if (hour12 == 12) 12 else hour12 + 12
        NIGHT -> when (hour12) {
            12 -> 0
            in 1..4 -> hour12
            else -> hour12 + 12
        }
    }
}

/** Día en que debe sonar la alarma. */
enum class AlarmDay { TODAY, TOMORROW, UNSPECIFIED }
