package dev.pridgey.gates.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.gates.update.Updater
import kotlinx.coroutines.launch

/** Short label for the Updates row in settings. */
fun updateLabel(state: Updater.State): String = when (state) {
    Updater.State.Idle -> "Check now"
    Updater.State.Checking -> "Checking…"
    is Updater.State.UpToDate -> "Up to date"
    is Updater.State.Available -> "${state.release.versionName} available"
    is Updater.State.Downloading -> "Downloading ${(state.progress * 100).toInt()}%"
    is Updater.State.Installing -> "Installing…"
    is Updater.State.Failed -> "Try again"
}

@Composable
fun UpdateSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsStateWithLifecycle()
    val current = Updater.currentVersionName(context)

    // Opening the sheet always checks, unless something is already happening.
    LaunchedEffect(Unit) {
        if (state is Updater.State.Idle || state is Updater.State.UpToDate || state is Updater.State.Failed) Updater.check(context)
    }

    GSheet(onDismiss = onDismiss) {
        Eyebrow("Updates")
        when (val s = state) {
            is Updater.State.Available, is Updater.State.Downloading, is Updater.State.Installing -> {
                val release = when (s) {
                    is Updater.State.Available -> s.release
                    is Updater.State.Downloading -> s.release
                    is Updater.State.Installing -> s.release
                    else -> null
                }!!
                SheetTitle("Gates ${release.versionName}", "You have $current.")
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        Text(release.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(28.dp))
                when {
                    s is Updater.State.Downloading -> {
                        Meter(s.progress)
                        Text(
                            "Downloading… ${(s.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    s is Updater.State.Installing -> Text(
                        "Confirm the install when Android asks. Gates will restart.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    !Updater.canInstall(context) -> {
                        Text(
                            "Android needs your OK for Gates to install its own updates. Turn on “Allow from this source”, then come back.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton("Allow updates", Modifier.fillMaxWidth()) { Updater.openInstallPermission(context) }
                    }
                    else -> PrimaryButton("Update", Modifier.fillMaxWidth()) {
                        scope.launch { Updater.downloadAndInstall(context, release) }
                    }
                }
            }
            Updater.State.Checking, Updater.State.Idle -> {
                SheetTitle("Checking for updates…", "You have $current.")
                Spacer(Modifier.height(28.dp))
            }
            is Updater.State.UpToDate -> {
                SheetTitle("You're up to date", "Gates $current is the latest.")
                Spacer(Modifier.height(28.dp))
                TonalButton("Check again", Modifier.fillMaxWidth()) { scope.launch { Updater.check(context) } }
            }
            is Updater.State.Failed -> {
                SheetTitle("Update didn't finish", s.message)
                Spacer(Modifier.height(28.dp))
                PrimaryButton("Try again", Modifier.fillMaxWidth()) {
                    scope.launch {
                        val r = s.release
                        if (r != null && Updater.canInstall(context)) Updater.downloadAndInstall(context, r) else Updater.check(context)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        QuietButton("Close", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}
