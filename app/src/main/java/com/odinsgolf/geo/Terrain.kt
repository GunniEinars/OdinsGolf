package com.odinsgolf.geo

import com.odinsgolf.data.model.FeatureKind
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Hole
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Elevation-adjusted "plays like" distance. Uphill plays longer, downhill shorter
 * (≈1 m per 1 m of rise, the common rule). The player's ground elevation is read
 * from the hole's baked EU-DEM profile at their projected position along the
 * tee→green line — fully offline, no unreliable GPS altitude or wind guesswork.
 */
object PlaysLike {

    data class Result(val rawMeters: Double, val playsLikeMeters: Double, val deltaMeters: Double) {
        /** True when the elevation change is big enough to be worth showing. */
        val significant: Boolean get() = kotlin.math.abs(deltaMeters) >= SIGNIFICANT_M
    }

    private const val SIGNIFICANT_M = 3.0

    fun toCenter(hole: Hole, from: GeoPoint?): Result? {
        val center = hole.green.center ?: return null
        val origin = from ?: return null
        val raw = Geo.distanceMeters(origin, center)
        val profile = hole.elevationProfile
        val tee = hole.tee
        if (profile.size < 2 || tee == null) return Result(raw, raw, 0.0)
        val t = projectFraction(origin, tee, center)
        val playerElev = elevationAt(profile, t)
        val greenElev = profile.last()
        val delta = greenElev - playerElev
        return Result(raw, (raw + delta).coerceAtLeast(0.0), delta)
    }

    /** Linear sample of the evenly-spaced profile at fraction [t] in 0..1. */
    fun elevationAt(profile: List<Double>, t: Double): Double {
        if (profile.size == 1) return profile[0]
        val pos = t.coerceIn(0.0, 1.0) * (profile.size - 1)
        val i = pos.toInt().coerceIn(0, profile.size - 2)
        return profile[i] + (profile[i + 1] - profile[i]) * (pos - i)
    }

    /** Fraction along a→b of p's perpendicular projection, clamped to 0..1. */
    private fun projectFraction(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val cosLat = cos(Math.toRadians(a.lat))
        val ax = a.lon * cosLat; val ay = a.lat
        val bx = b.lon * cosLat; val by = b.lat
        val px = p.lon * cosLat; val py = p.lat
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return 0.0
        return (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
    }
}

/**
 * Carry distances over hazards that lie ahead on the line of play (tee shots and
 * layups). Carry = distance to clear the hazard's far edge, measured along the
 * bearing from the player (or the tee) to the green.
 */
object Carry {

    data class HazardCarry(val label: String, val carryMeters: Double)

    fun ahead(hole: Hole, from: GeoPoint?): List<HazardCarry> {
        val center = hole.green.center ?: return emptyList()
        val origin = from ?: hole.tee ?: return emptyList()
        val bearing = Geo.bearingDegrees(origin, center)
        val toGreen = Geo.distanceMeters(origin, center)
        val result = ArrayList<HazardCarry>()
        for (f in hole.features) {
            if (f.kind != FeatureKind.WATER && f.kind != FeatureKind.BUNKER) continue
            var near = Double.MAX_VALUE
            var far = -Double.MAX_VALUE
            var minPerp = Double.MAX_VALUE
            for (v in f.ring) {
                val dist = Geo.distanceMeters(origin, v)
                val ang = Math.toRadians(Geo.bearingDegrees(origin, v) - bearing)
                val along = dist * cos(ang)
                val perp = abs(dist * sin(ang))
                if (along < near) near = along
                if (along > far) far = along
                if (perp < minPerp) minPerp = perp
            }
            // A real carry: ahead, within reach (so far greenside bunkers don't show
            // as a meaningless tee "carry"), finishing before the green, and on the
            // corridor (not off to the side, which projects to a bogus short carry).
            if (near in 10.0..REACH_M && far < toGreen - 5.0 && minPerp < 22.0) {
                val label = if (f.kind == FeatureKind.WATER) "Water" else "Bunker"
                result.add(HazardCarry(label, far))
            }
        }
        return result.sortedBy { it.carryMeters }.take(2)
    }

    /** Hazards whose near edge is beyond this (m) aren't a carry you can attempt yet. */
    private const val REACH_M = 240.0
}
