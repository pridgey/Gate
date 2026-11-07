package dev.pridgey.gates.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pridgey.gates.Gates
import dev.pridgey.gates.data.AppConfig
import dev.pridgey.gates.data.Apps
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.Group
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.data.Reason
import dev.pridgey.gates.data.Usage
import dev.pridgey.gates.store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/** What the Gates app screen needs from the launcher; lets it render in previews and tests. */
interface GatesActions {
    fun refreshUsage(): Any
    fun updateSettings(transform: (dev.pridgey.gates.data.Settings) -> dev.pridgey.gates.data.Settings): Any
    fun newGroup(name: String, limit: Int): String
    fun editGroup(id: String, name: String, limit: Int): Any
    fun deleteGroup(id: String): Any
}

const val MAX_FAVORITES = 8

class LauncherViewModel(app: Application) : AndroidViewModel(app), GatesActions {
    private val ctx get() = getApplication<Application>()
    private val store = app.store
    val state = store.state

    private val _apps = MutableStateFlow<List<LaunchApp>>(emptyList())
    val apps = _apps.asStateFlow()
    private val _usage = MutableStateFlow<Map<String, Int>>(emptyMap())
    val usage = _usage.asStateFlow()
    private val _hasUsageAccess = MutableStateFlow(false)
    val hasUsageAccess = _hasUsageAccess.asStateFlow()

    init {
        reloadApps()
        viewModelScope.launch {
            while (isActive) {
                refreshUsage()
                delay(60_000)
            }
        }
    }

    fun reloadApps() = viewModelScope.launch(Dispatchers.IO) {
        val list = Apps.load(ctx)
        _apps.value = list
        if (!state.value.homeInitialized) firstRunHome(list)
        else if (state.value.layoutVersion < 2) {
            // The calm text home fits about seven names; trim the old 16-icon grid once.
            store.update { it.copy(home = it.home.take(7), dock = it.dock.take(3), layoutVersion = 2) }
        }
    }

    override fun refreshUsage() = viewModelScope.launch(Dispatchers.IO) {
        _hasUsageAccess.value = Usage.hasAccess(ctx)
        _usage.value = Gates.usage(ctx)
    }

    private fun firstRunHome(list: List<LaunchApp>) {
        val pkgs = list.map { it.pkg }.toSet()
        val dock = Apps.defaultDock(ctx).filter { it in pkgs }.take(3)
        val home = listOf(ctx.packageName) +
            list.map { it.pkg }.filter { it != ctx.packageName && it !in dock }.take(5)
        store.update {
            it.copy(
                home = home,
                dock = dock,
                homeInitialized = true,
                layoutVersion = 2,
                // APP-4: the Gates app ships with its own Pause gate as a live example.
                apps = it.apps + (ctx.packageName to AppConfig(gate = GateType.PAUSE, pauseChoices = listOf(1, 5))),
            )
        }
    }

    // --- Derived values ---

    fun remaining(pkg: String): Int? = Limits.remaining(state.value, pkg, usage.value)
    fun isUpstairs(pkg: String): Boolean = Limits.isUpstairs(state.value, pkg, usage.value)

    // --- Actions ---

    fun launch(app: LaunchApp) {
        val cn = app.component ?: return
        // Opened through its gate here, so the accessibility service shouldn't gate it again.
        dev.pridgey.gates.access.GateAccessibilityService.approved = app.pkg
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    fun startSession(app: LaunchApp, minutes: Int, reason: String?) {
        Gates.startSession(ctx, app.pkg, app.label, minutes, reason)
        launch(app)
    }

    /** Coming back to home ends a running session; its time is counted. */
    fun onHomeResumed() {
        val s = state.value.session
        if (s != null && System.currentTimeMillis() - s.startedAt > 2_000) Gates.endSession(ctx)
        store.ensureDay()
        refreshUsage()
        viewModelScope.launch { dev.pridgey.gates.update.Updater.checkIfDue(ctx) }
    }

    fun updateApp(pkg: String, transform: (AppConfig) -> AppConfig) = store.updateApp(pkg, transform)

    fun saveReason(pkg: String, reason: Reason) = store.updateApp(pkg) { c ->
        if (c.reasons.any { it.label.equals(reason.label, ignoreCase = true) }) c
        else c.copy(reasons = c.reasons + reason)
    }

    /** Favorites are the apps listed on home. Returns false when home is full. */
    fun toggleFavorite(pkg: String): Boolean {
        var added = true
        store.update {
            when {
                pkg in it.home -> it.copy(home = it.home - pkg)
                it.home.size >= MAX_FAVORITES -> { added = false; it }
                else -> it.copy(home = it.home + pkg)
            }
        }
        return added
    }

    fun toggleDock(pkg: String) = store.update {
        when {
            pkg in it.dock -> it.copy(dock = it.dock - pkg)
            it.dock.size >= 4 -> it
            else -> it.copy(dock = it.dock + pkg)
        }
    }

    /** Move [pkg] to [toIndex] among the apps visible on home (indexes in that visible list). */
    fun moveOnHome(pkg: String, toIndex: Int) = store.update { s ->
        val visible = s.home.filter { p -> !Limits.isUpstairs(s, p, usage.value) && !Limits.hiddenWhileLending(s, p) }
        val target = visible.getOrNull(toIndex) ?: return@update s
        val list = s.home.toMutableList()
        list.remove(pkg)
        val at = list.indexOf(target).let { if (toIndex > visible.indexOf(pkg)) it + 1 else it }
        list.add(at.coerceIn(0, list.size), pkg)
        s.copy(home = list)
    }

    fun countCancel(pkg: String) = store.update { Limits.countCancel(it, pkg) }

    fun borrow(pkg: String, minutes: Int) {
        store.update { Limits.borrow(it, pkg, minutes) }
        refreshUsage()
    }

    override fun newGroup(name: String, limit: Int): String {
        val id = UUID.randomUUID().toString().take(8)
        store.update { it.copy(groups = it.groups + Group(id, name, limit)) }
        return id
    }

    override fun editGroup(id: String, name: String, limit: Int) = store.update { s ->
        s.copy(groups = s.groups.map { if (it.id == id) it.copy(name = name, limit = limit) else it })
    }

    override fun deleteGroup(id: String) = store.update { s ->
        s.copy(
            groups = s.groups.filter { it.id != id },
            apps = s.apps.mapValues { (_, c) -> if (c.groupId == id) c.copy(groupId = null) else c },
        )
    }

    override fun updateSettings(transform: (dev.pridgey.gates.data.Settings) -> dev.pridgey.gates.data.Settings) =
        store.update { it.copy(settings = transform(it.settings)) }
}
