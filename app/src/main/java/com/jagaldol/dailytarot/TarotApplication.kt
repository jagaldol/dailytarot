package com.jagaldol.dailytarot

import android.app.Application
import com.jagaldol.dailytarot.data.TarotRepository
import com.jagaldol.dailytarot.data.TarotStore
import com.jagaldol.dailytarot.widget.WidgetRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TarotApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository by lazy {
        TarotRepository(TarotStore(this), applicationScope) {
            WidgetRefreshWorker.enqueue(this)
        }
    }
}
