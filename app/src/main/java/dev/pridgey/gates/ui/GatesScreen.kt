package dev.pridgey.gates.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pridgey.gates.access.GateAccessibilityService
import dev.pridgey.gates.island.GatesIslandService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.gates.data.Days
import dev.pridgey.gates.timer.SoftExit
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.GatesState
import dev.pridgey.gates.data.Group
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.data.TimerDisplay
import dev.pridgey.gates.data.formatMinutes

/** The Gates app (APP-1..4, SET-1): every gate, limit and group in one place. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GatesScreen(
    vm: LauncherViewModel,
    state: GatesState,
    apps: List<LaunchApp>,
    usage: Map<String, Int>,
    hasUsageAccess: Boolean,
    onEditApp: (LaunchApp) -> Unit,
    onDiagnostics: () -> Unit,
    onAuth: (label: String, onSuccess: () -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val settings = state.settings
    var picker by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<Group?>(null) }
    var newGroup by remember { mutableStateOf(false) }
    val gated = apps.filter { state.config(it.pkg).gate != GateType.NONE }
    val ungated = apps.filter { state.config(it.pkg).gate == GateType.NONE && !it.isGates }

    LaunchedEffect(Unit) { vm.refreshUsage() }
    // Re-read on every recomposition; returning from Settings triggers one via usage refresh.
    val a11yOn = GateAccessibilityService.isEnabled(context)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Gates", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
                }
            }
            if (!settings.gatesIntroSeen) item {
                Notice(
                    "Gates puts a pause only where you ask for one. Long-press any icon to give it a gate, a limit or a group.",
                    action = "Got it",
                ) { vm.updateSettings { it.copy(gatesIntroSeen = true) } }
            }
            if (!hasUsageAccess) item {
                Notice("Daily limits need usage access, so Gates can see how long each app is open. Nothing leaves this phone.", "Allow") {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            if (!Settings.canDrawOverlays(context)) item {
                Notice("Optional: allow Gates to show its timer pill and time's-up sheet over other apps.", "Allow") {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }

            if (!a11yOn) item {
                Notice(
                    "Opening an app from a notification or recents skips its gate. Gates can cover those too with an " +
                        "accessibility service. It only sees which app is in front and whether the status bar is " +
                        "showing, never what's inside apps, and nothing leaves this phone. It also hides the timer " +
                        "during full-screen video and games.",
                    "Turn on",
                ) { GateAccessibilityService.openSettings(context) }
            }

            // APP-1: gated apps by gate type.
            listOf(GateType.PAUSE, GateType.ASK, GateType.VAULT).forEach { type ->
                val list = gated.filter { state.config(it.pkg).gate == type }
                if (list.isNotEmpty()) {
                    item { SectionLabel(type.title) }
                    items(list, key = { "${type.name}-${it.pkg}" }) { app ->
                        AppRow(app, state, usage, onClick = { onEditApp(app) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("${ungated.size} apps open as usual", Modifier.weight(1f))
                    Button(onClick = { picker = true }) { Text("Add a gate") }
                }
            }

            // WEEK-1: weekly summary. No scores, no streaks.
            item { SectionLabel("This week") }
            item { WeeklySummary(state, apps, usage) }

            // APP-3: groups.
            item { SectionLabel("Groups") }
            items(state.groups, key = { "g-${it.id}" }) { g ->
                val used = Limits.groupUsed(state, g.id, usage)
                val members = apps.filter { state.config(it.pkg).groupId == g.id }.joinToString { it.label }
                Column(Modifier.fillMaxWidth().clickable { editGroup = g }.padding(vertical = 6.dp)) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium)
                    Text("$used / ${g.limit} min today", style = MaterialTheme.typography.bodySmall)
                    if (members.isNotEmpty()) Text(members, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { OutlinedButton(onClick = { newGroup = true }) { Text("New group") } }

            // SET-1: settings.
            item { SectionLabel("Settings") }
            item {
                Column {
                    Text("Limits reset at")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = settings.pendingResetMinutes ?: settings.resetMinutes
                        TextButton(onClick = { setReset(vm, shown - 15) }) { Text("−") }
                        Text(Days.formatTime(shown), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { setReset(vm, shown + 15) }) { Text("+") }
                    }
                    if (settings.pendingResetMinutes != null) {
                        Text(
                            "Starts after the next reset (${Days.formatTime(settings.resetMinutes)}), so it can't refill today.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item { SwitchRow("Still mode", "Calmer colours and a slow breath on gates.", settings.stillMode) { v -> vm.updateSettings { it.copy(stillMode = v) } } }
            if (settings.stillMode) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.stillFrom == null,
                        onClick = { vm.updateSettings { it.copy(stillFrom = null) } },
                        label = { Text("Always") },
                    )
                    listOf(20 * 60, 21 * 60, 22 * 60).forEach { m ->
                        FilterChip(
                            selected = settings.stillFrom == m,
                            onClick = { vm.updateSettings { it.copy(stillFrom = m) } },
                            label = { Text("${Days.formatTime(m)} to reset") },
                        )
                    }
                }
            }
            if (settings.stillMode && !SoftExit.canGrayscale(context)) item {
                Text(
                    "Time's up fades to gray with an overlay. For true grayscale, run once from a computer: " +
                        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Column {
                    Text("Timer display")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimerDisplay.entries.forEach { d ->
                            FilterChip(
                                selected = settings.timerDisplay == d,
                                onClick = { vm.updateSettings { it.copy(timerDisplay = d) } },
                                label = { Text(d.title) },
                            )
                        }
                    }
                }
            }
            item { IslandStatus() }
            item {
                SwitchRow(
                    "Gate opens from notifications and recents",
                    if (a11yOn) "Uses the accessibility service." else "Turn on the accessibility service first.",
                    settings.gateFromNotifications && a11yOn,
                    enabled = a11yOn,
                ) { v -> vm.updateSettings { it.copy(gateFromNotifications = v) } }
            }
            item {
                SwitchRow("Show Gates on home", "You can always reach Gates from All apps.", settings.showGatesTile) { v ->
                    vm.updateSettings { it.copy(showGatesTile = v) }
                }
            }
            // LEND-1
            item { SectionLabel("Lending") }
            item {
                SwitchRow(
                    "Lend the phone",
                    "Hides Vault apps, apps you mark \"Hide when lending\" and Gates itself. Ending it needs your fingerprint.",
                    settings.lending,
                ) { on ->
                    if (on) {
                        vm.updateSettings { it.copy(lending = true) }
                        onClose()
                    } else {
                        onAuth("End lending") { vm.updateSettings { it.copy(lending = false) } }
                    }
                }
            }

            item { SectionLabel("Diagnostics") }
            item {
                OutlinedButton(onClick = onDiagnostics) { Text("Timer & island diagnostics") }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false },
            title = { Text("Add a gate") },
            text = {
                LazyColumn {
                    items(ungated, key = { it.pkg }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { picker = false; onEditApp(app) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppGlyph(app, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = false }) { Text("Close") } },
        )
    }
    editGroup?.let { g ->
        GroupDialog(
            group = g,
            onSave = { name, limit -> vm.editGroup(g.id, name, limit); editGroup = null },
            onDelete = { vm.deleteGroup(g.id); editGroup = null },
            onDismiss = { editGroup = null },
        )
    }
    if (newGroup) {
        GroupDialog(
            group = null,
            onSave = { name, limit -> vm.newGroup(name, limit); newGroup = false },
            onDelete = null,
            onDismiss = { newGroup = false },
        )
    }
}

/** Where the timer goes: the Essentials island when paired (PRD priority 1). */
@Composable
private fun IslandStatus() {
    val context = LocalContext.current
    val attached by GatesIslandService.attached.collectAsStateWithLifecycle()
    val installed = GatesIslandService.essentialsInstalled(context)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Dynamic island", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    attached -> "Connected to Essentials. Timers show on the island."
                    !installed -> "Install Essentials to show timers in a dynamic island. Until then Gates uses the status-bar chip or its own pill."
                    GatesIslandService.paired(context) -> "Paired with Essentials. It connects while the island is on."
                    else -> "In Essentials, open Island → Island apps and turn on Gates timer. Until then Essentials shows Gates' timer notification as a plain timer."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (installed && !attached) {
                TextButton(onClick = { GatesIslandService.openEssentials(context) }) { Text("Open Essentials") }
            }
        }
    }
}

/** RESET-2: 15-minute steps; the change waits for the next reset. */
private fun setReset(vm: LauncherViewModel, minutes: Int) {
    val m = ((minutes % 1440) + 1440) % 1440
    vm.updateSettings { s -> s.copy(pendingResetMinutes = if (m == s.resetMinutes) null else m) }
}

@Composable
private fun AppRow(app: LaunchApp, state: GatesState, usage: Map<String, Int>, onClick: () -> Unit) {
    val cfg = state.config(app.pkg)
    val left = Limits.remaining(state, app.pkg, usage)
    val group = cfg.groupId?.let { id -> state.groups.find { it.id == id } }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(app, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label)
            val details = listOfNotNull(
                cfg.dailyLimit?.let { "${formatMinutes(it)} a day" },
                group?.name,
                left?.let { "${formatMinutes(it)} left" },
                "Upstairs".takeIf { cfg.manualUpstairs },
            )
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WeeklySummary(state: GatesState, apps: List<LaunchApp>, usage: Map<String, Int>) {
    // Last 6 finished days plus today so far.
    val days = state.history.takeLast(6) + dev.pridgey.gates.data.DaySummary(
        dayStart = state.ledger.dayStart,
        minutes = usage.filterKeys { state.apps.containsKey(it) },
        cancels = state.ledger.cancels,
        borrowed = state.ledger.borrowed,
    )
    fun sum(pick: (dev.pridgey.gates.data.DaySummary) -> Map<String, Int>) =
        days.flatMap { pick(it).entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
    val minutes = sum { it.minutes }.filterKeys { state.config(it).gate != GateType.NONE || state.apps[it]?.dailyLimit != null }
    val cancels = sum { it.cancels }
    val borrowed = sum { it.borrowed }
    val label = { pkg: String -> apps.find { it.pkg == pkg }?.label ?: pkg }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (minutes.isEmpty() && cancels.isEmpty()) {
            Text("Nothing yet. Time in gated apps shows here.", style = MaterialTheme.typography.bodySmall)
        }
        minutes.entries.sortedByDescending { it.value }.forEach { (pkg, m) ->
            Row {
                Text(label(pkg), Modifier.weight(1f))
                Text(formatMinutes(m))
            }
        }
        val totalCancels = cancels.values.sum()
        if (totalCancels > 0) {
            Text(
                "Closed at the gate $totalCancels time${if (totalCancels == 1) "" else "s"}: " +
                    cancels.entries.sortedByDescending { it.value }.joinToString { "${label(it.key)} ${it.value}" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val totalBorrowed = borrowed.values.sum()
        if (totalBorrowed > 0) {
            Text("Borrowed from the next day: ${formatMinutes(totalBorrowed)}", style = MaterialTheme.typography.bodySmall)
        }
        Text("Over the last ${days.size} day${if (days.size == 1) "" else "s"}.", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Notice(text: String, action: String, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onCheckedChange = onChange, enabled = enabled)
    }
}
