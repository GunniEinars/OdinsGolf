package com.odinsgolf

import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.geo.Geo
import com.odinsgolf.geo.GreenEdges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenEdgesTest {

    // A green centred at G, with the tee due south, and a rectangular putting surface
    // elongated ~44 m along the N–S line of play and ~10 m wide.
    private val center = GeoPoint(64.0700, -21.9300)
    private val tee = GeoPoint(64.0660, -21.9300) // due south of centre
    private val ring = listOf(
        GeoPoint(64.0698, -21.9301), // south-west (front-ish)
        GeoPoint(64.0698, -21.9299), // south-east
        GeoPoint(64.0702, -21.9299), // north-east (back-ish)
        GeoPoint(64.0702, -21.9301), // north-west
    )

    @Test
    fun front_is_toward_the_tee_back_is_away() {
        val (front, back) = GreenEdges.fromPolygon(tee, center, ring)!!
        // Tee is south, so the front edge must sit south of centre and the back north.
        assertTrue("front south of centre", front.lat < center.lat)
        assertTrue("back north of centre", back.lat > center.lat)
        // And the front is genuinely nearer the tee than the back.
        assertTrue(
            "front nearer tee than back",
            Geo.distanceMeters(tee, front) < Geo.distanceMeters(tee, back),
        )
    }

    @Test
    fun depth_matches_the_polygon_extent_along_play() {
        val (front, back) = GreenEdges.fromPolygon(tee, center, ring)!!
        // Ring spans 64.0698..64.0702 ≈ 0.0004° ≈ 44.5 m along the N–S play axis.
        val depth = Geo.distanceMeters(front, back)
        assertEquals(44.5, depth, 3.0)
        // Centre is roughly midway, so each half ≈ 22 m.
        assertEquals(22.0, Geo.distanceMeters(center, front), 4.0)
        assertEquals(22.0, Geo.distanceMeters(center, back), 4.0)
    }

    @Test
    fun degenerate_input_returns_null() {
        assertNull("too few ring points", GreenEdges.fromPolygon(tee, center, ring.take(2)))
        assertNull("tee coincides with centre", GreenEdges.fromPolygon(center, center, ring))
    }
}
