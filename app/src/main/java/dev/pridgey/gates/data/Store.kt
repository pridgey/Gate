package dev.pridgey.gates.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * All Gates state lives on the device in one JSON blob (see PRD: Privacy).
 * Small enough that rewriting it on every change is fine.
 */
class Store(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("gates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<GatesState> = _state.asStateFlow()

    @Synchronized
    fun update(transform: (GatesState) -> GatesState) {
        val next = transform(_state.value)
        if (next != _state.value) {
            _state.value = next
            prefs.edit().putString(KEY, encode(next).toString()).apply()
        }
    }

    fun updateApp(pkg: String, transform: (AppConfig) -> AppConfig) = update { s ->
        s.copy(apps = s.apps + (pkg to transform(s.config(pkg))))
    }

    /** Roll the ledger over if a reset has passed. Call before reading limits. */
    fun ensureDay(now: Long = System.currentTimeMillis()) {
        val s = _state.value
        val dayStart = s.ledger.dayStart
        if (dayStart != 0L && now >= Days.nextReset(dayStart, s.settings.resetMinutes)) {
            // Summarise the day that just ended before the ledger is cleared.
            val end = Days.nextReset(dayStart, s.settings.resetMinutes)
            val minutes = if (Usage.hasAccess(appContext)) Usage.minutesSince(appContext, dayStart, end) else s.ledger.sessionMinutes
            val tracked = s.apps.filterValues { it.gate != GateType.NONE || it.dailyLimit != null || it.groupId != null }.keys
            val summary = DaySummary(
                dayStart = dayStart,
                minutes = minutes.filterKeys { it in tracked },
                cancels = s.ledger.cancels,
                borrowed = s.ledger.borrowed,
            )
            update { it.copy(history = (it.history + summary).takeLast(28)) }
        }
        update { Days.rollOver(it, now) }
    }

    private fun load(): GatesState = try {
        prefs.getString(KEY, null)?.let { decode(JSONObject(it)) } ?: GatesState()
    } catch (e: Exception) {
        GatesState()
    }

    companion object {
        private const val KEY = "state"

        fun encode(s: GatesState) = JSONObject().apply {
            put("apps", JSONObject().apply {
                s.apps.forEach { (pkg, c) -> put(pkg, encodeApp(c)) }
            })
            put("groups", JSONArray().apply {
                s.groups.forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name).put("limit", it.limit))
                }
            })
            put("settings", s.settings.let {
                JSONObject()
                    .put("resetMinutes", it.resetMinutes)
                    .put("pendingResetMinutes", it.pendingResetMinutes ?: JSONObject.NULL)
                    .put("stillMode", it.stillMode)
                    .put("timerDisplay", it.timerDisplay.name)
                    .put("onboarded", it.onboarded)
                    .put("gatesIntroSeen", it.gatesIntroSeen)
                    .put("stillFrom", it.stillFrom ?: JSONObject.NULL)
                    .put("lending", it.lending)
            })
            put("ledger", s.ledger.let {
                JSONObject()
                    .put("dayStart", it.dayStart)
                    .put("adjust", mapToJson(it.adjust))
                    .put("nextAdjust", mapToJson(it.nextAdjust))
                    .put("sessionMinutes", mapToJson(it.sessionMinutes))
                    .put("cancels", mapToJson(it.cancels))
                    .put("borrowed", mapToJson(it.borrowed))
            })
            put("history", JSONArray().apply {
                s.history.forEach {
                    put(JSONObject().put("dayStart", it.dayStart)
                        .put("minutes", mapToJson(it.minutes))
                        .put("cancels", mapToJson(it.cancels))
                        .put("borrowed", mapToJson(it.borrowed)))
                }
            })
            s.session?.let {
                put("session", JSONObject()
                    .put("pkg", it.pkg).put("label", it.label)
                    .put("startedAt", it.startedAt).put("endsAt", it.endsAt)
                    .put("reason", it.reason ?: JSONObject.NULL))
            }
        }

        private fun encodeApp(c: AppConfig) = JSONObject()
            .put("gate", c.gate.name)
            .put("dailyLimit", c.dailyLimit ?: JSONObject.NULL)
            .put("groupId", c.groupId ?: JSONObject.NULL)
            .put("reasons", JSONArray().apply {
                c.reasons.forEach { put(JSONObject().put("label", it.label).put("minutes", it.minutes)) }
            })
            .put("pauseChoices", JSONArray(c.pauseChoices))
            .put("hideWhenLending", c.hideWhenLending)

        fun decode(o: JSONObject): GatesState {
            val apps = o.optJSONObject("apps")?.let { a ->
                a.keys().asSequence().associateWith { decodeApp(a.getJSONObject(it)) }
            } ?: emptyMap()
            val groups = o.optJSONArray("groups")?.let { arr ->
                (0 until arr.length()).map {
                    val g = arr.getJSONObject(it)
                    Group(g.getString("id"), g.getString("name"), g.getInt("limit"))
                }
            } ?: emptyList()
            val settings = o.optJSONObject("settings")?.let {
                Settings(
                    resetMinutes = it.optInt("resetMinutes", 360),
                    pendingResetMinutes = it.optIntOrNull("pendingResetMinutes"),
                    stillMode = it.optBoolean("stillMode"),
                    timerDisplay = enumOr(it.optString("timerDisplay"), TimerDisplay.AUTOMATIC),
                    onboarded = it.optBoolean("onboarded"),
                    gatesIntroSeen = it.optBoolean("gatesIntroSeen"),
                    stillFrom = it.optIntOrNull("stillFrom"),
                    lending = it.optBoolean("lending"),
                )
            } ?: Settings()
            val ledger = o.optJSONObject("ledger")?.let {
                Ledger(
                    dayStart = it.optLong("dayStart"),
                    adjust = jsonToMap(it.optJSONObject("adjust")),
                    nextAdjust = jsonToMap(it.optJSONObject("nextAdjust")),
                    sessionMinutes = jsonToMap(it.optJSONObject("sessionMinutes")),
                    cancels = jsonToMap(it.optJSONObject("cancels")),
                    borrowed = jsonToMap(it.optJSONObject("borrowed")),
                )
            } ?: Ledger()
            val session = o.optJSONObject("session")?.let {
                Session(
                    pkg = it.getString("pkg"),
                    label = it.getString("label"),
                    startedAt = it.getLong("startedAt"),
                    endsAt = it.getLong("endsAt"),
                    reason = if (it.isNull("reason")) null else it.getString("reason"),
                )
            }
            return GatesState(
                apps = apps,
                groups = groups,
                settings = settings,
                ledger = ledger,
                session = session,
                history = o.optJSONArray("history")?.let { arr ->
                    (0 until arr.length()).map {
                        val d = arr.getJSONObject(it)
                        DaySummary(
                            dayStart = d.getLong("dayStart"),
                            minutes = jsonToMap(d.optJSONObject("minutes")),
                            cancels = jsonToMap(d.optJSONObject("cancels")),
                            borrowed = jsonToMap(d.optJSONObject("borrowed")),
                        )
                    }
                } ?: emptyList(),
            )
        }

        private fun decodeApp(o: JSONObject) = AppConfig(
            gate = enumOr(o.optString("gate"), GateType.NONE),
            dailyLimit = o.optIntOrNull("dailyLimit"),
            groupId = if (o.isNull("groupId")) null else o.optString("groupId"),
            reasons = o.optJSONArray("reasons")?.let { arr ->
                (0 until arr.length()).map {
                    val r = arr.getJSONObject(it)
                    Reason(r.getString("label"), r.getInt("minutes"))
                }
            } ?: DefaultReasons,
            pauseChoices = o.optJSONArray("pauseChoices")?.let { arr ->
                (0 until arr.length()).map { arr.getInt(it) }
            } ?: listOf(5, 10, 15),
            hideWhenLending = o.optBoolean("hideWhenLending"),
        )

        private fun JSONObject.optIntOrNull(key: String): Int? =
            if (!has(key) || isNull(key)) null else getInt(key)

        private fun mapToJson(m: Map<String, Int>) =
            JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }

        private fun jsonToMap(o: JSONObject?): Map<String, Int> =
            o?.let { j -> j.keys().asSequence().associateWith { j.getInt(it) } } ?: emptyMap()

        private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback
    }
}
