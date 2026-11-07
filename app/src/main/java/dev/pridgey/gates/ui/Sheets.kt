package dev.pridgey.gates.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pridgey.gates.data.AppConfig
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.Group
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.data.Reason
import dev.pridgey.gates.data.formatMinutes

/** One-tap time choices. Never a hold. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeChips(choices: List<Pair<Int, Boolean>>, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (m, all) ->
            Button(onClick = { onPick(m) }) { Text(if (all) "All ${m}m" else formatMinutes(m)) }
        }
    }
}

/** STILL-3: one paced breath (about 4s in, 6s out). Purely visual; never blocks a choice. */
@Composable
private fun BreathRing() {
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(4000, easing = LinearOutSlowInEasing))
        scale.animateTo(0.4f, tween(6000, easing = LinearOutSlowInEasing))
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(64.dp)) {
        drawCircle(color.copy(alpha = 0.6f), radius = size.minDimension / 2 * scale.value, style = Stroke(3.dp.toPx()))
    }
}

/** GATE-1 / GATE-3: Pause and Ask first sheets. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GateSheet(
    app: LaunchApp,
    config: AppConfig,
    remaining: Int?,
    intro: String?,
    onPick: (minutes: Int, reason: String?) -> Unit,
    onSaveReason: (Reason) -> Unit,
    onCancel: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheet) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppGlyph(app, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    remaining?.let { Text("${formatMinutes(it)} left today", style = MaterialTheme.typography.bodySmall) }
                }
                if (LocalStill.current) BreathRing()
            }
            intro?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            if (config.gate == GateType.ASK) {
                Text("What are you opening it for?", style = MaterialTheme.typography.titleSmall)
                config.reasons.forEach { r ->
                    val m = remaining?.let { minOf(r.minutes, it) } ?: r.minutes
                    OutlinedButton(onClick = { onPick(m, r.label) }, modifier = Modifier.fillMaxWidth()) {
                        Text(r.label, Modifier.weight(1f))
                        Text(if (remaining != null && m == remaining && r.minutes >= remaining) "All ${m}m" else formatMinutes(m))
                    }
                }
                var typed by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Something else") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (typed.isNotBlank()) {
                    TimeChips(Limits.choices(listOf(5, 10, 15), remaining)) { m ->
                        onSaveReason(Reason(typed.trim(), m))
                        onPick(m, typed.trim())
                    }
                }
            } else {
                TimeChips(Limits.choices(config.pauseChoices, remaining)) { onPick(it, null) }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

/** TIME-3: opening an out-of-time app. */
@Composable
fun OutOfTimeDialog(app: LaunchApp, backAt: String, onBorrow: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${app.label} is out of time") },
        text = { Text("$backAt. You can borrow from tomorrow's time if you need it.") },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onBorrow(10) }) { Text("Borrow 10m from tomorrow") } },
    )
}

/** HOME-3 / APP-2: everything about one app, from a long-press or the Gates app. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppOptionsSheet(
    app: LaunchApp,
    config: AppConfig,
    groups: List<Group>,
    onHome: Boolean,
    onDock: Boolean,
    remaining: Int?,
    onChange: ((AppConfig) -> AppConfig) -> Unit,
    onToggleHome: () -> Unit,
    onToggleDock: () -> Unit,
    onRearrange: () -> Unit,
    onNewGroup: (name: String, limit: Int) -> String,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newGroup by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppGlyph(app, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    remaining?.let { Text("${formatMinutes(it)} left today", style = MaterialTheme.typography.bodySmall) }
                }
            }

            SectionLabel("Gate")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GateType.entries.forEach { g ->
                    FilterChip(
                        selected = config.gate == g,
                        onClick = { onChange { it.copy(gate = g) } },
                        label = { Text(g.title) },
                    )
                }
            }
            Text(
                when (config.gate) {
                    GateType.NONE -> "Opens immediately."
                    GateType.PAUSE -> "Pick a time, it opens for that long."
                    GateType.ASK -> "Name a reason; it opens with that reason's time."
                    GateType.VAULT -> "Fingerprint every time. No timer."
                },
                style = MaterialTheme.typography.bodySmall,
            )

            if (config.gate == GateType.ASK) {
                SectionLabel("Reasons")
                config.reasons.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, Modifier.weight(1f))
                        Text(formatMinutes(r.minutes), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { onChange { c -> c.copy(reasons = c.reasons - r) } }) {
                            Icon(Icons.Filled.Close, "Remove reason")
                        }
                    }
                }
                AddReasonRow { r -> onChange { c -> c.copy(reasons = c.reasons + r) } }
            }

            SectionLabel("Daily limit")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Int?>(null, 15, 30, 60, 90, 120).forEach { m ->
                    FilterChip(
                        selected = config.dailyLimit == m,
                        onClick = { onChange { it.copy(dailyLimit = m) } },
                        label = { Text(m?.let(::formatMinutes) ?: "None") },
                    )
                }
            }

            SectionLabel("Group")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.groupId == null,
                    onClick = { onChange { it.copy(groupId = null) } },
                    label = { Text("None") },
                )
                groups.forEach { g ->
                    FilterChip(
                        selected = config.groupId == g.id,
                        onClick = { onChange { it.copy(groupId = g.id) } },
                        label = { Text("${g.name} · ${formatMinutes(g.limit)}") },
                    )
                }
                AssistChip(onClick = { newGroup = true }, label = { Text("New group…") })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Move Upstairs when out of time", Modifier.weight(1f))
                Switch(config.upstairsWhenOut, onCheckedChange = { v -> onChange { it.copy(upstairsWhenOut = v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hide when lending the phone")
                    if (config.gate == GateType.VAULT) Text("Vault apps are always hidden.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    config.hideWhenLending || config.gate == GateType.VAULT,
                    enabled = config.gate != GateType.VAULT,
                    onCheckedChange = { v -> onChange { it.copy(hideWhenLending = v) } },
                )
            }

            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onChange { it.copy(manualUpstairs = !it.manualUpstairs) } }) {
                    Text(if (config.manualUpstairs) "Bring down" else "Move Upstairs")
                }
                OutlinedButton(onClick = onToggleHome) { Text(if (onHome) "Remove from home" else "Add to home") }
                OutlinedButton(onClick = onToggleDock) { Text(if (onDock) "Remove from dock" else "Add to dock") }
                if (onHome) OutlinedButton(onClick = onRearrange) { Text("Rearrange home") }
                if (!app.isGates) OutlinedButton(onClick = onAppInfo) { Text("App info") }
            }
        }
    }
    if (newGroup) {
        GroupDialog(
            group = null,
            onSave = { name, limit ->
                val id = onNewGroup(name, limit)
                onChange { it.copy(groupId = id) }
                newGroup = false
            },
            onDelete = null,
            onDismiss = { newGroup = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddReasonRow(onAdd: (Reason) -> Unit) {
    var text by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(5) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            label = { Text("Add a reason") }, modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions(onDone = {
                if (text.isNotBlank()) { onAdd(Reason(text.trim(), minutes)); text = "" }
            }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        if (text.isNotBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 5, 10, 15, 30).forEach { m ->
                    FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(formatMinutes(m)) })
                }
                Button(onClick = { onAdd(Reason(text.trim(), minutes)); text = "" }) { Text("Add") }
            }
        }
    }
}

/** APP-3: create, rename or delete a group. */
@Composable
fun GroupDialog(
    group: Group?,
    onSave: (name: String, limit: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var limit by remember { mutableStateOf(group?.limit?.toString() ?: "60") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "New group" else "Edit group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    limit, { limit = it.filter(Char::isDigit) },
                    label = { Text("Shared daily limit (minutes)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete group", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && (limit.toIntOrNull() ?: 0) > 0,
                onClick = { onSave(name.trim(), limit.toInt()) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionLabel(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

