package com.jagaldol.dailytarot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import com.jagaldol.dailytarot.model.Card
import com.jagaldol.dailytarot.model.displayLabel
import com.jagaldol.dailytarot.data.saveToday
import com.jagaldol.dailytarot.data.loadToday
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.widget.syncAllWidgetState
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var reversed by remember { mutableStateOf(false) }
    var savedId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val (sid, _) = loadToday(ctx)
        savedId = sid
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("오늘의 카드", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = reversed,
                    onCheckedChange = { reversed = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(if (reversed) "역방향" else "정방향")
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = selectedId != null,
                    onClick = {
                        scope.launch {
                            val id = selectedId ?: return@launch
                            val name = Deck.first { it.id == id }.name
                            saveToday(ctx, id, reversed)
                            syncAllWidgetState(ctx, id, name, reversed)
                            onSaved()
                        }
                    }
                ) { Text("저장") }
            }
            Spacer(Modifier.height(12.dp))
            SectionsGrid(
                selectedId = selectedId,
                savedId = savedId,
                onSelect = { selectedId = it }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionsGrid(
    selectedId: Int?,
    savedId: Int?,
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
                    cards.forEach { c ->
                        val sel = selectedId == c.id
                        val isSaved = savedId == c.id
                        val resId = com.jagaldol.dailytarot.model.thumbResFor(ctx, c.id)
                        val container = if (sel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        Card(
                            modifier = Modifier
                                .width(96.dp)
                                .aspectRatio(70f / 120f),
                            onClick = { onSelect(c.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = container
                            ),
                            border = if (isSaved) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            elevation = CardDefaults.cardElevation(defaultElevation = if (sel) 4.dp else 2.dp)
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = c.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, start = 6.dp, end = 6.dp),
                                    contentScale = ContentScale.Fit
                                )
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
                                        .background(container)
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                        .heightIn(min = 30.dp, max = 42.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(0.dp))
            }
        }
    }
}
