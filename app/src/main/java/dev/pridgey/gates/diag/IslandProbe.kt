package dev.pridgey.gates.diag

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import org.xmlpull.v1.XmlPullParser

/**
 * Finds out how a dynamic-island app (such as Essentials) could receive Gates' timer.
 *
 * Collects: device and Live Updates support, every app that holds notification access
 * or an accessibility service, apps whose name looks island-like, and for each candidate
 * its full manifest (exported components, intent actions, meta-data, permissions).
 * A public API would show up as an exported service/receiver/provider with a custom
 * action or authority; a notification-reading island shows up as a notification listener.
 */
object IslandProbe {
    private val keywords = listOf("island", "essential", "notch", "dynamic", "pill", "spot", "cutout", "live")

    data class Candidate(val pkg: String, val label: String, val reasons: List<String>)

    fun candidates(context: Context): List<Candidate> {
        val pm = context.packageManager
        val listeners = enabledComponents(context, "enabled_notification_listeners")
        val a11y = enabledComponents(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val listenerApps = pm.queryIntentServices(Intent("android.service.notification.NotificationListenerService"), 0)
            .map { it.serviceInfo.packageName }.toSet()
        val reasons = HashMap<String, MutableList<String>>()
        fun add(pkg: String, why: String) { reasons.getOrPut(pkg) { mutableListOf() } += why }

        listeners.forEach { add(it.packageName, "notification access ON (${it.shortClassName})") }
        a11y.forEach { add(it.packageName, "accessibility ON (${it.shortClassName})") }
        pm.getInstalledApplications(0).forEach { ai ->
            val label = pm.getApplicationLabel(ai).toString()
            val hay = "${ai.packageName} $label".lowercase()
            keywords.filter { it in hay }.takeIf { it.isNotEmpty() }?.let { add(ai.packageName, "name matches ${it.joinToString()}") }
            if (ai.packageName in listenerApps && ai.packageName !in listeners.map { it.packageName }) {
                add(ai.packageName, "has notification listener (not enabled)")
            }
        }
        return reasons.filterKeys { it != context.packageName }.map { (pkg, why) ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            Candidate(pkg, label, why)
        }.sortedByDescending { c -> c.reasons.count { "ON" in it } * 10 + c.reasons.size }
    }

    /** Most likely island apps: name matches island/essential AND reads notifications or the screen. */
    fun likelyIslands(context: Context) = candidates(context).filter { c ->
        c.reasons.any { "island" in it || "essential" in it || "notch" in it || "dynamic" in it } &&
            c.reasons.any { "notification" in it || "accessibility" in it }
    }

    private var cache: Pair<Long, Set<String>>? = null

    /** Package names of likely island apps, cached for a minute. */
    fun islandPackages(context: Context): Set<String> {
        val now = System.currentTimeMillis()
        cache?.let { (at, set) -> if (now - at < 60_000) return set }
        return likelyIslands(context).map { it.pkg }.toSet().also { cache = now to it }
    }

    private fun enabledComponents(context: Context, key: String): List<ComponentName> =
        (Settings.Secure.getString(context.contentResolver, key) ?: "")
            .split(':').filter { it.isNotBlank() }.mapNotNull { ComponentName.unflattenFromString(it) }

    fun report(context: Context): String = buildString {
        val pm = context.packageManager
        appendLine("## Device")
        appendLine("manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} model=${Build.MODEL} device=${Build.DEVICE}")
        appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} build=${Build.DISPLAY} patch=${Build.VERSION.SECURITY_PATCH}")
        appendLine("fingerprint=${Build.FINGERPRINT}")
        appendLine()
        appendLine("## Live Updates")
        appendLine("canPostPromotedNotifications=${LiveUpdates.canPostPromotedOrNull(context)} (null = API not present)")
        appendLine("POST_NOTIFICATIONS granted=${context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED}")
        val promoSettings = Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS")
        appendLine("promotion settings screen resolvable=${promoSettings.resolveActivity(pm) != null}")
        appendLine()
        appendLine("## Essentials island provider")
        appendLine("essentials=${dev.pridgey.gates.island.GatesIslandService.essentialsVersion(context) ?: "not installed"}")
        appendLine("paired=${dev.pridgey.gates.island.GatesIslandService.paired(context)} attached=${dev.pridgey.gates.island.GatesIslandService.attached.value}")
        appendLine("gatesSigningKey=${com.sameerasw.island.sdk.IslandIdentity.fingerprint(context, context.packageName)?.let { com.sameerasw.island.sdk.IslandIdentity.shortFingerprint(it) }}")
        appendLine()
        appendLine("## Gates permissions")
        appendLine("overlay=${Settings.canDrawOverlays(context)}")
        appendLine("writeSecureSettings=${context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED}")
        appendLine("gatesNotificationListener=${GatesNotificationListener.isEnabled(context)}")
        appendLine()

        val cands = candidates(context)
        appendLine("## Candidates (${cands.size})")
        cands.forEach { c -> appendLine("- ${c.label} [${c.pkg}]: ${c.reasons.joinToString("; ")}") }
        appendLine()

        // Full manifests only for island-like apps, to keep the report readable.
        val deep = cands.filter { c -> c.reasons.any { r -> keywords.any { it in r } } }.take(8)
        deep.forEach { c ->
            appendLine("## Manifest: ${c.label} [${c.pkg}]")
            append(describePackage(context, c.pkg))
            appendLine()
        }
    }

    private fun describePackage(context: Context, pkg: String): String = buildString {
        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
        val info: PackageInfo = runCatching { pm.getPackageInfo(pkg, flags) }.getOrElse {
            appendLine("  (could not read package: $it)")
            return@buildString
        }
        appendLine("  version=${info.versionName} (${info.longVersionCode}) targetSdk=${info.applicationInfo?.targetSdkVersion}")
        appendLine("  installer=${runCatching { pm.getInstallSourceInfo(pkg).installingPackageName }.getOrNull()}")
        info.applicationInfo?.metaData?.let { md ->
            md.keySet().forEach { appendLine("  app meta-data: $it=${md.get(it)}") }
        }
        info.permissions?.forEach { appendLine("  DECLARES permission: ${it.name} (protection=${it.protection})") }
        info.requestedPermissions?.let { appendLine("  requests: ${it.joinToString()}") }
        info.services?.filter { it.exported }?.forEach { appendLine("  exported service: ${it.name} perm=${it.permission}") }
        info.receivers?.filter { it.exported }?.forEach { appendLine("  exported receiver: ${it.name} perm=${it.permission}") }
        info.providers?.filter { it.exported }?.forEach {
            appendLine("  exported provider: ${it.name} authority=${it.authority} read=${it.readPermission} write=${it.writePermission}")
        }
        appendLine("  -- manifest intent filters and meta-data --")
        append(manifestFilters(context, pkg))
    }

    /** Reads the other app's compiled AndroidManifest.xml to list intent actions and meta-data. */
    private fun manifestFilters(context: Context, pkg: String): String = buildString {
        val parser = runCatching {
            context.createPackageContext(pkg, 0).assets.openXmlResourceParser("AndroidManifest.xml")
        }.getOrElse {
            appendLine("  (manifest not readable: $it)")
            return@buildString
        }
        val components = setOf("activity", "activity-alias", "service", "receiver", "provider")
        var current: String? = null
        var exported: String? = null
        val lines = mutableListOf<String>()
        fun attr(name: String): String? = (0 until parser.attributeCount)
            .firstOrNull { parser.getAttributeName(it) == name }?.let { parser.getAttributeValue(it) }
        runCatching {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (val tag = parser.name) {
                        in components -> {
                            current = "$tag ${attr("name")}"
                            exported = attr("exported")
                            lines.clear()
                        }
                        "action", "category" -> lines += "$tag ${attr("name")}"
                        "data" -> lines += "data " + listOfNotNull(attr("scheme"), attr("host"), attr("mimeType"), attr("path")).joinToString(" ")
                        "meta-data" -> lines += "meta-data ${attr("name")}=${attr("value") ?: attr("resource")}"
                        else -> if (current != null && tag != "intent-filter") lines += tag
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.name in components) {
                    // Skip plain launcher activities; anything else may be an integration point.
                    val boring = lines.all { it == "action android.intent.action.MAIN" || it == "category android.intent.category.LAUNCHER" }
                    if (lines.isNotEmpty() && !boring) {
                        appendLine("  $current exported=$exported")
                        lines.forEach { appendLine("      $it") }
                    }
                    current = null
                }
            }
        }.onFailure { appendLine("  (parse stopped: $it)") }
        parser.close()
    }
}
