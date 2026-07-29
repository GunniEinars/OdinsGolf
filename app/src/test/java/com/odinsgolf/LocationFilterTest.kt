package com.odinsgolf

import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.GpsState
import com.odinsgolf.data.model.GpsStatus
import com.odinsgolf.location.isBetterFix
import com.odinsgolf.location.shouldAccept
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wrist-raise "jumping number" fix: a good recent fix must not be replaced by a much
 * less accurate one (the low-accuracy spikes the receiver throws right after it refocuses),
 * while genuine movement (a clearly newer fix) is always accepted so the yardage never sticks.
 */
class LocationFilterTest {

    private fun fix(timeMs: Long, acc: Float?, point: GeoPoint? = GeoPoint(64.07, -21.92)) =
        GpsState(GpsStatus.GOOD_FIX, point, acc, timeMs)

    @Test fun first_fix_is_always_accepted() {
        val none = GpsState(status = GpsStatus.SEARCHING, point = null)
        assertTrue(isBetterFix(fix(1_000, 8f), none))
    }

    @Test fun more_accurate_fix_is_accepted() {
        assertTrue(isBetterFix(fix(2_000, 5f), fix(1_000, 10f)))
    }

    @Test fun newer_but_much_worse_fix_is_rejected() {
        // 5 s newer but 25 m worse — a refocus spike. Keep the good one.
        assertFalse(isBetterFix(fix(6_000, 30f), fix(1_000, 5f)))
    }

    @Test fun newer_and_only_slightly_worse_is_accepted() {
        // 6 m worse, within slack — normal GPS wobble while walking.
        assertTrue(isBetterFix(fix(6_000, 12f), fix(1_000, 6f)))
    }

    @Test fun much_newer_fix_is_accepted_even_if_worse() {
        // 9 s newer (> significant): you may have moved — never stick on the old fix.
        assertTrue(isBetterFix(fix(10_000, 30f), fix(1_000, 5f)))
    }

    @Test fun older_but_more_accurate_fix_is_accepted() {
        assertTrue(isBetterFix(fix(900, 4f), fix(1_000, 10f)))
    }

    @Test fun older_and_worse_fix_is_rejected() {
        assertFalse(isBetterFix(fix(900, 20f), fix(1_000, 6f)))
    }

    // ---- Anti-freeze (shouldAccept) -----------------------------------------
    // The round-3 failure: a stuck warm fix keeps its own clock fresh, so worse-accuracy successors
    // are rejected forever and the position freezes hundreds of metres from where you stand.

    @Test fun within_the_window_shouldAccept_defers_to_isBetterFix() {
        // A worse spike, only 1 s since the last accept → still rejected (no jumping).
        assertFalse(shouldAccept(fix(6_000, 30f), fix(1_000, 5f), msSinceLastAccept = 1_000))
    }

    @Test fun after_a_real_time_gap_the_newest_fix_is_force_taken() {
        // Same worse fix, but 6 s of real time with nothing accepted → take it, so it can't freeze.
        assertTrue(shouldAccept(fix(6_000, 30f), fix(1_000, 5f), msSinceLastAccept = 6_000))
    }
}
