package com.jagaldol.dailytarot.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.data.CardImages
import com.jagaldol.dailytarot.data.SaveError
import com.jagaldol.dailytarot.data.TodayState
import com.jagaldol.dailytarot.model.Card
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.model.DeckSections
import com.jagaldol.dailytarot.model.TodaySelection
import com.jagaldol.dailytarot.model.displayLabel
import com.jagaldol.dailytarot.ui.theme.DailytarotTheme

@Composable
fun TodayScreen(
    state: TodayState,
    onSelect: (Int) -> Unit,
    onReverse: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(if (state.selection.reversed) R.string.reversed else R.string.upright),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(8.dp))
                val reverseLabel = stringResource(R.string.reverse_card)
                Switch(
                    checked = state.selection.reversed,
                    onCheckedChange = onReverse,
                    enabled = !state.loading,
                    modifier = Modifier.testTag("reverse").semantics { contentDescription = reverseLabel },
                )
            }
            // Selection feedback stays stable during persistence; failures offer an explicit retry.
            val selectedName = state.selection.cardId?.let { Deck[it].name }
            val status = when {
                state.error == SaveError.LOAD -> stringResource(R.string.load_failed)
                state.error == SaveError.SAVE -> stringResource(R.string.save_failed)
                state.error == SaveError.WIDGET -> stringResource(R.string.widget_failed)
                state.loading -> stringResource(R.string.loading)
                selectedName != null -> stringResource(R.string.selected_card, selectedName)
                else -> stringResource(R.string.choose_card)
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).testTag("save-status"),
                    color = if (state.error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.error != null) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
            if (state.loading) {
                if (state.error == null) CircularProgressIndicator()
            } else {
                SectionsGrid(state.selection, onSelect)
            }
        }
    }
}

@Composable
private fun SectionsGrid(selection: TodaySelection, onSelect: (Int) -> Unit) {
    // Read angle.value only inside graphicsLayer: animation frames do not recompose the grid.
    val angle = animateFloatAsState(
        targetValue = if (selection.reversed) 180f else 0f,
        animationSpec = tween(350),
        label = "card orientation",
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = Modifier.fillMaxSize().testTag("cards"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        DeckSections.forEach { section ->
            item(key = section.title, span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                Text(
                    section.title,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            items(section.cards, key = { it.id }, contentType = { "card" }) { card ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    TarotCard(card, card.id == selection.cardId, angle, onSelect)
                }
            }
        }
    }
}

@Composable
private fun TarotCard(card: Card, selected: Boolean, angle: State<Float>, onSelect: (Int) -> Unit) {
    val resources = LocalResources.current
    val bitmap by produceState(CardImages.cachedThumbnail(card.thumbnailRes), card.thumbnailRes) {
        value = CardImages.thumbnail(resources, card.thumbnailRes)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Column(
        modifier = Modifier.width(96.dp).testTag("card-${card.id}")
            .selectable(selected = selected, role = Role.Button, onClick = { onSelect(card.id) })
            .semantics { contentDescription = card.name },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 5f)) {
            Card(
                modifier = Modifier.fillMaxSize(),
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {}
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null, // The selectable card is labelled by its text.
                    modifier = Modifier.fillMaxSize().padding(6.dp).graphicsLayer {
                        rotationZ = angle.value
                    }.testTag("image-${card.id}"),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            displayLabel(card.id),
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740)
@Composable
private fun TodayPreview() {
    DailytarotTheme {
        TodayScreen(TodayState(TodaySelection(0), loading = false), {}, {}, {})
    }
}
