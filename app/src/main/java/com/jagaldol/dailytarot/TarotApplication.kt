package com.jagaldol.dailytarot

import android.app.Application
import com.jagaldol.dailytarot.data.FortuneCatalog
import com.jagaldol.dailytarot.data.ReadingRepository
import com.jagaldol.dailytarot.data.SettingsStore
import com.jagaldol.dailytarot.data.SqliteReadingStore
import com.jagaldol.dailytarot.data.withAppLanguage
import com.jagaldol.dailytarot.data.lifebase.LifebaseSync
import com.jagaldol.dailytarot.widget.WidgetRefreshWorker
import com.jagaldol.dailytarot.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class TarotApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings by lazy { SettingsStore(this) }
    val repository by lazy {
        // Draws can run from the widget or scheduled work, so they ask for the language afresh.
        ReadingRepository(SqliteReadingStore(this), settings, { FortuneCatalog.load(withAppLanguage()) }) {
            WidgetRefreshWorker.enqueue(this)
        }
    }
    val sync by lazy { LifebaseSync(this, repository, settings) }

    /** Bumped when the first widget is added or the last one removed, so the UI re-checks. */
    val widgetChanges = MutableStateFlow(0)

    /** Bumped on a device or per-app language change, so a running widget session recomposes. */
    val languageChanges = MutableStateFlow(0)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { FortuneCatalog.load(withAppLanguage()) }
            runCatching { RefreshScheduler.reconcile(this@TarotApplication) }
        }
    }
}
