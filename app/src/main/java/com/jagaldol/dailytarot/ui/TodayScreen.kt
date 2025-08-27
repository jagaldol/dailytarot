package com.jagaldol.dailytarot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.jagaldol.dailytarot.model.Card
import com.jagaldol.dailytarot.model.displayLabel
import com.jagaldol.dailytarot.data.saveToday
import com.jagaldol.dailytarot.data.loadToday
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.widget.syncAllWidgetState
import kotlinx.coroutines.launch
import com.jagaldol.dailytarot.ui.theme.DailytarotTheme

@Composable
fun TodayScreen(loadInitial: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var reversed by remember { mutableStateOf(false) }
    var savedId by remember { mutableStateOf<Int?>(null) }

    if (loadInitial) {
        LaunchedEffect(Unit) {
            val (sid, srev) = loadToday(ctx)
            savedId = sid
            reversed = srev ?: false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Daily Tarot",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (reversed) "Reversed" else "Upright",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = reversed,
                    onCheckedChange = { newReversed ->
                        reversed = newReversed
                        // 즉시 저장: 현재 선택된 카드가 있으면 그 카드로, 없으면 저장된 카드로
                        val idToSave = selectedId ?: savedId
                        if (idToSave != null) {
                            scope.launch {
                                val name = Deck.first { it.id == idToSave }.name
                                saveToday(ctx, idToSave, reversed)
                                syncAllWidgetState(ctx, idToSave, name, reversed)
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            SectionsGrid(
                selectedId = selectedId,
                savedId = savedId,
                reversed = reversed,
                onSelect = { id ->
                    selectedId = id
                    // 카드 클릭 즉시 저장
                    scope.launch {
                        val name = Deck.first { it.id == id }.name
                        saveToday(ctx, id, reversed)
                        syncAllWidgetState(ctx, id, name, reversed)
                        savedId = id
                    }
                }
            )
        }
    }
}

// ---- Previews ----

@Preview(name = "Sections Grid", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun Preview_SectionsGrid_Default() {
    DailytarotTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SectionsGrid(
                selectedId = null,
                savedId = 10,
                reversed = false,
                onSelect = {}
            )
        }
    }
}

@Preview(name = "Sections Grid Reversed + Selected", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun Preview_SectionsGrid_Reversed_Selected() {
    DailytarotTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SectionsGrid(
                selectedId = 0,
                savedId = 0,
                reversed = true,
                onSelect = {}
            )
        }
    }
}

@Preview(name = "Today Screen", showBackground = true, widthDp = 360, heightDp = 740)
@Composable
private fun Preview_TodayScreen() {
    DailytarotTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            TodayScreen(loadInitial = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionsGrid(
    selectedId: Int?,
    savedId: Int?,
    reversed: Boolean,
    onSelect: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val sections: List<Pair<String, List<Card>>> = listOf(
        "MAJOR ARCANA" to Deck.filter { it.id in 0..21 },
        "WANDS" to Deck.filter { it.id in 36..49 },
        "CUPS" to Deck.filter { it.id in 50..63 },
        "PENTACLES" to Deck.filter { it.id in 22..35 },
        "SWORDS" to Deck.filter { it.id in 64..77 },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(sections.size) { idx ->
            val (title, cards) = sections[idx]
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Rotation animation for reversed toggle
                    val targetAngle = if (reversed) 180f else 0f
                    val angle by animateFloatAsState(
                        targetValue = targetAngle,
                        animationSpec = tween(durationMillis = 350)
                    )
                    val camDist = with(LocalDensity.current) { 12.dp.toPx() }

                    cards.forEach { c ->
                        val sel = selectedId == c.id
                        val isSaved = savedId == c.id
                        val resId = com.jagaldol.dailytarot.model.thumbResFor(ctx, c.id)
                        val container = if (sel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        Column(
                            modifier = Modifier.width(96.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(70f / 120f)
                            ) {
                                // Background card and click ripple with rounded corners
                                Card(
                                    modifier = Modifier.fillMaxSize(),
                                    onClick = { onSelect(c.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = container
                                    ),
                                    border = if (isSaved) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (sel) 4.dp else 2.dp)
                                ) {}

                                // Rotating image drawn on top without clipping
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = c.name,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                        .graphicsLayer {
                                            rotationZ = angle
                                            clip = false
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }

                            Text(
                                text = displayLabel(c.id),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .heightIn(min = 30.dp, max = 42.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(0.dp))
            }
        }
    }
}
