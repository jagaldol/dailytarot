package com.jagaldol.dailytarot.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition

suspend fun syncAllWidgetState(context: Context, id: Int, name: String, reversed: Boolean) {
    val manager = GlanceAppWidgetManager(context)
    val ids = manager.getGlanceIds(DailyTarotWidget::class.java)
    ids.forEach { glanceId ->
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
            val m = p.toMutablePreferences()
            m[intPreferencesKey("w_id")] = id       // ← 추가
            m[stringPreferencesKey("w_name")] = name
            m[booleanPreferencesKey("w_reversed")] = reversed
            m
        }
    }
    DailyTarotWidget().updateAll(context)
}