package com.odinsgolf.data.dto

import com.odinsgolf.data.model.Course
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Green
import com.odinsgolf.data.model.Hazard
import com.odinsgolf.data.model.Hole
import com.odinsgolf.data.model.Units
import kotlinx.serialization.Serializable

/**
 * Serializable mirror of the course JSON in assets. Kept separate from the
 * domain model so parsing concerns never leak into the UI. [toDomain] resolves
 * shared greens and drops placeholder coordinates.
 */
@Serializable
data class CourseDto(
    val schemaVersion: Int = 1,
    val courseId: String,
    val courseName: String,
    val clubName: String = "",
    val country: String = "",
    val locationHint: String = "",
    val defaultUnits: String = "meters",
    val par: Int? = null,
    val physicalHoles: Int? = null,
    val playedHoles: Int? = null,
    val sourceAttribution: List<String> = emptyList(),
    val dataQuality: List<String> = emptyList(),
    val notes: String = "",
    val greens: List<GreenDto> = emptyList(),
    val hazards: List<HazardDto> = emptyList(),
    val holes: List<HoleDto> = emptyList(),
) {
    fun toDomain(): Course {
        val greensById = greens.associateBy { it.id }
        val hazardsById = hazards.associateBy { it.id }
        return Course(
            id = courseId,
            name = courseName,
            clubName = clubName,
            country = country,
            locationHint = locationHint,
            defaultUnits = Units.parse(defaultUnits),
            par = par,
            attribution = sourceAttribution,
            dataQuality = dataQuality,
            notes = notes,
            holes = holes.map { it.toDomain(greensById, hazardsById) },
        )
    }
}

@Serializable
data class CoordDto(
    val lat: Double,
    val lon: Double,
    val quality: String? = null,
) {
    /** Null if this is a placeholder (0,0 or explicitly flagged PLACEHOLDER). */
    fun toGeoPointOrNull(): GeoPoint? {
        if (quality?.equals("PLACEHOLDER", ignoreCase = true) == true) return null
        if (lat == 0.0 && lon == 0.0) return null
        return GeoPoint(lat, lon)
    }
}

@Serializable
data class GreenDto(
    val id: String,
    val center: CoordDto,
    val quality: String = "UNKNOWN",
)

@Serializable
data class HazardDto(
    val id: String,
    val name: String = "",
    val type: String = "hazard",
    val point: CoordDto,
    val quality: String = "UNKNOWN",
)

@Serializable
data class HoleDto(
    val number: Int,
    val displayNumber: String? = null,
    val par: Int,
    val strokeIndex: Int? = null,
    val greenId: String? = null,
    val lengthMeters: Double? = null,
    val tee: CoordDto? = null,
    val greenFront: CoordDto? = null,
    val greenBack: CoordDto? = null,
    val hazardRefs: List<String> = emptyList(),
    val path: List<CoordDto> = emptyList(),
    val notes: String = "",
) {
    fun toDomain(
        greensById: Map<String, GreenDto>,
        hazardsById: Map<String, HazardDto>,
    ): Hole {
        val center = greenId?.let { greensById[it]?.center?.toGeoPointOrNull() }
        val green = Green(
            center = center,
            front = greenFront?.toGeoPointOrNull(),
            back = greenBack?.toGeoPointOrNull(),
        )
        val resolvedHazards = hazardRefs.mapNotNull { ref ->
            hazardsById[ref]?.let { h ->
                h.point.toGeoPointOrNull()?.let { p -> Hazard(h.id, h.name, h.type, p) }
            }
        }
        return Hole(
            number = number,
            displayNumber = displayNumber ?: number.toString(),
            par = par,
            strokeIndex = strokeIndex,
            tee = tee?.toGeoPointOrNull(),
            green = green,
            hazards = resolvedHazards,
            path = path.mapNotNull { it.toGeoPointOrNull() },
            notes = notes,
        )
    }
}
