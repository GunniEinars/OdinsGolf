package com.odinsgolf

import com.odinsgolf.data.SurveyData
import com.odinsgolf.data.SurveyKind
import com.odinsgolf.data.SurveyPoint
import com.odinsgolf.data.SurveyRepository
import com.odinsgolf.data.model.Course
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Green
import com.odinsgolf.data.model.Hole
import com.odinsgolf.data.model.Units
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Setberg plays 9 physical greens as 18 holes: hole N and N+9 share a greenId.
 * A surveyed green centre on one must apply to its sibling too (same physical green).
 */
class SurveyOverlayTest {

    private val baseG1 = GeoPoint(64.0696949, -21.9290879)
    private val baseG2 = GeoPoint(64.0711684, -21.9284306)

    private fun hole(n: Int, greenId: String, center: GeoPoint) = Hole(
        number = n,
        displayNumber = n.toString(),
        par = 4,
        strokeIndex = n,
        tee = GeoPoint(64.066, -21.923),
        green = Green(center = center, front = null, back = null),
        hazards = emptyList(),
        path = emptyList(),
        notes = "",
        greenId = greenId,
    )

    private fun course() = Course(
        id = "setbergsvollur", name = "Setberg", clubName = "", country = "",
        locationHint = "", defaultUnits = Units.METERS, par = null,
        attribution = emptyList(), dataQuality = emptyList(), notes = "",
        holes = listOf(
            hole(1, "G1", baseG1),
            hole(10, "G1", baseG1), // shares the physical green with hole 1
            hole(2, "G2", baseG2),  // a different green, must stay untouched
        ),
    )

    @Test
    fun captured_centre_propagates_to_sibling_sharing_the_green() {
        val captured = GeoPoint(64.07000, -21.92950)
        val data = SurveyData(
            "setbergsvollur",
            listOf(SurveyPoint(holeNumber = 1, kind = SurveyKind.CENTER, lat = captured.lat, lon = captured.lon, epochMillis = 1L)),
        )
        val out = SurveyRepository.overlayOnto(course(), data)
        assertEquals(captured, out.holes.first { it.number == 1 }.green.center)
        assertEquals(captured, out.holes.first { it.number == 10 }.green.center)
        // A hole on a different green is unaffected.
        assertEquals(baseG2, out.holes.first { it.number == 2 }.green.center)
    }

    @Test
    fun a_holes_own_capture_takes_priority_over_its_sibling() {
        val c1 = GeoPoint(64.07001, -21.92951)
        val c10 = GeoPoint(64.07002, -21.92952)
        val data = SurveyData(
            "setbergsvollur",
            listOf(
                SurveyPoint(holeNumber = 1, kind = SurveyKind.CENTER, lat = c1.lat, lon = c1.lon, epochMillis = 1L),
                SurveyPoint(holeNumber = 10, kind = SurveyKind.CENTER, lat = c10.lat, lon = c10.lon, epochMillis = 2L),
            ),
        )
        val out = SurveyRepository.overlayOnto(course(), data)
        assertEquals(c1, out.holes.first { it.number == 1 }.green.center)
        assertEquals(c10, out.holes.first { it.number == 10 }.green.center)
    }
}
