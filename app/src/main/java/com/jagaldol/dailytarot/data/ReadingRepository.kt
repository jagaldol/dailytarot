package com.jagaldol.dailytarot.data

import com.jagaldol.dailytarot.data.lifebase.JournalTarotParser
import com.jagaldol.dailytarot.model.ContentStatus
import com.jagaldol.dailytarot.model.DailyReading
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.model.ReadingSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

enum class ApplyResult { APPLIED, UNCHANGED, KEPT, STALE }

/**
 * What the app and widget show for "today". Before the daily draw time the previous day's
 * card stays up until today's exists; after it, an empty date shows a face-down card.
 */
data class TodayView(
    val day: Day,
    val reading: DailyReading?,
    val showingPrevious: Boolean,
) {
    val awaitingDraw: Boolean get() = reading == null
}

/**
 * Single writer for daily readings. Local draws only fill empty dates; Lifebase results replace
 * them. Writes are serialized so a late local draw can never overwrite an imported reading.
 */
class ReadingRepository(
    private val store: ReadingStore,
    private val settings: ReadingSettings,
    private val catalog: FortuneCatalog,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val todayChanged: suspend () -> Unit = {},
) {
    private data class Clock(val day: Day, val dayStarted: Boolean)

    private val writes = Mutex()
    private val clock = MutableStateFlow<Clock?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayView: Flow<TodayView> = clock.filterNotNull().flatMapLatest { c ->
        combine(store.observe(c.day), store.observe(c.day.minusDays(1))) { today, previous ->
            view(c, today, previous)
        }
    }

    val history: Flow<List<DailyReading>> = store.observeAll()

    fun observe(day: Day): Flow<DailyReading?> = store.observe(day)

    suspend fun currentDay(): Day = Day.of(now(), TimeZone.getTimeZone(settings.zoneId()))

    /**
     * Re-reads the clock. Before the day start yesterday's card stays up; after it an empty date
     * shows a face-down card, which automatic drawing (when on) fills at its own time.
     */
    suspend fun refreshToday(): TodayView {
        val zone = TimeZone.getTimeZone(settings.zoneId())
        val time = now()
        val day = Day.of(time, zone)
        val minute = minuteOfDay(time, zone)
        val dayStart = settings.dayStartMinutes()
        val started = minute >= dayStart
        val autoDue = settings.autoDraw() && minute >= maxOf(dayStart, settings.autoDrawMinutes())
        if (autoDue && store.get(day) == null) draw(day)
        val current = Clock(day, started)
        if (clock.value != current) {
            clock.value = current
            notifyToday()
        }
        return view(current, store.get(day), store.get(day.minusDays(1)))
    }

    /** Draws today's card now (a tap on the face-down card, or the automatic draw). */
    suspend fun drawToday(): DailyReading {
        val day = currentDay()
        val reading = draw(day)
        refreshToday()
        return reading
    }

    private suspend fun draw(day: Day): DailyReading {
        store.get(day)?.let { return it }
        val reading = writes.withLock {
            store.get(day)?.let { return@withLock null }
            // An older app version kept one undated card; it may start the very first record.
            val legacy = if (store.isEmpty()) settings.legacySelection() else null
            val draft = legacy?.cardId?.let { snapshot(day, it, legacy.reversed, ReadingSource.LEGACY) }
                ?: snapshot(day, random.nextInt(Deck.size), random.nextBoolean(), ReadingSource.DEFAULT)
            store.insertIfAbsent(draft)
        }
        settings.markLegacyMigrated()
        if (reading == null) return store.get(day) ?: error("Reading for $day disappeared")
        notifyToday()
        return reading
    }

    private fun view(c: Clock, today: DailyReading?, previous: DailyReading?): TodayView = when {
        today != null -> TodayView(c.day, today, showingPrevious = false)
        !c.dayStarted && previous != null -> TodayView(c.day, previous, showingPrevious = true)
        else -> TodayView(c.day, null, showingPrevious = false)
    }

    /** Hand-picked card for today. Refused while Lifebase is the source of truth. */
    suspend fun selectManually(cardId: Int, reversed: Boolean): Boolean {
        if (settings.connectionId() != null) return false
        val day = currentDay()
        writes.withLock {
            val existing = store.get(day)
            store.put(
                snapshot(day, cardId, reversed, ReadingSource.MANUAL)
                    .copy(createdAt = existing?.createdAt ?: now()),
            )
        }
        settings.markLegacyMigrated()
        notifyToday()
        refreshToday()
        return true
    }

    /** A record carried over from the old single-card app may be replaced by one fresh draw. */
    suspend fun redrawLegacy(): Boolean {
        val day = currentDay()
        val replaced = writes.withLock {
            val existing = store.get(day)
            if (existing?.source != ReadingSource.LEGACY) return@withLock false
            store.put(
                snapshot(day, random.nextInt(Deck.size), random.nextBoolean(), ReadingSource.DEFAULT)
                    .copy(createdAt = existing.createdAt),
            )
            true
        }
        if (replaced) notifyToday()
        return replaced
    }

    suspend fun applyLifebase(
        day: Day,
        parsed: JournalTarotParser.Parsed,
        relativePath: String,
        connectionId: String,
    ): ApplyResult {
        val result = writes.withLock {
            if (settings.connectionId() != connectionId) return@withLock ApplyResult.STALE
            val existing = store.get(day)
            if (existing?.source == ReadingSource.LIFEBASE) {
                if (existing.sourceHash == parsed.hash) return@withLock ApplyResult.UNCHANGED
                val downgrade = existing.contentStatus == ContentStatus.COMPLETE &&
                    parsed.status == ContentStatus.KEYWORDS_ONLY &&
                    existing.cardId == parsed.cardId && existing.reversed == parsed.reversed
                if (downgrade) return@withLock ApplyResult.KEPT
            }
            val time = now()
            store.put(
                DailyReading(
                    day = day,
                    zoneId = existing?.zoneId ?: settings.zoneId(),
                    cardId = parsed.cardId,
                    reversed = parsed.reversed,
                    source = ReadingSource.LIFEBASE,
                    headline = parsed.headline,
                    keywordsText = parsed.keywordsText,
                    body = parsed.body,
                    fortuneRaw = parsed.fortuneRaw,
                    contentStatus = parsed.status,
                    catalogVersion = null,
                    sourceConnectionId = connectionId,
                    sourceRelativePath = relativePath,
                    sourceHash = parsed.hash,
                    createdAt = existing?.createdAt ?: time,
                    updatedAt = time,
                ),
            )
            ApplyResult.APPLIED
        }
        val today = currentDay()
        if (result == ApplyResult.APPLIED && (day == today || day == today.minusDays(1))) notifyToday()
        return result
    }

    /** Restores backed-up readings into empty dates only; existing dates are never overwritten. */
    suspend fun restore(readings: List<DailyReading>): Int {
        var added = 0
        writes.withLock {
            for (reading in readings) {
                if (store.get(reading.day) == null) {
                    store.insertIfAbsent(reading.copy(sourceConnectionId = null))
                    added++
                }
            }
        }
        if (added > 0) {
            settings.markLegacyMigrated()
            notifyToday()
        }
        return added
    }

    suspend fun all(): List<DailyReading> = store.all()

    /**
     * Records the app made (drawn or picked) can always be deleted. Imported Lifebase records
     * only while unlinked: the journal would bring them straight back otherwise.
     */
    suspend fun canDelete(reading: DailyReading): Boolean =
        reading.source != ReadingSource.LIFEBASE || settings.connectionId() == null

    suspend fun delete(day: Day): Boolean {
        val deleted = writes.withLock {
            val existing = store.get(day) ?: return@withLock false
            if (!canDelete(existing)) return@withLock false
            store.delete(day)
            true
        }
        val today = currentDay()
        if (deleted && (day == today || day == today.minusDays(1))) notifyToday()
        return deleted
    }

    /** Deletes every deletable record (see [canDelete]) and returns how many went. */
    suspend fun deleteAll(): Int {
        val keepLifebase = settings.connectionId() != null
        val count = writes.withLock {
            if (keepLifebase) store.deleteWhereSourceIsNot(ReadingSource.LIFEBASE) else store.deleteAll()
        }
        if (count > 0) notifyToday()
        return count
    }

    suspend fun isFromLifebase(day: Day): Boolean = store.get(day)?.source == ReadingSource.LIFEBASE

    fun defaultFortune(cardId: Int, reversed: Boolean): FortuneEntry = catalog.entry(cardId, reversed)

    private suspend fun snapshot(day: Day, cardId: Int, reversed: Boolean, source: ReadingSource): DailyReading {
        val entry = catalog.entry(cardId, reversed)
        val time = now()
        return DailyReading(
            day = day,
            zoneId = settings.zoneId(),
            cardId = cardId,
            reversed = reversed,
            source = source,
            headline = entry.fortuneText,
            keywordsText = entry.keywordsText,
            body = null,
            fortuneRaw = null,
            contentStatus = ContentStatus.COMPLETE,
            catalogVersion = catalog.version,
            sourceConnectionId = null,
            sourceRelativePath = null,
            sourceHash = null,
            createdAt = time,
            updatedAt = time,
        )
    }

    // A widget refresh failure must never undo or block a saved reading.
    private suspend fun notifyToday() {
        try {
            todayChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}

internal fun minuteOfDay(epochMillis: Long, zone: TimeZone): Int =
    GregorianCalendar(zone, Locale.ROOT).apply { timeInMillis = epochMillis }.let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }
