package com.jagaldol.dailytarot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jagaldol.dailytarot.data.saveToday
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.widget.syncAllWidgetState
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var reversed by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("오늘의 카드", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Row {
            Switch(checked = reversed, onCheckedChange = { reversed = it })
            Spacer(Modifier.width(8.dp))
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
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(Deck) { c ->
                val sel = selectedId == c.id
                ListItem(
                    headlineContent = { Text(c.name) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { selectedId = c.id },
                    tonalElevation = if (sel) 6.dp else 0.dp
                )
                Divider()
            }
        }
    }
}