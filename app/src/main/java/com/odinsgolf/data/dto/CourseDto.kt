package com.odinsgolf.data.dto

import com.odinsgolf.data.model.Course
import com.odinsgolf.data.model.FeatureKind
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Green
import com.odinsgolf.data.model.Hazard
import com.odinsgolf.data.model.Hole
import com.odinsgolf.data.model.HoleFeature
import com.odinsgolf.data.model.Units
import com.odinsgolf.geo.Geo
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
data class FeatureDto(
    val kind: String,
    /** Polygon ring as [[lat,lon], …], not repeated at the end. */
    val ring: List<List<Double>> = emptyList(),
)

@Serializable
data class ElevationDto(val profile: List<Double> = emptyList())

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
    val features: List<FeatureDto> = emptyList(),
    val elevation: ElevationDto? = null,
    val notes: String = "",
) {
    fun toDomain(
        greensById: Map<String, GreenDto>,
        hazardsById: Map<String, HazardDto>,
    ): Hole {
        val center = greenId?.let { greensById[it]?.center?.toGeoPointOrNull() }
        val teePoint = tee?.toGeoPointOrNull()
        var front = greenFront?.toGeoPointOrNull()
        var back = greenBack?.toGeoPointOrNull()
        // When real front/back edges aren't supplied, approximate them by stepping
        // off the green centre along this hole's playing line (centre±~half a green
        // depth). Front is toward the tee, back away from it. A real Survey capture
        // overrides these. Good enough to show approach yardages without field work.
        if (front == null && back == null && center != null && teePoint != null) {
            val bearingToGreen = Geo.bearingDegrees(teePoint, center)
            front = Geo.destination(center, (bearingToGreen + 180.0) % 360.0, GREEN_HALF_DEPTH_M)
            back = Geo.destination(center, bearingToGreen, GREEN_HALF_DEPTH_M)
        }
        val green = Green(center = center, front = front, back = back)
        val resolvedHazards = hazardRefs.mapNotNull { ref ->
            hazardsById[ref]?.let { h ->
                h.point.toGeoPointOrNull()?.let { p -> Hazard(h.id, h.name, h.type, p) }
            }
        }
        val resolvedFeatures = features.mapNotNull { f ->
            val kind = runCatching { FeatureKind.valueOf(f.kind.uppercase()) }.getOrNull() ?: return@mapNotNull null
            val ring = f.ring.mapNotNull { if (it.size >= 2) GeoPoint(it[0], it[1]) else null }
            if (ring.size >= 3) HoleFeature(kind, ring) else null
        }
        return Hole(
            number = number,
            displayNumber = displayNumber ?: number.toString(),
            par = par,
            strokeIndex = strokeIndex,
            tee = teePoint,
            green = green,
            hazards = resolvedHazards,
            path = path.mapNotNull { it.toGeoPointOrNull() },
            notes = notes,
            features = resolvedFeatures,
            elevationProfile = elevation?.profile ?: emptyList(),
        )
    }

    private companion object {
        /** Approx. green centre→edge distance (m) used to synthesize front/back. */
        const val GREEN_HALF_DEPTH_M = 11.0
    }
}
