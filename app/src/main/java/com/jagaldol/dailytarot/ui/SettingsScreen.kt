package com.jagaldol.dailytarot.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.data.AppSettings
import com.jagaldol.dailytarot.data.LifebaseConnection
import com.jagaldol.dailytarot.data.SyncState
import com.jagaldol.dailytarot.data.SyncStatus
import com.jagaldol.dailytarot.ui.theme.DailytarotTheme
import com.jagaldol.dailytarot.ui.theme.gold
import com.jagaldol.dailytarot.work.ImportProgress
import java.util.Calendar

data class SettingsActions(
    val connect: () -> Unit,
    val disconnect: () -> Unit,
    val checkNow: () -> Unit,
    val startImport: () -> Unit,
    val stopImport: () -> Unit,
    val useDeviceZone: () -> Unit,
    val setAutoDraw: (Boolean) -> Unit,
    val setDayStartMinutes: (Int) -> Unit,
    val setAutoDrawMinutes: (Int) -> Unit,
    val deleteAll: () -> Unit,
    val addWidget: () -> Unit,
    val export: () -> Unit,
    val restore: () -> Unit,
)

// The Lifebase card shares the card back's night palette in both themes.
private val Ink = Brush.linearGradient(listOf(Color(0xFF332F58), Color(0xFF1B1931)))
private val InkText = Color(0xFFF3EEE4)
private val InkMuted = Color(0xFFB7B0C9)
private val InkGold = Color(0xFFE2C68E)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    deviceZoneId: String,
    importProgress: ImportProgress?,
    message: String?,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    recordCount: Int = 0,
    /** Null until checked. */
    hasWidget: Boolean? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        message?.let {
            Notice(it)
            Spacer(Modifier.height(12.dp))
        }

        LifebaseCard(settings, importProgress, actions)

        val connection = settings.connection
        if (connection != null) {
            Group(stringResource(R.string.settings_group_sync)) {
                SettingRow(
                    R.drawable.ic_refresh, stringResource(R.string.settings_check_now),
                    stringResource(R.string.settings_check_now_sub), onClick = actions.checkNow,
                )
                Divider()
                SettingRow(
                    R.drawable.ic_nav_history, stringResource(R.string.settings_import),
                    importSubtitle(importProgress),
                    enabled = importProgress?.running != true, onClick = actions.startImport,
                )
                Divider()
                SettingRow(
                    R.drawable.ic_folder, stringResource(R.string.settings_reconnect),
                    stringResource(R.string.settings_reconnect_sub), onClick = actions.connect,
                )
                Divider()
                SettingRow(
                    R.drawable.ic_unlink, stringResource(R.string.settings_disconnect),
                    stringResource(R.string.settings_disconnect_sub),
                    tint = MaterialTheme.colorScheme.error, onClick = actions.disconnect,
                    modifier = Modifier.testTag("disconnect"),
                )
            }
        }

        CardGroup(settings, actions)

        Group(stringResource(R.string.settings_group_widget)) {
            SettingRow(
                R.drawable.ic_widget, stringResource(R.string.settings_widget_add),
                stringResource(
                    if (hasWidget == true) R.string.settings_widget_add_sub_installed else R.string.settings_widget_add_sub,
                ),
                onClick = actions.addWidget,
                modifier = Modifier.testTag("add-widget"),
            )
        }

        Group(stringResource(R.string.settings_group_records)) {
            SettingRow(
                R.drawable.ic_clock, stringResource(R.string.settings_timezone),
                stringResource(R.string.settings_timezone_sub),
                trailing = settings.zoneId.orEmpty(),
            )
            if (settings.zoneId != null && settings.zoneId != deviceZoneId) {
                Divider()
                SettingRow(
                    R.drawable.ic_clock, stringResource(R.string.settings_timezone_use_device),
                    deviceZoneId, tint = MaterialTheme.colorScheme.gold, onClick = actions.useDeviceZone,
                )
            }
            Divider()
            SettingRow(
                R.drawable.ic_export, stringResource(R.string.settings_export),
                stringResource(R.string.settings_export_sub), onClick = actions.export,
            )
            Divider()
            SettingRow(
                R.drawable.ic_restore, stringResource(R.string.settings_restore),
                stringResource(R.string.settings_restore_sub), onClick = actions.restore,
            )
            Divider()
            DeleteAllRow(settings.connection != null, recordCount, actions.deleteAll)
        }

        Footer()
    }
}

@Composable
private fun LifebaseCard(settings: AppSettings, progress: ImportProgress?, actions: SettingsActions) {
    val connection = settings.connection
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Ink)
            .border(BorderStroke(1.dp, InkGold.copy(alpha = 0.22f)), RoundedCornerShape(26.dp))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(InkGold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_moon), null, tint = InkGold, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "LIFEBASE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing),
                    color = InkGold,
                )
                Text(
                    stringResource(if (connection == null) R.string.settings_hero_off else R.string.settings_hero_on),
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkText,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (connection == null) {
            Text(
                stringResource(R.string.settings_lifebase_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(InkGold)
                    .clickable(role = Role.Button, onClick = actions.connect)
                    .padding(horizontal = 22.dp, vertical = 12.dp)
                    .testTag("connect"),
            ) {
                Text(
                    stringResource(R.string.settings_connect),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF231B0C),
                )
            }
        } else {
            StatusLine(settings.sync)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_folder), null, tint = InkMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                // Only the chosen folder's name; the vault layout inside it is not the user's concern.
                Text(
                    connection.label.substringBefore('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            if (progress?.running == true) {
                Spacer(Modifier.height(18.dp))
                val fraction = if (progress.monthsTotal > 0) progress.monthsDone.toFloat() / progress.monthsTotal else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    color = InkGold,
                    trackColor = InkText.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        progress.label?.let { stringResource(R.string.settings_import_progress, it, progress.imported) }
                            ?: stringResource(R.string.settings_import_starting),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.settings_import_stop),
                        style = MaterialTheme.typography.labelLarge,
                        color = InkGold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = actions.stopImport)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(sync: SyncStatus) {
    val (text, color) = when (sync.state) {
        SyncState.NEVER -> stringResource(R.string.sync_never) to InkMuted
        SyncState.UPDATED -> stringResource(R.string.sync_updated) to Color(0xFF9FD3A8)
        SyncState.WAITING -> stringResource(R.string.sync_waiting) to InkGold
        SyncState.ERROR -> stringResource(R.string.sync_error) to Color(0xFFF0A196)
        SyncState.PERMISSION_LOST -> stringResource(R.string.sync_permission_lost) to Color(0xFFF0A196)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = InkText, modifier = Modifier.testTag("sync-state"))
    }
    // Parser reasons stay internal; the user sees the state and when it was checked.
    sync.checkedAt.takeIf { it > 0 }?.let {
        Text(
            stringResource(R.string.sync_checked_at, timeLabel(it)),
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            modifier = Modifier.padding(start = 15.dp, top = 2.dp),
        )
    }
}

/** "오후 11:02", or "10월 6일 오후 11:02" for earlier days. */
private fun timeLabel(millis: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    val clock = minutesLabel(then.get(Calendar.HOUR_OF_DAY) * 60 + then.get(Calendar.MINUTE))
    val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) clock else "${then.get(Calendar.MONTH) + 1}월 ${then.get(Calendar.DAY_OF_MONTH)}일 $clock"
}

private enum class TimeTarget { DAY_START, AUTO_DRAW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardGroup(settings: AppSettings, actions: SettingsActions) {
    var picking by rememberSaveable { mutableStateOf<TimeTarget?>(null) }
    Group(stringResource(R.string.settings_group_card)) {
        SettingRow(
            R.drawable.ic_clock, stringResource(R.string.settings_day_start),
            stringResource(R.string.settings_day_start_sub),
            trailing = minutesLabel(settings.dayStartMinutes),
            onClick = { picking = TimeTarget.DAY_START },
        )
        Divider()
        SettingRow(
            R.drawable.ic_nav_today, stringResource(R.string.settings_auto_draw),
            when {
                settings.autoDraw -> stringResource(R.string.settings_auto_draw_on)
                settings.connection != null -> stringResource(R.string.settings_auto_draw_off_linked)
                else -> stringResource(R.string.settings_auto_draw_off)
            },
            onClick = { actions.setAutoDraw(!settings.autoDraw) },
            modifier = Modifier.testTag("auto-draw"),
            switch = settings.autoDraw,
        )
        Divider()
        SettingRow(
            R.drawable.ic_clock, stringResource(R.string.settings_auto_draw_time),
            stringResource(R.string.settings_auto_draw_time_sub),
            trailing = minutesLabel(settings.autoDrawMinutes),
            enabled = settings.autoDraw,
            onClick = { picking = TimeTarget.AUTO_DRAW },
        )
    }
    picking?.let { target ->
        val initial = if (target == TimeTarget.DAY_START) settings.dayStartMinutes else settings.autoDrawMinutes
        val state = rememberTimePickerState(initial / 60, initial % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = null },
            title = {
                Text(
                    stringResource(
                        if (target == TimeTarget.DAY_START) R.string.settings_day_start else R.string.settings_auto_draw_time,
                    ),
                )
            },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    picking = null
                    val minutes = state.hour * 60 + state.minute
                    if (target == TimeTarget.DAY_START) actions.setDayStartMinutes(minutes)
                    else actions.setAutoDrawMinutes(minutes)
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DeleteAllRow(connected: Boolean, count: Int, onDelete: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingRow(
        R.drawable.ic_delete,
        stringResource(if (connected) R.string.settings_delete_local else R.string.settings_delete_all),
        stringResource(if (connected) R.string.settings_delete_local_sub else R.string.settings_delete_all_sub),
        tint = MaterialTheme.colorScheme.error,
        enabled = count > 0,
        onClick = { confirming = true },
        modifier = Modifier.testTag("delete-all"),
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = {
                Text(
                    stringResource(
                        if (connected) R.string.settings_delete_local_title else R.string.settings_delete_all_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (connected) R.string.settings_delete_local_body else R.string.settings_delete_all_body,
                        count,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun importSubtitle(progress: ImportProgress?): String = when {
    progress?.running == true -> stringResource(R.string.settings_import_running)
    progress?.finished == true -> stringResource(R.string.settings_import_done, progress.imported)
    else -> stringResource(R.string.settings_import_sub)
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(30.dp))
    SectionLabel(title, Modifier.padding(start = 6.dp, bottom = 10.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = content,
    )
}

@Composable
private fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    switch: Boolean? = null,
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint.copy(alpha = alpha))
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        switch?.let { checked ->
            Spacer(Modifier.width(12.dp))
            // The whole row toggles; the switch only shows the state.
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.gold,
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(start = 68.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("settings-message"),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun Footer() {
    Column(
        Modifier.fillMaxWidth().padding(top = 44.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ornament()
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.wordmark),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_about),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(widthDp = 380, heightDp = 1100)
@Composable
private fun SettingsPreview() {
    DailytarotTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(
                AppSettings(
                    zoneId = "Asia/Seoul",
                    connection = LifebaseConnection("", "", "lifebase/Journal", "id"),
                    sync = SyncStatus(SyncState.UPDATED, System.currentTimeMillis()),
                ),
                "Asia/Seoul", null, null,
                SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
            )
        }
    }
}
