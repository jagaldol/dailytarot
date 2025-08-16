package com.jagaldol.dailytarot.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import com.jagaldol.dailytarot.MainActivity
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.model.CardImageRes
import com.jagaldol.dailytarot.model.Deck

class DailyTarotWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 180.dp), DpSize(300.dp, 300.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val idKey = intPreferencesKey("w_id")
        val cardId = prefs[idKey] ?: -1

        // 이름은 저장값 없으면 fallback
        val name = prefs[stringPreferencesKey("w_name")]
            ?: if (cardId >= 0) Deck.firstOrNull { it.id == cardId }?.name ?: "오늘 카드 선택"
            else "오늘 카드 선택"

        // 이미지 리소스 선택
        val imageRes = if (cardId >= 0) com.jagaldol.dailytarot.model.imageResFor(cardId) else R.mipmap.ic_launcher

        Image(
            provider = ImageProvider(imageRes),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxWidth().height(180.dp)
        )
    }
}

class DailyTarotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyTarotWidget()
}