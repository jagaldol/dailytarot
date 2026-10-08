package com.jagaldol.dailytarot.work

import com.jagaldol.dailytarot.data.decodeFingerprints
import com.jagaldol.dailytarot.data.encodeFingerprints
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class SchedulingTest {
    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        GregorianCalendar(seoul, Locale.ROOT).apply {
            clear()
            set(y, m - 1, d, h, min)
        }.timeInMillis

    @Test
    fun runsLandOnTheNextOccurrenceOfTheTimeOfDay() {
        val minute = 60_000L
        // Just after midnight.
        assertEquals(61 * minute, millisUntil(at(2026, 10, 7, 23, 0), seoul, 1))
        assertEquals(2 * minute, millisUntil(at(2026, 12, 31, 23, 59), seoul, 1))
        // The 08:00 draw time: later today, or tomorrow once it has passed (including exactly 08:00).
        assertEquals(60 * minute, millisUntil(at(2026, 10, 7, 7, 0), seoul, 8 * 60))
        assertEquals(24 * 60 * minute, millisUntil(at(2026, 10, 7, 8, 0), seoul, 8 * 60))
        assertEquals(23 * 60 * minute, millisUntil(at(2026, 10, 7, 9, 0), seoul, 8 * 60))
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals((15 * 60 + 1) * minute, millisUntil(at(2026, 10, 7, 18, 0), utc, 1))
    }

    @Test
    fun fingerprintsRoundTripAndIgnoreJunk() {
        val values = mapOf("2026-10-07" to "746:1791385200000", "2026-10-06" to "512:1")
        assertEquals(values, decodeFingerprints(encodeFingerprints(values)))
        assertEquals(emptyMap<String, String>(), decodeFingerprints(null))
        assertEquals(mapOf("a" to "1"), decodeFingerprints("a=1;broken;=x;y="))
    }
}
