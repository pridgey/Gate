package dev.pridgey.gates.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.pridgey.gates.data.AppConfig
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.Group
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.data.Reason
import dev.pridgey.gates.data.formatMinutes
import dev.pridgey.gates.data.spokenMinutes

/** STILL-3: one paced breath (about 4s in, 6s out). Purely visual; never blocks a choice. */
@Composable
private fun BreathRing() {
    val scale = remember { Animatable(0.35f) }
    var phase by remember { mutableStateOf("Breathe in") }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
        phase = "and out"
        scale.animateTo(0.35f, tween(6000, easing = FastOutSlowInEasing))
        phase = ""
    }
    val color = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(96.dp)) {
            val r = size.minDimension / 2 * scale.value
            drawCircle(color.copy(alpha = 0.12f), radius = r)
            drawCircle(color.copy(alpha = 0.5f), radius = r, style = Stroke(1.5.dp.toPx()))
        }
        Text(phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * GATE-1 / GATE-3: the Pause and Ask first sheets.
 * Reads top to bottom as one guided step: which app, one plain question, what happens
 * next, then choices that each say what they do. Still one tap to open.
 */
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
    val ask = config.gate == GateType.ASK
    GSheet(onDismiss = onCancel) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetAppHeader(
                app,
                listOfNotNull(if (ask) "Ask first" else "Pause", remaining?.let { "${spokenMinutes(it)} left today" }).joinToString(" · "),
                trailing = { GateMark(config.gate, MaterialTheme.colorScheme.primary, 18.dp) },
            )
            Text(
                if (ask) "What are you opening ${app.label} for?" else "How long do you need ${app.label}?",
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                intro ?: if (ask) {
                    "Pick a reason and it opens for that long. When time's up, Gates reminds you what you came for."
                } else {
                    "Pick a time and it opens. When time's up, Gates asks you to close it."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (LocalStill.current) BreathRing() else Spacer(Modifier.height(24.dp))

            if (ask) {
                AskChoices(config, remaining, onPick, onSaveReason)
            } else {
                Text(
                    "Open for", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp),
                )
                TimeChoices(Limits.choices(config.pauseChoices, remaining)) { onPick(it, null) }
            }
            Spacer(Modifier.height(16.dp))
            QuietButton("Don't open", Modifier.fillMaxWidth(), onClick = onCancel)
        }
    }
}

@Composable
private fun AskChoices(
    config: AppConfig,
    remaining: Int?,
    onPick: (Int, String?) -> Unit,
    onSaveReason: (Reason) -> Unit,
) {
    val haptics = rememberHaptics()
    var typed by remember { mutableStateOf("") }
    GroupCard {
        config.reasons.forEachIndexed { i, r ->
            if (i > 0) CardDivider()
            val m = remaining?.let { minOf(r.minutes, it) } ?: r.minutes
            val all = remaining != null && r.minutes >= remaining
            RowItem(
                title = r.label,
                subtitle = if (all) "Opens for the ${spokenMinutes(m)} you have left" else "Opens for ${spokenMinutes(m)}",
                chevron = true,
                onClick = { haptics.confirm(); onPick(m, r.label) },
            )
        }
        if (config.reasons.isNotEmpty()) CardDivider()
        InlineField(typed, "Something else…", { typed = it.take(60) })
    }
    AnimatedVisibility(typed.isNotBlank(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column {
            Text(
                "Open for", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
            )
            TimeChoices(Limits.choices(listOf(5, 10, 15), remaining)) { m ->
                val reason = typed.trim()
                onSaveReason(Reason(reason, m))
                onPick(m, reason)
            }
        }
    }
}

/** A text field that sits inside a GroupCard like a row. */
@Composable
internal fun InlineField(value: String, placeholder: String, onChange: (String) -> Unit, onDone: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** TIME-3: opening an app that's out of time. Calm, never scolding. */
@Composable
fun OutOfTimeSheet(app: LaunchApp, backAt: String, onBorrow: (Int) -> Unit, onDismiss: () -> Unit) {
    GSheet(onDismiss = onDismiss) {
        SheetAppHeader(app, "Out of time today")
        Text("${app.label} is resting until tomorrow", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(10.dp))
        Text(
            "$backAt. If you really need it now, you can borrow a little from tomorrow's time.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("OK", Modifier.fillMaxWidth(), onClick = onDismiss)
        Spacer(Modifier.height(10.dp))
        TonalButton("Borrow 10 min from tomorrow", Modifier.fillMaxWidth()) { onBorrow(10) }
    }
}

/** APP-2: everything about one app's gate and limits. */
@Composable
fun AppOptionsSheet(
    app: LaunchApp,
    config: AppConfig,
    groups: List<Group>,
    remaining: Int?,
    onChange: ((AppConfig) -> AppConfig) -> Unit,
    onNewGroup: (name: String, limit: Int) -> String,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    var newGroup by remember { mutableStateOf(false) }
    GSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetAppHeader(app, remaining?.let { "${spokenMinutes(it)} left today" } ?: "No daily limit")

            SectionTitle("Gate")
            Segmented(GateType.entries, config.gate, { if (it == GateType.ASK) "Ask" else it.title }) { g ->
                onChange { it.copy(gate = g) }
            }
            Text(
                when (config.gate) {
                    GateType.NONE -> "Opens straight away."
                    GateType.PAUSE -> "Pick a time; it opens for that long."
                    GateType.ASK -> "Pick a reason; it opens for that reason's time and reminds you of it."
                    GateType.VAULT -> "Fingerprint every time. No timer."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )

            if (config.gate == GateType.ASK) {
                SectionTitle("Reasons")
                ReasonsEditor(config.reasons) { reasons -> onChange { it.copy(reasons = reasons) } }
            }

            SectionTitle("Daily limit")
            PillRow {
                listOf<Int?>(null, 15, 30, 45, 60, 90, 120).forEach { m ->
                    Pill(m?.let(::formatMinutes) ?: "None", config.dailyLimit == m) { onChange { it.copy(dailyLimit = m) } }
                }
            }

            SectionTitle("Shared limit")
            PillRow {
                Pill("None", config.groupId == null) { onChange { it.copy(groupId = null) } }
                groups.forEach { g ->
                    Pill("${g.name} · ${formatMinutes(g.limit)}", config.groupId == g.id) { onChange { it.copy(groupId = g.id) } }
                }
                Pill("+ New group", false) { newGroup = true }
            }

            Spacer(Modifier.height(28.dp))
            GroupCard {
                ToggleRow(
                    "Hide when lending the phone",
                    subtitle = if (config.gate == GateType.VAULT) "Vault apps are always hidden" else null,
                    checked = config.hideWhenLending || config.gate == GateType.VAULT,
                    enabled = config.gate != GateType.VAULT,
                ) { v -> onChange { it.copy(hideWhenLending = v) } }
            }

            Spacer(Modifier.height(12.dp))
            GroupCard {
                RowItem("App info", chevron = true, onClick = onAppInfo)
            }
        }
    }
    if (newGroup) {
        GroupSheet(
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

@Composable
private fun ReasonsEditor(reasons: List<Reason>, onChange: (List<Reason>) -> Unit) {
    var text by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(5) }
    val add = {
        if (text.isNotBlank()) {
            onChange(reasons + Reason(text.trim(), minutes))
            text = ""
        }
    }
    GroupCard {
        reasons.forEachIndexed { i, r ->
            if (i > 0) CardDivider()
            RowItem(r.label, value = formatMinutes(r.minutes), trailing = {
                Box(
                    Modifier.padding(start = 8.dp).size(32.dp).clip(RoundedCornerShape(50)).pressable { onChange(reasons - r) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            })
        }
        if (reasons.isNotEmpty()) CardDivider()
        InlineField(text, "Add a reason", { text = it.take(60) }, onDone = add)
    }
    AnimatedVisibility(text.isNotBlank()) {
        Column(Modifier.padding(top = 12.dp)) {
            PillRow {
                listOf(2, 5, 10, 15, 30).forEach { m -> Pill(formatMinutes(m), minutes == m) { minutes = m } }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Add reason", Modifier.fillMaxWidth(), onClick = add)
        }
    }
}

/** APP-3: create, rename or delete a group. */
@Composable
fun GroupSheet(
    group: Group?,
    onSave: (name: String, limit: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var limit by remember { mutableIntStateOf(group?.limit ?: 60) }
    GSheet(onDismiss = onDismiss) {
        Eyebrow("Shared limit")
        Text(if (group == null) "New group" else "Edit group", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Apps in a group share one daily allowance.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            InlineField(name, "Name, e.g. Social", { name = it.take(24) })
        }
        SectionTitle("Together, per day")
        PillRow {
            listOf(15, 30, 45, 60, 90, 120, 180).forEach { m -> Pill(formatMinutes(m), limit == m) { limit = m } }
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Save", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) { onSave(name.trim(), limit) }
        if (onDelete != null) {
            Spacer(Modifier.height(4.dp))
            QuietButton("Delete group", Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, onClick = onDelete)
        }
    }
}

/** A quoted reason, used on time's up. */
@Composable
fun ReasonQuote(reason: String) {
    Column(
        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("YOU CAME TO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(reason, style = MaterialTheme.typography.titleLarge, fontStyle = FontStyle.Italic)
    }
}
