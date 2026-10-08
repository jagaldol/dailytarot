package com.jagaldol.dailytarot.data

import com.jagaldol.dailytarot.model.ContentStatus
import com.jagaldol.dailytarot.model.DailyReading
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.model.ReadingSource

/** User-initiated JSON export/restore. Connection ids are device-specific and left out. */
object BackupCodec {
    private const val SCHEMA = "daily-tarot-readings"
    private const val VERSION = 1L

    fun encode(readings: List<DailyReading>, exportedAt: Long): String = Json.write(
        linkedMapOf(
            "schema" to SCHEMA,
            "version" to VERSION,
            "exportedAt" to exportedAt,
            "readings" to readings.sortedBy { it.day }.map { reading ->
                linkedMapOf(
                    "date" to reading.day.toString(),
                    "zoneId" to reading.zoneId,
                    "cardId" to reading.cardId.toLong(),
                    "cardName" to Deck[reading.cardId].name,
                    "reversed" to reading.reversed,
                    "source" to reading.source.name,
                    "headline" to reading.headline,
                    "keywordsText" to reading.keywordsText,
                    "body" to reading.body,
                    "fortuneRaw" to reading.fortuneRaw,
                    "contentStatus" to reading.contentStatus.name,
                    "catalogVersion" to reading.catalogVersion,
                    "sourceRelativePath" to reading.sourceRelativePath,
                    "sourceHash" to reading.sourceHash,
                    "createdAt" to reading.createdAt,
                    "updatedAt" to reading.updatedAt,
                )
            },
        ),
    )

    /** @throws IllegalArgumentException for files that are not a valid backup. */
    fun decode(text: String): List<DailyReading> {
        val root = Json.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("Not a backup")
        require(root["schema"] == SCHEMA) { "Not a Daily Tarot backup" }
        require(root["version"] == VERSION) { "Unsupported backup version" }
        val items = root["readings"] as? List<*> ?: throw IllegalArgumentException("Missing readings")
        val readings = items.map { raw ->
            val item = raw as? Map<*, *> ?: throw IllegalArgumentException("Bad reading")
            fun text(key: String): String? = item[key]?.let { it as? String ?: throw IllegalArgumentException("Bad $key") }
            fun long(key: String): Long = item[key] as? Long ?: throw IllegalArgumentException("Bad $key")
            val cardId = long("cardId").toInt()
            require(cardId in Deck.indices && Deck[cardId].name == text("cardName")) { "Bad card" }
            DailyReading(
                day = text("date")?.let(Day::parse) ?: throw IllegalArgumentException("Bad date"),
                zoneId = text("zoneId").orEmpty(),
                cardId = cardId,
                reversed = item["reversed"] as? Boolean ?: throw IllegalArgumentException("Bad reversed"),
                source = enumOrNull<ReadingSource>(text("source")) ?: throw IllegalArgumentException("Bad source"),
                headline = text("headline"),
                keywordsText = text("keywordsText"),
                body = text("body"),
                fortuneRaw = text("fortuneRaw"),
                contentStatus = enumOrNull<ContentStatus>(text("contentStatus"))
                    ?: throw IllegalArgumentException("Bad status"),
                catalogVersion = text("catalogVersion"),
                sourceConnectionId = null,
                sourceRelativePath = text("sourceRelativePath"),
                sourceHash = text("sourceHash"),
                createdAt = long("createdAt"),
                updatedAt = long("updatedAt"),
            )
        }
        require(readings.map { it.day }.toSet().size == readings.size) { "Duplicate dates" }
        return readings
    }
}
