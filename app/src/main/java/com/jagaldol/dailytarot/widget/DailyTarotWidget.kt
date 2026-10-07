package com.jagaldol.dailytarot.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import com.jagaldol.dailytarot.MainActivity
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.data.CardImages
import com.jagaldol.dailytarot.data.TarotStore
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.model.TodaySelection

class DailyTarotWidget : GlanceAppWidget() {
    // The selected card is application data, not per-widget state.
    override val stateDefinition = null
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = TarotStore(context)
        val initial = store.load()
        val initialImage = initial to render(context, initial)
        provideContent {
            val selection by store.selections.collectAsState(initial)
            val image by produceState(initialImage, selection) {
                value = selection to render(context, selection)
            }
            Box(
                modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
                    .clickable(actionStartActivity(MainActivity::class.java)),
                contentAlignment = Alignment.Center,
            ) {
                val (renderedSelection, bitmap) = image
                if (bitmap == null) {
                    Text(context.getString(R.string.widget_empty))
                } else {
                    val card = Deck.getOrNull(renderedSelection.cardId ?: -1)
                    val orientation = context.getString(
                        if (renderedSelection.reversed) R.string.reversed else R.string.upright,
                    )
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = "${card?.name}, $orientation",
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }

    private suspend fun render(context: Context, selection: TodaySelection): Bitmap? {
        val card = Deck.getOrNull(selection.cardId ?: -1) ?: return null
        return CardImages.widget(context.resources, card.imageRes, selection.reversed)
    }
}

class DailyTarotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyTarotWidget()
}
