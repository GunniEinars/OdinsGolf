package com.odinsgolf

import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.geo.CanvasProjector
import com.odinsgolf.geo.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun distance_hole1_tee_to_green_is_reasonable() {
        // Setberg hole 1 tee -> green G1 (real OSM data). Expect a few hundred meters.
        val tee = GeoPoint(64.0668091, -21.9238815)
        val green = GeoPoint(64.0696949, -21.9290879)
        val d = Geo.distanceMeters(tee, green)
        assertTrue("expected 300-450m for a par 5, got $d", d in 300.0..480.0)
    }

    @Test
    fun distance_symmetric_and_zero() {
        val a = GeoPoint(64.07, -21.92)
        val b = GeoPoint(64.071, -21.921)
        assertEquals(Geo.distanceMeters(a, b), Geo.distanceMeters(b, a), 1e-6)
        assertEquals(0.0, Geo.distanceMeters(a, a), 1e-6)
    }

    @Test
    fun one_degree_latitude_is_about_111km() {
        val a = GeoPoint(0.0, 0.0)
        val b = GeoPoint(1.0, 0.0)
        val d = Geo.distanceMeters(a, b)
        assertEquals(111_195.0, d, 200.0)
    }

    @Test
    fun projector_null_for_empty_points() {
        assertNull(CanvasProjector.create(emptyList(), 100f, 100f, 8f))
    }

    @Test
    fun projector_handles_single_point_without_crashing() {
        val proj = CanvasProjector.create(listOf(GeoPoint(64.07, -21.92)), 100f, 100f, 8f)
        assertNotNull(proj)
        val p = proj!!.project(GeoPoint(64.07, -21.92))
        // Single point should land roughly centered.
        assertEquals(50f, p.x, 1f)
        assertEquals(50f, p.y, 1f)
    }

    @Test
    fun projector_keeps_north_up() {
        val south = GeoPoint(64.0668091, -21.9238815)
        val north = GeoPoint(64.0743719, -21.9267843)
        val proj = CanvasProjector.create(listOf(south, north), 200f, 200f, 10f)!!
        val pSouth = proj.project(south)
        val pNorth = proj.project(north)
        // Higher latitude must have a smaller y (closer to top of screen).
        assertTrue("north should be above south on screen", pNorth.y < pSouth.y)
    }
}
