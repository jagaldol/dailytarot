package com.jagaldol.dailytarot.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jagaldol.dailytarot.OpenRequest
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.TarotApplication
import com.jagaldol.dailytarot.data.AppSettings
import com.jagaldol.dailytarot.data.BackupCodec
import com.jagaldol.dailytarot.data.TodayView
import com.jagaldol.dailytarot.data.lifebase.LifebaseSync
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.ReadingSource
import com.jagaldol.dailytarot.ui.theme.gold
import com.jagaldol.dailytarot.widget.HomeWidgets
import com.jagaldol.dailytarot.work.RefreshScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.TimeZone

enum class Tab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    TODAY(R.string.tab_today, R.drawable.ic_nav_today),
    HISTORY(R.string.tab_history, R.drawable.ic_nav_history),
    SETTINGS(R.string.tab_settings, R.drawable.ic_nav_settings),
}

private const val MAX_BACKUP_BYTES = 32 shl 20

@Composable
fun TarotApp(app: TarotApplication, openRequest: OpenRequest? = null, introKey: Int = 0) {
    val repository = app.repository
    val scope = app.applicationScope
    val view by repository.todayView.collectAsState(null)
    // The picker starts from today's own card, never from yesterday's that is still on display.
    val todayReading = view?.reading?.takeUnless { view?.showingPrevious == true }
    val settings by app.settings.state.collectAsState(null)
    val history by repository.history.collectAsState(emptyList())
    val importProgress by remember { RefreshScheduler.importProgress(app) }.collectAsState(null)

    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var detailDay by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    // Each launch (and each return after a long absence, see introKey) opens on the face-down
    // card and turns it over once; tab switches and rotation do not replay it.
    var introDone by rememberSaveable(introKey) { mutableStateOf(false) }
    LaunchedEffect(introKey, view?.reading != null) {
        if (view?.reading != null && !introDone) {
            delay(INTRO_DELAY_MS)
            introDone = true
        }
    }
    // A widget tap lands on Today; a face-down standalone widget also draws, so the flip plays here.
    LaunchedEffect(openRequest?.id) {
        val request = openRequest ?: return@LaunchedEffect
        tab = Tab.TODAY
        picking = false
        detailDay = null
        if (request.draw) {
            delay(INTRO_DELAY_MS)
            val today = repository.refreshToday()
            if (today.awaitingDraw && app.settings.connection() == null) {
                val drawn = repository.drawToday()
                app.settings.markRevealed(drawn.day.toString())
                RefreshScheduler.reconcile(app)
            }
        }
    }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    // Whether a widget is on the home screen; re-checked on each start and when widgets come or go.
    val widgetSignal by app.widgetChanges.collectAsState()
    var hasWidget by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(widgetSignal) { hasWidget = runCatching { HomeWidgets.installed(app) }.getOrNull() }
    var widgetHelp by rememberSaveable { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // The launcher's own "add to home screen" sheet; launchers without it get written steps.
    val addWidget: () -> Unit = {
        uiScope.launch { if (!runCatching { HomeWidgets.requestPin(app) }.getOrDefault(false)) widgetHelp = true }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = when (app.sync.connect(uri)) {
                is LifebaseSync.ConnectResult.Connected -> {
                    RefreshScheduler.reconcile(app)
                    RefreshScheduler.refreshNow(app)
                    RefreshScheduler.startImport(app)
                    null
                }
                LifebaseSync.ConnectResult.JournalNotFound -> app.getString(R.string.settings_journal_not_found)
                LifebaseSync.ConnectResult.PermissionDenied -> app.getString(R.string.settings_permission_denied)
            }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val readings = repository.all()
            message = runCatching {
                app.contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(BackupCodec.encode(readings, System.currentTimeMillis()).toByteArray(Charsets.UTF_8))
                }
                app.getString(R.string.settings_exported, readings.size)
            }.getOrElse { it.message }
        }
    }
    val restoreFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                val bytes = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                require(bytes.size <= MAX_BACKUP_BYTES)
                val added = repository.restore(BackupCodec.decode(String(bytes, Charsets.UTF_8)))
                app.getString(R.string.settings_restored, added)
            }.getOrElse { app.getString(R.string.settings_restore_failed) }
        }
    }

    BackHandler(enabled = picking || detailDay != null || tab != Tab.TODAY) {
        when {
            picking -> picking = false
            detailDay != null -> detailDay = null
            else -> tab = Tab.TODAY
        }
    }

    val current = settings ?: run {
        // Wait for settings so an already revealed card does not replay its flip.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val nameKo: (Int) -> String = { app.catalog.nameKo(it) }

    // Asked after today's card has been turned over and read, never on launch: at most once per
    // card day while no widget exists, until "다시 보지 않기".
    val todayState = todayUi(app, view, current, introDone)
    val widgetAsk = tab == Tab.TODAY && !picking && detailDay == null &&
        shouldAskForWidget(hasWidget, current, todayState)
    var widgetSheet by rememberSaveable { mutableStateOf(false) }
    val answerWidget: (Boolean) -> Unit = { never ->
        val day = todayState.day?.toString()
        if (day != null) scope.launch { app.settings.answerWidgetPrompt(day, never) }
    }
    LaunchedEffect(widgetAsk) {
        if (widgetAsk) {
            delay(WIDGET_ASK_DELAY_MS)
            widgetSheet = true
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                val screen = when {
                    picking -> "picker"
                    detailDay != null -> "detail"
                    else -> tab.name
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                    modifier = Modifier.weight(1f),
                    label = "screen",
                ) { target ->
                    when (target) {
                        "picker" -> PickerScreen(
                            initialCardId = todayReading?.cardId,
                            initialReversed = todayReading?.reversed == true,
                            nameKo = nameKo,
                            onConfirm = { cardId, reversed ->
                                picking = false
                                scope.launch {
                                    if (repository.selectManually(cardId, reversed)) {
                                        app.settings.markRevealed(repository.currentDay().toString())
                                    }
                                }
                            },
                            onBack = { picking = false },
                        )
                        "detail" -> {
                            val item = history.firstOrNull { it.day.toString() == detailDay }
                            if (item == null) {
                                Box(Modifier.fillMaxSize())
                            } else {
                                ReadingDetailScreen(
                                    item, nameKo(item.cardId),
                                    repository.defaultFortune(item.cardId, item.reversed),
                                    onBack = { detailDay = null },
                                    // Imported records come back from the journal while linked.
                                    onDelete = if (current.connection != null && item.source == ReadingSource.LIFEBASE) null else {
                                        {
                                            detailDay = null
                                            scope.launch {
                                                if (repository.delete(item.day)) {
                                                    app.settings.forgetRevealed(item.day.toString())
                                                    repository.refreshToday()
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        Tab.HISTORY.name -> HistoryScreen(history, nameKo, onOpen = { detailDay = it.day.toString() })
                        Tab.SETTINGS.name -> SettingsScreen(
                            settings = current,
                            deviceZoneId = TimeZone.getDefault().id,
                            importProgress = importProgress,
                            message = message,
                            actions = settingsActions(
                                app, pickFolder::launch, exportFile::launch, restoreFile::launch, addWidget,
                            ) {
                                message = it
                            },
                            hasWidget = hasWidget,
                            // While linked only the app's own records can go.
                            recordCount = if (current.connection != null) {
                                history.count { it.source != ReadingSource.LIFEBASE }
                            } else {
                                history.size
                            },
                        )
                        else -> TodayScreen(
                            ui = todayState,
                            onReveal = { todayReading?.let { r -> scope.launch { app.settings.markRevealed(r.day.toString()) } } },
                            onDraw = {
                                scope.launch {
                                    val drawn = repository.drawToday()
                                    app.settings.markRevealed(drawn.day.toString())
                                    RefreshScheduler.reconcile(app)
                                }
                            },
                            onPick = { picking = true },
                            onRedraw = { scope.launch { repository.redrawLegacy() } },
                        )
                    }
                }
                if (!picking && detailDay == null) {
                    NavBar(tab) {
                        tab = it
                        message = null
                    }
                }
            }
            if (widgetSheet) {
                WidgetSheet(
                    onAdd = { never ->
                        widgetSheet = false
                        answerWidget(never)
                        addWidget()
                    },
                    onDecline = { never ->
                        widgetSheet = false
                        answerWidget(never)
                        uiScope.launch { snackbar.showSnackbar(app.getString(R.string.widget_prompt_later)) }
                    },
                )
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 76.dp),
            ) { data ->
                Snackbar(
                    data,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
            if (widgetHelp) {
                AlertDialog(
                    onDismissRequest = { widgetHelp = false },
                    title = { Text(stringResource(R.string.widget_manual_title)) },
                    text = { Text(stringResource(R.string.widget_manual_body)) },
                    confirmButton = {
                        TextButton(onClick = { widgetHelp = false }) { Text(stringResource(R.string.ok)) }
                    },
                )
            }
            // Scrolled content fades under the transparent status bar instead of colliding with it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
            )
        }
    }
}

private const val INTRO_DELAY_MS = 450L

/**
 * The home screen widget is suggested once today's own card is face up, while no widget exists,
 * at most once per card day, and never after "다시 보지 않기".
 */
internal fun shouldAskForWidget(hasWidget: Boolean?, settings: AppSettings, today: TodayUi): Boolean =
    hasWidget == false && !settings.widgetPromptNever &&
        today.revealed && !today.showingPrevious && today.reading != null &&
        today.day != null && settings.widgetPromptAskedDay != today.day.toString()

// Lets the card turn and the fortune fade in before the widget question slides up.
private const val WIDGET_ASK_DELAY_MS = 1_600L

private fun todayUi(
    app: TarotApplication,
    view: TodayView?,
    settings: AppSettings,
    introDone: Boolean,
): TodayUi {
    val reading = view?.reading
    val previous = view?.showingPrevious == true
    val connected = settings.connection != null
    val revealed = introDone && reading != null && (previous || settings.revealedDay == reading.day.toString())
    return TodayUi(
        day = view?.day,
        reading = reading,
        nameKo = reading?.let { app.catalog.nameKo(it.cardId) },
        defaultFortune = reading?.let { app.catalog.entry(it.cardId, it.reversed) },
        // Yesterday's card was already seen; only today's waits to be turned over by a tap.
        // Seen cards still start face-down on each launch and flip once the intro runs.
        revealed = revealed,
        introPending = !introDone && reading != null && (previous || settings.revealedDay == reading.day.toString()),
        connected = connected,
        waitingForLifebase = connected && reading != null && !previous && reading.source != ReadingSource.LIFEBASE,
        showingPrevious = previous,
        awaitingDraw = view?.awaitingDraw == true,
        dayStartLabel = minutesLabel(settings.dayStartMinutes),
        autoDrawLabel = if (settings.autoDraw) minutesLabel(settings.autoDrawMinutes) else null,
    )
}

private fun settingsActions(
    app: TarotApplication,
    pickFolder: (Uri?) -> Unit,
    export: (String) -> Unit,
    restore: (Array<String>) -> Unit,
    addWidget: () -> Unit,
    setMessage: (String?) -> Unit,
) = SettingsActions(
    addWidget = addWidget,
    connect = {
        setMessage(null)
        // Opens the picker near the usual Syncthing location; any folder can still be chosen.
        pickFolder(DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents"))
    },
    disconnect = {
        app.applicationScope.launch {
            RefreshScheduler.cancelImport(app)
            app.sync.disconnect()
            RefreshScheduler.reconcile(app)
        }
    },
    checkNow = { RefreshScheduler.refreshNow(app) },
    startImport = { RefreshScheduler.startImport(app) },
    stopImport = { RefreshScheduler.cancelImport(app) },
    useDeviceZone = {
        app.applicationScope.launch {
            app.settings.setZoneId(TimeZone.getDefault().id)
            app.repository.refreshToday()
            RefreshScheduler.reconcile(app)
        }
    },
    setAutoDraw = { enabled ->
        app.applicationScope.launch {
            app.settings.setAutoDraw(enabled)
            app.repository.refreshToday()
            RefreshScheduler.reconcile(app)
        }
    },
    setDayStartMinutes = { minutes ->
        app.applicationScope.launch {
            app.settings.setDayStartMinutes(minutes)
            app.repository.refreshToday()
            RefreshScheduler.reconcile(app)
        }
    },
    setAutoDrawMinutes = { minutes ->
        app.applicationScope.launch {
            app.settings.setAutoDrawMinutes(minutes)
            app.repository.refreshToday()
            RefreshScheduler.reconcile(app)
        }
    },
    deleteAll = {
        app.applicationScope.launch {
            val linked = app.settings.connection() != null
            val count = app.repository.deleteAll()
            // If today's card went too, its replacement (auto-drawn or not) waits face-down again.
            val today = app.repository.currentDay()
            if (app.repository.observe(today).first() == null) app.settings.forgetRevealed(today.toString())
            app.repository.refreshToday()
            setMessage(
                app.getString(if (linked) R.string.settings_deleted_local else R.string.settings_deleted_all, count),
            )
        }
    },
    export = { export("daily-tarot-${Day.of(System.currentTimeMillis(), TimeZone.getDefault())}.json") },
    restore = { restore(arrayOf("application/json", "text/plain", "application/octet-stream")) },
)

@Composable
private fun NavBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .selectable(selected, role = Role.Tab) { onSelect(tab) }
                    .testTag("tab-${tab.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall, color = color)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.gold else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}
