package com.jagaldol.dailytarot.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jagaldol.dailytarot.model.TodaySelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tarot_prefs")
private val cardIdKey = intPreferencesKey("today_card_id")
private val reversedKey = booleanPreferencesKey("today_reversed")

interface SelectionStore {
    suspend fun load(): TodaySelection
    suspend fun save(selection: TodaySelection)
}

class TarotStore(context: Context) : SelectionStore {
    private val store = context.applicationContext.dataStore

    val selections: Flow<TodaySelection> = store.data.map { prefs ->
        TodaySelection(
            cardId = prefs[cardIdKey]?.takeIf { it in 0..77 },
            reversed = prefs[reversedKey] ?: false,
        )
    }

    override suspend fun load(): TodaySelection = selections.first()

    override suspend fun save(selection: TodaySelection) {
        store.edit { prefs ->
            selection.cardId?.let { prefs[cardIdKey] = it } ?: prefs.remove(cardIdKey)
            prefs[reversedKey] = selection.reversed
        }
    }
}
