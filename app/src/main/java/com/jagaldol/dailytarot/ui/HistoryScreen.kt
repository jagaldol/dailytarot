package com.jagaldol.dailytarot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.data.FortuneEntry
import com.jagaldol.dailytarot.model.DailyReading
import com.jagaldol.dailytarot.model.ReadingSource
import com.jagaldol.dailytarot.ui.theme.gold

@Composable
fun HistoryScreen(
    readings: List<DailyReading>,
    cardName: (Int) -> String,
    onOpen: (DailyReading) -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = readings.groupBy { it.day.year to it.day.month }
    LazyColumn(
        modifier.fillMaxSize().testTag("history"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
    ) {
        item(key = "header") {
            Column(Modifier.statusBarsPadding().padding(top = 28.dp, bottom = 8.dp)) {
                Text(
                    stringResource(R.string.history_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    pluralStringResource(R.plurals.history_count, readings.size, readings.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (readings.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                )
            }
        }
        months.forEach { (month, items) ->
            item(key = "month-${month.first}-${month.second}", contentType = "month") {
                SectionLabel(
                    monthLabel(LocalResources.current, month.first, month.second),
                    Modifier.padding(top = 28.dp, bottom = 6.dp),
                )
            }
            items(items, key = { it.day.toString() }, contentType = { "reading" }) { reading ->
                HistoryRow(reading, cardName(reading.cardId), onOpen)
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun HistoryRow(reading: DailyReading, name: String, onOpen: (DailyReading) -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(reading) }
                .padding(vertical = 12.dp)
                .testTag("history-${reading.day}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    reading.day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    reading.day.weekdayShort(LocalResources.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            CardFace(reading.cardId, reading.reversed, Modifier.width(38.dp), corner = 5.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        orientationLabel(reading.reversed),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (reading.reversed) MaterialTheme.colorScheme.gold
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (reading.source == ReadingSource.LIFEBASE) {
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .padding(start = 8.dp)
                                .width(5.dp)
                                .height(5.dp)
                                .background(MaterialTheme.colorScheme.gold, androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    reading.headline ?: reading.keywordsText.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .padding(start = 108.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** [onDelete] is null while Lifebase is linked: the journal would bring the record back. */
@Composable
fun ReadingDetailScreen(
    reading: DailyReading,
    name: String,
    defaultFortune: FortuneEntry?,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackBar(
                reading.day.fullLabel(LocalResources.current), onBack,
                Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (onDelete != null) {
                IconButton(onClick = { confirming = true }, modifier = Modifier.padding(end = 8.dp).testTag("delete")) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (confirming && onDelete != null) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(stringResource(R.string.detail_delete_title)) },
                text = { Text(stringResource(R.string.detail_delete_body)) },
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
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(20.dp))
            Box(contentAlignment = Alignment.Center) {
                Glow(Modifier.matchParentSize())
                CardFace(reading.cardId, reading.reversed, Modifier.width(180.dp), full = true, elevation = 14.dp)
            }
            Spacer(Modifier.height(32.dp))
            ReadingText(reading, name, defaultFortune, waiting = false)
            Spacer(Modifier.height(40.dp))
        }
    }
}
