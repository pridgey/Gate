package dev.pridgey.gates

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.gates.diag.DiagLog
import dev.pridgey.gates.ui.GatesTheme
import dev.pridgey.gates.ui.GatesRoot
import dev.pridgey.gates.ui.GatesViewModel

class MainActivity : ComponentActivity() {
    private val vm: GatesViewModel by viewModels()

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vm.reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerReceiver(
            packageChanges,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        )
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            GatesTheme(still = state.settings.stillActive()) {
                GatesRoot(vm, onAuth = ::authenticate)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onDestroy() {
        unregisterReceiver(packageChanges)
        super.onDestroy()
    }

    /** The system biometric prompt, falling back to PIN. No extra screen. */
    private fun authenticate(label: String, onSuccess: () -> Unit) {
        runCatching {
            BiometricPrompt.Builder(this)
                .setTitle(label)
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
                .build()
                .authenticate(
                    CancellationSignal(),
                    mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                        override fun onAuthenticationError(code: Int, msg: CharSequence) {
                            if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
        }.onFailure { e ->
            // Never crash here: a crash would leave lending stuck on.
            DiagLog.add(this, "auth", "prompt failed for $label: $e")
            Toast.makeText(this, "Couldn't ask for your fingerprint", Toast.LENGTH_SHORT).show()
        }
    }
}
