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

    /**
     * Sanity check that a GPS position is plausibly *on this hole*, to catch a wildly wrong fix
     * before it shows a confident wrong yardage (or squishes the map). During normal play you're
     * always at least as close to the green as the tee is — you tee off *toward* it — so a fix that
     * puts you materially farther from the green than the tee is almost certainly bad. A generous
     * [marginMeters] means only egregious errors (the "hundreds of metres off" kind) are rejected;
     * ordinary GPS noise and legitimate long approaches always pass. Returns true when we can't tell
     * (no green, or no tee to compare against within a wide cap).
     */
    fun isOnHole(from: GeoPoint, hole: Hole, marginMeters: Double = 100.0): Boolean {
        val green = hole.green.center ?: return true
        val toGreen = Geo.distanceMeters(from, green)
        val tee = hole.tee ?: return toGreen < 700.0 // no tee to compare: only reject the absurd
        return toGreen <= Geo.distanceMeters(tee, green) + marginMeters
    }
}
