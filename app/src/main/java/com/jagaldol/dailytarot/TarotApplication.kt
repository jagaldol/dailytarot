package com.jagaldol.dailytarot

import android.app.Application
import com.jagaldol.dailytarot.data.FortuneCatalog
import com.jagaldol.dailytarot.data.ReadingRepository
import com.jagaldol.dailytarot.data.SettingsStore
import com.jagaldol.dailytarot.data.SqliteReadingStore
import com.jagaldol.dailytarot.data.lifebase.LifebaseSync
import com.jagaldol.dailytarot.widget.WidgetRefreshWorker
import com.jagaldol.dailytarot.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TarotApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val catalog by lazy { FortuneCatalog.load(this) }
    val settings by lazy { SettingsStore(this) }
    val repository by lazy {
        ReadingRepository(SqliteReadingStore(this), settings, catalog) {
            WidgetRefreshWorker.enqueue(this)
        }
    }
    val sync by lazy { LifebaseSync(this, repository, settings) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            catalog
            runCatching { RefreshScheduler.reconcile(this@TarotApplication) }
        }
    }
}
