package com.jagaldol.dailytarot.data.lifebase

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.jagaldol.dailytarot.model.Day
import java.io.FileNotFoundException
import java.util.Locale

/**
 * Read-only access to `Journal/YYYY/MM/YYYY-MM-DD.md` through a Storage Access Framework tree
 * grant. Paths are looked up by name; nothing outside the Journal tree is scanned.
 */
class JournalReader(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val journalDocumentId: String,
) {
    data class Entry(
        val documentId: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
    ) {
        /** Size and modification time from the folder listing; null when the provider omits them. */
        val fingerprint: String?
            get() = if (size >= 0 && lastModified > 0) "$size:$lastModified" else null
    }

    data class Located(val entry: Entry, val relativePath: String)

    sealed interface Read {
        data class Found(val text: String, val relativePath: String) : Read
        data object Missing : Read

        /** Too large or changing while read; the stored reading stays untouched. */
        data class Unreadable(val reason: String) : Read
    }

    private val childCache = HashMap<String, List<Entry>>()

    /** @throws SecurityException when the folder grant was revoked. */
    fun read(day: Day): Read = locate(day)?.let(::read) ?: Read.Missing

    /** Finds a daily note from folder listings only, without opening the file. */
    fun locate(day: Day): Located? {
        val year = child(journalDocumentId, pad(day.year, 4), directory = true) ?: return null
        val month = child(year.documentId, pad(day.month, 2), directory = true) ?: return null
        val file = child(month.documentId, "$day.md", directory = false) ?: return null
        return Located(file, "${year.name}/${month.name}/${file.name}")
    }

    /** Months that exist in the Journal, newest first. */
    fun months(): List<Pair<Int, Int>> = children(journalDocumentId)
        .filter { it.isDirectory && it.name.matches(Regex("""\d{4}""")) }
        .flatMap { year ->
            children(year.documentId)
                .filter { it.isDirectory && it.name.matches(Regex("""\d{2}""")) }
                .mapNotNull { month -> month.name.toInt().takeIf { it in 1..12 }?.let { year.name.toInt() to it } }
        }
        .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })

    /** Daily notes in one month, newest first. */
    fun days(year: Int, month: Int): List<Day> {
        val yearEntry = child(journalDocumentId, pad(year, 4), directory = true) ?: return emptyList()
        val monthEntry = child(yearEntry.documentId, pad(month, 2), directory = true) ?: return emptyList()
        return children(monthEntry.documentId)
            .filter { !it.isDirectory && it.name.endsWith(".md") }
            .mapNotNull { Day.parse(it.name.removeSuffix(".md")) }
            .filter { it.year == year && it.month == month }
            .sortedDescending()
    }

    fun children(parentDocumentId: String): List<Entry> = childCache.getOrPut(parentDocumentId) {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Entry(
                            documentId = cursor.getString(0),
                            name = cursor.getString(1).orEmpty(),
                            isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = if (cursor.isNull(3)) -1 else cursor.getLong(3),
                            lastModified = if (cursor.isNull(4)) -1 else cursor.getLong(4),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun child(parentId: String, name: String, directory: Boolean): Entry? =
        children(parentId).firstOrNull { it.name == name && it.isDirectory == directory }

    fun read(located: Located): Read {
        val entry = located.entry
        val relativePath = located.relativePath
        val limit = JournalTarotParser.MAX_NOTE_BYTES
        if (entry.size > limit) return Read.Unreadable("일지 파일이 너무 커요")
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.documentId)
        val bytes = try {
            resolver.openInputStream(uri)?.use { input -> input.readNBytesCompat(limit + 1) }
        } catch (_: FileNotFoundException) {
            return Read.Missing
        } ?: return Read.Missing
        if (bytes.size > limit) return Read.Unreadable("일지 파일이 너무 커요")
        // A size mismatch means a sync client is replacing the file; read it again later.
        if (entry.size >= 0 && bytes.size.toLong() != entry.size) return Read.Unreadable("일지 파일이 갱신되는 중이에요")
        return Read.Found(String(bytes, Charsets.UTF_8), relativePath)
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < max) {
            val read = read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun pad(value: Int, width: Int) = String.format(Locale.ROOT, "%0${width}d", value)
}
