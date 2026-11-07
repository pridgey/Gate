package dev.pridgey.gates.diag

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Android 16 Live Updates (promoted ongoing notifications → status-bar chip).
 * We compile against API 35, so the API 36 calls go through reflection and
 * every result is logged for the investigation.
 */
object LiveUpdates {
    const val EXTRA_REQUEST_PROMOTED = "android.requestPromotedOngoing"

    /** NotificationManager.canPostPromotedNotifications() (API 36), or null if unavailable. */
    fun canPostPromotedOrNull(context: Context): Boolean? = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.javaClass.getMethod("canPostPromotedNotifications").invoke(nm) as Boolean
    }.getOrNull()

    fun canPostPromoted(context: Context) = canPostPromotedOrNull(context) == true

    /** setRequestPromotedOngoing(true) and, if given, setShortCriticalText(text). */
    fun requestPromotion(builder: Notification.Builder, shortText: String?): List<String> {
        val notes = mutableListOf<String>()
        val viaMethod = runCatching {
            builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).invoke(builder, true)
        }.isSuccess
        if (!viaMethod) builder.addExtras(android.os.Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED, true) })
        notes += "requestPromotedOngoing via ${if (viaMethod) "method" else "extra"}"
        if (shortText != null) {
            val ok = runCatching {
                builder.javaClass.getMethod("setShortCriticalText", String::class.java).invoke(builder, shortText)
            }.isSuccess
            notes += "setShortCriticalText ${if (ok) "ok" else "unavailable"}"
        }
        return notes
    }

    /** Notification.hasPromotableCharacteristics() (API 36), or null. */
    fun promotable(n: Notification): Boolean? = runCatching {
        n.javaClass.getMethod("hasPromotableCharacteristics").invoke(n) as Boolean
    }.getOrNull()

    fun logEligibility(context: Context, what: String, n: Notification) {
        DiagLog.add(
            context, "live",
            "$what: sdk=${Build.VERSION.SDK_INT} canPostPromoted=${canPostPromotedOrNull(context)} " +
                "promotable=${promotable(n)} flags=0x${Integer.toHexString(n.flags)} category=${n.category}",
        )
    }
}
