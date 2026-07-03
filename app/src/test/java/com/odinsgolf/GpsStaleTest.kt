package com.odinsgolf

import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.GpsState
import com.odinsgolf.data.model.GpsStatus
import com.odinsgolf.location.effectiveStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Staleness honesty: a fix older than the mode's threshold must surface as STALE_FIX even
 * when its underlying status is GOOD. LocationEngine now ages fixes by the GPS's own clock
 * (Location.elapsedRealtimeNanos), so a stale/cached "last-known" fix feeds a real age here
 * and dims — instead of a frozen position posing as a live yardage.
 */
class GpsStaleTest {

    private val goodFix = GpsState(
        status = GpsStatus.GOOD_FIX,
        point = GeoPoint(64.07, -21.92),
        accuracyMeters = 6f,
        fixElapsedRealtimeMillis = 100_000L,
    )

    @Test
    fun fresh_fix_stays_good() {
        assertEquals(
            GpsStatus.GOOD_FIX,
            goodFix.effectiveStatus(nowElapsedMillis = 110_000L, staleAfterMillis = 20_000L),
        )
    }

    @Test
    fun aged_fix_becomes_stale() {
        // 25 s old against a 20 s threshold → stale, though the raw status is GOOD.
        assertEquals(
            GpsStatus.STALE_FIX,
            goodFix.effectiveStatus(nowElapsedMillis = 125_000L, staleAfterMillis = 20_000L),
        )
    }

    @Test
    fun searching_without_a_point_is_never_downgraded() {
        val searching = GpsState(status = GpsStatus.SEARCHING, point = null, fixElapsedRealtimeMillis = null)
        assertEquals(
            GpsStatus.SEARCHING,
            searching.effectiveStatus(nowElapsedMillis = 999_999L, staleAfterMillis = 20_000L),
        )
    }
}
