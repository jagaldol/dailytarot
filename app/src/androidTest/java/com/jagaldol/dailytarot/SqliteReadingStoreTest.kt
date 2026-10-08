package com.jagaldol.dailytarot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jagaldol.dailytarot.data.SqliteReadingStore
import com.jagaldol.dailytarot.model.ContentStatus
import com.jagaldol.dailytarot.model.DailyReading
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.ReadingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteReadingStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "readings-test.db"
    private val store = SqliteReadingStore(context, name)

    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    private fun reading(day: Day, cardId: Int, source: ReadingSource = ReadingSource.DEFAULT) = DailyReading(
        day, "Asia/Seoul", cardId, false, source, "한 줄", "키워드", null, null,
        ContentStatus.COMPLETE, "v", null, null, null, 1, 1,
    )

    @Test
    fun insertIfAbsentKeepsTheFirstRowUnderConcurrency() = runBlocking {
        val day = Day(2030, 1, 1)
        val results = (0 until 100).map { id ->
            async(Dispatchers.IO) { store.insertIfAbsent(reading(day, id % 78)) }
        }.awaitAll()
        assertEquals(1, results.toSet().size)
        assertEquals(1, store.all().size)
    }

    @Test
    fun putReplacesAndObserversSeeTheNewestRow() = runBlocking {
        val day = Day(2030, 1, 2)
        assertNull(store.observe(day).first())
        store.insertIfAbsent(reading(day, 1))
        store.put(reading(day, 2, ReadingSource.LIFEBASE))
        assertEquals(2, store.observe(day).first()?.cardId)
        store.insertIfAbsent(reading(day, 3))
        assertEquals(ReadingSource.LIFEBASE, store.get(day)?.source)
        store.insertIfAbsent(reading(Day(2029, 12, 31), 4))
        assertEquals(listOf(Day(2030, 1, 2), Day(2029, 12, 31)), store.observeAll().first().map { it.day })
    }
}
