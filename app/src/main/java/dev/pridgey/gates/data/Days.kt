package dev.pridgey.gates.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Gates day runs from one reset to the next (6:00 am by default), in local time.
 * Built from the local date each time, so DST and time-zone changes never skip
 * or double a day (RESET-5).
 */
object Days {
    private fun resetOn(date: LocalDate, resetMinutes: Int, zone: ZoneId): Long =
        date.atTime(resetMinutes / 60, resetMinutes % 60).atZone(zone).toInstant().toEpochMilli()

    /** The most recent reset at or before [now]. */
    fun dayStart(now: Long, resetMinutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val candidate = resetOn(today, resetMinutes, zone)
        return if (candidate <= now) candidate else resetOn(today.minusDays(1), resetMinutes, zone)
    }

    /** The first reset strictly after [after]. */
    fun nextReset(after: Long, resetMinutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val day = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        val candidate = resetOn(day, resetMinutes, zone)
        return if (candidate > after) candidate else resetOn(day.plusDays(1), resetMinutes, zone)
    }

    /** Advance the ledger when a reset has passed; apply a pending reset time then. */
    fun rollOver(s: GatesState, now: Long): GatesState {
        val settings = s.settings
        if (s.ledger.dayStart == 0L) {
            return s.copy(ledger = s.ledger.copy(dayStart = dayStart(now, settings.resetMinutes)))
        }
        val next = nextReset(s.ledger.dayStart, settings.resetMinutes)
        if (now < next) return s

        // A reset has passed. A pending reset-time change takes effect from here on.
        val reset = settings.pendingResetMinutes ?: settings.resetMinutes
        val newStart = maxOf(next, dayStart(now, reset))
        return s.copy(
            settings = settings.copy(resetMinutes = reset, pendingResetMinutes = null),
            ledger = Ledger(
                dayStart = newStart,
                adjust = s.ledger.nextAdjust,
                nextAdjust = emptyMap(),
                sessionMinutes = emptyMap(),
            ),
        )
    }

    fun formatTime(minutes: Int): String {
        val t = ZonedDateTime.now().toLocalDate().atTime(minutes / 60, minutes % 60)
        return t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).lowercase()
    }
}

fun formatMinutes(m: Int): String = when {
    m < 60 -> "${m}m"
    m % 60 == 0 -> "${m / 60}h"
    else -> "${m / 60}h ${m % 60}m"
}
