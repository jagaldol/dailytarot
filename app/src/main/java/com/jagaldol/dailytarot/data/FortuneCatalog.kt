package com.jagaldol.dailytarot.data

import android.content.Context
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.R

data class FortuneEntry(
    val cardId: Int,
    val reversed: Boolean,
    /** The card name in the catalog's language. */
    val name: String,
    val keywordsText: String,
    val fortuneText: String,
)

/** The 156 upright/reversed fortunes copied verbatim from one language's Lifebase card dictionary. */
class FortuneCatalog(val version: String, entries: List<FortuneEntry>) {
    private val byKey = entries.associateBy { key(it.cardId, it.reversed) }

    init {
        require(entries.size == Deck.size * 2 && byKey.size == entries.size) {
            "Catalog must hold one entry per card and orientation"
        }
        require(byKey.keys == (0 until Deck.size * 2).toSet())
    }

    fun entry(cardId: Int, reversed: Boolean): FortuneEntry = byKey.getValue(key(cardId, reversed))

    fun name(cardId: Int): String = entry(cardId, false).name

    private fun key(cardId: Int, reversed: Boolean) = cardId * 2 + if (reversed) 1 else 0

    companion object {
        const val KOREAN = "ko"
        const val ENGLISH = "en"

        fun asset(language: String) = "default_fortunes.$language.json"

        /**
         * The language the app's strings resolved to: Korean where `values-ko` applies, English
         * otherwise, so fortunes never disagree with the screen around them.
         */
        fun language(context: Context): String = context.getString(R.string.content_language)

        private val loaded = HashMap<String, FortuneCatalog>()

        /** The catalog in the language of [context]; pass the Activity, or [withAppLanguage] in the background. */
        fun load(context: Context): FortuneCatalog {
            val language = language(context)
            return synchronized(loaded) {
                loaded.getOrPut(language) {
                    parse(context.assets.open(asset(language)).bufferedReader(Charsets.UTF_8).use { it.readText() })
                }
            }
        }

        fun parse(text: String): FortuneCatalog {
            val root = Json.parse(text) as Map<*, *>
            require(root["schemaVersion"] == 2L) { "Unsupported catalog schema" }
            val entries = (root["entries"] as List<*>).map { raw ->
                val item = raw as Map<*, *>
                val cardId = (item["cardId"] as Long).toInt()
                require(Deck.getOrNull(cardId)?.name == item["cardName"]) { "Card $cardId name mismatch" }
                FortuneEntry(
                    cardId = cardId,
                    reversed = item["reversed"] as Boolean,
                    name = item["name"] as String,
                    keywordsText = item["keywordsText"] as String,
                    fortuneText = item["fortuneText"] as String,
                )
            }
            return FortuneCatalog(root["catalogVersion"] as String, entries)
        }
    }
}
