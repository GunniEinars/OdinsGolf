package com.odinsgolf

import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Green
import com.odinsgolf.data.model.Hole
import com.odinsgolf.geo.HoleHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Nearest-hole hint for Setberg's 9-greens-as-18 layout. Coordinates are the real
 * G1/G2 greens and hole 1/2/10 tees, so the geometry mirrors the course.
 */
class HoleHintTest {

    private val g1 = GeoPoint(64.0696949, -21.9290879)
    private val g2 = GeoPoint(64.0711684, -21.9284306)
    private val tee1 = GeoPoint(64.0668091, -21.9238815)
    private val tee2 = GeoPoint(64.0702182, -21.9299546)
    private val tee10 = GeoPoint(64.0671818, -21.9242914)
    private val tee11 = GeoPoint(64.0700752, -21.9298936)

    private fun hole(n: Int, greenId: String, tee: GeoPoint, center: GeoPoint) = Hole(
        number = n,
        displayNumber = n.toString(),
        par = 4,
        strokeIndex = n,
        tee = tee,
        green = Green(center = center, front = null, back = null),
        hazards = emptyList(),
        path = listOf(tee, center),
        notes = "",
        greenId = greenId,
    )

    private val holes = listOf(
        hole(1, "G1", tee1, g1),
        hole(2, "G2", tee2, g2),
        hole(10, "G1", tee10, g1), // shares G1 with hole 1 (back-nine sibling)
        hole(11, "G2", tee11, g2), // shares G2 with hole 2
    )

    private val range = 1..18

    @Test
    fun suggests_the_hole_you_are_standing_at() {
        // On the 2nd green with the watch stuck on hole 1 → offer hole 2.
        assertEquals(2, HoleHint.suggest(holes, g2, currentHole = 1, range))
    }

    @Test
    fun no_hint_when_already_on_the_selected_hole() {
        assertNull(HoleHint.suggest(holes, g1, currentHole = 1, range))
    }

    @Test
    fun shared_green_prefers_the_sibling_in_the_current_nine() {
        // Standing at the shared 1st/10th green while playing the front nine (hole 2
        // selected) must offer hole 1, never its back-nine sibling hole 10.
        assertEquals(1, HoleHint.suggest(holes, g1, currentHole = 2, range))
        // And on the back nine (hole 11 selected) the same spot offers hole 10.
        assertEquals(10, HoleHint.suggest(holes, g1, currentHole = 11, range))
    }

    @Test
    fun no_hint_without_a_fix() {
        assertNull(HoleHint.suggest(holes, null, currentHole = 1, range))
    }

    @Test
    fun respects_the_active_range() {
        // Front-nine round: hole 2 is out of range, so standing on its green offers nothing.
        assertNull(HoleHint.suggest(holes, g2, currentHole = 1, range = 1..1))
    }
}
