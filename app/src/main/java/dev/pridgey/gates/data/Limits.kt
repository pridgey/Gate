package dev.pridgey.gates.data

import kotlin.math.min

/** Remaining-time math. Usage is minutes per package for the current Gates day. */
object Limits {
    fun groupUsed(s: GatesState, groupId: String, usage: Map<String, Int>): Int =
        s.apps.filterValues { it.groupId == groupId }.keys.sumOf { usage[it] ?: 0 }

    /**
     * Minutes left today: the smaller of the app's own limit and its group's limit.
     * Null when the app has no limit. Never negative.
     */
    fun remaining(s: GatesState, pkg: String, usage: Map<String, Int>): Int? {
        val cfg = s.apps[pkg] ?: return null
        val adjust = s.ledger.adjust
        var left: Int? = cfg.dailyLimit?.let { it + (adjust[pkg] ?: 0) - (usage[pkg] ?: 0) }
        val group = cfg.groupId?.let { id -> s.groups.find { it.id == id } }
        if (group != null) {
            val g = group.limit + (adjust[groupKey(group.id)] ?: 0) - groupUsed(s, group.id, usage)
            left = left?.let { min(it, g) } ?: g
        }
        return left?.coerceAtLeast(0)
    }

    fun isOutOfTime(s: GatesState, pkg: String, usage: Map<String, Int>): Boolean =
        remaining(s, pkg, usage) == 0

    /**
     * Pause time choices, never more than what's left (GATE-2). The last chip is
     * "All Nm" when it equals the time left. Returns (minutes, isAll) pairs.
     */
    fun choices(base: List<Int>, remaining: Int?): List<Pair<Int, Boolean>> {
        if (remaining == null) return base.map { it to false }
        val under = base.filter { it < remaining }.map { it to false }
        val all = if (base.any { it >= remaining }) listOf(remaining to true) else emptyList()
        return (under + all).takeLast(4)
    }

    fun hiddenWhileLending(s: GatesState, pkg: String): Boolean {
        if (!s.settings.lending) return false
        val cfg = s.apps[pkg] ?: return false
        return cfg.gate == GateType.VAULT || cfg.hideWhenLending
    }

    fun countCancel(s: GatesState, pkg: String): GatesState =
        s.copy(ledger = s.ledger.copy(cancels = s.ledger.cancels + (pkg to (s.ledger.cancels[pkg] ?: 0) + 1)))

    /** Borrow minutes from the allowance that starts at the next reset (RESET-4). */
    fun borrow(s: GatesState, pkg: String, minutes: Int): GatesState {
        val cfg = s.config(pkg)
        val keys = buildList {
            if (cfg.dailyLimit != null) add(pkg)
            cfg.groupId?.let { add(groupKey(it)) }
        }
        val l = s.ledger
        return s.copy(
            ledger = l.copy(
                borrowed = l.borrowed + (pkg to (l.borrowed[pkg] ?: 0) + minutes),
                adjust = l.adjust + keys.associateWith { (l.adjust[it] ?: 0) + minutes },
                nextAdjust = l.nextAdjust + keys.associateWith { (l.nextAdjust[it] ?: 0) - minutes },
            ),
        )
    }
}
