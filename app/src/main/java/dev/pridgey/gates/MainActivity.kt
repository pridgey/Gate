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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.gates.ui.GatesTheme
import dev.pridgey.gates.ui.LauncherRoot
import dev.pridgey.gates.ui.LauncherViewModel
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

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
                BackHandler { /* Back on home does nothing, as on other launchers. */ }
                LauncherRoot(vm, homePresses, onVault = ::authenticate)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onHomeResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home: close any open panel.
        homePresses.tryEmit(Unit)
    }

    override fun onDestroy() {
        unregisterReceiver(packageChanges)
        super.onDestroy()
    }

    /** GATE-4: the system biometric prompt, falling back to PIN. No extra screen. */
    private fun authenticate(label: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt.Builder(this)
            .setTitle("Open $label")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(
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
    }
}
