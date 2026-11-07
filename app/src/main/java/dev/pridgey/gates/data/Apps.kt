package dev.pridgey.gates.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class LaunchApp(
    val pkg: String,
    val label: String,
    val component: ComponentName?,
    val icon: ImageBitmap?,
) {
}

object Apps {
    /** Every app with a launcher entry except Gates itself. */
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
        return apps.sortedBy { it.label.lowercase() }
    }

    /** One app by package name, for the gate shown over it. */
    fun forPackage(context: Context, pkg: String): LaunchApp? = runCatching {
        val pm = context.packageManager
        val ai = pm.getApplicationInfo(pkg, 0)
        LaunchApp(
            pkg = pkg,
            label = pm.getApplicationLabel(ai).toString(),
            component = pm.getLaunchIntentForPackage(pkg)?.component,
            icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap() }.getOrNull(),
        )
    }.getOrNull()
}
