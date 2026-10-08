package com.jagaldol.dailytarot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.ui.theme.gold

/**
 * Asks whether to put the widget on the home screen. It stays until one of the two buttons is
 * chosen: no swipe, outside tap or back press closes it. Both report "다시 보지 않기".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSheet(onAdd: (never: Boolean) -> Unit, onDecline: (never: Boolean) -> Unit) {
    var never by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = false,
        ),
        modifier = Modifier.testTag("widget-sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.gold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_widget), null,
                    tint = MaterialTheme.colorScheme.gold, modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.widget_prompt_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.widget_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { onAdd(never) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("widget-sheet-add"),
            ) {
                Text(stringResource(R.string.widget_prompt_add), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(10.dp))
            // A real second button in both themes, quieter than the filled one.
            OutlinedButton(
                onClick = { onDecline(never) },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("widget-sheet-decline"),
            ) {
                Text(stringResource(R.string.widget_prompt_dismiss), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .toggleable(value = never, role = Role.Checkbox, onValueChange = { never = it })
                    .padding(start = 4.dp, end = 12.dp)
                    .testTag("widget-sheet-never"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The row handles the toggle; the box only shows it.
                Checkbox(
                    checked = never,
                    onCheckedChange = null,
                    modifier = Modifier.padding(12.dp).size(18.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.gold,
                        checkmarkColor = MaterialTheme.colorScheme.onSecondary,
                        uncheckedColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Text(
                    stringResource(R.string.widget_prompt_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
