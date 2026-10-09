package com.jagaldol.dailytarot.data.lifebase

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.provider.DocumentsContract
import com.jagaldol.dailytarot.data.ApplyResult
import com.jagaldol.dailytarot.data.LifebaseConnection
import com.jagaldol.dailytarot.data.ReadingRepository
import com.jagaldol.dailytarot.data.SettingsStore
import com.jagaldol.dailytarot.data.SyncState
import com.jagaldol.dailytarot.data.SyncStatus
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.Deck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class LifebaseSync(
    private val context: Context,
    private val repository: ReadingRepository,
    private val settings: SettingsStore,
) {
    private val cardIds = Deck.associate { it.name to it.id }

    sealed interface ConnectResult {
        data class Connected(val connection: LifebaseConnection) : ConnectResult
        data object JournalNotFound : ConnectResult
        data object PermissionDenied : ConnectResult
    }

    /** Validates the picked folder, keeps a read-only grant and starts a new connection id. */
    suspend fun connect(treeUri: Uri): ConnectResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            return@withContext ConnectResult.PermissionDenied
        }
        val journal = try {
            locateJournal(treeUri)
        } catch (_: SecurityException) {
            null
        }
        if (journal == null) {
            releaseGrant(treeUri.toString())
            return@withContext ConnectResult.JournalNotFound
        }
        val previous = settings.connection()
        val connection = LifebaseConnection(
            treeUri = treeUri.toString(),
            journalDocumentId = journal.first,
            label = journal.second,
            id = UUID.randomUUID().toString(),
        )
        settings.connect(connection)
        // The journal decides today's card now; the app stops drawing on its own (re-enable in settings).
        settings.setAutoDraw(false)
        if (previous != null && previous.treeUri != connection.treeUri) releaseGrant(previous.treeUri)
        ConnectResult.Connected(connection)
    }

    /**
     * A new heading can match notes that were skipped before: forget the file fingerprints so
     * the next checks read every note again.
     */
    suspend fun setJournalHeading(title: String?): Boolean {
        if (!settings.setJournalHeading(title)) return false
        settings.connection()?.let { settings.setFingerprints(it.id, emptyMap()) }
        return true
    }

    /** Imported readings stay; only the grant and the connection are removed. */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val previous = settings.connection() ?: return@withContext
        settings.disconnect()
        releaseGrant(previous.treeUri)
    }

    /**
     * [FULL] runs when the user opens the app or asks for a check: the last eight days, every file
     * read in full. [BACKGROUND] runs from scheduled work: only today and yesterday, and a note
     * whose size and modification time match the previous read is not opened again.
     */
    enum class Mode(val days: Int, val skipUnchanged: Boolean) { FULL(8, false), BACKGROUND(2, true) }

    suspend fun syncRecent(mode: Mode = Mode.FULL): SyncState = withContext(Dispatchers.IO) {
        val connection = settings.connection() ?: return@withContext SyncState.NEVER
        val today = repository.currentDay()
        val reader = reader(connection)
        val titles = JournalTarotParser.titles(settings.journalHeading())
        val previous = settings.fingerprints()
        val seen = HashMap<String, String>()
        var status = SyncStatus(SyncState.WAITING, System.currentTimeMillis())
        try {
            for (offset in 0 until mode.days) {
                val day = today.minusDays(offset)
                val located = reader.locate(day)
                val fingerprint = located?.entry?.fingerprint
                val outcome = when {
                    located == null -> SyncState.WAITING to null
                    mode.skipUnchanged && fingerprint != null && previous[day.toString()] == fingerprint -> {
                        seen[day.toString()] = fingerprint
                        unchangedState(day) to null
                    }
                    else -> importDay(reader.read(located), connection, day, titles).also { (state, _) ->
                        if (fingerprint != null && state != SyncState.ERROR) seen[day.toString()] = fingerprint
                    }
                }
                if (offset == 0) status = status.copy(state = outcome.first, message = outcome.second)
            }
        } catch (_: SecurityException) {
            status = SyncStatus(SyncState.PERMISSION_LOST, System.currentTimeMillis())
        }
        // A heading changed meanwhile: this run read with the old one, so it records nothing.
        if (JournalTarotParser.titles(settings.journalHeading()) != titles) return@withContext status.state
        // Keep fingerprints for dates still inside the window; older ones are never skipped anyway.
        val window = (0 until Mode.FULL.days).map { today.minusDays(it).toString() }.toSet()
        settings.setFingerprints(connection.id, (previous + seen).filterKeys { it in window })
        settings.setSync(connection.id, status)
        status.state
    }

    /** An untouched note keeps whatever it produced last time. */
    private suspend fun unchangedState(day: Day): SyncState =
        if (repository.isFromLifebase(day)) SyncState.UPDATED else SyncState.WAITING

    /**
     * Walks the whole Journal newest month first. Safe to stop and run again: every date is
     * applied independently through the same upsert rules.
     */
    suspend fun importHistory(
        isStopped: () -> Boolean,
        /** [month] is `YYYY-MM`; the screen words it in the app language. */
        progress: suspend (monthsDone: Int, monthsTotal: Int, imported: Int, month: String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val connection = settings.connection() ?: return@withContext 0
        val reader = reader(connection)
        val titles = JournalTarotParser.titles(settings.journalHeading())
        val months = reader.months()
        var imported = 0
        for ((index, month) in months.withIndex()) {
            if (isStopped() || settings.connectionId() != connection.id) break
            for (day in reader.days(month.first, month.second)) {
                if (isStopped()) break
                if (importDay(reader.read(day), connection, day, titles).first == SyncState.UPDATED) imported++
            }
            progress(index + 1, months.size, imported, "%04d-%02d".format(Locale.ROOT, month.first, month.second))
        }
        imported
    }

    private suspend fun importDay(
        read: JournalReader.Read,
        connection: LifebaseConnection,
        day: Day,
        titles: List<String>,
    ): Pair<SyncState, String?> =
        when (read) {
            JournalReader.Read.Missing -> SyncState.WAITING to null
            is JournalReader.Read.Unreadable -> SyncState.ERROR to read.reason
            is JournalReader.Read.Found -> when (val parsed = JournalTarotParser.parse(read.text, cardIds, titles)) {
                is JournalTarotParser.Parsed -> {
                    val result = repository.applyLifebase(day, parsed, read.relativePath, connection.id)
                    (if (result == ApplyResult.STALE) SyncState.WAITING else SyncState.UPDATED) to null
                }
                JournalTarotParser.NoCard -> SyncState.WAITING to null
                is JournalTarotParser.Incomplete -> SyncState.WAITING to parsed.reason
                is JournalTarotParser.Invalid -> SyncState.ERROR to parsed.reason
            }
        }

    private fun reader(connection: LifebaseConnection) =
        JournalReader(context.contentResolver, connection.treeUri.toUri(), connection.journalDocumentId)

    /** Accepts the Journal folder itself or a vault root that contains `Journal/`. */
    private fun locateJournal(treeUri: Uri): Pair<String, String>? {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val reader = JournalReader(context.contentResolver, treeUri, rootId)
        val children = reader.children(rootId)
        val rootName = rootId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Journal" }
        val isJournal: (List<JournalReader.Entry>) -> Boolean = { entries ->
            entries.any { it.isDirectory && it.name.matches(Regex("""\d{4}""")) }
        }
        children.firstOrNull { it.isDirectory && it.name == "Journal" }?.let { journal ->
            if (isJournal(reader.children(journal.documentId))) return journal.documentId to "$rootName/Journal"
        }
        return if (isJournal(children)) rootId to rootName else null
    }

    private fun releaseGrant(uri: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
    }
}
