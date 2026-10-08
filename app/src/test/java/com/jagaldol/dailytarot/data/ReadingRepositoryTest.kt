package com.jagaldol.dailytarot.data

import com.jagaldol.dailytarot.data.lifebase.JournalTarotParser
import com.jagaldol.dailytarot.model.ContentStatus
import com.jagaldol.dailytarot.model.DailyReading
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.ReadingSource
import com.jagaldol.dailytarot.model.TodaySelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.random.Random

class ReadingRepositoryTest {
    private val catalog = FortuneCatalog.parse(File("src/main/assets/${FortuneCatalog.ASSET}").readText())
    private val store = FakeStore()
    private val settings = FakeSettings()
    private var clock = millis(2026, 10, 7, 9)
    private var notifications = 0

    private fun repository(random: Random = Random(7)) = ReadingRepository(
        store, settings, catalog, now = { clock }, random = random, todayChanged = { notifications++ },
    )

    @Test
    fun firstDrawOfTheDayIsStoredWithItsCatalogTextAndReused() = runBlocking {
        val repo = repository()
        val first = repo.drawToday()
        assertEquals(Day(2026, 10, 7), first.day)
        assertEquals(ReadingSource.DEFAULT, first.source)
        val entry = catalog.entry(first.cardId, first.reversed)
        assertEquals(entry.fortuneText, first.headline)
        assertEquals(entry.keywordsText, first.keywordsText)
        assertEquals(catalog.version, first.catalogVersion)
        // Asking again changes nothing and does not refresh widgets.
        val after = notifications
        assertEquals(first, repo.drawToday())
        assertEquals(after, notifications)
        // A restart (new repository, different random) keeps the same card.
        assertEquals(first, repository(Random(99)).drawToday())
    }

    @Test
    fun concurrentRequestsCreateExactlyOneReading() = runBlocking {
        val repo = repository(Random(1))
        val results = (1..100).map { async(Dispatchers.Default) { repo.drawToday() } }.awaitAll()
        assertEquals(1, results.toSet().size)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun theDateFollowsTheSavedZoneAcrossMidnight() = runBlocking {
        val repo = repository()
        clock = millis(2026, 12, 31, 23, zone = "Asia/Seoul")
        assertEquals(Day(2026, 12, 31), repo.drawToday().day)
        clock += 2 * 60 * 60 * 1000
        assertEquals(Day(2027, 1, 1), repo.drawToday().day)
        assertEquals(2, store.rows.size)
    }

    @Test
    fun missedDaysAreNeverBackfilled() = runBlocking {
        val repo = repository()
        repo.refreshToday()
        clock += 5L * 24 * 60 * 60 * 1000
        repo.refreshToday()
        assertEquals(listOf(Day(2026, 10, 12), Day(2026, 10, 7)), store.rows.keys.sortedDescending())
    }

    @Test
    fun legacySelectionStartsTheFirstRecordOnceAndMayBeRedrawn() = runBlocking {
        settings.legacy = TodaySelection(17, true)
        val repo = repository()
        val first = repo.drawToday()
        assertEquals(ReadingSource.LEGACY, first.source)
        assertEquals(17, first.cardId)
        assertTrue(first.reversed)
        assertTrue(settings.legacyMigrated)
        assertTrue(repo.redrawLegacy())
        assertEquals(ReadingSource.DEFAULT, store.rows.getValue(first.day).source)
        assertFalse(repo.redrawLegacy())
    }

    @Test
    fun manualSelectionReplacesTodayUnlessLifebaseIsLinked() = runBlocking {
        val repo = repository()
        repo.drawToday()
        assertTrue(repo.selectManually(21, false))
        val manual = store.rows.getValue(Day(2026, 10, 7))
        assertEquals(ReadingSource.MANUAL, manual.source)
        assertEquals(catalog.entry(21, false).fortuneText, manual.headline)
        settings.connection = "c1"
        assertFalse(repo.selectManually(0, false))
        assertEquals(21, store.rows.getValue(Day(2026, 10, 7)).cardId)
    }

    @Test
    fun lifebaseReplacesTheLocalCardAtomicallyAndLateDrawsCannotOverwriteIt() = runBlocking {
        settings.connection = "c1"
        val repo = repository()
        val local = repo.drawToday()
        val parsed = parsed(cardId = (local.cardId + 1) % 78, reversed = !local.reversed)
        assertEquals(ApplyResult.APPLIED, repo.applyLifebase(local.day, parsed, "2026/10/2026-10-07.md", "c1"))
        val imported = store.rows.getValue(local.day)
        assertEquals(ReadingSource.LIFEBASE, imported.source)
        assertEquals(parsed.cardId, imported.cardId)
        assertEquals(parsed.reversed, imported.reversed)
        assertEquals(parsed.headline, imported.headline)
        assertEquals(parsed.body, imported.body)
        assertNull(imported.catalogVersion)
        assertEquals(imported, repo.drawToday())
        assertEquals(imported, repo.refreshToday().reading)
    }

    @Test
    fun repeatedImportsAndOtherConnectionsChangeNothing() = runBlocking {
        settings.connection = "c1"
        val repo = repository()
        val day = Day(2026, 10, 7)
        val parsed = parsed()
        repo.applyLifebase(day, parsed, "p", "c1")
        val before = notifications
        assertEquals(ApplyResult.UNCHANGED, repo.applyLifebase(day, parsed, "p", "c1"))
        assertEquals(before, notifications)
        assertEquals(ApplyResult.STALE, repo.applyLifebase(day, parsed(headline = "다른 결과"), "p", "old"))
        assertEquals(parsed.headline, store.rows.getValue(day).headline)
    }

    @Test
    fun editedFortuneTextUpdatesButACompleteReadingIsNotDowngraded() = runBlocking {
        settings.connection = "c1"
        val repo = repository()
        val day = Day(2026, 10, 6)
        repo.applyLifebase(day, parsed(), "p", "c1")
        assertEquals(ApplyResult.APPLIED, repo.applyLifebase(day, parsed(headline = "고친 한 줄"), "p", "c1"))
        assertEquals("고친 한 줄", store.rows.getValue(day).headline)
        val keywordsOnly = parsed(headline = null, body = null, status = ContentStatus.KEYWORDS_ONLY)
        assertEquals(ApplyResult.KEPT, repo.applyLifebase(day, keywordsOnly, "p", "c1"))
        assertEquals("고친 한 줄", store.rows.getValue(day).headline)
    }

    @Test
    fun savedTextIsASnapshotIndependentOfTheCatalog() = runBlocking {
        val first = repository().drawToday()
        val edited = FortuneCatalog(
            "test",
            (0 until 78).flatMap { id ->
                listOf(false, true).map { FortuneEntry(id, it, "이름", "키워드", "바뀐 문구") }
            },
        )
        val again = ReadingRepository(store, settings, edited, now = { clock }).drawToday()
        assertEquals(first.headline, again.headline)
    }

    @Test
    fun restoreFillsOnlyEmptyDates() = runBlocking {
        val repo = repository()
        val today = repo.drawToday()
        val backup = listOf(
            today.copy(cardId = 3, headline = "backup"),
            today.copy(day = Day(2026, 1, 1), cardId = 4),
        )
        assertEquals(1, repo.restore(backup))
        assertEquals(today, store.rows.getValue(today.day))
        assertEquals(4, store.rows.getValue(Day(2026, 1, 1)).cardId)
    }

    @Test
    fun beforeTheDayStartYesterdaysCardStaysUpAndNothingIsDrawn() = runBlocking {
        settings.dayStart = 8 * 60
        val repo = repository()
        clock = millis(2026, 10, 6, 9)
        val yesterday = repo.drawToday()
        clock = millis(2026, 10, 7, 7)
        val view = repo.refreshToday()
        assertEquals(Day(2026, 10, 7), view.day)
        assertTrue(view.showingPrevious)
        assertEquals(yesterday, view.reading)
        assertNull(store.rows[Day(2026, 10, 7)])
    }

    @Test
    fun todaysCardReplacesYesterdaysEvenBeforeTheDayStart() = runBlocking {
        settings.dayStart = 8 * 60
        settings.connection = "c1"
        val repo = repository()
        clock = millis(2026, 10, 6, 9)
        repo.drawToday()
        clock = millis(2026, 10, 7, 6)
        repo.applyLifebase(Day(2026, 10, 7), parsed(), "p", "c1")
        val view = repo.refreshToday()
        assertFalse(view.showingPrevious)
        assertEquals(ReadingSource.LIFEBASE, view.reading?.source)
    }

    @Test
    fun afterTheDrawTimeAutomaticDrawingFillsTheDay() = runBlocking {
        val repo = repository()
        clock = millis(2026, 10, 7, 8)
        val view = repo.refreshToday()
        assertEquals(ReadingSource.DEFAULT, view.reading?.source)
        assertFalse(view.awaitingDraw)
    }

    @Test
    fun withoutAutomaticDrawingTheDayWaitsFaceDown() = runBlocking {
        settings.autoDraw = false
        val repo = repository()
        clock = millis(2026, 10, 6, 9)
        repo.drawToday()
        clock = millis(2026, 10, 7, 10)
        val view = repo.refreshToday()
        assertTrue(view.awaitingDraw)
        assertFalse(view.showingPrevious)
        assertNull(store.rows[Day(2026, 10, 7)])
        // A tap draws it.
        assertEquals(Day(2026, 10, 7), repo.drawToday().day)
        assertFalse(repo.refreshToday().awaitingDraw)
    }

    @Test
    fun aLaterAutomaticDrawTimeIsRespected() = runBlocking {
        settings.autoDrawAt = 21 * 60
        val repo = repository()
        clock = millis(2026, 10, 7, 20)
        assertTrue(repo.refreshToday().awaitingDraw)
        clock = millis(2026, 10, 7, 21)
        assertFalse(repo.refreshToday().awaitingDraw)
    }

    @Test
    fun withTheDefaultMidnightStartANewDayGoesFaceDownUntilTheAutomaticDraw() = runBlocking {
        val repo = repository()
        clock = millis(2026, 10, 6, 9)
        repo.refreshToday()
        clock = millis(2026, 10, 7, 0) + 10 * 60_000
        val night = repo.refreshToday()
        assertTrue(night.awaitingDraw)
        assertFalse(night.showingPrevious)
        clock = millis(2026, 10, 7, 8)
        assertFalse(repo.refreshToday().awaitingDraw)
    }

    @Test
    fun theAutomaticDrawNeverRunsBeforeTheDayStart() = runBlocking {
        settings.dayStart = 9 * 60
        settings.autoDrawAt = 7 * 60
        val repo = repository()
        clock = millis(2026, 10, 7, 8)
        assertNull(repo.refreshToday().reading)
        clock = millis(2026, 10, 7, 9)
        assertEquals(Day(2026, 10, 7), repo.refreshToday().reading?.day)
    }

    @Test
    fun appRecordsAreAlwaysDeletableButImportedOnesOnlyWhileUnlinked() = runBlocking {
        val repo = repository()
        val drawn = repo.drawToday()
        settings.connection = "c1"
        val imported = Day(2026, 10, 6)
        repo.applyLifebase(imported, parsed(), "p", "c1")
        // Linked: the app's own card goes, the journal's stays.
        assertFalse(repo.delete(imported))
        assertTrue(repo.delete(drawn.day))
        assertNull(store.rows[drawn.day])
        repo.drawToday()
        assertEquals(1, repo.deleteAll())
        assertEquals(listOf(imported), store.rows.keys.toList())
        // Unlinked: everything can go.
        settings.connection = null
        assertTrue(repo.delete(imported))
        repo.drawToday()
        assertEquals(1, repo.deleteAll())
        assertTrue(store.rows.isEmpty())
        assertFalse(repo.delete(imported))
    }

    private fun parsed(
        cardId: Int = 60,
        reversed: Boolean = false,
        headline: String? = "한 줄",
        body: String? = "본문",
        status: ContentStatus = ContentStatus.COMPLETE,
    ) = JournalTarotParser.Parsed(
        cardId, reversed, headline, "키워드", body, "raw $headline $body $cardId $reversed", status,
        hash = "$cardId|$reversed|$headline|$body|$status",
    )

    private fun millis(y: Int, m: Int, d: Int, h: Int, zone: String = "Asia/Seoul"): Long =
        GregorianCalendar(TimeZone.getTimeZone(zone)).apply {
            clear()
            set(y, m - 1, d, h, 0)
        }.timeInMillis

    private class FakeSettings : ReadingSettings {
        var legacy: TodaySelection? = null
        var legacyMigrated = false
        var connection: String? = null
        var autoDraw = true
        var dayStart = 0
        var autoDrawAt = 8 * 60
        override suspend fun zoneId() = "Asia/Seoul"
        override suspend fun autoDraw() = autoDraw
        override suspend fun dayStartMinutes() = dayStart
        override suspend fun autoDrawMinutes() = autoDrawAt
        override suspend fun legacySelection() = legacy.takeUnless { legacyMigrated }
        override suspend fun markLegacyMigrated() {
            legacyMigrated = true
        }
        override suspend fun connectionId() = connection
    }

    private class FakeStore : ReadingStore {
        val rows = linkedMapOf<Day, DailyReading>()
        private val lock = Mutex()
        private val version = MutableStateFlow(0)

        override suspend fun get(day: Day) = lock.withLock { rows[day] }
        override suspend fun insertIfAbsent(reading: DailyReading) = lock.withLock {
            rows.getOrPut(reading.day) { reading.also { version.value++ } }
        }
        override suspend fun put(reading: DailyReading) {
            lock.withLock {
                rows[reading.day] = reading
                version.value++
            }
        }
        override suspend fun delete(day: Day) {
            lock.withLock {
                rows.remove(day)
                version.value++
            }
        }
        override suspend fun deleteAll(): Int = lock.withLock {
            rows.size.also {
                rows.clear()
                version.value++
            }
        }
        override suspend fun deleteWhereSourceIsNot(source: ReadingSource): Int = lock.withLock {
            val gone = rows.values.filter { it.source != source }.map { it.day }
            gone.forEach(rows::remove)
            version.value++
            gone.size
        }
        override suspend fun all() = lock.withLock { rows.values.sortedByDescending { it.day } }
        override suspend fun isEmpty() = lock.withLock { rows.isEmpty() }
        override fun observe(day: Day): Flow<DailyReading?> = version.map { rows[day] }
        override fun observeAll(): Flow<List<DailyReading>> = version.map { rows.values.toList() }
    }
}
