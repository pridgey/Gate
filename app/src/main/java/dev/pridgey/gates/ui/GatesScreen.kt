package dev.pridgey.gates.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.gates.access.GateAccessibilityService
import dev.pridgey.gates.data.Days
import dev.pridgey.gates.data.DaySummary
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.GatesState
import dev.pridgey.gates.data.Group
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.data.TimerDisplay
import dev.pridgey.gates.data.formatMinutes
import dev.pridgey.gates.island.GatesIslandService
import dev.pridgey.gates.timer.SoftExit
import dev.pridgey.gates.update.Updater

private enum class SettingSheet { RESET, STILL, TIMER, PICKER, UPDATE }

/** The Gates app (APP-1..4, SET-1, WEEK-1, LEND-1): everything in one calm place. */
@Composable
fun GatesScreen(
    vm: GatesActions,
    state: GatesState,
    apps: List<LaunchApp>,
    usage: Map<String, Int>,
    hasUsageAccess: Boolean,
    onEditApp: (LaunchApp) -> Unit,
    onDiagnostics: () -> Unit,
    onAuth: (label: String, onSuccess: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val settings = state.settings
    var sheet by remember { mutableStateOf<SettingSheet?>(null) }
    var editGroup by remember { mutableStateOf<Group?>(null) }
    var newGroup by remember { mutableStateOf(false) }
    val gated = apps.filter { state.config(it.pkg).gate != GateType.NONE }
    val ungated = apps.filter { state.config(it.pkg).gate == GateType.NONE }

    LaunchedEffect(Unit) { vm.refreshUsage() }
    // Re-read on recomposition; returning from Settings triggers one via the usage refresh.
    val a11yOn = GateAccessibilityService.isEnabled(context)
    val overlayOn = Settings.canDrawOverlays(context)
    val notificationsOn = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 48.dp),
        ) {
            item {
                Column(Modifier.padding(top = 28.dp, start = 4.dp)) {
                    Text("Gates", style = MaterialTheme.typography.displayMedium)
                    Text(
                        when {
                            !a11yOn && gated.isNotEmpty() -> "Gates are off until you allow them below"
                            else -> "${gated.size} gated · ${ungated.size} open as usual"
                        },
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                val update by Updater.state.collectAsStateWithLifecycle()
                (update as? Updater.State.Available)?.let { a ->
                    Note("Gates ${a.release.versionName} is ready to install.", "Update") { sheet = SettingSheet.UPDATE }
                }
            }

            if (!settings.gatesIntroSeen) item {
                Note(
                    "Gates puts a pause only where you ask for one. Tap Add a gate to give an app a gate, a daily limit or a shared limit. It shows up whenever you open that app.",
                    "Got it",
                ) { vm.updateSettings { it.copy(gatesIntroSeen = true) } }
            }

            // Setup checklist: only while something is missing.
            if (!hasUsageAccess || !a11yOn || !overlayOn || !notificationsOn) {
                item { SectionTitle("Finish setting up") }
                item {
                    GroupCard {
                        SetupRow("Turn on gates", "Accessibility: lets Gates step in when a gated app opens", a11yOn) {
                            GateAccessibilityService.openSettings(context)
                        }
                        CardDivider()
                        SetupRow("Usage access", "Needed for daily limits", hasUsageAccess) {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        CardDivider()
                        SetupRow("Show over other apps", "Timer pill and time's-up sheet", overlayOn) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        CardDivider()
                        SetupRow("Notifications", "The session timer", notificationsOn) {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }

            // APP-1: gated apps.
            item { SectionTitle("Gated apps") }
            item {
                GroupCard {
                    if (gated.isEmpty()) {
                        RowItem("No gates yet", subtitle = "Every app opens as usual")
                        CardDivider()
                    }
                    listOf(GateType.PAUSE, GateType.ASK, GateType.VAULT).forEach { type ->
                        gated.filter { state.config(it.pkg).gate == type }.forEach { app ->
                            GatedRow(app, state, usage) { onEditApp(app) }
                            CardDivider()
                        }
                    }
                    RowItem(
                        "Add a gate", titleColor = MaterialTheme.colorScheme.primary,
                        leading = { Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    ) { sheet = SettingSheet.PICKER }
                }
            }

            // WEEK-1
            item { SectionTitle("This week") }
            item { WeeklyCard(state, apps, usage) }

            // APP-3
            item { SectionTitle("Shared limits") }
            item {
                GroupCard {
                    state.groups.forEach { g ->
                        val used = Limits.groupUsed(state, g.id, usage)
                        val members = apps.filter { state.config(it.pkg).groupId == g.id }.joinToString { it.label }
                        Column(Modifier.fillMaxWidth().pressable { editGroup = g }.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(g.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text("$used of ${formatMinutes(g.limit)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(10.dp))
                            Meter(used / g.limit.toFloat())
                            if (members.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(members, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        CardDivider()
                    }
                    RowItem(
                        "New group", titleColor = MaterialTheme.colorScheme.primary,
                        leading = { Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    ) { newGroup = true }
                }
            }

            // SET-1
            item { SectionTitle("Settings") }
            item {
                val island by GatesIslandService.attached.collectAsStateWithLifecycle()
                GroupCard {
                    RowItem(
                        "Day starts at",
                        value = Days.formatTime(settings.pendingResetMinutes ?: settings.resetMinutes),
                        chevron = true,
                    ) { sheet = SettingSheet.RESET }
                    CardDivider()
                    RowItem(
                        "Still mode",
                        value = when {
                            !settings.stillMode -> "Off"
                            settings.stillFrom == null -> "Always"
                            else -> "From ${Days.formatTime(settings.stillFrom)}"
                        },
                        chevron = true,
                    ) { sheet = SettingSheet.STILL }
                    CardDivider()
                    RowItem(
                        "Timer",
                        value = if (island && settings.timerDisplay in setOf(TimerDisplay.AUTOMATIC, TimerDisplay.ISLAND)) "On the island" else settings.timerDisplay.title,
                        chevron = true,
                    ) { sheet = SettingSheet.TIMER }
                }
            }

            // LEND-1
            item { SectionTitle("Lending") }
            item {
                GroupCard {
                    ToggleRow(
                        "Lend the phone",
                        subtitle = "Blocks Vault apps and apps you choose, and locks Gates. Ending it needs your fingerprint.",
                        checked = settings.lending,
                    ) { on ->
                        if (on) {
                            vm.updateSettings { it.copy(lending = true) }
                        } else {
                            onAuth("End lending") { vm.updateSettings { it.copy(lending = false) } }
                        }
                    }
                }
            }

            item { SectionTitle("Help") }
            item {
                val update by Updater.state.collectAsStateWithLifecycle()
                GroupCard {
                    RowItem("Updates", value = updateLabel(update), chevron = true) { sheet = SettingSheet.UPDATE }
                    CardDivider()
                    RowItem("Timer & island diagnostics", chevron = true, onClick = onDiagnostics)
                }
            }
            item {
                val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
                Text(
                    "Gates $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 32.dp).navigationBarsPadding(),
                )
            }
        }
    }

    when (sheet) {
        SettingSheet.PICKER -> PickerSheet(ungated, onPick = { sheet = null; onEditApp(it) }, onDismiss = { sheet = null })
        SettingSheet.RESET -> ResetSheet(state, vm) { sheet = null }
        SettingSheet.STILL -> StillSheet(state, vm) { sheet = null }
        SettingSheet.TIMER -> TimerSheet(state, vm) { sheet = null }
        SettingSheet.UPDATE -> UpdateSheet { sheet = null }
        null -> Unit
    }
    editGroup?.let { g ->
        GroupSheet(
            group = g,
            onSave = { name, limit -> vm.editGroup(g.id, name, limit); editGroup = null },
            onDelete = { vm.deleteGroup(g.id); editGroup = null },
            onDismiss = { editGroup = null },
        )
    }
    if (newGroup) {
        GroupSheet(
            group = null,
            onSave = { name, limit -> vm.newGroup(name, limit); newGroup = false },
            onDelete = null,
            onDismiss = { newGroup = false },
        )
    }
}

@Composable
private fun Note(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.padding(top = 24.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer).padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(end = 12.dp))
        QuietButton(action, Modifier.align(Alignment.End), color = MaterialTheme.colorScheme.onPrimaryContainer, onClick = onAction)
    }
}

@Composable
private fun SetupRow(title: String, subtitle: String, done: Boolean, onClick: () -> Unit) {
    RowItem(
        title = title,
        subtitle = subtitle,
        leading = {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .then(if (done) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        },
        value = if (done) "On" else "Allow",
        chevron = !done,
        onClick = if (done) null else onClick,
    )
}

@Composable
private fun GatedRow(app: LaunchApp, state: GatesState, usage: Map<String, Int>, onClick: () -> Unit) {
    val cfg = state.config(app.pkg)
    val left = Limits.remaining(state, app.pkg, usage)
    val group = cfg.groupId?.let { id -> state.groups.find { it.id == id } }
    val details = listOfNotNull(
        cfg.gate.title,
        cfg.dailyLimit?.let { "${formatMinutes(it)} a day" },
        group?.name,
        left?.let { "${formatMinutes(it)} left" },
    ).joinToString(" · ")
    RowItem(
        app.label,
        subtitle = details,
        leading = { AppGlyph(app, 32.dp) },
        trailing = { GateMark(cfg.gate, MaterialTheme.colorScheme.onSurfaceVariant, 16.dp) },
        chevron = true,
        onClick = onClick,
    )
}

@Composable
private fun WeeklyCard(state: GatesState, apps: List<LaunchApp>, usage: Map<String, Int>) {
    // Last 6 finished days plus today so far. No scores, no streaks.
    val days = state.history.takeLast(6) + DaySummary(
        dayStart = state.ledger.dayStart,
        minutes = usage.filterKeys { state.apps.containsKey(it) },
        cancels = state.ledger.cancels,
        borrowed = state.ledger.borrowed,
    )
    fun sum(pick: (DaySummary) -> Map<String, Int>) =
        days.flatMap { pick(it).entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
    val minutes = sum { it.minutes }
        .filterKeys { state.config(it).gate != GateType.NONE || state.apps[it]?.dailyLimit != null }
        .filterValues { it > 0 }
    val cancels = sum { it.cancels }.values.sum()
    val borrowed = sum { it.borrowed }.values.sum()
    val label = { pkg: String -> apps.find { it.pkg == pkg }?.label ?: pkg }
    val max = (minutes.values.maxOrNull() ?: 1).coerceAtLeast(1)

    GroupCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (minutes.isEmpty()) {
                Text(
                    "Time in gated apps will show here.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            minutes.entries.sortedByDescending { it.value }.take(6).forEach { (pkg, m) ->
                Column {
                    Row {
                        Text(label(pkg), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(formatMinutes(m), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Meter(m / max.toFloat())
                }
            }
            val notes = listOfNotNull(
                "Closed at the gate $cancels ${if (cancels == 1) "time" else "times"}".takeIf { cancels > 0 },
                "Borrowed ${formatMinutes(borrowed)}".takeIf { borrowed > 0 },
            )
            Text(
                (notes + "Last ${days.size} ${if (days.size == 1) "day" else "days"}").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ setting sheets

@Composable
private fun PickerSheet(apps: List<LaunchApp>, onPick: (LaunchApp) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps
        else apps.filter { it.label.contains(q, ignoreCase = true) }
            .sortedBy { if (it.label.startsWith(q, ignoreCase = true)) 0 else 1 }
    }
    GSheet(onDismiss = onDismiss) {
        Eyebrow("Add a gate")
        Text("Which app?", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            InlineField(query, "Search apps", { query = it.take(40) })
        }
        Spacer(Modifier.height(12.dp))
        GroupCard {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (shown.isEmpty()) item { RowItem("Nothing called “$query”") }
                items(shown, key = { it.pkg }) { app ->
                    RowItem(app.label, leading = { AppGlyph(app, 30.dp) }) { onPick(app) }
                }
            }
        }
    }
}

/** RESET-2: 15-minute steps; the change waits for the next reset. */
@Composable
private fun ResetSheet(state: GatesState, vm: GatesActions, onDismiss: () -> Unit) {
    val s = state.settings
    val shown = s.pendingResetMinutes ?: s.resetMinutes
    fun set(minutes: Int) {
        val m = ((minutes % 1440) + 1440) % 1440
        vm.updateSettings { it.copy(pendingResetMinutes = if (m == it.resetMinutes) null else m) }
    }
    GSheet(onDismiss = onDismiss) {
        Eyebrow("Daily reset")
        SheetTitle("When does your day start?", "Limits refill then, not at midnight.")
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            StepButton("−") { set(shown - 15) }
            Text(
                Days.formatTime(shown), style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center, modifier = Modifier.width(200.dp),
            )
            StepButton("+") { set(shown + 15) }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (s.pendingResetMinutes != null) "Starts after the next reset at ${Days.formatTime(s.resetMinutes)}, so it can't refill today."
            else "Changes start from the next reset.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Done", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pressable { haptics.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
private fun StillSheet(state: GatesState, vm: GatesActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = state.settings
    GSheet(onDismiss = onDismiss) {
        Eyebrow("Still mode")
        SheetTitle("A calmer phone", "Warm, quiet colours, a slow breath on gates, and a soft fade when time's up.")
        Spacer(Modifier.height(20.dp))
        GroupCard { ToggleRow("Still mode", checked = s.stillMode) { v -> vm.updateSettings { it.copy(stillMode = v) } } }
        if (s.stillMode) {
            SectionTitle("When")
            PillRow {
                Pill("Always", s.stillFrom == null) { vm.updateSettings { it.copy(stillFrom = null) } }
                listOf(20 * 60, 21 * 60, 22 * 60).forEach { m ->
                    Pill("From ${Days.formatTime(m)}", s.stillFrom == m) { vm.updateSettings { it.copy(stillFrom = m) } }
                }
            }
            Text(
                "Until the day resets at ${Days.formatTime(s.resetMinutes)}.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
            if (!SoftExit.canGrayscale(context)) {
                Text(
                    "For a true grayscale fade, run once from a computer:\nadb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Done", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

@Composable
private fun TimerSheet(state: GatesState, vm: GatesActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val attached by GatesIslandService.attached.collectAsStateWithLifecycle()
    val installed = GatesIslandService.essentialsInstalled(context)
    val selected = state.settings.timerDisplay
    GSheet(onDismiss = onDismiss) {
        Eyebrow("Timer")
        SheetTitle("Where the timer shows", "Never over full-screen video or games.")
        Spacer(Modifier.height(20.dp))
        GroupCard {
            TimerDisplay.entries.forEachIndexed { i, d ->
                if (i > 0) CardDivider()
                RowItem(
                    d.title,
                    subtitle = when (d) {
                        TimerDisplay.AUTOMATIC -> "Island, then status bar, then Gates' pill"
                        TimerDisplay.ISLAND -> "Essentials dynamic island"
                        TimerDisplay.STATUS_CHIP -> "Android 16 Live Update chip"
                        TimerDisplay.OVERHEAD_PILL -> "A small pill under the status bar"
                        TimerDisplay.OFF -> "Only the time's-up sheet"
                    },
                    trailing = {
                        if (d == selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    },
                ) { vm.updateSettings { it.copy(timerDisplay = d) } }
            }
        }
        SectionTitle("Dynamic island")
        GroupCard {
            RowItem(
                when {
                    attached -> "Connected to Essentials"
                    !installed -> "Essentials isn't installed"
                    GatesIslandService.paired(context) -> "Paired with Essentials"
                    else -> "Not paired yet"
                },
                subtitle = when {
                    attached -> "Timers show on the island"
                    !installed -> "Gates uses the status bar or its own pill"
                    GatesIslandService.paired(context) -> "Connects while the island is on"
                    else -> "In Essentials: Island → Island apps → Gates timer"
                },
                chevron = installed && !attached,
                onClick = if (installed && !attached) ({ GatesIslandService.openEssentials(context) }) else null,
            )
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Done", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}
