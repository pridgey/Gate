package dev.pridgey.gates.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** Foreground time per app, from Android's usage stats. Gates never sees inside apps. */
object Usage {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Minutes in the foreground per package between [since] and [now]. */
    fun minutesSince(context: Context, since: Long, now: Long = System.currentTimeMillis()): Map<String, Int> {
        if (!hasAccess(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(since, now)
        val resumedAt = HashMap<String, Long>()
        val seen = HashSet<String>()
        val totals = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    resumedAt.putIfAbsent(pkg, e.timeStamp)
                    seen += pkg
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    // Paused with no resume seen: it was already open when the day began.
                    val start = resumedAt.remove(pkg) ?: if (pkg !in seen) since else null
                    seen += pkg
                    if (start != null) totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - start)
                }
            }
        }
        resumedAt.forEach { (pkg, start) -> totals[pkg] = (totals[pkg] ?: 0L) + (now - start) }
        return totals.mapValues { (it.value / 60_000L).toInt() }
    }
}
