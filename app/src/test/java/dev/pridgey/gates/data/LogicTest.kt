package dev.pridgey.gates.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DaysTest {
    private val zone = ZoneId.of("America/Denver")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    @Test fun beforeResetBelongsToYesterday() {
        assertEquals(at("2026-11-05T06:00"), Days.dayStart(at("2026-11-06T01:30"), 360, zone))
    }

    @Test fun afterResetBelongsToToday() {
        assertEquals(at("2026-11-06T06:00"), Days.dayStart(at("2026-11-06T06:00"), 360, zone))
        assertEquals(at("2026-11-07T06:00"), Days.nextReset(at("2026-11-06T06:00"), 360, zone))
    }

    @Test fun dstChangeNeverSkipsADay() {
        // US DST ended 2026-11-01; the day is 25 hours long but still one day.
        val start = Days.dayStart(at("2026-10-31T12:00"), 360, zone)
        assertEquals(at("2026-11-01T06:00"), Days.nextReset(start, 360, zone))
    }

    @Test fun pendingResetAppliesOnlyAfterNextReset() {
        val now = System.currentTimeMillis()
        val dayStart = Days.dayStart(now, 360)
        val s = GatesState(
            settings = Settings(resetMinutes = 360, pendingResetMinutes = 8 * 60),
            ledger = Ledger(dayStart = dayStart, nextAdjust = mapOf("a" to -10), cancels = mapOf("a" to 2)),
        )
        // Same day: nothing changes, so today can't be refilled.
        assertEquals(s, Days.rollOver(s, now))
        // After the next reset: new time applies, debt carries over, counters clear.
        val later = Days.rollOver(s, Days.nextReset(dayStart, 360) + 1)
        assertEquals(480, later.settings.resetMinutes)
        assertNull(later.settings.pendingResetMinutes)
        assertEquals(mapOf("a" to -10), later.ledger.adjust)
        assertTrue(later.ledger.cancels.isEmpty())
    }
}

class LimitsTest {
    private val state = GatesState(
        apps = mapOf(
            "video" to AppConfig(gate = GateType.PAUSE, dailyLimit = 30, groupId = "g"),
            "feed" to AppConfig(gate = GateType.ASK, groupId = "g"),
            "bank" to AppConfig(gate = GateType.VAULT),
        ),
        groups = listOf(Group("g", "Social", 45)),
    )

    @Test fun remainingIsSmallerOfOwnAndGroup() {
        assertEquals(20, Limits.remaining(state, "video", mapOf("video" to 10)))
        assertEquals(5, Limits.remaining(state, "video", mapOf("video" to 10, "feed" to 30)))
        assertNull(Limits.remaining(state, "bank", emptyMap()))
    }

    @Test fun outOfTimeGoesUpstairs() {
        val usage = mapOf("feed" to 45)
        assertTrue(Limits.isOutOfTime(state, "video", usage))
        assertTrue(Limits.isUpstairs(state, "video", usage))
        assertFalse(Limits.isUpstairs(state, "bank", usage))
    }

    @Test fun choicesNeverExceedRemaining() {
        assertEquals(listOf(5 to false, 10 to false, 15 to false), Limits.choices(listOf(5, 10, 15), null))
        assertEquals(listOf(5 to false, 8 to true), Limits.choices(listOf(5, 10, 15), 8))
        assertEquals(listOf(5 to false, 10 to true), Limits.choices(listOf(5, 10, 15), 10))
    }

    @Test fun borrowAddsTodayAndOwesTomorrow() {
        val s = Limits.borrow(state, "video", 10)
        assertEquals(10, s.ledger.adjust["video"])
        assertEquals(10, s.ledger.adjust[groupKey("g")])
        assertEquals(-10, s.ledger.nextAdjust["video"])
        assertEquals(10, s.ledger.borrowed["video"])
        assertEquals(15, Limits.remaining(s, "video", mapOf("video" to 10, "feed" to 30)))
    }

    @Test fun lendingHidesVaultAndChosen() {
        val s = state.copy(
            settings = Settings(lending = true),
            apps = state.apps + ("chat" to AppConfig(hideWhenLending = true)),
        )
        assertTrue(Limits.hiddenWhileLending(s, "bank"))
        assertTrue(Limits.hiddenWhileLending(s, "chat"))
        assertFalse(Limits.hiddenWhileLending(s, "video"))
        assertFalse(Limits.hiddenWhileLending(state, "bank"))
    }
}

class StillTest {
    @Test fun scheduleWrapsMidnight() {
        val s = Settings(stillMode = true, stillFrom = 21 * 60, resetMinutes = 6 * 60)
        assertTrue(s.stillActive(LocalTime.of(22, 0)))
        assertTrue(s.stillActive(LocalTime.of(2, 0)))
        assertFalse(s.stillActive(LocalTime.of(12, 0)))
        assertTrue(Settings(stillMode = true).stillActive(LocalTime.NOON))
        assertFalse(Settings(stillMode = false).stillActive(LocalTime.NOON))
    }
}
