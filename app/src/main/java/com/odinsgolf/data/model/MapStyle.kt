package com.odinsgolf.data.model

/** Hole-map base layer: an offline vector drawing, or satellite imagery. */
enum class MapStyle {
    VECTOR,
    SATELLITE,
    ;

    companion object {
        fun fromName(name: String?): MapStyle =
            entries.firstOrNull { it.name == name } ?: VECTOR
    }
}
