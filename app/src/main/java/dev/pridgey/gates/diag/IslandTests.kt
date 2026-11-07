package dev.pridgey.gates.diag

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import dev.pridgey.gates.R

/**
 * Test notifications, each a different way a timer could reach a dynamic island or the
 * Android 16 status-bar chip. Post one, look at the island and status bar, then record
 * what you saw. Every post logs the notification's shape and promotion eligibility.
 */
object IslandTests {
    data class Variant(val key: String, val title: String, val what: String)

    val variants = listOf(
        Variant("chrono", "Ongoing countdown", "What Gates uses today: ongoing + countdown chronometer, CATEGORY_PROGRESS."),
        Variant("promoted", "Live Update request", "Countdown + requestPromotedOngoing + shortCriticalText (Android 16)."),
        Variant("progress", "ProgressStyle", "Android 16 ProgressStyle + promotion request + countdown."),
        Variant("media", "Media session", "A playing MediaSession with MediaStyle; title carries the time left."),
        Variant("headsup", "High-importance alarm", "Ongoing countdown on a high-importance channel, CATEGORY_ALARM."),
        Variant("stopwatch", "Stopwatch, colorized", "Colorized ongoing countdown with CATEGORY_STOPWATCH."),
        Variant("text", "Text that updates", "No chronometer; the text is rewritten every second (\"Video · 4:12\")."),
    )

    private const val CH_DEFAULT = "island_test"
    private const val CH_HIGH = "island_test_high"
    private const val BASE_ID = 1000
    private const val DURATION_MS = 5 * 60_000L

    private var media: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var textTicker: Runnable? = null

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DEFAULT, "Island tests", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null); enableVibration(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_HIGH, "Island tests (high)", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(false)
        })
    }

    fun post(context: Context, v: Variant) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val id = BASE_ID + variants.indexOf(v)
        val end = System.currentTimeMillis() + DURATION_MS
        val notes = mutableListOf<String>()

        fun base(channel: String = CH_DEFAULT) = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Video")
            .setContentText("Gates island test · ${v.title}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        fun countdown(b: Notification.Builder) = b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(end).setShowWhen(true)

        val n: Notification = when (v.key) {
            "chrono" -> countdown(base()).setCategory(Notification.CATEGORY_PROGRESS).build()
            "promoted" -> countdown(base()).setCategory(Notification.CATEGORY_PROGRESS)
                .also { notes += LiveUpdates.requestPromotion(it, "5:00") }.build()
            "progress" -> countdown(base()).setCategory(Notification.CATEGORY_PROGRESS).also { b ->
                val ok = runCatching {
                    val style = Class.forName("android.app.Notification\$ProgressStyle").getConstructor().newInstance()
                    style.javaClass.getMethod("setProgress", Int::class.javaPrimitiveType).invoke(style, 0)
                    b.setStyle(style as Notification.Style)
                }
                notes += "ProgressStyle ${if (ok.isSuccess) "set" else "unavailable: ${ok.exceptionOrNull()?.javaClass?.simpleName}"}"
                notes += LiveUpdates.requestPromotion(b, "5:00")
            }.build()
            "media" -> {
                val session = media ?: MediaSession(context, "GatesIslandTest").also { media = it }
                session.setMetadata(
                    MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, "Video · 5:00 left")
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, "Gates")
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, DURATION_MS)
                        .build(),
                )
                session.setPlaybackState(
                    PlaybackState.Builder()
                        .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                        .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                        .build(),
                )
                session.isActive = true
                base().setCategory(Notification.CATEGORY_TRANSPORT)
                    .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
                    .build()
            }
            "headsup" -> countdown(base(CH_HIGH)).setCategory(Notification.CATEGORY_ALARM).build()
            "stopwatch" -> countdown(base()).setCategory("stopwatch").setColorized(true).setColor(Color.rgb(31, 77, 58)).build()
            "text" -> {
                startTextTicker(context, id, end)
                textNotification(context, end)
            }
            else -> return
        }
        nm.notify(id, n)
        DiagLog.add(context, "test", "posted ${v.key} (id=$id) ${notes.joinToString("; ")}")
        LiveUpdates.logEligibility(context, "test ${v.key}", n)
        DiagLog.add(context, "test", "extras ${v.key}: ${n.extras.keySet().sorted().joinToString()}")
        // What the system actually kept (flags can change after posting, e.g. promotion).
        handler.postDelayed({
            val active = nm.activeNotifications.firstOrNull { it.id == id }
            DiagLog.add(
                context, "test",
                if (active == null) "${v.key}: NOT active after 1.5s (blocked or dismissed?)"
                else "${v.key}: active flags=0x${Integer.toHexString(active.notification.flags)} promotable=${LiveUpdates.promotable(active.notification)}",
            )
        }, 1_500)
    }

    private fun textNotification(context: Context, end: Long): Notification {
        val left = ((end - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        return Notification.Builder(context, CH_DEFAULT)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Video · %d:%02d".format(left / 60, left % 60))
            .setContentText("Gates island test · Text that updates")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .build()
    }

    private fun startTextTicker(context: Context, id: Int, end: Long) {
        textTicker?.let(handler::removeCallbacks)
        val nm = context.getSystemService(NotificationManager::class.java)
        textTicker = object : Runnable {
            override fun run() {
                if (System.currentTimeMillis() > end || nm.activeNotifications.none { it.id == id }) return
                nm.notify(id, textNotification(context, end))
                handler.postDelayed(this, 1_000)
            }
        }.also { handler.postDelayed(it, 1_000) }
    }

    fun record(context: Context, v: Variant, island: Boolean, chip: Boolean, notes: String) {
        DiagLog.add(context, "result", "${v.key}: island=${yn(island)} statusChip=${yn(chip)}${if (notes.isNotBlank()) " notes=\"$notes\"" else ""}")
    }

    private fun yn(b: Boolean) = if (b) "YES" else "no"

    fun clearAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        variants.indices.forEach { nm.cancel(BASE_ID + it) }
        textTicker?.let(handler::removeCallbacks)
        media?.run { isActive = false; release() }
        media = null
        DiagLog.add(context, "test", "cleared test notifications")
    }
}
