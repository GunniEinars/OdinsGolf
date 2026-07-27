package com.odinsgolf

import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Green
import com.odinsgolf.data.model.Hole
import com.odinsgolf.geo.Geo
import com.odinsgolf.geo.HoleProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The map projection must be invertible for tap-to-measure: a tapped pixel unprojects back to the
 * geographic point that would project to that pixel. Coordinates are Setberg hole 1 (real OSM data).
 */
class HoleProjectionTest {

    private val tee = GeoPoint(64.0668091, -21.9238815)
    private val green = GeoPoint(64.0696949, -21.9290879)

    private val hole = Hole(
        number = 1,
        displayNumber = "1",
        par = 5,
        strokeIndex = 6,
        tee = tee,
        green = Green(center = green, front = null, back = null),
        hazards = emptyList(),
        path = listOf(tee, green),
        notes = "",
    )

    private val proj = HoleProjection.build(hole, listOf(tee, green), 400f, 400f, 20f)

    @Test
    fun project_then_unproject_round_trips() {
        assertNotNull(proj)
        val p = proj!!
        for (pt in listOf(tee, green, GeoPoint(64.0682, -21.9265))) {
            val (x, y) = p.project(pt)
            val back = p.unproject(x, y)
            // Round-trip should land within a metre — well under tap resolution.
            val err = Geo.distanceMeters(pt, back)
            assertTrue("round-trip error ${err}m too high for $pt", err < 1.0)
        }
    }

    @Test
    fun up_bearing_matches_tee_to_green() {
        val p = proj!!
        val expected = Geo.bearingDegrees(tee, green) // map "up" is the tee→green direction
        assertEquals(expected, p.upBearingDegrees(), 0.5)
    }
}
