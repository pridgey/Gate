package dev.pridgey.gates.data

enum class GateType(val title: String) {
    NONE("None"),
    PAUSE("Pause"),
    ASK("Ask first"),
    VAULT("Vault"),
}

enum class TimerDisplay(val title: String) {
    AUTOMATIC("Automatic"),
    ISLAND("Island"),
    STATUS_CHIP("Status bar chip"),
    OVERHEAD_PILL("Overhead pill"),
    OFF("Off"),
}

data class Reason(val label: String, val minutes: Int)

val DefaultReasons = listOf(
    Reason("Reply to someone", 5),
    Reason("Look something up", 5),
    Reason("Just browsing", 10),
)

data class AppConfig(
    val gate: GateType = GateType.NONE,
    /** Own daily limit in minutes, or null for none. */
    val dailyLimit: Int? = null,
    val groupId: String? = null,
    /** The user put this app Upstairs themselves. */
    val manualUpstairs: Boolean = false,
    /** Move Upstairs automatically when out of time. */
    val upstairsWhenOut: Boolean = true,
    /** Ask first reasons; typed reasons are appended here. */
    val reasons: List<Reason> = DefaultReasons,
    val pauseChoices: List<Int> = listOf(5, 10, 15),
    /** Hidden while Lending mode is on (Vault apps always are). */
    val hideWhenLending: Boolean = false,
)

data class Group(val id: String, val name: String, val limit: Int)

data class Settings(
    /** Daily reset, minutes after local midnight. 6:00 am by default. */
    val resetMinutes: Int = 6 * 60,
    /** A changed reset time waits here until the next reset (RESET-2). */
    val pendingResetMinutes: Int? = null,
    val stillMode: Boolean = false,
    val timerDisplay: TimerDisplay = TimerDisplay.AUTOMATIC,
    val gateFromNotifications: Boolean = false,
    val onboarded: Boolean = false,
    val showGatesTile: Boolean = true,
    val gatesIntroSeen: Boolean = false,
    /** Still mode only from this time until the reset; null = always (STILL-1). */
    val stillFrom: Int? = null,
    /** Lending mode (LEND-1): chosen apps and all Vault apps are hidden. */
    val lending: Boolean = false,
) {
    fun stillActive(now: java.time.LocalTime = java.time.LocalTime.now()): Boolean {
        if (!stillMode) return false
        val from = stillFrom ?: return true
        val m = now.hour * 60 + now.minute
        // Window wraps midnight when from > reset (e.g. 9:00 pm → 6:00 am).
        return if (from > resetMinutes) m >= from || m < resetMinutes else m in from until resetMinutes
    }
}

data class Session(
    val pkg: String,
    val label: String,
    val startedAt: Long,
    val endsAt: Long,
    val reason: String?,
)

data class Ledger(
    /** Start of the current Gates day (the last reset), epoch millis. */
    val dayStart: Long = 0,
    /** Extra minutes for today, keyed by package or "group:<id>" (borrowed). */
    val adjust: Map<String, Int> = emptyMap(),
    /** Minutes owed by the next day (negative). */
    val nextAdjust: Map<String, Int> = emptyMap(),
    /** Minutes used in timed sessions; the fallback when usage access is off. */
    val sessionMinutes: Map<String, Int> = emptyMap(),
    /** Opens cancelled at the gate today, per package (WEEK-1). */
    val cancels: Map<String, Int> = emptyMap(),
    /** Minutes borrowed from tomorrow today, per package. */
    val borrowed: Map<String, Int> = emptyMap(),
)

/** One finished Gates day, kept for the weekly summary. */
data class DaySummary(
    val dayStart: Long,
    val minutes: Map<String, Int>,
    val cancels: Map<String, Int>,
    val borrowed: Map<String, Int>,
)

data class GatesState(
    val apps: Map<String, AppConfig> = emptyMap(),
    val groups: List<Group> = emptyList(),
    val settings: Settings = Settings(),
    val ledger: Ledger = Ledger(),
    val home: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val homeInitialized: Boolean = false,
    val session: Session? = null,
    val history: List<DaySummary> = emptyList(),
) {
    fun config(pkg: String): AppConfig = apps[pkg] ?: AppConfig()
}

fun groupKey(id: String) = "group:$id"
