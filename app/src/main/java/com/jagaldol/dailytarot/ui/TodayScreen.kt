package com.jagaldol.dailytarot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.data.FortuneEntry
import com.jagaldol.dailytarot.model.ContentStatus
import com.jagaldol.dailytarot.model.DailyReading
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.model.ReadingSource
import com.jagaldol.dailytarot.model.longLabelKo
import com.jagaldol.dailytarot.ui.theme.DailytarotTheme
import com.jagaldol.dailytarot.ui.theme.gold

data class TodayUi(
    /** The calendar date today, which may differ from the shown card's date before the draw time. */
    val day: Day?,
    val reading: DailyReading?,
    val nameKo: String?,
    val defaultFortune: FortuneEntry?,
    val revealed: Boolean,
    /** An already seen card waiting for its launch flip: no hint, no tap. */
    val introPending: Boolean = false,
    val connected: Boolean,
    /** Today's card was drawn locally while Lifebase has not delivered one yet. */
    val waitingForLifebase: Boolean,
    /** Before the draw time: yesterday's card stays up until today's exists. */
    val showingPrevious: Boolean,
    /** No card for today yet and none is shown. */
    val awaitingDraw: Boolean,
    val dayStartLabel: String,
    /** Set while automatic drawing is on and will fill today later. */
    val autoDrawLabel: String?,
)

@Composable
fun TodayScreen(
    ui: TodayUi,
    onReveal: () -> Unit,
    onDraw: () -> Unit,
    onPick: () -> Unit,
    onRedraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reading = ui.reading
    var confirmDraw by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            ui.day?.longLabelKo().orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.gold,
            modifier = Modifier.testTag("today-date"),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.wordmark),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(32.dp))

        // Tapping the card reveals today's card; an empty day is drawn by a tap only without
        // Lifebase, so stray taps never put a local card in front of the journal.
        val onCardTap = when {
            ui.introPending -> null
            reading != null && !ui.revealed -> onReveal
            ui.awaitingDraw && !ui.connected -> onDraw
            else -> null
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cardWidth = minOf(maxWidth * 0.62f, 280.dp)
            Box(contentAlignment = Alignment.Center) {
                // Drawn past the card bounds without taking layout space.
                Glow(Modifier.matchParentSize().graphicsLayer { scaleX = 2.2f; scaleY = 1.45f })
                val description = when {
                    reading != null && ui.revealed ->
                        "${ui.nameKo} (${Deck[reading.cardId].name}), ${orientationLabel(reading.reversed)}"
                    reading != null -> stringResource(R.string.today_tap_to_reveal)
                    ui.connected -> stringResource(R.string.today_waiting_lifebase)
                    else -> stringResource(R.string.today_tap_to_draw)
                }
                FlipCard(
                    cardId = reading?.cardId,
                    reversed = reading?.reversed == true,
                    revealed = ui.revealed,
                    onClick = onCardTap,
                    contentDescription = description,
                    modifier = Modifier.width(cardWidth).testTag("today-card"),
                )
            }
        }
        Spacer(Modifier.height(36.dp))

        when {
            ui.awaitingDraw && ui.connected -> WaitingForLifebase(onManualDraw = { confirmDraw = true })
            ui.awaitingDraw -> {
                Hint(stringResource(R.string.today_tap_to_draw))
                ui.autoDrawLabel?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.today_auto_draw_at, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                TextButton(onClick = onPick, modifier = Modifier.testTag("pick")) {
                    Text(stringResource(R.string.today_choose_manually), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            reading != null && !ui.revealed && !ui.introPending -> Hint(stringResource(R.string.today_tap_to_reveal))
        }

        AnimatedVisibility(
            visible = ui.revealed && reading != null,
            enter = fadeIn(tween(700, delayMillis = 450)) +
                slideInVertically(tween(700, delayMillis = 450)) { it / 12 },
        ) {
            if (reading != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ui.showingPrevious) {
                        PreviousBadge(stringResource(R.string.today_previous_card, ui.dayStartLabel))
                        Spacer(Modifier.height(24.dp))
                    }
                    ReadingText(
                        reading = reading,
                        nameKo = ui.nameKo.orEmpty(),
                        defaultFortune = ui.defaultFortune,
                        waiting = ui.waitingForLifebase,
                        modifier = Modifier.testTag("today-reading"),
                    )
                    Spacer(Modifier.height(12.dp))
                    when {
                        ui.showingPrevious && !ui.connected ->
                            TextButton(onClick = onDraw, modifier = Modifier.testTag("draw-early")) {
                                Text(stringResource(R.string.today_draw_now), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        !ui.showingPrevious -> Actions(reading, ui.connected, onPick, onRedraw)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmDraw) {
        AlertDialog(
            onDismissRequest = { confirmDraw = false },
            title = { Text(stringResource(R.string.today_manual_draw_title)) },
            text = { Text(stringResource(R.string.today_manual_draw_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDraw = false
                        onDraw()
                    },
                    modifier = Modifier.testTag("confirm-draw"),
                ) { Text(stringResource(R.string.today_manual_draw_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDraw = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WaitingForLifebase(onManualDraw: () -> Unit) {
    Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.today_waiting_lifebase),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("waiting-lifebase"),
        )
        Spacer(Modifier.height(8.dp))
        Hint(stringResource(R.string.today_waiting_lifebase_body))
        Spacer(Modifier.height(48.dp))
        // Deliberately small and away from the card: a manual card is the exception here.
        TextButton(onClick = onManualDraw, modifier = Modifier.testTag("manual-draw")) {
            Text(
                stringResource(R.string.today_manual_draw),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.today_manual_draw_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PreviousBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CircleShape) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("previous-card"),
        )
    }
}

@Composable
private fun Actions(reading: DailyReading, connected: Boolean, onPick: () -> Unit, onRedraw: () -> Unit) {
    if (connected) return
    if (reading.source == ReadingSource.LEGACY) {
        Text(
            stringResource(R.string.today_legacy_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.Center) {
        if (reading.source == ReadingSource.LEGACY) {
            TextButton(onClick = onRedraw, modifier = Modifier.testTag("redraw")) {
                Text(stringResource(R.string.today_redraw), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onPick, modifier = Modifier.testTag("pick")) {
            Text(stringResource(R.string.today_choose_manually), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun previewReading(source: ReadingSource = ReadingSource.LIFEBASE) = DailyReading(
    day = Day(2026, 10, 7), zoneId = "Asia/Seoul", cardId = 60, reversed = false, source = source,
    headline = "멀쩡한 문장보다 조금 민망한 진심이 먼저 살아납니다",
    keywordsText = "감수성, 새 마음, 공상, 감정적인 충동",
    body = "지나간 일을 쓰다가 성과보다 그때의 설렘이 먼저 떠오를 수 있어요. 오늘은 멋있어 보이는 설명보다 솔직한 한 줄이 사람을 끌어당길지도 몰라요.",
    fortuneRaw = null, contentStatus = ContentStatus.COMPLETE, catalogVersion = null,
    sourceConnectionId = null, sourceRelativePath = null, sourceHash = null, createdAt = 0, updatedAt = 0,
)

@Preview(widthDp = 380, heightDp = 1000)
@Composable
private fun WaitingPreview() {
    DailytarotTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            TodayScreen(
                TodayUi(
                    Day(2026, 10, 8), null, null, null, revealed = false, connected = true,
                    waitingForLifebase = false, showingPrevious = false, awaitingDraw = true,
                    dayStartLabel = "오전 12:00", autoDrawLabel = null,
                ),
                {}, {}, {}, {},
            )
        }
    }
}
