package com.jagaldol.dailytarot.ui

import android.content.res.Resources
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.data.FortuneCatalog
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.Deck
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

// Dates and times in the app language. Word order lives in the string resources and the month
// and weekday names come from the locale ("10월", "수요일" in Korean), so Korean keeps
// "10월 7일 수요일" and English reads "Wednesday, October 7".

// Names follow the language the strings resolved to, never a device language the app lacks.
private val Resources.locale: Locale
    get() = if (getString(R.string.content_language) == FortuneCatalog.KOREAN) Locale.KOREAN else Locale.US

private fun Resources.symbols() = DateFormatSymbols.getInstance(locale)

/** ISO 1 = Monday … 7 = Sunday to [Calendar.SUNDAY] … [Calendar.SATURDAY]. */
private fun calendarWeekday(day: Day) = day.dayOfWeek % 7 + 1

fun Day.weekdayShort(resources: Resources): String = resources.symbols().shortWeekdays[calendarWeekday(this)]

/** "10월 7일 수요일" / "Wednesday, October 7" */
fun Day.longLabel(resources: Resources): String = resources.getString(
    R.string.date_long, resources.symbols().months[month - 1], dayOfMonth,
    resources.symbols().weekdays[calendarWeekday(this)],
)

/** "2026년 10월 7일 수요일" / "Wednesday, October 7, 2026" */
fun Day.fullLabel(resources: Resources): String = resources.getString(
    R.string.date_full, resources.symbols().months[month - 1], dayOfMonth,
    resources.symbols().weekdays[calendarWeekday(this)], year,
)

/** "10.7 수" / "Wed, Oct 7" for the widget caption: Korean numbers the month, English names it. */
fun Day.shortLabel(resources: Resources): String = resources.getString(
    R.string.date_short, resources.symbols().shortMonths[month - 1], month, dayOfMonth, weekdayShort(resources),
)

/** "10월 6일" / "Oct 6" */
fun monthDayLabel(resources: Resources, month: Int, dayOfMonth: Int): String =
    resources.getString(R.string.date_month_day, resources.symbols().shortMonths[month - 1], dayOfMonth)

/** "2026년 10월" / "October 2026" */
fun monthLabel(resources: Resources, year: Int, month: Int): String =
    resources.getString(R.string.month_label, year, resources.symbols().months[month - 1])

/** "오전 8:00" / "8:00 AM" for minutes after midnight. */
fun minutesLabel(resources: Resources, minutes: Int): String {
    val hour = minutes / 60
    return resources.getString(
        if (hour < 12) R.string.clock_am else R.string.clock_pm,
        (hour + 11) % 12 + 1,
        (minutes % 60).toString().padStart(2, '0'),
    )
}

/** "컵 페이지 (Page of Cups)", or just "Page of Cups" when the localized name is the English one. */
fun cardDescription(cardId: Int, name: String): String =
    Deck[cardId].name.let { english -> if (name == english) name else "$name ($english)" }
