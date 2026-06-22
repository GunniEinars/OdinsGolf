package com.odinsgolf.data.model

/**
 * A WGS84 coordinate used throughout the app. Internally everything is in
 * degrees lat/lon; distances are computed in meters and only converted to
 * yards for display.
 */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
)
