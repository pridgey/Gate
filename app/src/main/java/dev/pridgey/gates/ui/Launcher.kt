package dev.pridgey.gates.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.gates.Gates
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.LaunchApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private sealed interface Overlay {
    data class Gate(val app: LaunchApp) : Overlay
    data class OutOfTime(val app: LaunchApp) : Overlay
    data class Options(val app: LaunchApp) : Overlay
}

/**
 * The launcher: home, All apps, Upstairs and the Gates app, plus the gate sheets.
 * [onVault] shows the system biometric prompt and runs its callback on success.
 */
@Composable
fun LauncherRoot(
    vm: LauncherViewModel,
    homePresses: Flow<Unit>,
    onVault: (label: String, onSuccess: () -> Unit) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val hasUsage by vm.hasUsageAccess.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var drawer by remember { mutableStateOf(false) }
    var upstairs by remember { mutableStateOf(false) }
    var gatesApp by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    var editHome by remember { mutableStateOf(false) }
    val lending = state.settings.lending
    val still = state.settings.stillActive()
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    LaunchedEffect(homePresses) {
        homePresses.collect { drawer = false; upstairs = false; gatesApp = false; diagnostics = false; editHome = false; overlay = null }
    }

    val byPkg = remember(apps) { apps.associateBy { it.pkg } }
    val gatesPkg = context.packageName
    // LEND-1: while lending, Vault apps, chosen apps and Gates itself disappear everywhere.
    val visible: (LaunchApp) -> Boolean = {
        !(it.isGates && (!state.settings.showGatesTile || lending)) &&
            !dev.pridgey.gates.data.Limits.hiddenWhileLending(state, it.pkg)
    }
    // usage is read so these recompute when it changes.
    val upstairsPkgs = remember(state, usage, apps) { apps.filter { visible(it) && vm.isUpstairs(it.pkg) }.map { it.pkg }.toSet() }

    // GATE-5: the same gate logic for every place an app can be opened from.
    fun open(app: LaunchApp) {
        val cfg = state.config(app.pkg)
        val left = vm.remaining(app.pkg)
        when {
            cfg.gate == GateType.VAULT -> onVault(app.label) { vm.launch(app) }
            left == 0 -> overlay = Overlay.OutOfTime(app)
            cfg.gate == GateType.NONE -> if (app.isGates) gatesApp = true else vm.launch(app)
            else -> overlay = Overlay.Gate(app)
        }
    }

    val iconFor: @Composable (LaunchApp, Boolean, String?) -> Unit = { app, onWallpaper, tag ->
        AppIcon(
            app = app,
            gate = state.config(app.pkg).gate,
            remaining = vm.remaining(app.pkg),
            onClick = { open(app) },
            onLongClick = { if (!lending) overlay = Overlay.Options(app) },
            tag = tag,
            onWallpaper = onWallpaper,
            still = still,
        )
    }

    Home(
        still = still,
        lending = lending,
        onEndLending = { onVault("End lending") { vm.updateSettings { it.copy(lending = false) } } },
        homeApps = state.home.mapNotNull { byPkg[it] }.filter { visible(it) && it.pkg !in upstairsPkgs },
        dockApps = state.dock.mapNotNull { byPkg[it] }.filter { visible(it) && it.pkg !in upstairsPkgs },
        upstairsCount = upstairsPkgs.size,
        icon = { iconFor(it, true, null) },
        editing = editHome,
        onMove = { pkg, to -> vm.moveOnHome(pkg, to) },
        onDoneEditing = { editHome = false },
        onUpstairs = { upstairs = true },
        onSwipeUp = { drawer = true },
        onSwipeDown = { expandNotifications(context) },
    )

    AnimatedVisibility(drawer, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        BackHandler { drawer = false }
        AllApps(
            apps = apps.filter(visible),
            tagFor = { pkg ->
                when {
                    pkg !in upstairsPkgs -> null
                    vm.remaining(pkg) == 0 -> "OUT OF TIME"
                    else -> "UPSTAIRS"
                }
            },
            icon = { app, tag -> iconFor(app, false, tag) },
            onSettings = if (lending) null else ({ drawer = false; gatesApp = true }),
            onClose = { drawer = false },
        )
    }

    AnimatedVisibility(upstairs, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        BackHandler { upstairs = false }
        Upstairs(
            outOfTime = upstairsPkgs.filter { vm.remaining(it) == 0 && !state.config(it).manualUpstairs }.mapNotNull { byPkg[it] },
            chosen = upstairsPkgs.filter { state.config(it).manualUpstairs }.mapNotNull { byPkg[it] },
            backAt = Gates.backAt(context),
            icon = { iconFor(it, false, null) },
            onClose = { upstairs = false },
        )
    }

    AnimatedVisibility(gatesApp, enter = fadeIn(), exit = fadeOut()) {
        BackHandler { gatesApp = false }
        GatesScreen(
            vm = vm,
            state = state,
            apps = apps,
            usage = usage,
            hasUsageAccess = hasUsage,
            onEditApp = { overlay = Overlay.Options(it) },
            onDiagnostics = { diagnostics = true },
            onAuth = onVault,
            onClose = { gatesApp = false },
        )
    }

    AnimatedVisibility(diagnostics, enter = fadeIn(), exit = fadeOut()) {
        BackHandler { diagnostics = false }
        DiagnosticsScreen(onClose = { diagnostics = false })
    }

    when (val o = overlay) {
        is Overlay.Gate -> GateSheet(
            app = o.app,
            config = state.config(o.app.pkg),
            remaining = vm.remaining(o.app.pkg),
            intro = if (o.app.isGates && !state.settings.gatesIntroSeen) {
                "This is a Pause gate, the same one you can put on any app. Pick a time to open Gates."
            } else null,
            onPick = { minutes, reason ->
                overlay = null
                if (o.app.isGates) gatesApp = true else vm.startSession(o.app, minutes, reason)
            },
            onSaveReason = { vm.saveReason(o.app.pkg, it) },
            onCancel = { vm.countCancel(o.app.pkg); overlay = null },
        )
        is Overlay.OutOfTime -> OutOfTimeDialog(
            app = o.app,
            backAt = Gates.backAt(context),
            onBorrow = { minutes -> vm.borrow(o.app.pkg, minutes); overlay = null },
            onDismiss = { overlay = null },
        )
        is Overlay.Options -> AppOptionsSheet(
            app = o.app,
            config = state.config(o.app.pkg),
            groups = state.groups,
            onHome = o.app.pkg in state.home,
            onDock = o.app.pkg in state.dock,
            onToggleDock = { vm.toggleDock(o.app.pkg) },
            onRearrange = { overlay = null; drawer = false; gatesApp = false; editHome = true },
            remaining = vm.remaining(o.app.pkg),
            onChange = { vm.updateApp(o.app.pkg, it) },
            onToggleHome = { vm.toggleHome(o.app.pkg) },
            onNewGroup = vm::newGroup,
            onAppInfo = { Gates.appInfo(context, o.app.pkg) },
            onDismiss = { overlay = null },
        )
        null -> Unit
    }

    if (!state.settings.onboarded) {
        Onboarding(
            onDone = { resetMinutes, still ->
                vm.updateSettings { it.copy(resetMinutes = resetMinutes, stillMode = still, onboarded = true) }
            },
        )
    }
}

/** HOME-1: clock, Upstairs bar, icon grid and dock. Swipe up for All apps, down for notifications. */
@Composable
private fun Home(
    still: Boolean,
    lending: Boolean,
    onEndLending: () -> Unit,
    homeApps: List<LaunchApp>,
    dockApps: List<LaunchApp>,
    upstairsCount: Int,
    icon: @Composable (LaunchApp) -> Unit,
    editing: Boolean,
    onMove: (pkg: String, toIndex: Int) -> Unit,
    onDoneEditing: () -> Unit,
    onUpstairs: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    var drag by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag < -120f) onSwipeUp() else if (drag > 120f) onSwipeDown()
                    },
                    onVerticalDrag = { _, dy -> drag += dy },
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        if (lending) {
            Surface(
                onClick = onEndLending,
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            ) {
                Text(
                    "Lending · tap to end", color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        if (upstairsCount > 0) {
            // UP-1
            Surface(
                onClick = onUpstairs,
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            ) {
                Text(
                    "Upstairs · $upstairsCount",
                    color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        Clock(still = still)
        Spacer(Modifier.height(24.dp))
        if (editing) {
            EditableGrid(homeApps, onMove, onDoneEditing)
        } else {
            homeApps.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { icon(it) }
                    repeat(4 - row.size) { Spacer(Modifier.padding(38.dp)) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (dockApps.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                dockApps.forEach { icon(it) }
            }
        }
    }
}

@Composable
private fun Clock(still: Boolean) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val alpha = if (still) 0.75f else 1f
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            now.format(DateTimeFormatter.ofPattern("h:mm")),
            color = Color.White.copy(alpha = alpha),
            fontSize = 64.sp,
            fontWeight = if (still) FontWeight.Light else FontWeight.Normal,
        )
        Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = Color.White.copy(alpha = alpha))
    }
}

/** All apps with search (HOME-1, UP-2). */
@Composable
private fun AllApps(
    apps: List<LaunchApp>,
    tagFor: (String) -> String?,
    icon: @Composable (LaunchApp, String?) -> Unit,
    onSettings: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val shown = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Gates settings") }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            }
            LazyVerticalGrid(GridCells.Adaptive(80.dp), Modifier.fillMaxSize()) {
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("No apps match “$query”", Modifier.padding(24.dp))
                    }
                }
                items(shown, key = { it.pkg }) { app ->
                    Box(contentAlignment = Alignment.Center) { icon(app, tagFor(app.pkg)) }
                }
            }
        }
    }
}

/** Upstairs: out-of-time apps (until reset) and apps the user put there. */
@Composable
private fun Upstairs(
    outOfTime: List<LaunchApp>,
    chosen: List<LaunchApp>,
    backAt: String,
    icon: @Composable (LaunchApp) -> Unit,
    onClose: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Upstairs", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            }
            if (outOfTime.isNotEmpty()) {
                SectionLabel("Out of time · $backAt")
                IconRows(outOfTime, icon)
            }
            if (chosen.isNotEmpty()) {
                SectionLabel("Put here by you")
                IconRows(chosen, icon)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Tap here or press back to go down",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onClose).padding(12.dp),
            )
        }
    }
}

@Composable
private fun IconRows(apps: List<LaunchApp>, icon: @Composable (LaunchApp) -> Unit) {
    apps.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { row.forEach { icon(it) } }
    }
}

/**
 * Rearranging home: drag an icon onto another slot. Tap Done when finished.
 * Long-press stays free for the app's options, so this is its own mode.
 */
@Composable
private fun EditableGrid(
    apps: List<LaunchApp>,
    onMove: (pkg: String, toIndex: Int) -> Unit,
    onDone: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var cellW by remember { mutableStateOf(1f) }
    val cellH = with(density) { 104.dp.toPx() }
    Column(
        Modifier.fillMaxWidth().onSizeChanged { cellW = it.width / 4f },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        apps.chunked(4).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { c, app ->
                    val index = r * 4 + c
                    var offset by remember(app.pkg, index) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(104.dp)
                            .zIndex(if (offset != androidx.compose.ui.geometry.Offset.Zero) 1f else 0f)
                            .graphicsLayer { translationX = offset.x; translationY = offset.y; alpha = 0.9f }
                            .pointerInput(app.pkg, index) {
                                detectDragGestures(
                                    onDragEnd = {
                                        val dc = kotlin.math.round(offset.x / cellW).toInt()
                                        val dr = kotlin.math.round(offset.y / cellH).toInt()
                                        val col = (c + dc).coerceIn(0, 3)
                                        val target = ((r + dr) * 4 + col).coerceIn(0, apps.lastIndex)
                                        offset = androidx.compose.ui.geometry.Offset.Zero
                                        if (target != index) onMove(app.pkg, target)
                                    },
                                    onDragCancel = { offset = androidx.compose.ui.geometry.Offset.Zero },
                                    onDrag = { change, amount -> change.consume(); offset += amount },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Plain icons: nothing opens while rearranging.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppGlyph(app)
                            Text(app.label, color = Color.White, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Surface(
            onClick = onDone,
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("Drag to rearrange · Done", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/** Swipe down on home opens the notification shade, as on other launchers. */
@SuppressLint("WrongConstant")
private fun expandNotifications(context: Context) {
    runCatching {
        val sbm = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
    }
}
