package dev.pridgey.gates.timer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import dev.pridgey.gates.Gates
import dev.pridgey.gates.R
import dev.pridgey.gates.access.GateAccessibilityService
import dev.pridgey.gates.diag.LiveUpdates
import dev.pridgey.gates.island.GatesIslandService
import dev.pridgey.gates.data.Session
import dev.pridgey.gates.data.TimerDisplay
import dev.pridgey.gates.store
import kotlin.math.abs

/**
 * Keeps the countdown for the current timed session.
 *
 * Shows it as an ongoing notification (which Android 16 can promote to a status-bar
 * chip) and, when overlay permission is granted, as Gates' own overhead pill.
 * At zero it opens the time's-up sheet.
 *
 * Island integration is still an open question; see diag/ for the probes.
 */
class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var pill: TextView? = null
    private var timesUpShownFor: Long = 0L

    private val tick = object : Runnable {
        override fun run() {
            val session = store.state.value.session
            if (session == null) {
                stopSelf()
                return
            }
            val left = session.endsAt - System.currentTimeMillis()
            updatePill(session, left)
            if (left <= 0 && timesUpShownFor != session.endsAt) {
                timesUpShownFor = session.endsAt
                if (store.state.value.settings.stillActive()) {
                    // STILL-4: fade first, then the sheet.
                    SoftExit.start(this@TimerService)
                    handler.postDelayed({ showTimesUp(session) }, SoftExit.DURATION_MS)
                } else {
                    showTimesUp(session)
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END -> {
                Gates.endSession(this)
                Gates.goHome(this)
                return START_NOT_STICKY
            }
            ACTION_PLUS5 -> {
                val left = Gates.remaining(this, store.state.value.session?.pkg ?: "")
                Gates.extendSession(this, minOf(5, left ?: 5).coerceAtLeast(1))
                return START_NOT_STICKY
            }
        }
        val session = store.state.value.session
        if (session == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannels()
        val notification = sessionNotification(session)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_SESSION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_SESSION, notification)
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIF_TIMES_UP)
        SoftExit.stop(this)
        LiveUpdates.logEligibility(this, "session", notification)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        removePill()
        SoftExit.stop(this)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_TIMES_UP)
        super.onDestroy()
    }

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SESSION, "Session timer", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TIMES_UP, "Time's up", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun sessionNotification(s: Session): Notification {
        val end = PendingIntent.getService(
            this, 1, Intent(this, TimerService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val plus = PendingIntent.getService(
            this, 2, Intent(this, TimerService::class.java).setAction(ACTION_PLUS5),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // On the island (priority 1) the notification stays plain: a chronometer would make
        // Essentials show a second, notification-based timer next to ours.
        val onIsland = GatesIslandService.attached.value && islandAllowed()
        val builder = Notification.Builder(this, CH_SESSION)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(s.label)
            .setContentText(
                if (onIsland) "Timer is on the island" + (s.reason?.let { " · “$it”" } ?: "")
                else s.reason?.let { "“$it”" } ?: "Timed session",
            )
            .setUsesChronometer(!onIsland)
            .setChronometerCountDown(true)
            .setWhen(s.endsAt)
            .setShowWhen(!onIsland)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .addAction(Notification.Action.Builder(null, "End now", end).build())
            .addAction(Notification.Action.Builder(null, "+5 min", plus).build())
        // Android 16 Live Updates: ask to be promoted to a status-bar chip (TIMER-2).
        val mode = store.state.value.settings.timerDisplay
        if (!onIsland && mode != TimerDisplay.OFF && mode != TimerDisplay.OVERHEAD_PILL && mode != TimerDisplay.ISLAND) {
            LiveUpdates.requestPromotion(builder, shortText = null)
        }
        return builder.build()
    }

    private fun showTimesUp(s: Session) {
        val intent = Intent(this, TimesUpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val pi = PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH_TIMES_UP)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Time's up · ${s.label}")
            .setContentText(s.reason?.let { "You came to: $it" } ?: "Tap to close or add time")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_TIMES_UP, n)
        // The accessibility service, or overlay permission, lets us open the sheet over the app.
        if (!GateAccessibilityService.startFromBackground(this, intent) && Settings.canDrawOverlays(this)) {
            runCatching { startActivity(intent) }
        }
    }

    private fun islandAllowed(): Boolean {
        val mode = store.state.value.settings.timerDisplay
        return mode == TimerDisplay.AUTOMATIC || mode == TimerDisplay.ISLAND
    }

    // --- Overhead pill (TIMER-3, TIMER-4) ---

    private fun wantsPill(): Boolean {
        val mode = store.state.value.settings.timerDisplay
        // TIMER-1: never over full-screen content. Automatic uses the pill only when
        // the status-bar chip isn't available.
        if (GateAccessibilityService.fullscreen.value) return false
        if (GatesIslandService.attached.value && islandAllowed()) return false
        val wanted = mode == TimerDisplay.OVERHEAD_PILL ||
            (mode == TimerDisplay.AUTOMATIC && !LiveUpdates.canPostPromoted(this))
        return wanted && Settings.canDrawOverlays(this)
    }

    private fun updatePill(s: Session, leftMs: Long) {
        if (!wantsPill() || leftMs <= 0) {
            removePill()
            return
        }
        val view = pill ?: createPill() ?: return
        val secs = (leftMs / 1000).toInt()
        view.text = "${s.label} · %d:%02d".format(secs / 60, secs % 60)
        val lastMinute = secs < 60
        (view.background as GradientDrawable).setColor(if (lastMinute) ACCENT else PILL_BG)
        view.setTextColor(if (lastMinute) 0xFF1B1B1B.toInt() else 0xFFFFFFFF.toInt())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createPill(): TextView? {
        val wm = getSystemService(WindowManager::class.java)
        val dp = resources.displayMetrics.density
        val statusBar = resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it != 0 }?.let { resources.getDimensionPixelSize(it) } ?: (24 * dp).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBar + (4 * dp).toInt()
        }
        val view = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 100 * dp; setColor(PILL_BG) }
            setTextColor(0xFFFFFFFF.toInt())
        }
        // Drag left or right along the top edge; a tap ends the session (TIME-1).
        var downX = 0f
        var startX = 0
        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; startX = params.x }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downX).toInt()
                    wm.updateViewLayout(v, params)
                }
                MotionEvent.ACTION_UP -> if (abs(e.rawX - downX) < 10 * dp) {
                    Gates.endSession(this)
                    Gates.goHome(this)
                }
            }
            true
        }
        return runCatching { wm.addView(view, params); view }.getOrNull()?.also { pill = it }
    }

    private fun removePill() {
        pill?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        pill = null
    }

    companion object {
        private const val CH_SESSION = "session"
        private const val CH_TIMES_UP = "times_up"
        private const val NOTIF_SESSION = 1
        private const val NOTIF_TIMES_UP = 2
        private const val ACTION_END = "end"
        private const val ACTION_PLUS5 = "plus5"
        private const val PILL_BG = 0xE6202020.toInt()
        private const val ACCENT = 0xFFF2C14E.toInt()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TimerService::class.java))
        }

        /** Re-post the session notification (e.g. the island connected or left). */
        fun refresh(context: Context) {
            if (context.store.state.value.session != null) start(context)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}
