package com.jagaldol.dailytarot.work

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jagaldol.dailytarot.TarotApplication
import com.jagaldol.dailytarot.data.lifebase.LifebaseSync
import com.jagaldol.dailytarot.widget.DailyTarotWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Checks Lifebase (when linked), makes sure today's reading exists, then picks the next run. */
class DailyRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TarotApplication
        val mode = if (inputData.getBoolean(FULL, false)) LifebaseSync.Mode.FULL else LifebaseSync.Mode.BACKGROUND
        return try {
            if (app.settings.connection() != null) app.sync.syncRecent(mode)
            // Local documents only: no network constraint. Draws only if automatic drawing is on.
            app.repository.refreshToday()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        } finally {
            // Outside this worker: rescheduling may cancel the periodic work that is running it.
            app.applicationScope.launch { runCatching { RefreshScheduler.reconcile(app) } }
        }
    }

    companion object {
        const val FULL = "full"
    }
}

class LifebaseImportWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TarotApplication
        return try {
            val imported = app.sync.importHistory(isStopped = { isStopped }) { done, total, count, label ->
                setProgress(workDataOf(DONE to done, TOTAL to total, IMPORTED to count, LABEL to label))
            }
            Result.success(workDataOf(IMPORTED to imported))
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            Result.failure()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val DONE = "done"
        const val TOTAL = "total"
        const val IMPORTED = "imported"
        const val LABEL = "label"
    }
}

data class ImportProgress(
    val running: Boolean,
    val monthsDone: Int,
    val monthsTotal: Int,
    val imported: Int,
    val label: String?,
    val finished: Boolean,
)

object RefreshScheduler {
    private const val PERIODIC = "daily-tarot-periodic"
    private const val NEXT_DAY = "daily-tarot-next-day"
    private const val DAY_START = "daily-tarot-day-start"
    private const val AUTO_DRAW = "daily-tarot-auto-draw"
    private const val NOW = "daily-tarot-refresh-now"
    private const val IMPORT = "lifebase-import"

    /**
     * - Linked and today's fortune not imported yet: check every 15 minutes (WorkManager minimum),
     *   restarted shortly after each midnight.
     * - Any widget: one run at the day start, when today's card (or a face-down one) takes over
     *   from yesterday's, and one at the automatic draw time when that is on.
     * - Neither linked nor any widget: no background work; opening the app is enough.
     */
    suspend fun reconcile(context: Context) {
        val app = context.applicationContext as TarotApplication
        val connected = app.settings.connection() != null
        val hasWidgets = GlanceAppWidgetManager(context).getGlanceIds(DailyTarotWidget::class.java).isNotEmpty()
        val work = WorkManager.getInstance(context)
        val zone = TimeZone.getTimeZone(app.settings.zoneId())
        val now = System.currentTimeMillis()

        if (connected && !app.repository.isFromLifebase(app.repository.currentDay())) {
            work.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                // The first run waits a period: opening the app already ran a full check.
                PeriodicWorkRequestBuilder<DailyRefreshWorker>(15, TimeUnit.MINUTES)
                    .setInitialDelay(15, TimeUnit.MINUTES)
                    .build(),
            )
        } else {
            work.cancelUniqueWork(PERIODIC)
        }
        if (connected) {
            work.enqueueUniqueWork(NEXT_DAY, ExistingWorkPolicy.REPLACE, delayed(millisUntil(now, zone, 1)))
        } else {
            work.cancelUniqueWork(NEXT_DAY)
        }
        if (hasWidgets) {
            val dayStart = app.settings.dayStartMinutes()
            work.enqueueUniqueWork(DAY_START, ExistingWorkPolicy.REPLACE, delayed(millisUntil(now, zone, dayStart)))
        } else {
            work.cancelUniqueWork(DAY_START)
        }
        if (hasWidgets && app.settings.autoDraw()) {
            val autoDraw = app.settings.autoDrawMinutes()
            work.enqueueUniqueWork(AUTO_DRAW, ExistingWorkPolicy.REPLACE, delayed(millisUntil(now, zone, autoDraw)))
        } else {
            work.cancelUniqueWork(AUTO_DRAW)
        }
    }

    private fun delayed(millis: Long) =
        OneTimeWorkRequestBuilder<DailyRefreshWorker>().setInitialDelay(millis, TimeUnit.MILLISECONDS).build()

    /** A full check of the recent week, used when the app opens or the user asks. */
    fun refreshNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailyRefreshWorker>()
                .setInputData(workDataOf(DailyRefreshWorker.FULL to true))
                .build(),
        )
    }

    fun startImport(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMPORT, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<LifebaseImportWorker>().build(),
        )
    }

    fun cancelImport(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(IMPORT)
    }

    fun importProgress(context: Context): Flow<ImportProgress?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(IMPORT).map { infos ->
            val info = infos.lastOrNull() ?: return@map null
            val data = if (info.state.isFinished) info.outputData else info.progress
            ImportProgress(
                running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED,
                monthsDone = data.getInt(LifebaseImportWorker.DONE, 0),
                monthsTotal = data.getInt(LifebaseImportWorker.TOTAL, 0),
                imported = data.getInt(LifebaseImportWorker.IMPORTED, 0),
                label = data.getString(LifebaseImportWorker.LABEL),
                finished = info.state == WorkInfo.State.SUCCEEDED,
            )
        }
}

/** Delay until the next [minuteOfDay] in [zone]: later today, or tomorrow if it has passed. */
internal fun millisUntil(now: Long, zone: TimeZone, minuteOfDay: Int): Long {
    val next = GregorianCalendar(zone, Locale.ROOT).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now) add(Calendar.DAY_OF_MONTH, 1)
    }
    return next.timeInMillis - now
}
