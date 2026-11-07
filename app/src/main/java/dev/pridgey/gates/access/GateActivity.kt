package dev.pridgey.gates.access

import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import dev.pridgey.gates.Gates
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.diag.DiagLog
import dev.pridgey.gates.store
import dev.pridgey.gates.ui.GateSheet
import dev.pridgey.gates.ui.GatesTheme
import dev.pridgey.gates.ui.OutOfTimeSheet

/**
 * Shown over a gated app as it opens, from anywhere (GATE-5). Cancel goes home.
 */
class GateActivity : ComponentActivity() {
    private var done = false
    /** A Vault app waiting for the fingerprint prompt, asked once the gate is resumed. */
    private var vaultApp: LaunchApp? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return finish()
        val app = dev.pridgey.gates.data.Apps.forPackage(this, pkg) ?: return finish()
        val state = store.state.value
        val cfg = state.config(pkg)
        val usage = Gates.usage(this)
        val remaining = Limits.remaining(state, pkg, usage)

        if (cfg.gate == GateType.VAULT) {
            // The system can refuse a prompt from an activity that isn't on screen yet,
            // so ask in onResume rather than here.
            vaultApp = app
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

    override fun onResume() {
        super.onResume()
        val app = vaultApp ?: return
        vaultApp = null
        vault(app)
    }

    private fun vault(app: LaunchApp) {
        DiagLog.add(this, "vault", "asking for ${app.pkg}")
        val result = runCatching {
            BiometricPrompt.Builder(this)
                .setTitle("Open ${app.label}")
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
                .build()
                .authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        DiagLog.add(this@GateActivity, "vault", "unlocked ${app.pkg}")
                        allow(app.pkg)
                    }

                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        DiagLog.add(this@GateActivity, "vault", "error $code for ${app.pkg}: $msg")
                        if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                            Toast.makeText(this@GateActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                        cancel()
                    }
                })
        }
        result.exceptionOrNull()?.let { e ->
            DiagLog.add(this, "vault", "prompt failed for ${app.pkg}: $e")
            Toast.makeText(this, "Couldn't ask for your fingerprint", Toast.LENGTH_SHORT).show()
            cancel()
        }
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
