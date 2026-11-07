package dev.pridgey.gates.access

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import dev.pridgey.gates.Gates
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.diag.DiagLog
import dev.pridgey.gates.store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional accessibility service. It only looks at which package is in front and
 * whether the status bar is showing. It is used to:
 *  - gate apps opened from notifications, recents or other apps (GATE-5, SET-1)
 *  - hide the timer while the foreground app is full screen (TIMER-1)
 *  - start the time's-up sheet and Still mode's soft exit over other apps (STILL-4)
 */
class GateAccessibilityService : AccessibilityService() {
    private var lastPkg: String? = null
    private val ignored = mutableSetOf<String>()

    override fun onServiceConnected() {
        instance = this
        ignored += setOf("com.android.systemui", "android")
        getSystemService(InputMethodManager::class.java).inputMethodList.forEach { ignored += it.packageName }
        DiagLog.add(this, "a11y", "service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _fullscreen.value = false
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        updateFullscreen()
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored || pkg == lastPkg) return
        lastPkg = pkg
        _foreground.value = pkg
        onAppInFront(pkg)
    }

    private fun onAppInFront(pkg: String) {
        // Leaving an approved app means the next open needs its gate again.
        if (approved != null && approved != pkg) approved = null
        if (pkg == packageName || pkg == homePackage()) return

        val s = store.state.value
        // LEND-1: hidden apps can't be reached any other way either.
        if (Limits.hiddenWhileLending(s, pkg)) {
            DiagLog.add(this, "a11y", "lending: sent $pkg home")
            Gates.goHome(this)
            return
        }
        if (!s.settings.gateFromNotifications) return
        if (pkg == approved || s.session?.pkg == pkg) return
        val cfg = s.apps[pkg] ?: return
        val outOfTime = cfg.gate != GateType.VAULT && Limits.isOutOfTime(s, pkg, Gates.usage(this))
        if (cfg.gate == GateType.NONE && !outOfTime) return

        DiagLog.add(this, "a11y", "gating $pkg opened outside the launcher (gate=${cfg.gate}, outOfTime=$outOfTime)")
        startActivity(
            Intent(this, GateActivity::class.java)
                .putExtra(GateActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    /**
     * Full screen = no status-bar system window across the top. Heuristic: when an app
     * goes immersive, SystemUI's status-bar window drops out of the interactive list.
     */
    private fun updateFullscreen() {
        val list = runCatching { windows }.getOrNull() ?: return
        val r = Rect()
        val statusBarShown = list.any { w ->
            w.type == AccessibilityWindowInfo.TYPE_SYSTEM && run {
                w.getBoundsInScreen(r)
                r.top == 0 && r.height() in 1..(resources.displayMetrics.density * 80).toInt()
            }
        }
        if (_fullscreen.value != !statusBarShown) {
            _fullscreen.value = !statusBarShown
            DiagLog.add(this, "a11y", "fullscreen=${!statusBarShown} (front=$lastPkg)")
        }
    }

    private fun homePackage(): String? = packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
    )?.activityInfo?.packageName

    companion object {
        @Volatile
        var instance: GateAccessibilityService? = null
            private set

        /** A package the user just opened through its gate; not gated again until they leave it. */
        @Volatile
        var approved: String? = null

        private val _fullscreen = MutableStateFlow(false)
        val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()

        private val _foreground = MutableStateFlow<String?>(null)
        val foreground: StateFlow<String?> = _foreground.asStateFlow()

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, GateAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun openSettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        /** Start an activity from the background; the bound service is allowed to. */
        fun startFromBackground(context: Context, intent: Intent): Boolean {
            val svc = instance ?: return false
            return runCatching { svc.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
        }
    }
}
