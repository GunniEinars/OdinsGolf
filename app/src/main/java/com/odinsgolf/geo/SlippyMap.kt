package com.odinsgolf.geo

import com.odinsgolf.data.model.GeoPoint
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** One slippy-map tile placed on the canvas (screen px), ready to draw. */
data class MapTile(
    val z: Int,
    val x: Int,
    val y: Int,
    val left: Float,
    val top: Float,
    val size: Float,
) {
    /** Stable key for caching/decoded-bitmap maps. */
    val key: Long get() = (z.toLong() shl 44) or (x.toLong() shl 22) or y.toLong()
}

/**
 * A Web-Mercator plan for one hole: which XYZ tiles cover the hole's bounding
 * box at the best zoom that fits the canvas, plus a [project] that maps any
 * lat/lon to the same screen pixels (so the schematic overlays line up with the
 * tiles). North is up.
 */
class MapPlan private constructor(
    private val zoom: Int,
    private val minWorldX: Double,
    private val minWorldY: Double,
    private val scale: Double,
    private val offsetX: Double,
    private val offsetY: Double,
    val tiles: List<MapTile>,
) {
    val z: Int get() = zoom

    fun project(p: GeoPoint): Pair<Float, Float> {
        val wx = worldX(p.lon, zoom)
        val wy = worldY(p.lat, zoom)
        val sx = (wx - minWorldX) * scale + offsetX
        val sy = (wy - minWorldY) * scale + offsetY
        return sx.toFloat() to sy.toFloat()
    }

    companion object {
        private const val TILE = 256.0
        private const val MIN_ZOOM = 13
        private const val MAX_ZOOM = 18
        private const val MAX_TILES = 16

        private fun worldX(lon: Double, z: Int): Double =
            (lon + 180.0) / 360.0 * TILE * (1 shl z)

        private fun worldY(lat: Double, z: Int): Double {
            val rad = Math.toRadians(lat)
            val y = (1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0
            return y * TILE * (1 shl z)
        }

        /**
         * Build a plan that fits [points] into [w]x[h] px with [pad] px margin.
         * Returns null if there are no points.
         */
        fun compute(points: List<GeoPoint>, w: Float, h: Float, pad: Float): MapPlan? {
            if (points.isEmpty() || w <= 0f || h <= 0f) return null
            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
            for (p in points) {
                minLat = min(minLat, p.lat); maxLat = max(maxLat, p.lat)
                minLon = min(minLon, p.lon); maxLon = max(maxLon, p.lon)
            }
            val usableW = (w - 2 * pad).coerceAtLeast(1f).toDouble()
            val usableH = (h - 2 * pad).coerceAtLeast(1f).toDouble()

            // Largest zoom whose bbox still fits the usable area (most detail).
            var zoom = MIN_ZOOM
            for (z in MAX_ZOOM downTo MIN_ZOOM) {
                val spanX = worldX(maxLon, z) - worldX(minLon, z)
                val spanY = worldY(minLat, z) - worldY(maxLat, z) // minLat -> larger y
                if (spanX <= usableW && spanY <= usableH) { zoom = z; break }
                if (z == MIN_ZOOM) zoom = MIN_ZOOM
            }

            val minWorldX = worldX(minLon, zoom)
            val maxWorldX = worldX(maxLon, zoom)
            val minWorldY = worldY(maxLat, zoom) // top (north)
            val maxWorldY = worldY(minLat, zoom) // bottom (south)
            var spanX = maxWorldX - minWorldX
            var spanY = maxWorldY - minWorldY
            val eps = 1.0
            if (spanX < eps) spanX = eps
            if (spanY < eps) spanY = eps

            val scale = min(usableW / spanX, usableH / spanY)
            val offsetX = pad + (usableW - spanX * scale) / 2.0
            val offsetY = pad + (usableH - spanY * scale) / 2.0

            // Tiles covering the bbox.
            val txMin = floor(minWorldX / TILE).toInt()
            val txMax = floor(maxWorldX / TILE).toInt()
            val tyMin = floor(minWorldY / TILE).toInt()
            val tyMax = floor(maxWorldY / TILE).toInt()
            val tiles = ArrayList<MapTile>()
            val n = 1 shl zoom
            for (tx in txMin..txMax) for (ty in tyMin..tyMax) {
                if (tx < 0 || ty < 0 || tx >= n || ty >= n) continue
                val left = ((tx * TILE) - minWorldX) * scale + offsetX
                val top = ((ty * TILE) - minWorldY) * scale + offsetY
                tiles.add(MapTile(zoom, tx, ty, left.toFloat(), top.toFloat(), (TILE * scale).toFloat()))
                if (tiles.size >= MAX_TILES) break
            }

            return MapPlan(zoom, minWorldX, minWorldY, scale, offsetX, offsetY, tiles)
        }
    }
}
