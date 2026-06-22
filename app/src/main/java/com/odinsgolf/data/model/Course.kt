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
) {
    /**
     * True when we have enough real geometry to show meaningful distances.
     * Front/back may still be missing (field-capture pending) but center is enough.
     */
    val hasGeometry: Boolean
        get() = green.center != null
}

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
