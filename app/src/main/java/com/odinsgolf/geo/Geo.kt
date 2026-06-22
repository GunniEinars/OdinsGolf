package com.odinsgolf.geo

import com.odinsgolf.data.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure geo math. No Android dependencies so it is unit-testable on the JVM. */
object Geo {

    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in meters (Haversine). */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial bearing from a to b, degrees 0..360 (0 = north). */
    fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }
}

/** A projected 2D point in canvas pixels. */
data class Point2D(val x: Float, val y: Float)

/**
 * Equirectangular projection of lat/lon onto a canvas. Longitude is scaled by
 * cos(meanLatitude) — at Setberg's ~64°N that factor is ~0.44, so without it
 * every hole map would be stretched roughly 2.3x sideways. Aspect ratio is
 * preserved; north is up; the result is centered with padding.
 */
class CanvasProjector private constructor(
    private val cosRefLat: Double,
    private val minX: Double,
    private val minY: Double,
    private val scale: Double,
    private val offsetX: Double,
    private val offsetY: Double,
    private val canvasHeight: Float,
) {
    private fun lonToX(lon: Double): Double = lon * cosRefLat

    fun project(p: GeoPoint): Point2D {
        val px = (lonToX(p.lon) - minX) * scale + offsetX
        // Invert Y so that increasing latitude (north) goes up the screen.
        val py = canvasHeight - ((p.lat - minY) * scale + offsetY)
        return Point2D(px.toFloat(), py.toFloat())
    }

    companion object {
        /**
         * Build a projector that fits all [points] into the given canvas with
         * [padding] px on every side. Returns null if there are no points.
         * Degenerate spans (one point / identical points) are expanded so the
         * content lands centered rather than dividing by zero.
         */
        fun create(
            points: List<GeoPoint>,
            canvasWidth: Float,
            canvasHeight: Float,
            padding: Float,
        ): CanvasProjector? {
            if (points.isEmpty()) return null
            val refLat = points.map { it.lat }.average()
            val cosRefLat = cos(Math.toRadians(refLat))

            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (p in points) {
                val x = p.lon * cosRefLat
                val y = p.lat
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
            }

            var spanX = maxX - minX
            var spanY = maxY - minY
            val eps = 1e-6
            if (spanX < eps) { minX -= eps; maxX += eps; spanX = maxX - minX }
            if (spanY < eps) { minY -= eps; maxY += eps; spanY = maxY - minY }

            val usableW = (canvasWidth - 2 * padding).coerceAtLeast(1f)
            val usableH = (canvasHeight - 2 * padding).coerceAtLeast(1f)
            val scale = min(usableW / spanX, usableH / spanY)

            // Center the content within the usable area.
            val contentW = spanX * scale
            val contentH = spanY * scale
            val offsetX = padding + (usableW - contentW) / 2.0
            val offsetY = padding + (usableH - contentH) / 2.0

            return CanvasProjector(cosRefLat, minX, minY, scale, offsetX, offsetY, canvasHeight)
        }
    }
}
