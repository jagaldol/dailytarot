package com.jagaldol.dailytarot.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jagaldol.dailytarot.model.ContentStatus
import com.jagaldol.dailytarot.model.DailyReading
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.ReadingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Date-keyed reading storage. Implementations must keep at most one row per date. */
interface ReadingStore {
    suspend fun get(day: Day): DailyReading?

    /** Stores [reading] only if its date is empty; returns whichever row is stored. */
    suspend fun insertIfAbsent(reading: DailyReading): DailyReading

    /** Replaces the row of the reading's date. */
    suspend fun put(reading: DailyReading)

    suspend fun delete(day: Day)

    /** Returns the number of deleted rows. */
    suspend fun deleteAll(): Int

    /** Deletes rows from every other source; returns the number of deleted rows. */
    suspend fun deleteWhereSourceIsNot(source: ReadingSource): Int

    suspend fun all(): List<DailyReading>

    suspend fun isEmpty(): Boolean

    fun observe(day: Day): Flow<DailyReading?>

    /** Newest date first. */
    fun observeAll(): Flow<List<DailyReading>>
}

class SqliteReadingStore(context: Context, name: String = DATABASE) : ReadingStore {
    private val helper = Helper(context.applicationContext, name)

    // Every write bumps the version; observers re-query. All writers live in this process.
    private val version = MutableStateFlow(0L)

    override suspend fun get(day: Day): DailyReading? = io {
        helper.readableDatabase.query(
            TABLE, null, "date = ?", arrayOf(day.toString()), null, null, null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toReading() else null }
    }

    override suspend fun insertIfAbsent(reading: DailyReading): DailyReading {
        val id = io {
            helper.writableDatabase.insertWithOnConflict(
                TABLE, null, reading.toValues(), SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
        if (id != -1L) version.update { it + 1 }
        return get(reading.day) ?: error("Reading for ${reading.day} disappeared")
    }

    override suspend fun put(reading: DailyReading) = io {
        helper.writableDatabase.insertWithOnConflict(
            TABLE, null, reading.toValues(), SQLiteDatabase.CONFLICT_REPLACE,
        )
        version.update { it + 1 }
        Unit
    }

    override suspend fun delete(day: Day) {
        io { helper.writableDatabase.delete(TABLE, "date = ?", arrayOf(day.toString())) }
        version.update { it + 1 }
    }

    override suspend fun deleteAll(): Int {
        val count = io { helper.writableDatabase.delete(TABLE, "1", null) }
        version.update { it + 1 }
        return count
    }

    override suspend fun deleteWhereSourceIsNot(source: ReadingSource): Int {
        val count = io { helper.writableDatabase.delete(TABLE, "source != ?", arrayOf(source.name)) }
        version.update { it + 1 }
        return count
    }

    override suspend fun all(): List<DailyReading> = io {
        helper.readableDatabase.query(TABLE, null, null, null, null, null, "date DESC").use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.toReading()?.let(::add) }
        }
    }

    override suspend fun isEmpty(): Boolean = io {
        helper.readableDatabase.rawQuery("SELECT 1 FROM $TABLE LIMIT 1", null).use { !it.moveToFirst() }
    }

    override fun observe(day: Day): Flow<DailyReading?> =
        version.map { get(day) }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override fun observeAll(): Flow<List<DailyReading>> =
        version.map { all() }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private class Helper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    date TEXT PRIMARY KEY NOT NULL,
                    zone_id TEXT NOT NULL,
                    card_id INTEGER NOT NULL CHECK (card_id BETWEEN 0 AND 77),
                    reversed INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    headline TEXT,
                    keywords_text TEXT,
                    body TEXT,
                    fortune_raw TEXT,
                    content_status TEXT NOT NULL,
                    catalog_version TEXT,
                    source_connection_id TEXT,
                    source_relative_path TEXT,
                    source_hash TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        const val DATABASE = "readings.db"
        private const val TABLE = "daily_reading"

        private fun DailyReading.toValues() = ContentValues().apply {
            put("date", day.toString())
            put("zone_id", zoneId)
            put("card_id", cardId)
            put("reversed", if (reversed) 1 else 0)
            put("source", source.name)
            put("headline", headline)
            put("keywords_text", keywordsText)
            put("body", body)
            put("fortune_raw", fortuneRaw)
            put("content_status", contentStatus.name)
            put("catalog_version", catalogVersion)
            put("source_connection_id", sourceConnectionId)
            put("source_relative_path", sourceRelativePath)
            put("source_hash", sourceHash)
            put("created_at", createdAt)
            put("updated_at", updatedAt)
        }

        private fun Cursor.toReading(): DailyReading? {
            fun text(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
            fun long(name: String): Long = getLong(getColumnIndexOrThrow(name))
            val day = Day.parse(text("date").orEmpty()) ?: return null
            return DailyReading(
                day = day,
                zoneId = text("zone_id").orEmpty(),
                cardId = long("card_id").toInt(),
                reversed = long("reversed") != 0L,
                source = enumOrNull<ReadingSource>(text("source")) ?: ReadingSource.DEFAULT,
                headline = text("headline"),
                keywordsText = text("keywords_text"),
                body = text("body"),
                fortuneRaw = text("fortune_raw"),
                contentStatus = enumOrNull<ContentStatus>(text("content_status")) ?: ContentStatus.COMPLETE,
                catalogVersion = text("catalog_version"),
                sourceConnectionId = text("source_connection_id"),
                sourceRelativePath = text("source_relative_path"),
                sourceHash = text("source_hash"),
                createdAt = long("created_at"),
                updatedAt = long("updated_at"),
            )
        }
    }
}

internal inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    enumValues<T>().firstOrNull { it.name == name }
