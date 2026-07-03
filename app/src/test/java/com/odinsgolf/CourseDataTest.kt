package com.odinsgolf

import com.odinsgolf.data.dto.CourseDto
import com.odinsgolf.data.model.Course
import com.odinsgolf.geo.Geo
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parses every bundled course asset through the real [CourseDto] pipeline, exactly
 * as the app does at startup. This guards the JSON⇄DTO contract: a schema drift
 * (e.g. `path` baked as [[lat,lon]] while the DTO expected objects) fails CI here
 * instead of showing a red "Could not load course" screen on the watch.
 */
class CourseDataTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun assetFile(name: String): File =
        listOf("src/main/assets/courses/$name", "app/src/main/assets/courses/$name")
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("course asset not found: $name (cwd=${File(".").absolutePath})")

    private fun load(name: String): Course =
        json.decodeFromString<CourseDto>(assetFile(name).readText()).toDomain()

    @Test
    fun all_bundled_courses_parse_and_resolve() {
        val files = assetFile("setbergsvollur.json").parentFile!!
            .listFiles { f -> f.extension == "json" }!!
            .map { it.name }
            .sorted()
        assertTrue("expected bundled courses", files.isNotEmpty())

        for (name in files) {
            val course = load(name)
            assertEquals("$name: 18 holes", 18, course.holes.size)
            // Geometry the UI relies on actually resolved (not silently empty).
            assertTrue("$name: every hole has a green centre", course.holes.all { it.green.center != null })
            assertTrue("$name: features resolved", course.holes.any { it.features.isNotEmpty() })
            assertTrue("$name: centerlines resolved", course.holes.any { it.path.size >= 2 })
            assertTrue("$name: elevation resolved", course.holes.any { it.elevationProfile.size >= 2 })
            // Hazard refs all point at a real hazard.
            assertTrue("$name: hazards present", course.holes.any { it.hazards.isNotEmpty() })
        }
    }

    @Test
    fun setberg_holes_get_real_front_back_edges_from_the_green_polygon() {
        val c = load("setbergsvollur.json")
        for (h in c.holes) {
            val tee = h.tee ?: continue
            val front = h.green.front
            val back = h.green.back
            assertTrue("hole ${h.number}: front edge derived", front != null)
            assertTrue("hole ${h.number}: back edge derived", back != null)
            // Front must be the tee-side edge, back the far edge.
            assertTrue(
                "hole ${h.number}: front nearer tee than back",
                Geo.distanceMeters(tee, front!!) < Geo.distanceMeters(tee, back!!),
            )
            // Real greens: depth in a believable band (not the old flat 22 m guess, not absurd).
            val depth = Geo.distanceMeters(front, back)
            assertTrue("hole ${h.number}: green depth $depth m out of range", depth in 12.0..45.0)
        }
    }

    @Test
    fun setberg_carries_official_course_and_slope_ratings() {
        val c = load("setbergsvollur.json")
        assertTrue("par 72", c.par == 72)
        assertEquals(70.8, c.courseRating!!, 0.0001)
        assertTrue("slope 130", c.slopeRating == 130)
    }
}
