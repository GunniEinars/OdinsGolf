package com.odinsgolf

import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Green
import com.odinsgolf.data.model.Hole
import com.odinsgolf.geo.Distances
import com.odinsgolf.geo.Geo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-hole plausibility guard that stops a wildly wrong GPS fix from showing a confident wrong
 * yardage (or squishing the map). Setberg hole 1: tee→green ≈ 409 m (real OSM coordinates).
 */
class DistancesTest {

    private val tee = GeoPoint(64.0668091, -21.9238815)
    private val green = GeoPoint(64.0696949, -21.9290879)
    private val hole = Hole(
        number = 1, displayNumber = "1", par = 5, strokeIndex = 6,
        tee = tee, green = Green(center = green, front = null, back = null),
        hazards = emptyList(), path = listOf(tee, green), notes = "",
    )

    @Test fun standing_on_the_tee_is_on_hole() {
        assertTrue(Distances.isOnHole(tee, hole))
    }

    @Test fun fairway_position_closer_than_the_tee_is_on_hole() {
        // A point ~150 m short of the green, on the play line.
        val fairway = Geo.destination(green, Geo.bearingDegrees(green, tee), 150.0)
        assertTrue(Distances.isOnHole(fairway, hole))
    }

    @Test fun a_fix_far_beyond_the_tee_is_rejected() {
        // 250 m the wrong side of the tee: ~660 m from the green, way past tee→green(+100).
        val wrong = Geo.destination(green, Geo.bearingDegrees(green, tee), 660.0)
        assertFalse(Distances.isOnHole(wrong, hole))
    }

    @Test fun a_couple_hundred_metres_off_is_rejected() {
        // The reported failure: standing ~150 m out but the fix reads ~450 m — i.e. ~40 m past the
        // tee → green + margin. Must be flagged, not shown as live.
        val off = Geo.destination(green, Geo.bearingDegrees(green, tee), 520.0)
        assertFalse(Distances.isOnHole(off, hole))
    }
}
