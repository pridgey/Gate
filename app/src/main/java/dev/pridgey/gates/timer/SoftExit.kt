package dev.pridgey.gates.timer

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import dev.pridgey.gates.access.GateAccessibilityService
import dev.pridgey.gates.diag.DiagLog

/**
 * STILL-4: at time's up, fade the screen toward gray over a few seconds before the sheet.
 *
 * True grayscale uses the system's monochrome color correction, which needs
 * WRITE_SECURE_SETTINGS (grant once with:
 * `adb shell pm grant dev.pridgey.gates android.permission.WRITE_SECURE_SETTINGS`).
 * Without it, a gray scrim fades in over the app instead (accessibility overlay when the
 * service is on, otherwise a normal overlay, otherwise nothing).
 */
object SoftExit {
    const val DURATION_MS = 3_000L
    private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val DALTONIZER = "accessibility_display_daltonizer"
    private const val MONOCHROME = 0

    private var scrim: View? = null
    private var saved: Pair<Int, Int>? = null

    fun canGrayscale(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun start(context: Context) {
        if (canGrayscale(context)) {
            runCatching {
                val cr = context.contentResolver
                if (saved == null) {
                    saved = Settings.Secure.getInt(cr, DALTONIZER_ENABLED, 0) to Settings.Secure.getInt(cr, DALTONIZER, -1)
                }
                Settings.Secure.putInt(cr, DALTONIZER, MONOCHROME)
                Settings.Secure.putInt(cr, DALTONIZER_ENABLED, 1)
                DiagLog.add(context, "still", "soft exit: system grayscale on")
            }.onFailure { DiagLog.add(context, "still", "grayscale failed: $it") }
            return
        }
        showScrim(context)
    }

    fun stop(context: Context) {
        saved?.let { (enabled, mode) ->
            runCatching {
                val cr = context.contentResolver
                if (mode >= 0) Settings.Secure.putInt(cr, DALTONIZER, mode)
                Settings.Secure.putInt(cr, DALTONIZER_ENABLED, enabled)
            }
            saved = null
        }
        scrim?.let { v ->
            runCatching { (v.context.getSystemService(WindowManager::class.java)).removeView(v) }
        }
        scrim = null
    }

    private fun showScrim(context: Context) {
        if (scrim != null) return
        val svc = GateAccessibilityService.instance
        val (host, type) = when {
            svc != null -> svc to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Settings.canDrawOverlays(context) -> context to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> return
        }
        val wm = host.getSystemService(WindowManager::class.java)
        val view = View(host).apply {
            setBackgroundColor(0xFF6E6E6E.toInt())
            alpha = 0f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { wm.addView(view, params) }.onSuccess {
            scrim = view
            ValueAnimator.ofFloat(0f, 0.7f).apply {
                duration = DURATION_MS
                addUpdateListener { view.alpha = it.animatedValue as Float }
                start()
            }
            DiagLog.add(context, "still", "soft exit: scrim fade")
        }
    }
}
