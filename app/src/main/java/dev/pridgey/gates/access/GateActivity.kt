package dev.pridgey.gates.access

import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import dev.pridgey.gates.Gates
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.store
import dev.pridgey.gates.ui.GateSheet
import dev.pridgey.gates.ui.GatesTheme
import dev.pridgey.gates.ui.OutOfTimeSheet

/**
 * Shown over an app that was opened from a notification, recents or another app
 * (not through the launcher). Same gates as home (GATE-5); Cancel goes home.
 */
class GateActivity : ComponentActivity() {
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return finish()
        val app = dev.pridgey.gates.data.Apps.forPackage(this, pkg) ?: return finish()
        val state = store.state.value
        val cfg = state.config(pkg)
        val usage = Gates.usage(this)
        val remaining = Limits.remaining(state, pkg, usage)

        if (cfg.gate == GateType.VAULT) {
            vault(app)
            return
        }
        setContent {
            GatesTheme(still = state.settings.stillActive()) {
                BackHandler { cancel() }
                if (remaining == 0) {
                    OutOfTimeSheet(
                        app = app,
                        backAt = Gates.backAt(this),
                        onBorrow = { m ->
                            store.update { Limits.borrow(it, pkg, m) }
                            allow(pkg)
                        },
                        onDismiss = ::cancel,
                    )
                } else {
                    GateSheet(
                        app = app,
                        config = cfg,
                        remaining = remaining,
                        intro = null,
                        onPick = { minutes, reason ->
                            Gates.startSession(this, pkg, app.label, minutes, reason)
                            allow(pkg)
                        },
                        onSaveReason = { r ->
                            store.updateApp(pkg) { c -> if (c.reasons.any { it.label == r.label }) c else c.copy(reasons = c.reasons + r) }
                        },
                        onCancel = {
                            store.update { Limits.countCancel(it, pkg) }
                            cancel()
                        },
                    )
                }
            }
        }
    }

    private fun vault(app: LaunchApp) {
        BiometricPrompt.Builder(this)
            .setTitle("Open ${app.label}")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = allow(app.pkg)
                override fun onAuthenticationError(code: Int, msg: CharSequence) = cancel()
            })
    }

    private fun allow(pkg: String) {
        done = true
        GateAccessibilityService.approved = pkg
        finish()
    }

    private fun cancel() {
        if (done) return
        done = true
        Gates.goHome(this)
        finish()
    }

    /** Leaving the gate without choosing (home, recents) counts as Cancel. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        cancel()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}
