package com.jagaldol.dailytarot.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.jagaldol.dailytarot.data.saveToday
import com.jagaldol.dailytarot.model.Deck
import kotlin.random.Random

class ToggleReverseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
            val key = booleanPreferencesKey("w_reversed")
            val cur = p[key] ?: false
            val m = p.toMutablePreferences()
            m[key] = !cur
            m
        }
        DailyTarotWidget().updateAll(context)
    }
}

class DrawTodayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // 뽑기 결과 정의
        val pick = Deck.random()              // ← 없다고 뜨면 Deck import 확인
        val reversed = Random.nextBoolean()   // ← 랜덤 역/정

        // 영구 저장(DataStore)
        saveToday(context, pick.id, reversed)

        // 위젯 상태 반영
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
            val m = p.toMutablePreferences()
            m[intPreferencesKey("w_id")] = pick.id
            m[stringPreferencesKey("w_name")] = pick.name
            m[booleanPreferencesKey("w_reversed")] = reversed
            m
        }
        DailyTarotWidget().updateAll(context)
    }
}