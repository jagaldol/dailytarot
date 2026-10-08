package com.jagaldol.dailytarot.data

import com.jagaldol.dailytarot.model.ContentStatus
import com.jagaldol.dailytarot.model.DailyReading
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.TimeZone

class CatalogAndBackupTest {
    private val catalogText = File("src/main/assets/${FortuneCatalog.ASSET}").readText()

    @Test
    fun bundledCatalogHasEveryCardAndOrientationMatchingTheDeck() {
        val catalog = FortuneCatalog.parse(catalogText)
        for (card in Deck) {
            for (reversed in listOf(false, true)) {
                val entry = catalog.entry(card.id, reversed)
                assertTrue(entry.fortuneText.isNotBlank())
                assertTrue(entry.keywordsText.isNotBlank())
            }
        }
        assertEquals("컵 페이지", catalog.nameKo(60))
        assertEquals("바보", catalog.nameKo(0))
    }

    @Test
    fun jsonRoundTripsEscapesAndUnicode() {
        val value = mapOf("text" to "줄\n\"따옴표\" \\ \u0001", "n" to 3L, "list" to listOf(true, null, 1.5))
        assertEquals(value, Json.parse(Json.write(value)))
        assertEquals("é", Json.parse("\"\\u00e9\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun jsonRejectsTrailingGarbage() {
        Json.parse("{} x")
    }

    @Test
    fun backupRoundTripDropsDeviceConnectionIds() {
        val reading = DailyReading(
            Day(2026, 10, 7), "Asia/Seoul", 60, true, ReadingSource.LIFEBASE,
            "한 줄", "키워드", "본문\n둘째 줄", "> raw", ContentStatus.COMPLETE, null,
            "connection", "2026/10/2026-10-07.md", "hash", 1, 2,
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(listOf(reading), 3)).single()
        assertNull(decoded.sourceConnectionId)
        assertEquals(reading.copy(sourceConnectionId = null), decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun backupRejectsForeignFiles() {
        BackupCodec.decode("""{"schema":"other","version":1,"readings":[]}""")
    }

    @Test
    fun daysHandleLeapYearsMonthEndsAndWeekdays() {
        assertEquals(Day(2028, 2, 29), Day(2028, 3, 1).minusDays(1))
        assertEquals(Day(2027, 1, 1), Day(2026, 12, 31).plusDays(1))
        assertEquals(3, Day(2026, 10, 7).dayOfWeek) // Wednesday
        assertEquals("2026-10-07", Day(2026, 10, 7).toString())
        assertEquals(Day(2026, 10, 7), Day.parse("2026-10-07"))
        assertNull(Day.parse("2026-02-30"))
        val instant = 1_791_385_200_000L // 2026-10-07T15:00Z
        assertEquals(Day(2026, 10, 8), Day.of(instant, TimeZone.getTimeZone("Asia/Seoul")))
        assertEquals(Day(2026, 10, 7), Day.of(instant, TimeZone.getTimeZone("America/Los_Angeles")))
    }
}
