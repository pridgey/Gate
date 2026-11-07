package dev.pridgey.gates

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.pridgey.gates.data.Days
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.data.Session
import dev.pridgey.gates.data.Store
import dev.pridgey.gates.data.Usage
import dev.pridgey.gates.timer.TimerService

class GatesApp : Application() {
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        store.ensureDay()
    }
}

val Context.store: Store get() = (applicationContext as GatesApp).store

/** Shared actions used by the launcher, the timer service and the time's-up sheet. */
object Gates {
    /** Minutes used today per package: usage stats when allowed, else timed-session totals. */
    fun usage(context: Context): Map<String, Int> {
        val store = context.store
        store.ensureDay()
        val s = store.state.value
        return if (Usage.hasAccess(context)) {
            Usage.minutesSince(context, s.ledger.dayStart)
        } else {
            s.ledger.sessionMinutes
        }
    }

    fun remaining(context: Context, pkg: String): Int? =
        Limits.remaining(context.store.state.value, pkg, usage(context))

    fun startSession(context: Context, pkg: String, label: String, minutes: Int, reason: String?) {
        val now = System.currentTimeMillis()
        context.store.update {
            it.copy(session = Session(pkg, label, now, now + minutes * 60_000L, reason))
        }
        TimerService.start(context)
    }

    fun extendSession(context: Context, minutes: Int) {
        context.store.update { s ->
            val session = s.session ?: return@update s
            val base = maxOf(session.endsAt, System.currentTimeMillis())
            s.copy(session = session.copy(endsAt = base + minutes * 60_000L))
        }
        TimerService.start(context)
    }

    /** End the timed session, counting its minutes for the no-usage-access fallback. */
    fun endSession(context: Context) {
        context.store.update { s ->
            val session = s.session ?: return@update s
            val end = minOf(System.currentTimeMillis(), session.endsAt)
            val used = ((end - session.startedAt) / 60_000L).toInt().coerceAtLeast(0)
            val sm = s.ledger.sessionMinutes
            s.copy(
                session = null,
                ledger = s.ledger.copy(sessionMinutes = sm + (session.pkg to (sm[session.pkg] ?: 0) + used)),
            )
        }
        TimerService.stop(context)
    }

    fun goHome(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun backAt(context: Context): String = "Back at ${Days.formatTime(context.store.state.value.settings.resetMinutes)}"

    fun appInfo(context: Context, pkg: String) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
