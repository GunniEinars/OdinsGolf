package com.odinsgolf.geo

import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Hole
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Projects a hole onto the canvas with the **playing line vertical** — tee at the
 * bottom, green at the top — like a golf-watch hole view. Local-metre planar
 * projection (fine at hole scale), rotated so tee→green points up, then fit to
 * the canvas. [metersToPx] lets callers draw true range rings.
 */
class HoleProjection private constructor(
    private val originLat: Double,
    private val originLon: Double,
    private val mPerLat: Double,
    private val mPerLon: Double,
    private val dAlongN: Double,
    private val dAlongE: Double,
    private val scale: Double,
    private val offsetX: Double,
    private val offsetY: Double,
    private val canvasH: Float,
) {
    val metersToPx: Float get() = scale.toFloat()

    fun project(p: GeoPoint): Pair<Float, Float> {
        val mE = (p.lon - originLon) * mPerLon
        val mN = (p.lat - originLat) * mPerLat
        val along = mE * dAlongE + mN * dAlongN          // toward green (+up)
        val perp = mE * dAlongN - mN * dAlongE           // right (+x)
        val x = offsetX + perp * scale
        val y = canvasH - (offsetY + along * scale)      // invert: green at top
        return x.toFloat() to y.toFloat()
    }

    /** Inverse of [project]: a canvas pixel back to a geographic point (for tap-to-measure). */
    fun unproject(x: Float, y: Float): GeoPoint {
        val perp = (x - offsetX) / scale
        val along = (canvasH - y - offsetY) / scale
        // Rotate the along/perp frame back to east/north (the 2x2 is its own inverse up to sign).
        val mE = along * dAlongE + perp * dAlongN
        val mN = along * dAlongN - perp * dAlongE
        return GeoPoint(originLat + mN / mPerLat, originLon + mE / mPerLon)
    }

    /** Geographic bearing (deg, 0 = N) of the map's "up" direction (tee→green), so overlays like the
     *  wind arrow can be rotated from a real-world bearing into this rotated, play-line-up frame. */
    fun upBearingDegrees(): Double = (Math.toDegrees(kotlin.math.atan2(dAlongE, dAlongN)) + 360.0) % 360.0

    companion object {
        fun build(hole: Hole, points: List<GeoPoint>, w: Float, h: Float, pad: Float): HoleProjection? {
            val tee = hole.tee
            val green = hole.green.center
            if (points.isEmpty() || green == null || w <= 0f || h <= 0f) return null

            val originLat = tee?.lat ?: green.lat
            val originLon = tee?.lon ?: green.lon
            val mPerLat = 111_320.0
            val mPerLon = 111_320.0 * cos(Math.toRadians(originLat))

            // Up = tee→green direction (fallback: due north if tee missing).
            val gE = (green.lon - originLon) * mPerLon
            val gN = (green.lat - originLat) * mPerLat
            val len = hypot(gE, gN)
            val dAlongE = if (len > 1e-6) gE / len else 0.0
            val dAlongN = if (len > 1e-6) gN / len else 1.0

            var minA = Double.MAX_VALUE; var maxA = -Double.MAX_VALUE
            var minP = Double.MAX_VALUE; var maxP = -Double.MAX_VALUE
            for (p in points) {
                val mE = (p.lon - originLon) * mPerLon
                val mN = (p.lat - originLat) * mPerLat
                val along = mE * dAlongE + mN * dAlongN
                val perp = mE * dAlongN - mN * dAlongE
                minA = min(minA, along); maxA = max(maxA, along)
                minP = min(minP, perp); maxP = max(maxP, perp)
            }
            var spanA = maxA - minA
            var spanP = maxP - minP
            if (spanA < 1.0) spanA = 1.0
            if (spanP < 1.0) spanP = 1.0

            val usableW = (w - 2 * pad).coerceAtLeast(1f).toDouble()
            val usableH = (h - 2 * pad).coerceAtLeast(1f).toDouble()
            val scale = min(usableW / spanP, usableH / spanA)

            // Centre the content; offsets are in the along/perp frame (metres*scale).
            val offsetX = pad + (usableW - spanP * scale) / 2.0 - minP * scale
            val offsetY = pad + (usableH - spanA * scale) / 2.0 - minA * scale
            return HoleProjection(originLat, originLon, mPerLat, mPerLon, dAlongN, dAlongE, scale, offsetX, offsetY, h)
        }
    }
}
