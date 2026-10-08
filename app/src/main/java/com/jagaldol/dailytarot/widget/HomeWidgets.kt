package com.jagaldol.dailytarot.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager

/** Home screen widget presence and the launcher's "add widget" flow. */
object HomeWidgets {
    suspend fun installed(context: Context): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(DailyTarotWidget::class.java).isNotEmpty()

    /**
     * Asks the launcher to place a widget (Android 8.0+ on supporting launchers). Returns false
     * when it cannot, so the caller can explain how to add one by hand.
     */
    suspend fun requestPin(context: Context): Boolean =
        GlanceAppWidgetManager(context).requestPinGlanceAppWidget(DailyTarotWidgetReceiver::class.java)
}
