package com.odinsgolf.geo

import com.odinsgolf.data.model.GeoPoint
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Derives real green front/back edge points from the green **polygon** by projecting
 * its vertices onto the tee→centre playing axis. The near extent (toward the tee) is
 * the front; the far extent is the back.
 *
 * This is far more accurate than a fixed centre±depth guess and is derived from the
 * verified OSM green geometry the app already ships: at Setberg it yields realistic,
 * asymmetric green depths (~17–33 m) that match a field survey, instead of a flat 22 m.
 *
 * Returns null for degenerate input (too few ring points, or a tee coincident with the
 * centre) so the caller can fall back to the simple estimate.
 */
object GreenEdges {

    fun fromPolygon(tee: GeoPoint, center: GeoPoint, ring: List<GeoPoint>): Pair<GeoPoint, GeoPoint>? {
        if (ring.size < 3) return null
        val mPerLat = 111_320.0
        val mPerLon = 111_320.0 * cos(Math.toRadians(center.lat))

        // Tee relative to the centre, in local metres.
        val tx = (tee.lon - center.lon) * mPerLon
        val ty = (tee.lat - center.lat) * mPerLat
        val len = hypot(tx, ty)
        if (len < 1e-6) return null
        // Unit vector pointing tee → centre (the direction of play; "back" is beyond it).
        val dx = -tx / len
        val dy = -ty / len

        var minProj = Double.MAX_VALUE
        var maxProj = -Double.MAX_VALUE
        for (p in ring) {
            val vx = (p.lon - center.lon) * mPerLon
            val vy = (p.lat - center.lat) * mPerLat
            val proj = vx * dx + vy * dy
            if (proj < minProj) minProj = proj
            if (proj > maxProj) maxProj = proj
        }

        // front = green extent toward the tee (smaller/negative projection);
        // back  = extent away from the tee (larger projection). Both on the play axis.
        val front = GeoPoint(center.lat + (minProj * dy) / mPerLat, center.lon + (minProj * dx) / mPerLon)
        val back = GeoPoint(center.lat + (maxProj * dy) / mPerLat, center.lon + (maxProj * dx) / mPerLon)
        return front to back
    }
}
