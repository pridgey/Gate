package dev.pridgey.gates.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class LaunchApp(
    val pkg: String,
    val label: String,
    val component: ComponentName?,
    val icon: ImageBitmap?,
) {
    val isGates: Boolean get() = component == null
}

object Apps {
    /** Every app with a launcher entry, plus the Gates app itself as a virtual tile. */
    fun load(context: Context): List<LaunchApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val ai = ri.activityInfo
                LaunchApp(
                    pkg = ai.packageName,
                    label = ri.loadLabel(pm).toString(),
                    component = ComponentName(ai.packageName, ai.name),
                    icon = runCatching { ri.loadIcon(pm).toBitmap(144, 144).asImageBitmap() }.getOrNull(),
                )
            }
        return (apps + gatesTile(context)).sortedBy { it.label.lowercase() }
    }

    fun gatesTile(context: Context) = LaunchApp(context.packageName, "Gates", null, null)

    /** Default dialer, messages, browser and camera, for the first-run dock. */
    fun defaultDock(context: Context): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        return intents.mapNotNull {
            pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.filter { it != "android" }.distinct()
    }
}
