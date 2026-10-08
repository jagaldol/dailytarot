package com.jagaldol.dailytarot.model

enum class ReadingSource {
    /** Drawn by the app with the bundled card-dictionary fortune. */
    DEFAULT,

    /** Chosen by hand in the app. */
    MANUAL,

    /** Imported from a Lifebase daily journal note. */
    LIFEBASE,

    /** Started from the card selected in an older app version. */
    LEGACY,
}

enum class ContentStatus {
    COMPLETE,

    /** Older Lifebase notes store only the card and keywords. */
    KEYWORDS_ONLY,
}

/**
 * One saved reading per date. Text is a snapshot taken when the reading was stored, so later
 * catalog or journal changes never rewrite it silently.
 */
data class DailyReading(
    val day: Day,
    val zoneId: String,
    val cardId: Int,
    val reversed: Boolean,
    val source: ReadingSource,
    val headline: String?,
    val keywordsText: String?,
    val body: String?,
    /** The original fortune section of the journal note; never the rest of the note. */
    val fortuneRaw: String?,
    val contentStatus: ContentStatus,
    val catalogVersion: String?,
    val sourceConnectionId: String?,
    val sourceRelativePath: String?,
    val sourceHash: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(cardId in Deck.indices)
    }

    val keywords: List<String>
        get() = keywordsText.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
