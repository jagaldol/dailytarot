package com.jagaldol.dailytarot.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "tarot_prefs")

object Keys {
    val CARD_ID = intPreferencesKey("today_card_id")
    val REVERSED = booleanPreferencesKey("today_reversed")
}

suspend fun saveToday(context: Context, cardId: Int, reversed: Boolean) {
    context.dataStore.edit { p ->
        p[Keys.CARD_ID] = cardId
        p[Keys.REVERSED] = reversed
    }
}

suspend fun loadToday(context: Context): Pair<Int?, Boolean?> {
    val p = context.dataStore.data.first()
    return p[Keys.CARD_ID] to p[Keys.REVERSED]
}