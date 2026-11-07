package dev.pridgey.gates.island

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.sameerasw.island.sdk.HostGrantStore
import com.sameerasw.island.sdk.HostInfo
import com.sameerasw.island.sdk.IslandAction
import com.sameerasw.island.sdk.IslandActionType
import com.sameerasw.island.sdk.IslandCellPayload
import com.sameerasw.island.sdk.IslandExpandedPayload
import com.sameerasw.island.sdk.IslandHostHandle
import com.sameerasw.island.sdk.IslandInteractionsPayload
import com.sameerasw.island.sdk.IslandItemPayload
import com.sameerasw.island.sdk.IslandLinePayload
import com.sameerasw.island.sdk.IslandPayload
import com.sameerasw.island.sdk.IslandSchema
import com.sameerasw.island.sdk.IslandTone
import com.sameerasw.island.sdk.compose.ComposeIslandProviderService
import com.sameerasw.island.sdk.compose.islandSnapshot
import dev.pridgey.gates.Gates
import dev.pridgey.gates.R
import dev.pridgey.gates.data.Session
import dev.pridgey.gates.diag.DiagLog
import dev.pridgey.gates.store
import dev.pridgey.gates.timer.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Gates as an Essentials island provider (timer priority 1 in the PRD).
 *
 * Essentials binds this service once the user turns Gates on under
 * Island → Island apps. While a timed session runs we publish one item:
 *  - compact: timer icon + "4:12" (alert tone in the last minute, TIMER-4)
 *  - line:    "Video" · reason or "4:12 left" (TIMER-2 collapsed)
 *  - expanded: our own view with End now and +5 min (TIMER-2 expanded)
 * Long-press on the island ends the session.
 */
class GatesIslandService : ComposeIslandProviderService() {
    private var scope: CoroutineScope? = null
    private var publishedEmpty = false
    private var peekedLastMinuteFor = 0L
    private var peekedTimesUpFor = 0L
    private val icon: Bitmap by lazy {
        ContextCompat.getDrawable(this, R.drawable.ic_timer)!!.toBitmap(96, 96)
    }

    override fun onHostGrantRequested(hostPackage: String): Boolean {
        DiagLog.add(this, "island", "pairing requested by $hostPackage")
        return true
    }

    override fun onAttached(host: IslandHostHandle, info: HostInfo) {
        DiagLog.add(
            this, "island",
            "attached host=${info.hostPackage} schema=${info.hostSchema} width=${info.expandedWidthPx} " +
                "camera=${info.cameraClearancePx} corner=${info.cornerPx} dark=${info.darkTheme} powerSave=${info.powerSave}",
        )
        _attached.value = true
        publishedEmpty = false
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            s.launch {
                while (isActive) {
                    runCatching { publish(host) }.onFailure { DiagLog.add(this@GatesIslandService, "island", "publish failed: $it") }
                    delay(1_000)
                }
            }
        }
        // The notification drops its chronometer while we're on the island (no duplicate timer).
        TimerService.refresh(this)
    }

    override fun onHostDetached() {
        super.onHostDetached()
        if (_attached.value) DiagLog.add(this, "island", "detached")
        _attached.value = false
        scope?.cancel()
        scope = null
        TimerService.refresh(this)
    }

    override fun onAction(itemKey: String, actionId: String) {
        DiagLog.add(this, "island", "action $actionId on $itemKey")
        when (actionId) {
            ACTION_END -> endNow(this)
            ACTION_PLUS5 -> plusFive(this)
        }
    }

    override fun hasExpandedContent(itemKey: String) = itemKey == ITEM

    private fun publish(host: IslandHostHandle) {
        val session = store.state.value.session
        if (session == null) {
            if (!publishedEmpty) {
                host.publish(IslandPayload(items = emptyList()))
                publishedEmpty = true
            }
            return
        }
        publishedEmpty = false
        val leftMs = session.endsAt - System.currentTimeMillis()
        val lastMinute = leftMs in 1..59_999
        val timesUp = leftMs <= 0
        val clock = clock(leftMs)

        val item = IslandItemPayload(
            key = ITEM,
            compact = listOf(
                IslandCellPayload(key = "$ITEM.icon", iconId = ICON, tone = if (lastMinute || timesUp) IslandTone.ALERT else IslandTone.ACCENT),
                IslandCellPayload(key = "$ITEM.time", text = if (timesUp) "Time's up" else clock, tone = if (lastMinute) IslandTone.ALERT else IslandTone.NONE),
            ),
            line = IslandLinePayload(
                // Essentials drops line text over 120 characters.
                start = session.label.take(40),
                end = when {
                    timesUp -> "Time's up"
                    session.reason != null -> "“${session.reason.take(80)}” · $clock"
                    else -> "$clock left"
                },
                iconId = ICON,
                tone = if (lastMinute || timesUp) IslandTone.ALERT else IslandTone.ACCENT,
            ),
            expanded = IslandExpandedPayload(heightDp = 180),
            priority = 30,
            priorityOverride = if (lastMinute || timesUp) 12 else null,
            interactions = IslandInteractionsPayload(
                onLongPress = IslandAction(IslandActionType.CALLBACK, ACTION_END),
            ),
        )
        host.publish(IslandPayload(items = listOf(item)), mapOf(ICON to icon))

        // Gentle nudges, within the host's peek limits. No sound or vibration.
        if (lastMinute && peekedLastMinuteFor != session.endsAt) {
            peekedLastMinuteFor = session.endsAt
            host.peek(ITEM, 4_000)
        }
        if (timesUp && peekedTimesUpFor != session.endsAt) {
            peekedTimesUpFor = session.endsAt
            host.peek(ITEM, 6_000)
        }
    }

    @Composable
    override fun ExpandedContent(itemKey: String) {
        val info = hostInfoState.value
        val density = LocalDensity.current
        val corner = with(density) { (info?.cornerPx ?: 0).toDp() }
        val camera = with(density) { (info?.cameraClearancePx ?: 0).toDp() }
        val accent = Color(info?.accent ?: 0xFFFFFFFF.toInt())
        val state by store.state.collectAsState()
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(500)
            }
        }
        val session: Session? = state.session
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .onSizeChanged { host?.resize(itemKey, it.height) }
                .islandSnapshot { host?.snapshot(itemKey, it) }
                .clip(RoundedCornerShape(corner))
                .background(Color.Black),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                Spacer(Modifier.height(camera))
                if (session == null) {
                    Text("No timer running", color = Color.White.copy(alpha = 0.7f))
                    return@Column
                }
                val left = session.endsAt - now
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(session.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (left <= 0) "Time's up" else clock(left),
                        color = if (left in 1..59_999) accent else Color.White,
                        fontSize = 32.sp, fontWeight = FontWeight.Light,
                    )
                }
                session.reason?.let {
                    Text("“$it”", color = Color.White.copy(alpha = 0.7f), fontStyle = FontStyle.Italic, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("End now", filled = true, accent = accent, modifier = Modifier.weight(1f)) { endNow(this@GatesIslandService) }
                    Pill("+5 min", filled = false, accent = accent, modifier = Modifier.weight(1f)) { plusFive(this@GatesIslandService) }
                }
            }
        }
    }

    @Composable
    private fun Pill(text: String, filled: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
        Box(
            modifier
                .clip(RoundedCornerShape(50))
                .background(if (filled) accent else Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = if (filled) Color.Black else Color.White, fontSize = 15.sp)
        }
    }

    override fun onDestroy() {
        scope?.cancel()
        _attached.value = false
        super.onDestroy()
    }

    companion object {
        private const val ITEM = "session"
        private const val ICON = "timer"
        private const val ACTION_END = "end"
        private const val ACTION_PLUS5 = "plus5"
        const val ESSENTIALS = IslandSchema.DEFAULT_HOST_PACKAGE

        private val _attached = MutableStateFlow(false)
        /** True while Essentials has the island bound to Gates. */
        val attached: StateFlow<Boolean> = _attached.asStateFlow()

        fun clock(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d".format(s / 60, s % 60)
        }

        private fun endNow(context: Context) {
            Gates.endSession(context)
            Gates.goHome(context)
        }

        private fun plusFive(context: Context) {
            val pkg = context.store.state.value.session?.pkg ?: return
            val left = Gates.remaining(context, pkg)
            Gates.extendSession(context, minOf(5, left ?: 5).coerceAtLeast(1))
        }

        fun essentialsInstalled(context: Context) = runCatching {
            context.packageManager.getPackageInfo(ESSENTIALS, 0)
        }.isSuccess

        fun essentialsVersion(context: Context): String? = runCatching {
            context.packageManager.getPackageInfo(ESSENTIALS, 0).let { "${it.versionName} (${it.longVersionCode})" }
        }.getOrNull()

        /** Gates approved Essentials, and Essentials' signing key still matches. */
        fun paired(context: Context) = HostGrantStore(context).isAuthorized(ESSENTIALS)

        fun openEssentials(context: Context) {
            context.packageManager.getLaunchIntentForPackage(ESSENTIALS)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
