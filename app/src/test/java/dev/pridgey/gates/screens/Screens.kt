package dev.pridgey.gates.screens

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import dev.pridgey.gates.data.AppConfig
import dev.pridgey.gates.data.DaySummary
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.GatesState
import dev.pridgey.gates.data.Group
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.Ledger
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.data.Session
import dev.pridgey.gates.data.Settings
import dev.pridgey.gates.ui.AppOptionsSheet
import dev.pridgey.gates.ui.AllApps
import dev.pridgey.gates.ui.GateSheet
import dev.pridgey.gates.ui.GatesActions
import dev.pridgey.gates.ui.GatesScreen
import dev.pridgey.gates.ui.GatesTheme
import dev.pridgey.gates.ui.Home
import dev.pridgey.gates.ui.Onboarding
import dev.pridgey.gates.ui.OutOfTimeSheet
import dev.pridgey.gates.ui.Upstairs
import org.junit.Rule
import org.junit.Test

/** Renders the main screens to PNGs for design review: ./gradlew recordPaparazziDebug -Pscreens */
class Screens {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar")

    private fun app(label: String, pkg: String = "app.${label.lowercase()}") = LaunchApp(pkg, label, ComponentName(pkg, "Main"), null)
    private val gates = LaunchApp("dev.pridgey.gates", "Gates", null, null)
    private val messages = app("Messages")
    private val maps = app("Maps")
    private val feed = app("Feed")
    private val video = app("Video")
    private val bank = app("Bank")
    private val notes = app("Notes")
    private val camera = app("Camera")
    private val phone = app("Phone")
    private val chess = app("Chess")
    private val allApps = listOf(bank, camera, chess, app("Calendar"), app("Clock"), feed, gates, maps, messages, notes, phone, video, app("Weather"))

    private val state = GatesState(
        apps = mapOf(
            feed.pkg to AppConfig(gate = GateType.ASK, dailyLimit = 30, groupId = "s"),
            video.pkg to AppConfig(gate = GateType.PAUSE, groupId = "s"),
            bank.pkg to AppConfig(gate = GateType.VAULT),
            gates.pkg to AppConfig(gate = GateType.PAUSE, pauseChoices = listOf(1, 5)),
            chess.pkg to AppConfig(manualUpstairs = true),
        ),
        groups = listOf(Group("s", "Social", 45)),
        settings = Settings(onboarded = true, gatesIntroSeen = true),
        ledger = Ledger(cancels = mapOf(feed.pkg to 3), borrowed = mapOf(video.pkg to 10)),
        home = listOf(messages.pkg, maps.pkg, feed.pkg, notes.pkg, bank.pkg, gates.pkg),
        dock = listOf(phone.pkg, camera.pkg),
        session = Session(feed.pkg, "Feed", 0, 252_000, "Reply to someone"),
        history = listOf(DaySummary(0, mapOf(feed.pkg to 34, video.pkg to 51), mapOf(feed.pkg to 2), emptyMap())),
    )
    private val usage = mapOf(feed.pkg to 18, video.pkg to 15)

    @Composable
    private fun Wallpaper(content: @Composable () -> Unit) = CompositionLocalProvider(LocalInspectionMode provides true) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF2B4A52), Color(0xFF52707A), Color(0xFFB59B83), Color(0xFF3A2E2B))),
            ),
        ) { content() }
    }

    @Composable
    private fun HomeScreen(s: GatesState = state) = Home(
        modifier = Modifier,
        lending = false,
        onEndLending = {},
        session = s.session?.copy(startedAt = System.currentTimeMillis(), endsAt = System.currentTimeMillis() + 252_000),
        onSessionTap = {},
        upstairsCount = 2,
        onUpstairs = {},
        homeApps = s.home.mapNotNull { p -> allApps.find { it.pkg == p } },
        dockApps = s.dock.mapNotNull { p -> allApps.find { it.pkg == p } },
        gateOf = { s.config(it).gate },
        remainingOf = { Limits.remaining(s, it, usage) },
        onOpen = {}, onOptions = {},
        editing = false, onMove = { _, _ -> }, onDoneEditing = {},
        drawerProgress = { 0f }, onDrawerDrag = {}, onDrawerRelease = {}, onSwipeDown = {},
    )

    private val actions = object : GatesActions {
        override fun refreshUsage() = Unit
        override fun updateSettings(transform: (Settings) -> Settings) = Unit
        override fun newGroup(name: String, limit: Int) = "x"
        override fun editGroup(id: String, name: String, limit: Int) = Unit
        override fun deleteGroup(id: String) = Unit
    }

    @Test fun home() = paparazzi.snapshot { GatesTheme { Wallpaper { HomeScreen() } } }

    @Test fun homeStill() = paparazzi.snapshot {
        GatesTheme(still = true) { Wallpaper { HomeScreen(state.copy(settings = state.settings.copy(stillMode = true))) } }
    }

    @Test fun allApps() = paparazzi.snapshot {
        GatesTheme {
            Wallpaper {
                AllApps(
                    progress = { 1f }, apps = allApps, gateOf = { state.config(it).gate },
                    remainingOf = { Limits.remaining(state, it, usage) },
                    tagFor = { if (it == chess.pkg) "Upstairs" else null },
                    isFavorite = { it in state.home },
                    onOpen = {}, onOptions = {}, onSettings = {}, onDrag = {}, onRelease = {},
                )
            }
        }
    }

    @Test fun upstairs() = paparazzi.snapshot {
        GatesTheme {
            Wallpaper {
                Upstairs(listOf(video), listOf(chess), "Back at 6:00 am", { state.config(it).gate }, {}, {}, {})
            }
        }
    }

    @Test fun pauseSheet() = paparazzi.snapshot {
        GatesTheme { Wallpaper { HomeScreen(); GateSheet(video, state.config(video.pkg), 12, null, { _, _ -> }, {}, {}) } }
    }

    @Test fun askSheet() = paparazzi.snapshot {
        GatesTheme { Wallpaper { HomeScreen(); GateSheet(feed, state.config(feed.pkg), 8, null, { _, _ -> }, {}, {}) } }
    }

    @Test fun pauseSheetStill() = paparazzi.snapshot {
        GatesTheme(still = true) { Wallpaper { HomeScreen(); GateSheet(video, state.config(video.pkg), null, null, { _, _ -> }, {}, {}) } }
    }

    @Test fun outOfTime() = paparazzi.snapshot {
        GatesTheme { Wallpaper { HomeScreen(); OutOfTimeSheet(video, "Back at 6:00 am", {}, {}) } }
    }

    @Test fun options() = paparazzi.snapshot {
        GatesTheme {
            Wallpaper {
                AppOptionsSheet(
                    feed, state.config(feed.pkg), state.groups, onHome = true, onDock = false, remaining = 12,
                    onChange = {}, onToggleHome = {}, onToggleDock = {}, onRearrange = {}, onNewGroup = { _, _ -> "" },
                    onAppInfo = {}, onDismiss = {},
                )
            }
        }
    }

    @Composable
    private fun Settings() = GatesScreen(
        vm = actions, state = state.copy(settings = state.settings.copy(gatesIntroSeen = false)), apps = allApps, usage = usage,
        hasUsageAccess = true, onEditApp = {}, onDiagnostics = {}, onAuth = { _, _ -> }, onClose = {},
    )

    @Test fun gatesLight() = paparazzi.snapshot { GatesTheme { Settings() } }

    @Test fun gatesDark() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
        paparazzi.snapshot { GatesTheme { Settings() } }
    }

    @Test fun gatesDarkScrolled() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT, screenHeight = 5200))
        paparazzi.snapshot { GatesTheme { Settings() } }
    }

    @Test fun onboarding() = paparazzi.snapshot { GatesTheme { Onboarding { _, _ -> } } }
}
