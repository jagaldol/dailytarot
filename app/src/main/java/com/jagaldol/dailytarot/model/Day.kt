package com.jagaldol.dailytarot.model

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** A calendar date without a time. java.time would need desugaring on API 24–25. */
data class Day(val year: Int, val month: Int, val dayOfMonth: Int) : Comparable<Day> {
    init {
        require(year in 1..9999 && month in 1..12 && dayOfMonth in 1..daysInMonth(year, month))
    }

    override fun compareTo(other: Day): Int =
        compareValuesBy(this, other, Day::year, Day::month, Day::dayOfMonth)

    override fun toString(): String =
        String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, dayOfMonth)

    fun plusDays(days: Int): Day = utcCalendar().apply { add(Calendar.DAY_OF_MONTH, days) }.toDay()

    fun minusDays(days: Int): Day = plusDays(-days)

    /** ISO numbering: 1 = Monday … 7 = Sunday. */
    val dayOfWeek: Int
        get() = when (val value = utcCalendar().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 7
            else -> value - 1
        }

    private fun utcCalendar(): Calendar =
        GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
            clear()
            set(year, month - 1, dayOfMonth)
        }

    companion object {
        private val pattern = Regex("""(\d{4})-(\d{2})-(\d{2})""")

        fun parse(text: String): Day? {
            val (y, m, d) = pattern.matchEntire(text)?.destructured ?: return null
            return runCatching { Day(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }

        // GregorianCalendar avoids locale calendars such as the Thai Buddhist era.
        fun of(epochMillis: Long, zone: TimeZone): Day =
            GregorianCalendar(zone, Locale.ROOT).apply { timeInMillis = epochMillis }.toDay()

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private fun Calendar.toDay() =
            Day(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))
    }
}
