package com.jagaldol.dailytarot.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jagaldol.dailytarot.TarotApplication
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The widget's captions are rendered here, so a new device or per-app language (Android 13+
 * delivers both as LOCALE_CHANGED) redraws it even when the app itself is not open.
 */
class LocaleChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCALE_CHANGED) return
        val app = context.applicationContext as TarotApplication
        app.languageChanges.update { it + 1 }
        val pending = goAsync()
        app.applicationScope.launch {
            try {
                runCatching { WidgetRefreshWorker.enqueue(app) }
            } finally {
                pending.finish()
            }
        }
    }
}
