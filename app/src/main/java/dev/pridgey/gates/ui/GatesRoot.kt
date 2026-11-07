package dev.pridgey.gates.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.gates.Gates
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.data.LaunchApp

/**
 * The Gates app: the gates screen, per-app options, diagnostics and first run.
 * [onAuth] shows the system biometric prompt and runs its callback on success.
 */
@Composable
fun GatesRoot(vm: GatesViewModel, onAuth: (label: String, onSuccess: () -> Unit) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val hasUsage by vm.hasUsageAccess.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var diagnostics by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LaunchApp?>(null) }

    // Status and nav bar icons follow the surface: dark icons only on the light theme.
    val lightSurface = !isSystemInDarkTheme() && !LocalStill.current
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightSurface
            isAppearanceLightNavigationBars = lightSurface
        }
    }

    // LEND-1: while lending, Gates itself stays locked so its settings can't be changed.
    if (state.settings.lending) {
        LendingLock { onAuth("End lending") { vm.updateSettings { it.copy(lending = false) } } }
        return
    }

    Box(Modifier.fillMaxSize()) {
        GatesScreen(
            vm = vm,
            state = state,
            apps = apps,
            usage = usage,
            hasUsageAccess = hasUsage,
            onEditApp = { editing = it },
            onDiagnostics = { diagnostics = true },
            onAuth = onAuth,
        )
        AnimatedVisibility(
            diagnostics,
            enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 8 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(180)) { it / 12 } + fadeOut(tween(160)),
        ) {
            BackHandler { diagnostics = false }
            DiagnosticsScreen(onClose = { diagnostics = false })
        }
    }

    editing?.let { app ->
        AppOptionsSheet(
            app = app,
            config = state.config(app.pkg),
            groups = state.groups,
            remaining = Limits.remaining(state, app.pkg, usage),
            onChange = { vm.updateApp(app.pkg, it) },
            onNewGroup = vm::newGroup,
            onAppInfo = { Gates.appInfo(context, app.pkg) },
            onDismiss = { editing = null },
        )
    }

    if (!state.settings.onboarded) {
        Onboarding(
            onDone = { resetMinutes, stillOn ->
                vm.updateSettings { it.copy(resetMinutes = resetMinutes, stillMode = stillOn, onboarded = true) }
            },
        )
    }
}

@Composable
private fun LendingLock(onEnd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(72.dp))
        Eyebrow("Lending")
        Text("The phone is lent out", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Vault apps and the apps you chose are hidden. Ending lending needs your fingerprint.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("End lending", Modifier.fillMaxWidth().padding(bottom = 16.dp), onClick = onEnd)
    }
}
