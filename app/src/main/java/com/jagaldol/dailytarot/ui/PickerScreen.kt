package com.jagaldol.dailytarot.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.model.Card
import com.jagaldol.dailytarot.model.DeckSections
import com.jagaldol.dailytarot.ui.theme.gold

private val sectionNamesKo = mapOf(
    "MAJOR ARCANA" to "메이저 아르카나",
    "WANDS" to "완드",
    "CUPS" to "컵",
    "PENTACLES" to "펜타클",
    "SWORDS" to "소드",
)

@Composable
fun PickerScreen(
    initialCardId: Int?,
    initialReversed: Boolean,
    nameKo: (Int) -> String,
    onConfirm: (Int, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(initialCardId) }
    var reversed by rememberSaveable { mutableStateOf(initialReversed) }
    // One animation drives every thumbnail; frames only touch graphicsLayer.
    val angle = animateFloatAsState(if (reversed) 180f else 0f, tween(380), label = "orientation")

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        BackBar(stringResource(R.string.picker_title), onBack, Modifier.padding(horizontal = 8.dp))
        OrientationToggle(
            reversed, { reversed = it },
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            modifier = Modifier.weight(1f).testTag("cards"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DeckSections.forEach { section ->
                item(key = section.title, span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                    SectionLabel(
                        sectionNamesKo[section.title] ?: section.title,
                        Modifier.padding(top = 20.dp, bottom = 2.dp, start = 4.dp),
                    )
                }
                items(section.cards, key = { it.id }, contentType = { "card" }) { card ->
                    PickerCard(card, nameKo(card.id), card.id == selected, angle) { selected = card.id }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 12.dp) {
            Button(
                onClick = { selected?.let { onConfirm(it, reversed) } },
                enabled = selected != null,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm"),
            ) {
                val label = selected?.let { "${nameKo(it)} · ${orientationLabel(reversed)}" }
                Text(
                    if (label == null) stringResource(R.string.picker_hint)
                    else "$label  —  ${stringResource(R.string.picker_confirm)}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun OrientationToggle(reversed: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
    ) {
        listOf(false, true).forEach { value ->
            val active = value == reversed
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
                    .selectable(active, role = Role.Tab) { onChange(value) }
                    .padding(vertical = 10.dp)
                    .testTag(if (value) "reversed" else "upright"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    orientationLabel(value),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PickerCard(
    card: Card,
    nameKo: String,
    selected: Boolean,
    angle: androidx.compose.runtime.State<Float>,
    onSelect: () -> Unit,
) {
    val gold = MaterialTheme.colorScheme.gold
    Column(
        Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = "$nameKo (${card.name})" }
            .testTag("card-${card.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 5f)
                .then(
                    if (selected) Modifier.border(BorderStroke(2.dp, gold), RoundedCornerShape(10.dp))
                    else Modifier,
                )
                .padding(if (selected) 4.dp else 0.dp),
        ) {
            CardFace(
                card.id, reversed = false,
                Modifier.fillMaxSize().graphicsLayer { rotationZ = angle.value },
                corner = 7.dp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            nameKo,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp),
        )
    }
}
