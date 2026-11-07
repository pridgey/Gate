package dev.pridgey.gates.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pridgey.gates.Gates
import dev.pridgey.gates.data.AppConfig
import dev.pridgey.gates.data.Apps
import dev.pridgey.gates.data.Group
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.Usage
import dev.pridgey.gates.store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/** What the Gates screen needs from the view model; lets it render in previews and tests. */
interface GatesActions {
    fun refreshUsage(): Any
    fun updateSettings(transform: (dev.pridgey.gates.data.Settings) -> dev.pridgey.gates.data.Settings): Any
    fun newGroup(name: String, limit: Int): String
    fun editGroup(id: String, name: String, limit: Int): Any
    fun deleteGroup(id: String): Any
}

class GatesViewModel(app: Application) : AndroidViewModel(app), GatesActions {
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
        _apps.value = Apps.load(ctx)
    }

    override fun refreshUsage() = viewModelScope.launch(Dispatchers.IO) {
        _hasUsageAccess.value = Usage.hasAccess(ctx)
        _usage.value = Gates.usage(ctx)
    }

    fun onResume() {
        store.ensureDay()
        refreshUsage()
        viewModelScope.launch { dev.pridgey.gates.update.Updater.checkIfDue(ctx) }
    }

    fun updateApp(pkg: String, transform: (AppConfig) -> AppConfig) = store.updateApp(pkg, transform)

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
