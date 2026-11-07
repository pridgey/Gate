package dev.pridgey.gates.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pridgey.gates.data.Days

/** RESET-6: one question, plus the one-time Still mode offer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(onDone: (resetMinutes: Int, still: Boolean) -> Unit) {
    var reset by remember { mutableStateOf(6 * 60) }
    var still by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("When does your day usually start?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Daily limits refill then, not at midnight.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5 * 60, 6 * 60, 7 * 60, 8 * 60, 9 * 60).forEach { m ->
                        FilterChip(selected = reset == m, onClick = { reset = m }, label = { Text(Days.formatTime(m)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Still mode")
                        Text("A calmer look and a slow breath on gates. Off by default.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(still, onCheckedChange = { still = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onDone(reset, still) }) { Text("Done") } },
    )
}
