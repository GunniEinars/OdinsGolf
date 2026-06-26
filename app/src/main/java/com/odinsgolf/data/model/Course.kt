package com.odinsgolf.data.model

/** Resolved, ready-to-use course model consumed by the UI and ViewModel. */
data class Course(
    val id: String,
    val name: String,
    val clubName: String,
    val country: String,
    val locationHint: String,
    val defaultUnits: Units,
    val par: Int?,
    val attribution: List<String>,
    val dataQuality: List<String>,
    val notes: String,
    val holes: List<Hole>,
) {
    fun holeByNumber(number: Int): Hole? = holes.firstOrNull { it.number == number }
}

data class Hole(
    val number: Int,
    val displayNumber: String,
    val par: Int,
    val strokeIndex: Int?,
    val tee: GeoPoint?,
    val green: Green,
    val hazards: List<Hazard>,
    val path: List<GeoPoint>,
    val notes: String,
    /** OSM area polygons (green/fairway/bunker/water/tee) for the vector hole map. */
    val features: List<HoleFeature> = emptyList(),
    /** Ground elevation in metres sampled evenly tee→green (EU-DEM). Empty if unknown. */
    val elevationProfile: List<Double> = emptyList(),
    /**
     * Id of the shared physical green. Setberg plays 9 greens as 18 holes, so hole N
     * and N+9 carry the same [greenId]; a surveyed green centre applies to both.
     */
    val greenId: String? = null,
) {
    /**
     * True when we have enough real geometry to show meaningful distances.
     * Front/back may still be missing (field-capture pending) but center is enough.
     */
    val hasGeometry: Boolean
        get() = green.center != null
}

/** Kinds of mapped golf areas, drawn back-to-front in that order. */
enum class FeatureKind { FAIRWAY, GREEN, BUNKER, WATER, TEE }

/** A closed area polygon (the ring is not repeated at the end; close it when drawing). */
data class HoleFeature(val kind: FeatureKind, val ring: List<GeoPoint>)

/**
 * A green. [center] is the shared physical green center (same for hole N and N+9).
 * [front]/[back] are per-hole approach edges and are often null until captured.
 */
data class Green(
    val center: GeoPoint?,
    val front: GeoPoint?,
    val back: GeoPoint?,
)

data class Hazard(
    val id: String,
    val name: String,
    val type: String,
    val point: GeoPoint,
)
