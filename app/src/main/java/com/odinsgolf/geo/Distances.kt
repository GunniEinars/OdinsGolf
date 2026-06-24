package com.odinsgolf.geo

import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Hole

/** Distances to a hole's green, in meters. Null where geometry is missing. */
data class GreenDistances(
    val frontMeters: Double?,
    val centerMeters: Double?,
    val backMeters: Double?,
) {
    /** Green depth (back - front) when both edges are known. */
    val depthMeters: Double?
        get() = if (frontMeters != null && backMeters != null) backMeters - frontMeters else null
}

object Distances {

    fun toGreen(hole: Hole, from: GeoPoint?): GreenDistances {
        if (from == null) return GreenDistances(null, null, null)
        fun d(to: GeoPoint?) = to?.let { Geo.distanceMeters(from, it) }
        return GreenDistances(
            frontMeters = d(hole.green.front),
            centerMeters = d(hole.green.center),
            backMeters = d(hole.green.back),
        )
    }
}
