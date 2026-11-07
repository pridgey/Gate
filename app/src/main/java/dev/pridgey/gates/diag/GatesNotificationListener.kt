package dev.pridgey.gates.diag

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Diagnostics only, off unless the user grants notification access.
 *
 * Records the *shape* of notifications (category, flags, style, extras keys) so we can
 * compare one the island shows (e.g. a Clock timer) with Gates' own. Text is only kept
 * for clock/timer apps, Gates' own tests and island-like apps; everything else is
 * reduced to key names and lengths.
 */
class GatesNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        DiagLog.add(this, "listener", "connected")
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Live-log only the interesting sources; snapshot() covers the rest.
        if (keepText(this, sbn.packageName)) DiagLog.add(this, "posted", describe(this, sbn))
    }

    companion object {
        @Volatile
        private var instance: GatesNotificationListener? = null

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, GatesNotificationListener::class.java).flattenToString()
            return (Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: "")
                .split(':').any { it == me }
        }

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        /** Log every active notification's shape. Returns false if the listener isn't connected. */
        fun snapshot(context: Context): Boolean {
            val svc = instance ?: return false
            val active = runCatching { svc.activeNotifications }.getOrNull() ?: return false
            DiagLog.add(context, "snapshot", "${active.size} active notifications")
            active.forEach { DiagLog.add(context, "snapshot", describe(context, it)) }
            return true
        }

        private fun keepText(context: Context, pkg: String): Boolean {
            val p = pkg.lowercase()
            return pkg == context.packageName || "clock" in p || "timer" in p ||
                pkg in IslandProbe.islandPackages(context)
        }

        fun describe(context: Context, sbn: StatusBarNotification): String {
            val n = sbn.notification
            val e = n.extras
            val text = keepText(context, sbn.packageName)
            val extras = e.keySet().sorted().joinToString { k ->
                val v = e.get(k)
                when {
                    v == null -> "$k=null"
                    v is CharSequence && !text -> "$k=<${v.length} chars>"
                    v is CharSequence -> "$k=\"${v.toString().take(60)}\""
                    v is Boolean || v is Number -> "$k=$v"
                    else -> "$k:${v.javaClass.simpleName}"
                }
            }
            return buildString {
                append("${sbn.packageName} id=${sbn.id} tag=${sbn.tag} ")
                append("ongoing=${sbn.isOngoing} category=${n.category} channel=${n.channelId} ")
                append("flags=0x${Integer.toHexString(n.flags)} when=${n.`when`} ")
                append("fgs=${n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0} ")
                append("promotable=${LiveUpdates.promotable(n)} ")
                append("template=${e.getString(Notification.EXTRA_TEMPLATE)} ")
                append("extras{ $extras }")
            }
        }
    }
}
