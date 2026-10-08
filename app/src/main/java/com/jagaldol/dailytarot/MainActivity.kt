package com.jagaldol.dailytarot

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jagaldol.dailytarot.ui.TarotApp
import com.jagaldol.dailytarot.ui.theme.DailytarotTheme
import com.jagaldol.dailytarot.work.RefreshScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a widget tap asks for; [id] makes repeated taps distinct events. */
data class OpenRequest(val id: Long, val draw: Boolean)

class MainActivity : ComponentActivity() {
    private val openRequest = mutableStateOf<OpenRequest?>(null)

    // Bumped when the app returns after a long absence, so the card flips in again like a fresh launch.
    private val introKey = mutableIntStateOf(0)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        backgroundedAt = savedInstanceState?.getLong(STATE_BACKGROUNDED_AT) ?: 0L
        val app = application as TarotApplication
        // Every return to the app re-checks today's journal note when linked.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt >= INTRO_REPLAY_AFTER_MS) {
                    introKey.intValue++
                }
                backgroundedAt = 0L
                app.applicationScope.launch {
                    if (app.settings.connection() != null) RefreshScheduler.refreshNow(app)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                backgroundedAt = SystemClock.elapsedRealtime()
            }
        })
        // While visible, follow the clock so the day start and the automatic draw switch the card in place.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    try {
                        app.repository.refreshToday()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                    delay(60_000 - System.currentTimeMillis() % 60_000)
                }
            }
        }
        if (savedInstanceState == null) handle(intent)
        setContent {
            DailytarotTheme {
                TarotApp(app, openRequest.value, introKey.intValue)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Kept across process death: a restored screen after a long absence still flips in.
        outState.putLong(STATE_BACKGROUNDED_AT, backgroundedAt)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_OPEN_TODAY, false)) return
        openRequest.value = OpenRequest(System.nanoTime(), draw = intent.getBooleanExtra(EXTRA_DRAW, false))
    }

    companion object {
        /** Widget taps open the Today screen; a face-down standalone widget also asks for a draw. */
        const val EXTRA_OPEN_TODAY = "open_today"
        const val EXTRA_DRAW = "draw"

        private const val STATE_BACKGROUNDED_AT = "backgrounded_at"
        private const val INTRO_REPLAY_AFTER_MS = 10 * 60 * 1000L
    }
}
