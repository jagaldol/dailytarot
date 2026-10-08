package com.jagaldol.dailytarot.data

import android.content.Context
import com.jagaldol.dailytarot.model.Deck

data class FortuneEntry(
    val cardId: Int,
    val reversed: Boolean,
    val nameKo: String,
    val keywordsText: String,
    val fortuneText: String,
)

/** The 156 upright/reversed fortunes copied verbatim from the Lifebase card dictionary. */
class FortuneCatalog(val version: String, entries: List<FortuneEntry>) {
    private val byKey = entries.associateBy { key(it.cardId, it.reversed) }

    init {
        require(entries.size == Deck.size * 2 && byKey.size == entries.size) {
            "Catalog must hold one entry per card and orientation"
        }
        require(byKey.keys == (0 until Deck.size * 2).toSet())
    }

    fun entry(cardId: Int, reversed: Boolean): FortuneEntry = byKey.getValue(key(cardId, reversed))

    fun nameKo(cardId: Int): String = entry(cardId, false).nameKo

    private fun key(cardId: Int, reversed: Boolean) = cardId * 2 + if (reversed) 1 else 0

    companion object {
        const val ASSET = "default_fortunes.ko.json"

        @Volatile private var loaded: FortuneCatalog? = null

        fun load(context: Context): FortuneCatalog = loaded ?: synchronized(this) {
            loaded ?: parse(
                context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() },
            ).also { loaded = it }
        }

        fun parse(text: String): FortuneCatalog {
            val root = Json.parse(text) as Map<*, *>
            require(root["schemaVersion"] == 1L) { "Unsupported catalog schema" }
            val entries = (root["entries"] as List<*>).map { raw ->
                val item = raw as Map<*, *>
                val cardId = (item["cardId"] as Long).toInt()
                require(Deck.getOrNull(cardId)?.name == item["cardName"]) { "Card $cardId name mismatch" }
                FortuneEntry(
                    cardId = cardId,
                    reversed = item["reversed"] as Boolean,
                    nameKo = item["nameKo"] as String,
                    keywordsText = item["keywordsText"] as String,
                    fortuneText = item["fortuneText"] as String,
                )
            }
            return FortuneCatalog(root["catalogVersion"] as String, entries)
        }
    }
}
