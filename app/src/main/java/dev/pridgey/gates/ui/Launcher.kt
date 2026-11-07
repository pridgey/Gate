package dev.pridgey.gates.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.gates.Gates
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.data.Session
import dev.pridgey.gates.data.formatMinutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface Overlay {
    data class Gate(val app: LaunchApp) : Overlay
    data class OutOfTime(val app: LaunchApp) : Overlay
    data class Options(val app: LaunchApp) : Overlay
}

private val OpenSpring = spring<Float>(dampingRatio = 0.92f, stiffness = 420f)
private val CloseSpring = spring<Float>(dampingRatio = 1f, stiffness = 600f)

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
    val scope = rememberCoroutineScope()

    // All apps follows the finger: 0 = home, 1 = All apps.
    val drawer = remember { Animatable(0f) }
    var upstairs by remember { mutableStateOf(false) }
    var gatesApp by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    var editHome by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    val lending = state.settings.lending

    fun openDrawer() = scope.launch { drawer.animateTo(1f, OpenSpring) }
    fun closeDrawer() = scope.launch { drawer.animateTo(0f, CloseSpring) }

    LaunchedEffect(homePresses) {
        homePresses.collect {
            closeDrawer(); upstairs = false; gatesApp = false; diagnostics = false; editHome = false; overlay = null
        }
    }

    val byPkg = remember(apps) { apps.associateBy { it.pkg } }
    // LEND-1: while lending, Vault apps, chosen apps and Gates itself disappear everywhere.
    val visible: (LaunchApp) -> Boolean = {
        !(it.isGates && (!state.settings.showGatesTile || lending)) && !Limits.hiddenWhileLending(state, it.pkg)
    }
    val upstairsPkgs = remember(state, usage, apps) {
        apps.filter { visible(it) && vm.isUpstairs(it.pkg) }.map { it.pkg }.toSet()
    }

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

    fun options(app: LaunchApp) {
        if (!lending) overlay = Overlay.Options(app)
    }

    val anyPanel = upstairs || gatesApp || diagnostics

    // Dark status and nav icons only over the light Gates screens; light everywhere else.
    val lightSurface = (gatesApp || diagnostics) && !androidx.compose.foundation.isSystemInDarkTheme() && !LocalStill.current
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightSurface
            isAppearanceLightNavigationBars = lightSurface
        }
    }
    val homeFade by androidx.compose.animation.core.animateFloatAsState(if (anyPanel) 0f else 1f, tween(220), label = "homeFade")

    Box(Modifier.fillMaxSize()) {
        // Home recedes as All apps comes up.
        val p = drawer.value
        Home(
            modifier = Modifier.graphicsLayer {
                alpha = (1f - p * 1.4f).coerceIn(0f, 1f) * homeFade
                val s = 1f - 0.05f * p
                scaleX = s; scaleY = s
            },
            lending = lending,
            onEndLending = { onVault("End lending") { vm.updateSettings { it.copy(lending = false) } } },
            session = state.session,
            onSessionTap = { state.session?.pkg?.let(byPkg::get)?.let(vm::launch) },
            upstairsCount = upstairsPkgs.size,
            onUpstairs = { upstairs = true },
            homeApps = state.home.mapNotNull { byPkg[it] }.filter { visible(it) && it.pkg !in upstairsPkgs },
            dockApps = state.dock.mapNotNull { byPkg[it] }.filter { visible(it) && it.pkg !in upstairsPkgs },
            gateOf = { state.config(it).gate },
            remainingOf = { vm.remaining(it) },
            onOpen = ::open,
            onOptions = ::options,
            editing = editHome,
            onMove = vm::moveOnHome,
            onDoneEditing = { editHome = false },
            drawerProgress = { drawer.value },
            onDrawerDrag = { scope.launch { drawer.snapTo(it.coerceIn(0f, 1f)) } },
            onDrawerRelease = { velocity ->
                if (velocity < -600f || drawer.value > 0.3f) openDrawer() else closeDrawer()
            },
            onSwipeDown = { expandNotifications(context) },
        )

        if (p > 0.001f) {
            BackHandler { closeDrawer() }
            AllApps(
                progress = { drawer.value },
                apps = apps.filter(visible),
                gateOf = { state.config(it).gate },
                remainingOf = { vm.remaining(it) },
                isFavorite = { it in state.home },
                tagFor = { pkg ->
                    when {
                        pkg !in upstairsPkgs -> null
                        vm.remaining(pkg) == 0 -> "Out of time"
                        else -> "Upstairs"
                    }
                },
                onOpen = { app ->
                    open(app)
                    val gate = state.config(app.pkg).gate
                    if ((gate == GateType.NONE || gate == GateType.VAULT) && !app.isGates && vm.remaining(app.pkg) != 0) closeDrawer()
                },
                onOptions = ::options,
                onSettings = if (lending) null else ({ closeDrawer(); gatesApp = true }),
                onDrag = { scope.launch { drawer.snapTo(it.coerceIn(0f, 1f)) } },
                onRelease = { velocity -> if (velocity > 600f || drawer.value < 0.7f) closeDrawer() else openDrawer() },
            )
        }

        Panel(upstairs) {
            BackHandler { upstairs = false }
            Upstairs(
                outOfTime = upstairsPkgs.filter { vm.remaining(it) == 0 && !state.config(it).manualUpstairs }.mapNotNull { byPkg[it] },
                chosen = upstairsPkgs.filter { state.config(it).manualUpstairs }.mapNotNull { byPkg[it] },
                backAt = Gates.backAt(context),
                gateOf = { state.config(it).gate },
                onOpen = ::open,
                onOptions = ::options,
                onClose = { upstairs = false },
            )
        }

        Panel(gatesApp) {
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

        Panel(diagnostics) {
            BackHandler { diagnostics = false }
            DiagnosticsScreen(onClose = { diagnostics = false })
        }
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
                if (o.app.isGates) gatesApp = true else {
                    vm.startSession(o.app, minutes, reason)
                    closeDrawer()
                }
            },
            onSaveReason = { vm.saveReason(o.app.pkg, it) },
            onCancel = { vm.countCancel(o.app.pkg); overlay = null },
        )
        is Overlay.OutOfTime -> OutOfTimeSheet(
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
            remaining = vm.remaining(o.app.pkg),
            onChange = { vm.updateApp(o.app.pkg, it) },
            onToggleHome = {
                if (!vm.toggleFavorite(o.app.pkg)) {
                    android.widget.Toast.makeText(context, "Home holds $MAX_FAVORITES favorites. Remove one first.", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onToggleDock = { vm.toggleDock(o.app.pkg) },
            onRearrange = { overlay = null; closeDrawer(); gatesApp = false; upstairs = false; editHome = true },
            onNewGroup = vm::newGroup,
            onAppInfo = { Gates.appInfo(context, o.app.pkg) },
            onDismiss = { overlay = null },
        )
        null -> Unit
    }

    if (!state.settings.onboarded) {
        Onboarding(
            onDone = { resetMinutes, stillOn ->
                vm.updateSettings { it.copy(resetMinutes = resetMinutes, stillMode = stillOn, onboarded = true) }
            },
        )
    }
}

/** Full-screen panels (Upstairs, Gates, Diagnostics) rise in gently. */
@Composable
private fun Panel(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 8 } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(180)) { it / 12 } + fadeOut(tween(160)),
    ) { content() }
}

// ------------------------------------------------------------------ home

/** HOME-1, calm: clock, a short text list of apps, a quiet bottom row. */
@Composable
internal fun Home(
    modifier: Modifier,
    lending: Boolean,
    onEndLending: () -> Unit,
    session: Session?,
    onSessionTap: () -> Unit,
    upstairsCount: Int,
    onUpstairs: () -> Unit,
    homeApps: List<LaunchApp>,
    dockApps: List<LaunchApp>,
    gateOf: (String) -> GateType,
    remainingOf: (String) -> Int?,
    onOpen: (LaunchApp) -> Unit,
    onOptions: (LaunchApp) -> Unit,
    editing: Boolean,
    onMove: (String, Int) -> Unit,
    onDoneEditing: () -> Unit,
    drawerProgress: () -> Float,
    onDrawerDrag: (Float) -> Unit,
    onDrawerRelease: (velocity: Float) -> Unit,
    onSwipeDown: () -> Unit,
) {
    val ink = LocalInk.current
    var heightPx by remember { mutableFloatStateOf(1f) }
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .background(Brush.verticalGradient(0f to ink.scrim.copy(alpha = 0.35f), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to ink.scrim.copy(alpha = 0.45f)))
            .pointerInput(editing) {
                if (editing) return@pointerInput
                val tracker = VelocityTracker()
                var pulledDown = 0f
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking(); pulledDown = 0f },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().y
                        if (drawerProgress() > 0f) onDrawerRelease(v)
                        else if (pulledDown > 60.dp.toPx() || v > 1200f) onSwipeDown()
                    },
                    onDragCancel = { onDrawerRelease(0f) },
                    onVerticalDrag = { change, dy ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (dy < 0 || drawerProgress() > 0f) onDrawerDrag(drawerProgress() - dy / (heightPx * 0.45f))
                        else pulledDown += dy
                    },
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(40.dp))
            ClockBlock(session, onSessionTap, upstairsCount, onUpstairs)
            if (lending) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Lending · tap to end", style = MaterialTheme.typography.labelMedium, color = ink.primary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(ink.primary.copy(alpha = 0.14f))
                        .pressable(onClick = onEndLending).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                val rowPx = with(LocalDensity.current) { 52.dp.toPx() }
                val fits = (constraints.maxHeight / rowPx).toInt().coerceAtLeast(1)
                if (editing) {
                    EditableList(homeApps.take(fits), onMove, onDoneEditing)
                } else {
                    if (homeApps.isEmpty()) {
                        Column {
                            Text("Your favorites live here", style = MaterialTheme.typography.headlineMedium, color = ink.secondary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Swipe up, long-press an app and tap Favorite.",
                                style = MaterialTheme.typography.bodyLarge, color = ink.faint,
                            )
                        }
                    }
                    Column {
                        homeApps.take(fits).forEach { app ->
                            HomeRow(app, gateOf(app.pkg), remainingOf(app.pkg), { onOpen(app) }, { onOptions(app) })
                        }
                    }
                }
            }
            if (dockApps.isNotEmpty() && !editing) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    dockApps.forEach { app ->
                        Text(
                            app.label, style = MaterialTheme.typography.titleMedium, color = ink.secondary, maxLines = 1,
                            modifier = Modifier.pressable(onLongClick = { onOptions(app) }) { onOpen(app) }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
            // A quiet handle: swipe up for everything else.
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 10.dp)
                    .size(28.dp, 3.dp).clip(RoundedCornerShape(50)).background(ink.faint),
            )
        }
    }
}

@Composable
private fun ClockBlock(session: Session?, onSessionTap: () -> Unit, upstairsCount: Int, onUpstairs: () -> Unit) {
    val ink = LocalInk.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var millis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            millis = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(now.format(DateTimeFormatter.ofPattern("h:mm")), style = MaterialTheme.typography.displayLarge, color = ink.primary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(now.format(DateTimeFormatter.ofPattern("EEE d MMM")), style = MaterialTheme.typography.bodyLarge, color = ink.secondary)
        if (upstairsCount > 0) {
            // UP-1: Upstairs lives in the date line.
            Text(
                "  ·  $upstairsCount upstairs", style = MaterialTheme.typography.bodyLarge, color = ink.primary,
                modifier = Modifier.pressable(onClick = onUpstairs).padding(vertical = 6.dp),
            )
        }
    }
    if (session != null) {
        val left = session.endsAt - millis
        Row(
            Modifier.padding(top = 4.dp).pressable(onClick = onSessionTap).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GateMark(GateType.PAUSE, ink.secondary, 14.dp)
            Spacer(Modifier.width(8.dp))
            val s = (left / 1000).coerceAtLeast(0)
            Text(
                if (left > 0) "${session.label} · %d:%02d left".format(s / 60, s % 60) else "${session.label} · time's up",
                style = MaterialTheme.typography.bodyLarge, color = ink.secondary,
            )
        }
    }
}

/** One app on home: its name, then a quiet gate mark and time left. */
@Composable
private fun HomeRow(app: LaunchApp, gate: GateType, remaining: Int?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).pressable(onLongClick = onLongClick, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            app.label, style = MaterialTheme.typography.headlineMedium, color = ink.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        Marks(gate, remaining, ink.secondary, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Marks(gate: GateType, remaining: Int?, color: Color, modifier: Modifier = Modifier) {
    if (gate == GateType.NONE && remaining == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (gate != GateType.NONE) GateMark(gate, color, 14.dp)
        if (remaining != null) {
            if (gate != GateType.NONE) Spacer(Modifier.width(6.dp))
            Text(formatMinutes(remaining), style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

/** Rearranging home: drag a name up or down, then Done. */
@Composable
private fun EditableList(apps: List<LaunchApp>, onMove: (String, Int) -> Unit, onDone: () -> Unit) {
    val ink = LocalInk.current
    val rowPx = with(LocalDensity.current) { 52.dp.toPx() }
    Column {
        Text("DRAG TO REORDER FAVORITES", style = MaterialTheme.typography.labelSmall, color = ink.faint, modifier = Modifier.padding(bottom = 8.dp))
        apps.forEachIndexed { index, app ->
            var dy by remember(app.pkg, index) { mutableFloatStateOf(0f) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .zIndex(if (dy != 0f) 1f else 0f)
                    .graphicsLayer { translationY = dy; if (dy != 0f) { scaleX = 1.03f; scaleY = 1.03f } }
                    .pointerInput(app.pkg, index) {
                        detectDragGestures(
                            onDragEnd = {
                                val target = (index + (dy / rowPx).roundToInt()).coerceIn(0, apps.lastIndex)
                                dy = 0f
                                if (target != index) onMove(app.pkg, target)
                            },
                            onDragCancel = { dy = 0f },
                            onDrag = { change, amount -> change.consume(); dy += amount.y },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(app.label, style = MaterialTheme.typography.headlineMedium, color = ink.primary, modifier = Modifier.weight(1f), maxLines = 1)
                Text("⋮⋮", style = MaterialTheme.typography.titleMedium, color = ink.faint)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Done", style = MaterialTheme.typography.labelLarge, color = ink.scrim,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(ink.primary).pressable(onClick = onDone)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}

// ------------------------------------------------------------------ all apps

/** All apps (HOME-1, UP-2): a quiet list over the dimmed wallpaper, search at the thumb. */
@Composable
internal fun AllApps(
    progress: () -> Float,
    apps: List<LaunchApp>,
    gateOf: (String) -> GateType,
    remainingOf: (String) -> Int?,
    tagFor: (String) -> String?,
    isFavorite: (String) -> Boolean,
    onOpen: (LaunchApp) -> Unit,
    onOptions: (LaunchApp) -> Unit,
    onSettings: (() -> Unit)?,
    onDrag: (Float) -> Unit,
    onRelease: (velocity: Float) -> Unit,
) {
    val ink = LocalInk.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    var heightPx by remember { mutableFloatStateOf(1f) }
    val shown = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps
        else apps.filter { it.label.contains(q, ignoreCase = true) }
            .sortedBy { if (it.label.startsWith(q, ignoreCase = true)) 0 else 1 }
    }

    // Search only opens when tapped; drop the keyboard as soon as the list starts to close.
    LaunchedEffect(Unit) {
        snapshotFlow { progress() }.collect { p ->
            if (p < 0.95f) { keyboard?.hide(); focusManager.clearFocus() }
            if (p <= 0.001f) query = ""
        }
    }

    // Pulling down at the top of the list closes All apps.
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    onDrag(progress() - available.y / (heightPx * 0.45f))
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && progress() < 1f && available.y < 0f) {
                    onDrag(progress() - available.y / (heightPx * 0.45f))
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (progress() < 1f) {
                    onRelease(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                val p = progress()
                alpha = (p * 1.3f).coerceIn(0f, 1f)
                translationY = (1f - p) * heightPx * 0.18f
            }
            .background(ink.scrim.copy(alpha = 0.86f)),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(connection),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 16.dp),
            ) {
                if (query.isBlank()) {
                    item(key = "hint") {
                        Text(
                            "Long-press an app to gate it or make it a favorite.",
                            style = MaterialTheme.typography.bodySmall, color = ink.faint,
                        )
                    }
                }
                if (shown.isEmpty()) {
                    item { Text("Nothing called “$query”", style = MaterialTheme.typography.titleLarge, color = ink.secondary) }
                }
                var lastLetter: Char? = null
                shown.forEach { app ->
                    val letter = app.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
                    if (query.isBlank() && letter != lastLetter) {
                        lastLetter = letter
                        item(key = "h-$letter") {
                            Text(
                                letter.toString(), style = MaterialTheme.typography.labelSmall, color = ink.faint,
                                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                            )
                        }
                    }
                    item(key = app.pkg) {
                        DrawerRow(app, gateOf(app.pkg), remainingOf(app.pkg), tagFor(app.pkg), { onOpen(app) }, { onOptions(app) }, isFavorite(app.pkg))
                    }
                }
            }
            // Search sits where the thumb is.
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(50)).background(ink.primary.copy(alpha = 0.10f))
                    .pressable { runCatching { focus.requestFocus() }; keyboard?.show() }
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = ink.secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search", style = MaterialTheme.typography.bodyLarge, color = ink.faint)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink.primary),
                        cursorBrush = SolidColor(ink.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(onOpen) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (onSettings != null) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(50)).pressable(onClick = onSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Settings, "Gates settings", tint = ink.secondary, modifier = Modifier.size(20.dp))
                        val update by dev.pridgey.gates.update.Updater.state.collectAsStateWithLifecycle()
                        if (update is dev.pridgey.gates.update.Updater.State.Available) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp).size(7.dp)
                                    .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(
    app: LaunchApp,
    gate: GateType,
    remaining: Int?,
    tag: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    favorite: Boolean = false,
) {
    val ink = LocalInk.current
    Row(
        Modifier.fillMaxWidth().height(50.dp).pressable(onLongClick = onLongClick, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                app.label, style = MaterialTheme.typography.titleLarge,
                color = if (tag != null) ink.secondary else ink.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Marks(gate, remaining, ink.secondary, Modifier.padding(start = 10.dp))
        }
        if (tag != null) Text(tag.uppercase(), style = MaterialTheme.typography.labelSmall, color = ink.faint)
        else if (favorite) StarMark(true, ink.faint, 13.dp)
    }
}

// ------------------------------------------------------------------ upstairs

/** Upstairs: apps resting until the reset, and apps the user put there. */
@Composable
internal fun Upstairs(
    outOfTime: List<LaunchApp>,
    chosen: List<LaunchApp>,
    backAt: String,
    gateOf: (String) -> GateType,
    onOpen: (LaunchApp) -> Unit,
    onOptions: (LaunchApp) -> Unit,
    onClose: () -> Unit,
) {
    val ink = LocalInk.current
    var pulled by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(ink.scrim.copy(alpha = 0.9f))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (abs(pulled) > 80.dp.toPx()) onClose(); pulled = 0f },
                    onVerticalDrag = { _, dy -> pulled += dy },
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(40.dp))
            Text("Upstairs", style = MaterialTheme.typography.displayMedium, color = ink.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Resting until the day resets, or put here by you.",
                style = MaterialTheme.typography.bodyLarge, color = ink.secondary,
            )
            if (outOfTime.isNotEmpty()) {
                Text(
                    "OUT OF TIME · ${backAt.uppercase()}", style = MaterialTheme.typography.labelSmall, color = ink.faint,
                    modifier = Modifier.padding(top = 36.dp, bottom = 6.dp),
                )
                outOfTime.forEach { DrawerRow(it, gateOf(it.pkg), null, null, { onOpen(it) }, { onOptions(it) }) }
            }
            if (chosen.isNotEmpty()) {
                Text(
                    "PUT HERE BY YOU", style = MaterialTheme.typography.labelSmall, color = ink.faint,
                    modifier = Modifier.padding(top = 36.dp, bottom = 6.dp),
                )
                chosen.forEach { DrawerRow(it, gateOf(it.pkg), null, null, { onOpen(it) }, { onOptions(it) }) }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Done", style = MaterialTheme.typography.labelLarge, color = ink.secondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).pressable(onClick = onClose).padding(16.dp),
            )
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
