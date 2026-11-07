package dev.pridgey.gates.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.gates.diag.DiagLog
import dev.pridgey.gates.diag.GatesNotificationListener
import dev.pridgey.gates.diag.IslandProbe
import dev.pridgey.gates.diag.IslandTests
import dev.pridgey.gates.diag.LiveUpdates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Timer & island diagnostics: gathers what we need to connect the session timer to a
 * dynamic island (Essentials or similar) and to Android 16 Live Updates.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val log by DiagLog.lines.collectAsStateWithLifecycle()
    var candidates by remember { mutableStateOf<List<IslandProbe.Candidate>?>(null) }
    var listenerOn by remember { mutableStateOf(GatesNotificationListener.isEnabled(context)) }

    LaunchedEffect(Unit) {
        candidates = withContext(Dispatchers.IO) { IslandProbe.candidates(context) }
        listenerOn = GatesNotificationListener.isEnabled(context)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Timer & island diagnostics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
                }
                Text(
                    "Work through steps 1–4, then send the report from step 5.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            item { SectionTitle("1 · What's on this phone") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT}) · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        Text("Live Updates allowed: ${LiveUpdates.canPostPromotedOrNull(context) ?: "not on this Android version"}")
                        Text(
                            "Essentials: ${dev.pridgey.gates.island.GatesIslandService.essentialsVersion(context) ?: "not installed"} · " +
                                "paired ${dev.pridgey.gates.island.GatesIslandService.paired(context)} · " +
                                "connected ${dev.pridgey.gates.island.GatesIslandService.attached.value}",
                        )
                        val c = candidates
                        when {
                            c == null -> Text("Looking for island apps…")
                            c.isEmpty() -> Text("No island-like apps or notification readers found.")
                            else -> c.take(10).forEach { Text("• ${it.label}: ${it.reasons.joinToString("; ")}", style = MaterialTheme.typography.bodySmall) }
                        }
                        TextButton(onClick = {
                            DiagLog.block(context, "probe", IslandProbe.report(context))
                            Toast.makeText(context, "Probe added to the log", Toast.LENGTH_SHORT).show()
                        }) { Text("Run full probe (reads island app manifests)") }
                    }
                }
            }

            item { SectionTitle("2 · Try each timer style") }
            item {
                Text(
                    "Post one, look at the island and the status bar, then mark what you saw. Each lasts 5 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(IslandTests.variants, key = { it.key }) { v -> VariantCard(v) }
            item { OutlinedButton(onClick = { IslandTests.clearAll(context) }) { Text("Clear test notifications") } }

            item { SectionTitle("3 · Capture one the island does show") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Gates needs notification access for this step only. You can turn it off afterwards.")
                        Text(
                            "Then start a 5-minute timer in your Clock app (or anything the island shows), check it's in the island, and tap Snapshot.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!listenerOn) {
                                Button(onClick = { GatesNotificationListener.openSettings(context) }) { Text("Allow notification access") }
                            }
                            Button(
                                enabled = listenerOn,
                                onClick = {
                                    val ok = GatesNotificationListener.snapshot(context)
                                    Toast.makeText(context, if (ok) "Snapshot added to the log" else "Listener not connected yet", Toast.LENGTH_SHORT).show()
                                },
                            ) { Text("Snapshot notifications") }
                        }
                    }
                }
            }

            item { SectionTitle("4 · Anything else you noticed") }
            item {
                var note by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(note, { note = it }, Modifier.weight(1f), label = { Text("Note for the log") })
                    TextButton(enabled = note.isNotBlank(), onClick = { DiagLog.add(context, "note", note); note = "" }) { Text("Add") }
                }
            }

            item { SectionTitle("5 · Send the report") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { DiagLog.share(context) }) { Text("Share report") }
                    OutlinedButton(onClick = {
                        val f = DiagLog.export(context)
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Gates report", f.readText()))
                        Toast.makeText(context, "Copied. Also saved to ${f.absolutePath}", Toast.LENGTH_LONG).show()
                    }) { Text("Copy + save file") }
                    TextButton(onClick = { DiagLog.clear(context) }) { Text("Clear log") }
                }
                Text(
                    "Or over USB: adb pull /sdcard/Android/data/${context.packageName}/files/gates-island-report.txt  ·  live: adb logcat -s GatesDiag",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            item { SectionTitle("Log (latest last)") }
            items(log.takeLast(60)) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantCard(v: IslandTests.Variant) {
    val context = LocalContext.current
    var island by remember { mutableStateOf(false) }
    var chip by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var posted by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(v.title, style = MaterialTheme.typography.titleSmall)
            Text(v.what, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { IslandTests.post(context, v); posted = true }) { Text(if (posted) "Post again" else "Post") }
                FilterChip(island, { island = !island }, label = { Text("In island") })
                FilterChip(chip, { chip = !chip }, label = { Text("Status-bar chip") })
            }
            if (posted) {
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("What did it look like? (optional)") })
                TextButton(onClick = {
                    IslandTests.record(context, v, island, chip, notes)
                    Toast.makeText(context, "Recorded", Toast.LENGTH_SHORT).show()
                }) { Text("Record result") }
            }
        }
    }
}
