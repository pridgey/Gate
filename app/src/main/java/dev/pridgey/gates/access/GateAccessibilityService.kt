package dev.pridgey.gates.access

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
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
 * The accessibility service that makes gates work. It only looks at which package is
 * in front and whether the status bar is showing. It is used to:
 *  - show an app's gate whenever it opens, from anywhere (GATE-5)
 *  - end a running session when the user goes back to the home screen
 *  - hide the timer while the foreground app is full screen (TIMER-1)
 *  - start the time's-up sheet and Still mode's soft exit over other apps (STILL-4)
 */
class GateAccessibilityService : AccessibilityService() {
    private var lastPkg: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ignored = mutableSetOf<String>()

    override fun onServiceConnected() {
        instance = this
        ignored += setOf("com.android.systemui", "android")
        getSystemService(InputMethodManager::class.java).inputMethodList.forEach { ignored += it.packageName }
        DiagLog.add(this, "a11y", "service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacksAndMessages(null)
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
        if (pkg == packageName) return
        if (pkg == homePackage()) {
            endSessionIfStillAway()
            return
        }

        val s = store.state.value
        // LEND-1: hidden apps can't be reached any other way either.
        if (Limits.hiddenWhileLending(s, pkg)) {
            DiagLog.add(this, "a11y", "lending: sent $pkg home")
            Gates.goHome(this)
            return
        }
        if (pkg == approved || s.session?.pkg == pkg) return
        val cfg = s.apps[pkg] ?: return
        val outOfTime = cfg.gate != GateType.VAULT && Limits.isOutOfTime(s, pkg, Gates.usage(this))
        if (cfg.gate == GateType.NONE && !outOfTime) return

        DiagLog.add(this, "a11y", "gating $pkg (gate=${cfg.gate}, outOfTime=$outOfTime)")
        startActivity(
            Intent(this, GateActivity::class.java)
                .putExtra(GateActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    /**
     * Going home ends a running session; its time is counted. Recents usually belongs to the
     * home app too, so wait a moment: switching straight back into the app keeps its session.
     */
    private fun endSessionIfStillAway() {
        val session = store.state.value.session ?: return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            val now = store.state.value.session
            if (now != null && now.startedAt == session.startedAt && _foreground.value != now.pkg) {
                DiagLog.add(this, "a11y", "left ${now.pkg} via home: ended session")
                Gates.endSession(this)
            }
        }, HOME_GRACE_MS)
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
        private const val HOME_GRACE_MS = 5_000L

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
